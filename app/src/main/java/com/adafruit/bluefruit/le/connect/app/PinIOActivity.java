package com.adafruit.bluefruit.le.connect.app;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.BuildConfig;
import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.settings.ConnectedSettingsActivity;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ui.utils.ExpandableHeightExpandableListView;

import java.util.ArrayList;

public class PinIOActivity extends UartInterfaceActivity {
    // Log
    private final static String TAG = UartActivity.class.getSimpleName();

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_ConnectedSettingsActivity = 0;

    // Config
    private static final long CAPABILITY_QUERY_TIMEOUT = 15000;      // in milliseconds

    // Pin Constants
    private static final byte SYSEX_START = (byte) 0xF0;
    private static final byte SYSEX_END = (byte) 0xF7;

    private static final int DEFAULT_PINS_COUNT = 20;
    private static final int FIRST_DIGITAL_PIN = 3;
    private static final int LAST_DIGITAL_PIN = 8;
    private static final int FIRST_ANALOG_PIN = 14;
    private static final int LAST_ANALOG_PIN = 19;

    // Uart
    private static final int kUartStatus_InputOutput = 0;       // Default mode (sending and receiving pin data)
    private static final int kUartStatus_QueryCapabilities = 1;
    private static final int kUartStatus_QueryAnalogMapping = 2;

    private class PinData {
        private static final int kMode_Unknown = 255;
        private static final int kMode_Input = 0;
        private static final int kMode_Output = 1;
        private static final int kMode_Analog = 2;
        private static final int kMode_PWM = 3;
        private static final int kMode_Servo = 4;

        private static final int kDigitalValue_Low = 0;
        private static final int kDigitalValue_High = 1;

        int digitalPinId = -1;
        int analogPinId = -1;
        boolean isDigital;
        boolean isAnalog;
        boolean isPwm;

        int mode = kMode_Input;
        int digitalValue = kDigitalValue_Low;
        int analogValue = 0;

        PinData(int digitalPinId, boolean isDigital, boolean isAnalog, boolean isPwm) {
            this.digitalPinId = digitalPinId;
            this.isDigital = isDigital;
            this.isAnalog = isAnalog;
            this.isPwm = isPwm;
        }
    }

    // UI
    private ExpandableHeightExpandableListView mPinListView;
    private ExpandableListAdapter mPinListAdapter;
    private ScrollView mPinScrollView;
    private AlertDialog mQueryCapabilitiesDialog;

    // Data
    private boolean mIsActivityFirstRun;
    private ArrayList<PinData> mPins = new ArrayList<>();
    private int mUartStatus = kUartStatus_InputOutput;
    private Handler mQueryCapabilitiesTimerHandler;
    private Runnable mQueryCapabilitiesTimerRunnable = new Runnable() {
        @Override
        public void run() {
            cancelQueryCapabilities();
        }
    };

    private DataFragment mRetainedDataFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_io);

        mBleManager = BleManager.getInstance(this);
        restoreRetainedDataFragment();

        // UI
        mPinListView = (ExpandableHeightExpandableListView) findViewById(R.id.pinListView);
        mPinListAdapter = new ExpandableListAdapter();
        mPinListView.setAdapter(mPinListAdapter);
        mPinListView.setExpanded(true);

        mPinScrollView = (ScrollView) findViewById(R.id.pinScrollView);

        mIsActivityFirstRun = savedInstanceState == null;

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
    public void onDestroy() {
        cancelQueryCapabilitiesTimer();

        // Retain data
        saveRetainedDataFragment();

        super.onDestroy();
    }

    // region Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_pin_io, menu);
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
        } else if (id == R.id.action_connected_settings) {
            startConnectedSettings();
            return true;
        } else if (id == R.id.action_refreshcache) {
            if (mBleManager != null) {
                mBleManager.refreshDeviceCache();
            }
        } else if (id == R.id.action_query) {
            reset();
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
        if (resultCode == RESULT_OK && requestCode == kActivityRequestCode_ConnectedSettingsActivity) {
            finish();
        }
    }

    private void startHelp() {
        // Launch app help activity
        Intent intent = new Intent(this, CommonHelpActivity.class);
        intent.putExtra("title", getString(R.string.pinio_help_title));
        intent.putExtra("help", "pinio_help.html");
        startActivity(intent);
    }
    // endregion

    private boolean isQueryingCapabilities() {
        return mUartStatus != kUartStatus_InputOutput;
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();
        Log.d(TAG, "received: " + BleUtils.bytesToHexWithSpaces(data));

        switch (mUartStatus) {
            case kUartStatus_QueryCapabilities:
                receivedQueryCapabilities(data);
                break;
            case kUartStatus_QueryAnalogMapping:
                receivedAnalogMapping(data);
                break;
            default:
                receivedPinState(data);
                break;
        }
    }

    // region Query Capabilities

    private void reset() {
        mUartStatus = kUartStatus_InputOutput;
        mPins.clear();

        mPinListAdapter.notifyDataSetChanged();

        // Reset Firmata
        byte bytes[] = new byte[]{(byte) 0xff};
        sendHexData(bytes);

        startQueryCapabilitiesProcess();
    }

    private ArrayList<Byte> queryCapabilitiesDataBuffer = new ArrayList<>();

    private void queryCapabilities() {
        Log.d(TAG, "queryCapabilities");

        // Set status
        mPins.clear();
        mUartStatus = kUartStatus_QueryCapabilities;
        queryCapabilitiesDataBuffer.clear();

        // Query Capabilities
        byte bytes[] = new byte[]{SYSEX_START, (byte) 0x6B, SYSEX_END};
        sendHexData(bytes);


        mQueryCapabilitiesTimerHandler = new Handler();
        mQueryCapabilitiesTimerHandler.postDelayed(mQueryCapabilitiesTimerRunnable, CAPABILITY_QUERY_TIMEOUT);
    }

    private void receivedQueryCapabilities(byte[] data) {
        // Read received packet
        for (final byte dataByte : data) {
            queryCapabilitiesDataBuffer.add(dataByte);
            if (dataByte == SYSEX_END) {
                Log.d(TAG, "Finished receiving capabilities");
                queryAnalogMapping();
                break;
            }
        }
    }

    private void cancelQueryCapabilitiesTimer() {
        if (mQueryCapabilitiesTimerHandler != null) {
            mQueryCapabilitiesTimerHandler.removeCallbacks(mQueryCapabilitiesTimerRunnable);
            mQueryCapabilitiesTimerHandler = null;
        }
    }
    // endregion

    // region Query AnalogMapping

    private ArrayList<Byte> queryAnalogMappingDataBuffer = new ArrayList<>();

    private void queryAnalogMapping() {
        Log.d(TAG, "queryAnalogMapping");

        // Set status
        mUartStatus = kUartStatus_QueryAnalogMapping;
        queryAnalogMappingDataBuffer.clear();

        // Query Analog Mapping
        byte bytes[] = new byte[]{SYSEX_START, (byte) 0x69, SYSEX_END};
        sendHexData(bytes);
    }

    private void receivedAnalogMapping(byte[] data) {
        cancelQueryCapabilitiesTimer();

        // Read received packet
        for (final byte dataByte : data) {
            queryAnalogMappingDataBuffer.add(dataByte);
            if (dataByte == SYSEX_END) {
                Log.d(TAG, "Finished receiving Analog Mapping");
                endPinQuery(false);
                break;
            }
        }
    }

    public void cancelQueryCapabilities() {
        Log.d(TAG, "timeout: cancelQueryCapabilities");
        endPinQuery(true);
    }

    // endregion

    // region Process Capabilities
    private void endPinQuery(boolean abortQuery) {
        cancelQueryCapabilitiesTimer();
        mUartStatus = kUartStatus_InputOutput;

        boolean capabilitiesParsed = false;
        boolean mappingDataParsed = false;
        if (!abortQuery && queryCapabilitiesDataBuffer.size() > 0 && queryAnalogMappingDataBuffer.size() > 0) {
            capabilitiesParsed = parseCapabilities(queryCapabilitiesDataBuffer);
            mappingDataParsed = parseAnalogMappingData(queryAnalogMappingDataBuffer);
        }

        final boolean isDefaultConfigurationAssumed = abortQuery || !capabilitiesParsed || !mappingDataParsed;
        if (isDefaultConfigurationAssumed) {
            initializeDefaultPins();
        }
        enableReadReports();

        // Clean received data
        queryCapabilitiesDataBuffer.clear();
        queryAnalogMappingDataBuffer.clear();

        // Refresh
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mQueryCapabilitiesDialog != null) {
                    mQueryCapabilitiesDialog.dismiss();
                }
                mPinListAdapter.notifyDataSetChanged();

                if (isDefaultConfigurationAssumed) {
                    defaultCapabilitiesAssumedDialog();
                }
            }
        });
    }

    private boolean parseCapabilities(ArrayList<Byte> capabilitiesData) {
        int endIndex = capabilitiesData.indexOf(SYSEX_END);
        if (capabilitiesData.size() > 2 && capabilitiesData.get(0) == SYSEX_START && capabilitiesData.get(1) == 0x6C && endIndex >= 0) {
            // Separate pin data
            ArrayList<ArrayList<Byte>> pinsBytes = new ArrayList<>();
            ArrayList<Byte> currentPin = new ArrayList<>();
            for (int i = 2; i < endIndex; i++) {        // Skip 2 header bytes and end byte
                byte dataByte = capabilitiesData.get(i);
                if (dataByte != 0x7f) {
                    currentPin.add(dataByte);
                } else {      // Finished current pin
                    pinsBytes.add(currentPin);
                    currentPin = new ArrayList<>();
                }
            }

            // Extract pin info
            mPins.clear();
            int pinNumber = 0;
            for (int j = 0; j < pinsBytes.size(); j++) {
                ArrayList<Byte> pinBytes = pinsBytes.get(j);

                boolean isInput = false, isOutput = false, isAnalog = false, isPWM = false;


                if (pinBytes.size() > 0) {
                    int i = 0;
                    while (i < pinBytes.size()) {
                        int dataByte = pinBytes.get(i) & 0xff;
                        switch (dataByte) {
                            case 0x00:
                                isInput = true;
                                i++;        // skip resolution byte
                                break;
                            case 0x01:
                                isOutput = true;
                                i++;        // skip resolution byte
                                break;
                            case 0x02:
                                isAnalog = true;
                                i++;        // skip resolution byte
                                break;
                            case 0x03:
                                isPWM = true;
                                i++;        // skip resolution byte
                                break;
                            case 0x04:
                                // Servo
                                i++;        // skip resolution byte
                                break;
                            case 0x06:
                                // I2C
                                i++;        // skip resolution byte
                                break;
                            default:
                                break;
                        }
                        i++;
                    }

                    PinData pinData = new PinData(pinNumber, isInput && isOutput, isAnalog, isPWM);
                    Log.d(TAG, "pin id: " + pinNumber + " digital: " + (pinData.isDigital ? "yes" : "no") + " analog: " + (pinData.isAnalog ? "yes" : "no"));
                    mPins.add(pinData);
                }

                pinNumber++;
            }
            return true;

        } else {
            Log.d(TAG, "invalid capabilities received");
            if (capabilitiesData.size() <= 2) {
                Log.d(TAG, "capabilitiesData size <= 2");
            }
            if (capabilitiesData.get(0) != SYSEX_START) {
                Log.d(TAG, "SYSEX_START not present");
            }
            if (endIndex < 0) {
                Log.d(TAG, "SYSEX_END not present");
            }

            return false;
        }
    }

    private boolean parseAnalogMappingData(ArrayList<Byte> analogData) {
        int endIndex = analogData.indexOf(SYSEX_END);
        if (analogData.size() > 2 && analogData.get(0) == SYSEX_START && analogData.get(1) == 0x6A && endIndex >= 0) {
            int pinNumber = 0;

            for (int i = 2; i < endIndex; i++) {        // Skip 2 header bytes and end byte
                byte dataByte = analogData.get(i);
                if (dataByte != 0x7f) {
                    int indexOfPinNumber = indexOfPinWithDigitalId(pinNumber);
                    if (indexOfPinNumber >= 0) {
                        mPins.get(indexOfPinNumber).analogPinId = dataByte & 0xff;
                        Log.d(TAG, "pin id: " + pinNumber + " analog id: " + dataByte);
                    } else {
                        Log.d(TAG, "warning: trying to set analog id: " + dataByte + " for pin id: " + pinNumber);
                    }

                }
                pinNumber++;
            }
            return true;
        } else {
            Log.d(TAG, "invalid analog mapping received");
            return false;
        }
    }

    private int indexOfPinWithDigitalId(int digitalPinId) {
        int i = 0;
        while (i < mPins.size() && mPins.get(i).digitalPinId != digitalPinId) {
            i++;
        }
        return i < mPins.size() ? i : -1;
    }

    private int indexOfPinWithAnalogId(int analogPinId) {
        int i = 0;
        while (i < mPins.size() && mPins.get(i).analogPinId != analogPinId) {
            i++;
        }
        return i < mPins.size() ? i : -1;
    }
    // endregion

    // region Pin Management
    private void initializeDefaultPins() {
        mPins.clear();

        for (int i = 0; i < DEFAULT_PINS_COUNT; i++) {
            PinData pin = null;
            if (i == 3 || i == 5 || i == 6) {
                pin = new PinData(i, true, false, false);
            } else if (i >= FIRST_DIGITAL_PIN && i <= LAST_DIGITAL_PIN) {
                pin = new PinData(i, true, false, false);
            } else if (i >= FIRST_ANALOG_PIN && i <= LAST_ANALOG_PIN) {
                pin = new PinData(i, true, true, false);
                pin.analogPinId = i - FIRST_ANALOG_PIN;
            }

            if (pin != null) {
                mPins.add(pin);
            }
        }
    }

    private void enableReadReports() {

        // Enable read reports by port
        for (int i = 0; i <= 2; i++) {
            byte data0 = (byte) (0xd0 + i);     // start port 0 digital reporting (0xD0 + port#)
            byte data1 = 1;                     // enable
            byte bytes[] = new byte[]{data0, data1};
            sendHexData(bytes);
        }

        // Set all pin modes active
        for (int i = 0; i < mPins.size(); i++) {
            // Write pin mode
            PinData pin = mPins.get(i);
            setControlMode(pin, pin.mode);
        }
    }

    private void setControlMode(PinData pin, int mode) {
        int previousMode = pin.mode;

        // Store
        pin.mode = mode;
        pin.digitalValue = PinData.kDigitalValue_Low;       // Reset dialog value when chaning mode
        pin.analogValue = 0;                                // Reset analog value when chaging mode

        // Write pin mode
        byte bytes[] = new byte[]{(byte) 0xf4, (byte) pin.digitalPinId, (byte) mode};
        sendHexData(bytes);

        // Update reporting for Analog pins
        if (mode == PinData.kMode_Analog) {
            setAnalogValueReporting(pin, true);
        } else if (previousMode == PinData.kMode_Analog) {
            setAnalogValueReporting(pin, false);
        }
    }

    private void setAnalogValueReporting(PinData pin, boolean enabled) {
        // Write pin mode
        byte data0 = (byte) (0xc0 + pin.analogPinId);       // start analog reporting for pin (192 + pin#)
        byte data1 = (byte) (enabled ? 1 : 0);       // enable

        // send data
        byte bytes[] = {data0, data1};
        sendHexData(bytes);
    }

    private void setDigitalValue(PinData pin, int value) {
        // Store
        pin.digitalValue = value;
        Log.d(TAG, "setDigitalValue: " + value + " for pin id: " + pin.digitalPinId);

        // Write value
        int port = pin.digitalPinId / 8;
        byte data0 = (byte) (0x90 + port);

        int offset = 8 * port;
        int state = 0;
        for (int i = 0; i <= 7; i++) {
            int pinIndex = indexOfPinWithDigitalId(offset + i);
            if (pinIndex >= 0) {
                int pinValue = mPins.get(pinIndex).digitalValue & 0x1;
                int pinMask = pinValue << i;
                state |= pinMask;
            }
        }

        byte data1 = (byte) (state & 0x7f);      // only 7 bottom bits
        byte data2 = (byte) (state >> 7);        // top bit in second byte

        // send data
        byte bytes[] = new byte[]{data0, data1, data2};
        sendHexData(bytes);
    }

    private long lastSentAnalogValueTime = 0;

    private boolean setPMWValue(PinData pin, int value) {

        // Limit the amount of messages sent over Uart
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSentAnalogValueTime >= 100) {
            Log.d(TAG, "pwm elapsed: " + (currentTime - lastSentAnalogValueTime));
            lastSentAnalogValueTime = currentTime;

            // Store
            pin.analogValue = value;

            // Send
            byte data0 = (byte) (0xe0 + pin.digitalPinId);
            byte data1 = (byte) (value & 0x7f);      // only 7 bottom bits
            byte data2 = (byte) (value >> 7);        // top bit in second byte

            byte bytes[] = new byte[]{data0, data1, data2};
            sendHexData(bytes);

            return true;
        } else {
            Log.d(TAG, "Won't send: Too many slider messages");
            return false;
        }
    }

    private ArrayList<Byte> receivedPinStateDataBuffer2 = new ArrayList<>();

    private int getUnsignedReceivedPinState(int index) {
        return receivedPinStateDataBuffer2.get(index) & 0xff;
    }

    private void receivedPinState(byte[] data) {

        // Append received bytes to buffer
        for (final byte dataByte : data) {
            receivedPinStateDataBuffer2.add(dataByte);
        }

        // Check if we received a pin state response
        int endIndex = receivedPinStateDataBuffer2.indexOf(SYSEX_END);
        if (receivedPinStateDataBuffer2.size() >= 5 && getUnsignedReceivedPinState(0) == SYSEX_START && getUnsignedReceivedPinState(1) == 0x6e && endIndex >= 0) {
            /* pin state response
            * -------------------------------
            * 0  START_SYSEX (0xF0) (MIDI System Exclusive)
            * 1  pin state response (0x6E)
            * 2  pin (0 to 127)
            * 3  pin mode (the currently configured mode)
            * 4  pin state, bits 0-6
            * 5  (optional) pin state, bits 7-13
            * 6  (optional) pin state, bits 14-20
            ...  additional optional bytes, as many as needed
            * N  END_SYSEX (0xF7)
            */

            int pinDigitalId = getUnsignedReceivedPinState(2);
            int pinMode = getUnsignedReceivedPinState(3);
            int pinState = getUnsignedReceivedPinState(4);

            int index = indexOfPinWithDigitalId(pinDigitalId);
            if (index >= 0) {
                PinData pin = mPins.get(index);
                pin.mode = pinMode;
                if (pinMode == PinData.kMode_Analog || pinMode == PinData.kMode_PWM || pinMode == PinData.kMode_Servo) {
                    if (receivedPinStateDataBuffer2.size() >= 6) {
                        pin.analogValue = pinState + (getUnsignedReceivedPinState(5) << 7);
                    } else {
                        Log.d(TAG, "Warning: received pinstate for analog pin without analogValue");
                    }
                } else {
                    if (pinState == PinData.kDigitalValue_Low || pinState == PinData.kDigitalValue_High) {
                        pin.digitalValue = pinState;
                    } else {
                        Log.d(TAG, "Warning: received pinstate with unknown digital value. Valid (0,1). Received: " + pinState);
                    }
                }

            } else {
                Log.d(TAG, "Warning: received pinstate for unknown digital pin id: " + pinDigitalId);
            }

            //  Remove from the buffer the bytes parsed
            for (int i = 0; i < endIndex; i++) {
                receivedPinStateDataBuffer2.remove(0);
            }
        } else {
            // Each pin message is 3 bytes long
            int data0 = getUnsignedReceivedPinState(0);
            boolean isDigitalReportingMessage = data0 >= 0x90 && data0 <= 0x9F;
            boolean isAnalogReportingMessage = data0 >= 0xe0 && data0 <= 0xef;
//            Log.d(TAG, "data0: "+data0);

            Log.d(TAG, "receivedPinStateDataBuffer size: " + receivedPinStateDataBuffer2.size());
            //          Log.d(TAG, "data[0]="+BleUtils.byteToHex(receivedPinStateDataBuffer.get(0))+ "data[1]="+BleUtils.byteToHex(receivedPinStateDataBuffer.get(1)));

            while (receivedPinStateDataBuffer2.size() >= 3 && (isDigitalReportingMessage || isAnalogReportingMessage)) {     // Check that current message length is at least 3 bytes
                if (isDigitalReportingMessage) {            // Digital Reporting (per port)
                     /* two byte digital data format, second nibble of byte 0 gives the port number (e.g. 0x92 is the third port, port 2)
                    * 0  digital data, 0x90-0x9F, (MIDI NoteOn, but different data format)
                    * 1  digital pins 0-6 bitmask
                    * 2  digital pin 7 bitmask
                    */

                    int port = getUnsignedReceivedPinState(0) - 0x90;
                    int pinStates = getUnsignedReceivedPinState(1);
                    pinStates |= getUnsignedReceivedPinState(2) << 7;        // PORT 0: use LSB of third byte for pin7, PORT 1: pins 14 & 15
                    updatePinsForReceivedStates(pinStates, port);
                } else if (isAnalogReportingMessage) {        // Analog Reporting (per pin)
                    /* analog 14-bit data format
                    * 0  analog pin, 0xE0-0xEF, (MIDI Pitch Wheel)
                    * 1  analog least significant 7 bits
                    * 2  analog most significant 7 bits
                    */

                    int analogPinId = getUnsignedReceivedPinState(0) - 0xe0;
                    int value = getUnsignedReceivedPinState(1) + (getUnsignedReceivedPinState(2) << 7);

                    int index = indexOfPinWithAnalogId(analogPinId);
                    if (index >= 0) {
                        PinData pin = mPins.get(index);
                        pin.analogValue = value;
                        Log.d(TAG, "received analog value: " + value + " pin analog id: " + analogPinId + " digital Id: " + index);
                    } else {
                        Log.d(TAG, "Warning: received pinstate for unknown analog pin id: " + index);
                    }
                }

                //  Remove from the buffer the bytes parsed
                for (int i = 0; i < 3; i++) {
                    receivedPinStateDataBuffer2.remove(0);
                }

                // Setup vars for next message
                if (receivedPinStateDataBuffer2.size() >= 3) {
                    data0 = getUnsignedReceivedPinState(0);
                    isDigitalReportingMessage = data0 >= 0x90 && data0 <= 0x9F;
                    isAnalogReportingMessage = data0 >= 0xe0 && data0 <= 0xef;

                } else {
                    isDigitalReportingMessage = false;
                    isAnalogReportingMessage = false;
                }
            }
        }

        // Refresh
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPinListAdapter.notifyDataSetChanged();
            }
        });
    }

    private void updatePinsForReceivedStates(int pinStates, int port) {
        int offset = 8 * port;

        // Iterate through all pins
        for (int i = 0; i <= 7; i++) {
            int mask = 1 << i;
            int state = (pinStates & mask) >> i;

            int digitalId = offset + i;

            int index = indexOfPinWithDigitalId(digitalId);
            if (index >= 0) {
                PinData pin = mPins.get(index);
                pin.digitalValue = state;
                //Log.d(TAG, "update pinid: " + digitalId + " digitalValue: " + state);
            }
        }
    }

    // endregion

    public void onClickPinIOTitle(final View view) {
        final int groupPosition = (Integer) view.getTag();
        if (mPinListView.isGroupExpanded(groupPosition)) {
            mPinListView.collapseGroup(groupPosition);
        } else {
            // Expand this, Collapse the rest
            int len = mPinListAdapter.getGroupCount();
            for (int i = 0; i < len; i++) {
                if (i != groupPosition) {
                    mPinListView.collapseGroup(i);
                }
            }

            mPinListView.expandGroup(groupPosition, true);

            // Force scrolling to view the children
            mPinScrollView.post(new Runnable() {
                @Override
                public void run() {
                    mPinListView.scrollToGroup(groupPosition, view, mPinScrollView);
                }
            });
        }
    }


    public void onClickMode(View view) {
        PinData pinData = (PinData) view.getTag();

        int newMode = PinData.kMode_Unknown;
        switch (view.getId()) {
            case R.id.inputRadioButton:
                newMode = PinData.kMode_Input;
                break;
            case R.id.outputRadioButton:
                newMode = PinData.kMode_Output;
                break;
            case R.id.pwmRadioButton:
                newMode = PinData.kMode_PWM;
                break;
            case R.id.analogRadioButton:
                newMode = PinData.kMode_Analog;
                break;
        }

        setControlMode(pinData, newMode);

        mPinListAdapter.notifyDataSetChanged();
    }

    public void onClickOutputType(View view) {
        PinData pinData = (PinData) view.getTag();

        int newState = PinData.kDigitalValue_Low;
        switch (view.getId()) {
            case R.id.lowRadioButton:
                newState = PinData.kDigitalValue_Low;
                break;
            case R.id.highRadioButton:
                newState = PinData.kDigitalValue_High;
                break;
        }

        setDigitalValue(pinData, newState);

        mPinListAdapter.notifyDataSetChanged();
    }

    // region BleManagerListener
    /*
    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }
*/
    @Override
    public void onDisconnected() {
        super.onDisconnected();
        Log.d(TAG, "Disconnected. Back to previous activity");
        setResult(-1);      // Unexpected Disconnect
        finish();
    }

    @Override
    public void onServicesDiscovered() {
        super.onServicesDiscovered();
        enableRxNotifications();

        // PinIo init
        if (mIsActivityFirstRun) {
            reset();
        }
    }
/*
    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }
    */
    // endregion


    private void sendHexData(byte[] data) {
        if (BuildConfig.DEBUG) {
            String hexRepresentation = BleUtils.bytesToHexWithSpaces(data);
            Log.d(TAG, "sendHex: " + hexRepresentation);
        }
        sendData(data);
    }

    // region UI

    private void startQueryCapabilitiesProcess() {
        if (!isQueryingCapabilities()) {

            // Show dialog
            mQueryCapabilitiesDialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.pinio_capabilityquery_querying_title)
                    .setCancelable(true)
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            endPinQuery(true);
                        }
                    })
                    .create();

            mQueryCapabilitiesDialog.show();

            // Start process
            queryCapabilities();
        } else {
            Log.d(TAG, "error: queryCapabilities called while querying capabilities");
        }
    }

    private void defaultCapabilitiesAssumedDialog() {
        Log.d(TAG, "QueryCapabilities not found");

        // Show dialog
        new AlertDialog.Builder(this)
                .setTitle(R.string.pinio_capabilityquery_expired_title)
                .setMessage(R.string.pinio_capabilityquery_expired_message)
                .create()
                .show();
    }

    // endregion

    private class ExpandableListAdapter extends BaseExpandableListAdapter {

        @Override
        public int getGroupCount() {
            return mPins.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return 1;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return null;
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return null;
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.layout_pinio_item_title, parent, false);
            }

            // Tag
            convertView.setTag(groupPosition);

            // Data
            PinData pin = mPins.get(groupPosition);

            // UI: Name
            TextView nameTextView = (TextView) convertView.findViewById(R.id.nameTextView);

            String name;
            if (pin.isAnalog) {
                name = String.format(getString(R.string.pinio_pinname_analog_format), pin.digitalPinId, pin.analogPinId);
            } else {
                name = String.format(getString(R.string.pinio_pinname_digital_format), pin.digitalPinId);
            }
            nameTextView.setText(name);

            // UI: Mode
            TextView modeTextView = (TextView) convertView.findViewById(R.id.modeTextView);
            modeTextView.setText(stringForPinMode(pin.mode));

            // UI: State
            TextView valueTextView = (TextView) convertView.findViewById(R.id.stateTextView);
            String valueString;
            switch (pin.mode) {
                case PinData.kMode_Input:
                    valueString = stringForPinDigitalValue(pin.digitalValue);
                    break;
                case PinData.kMode_Output:
                    valueString = stringForPinDigitalValue(pin.digitalValue);
                    break;
                case PinData.kMode_Analog:
                    valueString = String.valueOf(pin.analogValue);
                    break;
                case PinData.kMode_PWM:
                    valueString = String.valueOf(pin.analogValue);
                    break;
                default:
                    valueString = "";
                    break;
            }
            valueTextView.setText(valueString);


            return convertView;
        }

        private String stringForPinMode(int mode) {
            int modeStringResourceId;
            switch (mode) {
                case PinData.kMode_Input:
                    modeStringResourceId = R.string.pinio_pintype_input;
                    break;
                case PinData.kMode_Output:
                    modeStringResourceId = R.string.pinio_pintype_output;
                    break;
                case PinData.kMode_Analog:
                    modeStringResourceId = R.string.pinio_pintype_analog;
                    break;
                case PinData.kMode_PWM:
                    modeStringResourceId = R.string.pinio_pintype_pwm;
                    break;
                case PinData.kMode_Servo:
                    modeStringResourceId = R.string.pinio_pintype_servo;
                    break;
                default:
                    modeStringResourceId = R.string.pinio_pintype_unknown;
                    break;
            }

            return getString(modeStringResourceId);
        }

        private String stringForPinDigitalValue(int digitalValue) {
            int stateStringResourceId;
            switch (digitalValue) {
                case PinData.kDigitalValue_Low:
                    stateStringResourceId = R.string.pinio_pintype_low;
                    break;
                case PinData.kDigitalValue_High:
                    stateStringResourceId = R.string.pinio_pintype_high;
                    break;
                default:
                    stateStringResourceId = R.string.pinio_pintype_unknown;
                    break;
            }

            return getString(stateStringResourceId);
        }

        @Override
        public View getChildView(final int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.layout_pinio_item_child, parent, false);
            }
            // set tags
            final PinData pin = mPins.get(groupPosition);

            // Setup mode
            RadioButton inputRadioButton = (RadioButton) convertView.findViewById(R.id.inputRadioButton);
            inputRadioButton.setTag(pin);
            inputRadioButton.setChecked(pin.mode == PinData.kMode_Input);
            inputRadioButton.setVisibility(pin.isDigital ? View.VISIBLE : View.GONE);

            RadioButton outputRadioButton = (RadioButton) convertView.findViewById(R.id.outputRadioButton);
            outputRadioButton.setTag(pin);
            outputRadioButton.setChecked(pin.mode == PinData.kMode_Output);
            outputRadioButton.setVisibility(pin.isDigital ? View.VISIBLE : View.GONE);

            RadioButton pwmRadioButton = (RadioButton) convertView.findViewById(R.id.pwmRadioButton);
            pwmRadioButton.setTag(pin);
            pwmRadioButton.setChecked(pin.mode == PinData.kMode_PWM);
            pwmRadioButton.setVisibility(pin.isPwm ? View.VISIBLE : View.GONE);

            RadioButton analogRadioButton = (RadioButton) convertView.findViewById(R.id.analogRadioButton);
            analogRadioButton.setTag(pin);
            analogRadioButton.setChecked(pin.mode == PinData.kMode_Analog);
            analogRadioButton.setVisibility(pin.isAnalog ? View.VISIBLE : View.GONE);

            // Setup state
            RadioButton lowRadioButton = (RadioButton) convertView.findViewById(R.id.lowRadioButton);
            lowRadioButton.setTag(pin);
            lowRadioButton.setChecked(pin.digitalValue == PinData.kDigitalValue_Low);

            RadioButton highRadioButton = (RadioButton) convertView.findViewById(R.id.highRadioButton);
            highRadioButton.setTag(pin);
            highRadioButton.setChecked(pin.digitalValue == PinData.kDigitalValue_High);

            boolean isStateVisible = pin.mode == PinData.kMode_Output;
            RadioGroup stateRadioGroup = (RadioGroup) convertView.findViewById(R.id.stateRadioGroup);
            stateRadioGroup.setVisibility(isStateVisible ? View.VISIBLE : View.GONE);

            // pwm slider bar
            boolean isPwmBarVisible = pin.mode == PinData.kMode_PWM;
            SeekBar pmwSeekBar = (SeekBar) convertView.findViewById(R.id.pmwSeekBar);
            pmwSeekBar.setVisibility(isPwmBarVisible ? View.VISIBLE : View.GONE);
            pmwSeekBar.setProgress(pin.analogValue);
            pmwSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
//                        pin.analogValue = progress;
                        setPMWValue(pin, progress);

                        // Update only the value in the parent group
                        long parentPacketPosition = ExpandableListView.getPackedPositionForGroup(groupPosition);
                        long parentFlatPosition = mPinListView.getFlatListPosition(parentPacketPosition);
                        if (parentFlatPosition >= mPinListView.getFirstVisiblePosition() && parentFlatPosition <= mPinListView.getLastVisiblePosition()) {
                            View view = mPinListView.getChildAt((int) parentFlatPosition);
                            TextView valueTextView = (TextView) view.findViewById(R.id.stateTextView);
                            valueTextView.setText(String.valueOf(progress));
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    setPMWValue(pin, pin.analogValue);
                    mPinListAdapter.notifyDataSetChanged();
                }
            });

            // spacer visibility (spacers are shown if pwm or analog are visible)
            final boolean isSpacer2Visible = pin.isPwm || pin.isAnalog;
            View spacer2View = convertView.findViewById(R.id.spacer2View);
            spacer2View.setVisibility(isSpacer2Visible ? View.VISIBLE : View.GONE);
            /*
            final boolean isSpacer3Visible = isSpacer2Visible || (!pin.isPwm && pin.isDigital);
            View spacer3View = convertView.findViewById(R.id.spacer3View);
            spacer3View.setVisibility(isSpacer3Visible ? View.VISIBLE : View.GONE);
*/
            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }

    // region DataFragment
    public static class DataFragment extends Fragment {
        private ArrayList<PinData> mPins;

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

            // Init variables
            mPins = new ArrayList<>();

        } else {
            // Restore status
            mPins = mRetainedDataFragment.mPins;
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mPins = mPins;
    }
    // endregion
}
