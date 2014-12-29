package com.adafruit.bluefruit.le.connect.app.settings;


import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.adafruit.bluefruit.le.connect.R;


public class PreferencesFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

}
