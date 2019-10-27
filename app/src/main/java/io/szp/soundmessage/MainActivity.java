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

import java.util.ArrayList;
import java.util.Arrays;
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
    public int symbolLen = 1024;
    public float cyclicPrefixFactor = 0.1f;

    private float sampleFreq = 44100;
    private float carrierFreq = 1000;
    private float preambleLowFreq = 2000;
    private float preambleHighFreq = 4000;
    private int startPreambleNum = 2;
    private int endPreambleNum = 2;

    // Receiver Parameter
    private float startEndThreshold = 10;

    // Computer parameter
    public int dataSubcarrierNum;
    public int[] pilotSubcarrierIndices;
    public int[] dataSubcarrierIndices;
    public int cyclicPrefixLen;
    public int cyclicPrefixStart;
    public int cyclicPrefixEnd;
    public int realSymbolLen;

    private float[] preambleSymbolTime;
    private float[] startPreambleSymbol, endPreambleSymbol;

    // Receiver data
    private float[] previousWindow;
    private List<Float> signalBuffer;
    private boolean started;
    private List<Integer> starts;
    private List<Integer> ends;

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
        preambleSymbolTime = new float[symbolLen];
        float sampleTime = 1 / sampleFreq;
        for (int i = 0; i < symbolLen; ++i)
            preambleSymbolTime[i] = i * sampleTime;
        startPreambleSymbol = SignalProcessing.chirp(preambleLowFreq, preambleHighFreq, preambleSymbolTime);
        endPreambleSymbol = SignalProcessing.chirp(preambleHighFreq, preambleLowFreq, preambleSymbolTime);
        signalBuffer = new ArrayList<>();
        started = false;
        starts = new ArrayList<>();
        ends = new ArrayList<>();
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
            Box<float[]> startCor = new Box<>();
            Box<int []> startLags = new Box<>();
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
                    int mean = Math.round(SignalProcessing.mean(starts));
                    runOnUiThread(() -> {
                        log(String.format("started: %d", mean));
                    });
                    for (int i = mean; i < symbolLen; ++i)
                        signalBuffer.add(sigReceiveWindow[i]);
                    started = true;
                    skipBuffer = true;
                }
            } else {
                starts = new ArrayList<>();
            }
            Box<float[]> endCor = new Box<>();
            Box<int []> endLags = new Box<>();
            SignalProcessing.xcorr(sigReceiveWindow, endPreambleSymbol, endCor, endLags);
            Box<Float> endCorMax = new Box<>();
            SignalProcessing.max(endCor.value, endCorMax, index);
            int endLag = endLags.value[index.value];
            if (endCorMax.value > startEndThreshold && 0 < endLag && endLag <= symbolLen) {
                ends.add(endLag);
                runOnUiThread(() -> {
                    log(String.format("endCorMax: %f index: %d", endCorMax.value, endLag));
                });
                if (ends.size() == endPreambleNum) {
                    int mean = Math.round(SignalProcessing.mean(ends));
                    runOnUiThread(() -> {
                        log(String.format("ended: %d", mean));
                    });
                    for (int i = 0; i < mean; ++i)
                        signalBuffer.add(sigReceiveWindow[i]);
                    int length = signalBuffer.size() - endPreambleNum * symbolLen;
                    if (length > 0) {
                        float[] signal = new float[length];
                        for (int i = 0; i < length; ++i) {
                            signal[i] = signalBuffer.get(i + symbolLen);
                        }
                        processSignal(signal);
                    }
                    signalBuffer = new ArrayList<>();
                    started = false;
                }
            } else {
                ends = new ArrayList<>();
            }
            if (started && !skipBuffer) {
                for (int i = 0; i < symbolLen; ++i)
                    signalBuffer.add(sigReceiveWindow[i]);
            }
            previousWindow = window;
            //plotView.setTimeData(window);
            plotView.setFrequencyData(startCor.value);
            // plotView.setFrequencyData(freqAbs);
            plotView.postInvalidate();
        }

        @SuppressLint("DefaultLocale")
        private void processSignal(float[] signal) {
            runOnUiThread(() -> {
                log(String.format("signal length: %d", signal.length));
            });
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
