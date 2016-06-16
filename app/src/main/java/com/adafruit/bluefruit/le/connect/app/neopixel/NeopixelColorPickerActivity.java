package com.adafruit.bluefruit.le.connect.app.neopixel;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;

public class NeopixelColorPickerActivity extends AppCompatActivity implements ColorPicker.OnColorChangedListener {

    // Result return
    public static final String kActivityParameter_SelectedColorKey = "kActivityParameter_SelectedColorKey";
    public static final String kActivityResult_SelectedColorResultKey = "kActivityResult_SelectedColorResultKey";

    // UI
    private ColorPicker mColorPicker;
    private View mRgbColorView;
    private TextView mRgbTextView;

    private int mSelectedColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_neopixel_colorpicker);
        setContentView(R.layout.activity_color_picker);

        Intent intent = getIntent();
        mSelectedColor = intent.getIntExtra(kActivityParameter_SelectedColorKey, Color.WHITE);

        // UI
        mRgbColorView = findViewById(R.id.rgbColorView);
        mRgbTextView = (TextView) findViewById(R.id.rgbTextView);

        SaturationBar mSaturationBar = (SaturationBar) findViewById(R.id.saturationbar);
        ValueBar mValueBar = (ValueBar) findViewById(R.id.valuebar);
        mColorPicker = (ColorPicker) findViewById(R.id.colorPicker);
        if (mColorPicker != null) {
            mColorPicker.addSaturationBar(mSaturationBar);
            mColorPicker.addValueBar(mValueBar);
            mColorPicker.setOnColorChangedListener(this);

            mColorPicker.setOldCenterColor(mSelectedColor);
            mColorPicker.setColor(mSelectedColor);
        }
        onColorChanged(mSelectedColor);

        Button sendButton = (Button) findViewById(R.id.sendButton);
        sendButton.setText(R.string.neopixel_colorpicker_setcolor);

    }

    @Override
    public void onColorChanged(int color) {
        // Save selected color
        mSelectedColor = color;

        // Update UI
        mRgbColorView.setBackgroundColor(color);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = (color >> 0) & 0xFF;
        String text = String.format(getString(R.string.colorpicker_rgbformat), r, g, b);
        mRgbTextView.setText(text);

    }

    public void onClickSend(View view) {
        Intent output = new Intent();
        output.putExtra(kActivityResult_SelectedColorResultKey, mSelectedColor);
        setResult(RESULT_OK, output);
        finish();
    }
}
