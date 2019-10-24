package io.szp.soundmessage;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private boolean receiverEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        receiverEnabled = preferences.getBoolean(getString(R.string.receiver_enabled_key), true);
        preferences.registerOnSharedPreferenceChangeListener(this);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.settingsButton) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("SetTextI18n")
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
            receiverEnabled = preferences.getBoolean(
                    getString(R.string.receiver_enabled_key), true);
        }
    }
}
