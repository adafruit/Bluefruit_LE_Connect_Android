package com.adafruit.bluefruit.le.connect.app.neopixel;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.adafruit.bluefruit.le.connect.FileUtils;
import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.CommonHelpActivity;

import java.io.File;

public class NeopixelHelpActivity extends CommonHelpActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_neopixelhelp);

        setupHelp();
    }

    public void onClickExportSketch(View view) {
        String outPath = FileUtils.copyAssetFile(getAssets(), "neopixel" + File.separator + "Neopixel_Arduino.zip", "Neopixel_Arduino.zip");
        if (outPath != null) {
            Intent intentShareFile = new Intent(Intent.ACTION_SEND);
            intentShareFile.setType("application/zip");

            final String fileUrlString = "file://" + outPath;
            //final String fileUrlString = "https://github.com/adafruit/Bluefruit_LE_Connect_v2/releases/download/OSXcommandline_0.3/Bluefruit.CommandLine.dmg";
            //final String fileUrlString = "file:///android_asset/neopixel/Neopixel_Arduino.zip";
            //final String fileUrlString = "content://com.adafruit.bluefruit.le.connect/neopixel/Neopixel_Arduino.zip";
            intentShareFile.putExtra(Intent.EXTRA_STREAM, Uri.parse(fileUrlString));

            intentShareFile.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.neopixel_help_export_subject));
            intentShareFile.putExtra(Intent.EXTRA_TEXT, getString(R.string.neopixel_help_export_text));
            startActivity(Intent.createChooser(intentShareFile, getString(R.string.neopixel_help_export_chooser_title)));
        }
    }
}
