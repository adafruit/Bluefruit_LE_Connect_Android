package com.adafruit.bluefruit.le.connect.app.settings;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;


public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferencesFragment()).commit();
    }

}
