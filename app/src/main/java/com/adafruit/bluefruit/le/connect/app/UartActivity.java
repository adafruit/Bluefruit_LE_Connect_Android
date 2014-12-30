package com.adafruit.bluefruit.le.connect.app;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleServiceListener;

import java.nio.charset.Charset;


public class UartActivity extends UartInterfaceActivity implements BleServiceListener {
    // Log
    private final static String TAG = UartActivity.class.getSimpleName();

    // Constants
    private final static String kPreferences = "UartActivity_prefs";
    private final static String kPreferences_eol = "eol";
    private final static String kPreferences_echo = "echo";
    private final static String kPreferences_asciiMode = "ascii";

    // UI
    private Switch mEchoSwitch;
    private Switch mEolSwitch;
    private EditText mBufferTextView;
    private EditText mSendEditText;

    // Data
    private boolean mShowDataInHexFormat;

    private SpannableStringBuilder mAsciiSpanBuffer = new SpannableStringBuilder();
    private SpannableStringBuilder mHexSpanBuffer = new SpannableStringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uart);

        mBleManager = BleManager.getInstance(this);

        // Choose UI controls component based on available width
        {
            LinearLayout headerLayout = (LinearLayout) findViewById(R.id.headerLayout);
            ViewGroup controlsLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.layout_uart_singleline_controls, headerLayout, false);
            controlsLayout.measure(0, 0);
            int controlWidth = controlsLayout.getMeasuredWidth();

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int rootWidth = size.x;

            if (controlWidth > rootWidth)       // control too big, use a smaller version
            {
                controlsLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.layout_uart_multiline_controls, headerLayout, false);
            }
            Log.d(TAG, "width: " + controlWidth + " baseWidth: " + rootWidth);

            headerLayout.addView(controlsLayout);
        }

        // Read preferences
        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
        final boolean echo = preferences.getBoolean(kPreferences_echo, true);
        final boolean eol = preferences.getBoolean(kPreferences_eol, true);
        final boolean asciiMode = preferences.getBoolean(kPreferences_asciiMode, true);

        // UI
        mEchoSwitch = (Switch) findViewById(R.id.echoSwitch);
        mEchoSwitch.setChecked(echo);
        mEolSwitch = (Switch) findViewById(R.id.eolSwitch);
        mEolSwitch.setChecked(eol);

        RadioButton asciiFormatRadioButton = (RadioButton)findViewById(R.id.asciiFormatRadioButton);
        asciiFormatRadioButton.setChecked(asciiMode);
        RadioButton hexFormatRadioButton = (RadioButton)findViewById(R.id.hexFormatRadioButton);
        hexFormatRadioButton.setChecked(!asciiMode);
        mShowDataInHexFormat = !asciiMode;

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
        mSendEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    // Dismiss keyboard when sendEditText loses focus
                    dismissKeyboard(view);
                }
            }
        });


        mBufferTextView = (EditText) findViewById(R.id.bufferTextView);
        mBufferTextView.setKeyListener(null);     // make it not editable

        // Continue
        onServicesDiscovered();
    }

    @Override
    public void onPause()
    {
        super.onPause();

        // Save preferences
        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(kPreferences_echo, mEchoSwitch.isChecked());
        editor.putBoolean(kPreferences_eol, mEolSwitch.isChecked());
        editor.putBoolean(kPreferences_asciiMode, !mShowDataInHexFormat);

        editor.commit();
    }

    public void dismissKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public void onClickSend(View view) {
        String data = mSendEditText.getText().toString();
        mSendEditText.setText("");       // Clear editText

        if (mEolSwitch.isChecked()) {
            // Add newline character if checked
            data += "\n";
        }
        sendData(data);

        if (mEchoSwitch.isChecked()) {      // Add send data to visible buffer if checked
            addTextToSpanBuffer(mAsciiSpanBuffer, data, Color.BLUE);
            addTextToSpanBuffer(mHexSpanBuffer, asciiToHex(data), Color.BLUE);
        }

        updateUI();
    }

    public void onClickCopy(View view) {
        String text = mShowDataInHexFormat?mHexSpanBuffer.toString():mAsciiSpanBuffer.toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("UART", text);
        clipboard.setPrimaryClip(clip);
    }

    public void onClickClear(View view) {
        mAsciiSpanBuffer.clear();
        mHexSpanBuffer.clear();
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

                addTextToSpanBuffer(mAsciiSpanBuffer, data, Color.RED);
                addTextToSpanBuffer(mHexSpanBuffer, asciiToHex(data), Color.RED);

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

    private void addTextToSpanBuffer(SpannableStringBuilder spanBuffer, String text, int color) {
        final int from = spanBuffer.length();
        spanBuffer.append(text);
        spanBuffer.setSpan(new ForegroundColorSpan(color), from, from + text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }


    private void updateUI() {

        mBufferTextView.setText(mShowDataInHexFormat?mHexSpanBuffer:mAsciiSpanBuffer);
        mBufferTextView.setSelection(0, mBufferTextView.getText().length());        // to automatically scroll to the end
    }

    private String asciiToHex(String text)
    {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < text.length(); i++) {
            String charString = String.format("0x%02X", (byte) text.charAt(i));

            stringBuffer.append(charString + " ");
        }
        return stringBuffer.toString();
    }
}
