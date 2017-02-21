package com.adafruit.bluefruit.le.connect.PT;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.adafruit.bluefruit.le.connect.R;

public class PTActivity extends AppCompatActivity {

    private PTActivityFragment mFragment = new PTActivityFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pt);

        mFragment.setArguments(getIntent().getExtras());
        transitionToFragment(mFragment);

    }

    public void transitionToFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.replace(R.id.container, fragment);
        transaction.commit();
    }

}
