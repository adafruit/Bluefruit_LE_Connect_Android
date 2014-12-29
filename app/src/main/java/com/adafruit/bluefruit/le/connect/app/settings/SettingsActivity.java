package com.adafruit.bluefruit.le.connect.app.settings;

import android.app.Activity;
import android.os.Bundle;


public class SettingsActivity extends Activity  {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferencesFragment()).commit();
    }

}
