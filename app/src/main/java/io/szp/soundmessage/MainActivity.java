package io.szp.soundmessage;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import android.Manifest;
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

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private static final int SAMPLING_RATE_IN_HZ = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private boolean permissionToRecordAccepted = false;
    private int receiverBufferFactor;
    private int receiverBufferSize;
    private boolean receiverEnabled;

    private AudioRecord receiver;
    private AtomicBoolean receiverOn = new AtomicBoolean(false);
    private Thread receiverThread;

    private PlotView plotView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO_PERMISSION);
        setContentView(R.layout.activity_main);
        plotView = (PlotView) findViewById(R.id.plotView);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        receiverBufferFactor = Integer.parseInt(preferences.getString(
                getString(R.string.receiver_buffer_factor_key), "2"));
        updateReceiverBufferSize();
        setReceiverEnabled(preferences.getBoolean(getString(R.string.receiver_enabled_key), true));
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

    public void onSendButtonClick(View view) {
        EditText sendText = (EditText) findViewById(R.id.sendText);
        TextView contentText = (TextView) findViewById(R.id.contentText);
        contentText.setText(contentText.getText().toString() +
                sendText.getText().toString() + '\n');
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
            receiverThread = null;
        }
    }

    protected void updateReceiverBufferSize() {
        receiverBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG, AUDIO_FORMAT) * receiverBufferFactor;
    }

    private class ReceiverRunnable implements Runnable {

        @Override
        public void run() {
            short[] buffer = new short[receiverBufferSize];
            while (receiverOn.get()) {
                int offset = 0;
                while (offset != receiverBufferSize && receiverOn.get()) {
                    int result = receiver.read(buffer, offset, receiverBufferSize);
                    if (result < 0)
                        throw new RuntimeException("Reading of audio buffer failed: " +
                                getBufferReadFailureReason(result) + offset + " " + result);
                    offset += result;
                }
                plotView.setTimeData(buffer);
                plotView.postInvalidate();
            }
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
