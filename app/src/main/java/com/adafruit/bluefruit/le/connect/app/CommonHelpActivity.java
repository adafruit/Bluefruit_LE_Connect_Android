package com.adafruit.bluefruit.le.connect.app;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.webkit.WebView;

import com.adafruit.bluefruit.le.connect.R;

public class CommonHelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_commonhelp);

        setupHelp();
    }

    protected void setupHelp() {
        // Title
        String title = getIntent().getExtras().getString("title");
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }

        // Text
        String asset = getIntent().getExtras().getString("help");
        WebView infoWebView = (WebView) findViewById(R.id.infoWebView);
        if (infoWebView != null) {
            infoWebView.setBackgroundColor(Color.TRANSPARENT);
            infoWebView.loadUrl("file:///android_asset/help/" + asset);
        }
    }
}
