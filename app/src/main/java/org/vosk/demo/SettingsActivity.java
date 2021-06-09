package org.vosk.demo;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import org.vosk.api.LanguageModelDefinition;
import org.vosk.api.VoskHelperAndroid;

import java.util.Collection;

public class SettingsActivity extends AppCompatActivity {
    public static final String KEY_LANG_PREF_SWITCH = "example_switch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements org.vosk.demo.SettingsFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            ListPreference localePreference = findPreference("lang");
            Collection<LanguageModelDefinition> languages = VoskHelperAndroid.getLanguages(getContext()).values();
            localePreference.setEntries(languages.stream().map(l -> l.getLocaleName()).toArray(CharSequence[]::new));
            localePreference.setEntryValues(languages.stream().map(l -> l.getId()).toArray(CharSequence[]::new));
        }
    }

}