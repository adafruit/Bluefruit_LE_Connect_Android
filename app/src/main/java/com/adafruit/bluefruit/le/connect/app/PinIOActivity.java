package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
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

public class PinIOActivity extends UartInterfaceActivity implements BleManager.BleManagerListener {
    // Log
    private final static String TAG = UartActivity.class.getSimpleName();

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_ConnectedSettingsActivity = 0;

    // Pin Constants
    private static final int FIRST_DIGITAL_PIN = 3;
    private static final int LAST_DIGITAL_PIN = 8;
    private static final int FIRST_ANALOG_PIN = 14;
    private static final int LAST_ANALOG_PIN = 19;

    // UI
    private ExpandableHeightExpandableListView mDigitalListView;
    private ExpandableListAdapter mDigitalListAdapter;
    private ExpandableHeightExpandableListView mAnalogListView;
    private ExpandableListAdapter mAnalogListAdapter;
    private ScrollView mPinScrollView;

    // Data
    private boolean mIsActivityFirstRun;
    private PinData[] mDigitalPins;
    private PinData[] mAnalogPins;
    private byte[] portMasks;

    private DataFragment mRetainedDataFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_io);

        mBleManager = BleManager.getInstance(this);
        restoreRetainedDataFragment();

        // UI
        mDigitalListView = (ExpandableHeightExpandableListView) findViewById(R.id.digitalPinListView);
        mDigitalListAdapter = new ExpandableListAdapter(this, true, mDigitalPins);
        mDigitalListView.setAdapter(mDigitalListAdapter);
        mDigitalListView.setExpanded(true);

        mAnalogListView = (ExpandableHeightExpandableListView) findViewById(R.id.analogPinListView);
        mAnalogListAdapter = new ExpandableListAdapter(this, false, mAnalogPins);
        mAnalogListView.setAdapter(mAnalogListAdapter);
        mAnalogListView.setExpanded(true);

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
        }
        else if (id == R.id.action_connected_settings) {
            startConnectedSettings();
            return true;
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

    public void onClickPinIOTitle(final View view) {
        boolean isDigital = (Boolean) view.getTag(R.string.pinio_tag_mode);
        final ExpandableHeightExpandableListView listView = isDigital ? mDigitalListView : mAnalogListView;
        ExpandableListAdapter listAdapter = isDigital ? mDigitalListAdapter : mAnalogListAdapter;

        final int groupPosition = (Integer) view.getTag();
        if (listView.isGroupExpanded(groupPosition)) {
            listView.collapseGroup(groupPosition);
        } else {
            // Expand this, Collapse the rest
            int len = listAdapter.getGroupCount();
            for (int i = 0; i < len; i++) {
                if (i != groupPosition) {
                    listView.collapseGroup(i);
                }
            }

            listView.expandGroup(groupPosition, true);

            // Force scrolling to view the children
            mPinScrollView.post(new Runnable() {
                @Override
                public void run() {
                    listView.scrollToGroup(groupPosition, view, mPinScrollView);
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

        modeControlChanged(pinData, newMode);
        pinData.mode = newMode;

        ExpandableListAdapter listAdapter = pinData.isDigital ? mDigitalListAdapter : mAnalogListAdapter;
        listAdapter.notifyDataSetChanged();

    }

    public void onClickOutputType(View view) {
        PinData pinData = (PinData) view.getTag();

        int newState = PinData.kState_Low;
        switch (view.getId()) {
            case R.id.lowRadioButton:
                newState = PinData.kState_Low;
                break;
            case R.id.highRadioButton:
                newState = PinData.kState_High;
                break;
        }

        digitalControlChanged(pinData, newState);
        pinData.state = newState;

        ExpandableListAdapter listAdapter = pinData.isDigital ? mDigitalListAdapter : mAnalogListAdapter;
        listAdapter.notifyDataSetChanged();
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

        // PinIo init
        if (mIsActivityFirstRun) {
            enableReadReports();
        }
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        byte[] data = characteristic.getValue();

        if (data.length <= UartInterfaceActivity.kTxMaxCharacters) {
            processInputData(data);
        } else {
            Log.w(TAG, "unexpected received data length: " + data.length);
        }
    }

    private void processInputData(byte[] data) {

        Log.d(TAG, "received data: data = " + data[0] + " length = " + data.length);

        for (int i = 0; i < data.length; i += 3) {
            int data0 = data[i];

            //Digital Reporting (per port)
            if (data0 == 0x90) {                            //Port 0
                int pinStates = (int) (data[i + 1]);
                pinStates |= (int) (data[i + 2]) << 7;      //use LSB of third byte for pin7
                updateForPinStates(pinStates, 0);
            }
            else if (data0 == 0x91) {                       //Port 1
                int pinStates = (int) (data[i + 1]);
                pinStates |= (int) (data[i + 2]) << 7;      //pins 14 & 15
                updateForPinStates(pinStates, 1);
            }
            else if (data0 == 0x92) {                       // Port 2
                int pinStates = (int) (data[i + 1]);
                updateForPinStates(pinStates, 2);
            }

            //Analog Reporting (per pin)
            else if ((data0 >= 0xe0) && (data0 <= 0xe5)) {
                int pin = data0 - 0xe0;
                int val = (int) (data[i + 1]) + ((int) (data[i + 2]) << 7);

                if (pin < mAnalogPins.length) {
                    PinData pinData = mAnalogPins[pin];
                    pinData.state = val;

                    // Update UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mAnalogListAdapter.notifyDataSetChanged();

                        }
                    });
                }
            }
        }
    }

    private void updateForPinStates(int pinStates, int port) {

        // Update all ports
        for (int i = 0; i < 8; i++) {
            int state = pinStates;
            int mask = 1 << i;
            state = state & mask;
            state = state >> i;

            // update port
            if (port < mDigitalPins.length) {
                PinData pinData = mDigitalPins[port];
                if (pinData.mode == PinData.kMode_Input || pinData.mode == PinData.kMode_Output) {
                    if (state==0 || state==1) {
                        pinData.state = state==0?PinData.kState_Low:PinData.kState_High;
                    }
                    else {
                        Log.w(TAG, "Attempting set digital pin to analog value");
                    }
                }
                else {
                    Log.w(TAG, "Attempting set analog pin to digital value");
                }
            }
        }

        // Save reference state mask
        portMasks[port] = (byte)pinStates;

        // Update UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDigitalListAdapter.notifyDataSetChanged();

            }
        });
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }
    // endregion


    private void enableReadReports() {
        // Read Reports
        for (int i = 0; i < mDigitalPins.length; i++) {
            setDigitalStateReportingForPin((byte) (i + FIRST_DIGITAL_PIN), true);
        }
        for (int i = 0; i < mAnalogPins.length; i++) {
            setDigitalStateReportingForPin((byte) (i + FIRST_ANALOG_PIN), true);
        }

        // set all pin modes active
        for (int i = 0; i < mDigitalPins.length; i++) {
            modeControlChanged(mDigitalPins[i], mDigitalPins[i].mode);      // new mode is equal to old mode (just to initialize)
        }
        for (int i = 0; i < mAnalogPins.length; i++) {
            modeControlChanged(mAnalogPins[i], mAnalogPins[i].mode);        // new mode is equal to old mode (just to initialize)
        }
    }

    private void setDigitalStateReportingForPin(byte digitalPin, boolean enabled) {

        //Enable input/output for a digital pin

        //port 0: digital pins 0-7
        //port 1: digital pins 8-15
        //port 2: digital pins 16-23

        //find port for pin
        byte port;
        byte pin;

        //find pin for port
        if (digitalPin <= 7) {       //Port 0 (aka port D)
            port = 0;
            pin = digitalPin;
        } else if (digitalPin <= 15) { //Port 1 (aka port B)
            port = 1;
            pin = (byte) (digitalPin - 8);
        } else {                       //Port 2 (aka port C)
            port = 2;
            pin = (byte) (digitalPin - 16);
        }

        byte data0 = (byte) (0xd0 + port);         //start port 0 digital reporting (0xd0 + port#)
        byte data1 = portMasks[port];            //retrieve saved pin mask for port;

        if (enabled) {
            data1 |= 1 << pin;
        } else {
            data1 ^= 1 << pin;
        }

        portMasks[port] = data1;    //save new pin mask

        // send data
        byte bytes[] = {data0, data1};
        sendHexData(bytes);
    }

    private void modeControlChanged(PinData pinData, int newMode) {
        // Write pin
        writePinMode(newMode, pinData.pinId);

        // Update reporting for Analog pins
        if (newMode == PinData.kMode_Analog) {
            setAnalogValueReportingforPin(pinData.pinNumber, true);
        } else if (pinData.mode == PinData.kMode_Analog) {
            setAnalogValueReportingforPin(pinData.pinNumber, false);
        }
    }

    private void writePinMode(int newMode, byte pin) {
        byte data0 = (byte) 0xf4;       // status byte == 244
        byte data1 = pin;
        byte data2 = (byte) newMode;

        // send data
        byte bytes[] = {data0, data1, data2};
        sendHexData(bytes);
    }

    private void digitalControlChanged(PinData pinData, int newState) {
        writePinState(newState, pinData.pinId);
    }

    private void writePinState(int newState, byte pin) {
        byte port = (byte) (pin / 8);

        //Status byte == 144 + port#
        byte data0 = (byte) (0x90 + port);       //Status
        byte data1;                             //LSB of bitmask
        byte data2;                             //MSB of bitmask

        //Data1 == pin0State + 2*pin1State + 4*pin2State + 8*pin3State + 16*pin4State + 32*pin5State
        byte pinIndex = (byte) (pin - (port * 8));
        byte newMask = (byte) (newState * (int) (Math.pow(2, pinIndex)));

        if (port == 0) {
            portMasks[port] &= ~(1 << pinIndex); //prep the saved mask by zeroing this pin's corresponding bit
            newMask |= portMasks[port]; //merge with saved port state
            portMasks[port] = newMask;
            data1 = (byte) (newMask << 1);
            data1 >>= 1;  //remove MSB
            data2 = (byte) (newMask >> 7); //use data1's MSB as data2's LSB
        } else {
            portMasks[port] &= ~(1 << pinIndex); //prep the saved mask by zeroing this pin's corresponding bit
            newMask |= portMasks[port]; //merge with saved port state
            portMasks[port] = newMask;
            data1 = newMask;
            data2 = 0;

            //Hack for firmata pin15 reporting bug?
            if (port == 1) {
                data2 = (byte) (newMask >> 7);
                data1 &= ~(1 << 7);
            }
        }

        // send data
        byte bytes[] = {data0, data1, data2};
        sendHexData(bytes);
    }


    private void valueControlChanged(byte value, byte pin) {
        writePWMValue(value, pin);
    }

    private void writePWMValue(byte value, byte pin) {

        //Set an PWM output pin's value

        byte data0 = (byte) (0xe0 + pin);       //Status
        byte data1 = (byte) (value & 0x7F);     //LSB of bitmask
        byte data2 = (byte) (value >> 7);       //MSB of bitmask

        // send data
        byte bytes[] = {data0, data1, data2};
        sendHexData(bytes);
    }

    private void setAnalogValueReportingforPin(byte pin, boolean enabled) {
        byte data0 = (byte) (0xc0 + pin);       // start analog reporting for pin (192 + pin#)
        byte data1 = (byte) (enabled ? 1 : 0);       // enable

        // send data
        byte bytes[] = {data0, data1};
        sendHexData(bytes);
    }

    private void sendHexData(byte[] data) {
        if (BuildConfig.DEBUG) {
            String hexRepresentation = BleUtils.bytesToHexWithSpaces(data);
            Log.d(TAG, "sendHex: " + hexRepresentation);
        }
        sendData(data);
    }

    private class PinData {
        // Mode Constants
        final static int kMode_Unknown = -1;
        final static int kMode_Input = 0;
        final static int kMode_Output = 1;
        final static int kMode_Analog = 2;
        final static int kMode_PWM = 3;
        final static int kMode_Servo = 4;

        // State Constants
        final static int kState_Low = 0;
        final static int kState_High = 1;

        boolean isDigital;
        byte pinNumber;
        byte pinId;
        int mode = kMode_Unknown;
        int state = kState_Low;         // low-high for digital or int value for analog
        int pwm = 0;
    }

    private class ExpandableListAdapter extends BaseExpandableListAdapter {
        private Activity mActivity;
        private boolean mIsDigital;
        private PinData[] mPins;

        public ExpandableListAdapter(Activity activity, boolean isDigital, PinData[] pins) {
            mActivity = activity;
            mIsDigital = isDigital;
            mPins = pins;
        }


        @Override
        public int getGroupCount() {
            return mIsDigital ? (LAST_DIGITAL_PIN - FIRST_DIGITAL_PIN + 1) : (LAST_ANALOG_PIN - FIRST_ANALOG_PIN + 1);
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
                convertView = mActivity.getLayoutInflater().inflate(R.layout.layout_pinio_item_title, parent, false);
            }

            // Tag
            convertView.setTag(groupPosition);
            convertView.setTag(R.string.pinio_tag_mode, mIsDigital);

            // Data
            PinData pinData = mPins[groupPosition];

            // UI: Name
            TextView nameTextView = (TextView) convertView.findViewById(R.id.nameTextView);

            String name;
            if (mIsDigital) {
                name = String.format(getString(R.string.pinio_pinname_digital_format), "" + (groupPosition + FIRST_DIGITAL_PIN));
            } else {
                name = String.format(getString(R.string.pinio_pinname_analog_format), "" + (groupPosition));
            }
            nameTextView.setText(name);

            // UI: Mode
            TextView modeTextView = (TextView) convertView.findViewById(R.id.modeTextView);
            int modeStringResourceId;
            if (pinData.mode == PinData.kMode_Input)
                modeStringResourceId = R.string.pinio_pintype_input;
            else if (pinData.mode == PinData.kMode_Output)
                modeStringResourceId = R.string.pinio_pintype_output;
            else if (pinData.mode == PinData.kMode_Analog)
                modeStringResourceId = R.string.pinio_pintype_analog;
            else if (pinData.mode == PinData.kMode_PWM)
                modeStringResourceId = R.string.pinio_pintype_pwm;
            else modeStringResourceId = R.string.pinio_pintype_unknown;
            modeTextView.setText(getString(modeStringResourceId));

            // UI: State
            TextView stateTextView = (TextView) convertView.findViewById(R.id.stateTextView);
            int stateStringResourceId;
            if (pinData.mode == PinData.kMode_Analog) {
                stateTextView.setText("" + pinData.state);
            } else if (pinData.mode == PinData.kMode_PWM) {
                stateTextView.setText("" + pinData.pwm);
            } else {
                if (pinData.state == PinData.kState_Low)
                    stateStringResourceId = R.string.pinio_pintype_low;
                else if (pinData.state == PinData.kState_High)
                    stateStringResourceId = R.string.pinio_pintype_high;
                else stateStringResourceId = R.string.pinio_pintype_unknown;
                stateTextView.setText(getString(stateStringResourceId));
            }
            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mActivity.getLayoutInflater().inflate(R.layout.layout_pinio_item_child, parent, false);
            }

            // set tags
            final PinData pinData = mPins[groupPosition];
            RadioButton inputRadioButton = (RadioButton) convertView.findViewById(R.id.inputRadioButton);
            inputRadioButton.setTag(pinData);
            inputRadioButton.setChecked(pinData.mode == PinData.kMode_Input);
            RadioButton outputRadioButton = (RadioButton) convertView.findViewById(R.id.outputRadioButton);
            outputRadioButton.setTag(pinData);
            outputRadioButton.setChecked(pinData.mode == PinData.kMode_Output);
            RadioButton pwmRadioButton = (RadioButton) convertView.findViewById(R.id.pwmRadioButton);
            pwmRadioButton.setTag(pinData);
            pwmRadioButton.setChecked(pinData.mode == PinData.kMode_PWM);
            RadioButton analogRadioButton = (RadioButton) convertView.findViewById(R.id.analogRadioButton);
            analogRadioButton.setTag(pinData);
            analogRadioButton.setChecked(pinData.mode == PinData.kMode_Analog);
            RadioButton lowRadioButton = (RadioButton) convertView.findViewById(R.id.lowRadioButton);
            lowRadioButton.setTag(pinData);
            lowRadioButton.setChecked(pinData.state == PinData.kState_Low);
            RadioButton highRadioButton = (RadioButton) convertView.findViewById(R.id.highRadioButton);
            highRadioButton.setTag(pinData);
            highRadioButton.setChecked(pinData.state == PinData.kState_High);

            // pwm visibility
            int digitalPinId = groupPosition + FIRST_DIGITAL_PIN;
            boolean isPwmVisible = mIsDigital && ((digitalPinId == 3) || (digitalPinId == 5) || (digitalPinId == 6));
            pwmRadioButton.setVisibility(isPwmVisible ? View.VISIBLE : View.GONE);

            // analog visibility
            analogRadioButton.setVisibility(!mIsDigital ? View.VISIBLE : View.GONE);

            // state visibility
            boolean isStateVisible = pinData.mode == PinData.kMode_Output;
            RadioGroup stateRadioGroup = (RadioGroup) convertView.findViewById(R.id.stateRadioGroup);
            stateRadioGroup.setVisibility(isStateVisible ? View.VISIBLE : View.GONE);

            // pmw bar visibility
            boolean isPwmBarVisible = pinData.mode == PinData.kMode_PWM;
            SeekBar pmwSeekBar = (SeekBar) convertView.findViewById(R.id.pmwSeekBar);
            pmwSeekBar.setVisibility(isPwmBarVisible ? View.VISIBLE : View.GONE);
            pmwSeekBar.setProgress(pinData.pwm);
            pmwSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        pinData.pwm = progress;
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    valueControlChanged((byte) pinData.pwm, pinData.pinId);

                    ExpandableListAdapter listAdapter = pinData.isDigital ? mDigitalListAdapter : mAnalogListAdapter;
                    listAdapter.notifyDataSetChanged();
                }
            });

            // spacer visibility (spacers are shown if pwm or analog are visible)
            final boolean isSpacer2Visible = isPwmVisible || !mIsDigital;
            View spacer2View = convertView.findViewById(R.id.spacer2View);
            spacer2View.setVisibility(isSpacer2Visible ? View.VISIBLE : View.GONE);
            final boolean isSpacer3Visible = isSpacer2Visible || (!isPwmVisible && mIsDigital);
            View spacer3View = convertView.findViewById(R.id.spacer3View);
            spacer3View.setVisibility(isSpacer3Visible ? View.VISIBLE : View.GONE);

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }

    // region DataFragment
    public static class DataFragment extends Fragment {
        private PinData[] mDigitalPins;
        private PinData[] mAnalogPins;
        private byte[] portMasks;


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
            portMasks = new byte[3];           // initialized to 0 values
            mDigitalPins = new PinData[LAST_DIGITAL_PIN - FIRST_DIGITAL_PIN + 1];
            for (int i = 0; i < mDigitalPins.length; i++) {
                PinData pinData = new PinData();
                pinData.isDigital = true;
                pinData.pinNumber = (byte) i;
                pinData.pinId = (byte) (i + FIRST_DIGITAL_PIN);
                pinData.mode = PinData.kMode_Input;
                mDigitalPins[i] = pinData;
            }
            mAnalogPins = new PinData[LAST_ANALOG_PIN - FIRST_ANALOG_PIN + 1];
            for (int i = 0; i < mAnalogPins.length; i++) {
                PinData pinData = new PinData();
                pinData.isDigital = false;
                pinData.pinNumber = (byte) i;
                pinData.pinId = (byte) (i + FIRST_ANALOG_PIN);
                pinData.mode = i == 5 ? PinData.kMode_Analog : PinData.kMode_Input;
                mAnalogPins[i] = pinData;
            }
        } else {
            // Restore status
            mDigitalPins = mRetainedDataFragment.mDigitalPins;
            mAnalogPins = mRetainedDataFragment.mAnalogPins;
            portMasks = mRetainedDataFragment.portMasks;
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mDigitalPins = mDigitalPins;
        mRetainedDataFragment.mAnalogPins = mAnalogPins;
        mRetainedDataFragment.portMasks = portMasks;
    }
    // endregion
}
