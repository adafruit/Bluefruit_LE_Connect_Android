package com.adafruit.bluefruit.le.connect.app.neopixel;

import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.CommonHelpActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class NeopixelHelpActivity extends CommonHelpActivity {

    // Constants
    private static final String AUTHORITY = "com.adafruit.bluefruit.le.connect.fileprovider";          // Same as the authority field on the manifest provider

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_neopixelhelp);

        setupHelp();
    }

    public void onClickExportSketch(View view) {
        exportSketch();
    }

    private void exportSketch() {

        // Copy file from assets to FilesDir
        String filename = "Neopixel_Arduino.zip";
        File file = new File(getFilesDir(), filename);
        AssetManager assets = getResources().getAssets();
        try {
            copy(assets.open("neopixel" + File.separator + filename), file);
        } catch (IOException e) {
            Log.e("FileProvider", "Exception copying from assets", e);
        }

        // Export uri
        Uri uri = FileProvider.getUriForFile(this, AUTHORITY, file);

        if (uri != null) {
            Intent intentShareFile = new Intent(Intent.ACTION_SEND);
            intentShareFile.setType("application/zip");

            intentShareFile.putExtra(Intent.EXTRA_STREAM, uri);

            intentShareFile.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.neopixel_help_export_subject));
            intentShareFile.putExtra(Intent.EXTRA_TEXT, getString(R.string.neopixel_help_export_text));
            intentShareFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);        // to avoid the android.os.FileUriExposedException on api 24+
            startActivity(Intent.createChooser(intentShareFile, getString(R.string.neopixel_help_export_chooser_title)));
        }
    }

    private static void copy(InputStream in, File dst) throws IOException {
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[1024];
        int len;

        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }

        in.close();
        out.close();
    }
}
