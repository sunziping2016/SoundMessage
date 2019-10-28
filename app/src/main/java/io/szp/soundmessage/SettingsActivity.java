package io.szp.soundmessage;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
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
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private SharedPreferences preferences;

        private EditTextPreference subcarrierNumPreference;
        private EditTextPreference pilotSubcarrierNumPreference;
        private EditTextPreference symbolLenPreference;
        private EditTextPreference cyclicPrefixFactorPreference;

        private EditTextPreference sampleFreqPreference;
        private EditTextPreference carrierFreqPreference;
        private EditTextPreference preambleLowFreqPreference;
        private EditTextPreference preambleHighFreqPreference;
        private EditTextPreference startPreambleNumPreference;
        private EditTextPreference endPreambleNumPreference;

        private EditTextPreference startEndThresholdPreference;
        private EditTextPreference lagVarianceLimitPreference;
        private EditTextPreference symbolNumLimitPreference;

        @SuppressWarnings({"LambdaCanBeReplacedWithAnonymous", "CodeBlock2Expr"})
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            FragmentActivity activity = getActivity();
            if (activity == null)
                throw new AssertionError("Expected activity");
            preferences = PreferenceManager.getDefaultSharedPreferences(activity);
            preferences.registerOnSharedPreferenceChangeListener(this);

            subcarrierNumPreference = findPreference(getString(R.string.subcarrier_num_key));
            pilotSubcarrierNumPreference = findPreference(
                    getString(R.string.pilot_subcarrier_num_key));
            symbolLenPreference = findPreference(getString(R.string.symbol_len_key));
            cyclicPrefixFactorPreference = findPreference(
                    getString(R.string.cyclic_prefix_factor_key));

            sampleFreqPreference = findPreference(getString(R.string.sample_freq_key));
            carrierFreqPreference = findPreference(getString(R.string.carrier_freq_key));
            preambleLowFreqPreference = findPreference(getString(R.string.preamble_low_freq_key));
            preambleHighFreqPreference = findPreference(getString(R.string.preamble_high_freq_key));
            startPreambleNumPreference = findPreference(getString(R.string.start_preamble_num_key));
            endPreambleNumPreference = findPreference(getString(R.string.end_preamble_num_key));

            startEndThresholdPreference = findPreference(
                    getString(R.string.start_end_threshold_key));
            lagVarianceLimitPreference = findPreference(getString(R.string.lag_variance_limit_key));
            symbolNumLimitPreference = findPreference(getString(R.string.symbol_num_limit_key));

            boolean receiverEnabled = preferences.getBoolean(
                    getString(R.string.receiver_enabled_key), true);
            subcarrierNumPreference.setEnabled(!receiverEnabled);
            pilotSubcarrierNumPreference.setEnabled(!receiverEnabled);
            symbolLenPreference.setEnabled(!receiverEnabled);
            cyclicPrefixFactorPreference.setEnabled(!receiverEnabled);
            sampleFreqPreference.setEnabled(!receiverEnabled);
            carrierFreqPreference.setEnabled(!receiverEnabled);
            preambleLowFreqPreference.setEnabled(!receiverEnabled);
            preambleHighFreqPreference.setEnabled(!receiverEnabled);
            startPreambleNumPreference.setEnabled(!receiverEnabled);
            endPreambleNumPreference.setEnabled(!receiverEnabled);
            startEndThresholdPreference.setEnabled(!receiverEnabled);
            lagVarianceLimitPreference.setEnabled(!receiverEnabled);
            symbolNumLimitPreference.setEnabled(!receiverEnabled);

            subcarrierNumPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            });
            pilotSubcarrierNumPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            });
            symbolLenPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            });
            cyclicPrefixFactorPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL);
            });
            sampleFreqPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL);
            });
            carrierFreqPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL);
            });
            preambleLowFreqPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL);
            });
            preambleHighFreqPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL);
            });
            startPreambleNumPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            });
            endPreambleNumPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            });
            startEndThresholdPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL);
            });
            lagVarianceLimitPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL);
            });
            symbolNumLimitPreference.setOnBindEditTextListener((EditText editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            });
        }

        @Override
        public void onDestroy() {
            preferences.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }

        // Default value
        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            if (key.equals(getString(R.string.receiver_enabled_key))) {
                boolean enabled = preferences.getBoolean(key, true);
                subcarrierNumPreference.setEnabled(!enabled);
                pilotSubcarrierNumPreference.setEnabled(!enabled);
                symbolLenPreference.setEnabled(!enabled);
                cyclicPrefixFactorPreference.setEnabled(!enabled);
                sampleFreqPreference.setEnabled(!enabled);
                carrierFreqPreference.setEnabled(!enabled);
                preambleLowFreqPreference.setEnabled(!enabled);
                preambleHighFreqPreference.setEnabled(!enabled);
                startPreambleNumPreference.setEnabled(!enabled);
                endPreambleNumPreference.setEnabled(!enabled);
                startEndThresholdPreference.setEnabled(!enabled);
                lagVarianceLimitPreference.setEnabled(!enabled);
                symbolNumLimitPreference.setEnabled(!enabled);
            } else if (key.equals(getString(R.string.subcarrier_num_key))) {
                subcarrierNumPreference.setText(preferences.getString(key, "10"));
            } else if (key.equals(getString(R.string.pilot_subcarrier_num_key))) {
                pilotSubcarrierNumPreference.setText(preferences.getString(key, "2"));
            } else if (key.equals(getString(R.string.symbol_len_key))) {
                symbolLenPreference.setText(preferences.getString(key, "2048"));
            } else if (key.equals(getString(R.string.cyclic_prefix_factor_key))) {
                cyclicPrefixFactorPreference.setText(preferences.getString(key, "0.1"));
            } else if (key.equals(getString(R.string.sample_freq_key))) {
                sampleFreqPreference.setText(preferences.getString(key, "44100"));
            } else if (key.equals(getString(R.string.carrier_freq_key))) {
                carrierFreqPreference.setText(preferences.getString(key, "16000"));
            } else if (key.equals(getString(R.string.preamble_low_freq_key))) {
                preambleLowFreqPreference.setText(preferences.getString(key, "8000"));
            } else if (key.equals(getString(R.string.preamble_high_freq_key))) {
                preambleHighFreqPreference.setText(preferences.getString(key, "16000"));
            } else if (key.equals(getString(R.string.start_preamble_num_key))) {
                startPreambleNumPreference.setText(preferences.getString(key, "3"));
            } else if (key.equals(getString(R.string.end_preamble_num_key))) {
                endPreambleNumPreference.setText(preferences.getString(key, "3"));
            } else if (key.equals(getString(R.string.start_end_threshold_key))) {
                startEndThresholdPreference.setText(preferences.getString(key, "10"));
            } else if (key.equals(getString(R.string.lag_variance_limit_key))) {
                lagVarianceLimitPreference.setText(preferences.getString(key, "10"));
            } else if (key.equals(getString(R.string.symbol_num_limit_key))) {
                symbolNumLimitPreference.setText(preferences.getString(key, "16"));
            }
//            else if (key.equals(getString(R.string.subcarrier_num_key))) {
//                // Write back when changed by MainActivity
//                receiverBufferFactor.setText(preferences.getString(key, "2"));
//            }
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