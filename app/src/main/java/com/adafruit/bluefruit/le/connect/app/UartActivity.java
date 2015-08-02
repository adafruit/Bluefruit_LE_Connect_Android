package com.adafruit.bluefruit.le.connect.app;

import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
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
import com.adafruit.bluefruit.le.connect.app.settings.ConnectedSettingsActivity;
import com.adafruit.bluefruit.le.connect.mqtt.MqttManager;
import com.adafruit.bluefruit.le.connect.mqtt.MqttSettings;
import com.adafruit.bluefruit.le.connect.app.settings.MqttUartSettingsActivity;
import com.adafruit.bluefruit.le.connect.ble.BleManager;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.Charset;


public class UartActivity extends UartInterfaceActivity implements BleManager.BleManagerListener, MqttManager.MqttManagerListener {
    // Log
    private final static String TAG = UartActivity.class.getSimpleName();

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_ConnectedSettingsActivity = 0;
    private static final int kActivityRequestCode_MqttSettingsActivity = 1;

    // Constants
    private final static String kPreferences = "UartActivity_prefs";
    private final static String kPreferences_eol = "eol";
    private final static String kPreferences_echo = "echo";
    private final static String kPreferences_asciiMode = "ascii";

    private int mTxColor;
    private int mRxColor;
    private int mMqttSubscribedColor;

    // UI
    private Switch mEchoSwitch;
    private Switch mEolSwitch;
    private EditText mBufferTextView;
    private EditText mSendEditText;
    private MenuItem mMqttMenuItem;
    private Handler mMqttMenuItemAnimationHandler;

    // Data
    private boolean mShowDataInHexFormat;

    private SpannableStringBuilder mAsciiSpanBuffer;
    private SpannableStringBuilder mHexSpanBuffer;

    private DataFragment mRetainedDataFragment;

    private MqttManager mMqttManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uart);

        mBleManager = BleManager.getInstance(this);
        restoreRetainedDataFragment();

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
            //Log.d(TAG, "width: " + controlWidth + " baseWidth: " + rootWidth);

            headerLayout.addView(controlsLayout);
        }

        // Get default theme colors
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = getTheme();
        theme.resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        mTxColor = typedValue.data;
        theme.resolveAttribute(R.attr.colorControlActivated, typedValue, true);
        mRxColor= typedValue.data;

        //theme.resolveAttribute(R.attr.colorControlHighlight, typedValue, true);
        //mMqttSubscribedColor = typedValue.data;
        mMqttSubscribedColor = Color.parseColor("#555555");

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

        RadioButton asciiFormatRadioButton = (RadioButton) findViewById(R.id.asciiFormatRadioButton);
        asciiFormatRadioButton.setChecked(asciiMode);
        RadioButton hexFormatRadioButton = (RadioButton) findViewById(R.id.hexFormatRadioButton);
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

        // Mqtt init
        mMqttManager = MqttManager.getInstance(this);
        if (MqttSettings.getInstance(this).isConnected()) {
            mMqttManager.connectFromSavedSettings(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Setup listeners
        mBleManager.setBleListener(this);

        mMqttManager.setListener(this);
        updateMqttStatus();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Save preferences
        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(kPreferences_echo, mEchoSwitch.isChecked());
        editor.putBoolean(kPreferences_eol, mEolSwitch.isChecked());
        editor.putBoolean(kPreferences_asciiMode, !mShowDataInHexFormat);

        editor.commit();
    }

    @Override
    public void onDestroy() {
        // Disconnect mqtt
        if (mMqttManager != null) {
            mMqttManager.disconnect();
        }

        // Retain data
        saveRetainedDataFragment();

        super.onDestroy();
    }

    public void dismissKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public void onClickSend(View view) {
        String data = mSendEditText.getText().toString();
        mSendEditText.setText("");       // Clear editText

        uartSendData(data, false);
    }

    private void uartSendData(String data, boolean wasReceivedFromMqtt)
    {
        // MQTT publish to TX
        MqttSettings settings = MqttSettings.getInstance(UartActivity.this);
        if (!wasReceivedFromMqtt) {
            if (settings.isPublishEnabled()) {
                String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_TX);
                final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_TX);
                mMqttManager.publish(topic, data, qos);
            }
        }

        // Add eol
        if (mEolSwitch.isChecked()) {
            // Add newline character if checked
            data += "\n";
        }

        // Send to uart
        if (!wasReceivedFromMqtt || settings.getSubscribeBehaviour() == MqttSettings.kSubscribeBehaviour_Transmit) {
            sendData(data);
        }

        // Show on UI
        if (mEchoSwitch.isChecked()) {      // Add send data to visible buffer if checked
            int color = wasReceivedFromMqtt?mMqttSubscribedColor:mTxColor;       // mTxColor for standard input or mqttsubscribedcolor when is something that should not be published to mqtt (it has been received from a mqqt subscribed feed=
            addTextToSpanBuffer(mAsciiSpanBuffer, data, color);
            addTextToSpanBuffer(mHexSpanBuffer, asciiToHex(data), color);
        }

        updateUI();
    }

    public void onClickCopy(View view) {
        String text = mShowDataInHexFormat ? mHexSpanBuffer.toString() : mAsciiSpanBuffer.toString();
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


    // region Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_uart, menu);

        mMqttMenuItem = menu.findItem(R.id.action_mqttsettings);
        mMqttMenuItemAnimationHandler = new Handler();
        mMqttMenuItemAnimationRunnable.run();

        return true;
    }

    private Runnable mMqttMenuItemAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            updateMqttStatus();
            mMqttMenuItemAnimationHandler.postDelayed(mMqttMenuItemAnimationRunnable, 500);
        }
    };
    private  int mMqttMenuItemAnimationFrame = 0;

    private void updateMqttStatus() {
        if (mMqttMenuItem == null) return;      // Hack: Sometimes this could have not been initialized so we don't update icons

        MqttManager mqttManager = mMqttManager.getInstance(this);
        MqttManager.MqqtConnectionStatus status = mqttManager.getClientStatus();

        if (status == MqttManager.MqqtConnectionStatus.CONNECTING)
        {
            final int kConnectingAnimationDrawableIds[] = {R.drawable.mqtt_connecting1, R.drawable.mqtt_connecting2, R.drawable.mqtt_connecting3};
            mMqttMenuItem.setIcon(kConnectingAnimationDrawableIds[mMqttMenuItemAnimationFrame]);
            mMqttMenuItemAnimationFrame = (mMqttMenuItemAnimationFrame+1)%kConnectingAnimationDrawableIds.length;
        }
        else if (status == MqttManager.MqqtConnectionStatus.CONNECTED) {
            mMqttMenuItem.setIcon(R.drawable.mqtt_connected);
            mMqttMenuItemAnimationHandler.removeCallbacks(mMqttMenuItemAnimationRunnable);
        }
        else
        {
            mMqttMenuItem.setIcon(R.drawable.mqtt_disconnected);
            mMqttMenuItemAnimationHandler.removeCallbacks(mMqttMenuItemAnimationRunnable);
        }

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
        else if (id == R.id.action_connected_settings) {
            startConnectedSettings();
            return true;
        }
        else if (id == R.id.action_refreshcache)  {
            if (mBleManager != null ) {
                mBleManager.refreshDeviceCache();
            }
        }
        else if (id == R.id.action_mqttsettings)  {
            Intent intent = new Intent(this, MqttUartSettingsActivity.class);
            startActivityForResult(intent, kActivityRequestCode_MqttSettingsActivity);
        }


        return super.onOptionsItemSelected(item);
    }

    private void startConnectedSettings() {
        // Launch connected settings activity
        Intent intent = new Intent(this, ConnectedSettingsActivity.class);
        startActivityForResult(intent, kActivityRequestCode_ConnectedSettingsActivity);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == kActivityRequestCode_ConnectedSettingsActivity && resultCode == RESULT_OK) {
            finish();
        }
        else if (requestCode == kActivityRequestCode_MqttSettingsActivity && resultCode == RESULT_OK) {

        }
    }
    private void startHelp() {
        // Launch app help activity
        Intent intent = new Intent(this, CommonHelpActivity.class);
        intent.putExtra("title", getString(R.string.uart_help_title));
        intent.putExtra("help", "uart_help.html");
        startActivity(intent);
    }
    // endregion

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
        finish();
    }

    @Override
    public void onServicesDiscovered() {
        mUartService = mBleManager.getGattService(UUID_SERVICE);

        mBleManager.enableNotification(mUartService, UUID_RX, true);
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        // UART RX
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                final String data = new String(characteristic.getValue(), Charset.forName("UTF-8"));

                addTextToSpanBuffer(mAsciiSpanBuffer, data, mRxColor);
                addTextToSpanBuffer(mHexSpanBuffer, asciiToHex(data), mRxColor);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateUI();

                        // MQTT publish to RX
                        MqttSettings settings = MqttSettings.getInstance(UartActivity.this);
                        if (settings.isPublishEnabled()) {
                            String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_RX);
                            final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_RX);
                            mMqttManager.publish(topic, data, qos);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }

    // endregion

    private void addTextToSpanBuffer(SpannableStringBuilder spanBuffer, String text, int color) {
        final int from = spanBuffer.length();
        spanBuffer.append(text);
        spanBuffer.setSpan(new ForegroundColorSpan(color), from, from + text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void updateUI() {
        mBufferTextView.setText(mShowDataInHexFormat ? mHexSpanBuffer : mAsciiSpanBuffer);
        mBufferTextView.setSelection(0, mBufferTextView.getText().length());        // to automatically scroll to the end
    }

    private String asciiToHex(String text) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < text.length(); i++) {
            String charString = String.format("0x%02X", (byte) text.charAt(i));

            stringBuffer.append(charString + " ");
        }
        return stringBuffer.toString();
    }


    // region DataFragment
    public static class DataFragment extends Fragment {
        private boolean mShowDataInHexFormat;
        private SpannableStringBuilder mAsciiSpanBuffer;
        private SpannableStringBuilder mHexSpanBuffer;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }
    }

    private void restoreRetainedDataFragment() {
        // find the retained fragment
        FragmentManager fm = getFragmentManager();
        mRetainedDataFragment = (DataFragment) fm.findFragmentByTag(TAG);

        if (mRetainedDataFragment == null) {
            // Create
            mRetainedDataFragment = new DataFragment();
            fm.beginTransaction().add(mRetainedDataFragment, TAG).commit();

            mAsciiSpanBuffer = new SpannableStringBuilder();
            mHexSpanBuffer = new SpannableStringBuilder();
        } else {
            // Restore status
            mShowDataInHexFormat = mRetainedDataFragment.mShowDataInHexFormat;
            mAsciiSpanBuffer = mRetainedDataFragment.mAsciiSpanBuffer;
            mHexSpanBuffer = mRetainedDataFragment.mHexSpanBuffer;
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mShowDataInHexFormat = mShowDataInHexFormat;
        mRetainedDataFragment.mAsciiSpanBuffer = mAsciiSpanBuffer;
        mRetainedDataFragment.mHexSpanBuffer = mHexSpanBuffer;
    }
    // endregion



    // region MqttManagerListener

    @Override
    public void onMqttConnected() {
        updateMqttStatus();
    }

    @Override
    public void onMqttDisconnected() {
        updateMqttStatus();
    }

    @Override
    public void onMqttMessageArrived(String topic, MqttMessage mqttMessage) {
        final String message = new String(mqttMessage.getPayload());

        //Log.d(TAG, "Mqtt messageArrived from topic: " +topic+ " message: "+message);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                uartSendData(message, true);       // Don't republish to mqtt something received from mqtt
            }
        });

    }

    // endregion
}
