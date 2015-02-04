package com.adafruit.bluefruit.le.connect.app;

import android.app.AlertDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ui.keyboard.CustomEditTextFormatter;
import com.adafruit.bluefruit.le.connect.ui.keyboard.CustomKeyboard;

import java.nio.charset.Charset;
import java.util.Random;

public class BeaconActivity extends UartInterfaceActivity implements BleManager.BleManagerListener {
    // Log
    private final static String TAG = BeaconActivity.class.getSimpleName();

    // UI
    private EditText mVendorEditText;
    private EditText mUuidEditText;
    private EditText mMajorEditText;
    private EditText mMinorEditText;
    private EditText mRssiEditText;

    // Keyboard
    private CustomKeyboard mCustomKeyboard;

    // Data
    private int mRssi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beacon);

        // Get params
        Intent intent = getIntent();
        mRssi = intent.getIntExtra("rssi", 0);

        // Ble
        mBleManager = BleManager.getInstance(this);

        // UI
        mVendorEditText = (EditText)findViewById(R.id.vendorEditText);
        mUuidEditText = (EditText)findViewById(R.id.uuidEditText);
        mMajorEditText = (EditText)findViewById(R.id.majorEditText);
        mMinorEditText = (EditText)findViewById(R.id.minorEditText);
        mRssiEditText = (EditText)findViewById(R.id.rssiEditText);

        // Custom keyboard
        if (mCustomKeyboard == null) {
            mCustomKeyboard = new CustomKeyboard(this);
        }

        mCustomKeyboard.attachToEditText(mVendorEditText, R.xml.keyboard_hexadecimal);
        CustomEditTextFormatter.attachToEditText(mVendorEditText, 4, "", 4);

        mCustomKeyboard.attachToEditText(mUuidEditText, R.xml.keyboard_hexadecimal);
        CustomEditTextFormatter.attachToEditText(mUuidEditText, 32, "-", 2);

        mCustomKeyboard.attachToEditText(mMajorEditText, R.xml.keyboard_hexadecimal);
        CustomEditTextFormatter.attachToEditText(mMajorEditText, 4, "", 4);

        mCustomKeyboard.attachToEditText(mMinorEditText, R.xml.keyboard_hexadecimal);
        CustomEditTextFormatter.attachToEditText(mMinorEditText, 4, "", 4);

        mCustomKeyboard.attachToEditText(mRssiEditText, R.xml.keyboard_decimal);
        CustomEditTextFormatter.attachToEditText(mRssiEditText, 3, "", 3);

        // Generate initial state
        String manufacturers[] = getResources().getStringArray(R.array.beacon_manufacturers_ids);
        String manufacturerId = manufacturers[1];
        mVendorEditText.setText(manufacturerId);
        onClickRandomUuid(null);
        mMajorEditText.setText("0000");
        mMinorEditText.setText("0000");
        mRssiEditText.setText(""+mRssi);

        // Start services
        onServicesDiscovered();

    }

    @Override
    public void onResume() {
        super.onResume();

        // Setup listeners
        mBleManager.setBleListener(this);
    }


    @Override
    public void onBackPressed() {
        if (mCustomKeyboard.isCustomKeyboardVisible()) {
            mCustomKeyboard.hideCustomKeyboard();
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        dismissKeyboard();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_beacon, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public void testATParser() {
        String uartCommand = "AT\\r\\n";
        Log.d(TAG, "send command: "+uartCommand);
        sendData(uartCommand);

    }

    public void onClickEnable(View view) {
        String manufacturerId = "0x"+mVendorEditText.getText().toString();
        String uuid = mUuidEditText.getText().toString();
        String major = "0x"+mMajorEditText.getText().toString();
        String minor = "0x"+mMinorEditText.getText().toString();
        String rssi = mRssiEditText.getText().toString();

        String uartCommand = String.format("+++\r\nAT+BLEBEACON=%s,%s,%s,%s,%s\r\n+++\r\n", manufacturerId, uuid, major, minor, rssi);
        Log.d(TAG, "send command: "+uartCommand);
        sendData(uartCommand);
    }

    public void onClickDisable(View view) {
        String uartCommand = "+++\r\nAT+FACTORYRESET\r\n+++\r\n";
        Log.d(TAG, "send command: "+uartCommand);
        sendData(uartCommand);

    }

    public void onClickChooseVendorId(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.beacon_manufacturer_choose_title)
                .setItems(R.array.beacon_manufacturers_names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String manufacturers[] = getResources().getStringArray(R.array.beacon_manufacturers_ids);
                        String manufacturerId = manufacturers[which];
                        mVendorEditText.setText(manufacturerId);
                    }
                });
        builder.create().show();
    }

    public void onClickRandomUuid(View view) {

        final String kAllowedChars ="0123456789ABCDEF";
        final int kNumChars = 32;

            final Random random = new Random();
            final StringBuilder randomString = new StringBuilder(kNumChars);
            for(int i = 0; i < kNumChars; i++) {
                randomString.append(kAllowedChars.charAt(random.nextInt(kAllowedChars.length())));
            }

        String result = CustomEditTextFormatter.formatText(randomString.toString(), 32, "-", 2);
        mUuidEditText.setText(result);
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
        mBleManager.enableService(mUartService, UUID_RX, true);

        // Test AT parser
        testATParser();
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        // UART RX
        Log.d(TAG, "onDataAvailable");
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                String data = new String(characteristic.getValue(), Charset.forName("UTF-8"));
                Log.d(TAG, "received: "+data);
            }
        }
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }
    // endregion

    private void dismissKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        if (imm.isAcceptingText()) { // verify if the soft keyboard is open
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
}
