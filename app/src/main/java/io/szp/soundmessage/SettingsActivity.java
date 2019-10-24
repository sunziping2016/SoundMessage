package io.szp.soundmessage;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment(this))
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private SharedPreferences preferences;
        private EditTextPreference receiverBufferFactor;


        SettingsFragment(AppCompatActivity activity) {
            preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            preferences.registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onDestroy() {
            preferences.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            receiverBufferFactor = (EditTextPreference) findPreference(
                    getString(R.string.receiver_buffer_factor_key));
            boolean receiverEnabled = preferences.getBoolean(
                    getString(R.string.receiver_enabled_key), true);
            receiverBufferFactor.setEnabled(!receiverEnabled);
            receiverBufferFactor.setOnBindEditTextListener(
                    new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                }
            });
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            if (key.equals(getString(R.string.receiver_enabled_key))) {
                boolean enabled = preferences.getBoolean(key, true);
                receiverBufferFactor.setEnabled(!enabled);
            } else if (key.equals(getString(R.string.receiver_buffer_factor_key))) {
                // Write back when changed by MainActivity
                receiverBufferFactor.setText(preferences.getString(key, "2"));
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            return true;
        }
        return false;
    }

}