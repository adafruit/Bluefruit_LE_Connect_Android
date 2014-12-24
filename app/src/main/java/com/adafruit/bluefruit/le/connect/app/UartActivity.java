package com.adafruit.bluefruit.le.connect.app;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleServiceListener;

import java.nio.charset.Charset;


public class UartActivity extends UartInterfaceActivity implements BleServiceListener {
    // Log
    private final static String TAG = UartActivity.class.getSimpleName();

    // UI
    Switch mEchoSwitch;
    private EditText mReceivedTextView;
    private EditText mSendEditText;

    // Data
    private boolean mShowDataInHexFormat = false;

    private SpannableStringBuilder mSpanBuffer = new SpannableStringBuilder();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uart);

        mBleManager = BleManager.getInstance(this);

        // UI
        mEchoSwitch = (Switch) findViewById(R.id.echoSwitch);

        mSendEditText = (EditText) findViewById(R.id.sendEditText);
        mSendEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    onClickSend(null);
                    return true;
                }

                return false;
            }
        });
        mReceivedTextView = (EditText) findViewById(R.id.receivedTextView);
        mReceivedTextView.setKeyListener(null);     // make it not editable

        onServicesDiscovered();
    }

    public void onClickSend(View view) {
        String data = mSendEditText.getText().toString();
        sendData(data);
        mSendEditText.setText("");       // Clear editText

        if (mEchoSwitch.isChecked()) {
            int from = mSpanBuffer.length();
            mSpanBuffer.append(data);
            mSpanBuffer.setSpan(new ForegroundColorSpan(Color.BLUE), from, from + data.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

//            mTextBuffer += data;
        }

        updateUI();
    }

    public void onClickCopy(View view) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("UART", mSpanBuffer.toString());
        clipboard.setPrimaryClip(clip);
    }

    public void onClickClear(View view) {
        mSpanBuffer.clear();
        updateUI();
    }

    public void onClickFormatAscii(View view) {
        mShowDataInHexFormat = false;
        updateUI();
    }

    public void onClickFormatHex(View view) {
        mShowDataInHexFormat = true;
        updateUI();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Setup listeners
        mBleManager.setBleListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_uart, menu);
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
        // Launch app hep activity
        Intent intent = new Intent(this, CommonHelpActivity.class);
        intent.putExtra("title", getString(R.string.uart_help_title));
        intent.putExtra("help", "uart_help.html");
        startActivity(intent);
    }

    // region BleServiceListener
    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "Disconnected. Back to previous activity");
        finish();
    }

    @Override
    public void onServicesDiscovered() {
        mUartService = mBleManager.getGattService(UUID_SERVICE);

        mBleManager.enableService(mUartService, UUID_RX, true);
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        // UART RX
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                String data = new String(characteristic.getValue(), Charset.forName("UTF-8"));

                //mTextBuffer += data;
                int from = mSpanBuffer.length();
                mSpanBuffer.append(data);
                // Log.d(TAG, "from: " + from + " data:" + data + " total: " + mSpanBuffer.length());
                mSpanBuffer.setSpan(new ForegroundColorSpan(Color.RED), from, from + data.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateUI();
                    }
                });
            }
        }
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    // endregion

    private void updateUI() {

        SpannableStringBuilder finalSpanBuffer;
        if (mShowDataInHexFormat) {
            finalSpanBuffer = new SpannableStringBuilder();
            String text = mSpanBuffer.toString();
            ForegroundColorSpan[] colorSpans = mSpanBuffer.getSpans(0, text.length(), ForegroundColorSpan.class);

            for (int i = 0; i < text.length(); i++) {
                String charString = String.format("0x%02X", (byte) text.charAt(i));

                finalSpanBuffer.append(charString + " ");
//                finalSpanBuffer.setSpan(new ForegroundColorSpan(Color.BLUE), from, from+data.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            }
        } else {
            finalSpanBuffer = mSpanBuffer;
        }

        mReceivedTextView.setText(finalSpanBuffer);

        mReceivedTextView.setSelection(0, mReceivedTextView.getText().length());        // to automatically scroll to the end
    }
}
