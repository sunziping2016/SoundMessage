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
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
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
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int SAMPLING_RATE_IN_HZ = 44100;
    private static final int CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_IN_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_OUT_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final float SHORT_MAX = 32768;
    private static final String START_SEND_TEXT = "sendText";
    private static final String START_CONTENT_TEXT = "contentText";
    private static final String[] LOG_LEVEL_STRINGS = new String[] {
            "error", "warn", "info", "debug"
    };
    private enum LogLevel { ERROR, WARN, INFO, DEBUG };
    private static final float SOUND_AMPLIFIER = 200;

    // Default value
    // UI Parameter
    public static AtomicInteger logLevel = new AtomicInteger(0);
    public static AtomicBoolean drawTime = new AtomicBoolean(false);
    public static AtomicBoolean drawStartXcorr = new AtomicBoolean(false);
    public static AtomicBoolean drawEndXcorr = new AtomicBoolean(false);

    // Common Parameter
    public int pskModulate = 4;
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
    private float lagStdevLimit = 10;
    private int symbolNumLimit = 16;

    // Sender Parameter
    private float spaceFactor = 0;

    // Computed parameter
    public int bits;
    public SignalProcessing.PSK pskMethod;
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

    // Sender data
    private final Object sendBufferMutex = new Object();
    private float[] sendBuffer = new float[0];

    // Other fields
    private boolean permissionToRecordAccepted = false;
    private int receiverBufferSize;
    private int senderBufferSize;
    private boolean receiverEnabled;

    private AudioRecord receiver;
    private AtomicBoolean receiverOn = new AtomicBoolean(false);

    private AtomicBoolean senderOn = new AtomicBoolean(false);

    EditText sendText;
    TextView contentText;
    private PlotView plotView;

    // Default value
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO_PERMISSION);
        setContentView(R.layout.activity_main);
        sendText = findViewById(R.id.sendText);
        contentText = findViewById(R.id.contentText);
        if (savedInstanceState != null) {
            sendText.setText(savedInstanceState.getString(START_SEND_TEXT, ""));
            contentText.setText(savedInstanceState.getString(START_CONTENT_TEXT, ""));
        }
        plotView = findViewById(R.id.plotView);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        logLevel.set(findInStringArray(LOG_LEVEL_STRINGS, preferences.getString(
                getString(R.string.log_level_key), "error")));
        if (logLevel.get() == -1)
            throw new AssertionError("Unknown log level");
        drawTime.set(preferences.getBoolean(getString(R.string.draw_time_enabled_key), false));
        drawStartXcorr.set(preferences.getBoolean(
                getString(R.string.draw_start_xcorr_enabled_key), false));
        drawEndXcorr.set(preferences.getBoolean(
                getString(R.string.draw_end_xcorr_enabled_key), false));
        pskModulate = Integer.parseInt(preferences.getString(
                getString(R.string.psk_modulate_key), "4"));
        subcarrierNum = Integer.parseInt(preferences.getString(
                getString(R.string.subcarrier_num_key), "10"));
        pilotSubcarrierNum = Integer.parseInt(preferences.getString(
                getString(R.string.pilot_subcarrier_num_key), "2"));
        symbolLen = Integer.parseInt(preferences.getString(
                getString(R.string.symbol_len_key), "2048"));
        cyclicPrefixFactor = Float.parseFloat(preferences.getString(
                getString(R.string.cyclic_prefix_factor_key), "0.1"));
        sampleFreq = Float.parseFloat(preferences.getString(
                getString(R.string.sample_freq_key), "44100"));
        carrierFreq = Float.parseFloat(preferences.getString(
                getString(R.string.carrier_freq_key), "16000"));
        preambleLowFreq = Float.parseFloat(preferences.getString(
                getString(R.string.preamble_low_freq_key), "8000"));
        preambleHighFreq = Float.parseFloat(preferences.getString(
                getString(R.string.preamble_high_freq_key), "16000"));
        startPreambleNum = Integer.parseInt(preferences.getString(
                getString(R.string.start_preamble_num_key), "3"));
        endPreambleNum = Integer.parseInt(preferences.getString(
                getString(R.string.end_preamble_num_key), "3"));
        startEndThreshold = Float.parseFloat(preferences.getString(
                getString(R.string.start_end_threshold_key), "10"));
        lagStdevLimit = Float.parseFloat(preferences.getString(
                getString(R.string.lag_stdev_limit_key), "10"));
        symbolNumLimit = Integer.parseInt(preferences.getString(
                getString(R.string.symbol_num_limit_key), "16"));
        spaceFactor = Float.parseFloat(preferences.getString(
                getString(R.string.space_factor_key), "0"));
        updateUIParameter();
        updateBufferSize();
        updateReceiverParameter();
        updateSenderParameter();
        setReceiverEnabled(preferences.getBoolean(getString(R.string.receiver_enabled_key), true));
    }

    @Override
    protected void onDestroy() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(START_SEND_TEXT, sendText.getText().toString());
        outState.putString(START_CONTENT_TEXT, contentText.getText().toString());
        super.onSaveInstanceState(outState);
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
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.length > 0) {
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
        } else if (id == R.id.clearButton) {
            contentText.setText("");
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("SetTextI18n")
    public void log(String text) {
        contentText.setText(contentText.getText().toString() + text + '\n');
    }

    public void onSendButtonClick(View view) {
        sendText.getText().clear();
        sendData(new int[] {1,0,1,1,1,3,1,3});
    }

    // Default value
    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        Log.d("MainActivity", key + " changed");
        if (key.equals(getString(R.string.receiver_enabled_key))) {
            setReceiverEnabled(preferences.getBoolean(key, true));
        } else if (key.equals(getString(R.string.log_level_key))) {
            logLevel.set(findInStringArray(LOG_LEVEL_STRINGS, preferences.getString(key, "error")));
            if (logLevel.get() == -1)
                throw new AssertionError("Unknown log level");
        } else if (key.equals(getString(R.string.draw_time_enabled_key))) {
            drawTime.set(preferences.getBoolean(key, false));
            updateUIParameter();
        } else if (key.equals(getString(R.string.draw_start_xcorr_enabled_key))) {
            drawStartXcorr.set(preferences.getBoolean(key, false));
            updateUIParameter();
        } else if (key.equals(getString(R.string.draw_end_xcorr_enabled_key))) {
            drawEndXcorr.set(preferences.getBoolean(key, false));
            updateUIParameter();
        } else if (key.equals(getString(R.string.psk_modulate_key))) {
            pskModulate = Integer.parseInt(preferences.getString(key, "4"));
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.subcarrier_num_key))) {
            boolean commitBack = false;
            try {
                int newSubcarrierNum = Integer.parseInt(preferences.getString(key, "10"));
                if (newSubcarrierNum <= pilotSubcarrierNum) {
                    subcarrierNum = pilotSubcarrierNum + 1;
                    commitBack = true;
                } else if (newSubcarrierNum > symbolLen) {
                    subcarrierNum = symbolLen;
                    commitBack = true;
                } else {
                    subcarrierNum = newSubcarrierNum;
                }
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(subcarrierNum));
                editor.apply();
            }
            updateReceiverParameter();
        } else if(key.equals(getString(R.string.pilot_subcarrier_num_key))) {
            boolean commitBack = false;
            try {
                int newPilotSubcarrierNum = Integer.parseInt(preferences.getString(key, "2"));
                if (newPilotSubcarrierNum < 1) {
                    pilotSubcarrierNum = 1;
                    commitBack = true;
                } else if (newPilotSubcarrierNum >= subcarrierNum) {
                    pilotSubcarrierNum = subcarrierNum - 1;
                    commitBack = true;
                } else {
                    pilotSubcarrierNum = newPilotSubcarrierNum;
                }
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(pilotSubcarrierNum));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.symbol_len_key))) {
            boolean commitBack = false;
            try {
                int newSymbolLen = Integer.parseInt(preferences.getString(key, "2048"));
                if (newSymbolLen < subcarrierNum) {
                    symbolLen = subcarrierNum;
                    commitBack = true;
                } else {
                    symbolLen = newSymbolLen;
                }
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(symbolLen));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.cyclic_prefix_factor_key))) {
            boolean commitBack = false;
            try {
                float newCyclicPrefixFactor = Float.parseFloat(preferences.getString(key, "0.1"));
                if (newCyclicPrefixFactor < 0) {
                    cyclicPrefixFactor = 0;
                    commitBack = true;
                } else if (newCyclicPrefixFactor > 1) {
                    cyclicPrefixFactor = 1;
                    commitBack = true;
                } else {
                    cyclicPrefixFactor = newCyclicPrefixFactor;
                }
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(cyclicPrefixFactor));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.sample_freq_key))) {
            boolean commitBack = false;
            try {
                sampleFreq = Float.parseFloat(preferences.getString(key, "44100"));
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(sampleFreq));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.carrier_freq_key))) {
            boolean commitBack = false;
            try {
                carrierFreq = Float.parseFloat(preferences.getString(key, "16000"));
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(carrierFreq));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.preamble_low_freq_key))) {
            boolean commitBack = false;
            try {
                preambleLowFreq = Float.parseFloat(preferences.getString(key, "8000"));
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(preambleLowFreq));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.preamble_high_freq_key))) {
            boolean commitBack = false;
            try {
                preambleHighFreq = Float.parseFloat(preferences.getString(key, "16000"));
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(preambleHighFreq));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.start_preamble_num_key))) {
            boolean commitBack = false;
            try {
                int newStartPreambleNum = Integer.parseInt(preferences.getString(key, "3"));
                if (newStartPreambleNum < 1) {
                    startPreambleNum = 1;
                    commitBack = true;
                } else {
                    startPreambleNum = newStartPreambleNum;
                }
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(startPreambleNum));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.end_preamble_num_key))) {
            boolean commitBack = false;
            try {
                int newEndPreambleNum = Integer.parseInt(preferences.getString(key, "3"));
                if (newEndPreambleNum < 1) {
                    endPreambleNum = 1;
                    commitBack = true;
                } else {
                    endPreambleNum = newEndPreambleNum;
                }
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(endPreambleNum));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.start_end_threshold_key))) {
            boolean commitBack = false;
            try {
                startEndThreshold = Float.parseFloat(preferences.getString(key, "10"));
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(startEndThreshold));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.lag_stdev_limit_key))) {
            boolean commitBack = false;
            try {
                lagStdevLimit = Float.parseFloat(preferences.getString(key, "10"));
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(lagStdevLimit));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.symbol_num_limit_key))) {
            boolean commitBack = false;
            try {
                symbolNumLimit = Integer.parseInt(preferences.getString(key, "16"));
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(symbolNumLimit));
                editor.apply();
            }
            updateReceiverParameter();
        } else if (key.equals(getString(R.string.space_factor_key))) {
            boolean commitBack = false;
            try {
                spaceFactor = Float.parseFloat(preferences.getString(key, "0"));
            } catch (NumberFormatException e) {
                commitBack = true;
            }
            if (commitBack) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(key, String.valueOf(spaceFactor));
                editor.apply();
            }
            updateSenderParameter();
        }
    }

    protected void setReceiverEnabled(boolean enabled) {
        if (receiverEnabled == enabled)
            return;
        receiverEnabled = enabled;
        if (enabled) {
            receiver = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ,
                    CHANNEL_IN_CONFIG, AUDIO_IN_FORMAT, receiverBufferSize);
            receiver.startRecording();
            receiverOn.set(true);
            Thread receiverThread = new Thread(new ReceiverRunnable(), "Receiver Thread");
            receiverThread.start();
        } else {
            receiverOn.set(false);
            receiver.stop();
            receiver.release();
            receiver = null;
        }
    }

    protected void sendData(int[] dataInput) {
        int dataNum = dataInput.length;
        int symbolNum = dataNum / dataSubcarrierNum;
        if (dataNum != symbolNum * dataSubcarrierNum)
            throw new AssertionError("Wrong data input size");
        int pilotNum = symbolNum * pilotSubcarrierNum;
        int[] pilotInput = generatePilot(pilotNum);
        Box<float[]> realPSKModulatedData = new Box<>();
        Box<float[]> imagPSKModulatedData = new Box<>();
        pskMethod.modulate(dataInput, realPSKModulatedData, imagPSKModulatedData);
        Box<float[]> realPSKModulatedPilot = new Box<>();
        Box<float[]> imagPSKModulatedPilot = new Box<>();
        pskMethod.modulate(pilotInput, realPSKModulatedPilot, imagPSKModulatedPilot);
        float[][] realDataMatrix = new float[symbolNum][];
        float[][] imagDataMatrix = new float[symbolNum][];
        for (int i = 0; i < symbolNum; ++i) {
            realDataMatrix[i] = new float[dataSubcarrierNum];
            imagDataMatrix[i] = new float[dataSubcarrierNum];
            System.arraycopy(realPSKModulatedData.value, i * dataSubcarrierNum,
                    realDataMatrix[i], 0, dataSubcarrierNum);
            System.arraycopy(imagPSKModulatedData.value, i * dataSubcarrierNum,
                    imagDataMatrix[i], 0, dataSubcarrierNum);
        }
        float[][] realPilotMatrix = new float[symbolNum][];
        float[][] imagPilotMatrix = new float[symbolNum][];
        for (int i = 0; i < symbolNum; ++i) {
            realPilotMatrix[i] = new float[pilotSubcarrierNum];
            imagPilotMatrix[i] = new float[pilotSubcarrierNum];
            System.arraycopy(realPSKModulatedPilot.value, i * pilotSubcarrierNum,
                    realPilotMatrix[i], 0, pilotSubcarrierNum);
            System.arraycopy(imagPSKModulatedPilot.value, i * pilotSubcarrierNum,
                    imagPilotMatrix[i], 0, pilotSubcarrierNum);
        }
        float[][] realFullMatrix = new float[symbolNum][];
        float[][] imagFullMatrix = new float[symbolNum][];
        for (int i = 0; i < symbolNum; ++i) {
            realFullMatrix[i] = new float[symbolLen];
            imagFullMatrix[i] = new float[symbolLen];
        }
        for (int i = 0; i < dataSubcarrierNum; ++i) {
            int subcarrierIndex = dataSubcarrierIndices[i];
            for (int j = 0; j < symbolNum; ++j) {
                realFullMatrix[j][subcarrierIndex] = realDataMatrix[j][i];
                imagFullMatrix[j][subcarrierIndex] = imagDataMatrix[j][i];
            }
        }
        for (int i = 0; i < pilotSubcarrierNum; ++i) {
            int subcarrierIndex = pilotSubcarrierIndices[i];
            for (int j = 0; j < symbolNum; ++j) {
                realFullMatrix[j][subcarrierIndex] = realPilotMatrix[j][i];
                imagFullMatrix[j][subcarrierIndex] = imagPilotMatrix[j][i];
            }
        }
        float[][] realIFFTData = new float[symbolNum][];
        float[][] imagIFFTData = new float[symbolNum][];
        for (int i = 0; i < symbolNum; ++i) {
            FFT.ifft(realFullMatrix[i], imagFullMatrix[i]);
            realIFFTData[i] = new float[realSymbolLen];
            System.arraycopy(realFullMatrix[i], cyclicPrefixStart,
                    realIFFTData[i], 0, cyclicPrefixLen);
            System.arraycopy(realFullMatrix[i], 0, realIFFTData[i], cyclicPrefixLen, symbolLen);
            imagIFFTData[i] = new float[realSymbolLen];
            System.arraycopy(imagFullMatrix[i], cyclicPrefixStart,
                    imagIFFTData[i], 0, cyclicPrefixLen);
            System.arraycopy(imagFullMatrix[i], 0, imagIFFTData[i], cyclicPrefixLen, symbolLen);
        }
        float[] realOFDMSignal = new float[realSymbolLen * symbolNum];
        float[] imagOFDMSignal = new float[realSymbolLen * symbolNum];
        for (int i = 0; i < symbolNum; ++i) {
            System.arraycopy(realIFFTData[i], 0, realOFDMSignal, i * realSymbolLen, realSymbolLen);
            System.arraycopy(imagIFFTData[i], 0, imagOFDMSignal, i * realSymbolLen, realSymbolLen);
        }
        float[] tx = new float[realSymbolLen * symbolNum];
        for (int i = 0; i < realOFDMSignal.length; ++i) {
            float t = i / sampleFreq;
            float x = (float) (2 * Math.PI * carrierFreq * t);
            float realFactor = (float) Math.cos(x);
            float imagFactor = (float) Math.sin(x);
            tx[i] = realOFDMSignal[i] * realFactor - imagOFDMSignal[i] * imagFactor;
        }
        float[] realTx = new float[realSymbolLen * (startPreambleNum + symbolNum + endPreambleNum)];
        for (int i = 9; i < tx.length; ++i)
            realTx[startPreambleNum * realSymbolLen + i] = SOUND_AMPLIFIER * tx[i];
        for (int i = 0; i < startPreambleNum; ++i)
            System.arraycopy(startPreambleSymbol, 0, realTx, i * realSymbolLen, realSymbolLen);
        for (int i = 0; i < endPreambleNum; ++i)
            System.arraycopy(endPreambleSymbol, 0, realTx,
                    (startPreambleNum + symbolNum + i) * realSymbolLen, realSymbolLen);
        int space = Math.round(realSymbolLen * spaceFactor);
        float[] sound = new float[2 * space + realTx.length];
        System.arraycopy(realTx, 0, sound, space, realTx.length);
        sendSound(sound);
    }

    protected void sendSound(float[] sound) {
        synchronized (sendBufferMutex) {
            float[] newSendBuffer = new float[sendBuffer.length + sound.length];
            System.arraycopy(sendBuffer, 0, newSendBuffer, 0, sendBuffer.length);
            System.arraycopy(sound, 0, newSendBuffer, sendBuffer.length, sound.length);
            sendBuffer = newSendBuffer;
        }
        if (!senderOn.get()) {
            Thread senderThread = new Thread(new SenderRunnable(), "Sender Thread");
            senderThread.start();
        }
    }

    private class SenderRunnable implements Runnable {

        @Override
        public void run() {
            AudioTrack sender = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLING_RATE_IN_HZ,
                    CHANNEL_OUT_CONFIG, AUDIO_OUT_FORMAT, senderBufferSize, AudioTrack.MODE_STREAM);
            sender.play();
            while (true) {
                short[] data;
                synchronized (sendBufferMutex) {
                    if (sendBuffer.length == 0) {
                        senderOn.set(false);
                        break;
                    } else {
                        data = new short[sendBuffer.length];
                        for (int i = 0; i < sendBuffer.length; ++i)
                            data[i] = (short) Math.round(sendBuffer[i] * 0.8 * SHORT_MAX);
                        sendBuffer = new float[0];
                    }
                }
                int write = 0;
                while (write != data.length) {
                    int result = sender.write(data, write, data.length);
                    if (result < 0)
                        throw new RuntimeException("Error when writing audio: " +
                                getBufferWriteFailureReason(result) + ")");
                    write += result;
                }

            }
            sender.stop();
            sender.release();
        }

        private String getBufferWriteFailureReason(int errorCode) {
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

    protected void updateUIParameter() {
        if (!drawTime.get())
            plotView.setTimeData(null);
        if (!drawStartXcorr.get())
            plotView.setStartXcorrData(null);
        if (!drawEndXcorr.get())
            plotView.setEndXcorrData(null);
        plotView.invalidate();
    }

    protected void updateReceiverParameter() {
        bits = (int) Math.round(Math.log(pskModulate) / Math.log(2));
        pskMethod = SignalProcessing.getPSK(pskModulate);
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
        float[] preambleSymbolTime = new float[realSymbolLen];
        float sampleTime = 1 / sampleFreq;
        for (int i = 0; i < realSymbolLen; ++i)
            preambleSymbolTime[i] = i * sampleTime;
        startPreambleSymbol = SignalProcessing.chirp(preambleLowFreq, preambleHighFreq, preambleSymbolTime);
        endPreambleSymbol = SignalProcessing.chirp(preambleHighFreq, preambleLowFreq, preambleSymbolTime);
        signalBufferLenLimit = realSymbolLen * symbolNumLimit + endPreambleNum * symbolLen;
        signalBuffer = new ArrayList<>();
        started = false;
        starts = new ArrayDeque<>();
        ends = new ArrayDeque<>();
    }

    protected void updateSenderParameter() {
        // Do nothing
    }

    protected void updateBufferSize() {
        receiverBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
                CHANNEL_IN_CONFIG, AUDIO_IN_FORMAT);
        senderBufferSize = AudioTrack.getMinBufferSize(SAMPLING_RATE_IN_HZ,
                CHANNEL_OUT_CONFIG, AUDIO_IN_FORMAT);
    }

    private class ReceiverRunnable implements Runnable {

        @SuppressWarnings("LambdaCanBeReplacedWithAnonymous")
        private void logOnUiThread(LogLevel level, String content) {
            if (level.ordinal() <= logLevel.get())
                runOnUiThread(() -> log(content));
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void run() {
            short[] prevBuffer = new short[0], curBuffer = new short[receiverBufferSize];
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
                while (bufferOffset + realSymbolLen < totalBuffer.length) {
                    float[] window = new float[realSymbolLen];
                    for (int i = 0; i < realSymbolLen; ++i)
                        window[i] = totalBuffer[bufferOffset + i] / SHORT_MAX;
                    processWindow(window);
                    bufferOffset += realSymbolLen;
                }
                prevBuffer = Arrays.copyOfRange(totalBuffer, bufferOffset, totalBuffer.length);
            }
        }

        @SuppressWarnings({"LambdaCanBeReplacedWithAnonymous"})
        @SuppressLint("DefaultLocale")
        private void processWindow(float[] window) {
            if (previousWindow == null) {
                previousWindow = window;
                return;
            }
            boolean skipBuffer = false;
            float[] sigReceiveWindow = new float[realSymbolLen * 2];
            System.arraycopy(previousWindow, 0, sigReceiveWindow, 0, realSymbolLen);
            System.arraycopy(window, 0, sigReceiveWindow, realSymbolLen, realSymbolLen);
            if (!started) {
                Box<float[]> startCor = new Box<>();
                Box<int[]> startLags = new Box<>();
                SignalProcessing.xcorr(sigReceiveWindow, startPreambleSymbol, startCor, startLags);
                float[] clippedStartCor = new float[realSymbolLen];
                int[] clippedStartLags = new int[realSymbolLen];
                System.arraycopy(startCor.value, startCor.value.length / 2,
                        clippedStartCor, 0, realSymbolLen);
                System.arraycopy(startLags.value, startLags.value.length / 2,
                        clippedStartLags, 0, realSymbolLen);
                Box<Float> startCorMax = new Box<>();
                Box<Integer> index = new Box<>();
                SignalProcessing.max(clippedStartCor, startCorMax, index);
                float value = startCorMax.value / SignalProcessing.meanAbs(clippedStartCor);
                int startLag = clippedStartLags[index.value];
                if (value > startEndThreshold) {
                    starts.add(startLag);
                    if (starts.size() > startPreambleNum) {
                        starts.pop();
                    }
                    logOnUiThread(LogLevel.DEBUG, String.format("D: startCorMax: %f index: %d", value, startLag));
                    if (starts.size() == startPreambleNum) {
                        float mean = SignalProcessing.mean(starts);
                        float dev = SignalProcessing.stdev(starts, mean);
                        logOnUiThread(LogLevel.DEBUG, String.format("D: startCandidate mean: %f dev: %f", mean, dev));
                        if (dev <= lagStdevLimit) {
                            int roundMean = Math.round(mean);
                            logOnUiThread(LogLevel.INFO, String.format("I: started: %d", roundMean));
                            signalBuffer = new ArrayList<>();
                            for (int i = roundMean; i < realSymbolLen; ++i)
                                signalBuffer.add(sigReceiveWindow[i]);
                            started = true;
                            skipBuffer = true;
                            logOnUiThread(LogLevel.DEBUG, String.format("D: start write: %d", realSymbolLen - roundMean));
                        }
                    }
                    if (drawStartXcorr.get()) {
                        plotView.setStartXcorrData(clippedStartCor);
                        plotView.postInvalidate();
                    }
                } else {
                    starts = new ArrayDeque<>();
                }
            } else {
                Box<float[]> endCor = new Box<>();
                Box<int[]> endLags = new Box<>();
                SignalProcessing.xcorr(sigReceiveWindow, endPreambleSymbol, endCor, endLags);
                float[] clippedEndCor = new float[realSymbolLen];
                int[] clippedEndLags = new int[realSymbolLen];
                System.arraycopy(endCor.value, endCor.value.length / 2,
                        clippedEndCor, 0, realSymbolLen);
                System.arraycopy(endLags.value, endLags.value.length / 2,
                        clippedEndLags, 0, realSymbolLen);
                Box<Float> endCorMax = new Box<>();
                Box<Integer> index = new Box<>();
                SignalProcessing.max(clippedEndCor, endCorMax, index);
                float value = endCorMax.value / SignalProcessing.meanAbs(clippedEndCor);
                int endLag = clippedEndLags[index.value];
                if (value > startEndThreshold) {
                    ends.add(endLag);
                    if (ends.size() > endPreambleNum) {
                        ends.pop();
                    }
                    logOnUiThread(LogLevel.DEBUG, String.format("D: endCorMax: %f index: %d", value, endLag));
                    if (ends.size() == endPreambleNum) {
                        float mean = SignalProcessing.mean(ends);
                        float dev = SignalProcessing.stdev(ends, mean);
                        logOnUiThread(LogLevel.DEBUG, String.format("D: endCandidate mean: %f dev: %f", mean, dev));
                        if (dev <= lagStdevLimit) {
                            int roundMean = Math.round(mean);
                            logOnUiThread(LogLevel.INFO, String.format("I: ended: %d", roundMean));
                            for (int i = 0; i < roundMean; ++i)
                                signalBuffer.add(sigReceiveWindow[i]);
                            logOnUiThread(LogLevel.DEBUG, String.format("D: start write: %d", roundMean));
                            int length = signalBuffer.size() - endPreambleNum * realSymbolLen;
                            if (length > 0) {
                                float[] signal = new float[length];
                                for (int i = 0; i < length; ++i) {
                                    signal[i] = signalBuffer.get(i + realSymbolLen);
                                }
                                processSignalBuffer(signal);
                            }
                            signalBuffer = new ArrayList<>();
                            started = false;
                        }
                    }
                    if (drawEndXcorr.get()) {
                        plotView.setEndXcorrData(clippedEndCor);
                        plotView.postInvalidate();
                    }
                } else {
                    ends = new ArrayDeque<>();
                }
            }
            if (started && !skipBuffer) {
                for (int i = 0; i < realSymbolLen; ++i)
                    signalBuffer.add(sigReceiveWindow[i]);
                if (signalBufferLenLimit != 0 && signalBuffer.size() > signalBufferLenLimit) {
                    signalBuffer = new ArrayList<>();
                    started = false;
                    logOnUiThread(LogLevel.WARN, "W: packet too long");
                }
                logOnUiThread(LogLevel.DEBUG, String.format("continue write: %d", realSymbolLen));
            }
            previousWindow = window;
            if (drawTime.get()) {
                plotView.setTimeData(window);
                plotView.postInvalidate();
            }
        }

        @SuppressLint("DefaultLocale")
        private void processSignalBuffer(float[] signalBuffer) {
            int realSignalLen = signalBuffer.length;
            int symbolNum = Math.round((float) realSignalLen / realSymbolLen);
            int expectedSignalLen = symbolNum * realSymbolLen;
            logOnUiThread(LogLevel.DEBUG, String.format("D: real sig len: %d expected sig len: %d",
                        realSignalLen, expectedSignalLen));
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
            // Calculate signal shift
            Box<float[]> realPSKModulatedPilot = new Box<>();
            Box<float[]> imagPSKModulatedPilot = new Box<>();
            int[] pilotInput = generatePilot(pilotNum);
            pskMethod.modulate(pilotInput, realPSKModulatedPilot, imagPSKModulatedPilot);
            float[] realDelta = new float[pilotNum];
            float[] imagDelta = new float[pilotNum];
            // delta = PSKModulatedPilot / receivedSerialPilot
            for (int i = 0; i < pilotNum; ++i) {
                float factor = 1 / (realReceivedSerialPilot[i] * realReceivedSerialPilot[i] +
                        imagReceivedSerialPilot[i] * imagReceivedSerialPilot[i]);
                realDelta[i] = factor * (realPSKModulatedPilot.value[i] * realReceivedSerialPilot[i] +
                        imagPSKModulatedPilot.value[i] * imagReceivedSerialPilot[i]);
                imagDelta[i] = factor * (imagPSKModulatedPilot.value[i] * realReceivedSerialPilot[i] -
                        realPSKModulatedPilot.value[i] * imagReceivedSerialPilot[i]);
            }
            float realMeanDelta = SignalProcessing.mean(realDelta);
            float imagMeanDelta = SignalProcessing.mean(imagDelta);
            logOnUiThread(LogLevel.DEBUG,
                    String.format("D: mean delta: %f %f", realMeanDelta, imagMeanDelta));
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
            int[] pskDemodulatedData = pskMethod.demodulate(
                    realReceivedSerialDataCorrected, imagReceivedSerialDataCorrected);
            processData(pskDemodulatedData);
        }

        private void processData(int[] data) {
            StringBuilder str = new StringBuilder();
            for (int i: data) {
                str.append(i);
                str.append(' ');
            }
            logOnUiThread(LogLevel.WARN, String.format("W: receiver data: %s", str.toString()));
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

    private static int[] generatePilot(int length) {
        // Use zero
        return new int[length];
    }

    @SuppressWarnings("SameParameterValue")
    private static int findInStringArray(String[] array, String value) {
        for (int i = 0; i < array.length; ++i)
            if (array[i].equals(value))
                return i;
        return -1;
    }
}
