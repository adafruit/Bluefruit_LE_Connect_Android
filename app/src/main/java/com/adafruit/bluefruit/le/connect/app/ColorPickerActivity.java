package com.adafruit.bluefruit.le.connect.app;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;

import java.nio.ByteBuffer;

public class ColorPickerActivity extends UartInterfaceActivity implements BleManager.BleManagerListener, ColorPicker.OnColorChangedListener {
    // Log
    private final static String TAG = ColorPickerActivity.class.getSimpleName();

    // Constants
    private final static boolean kPersistValues = true;
    private final static String kPreferences = "ColorPickerActivity_prefs";
    private final static String kPreferences_color = "color";

    private final int kFirstTimeColor = 0xff0000;

    // UI
    private ColorPicker mColorPicker;
    private SaturationBar mSaturationBar;
    private ValueBar mValueBar;
    private View mRgbColorView;
    private TextView mRgbTextView;

    private int mSelectedColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_picker);

        mBleManager = BleManager.getInstance(this);

        // UI
        mRgbColorView = findViewById(R.id.rgbColorView);
        mRgbTextView = (TextView) findViewById(R.id.rgbTextView);

        mSaturationBar = (SaturationBar) findViewById(R.id.saturationbar);
        mValueBar = (ValueBar) findViewById(R.id.valuebar);
        mColorPicker = (ColorPicker) findViewById(R.id.colorPicker);
        mColorPicker.addSaturationBar(mSaturationBar);
        mColorPicker.addValueBar(mValueBar);
        mColorPicker.setOnColorChangedListener(this);

        if (kPersistValues) {
            SharedPreferences preferences = getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            mSelectedColor = preferences.getInt(kPreferences_color, kFirstTimeColor);
        }
        else {
            mSelectedColor = kFirstTimeColor;
        }

        mColorPicker.setOldCenterColor(mSelectedColor);
        mColorPicker.setColor(mSelectedColor);
        onColorChanged(mSelectedColor);

        // Start services
        onServicesDiscovered();
    }

    @Override
    public void onStop() {
        // Preserve values
        if (kPersistValues) {
            SharedPreferences settings = getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(kPreferences_color, mSelectedColor);
            editor.commit();
        }

        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_color_picker, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_help) {
            startHelp();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startHelp() {
        // Launch app help activity
        Intent intent = new Intent(this, CommonHelpActivity.class);
        intent.putExtra("title", getString(R.string.colorpicker_help_title));
        intent.putExtra("help", "colorpicker_help.html");
        startActivity(intent);
    }


    // region BleManagerListener
    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "Disconnected. Back to previous activity");
        setResult(-1);      // Unexpected Disconnect
        finish();
    }

    @Override
    public void onServicesDiscovered() {
        mUartService = mBleManager.getGattService(UUID_SERVICE);
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {

    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }

    // endregion

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
        // Set the old color
        mColorPicker.setOldCenterColor(mSelectedColor);

        // Send selected color !Crgb
        int r = (mSelectedColor >> 16) & 0xFF;
        int g = (mSelectedColor >> 8) & 0xFF;
        int b = (mSelectedColor >> 0) & 0xFF;

        Log.d(TAG, "Send to UART: !C " + r + " " + g + " " + b);

        ByteBuffer buffer = ByteBuffer.allocate(2 + 3 * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);

        // prefix
        String prefix = "!C";
        buffer.put(prefix.getBytes());

        // values
        buffer.putInt(r);
        buffer.putInt(g);
        buffer.putInt(b);

        byte[] result = buffer.array();
        sendData(result);
    }
}
