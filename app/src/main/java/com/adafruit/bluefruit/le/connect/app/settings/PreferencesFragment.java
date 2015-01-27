package com.adafruit.bluefruit.le.connect.app.settings;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.adafruit.bluefruit.le.connect.BuildConfig;
import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.update.FirmwareUpdater;


public class PreferencesFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // Set updateserver
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String updateServer = sharedPreferences.getString("pref_updateserver", FirmwareUpdater.kDefaultUpdateServerUrl);

        EditTextPreference etp = (EditTextPreference) findPreference("pref_updateserver");
        etp.setSummary(updateServer);
        etp.setText(updateServer);

        // Hide advanced options (if not debug)
        if (!BuildConfig.DEBUG) {
            PreferenceCategory category = (PreferenceCategory) findPreference(getResources().getString(R.string.pref_key_update_settings));
            Preference updateServerPreference = findPreference(getResources().getString(R.string.pref_updateserver));
            category.removePreference(updateServerPreference);

            Preference versionCheckPreference = findPreference(getResources().getString(R.string.pref_updatesversioncheck));
            category.removePreference(versionCheckPreference);

        }
    }

}
