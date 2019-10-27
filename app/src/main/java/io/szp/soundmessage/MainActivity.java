package io.szp.soundmessage;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int SAMPLING_RATE_IN_HZ = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final float SHORT_MAX = 32768;

    // Common Parameter
    public int subcarrierNum = 10;
    public int pilotSubcarrierNum = 2;
    public int symbolLen = 2048;
    public float cyclicPrefixFactor = 0.1f;

    private float sampleFreq = 44100;
    private float carrierFreq = 16000;
    private float preambleLowFreq = 8000;
    private float preambleHighFreq = 16000;
    private int startPreambleNum = 3;
    private int endPreambleNum = 3;

    // Receiver Parameter
    private float startEndThreshold = 10;
    private float lagVarianceLimit = 10;
    private int symbolNumLimit = 16;

    // Computer parameter
    public int dataSubcarrierNum;
    public int[] pilotSubcarrierIndices;
    public int[] dataSubcarrierIndices;
    public int cyclicPrefixLen;
    public int cyclicPrefixStart;
    public int cyclicPrefixEnd;
    public int realSymbolLen;

    private float[] startPreambleSymbol, endPreambleSymbol;

    private int signalBufferLenLimit;

    // Receiver data
    private float[] previousWindow;
    private List<Float> signalBuffer;
    private boolean started;
    private Deque<Integer> starts;
    private Deque<Integer> ends;

    // Other fields
    private boolean permissionToRecordAccepted = false;
    private int receiverBufferFactor;
    private int receiverBufferSize;
    private boolean receiverEnabled;

    private AudioRecord receiver;
    private AtomicBoolean receiverOn = new AtomicBoolean(false);

    EditText sendText;
    TextView contentText;
    private PlotView plotView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO_PERMISSION);
        setContentView(R.layout.activity_main);
        sendText = findViewById(R.id.sendText);
        contentText = findViewById(R.id.contentText);
        plotView = findViewById(R.id.plotView);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        receiverBufferFactor = Integer.parseInt(preferences.getString(
                getString(R.string.receiver_buffer_factor_key), "2"));
        updateReceiverBufferSize();
        setReceiverEnabled(preferences.getBoolean(getString(R.string.receiver_enabled_key), true));
        updateParameter();
    }

    @Override
    protected void onDestroy() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        if (!permissionToRecordAccepted)
            finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.settingsButton) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("SetTextI18n")
    public void log(String text) {
        contentText.setText(contentText.getText().toString() + text + '\n');
    }

    public void onSendButtonClick(View view) {
        log(sendText.getText().toString());
        sendText.getText().clear();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        Log.d("MainActivity", key + " changed");
        if (key.equals(getString(R.string.receiver_enabled_key))) {
            setReceiverEnabled(preferences.getBoolean(key, true));
        } else if (key.equals(getString(R.string.receiver_buffer_factor_key))) {
            boolean commitBack = false;
            try {
                int newReceiverBufferFactor = Integer.parseInt(preferences.getString(key, "2"));
                if (newReceiverBufferFactor < 1) {
                    receiverBufferFactor = 1;
                    commitBack = true;
                } else if (newReceiverBufferFactor > 10) {
                    receiverBufferFactor = 10;
                    commitBack = true;
                } else {
                    receiverBufferFactor = newReceiverBufferFactor;
                }
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(receiverBufferFactor));
                editor.apply();
            }
            updateReceiverBufferSize();
        }
    }

    protected void setReceiverEnabled(boolean enabled) {
        if (receiverEnabled == enabled)
            return;
        receiverEnabled = enabled;
        Thread receiverThread;
        if (enabled) {
            receiver = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ,
                    CHANNEL_CONFIG, AUDIO_FORMAT, receiverBufferSize);
            receiver.startRecording();
            receiverOn.set(true);
            receiverThread = new Thread(new ReceiverRunnable(), "Receiver Thread");
            receiverThread.start();
        } else {
            receiverOn.set(false);
            receiver.stop();
            receiver.release();
            receiver = null;
        }
    }

    protected void updateParameter() {
        dataSubcarrierNum = subcarrierNum - pilotSubcarrierNum;
        pilotSubcarrierIndices = new int[pilotSubcarrierNum];
        for (int i = 0; i < pilotSubcarrierNum; ++i)
            pilotSubcarrierIndices[i] = Math.round((i + 1) *
                    (float) subcarrierNum / (pilotSubcarrierNum + 1)) - 1;
        dataSubcarrierIndices = new int[dataSubcarrierNum];
        int dataIndex = 0, pilotIndex = 0;
        for (int j = 0; j < subcarrierNum; ++j) {
            if (pilotIndex< pilotSubcarrierNum &&
                    j == pilotSubcarrierIndices[pilotIndex]) {
                ++pilotIndex;
            } else {
                dataSubcarrierIndices[dataIndex++] = j;
            }
        }
        cyclicPrefixLen = (int) Math.round(Math.ceil(cyclicPrefixFactor * symbolLen));
        cyclicPrefixStart = symbolLen - cyclicPrefixLen;
        cyclicPrefixEnd = symbolLen;
        realSymbolLen = symbolLen + cyclicPrefixLen;
        float[] preambleSymbolTime = new float[symbolLen];
        float sampleTime = 1 / sampleFreq;
        for (int i = 0; i < symbolLen; ++i)
            preambleSymbolTime[i] = i * sampleTime;
        startPreambleSymbol = SignalProcessing.chirp(preambleLowFreq, preambleHighFreq, preambleSymbolTime);
        endPreambleSymbol = SignalProcessing.chirp(preambleHighFreq, preambleLowFreq, preambleSymbolTime);
        signalBufferLenLimit = realSymbolLen * symbolNumLimit + endPreambleNum * symbolLen;
        signalBuffer = new ArrayList<>();
        started = false;
        starts = new ArrayDeque<>();
        ends = new ArrayDeque<>();
    }

    protected void updateReceiverBufferSize() {
        receiverBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG, AUDIO_FORMAT) * receiverBufferFactor;
    }

    private class ReceiverRunnable implements Runnable {

        @Override
        public void run() {
            short[] prevBuffer = new short[0], curBuffer = new short[receiverBufferSize];
            float[] window = new float[symbolLen];
            while (receiverOn.get()) {
                int read = 0;
                while (receiverOn.get() && read != receiverBufferSize) {
                    int result = receiver.read(curBuffer, read, receiverBufferSize);
                    if (result < 0)
                        throw new RuntimeException("Error when reading audio: " +
                                getBufferReadFailureReason(result) + ")");
                    read += result;
                }
                if (!receiverOn.get())
                    break;
                short[] totalBuffer = new short[prevBuffer.length + curBuffer.length];
                System.arraycopy(prevBuffer, 0, totalBuffer, 0, prevBuffer.length);
                System.arraycopy(curBuffer, 0, totalBuffer, prevBuffer.length, curBuffer.length);
                int bufferOffset = 0;
                while (bufferOffset + symbolLen < totalBuffer.length) {
                    for (int i = 0; i < symbolLen; ++i)
                        window[i] = totalBuffer[bufferOffset + i] / SHORT_MAX;
                    processWindow(window);
                    bufferOffset += symbolLen;
                }
                prevBuffer = Arrays.copyOfRange(totalBuffer, bufferOffset, totalBuffer.length);
            }
        }

        @SuppressLint("DefaultLocale")
        private void processWindow(float[] window) {
            if (previousWindow == null) {
                previousWindow = window;
                return;
            }
            float[] sigReceiveWindow = new float[symbolLen * 2];
            System.arraycopy(previousWindow, 0, sigReceiveWindow, 0, symbolLen);
            System.arraycopy(window, 0, sigReceiveWindow, symbolLen, symbolLen);
            boolean skipBuffer = false;
            if (!started) {
                Box<float[]> startCor = new Box<>();
                Box<int[]> startLags = new Box<>();
                SignalProcessing.xcorr(sigReceiveWindow, startPreambleSymbol, startCor, startLags);
                Box<Float> startCorMax = new Box<>();
                Box<Integer> index = new Box<>();
                SignalProcessing.max(startCor.value, startCorMax, index);
                int startLag = startLags.value[index.value];
                if (startCorMax.value > startEndThreshold && 0 <= startLag && startLag < symbolLen) {
                    starts.add(startLag);
                    runOnUiThread(() -> {
                        log(String.format("startCorMax: %f index: %d", startCorMax.value, startLag));
                    });
                    if (starts.size() == startPreambleNum) {
                        float mean = SignalProcessing.mean(starts);
                        float var = SignalProcessing.variance(starts, mean);
                        runOnUiThread(() -> {
                            log(String.format("startCandidate mean: %f var: %f", mean, var));
                        });
                        if (var <= lagVarianceLimit) {
                            int roundMean = Math.round(mean);
                            runOnUiThread(() -> {
                                log(String.format("started: %d", roundMean));
                            });
                            for (int i = roundMean; i < symbolLen; ++i)
                                signalBuffer.add(sigReceiveWindow[i]);
                            started = true;
                            skipBuffer = true;
                        }
                    } else if (starts.size() > startPreambleNum) {
                        starts.pop();
                    }
                } else {
                    starts = new ArrayDeque<>();
                }
            } else {
                Box<float[]> endCor = new Box<>();
                Box<int[]> endLags = new Box<>();
                SignalProcessing.xcorr(sigReceiveWindow, endPreambleSymbol, endCor, endLags);
                Box<Float> endCorMax = new Box<>();
                Box<Integer> index = new Box<>();
                SignalProcessing.max(endCor.value, endCorMax, index);
                int endLag = endLags.value[index.value];
                if (endCorMax.value > startEndThreshold && 0 < endLag && endLag <= symbolLen) {
                    ends.add(endLag);
                    runOnUiThread(() -> {
                        log(String.format("endCorMax: %f index: %d", endCorMax.value, endLag));
                    });
                    if (ends.size() == endPreambleNum) {
                        float mean = SignalProcessing.mean(ends);
                        float var = SignalProcessing.variance(ends, mean);
                        runOnUiThread(() -> {
                            log(String.format("endCandidate mean: %f var: %f", mean, var));
                        });
                        if (var <= lagVarianceLimit) {
                            int roundMean = Math.round(mean);
                            runOnUiThread(() -> {
                                log(String.format("ended: %d", roundMean));
                            });
                            for (int i = 0; i < roundMean; ++i)
                                signalBuffer.add(sigReceiveWindow[i]);
                            int length = signalBuffer.size() - endPreambleNum * symbolLen;
                            if (length > 0) {
                                float[] signal = new float[length];
                                for (int i = 0; i < length; ++i) {
                                    signal[i] = signalBuffer.get(i + symbolLen);
                                }
                                processSignalBuffer(signal);
                            }
                            signalBuffer = new ArrayList<>();
                            started = false;
                        }
                    } else if (ends.size() > endPreambleNum) {
                        ends.pop();
                    }
                } else {
                    ends = new ArrayDeque<>();
                }
                if (started) {
                    for (int i = 0; i < symbolLen; ++i)
                        signalBuffer.add(sigReceiveWindow[i]);
                    if (signalBuffer.size() > signalBufferLenLimit) {
                        signalBuffer = new ArrayList<>();
                        started = false;
                    }
                }
            }
            previousWindow = window;
            plotView.setTimeData(window);
            plotView.postInvalidate();
        }

        @SuppressLint("DefaultLocale")
        private void processSignalBuffer(float[] signalBuffer) {
            int realSignalLen = signalBuffer.length;
            int symbolNum = Math.round((float) realSignalLen / realSymbolLen);
            int expectedSignalLen = symbolNum * realSymbolLen;
            runOnUiThread(() -> {
                log(String.format("real sig len: %d expected sig len: %d",
                        realSignalLen, expectedSignalLen));
            });
            if (realSignalLen == expectedSignalLen) {
                processReceivedSignal(signalBuffer, symbolNum);
                return;
            }
            float[] receivedSignal = new float[expectedSignalLen];
            if (realSignalLen < expectedSignalLen) {
                int paddingLeft = (int) Math.round(Math.floor(
                        (float) (expectedSignalLen - realSignalLen) / 2));
                System.arraycopy(signalBuffer, 0, receivedSignal, paddingLeft, realSignalLen);
            } else {
                int clipLeft = (int) Math.round(Math.floor(
                        (float) (realSignalLen - expectedSignalLen) / 2));
                System.arraycopy(signalBuffer, clipLeft, receivedSignal, 0, expectedSignalLen);
            }
            processReceivedSignal(receivedSignal, symbolNum);
        }

        @SuppressLint("DefaultLocale")
        private void processReceivedSignal(float[] receivedSignal, int symbolNum) {
            // Multiply carrier wave to extract signal
            float[] realReceivedSignal = new float[receivedSignal.length];
            float[] imagReceivedSignal = new float[receivedSignal.length];
            for (int i = 0; i < receivedSignal.length; ++i) {
                float t = i / sampleFreq;
                float x = (float) (2 * Math.PI * carrierFreq * t);
                realReceivedSignal[i] = receivedSignal[i] * (float) Math.cos(x);
                imagReceivedSignal[i] = receivedSignal[i] * (float) -Math.sin(x);
            }
            // Reshape to matrix
            float[][] realReceivedFullMatrix = new float[symbolNum][];
            float[][] imagReceivedFullMatrix = new float[symbolNum][];
            for (int i = 0; i < symbolNum; ++i) {
                realReceivedFullMatrix[i] = new float[symbolLen];
                imagReceivedFullMatrix[i] = new float[symbolLen];
                System.arraycopy(realReceivedSignal, i * realSymbolLen + cyclicPrefixLen,
                        realReceivedFullMatrix[i], 0, symbolLen);
                System.arraycopy(imagReceivedSignal, i * realSymbolLen + cyclicPrefixLen,
                        imagReceivedFullMatrix[i], 0, symbolLen);
            }
            // FFT
            float[][] realFFTData = new float[symbolNum][];
            float[][] imagFFTData = new float[symbolNum][];
            for (int i = 0; i < symbolNum; ++i) {
                FFT.fft(realReceivedFullMatrix[i], imagReceivedFullMatrix[i]);
                realFFTData[i] = new float[subcarrierNum];
                imagFFTData[i] = new float[subcarrierNum];
                System.arraycopy(realReceivedFullMatrix[i], 0, realFFTData[i], 0, subcarrierNum);
                System.arraycopy(imagReceivedFullMatrix[i], 0, imagFFTData[i], 0, subcarrierNum);
            }
            int dataNum = dataSubcarrierNum * symbolNum;
            int pilotNum = pilotSubcarrierNum * symbolNum;
            // Reshape to serial and split data and pilot
            float[] realReceivedSerialData = new float[dataNum];
            float[] imagReceivedSerialData = new float[dataNum];
            float[] realReceivedSerialPilot = new float[pilotNum];
            float[] imagReceivedSerialPilot = new float[pilotNum];
            for (int i = 0; i < symbolNum; ++i) {
                for (int j = 0; j < dataSubcarrierNum; ++j) {
                    realReceivedSerialData[i * dataSubcarrierNum + j] =
                            realFFTData[i][dataSubcarrierIndices[j]];
                    imagReceivedSerialData[i * dataSubcarrierNum + j] =
                            imagFFTData[i][dataSubcarrierIndices[j]];
                }
                for (int j = 0; j < pilotSubcarrierNum; ++j) {
                    realReceivedSerialPilot[i * pilotSubcarrierNum + j] =
                            realFFTData[i][pilotSubcarrierIndices[j]];
                    imagReceivedSerialPilot[i * pilotSubcarrierNum + j] =
                            imagFFTData[i][pilotSubcarrierIndices[j]];
                }
            }
            runOnUiThread(() -> {
                StringBuilder str = new StringBuilder();
                for (int i = 0; i < dataNum; ++i) {
                    str.append(String.format("%f %f,", realReceivedSerialData[i],
                            imagReceivedSerialData[i]));
                }
                log(String.format("serial data: %s", str.toString()));
            });
            // Calculate signal shift
            Box<float[]> realQPSKModulatedPilot = new Box<>();
            Box<float[]> imagQPSKModulatedPilot = new Box<>();
            generatePilot(pilotNum, realQPSKModulatedPilot, imagQPSKModulatedPilot);
            float[] realDelta = new float[pilotNum];
            float[] imagDelta = new float[pilotNum];
            // delta = QPSKModulatedPilot / receivedSerialPilot
            for (int i = 0; i < pilotNum; ++i) {
                float factor = 1 / (realReceivedSerialPilot[i] * realReceivedSerialPilot[i] +
                        imagReceivedSerialPilot[i] * imagReceivedSerialPilot[i]);
                realDelta[i] = factor * (realQPSKModulatedPilot.value[i] * realReceivedSerialPilot[i] +
                        imagQPSKModulatedPilot.value[i] * imagReceivedSerialPilot[i]);
                imagDelta[i] = factor * (imagQPSKModulatedPilot.value[i] * realReceivedSerialPilot[i] -
                        realQPSKModulatedPilot.value[i] * imagReceivedSerialPilot[i]);
            }
            float realMeanDelta = SignalProcessing.mean(realDelta);
            float imagMeanDelta = SignalProcessing.mean(imagDelta);
            runOnUiThread(() -> {
                log(String.format("mean delta: %f %f", realMeanDelta, imagMeanDelta));
            });
            // Recover signal
            float[] realReceivedSerialDataCorrected = new float[dataNum];
            float[] imagReceivedSerialDataCorrected = new float[dataNum];
            // receivedSerialDataCorrected = receivedSerialData * meanDelta
            for (int i = 0; i < dataNum; ++i) {
                realReceivedSerialDataCorrected[i] = realReceivedSerialData[i] * realMeanDelta -
                        imagReceivedSerialData[i] * imagMeanDelta;
                imagReceivedSerialDataCorrected[i] = realReceivedSerialData[i] * imagMeanDelta +
                        imagReceivedSerialData[i] * realMeanDelta;
            }
            // Demodulate
            int[] qpskDemodulatedData = SignalProcessing.qpskDemodulate(
                    realReceivedSerialDataCorrected, imagReceivedSerialDataCorrected);
            processData(qpskDemodulatedData);
        }

        private void processData(int[] data) {
            runOnUiThread(() -> {
                StringBuilder str = new StringBuilder();
                for (int i: data) {
                    str.append(i);
                    str.append(' ');
                }
                log(String.format("data: %s", str.toString()));
            });
        }

        private void generatePilot(int length, Box<float[]> real, Box<float[]> imag) {
            // Use zero
            int[] input = new int[length];
            SignalProcessing.qpskModulate(input, real, imag);
        }

        private String getBufferReadFailureReason(int errorCode) {
            switch (errorCode) {
                case AudioRecord.ERROR_INVALID_OPERATION:
                    return "ERROR_INVALID_OPERATION";
                case AudioRecord.ERROR_BAD_VALUE:
                    return "ERROR_BAD_VALUE";
                case AudioRecord.ERROR_DEAD_OBJECT:
                    return "ERROR_DEAD_OBJECT";
                case AudioRecord.ERROR:
                    return "ERROR";
                default:
                    return "Unknown (" + errorCode + ")";
            }
        }
    }

}
