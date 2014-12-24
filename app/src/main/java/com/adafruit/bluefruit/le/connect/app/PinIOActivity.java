package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
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
import android.widget.ExpandableListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.BuildConfig;
import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleServiceListener;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;

public class PinIOActivity extends UartInterfaceActivity implements BleServiceListener {
    // Log
    private final static String TAG = UartActivity.class.getSimpleName();

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

    // Data
    private PinData[] mDigitalPins;
    private PinData[] mAnalogPins;
    private byte[] portMasks = new byte[3];           // initialized to 0 values

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_io);

        mBleManager = BleManager.getInstance(this);

        // Init variables
        mDigitalPins = new PinData[LAST_DIGITAL_PIN - FIRST_DIGITAL_PIN + 1];
        for (int i = 0; i < mDigitalPins.length; i++) {
            PinData pinData = new PinData();
            pinData.isDigital = true;
            pinData.pinNumber = (byte)i;
            pinData.pinId = (byte) (i + FIRST_DIGITAL_PIN);
            pinData.mode = PinData.kMode_Input;
            mDigitalPins[i] = pinData;
        }
        mAnalogPins = new PinData[LAST_ANALOG_PIN - FIRST_ANALOG_PIN + 1];
        for (int i = 0; i < mAnalogPins.length; i++) {
            PinData pinData = new PinData();
            pinData.isDigital = false;
            pinData.pinNumber = (byte)i;
            pinData.pinId = (byte) (i + FIRST_ANALOG_PIN);
            pinData.mode = i == 5 ? PinData.kMode_Analog : PinData.kMode_Input;
            mAnalogPins[i] = pinData;
        }

        // UI
        mDigitalListView = (ExpandableHeightExpandableListView) findViewById(R.id.digitalPinListView);
        mDigitalListAdapter = new ExpandableListAdapter(this, true, mDigitalPins);
        mDigitalListView.setAdapter(mDigitalListAdapter);
        mDigitalListView.setExpanded(true);

        mAnalogListView = (ExpandableHeightExpandableListView) findViewById(R.id.analogPinListView);
        mAnalogListAdapter = new ExpandableListAdapter(this, false, mAnalogPins);
        mAnalogListView.setAdapter(mAnalogListAdapter);
        mAnalogListView.setExpanded(true);

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

        return super.onOptionsItemSelected(item);
    }

    private void startHelp() {
        // Launch app hep activity
        Intent intent = new Intent(this, CommonHelpActivity.class);
        intent.putExtra("title", getString(R.string.pinio_help_title));
        intent.putExtra("help", "pinio_help.html");
        startActivity(intent);
    }

    public void onClickPinIOTitle(View view) {
        boolean isDigital = (Boolean) view.getTag(R.string.pinio_tag_mode);
        ExpandableListView listView = isDigital ? mDigitalListView : mAnalogListView;
        ExpandableListAdapter listAdapter = isDigital ? mDigitalListAdapter : mAnalogListAdapter;

        int groupPosition = (Integer) view.getTag();
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
        }
    }

    public void onClickMode(View view) {
        PinData pinData = (PinData) view.getTag();
//        boolean checked = ((RadioButton) view).isChecked();

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
        //       boolean checked = ((RadioButton) view).isChecked();

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

    // region BleServiceListener
    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onServicesDiscovered() {
        mUartService = mBleManager.getGattService(UUID_SERVICE);

        // PinIo init
        enableReadReports();
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {

    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

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
        byte port = (byte)(pin/8);

        //Status byte == 144 + port#
        byte data0 = (byte)(0x90 + port);       //Status
        byte data1;                             //LSB of bitmask
        byte data2;                             //MSB of bitmask

        //Data1 == pin0State + 2*pin1State + 4*pin2State + 8*pin3State + 16*pin4State + 32*pin5State
        byte pinIndex = (byte)(pin - (port*8));
        byte newMask = (byte)(newState * (int)(Math.pow(2, pinIndex)));

        if (port == 0) {
            portMasks[port] &= ~(1 << pinIndex); //prep the saved mask by zeroing this pin's corresponding bit
            newMask |= portMasks[port]; //merge with saved port state
            portMasks[port] = newMask;
            data1 = (byte)(newMask<<1); data1 >>= 1;  //remove MSB
            data2 = (byte)(newMask >> 7); //use data1's MSB as data2's LSB
        }
        else {
            portMasks[port] &= ~(1 << pinIndex); //prep the saved mask by zeroing this pin's corresponding bit
            newMask |= portMasks[port]; //merge with saved port state
            portMasks[port] = newMask;
            data1 = newMask;
            data2 = 0;

            //Hack for firmata pin15 reporting bug?
            if (port == 1) {
                data2 = (byte)(newMask>>7);
                data1 &= ~(1<<7);
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

        byte data0 = (byte)(0xe0 + pin);       //Status
        byte data1 = (byte)(value & 0x7F);     //LSB of bitmask
        byte data2 = (byte)(value >> 7);       //MSB of bitmask

        // send data
        byte bytes[] = {data0, data1, data2};
        sendHexData(bytes);
    }

    private void setAnalogValueReportingforPin(byte pin, boolean enabled) {
        byte data0 = (byte) (0xc0 + pin);       // start analog reporting for pin (192 + pin#)
        byte data1 = (byte)(enabled?1:0);       // enable

        // send data
        byte bytes[] = {data0, data1};
        sendHexData(bytes);
    }

    /*
    private void setDigitalStateReportingForPort(byte port, boolean enabled) {

        //Enable input/output for a digital pin

        //Enable by port
        byte data0 = (byte) (0xd0 + port);  //start port 0 digital reporting (207 + port#)
        byte data1 = 0; //Enable
        if (enabled) {
            data1 = 1;
        }

        // send data
        byte bytes[] = {data0, data1};
        sendHexData(bytes);
    }
    */

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
        int state = kState_Low;
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
                stateTextView.setText("0");
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
            boolean isPwmVisible = ((digitalPinId == 3) || (digitalPinId == 5) || (digitalPinId == 6));
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
            pmwSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    //Log.d(TAG, "seek: "+progress);
                    pinData.pwm = progress;

                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    valueControlChanged((byte)pinData.pwm, pinData.pinId);

                    ExpandableListAdapter listAdapter = pinData.isDigital ? mDigitalListAdapter : mAnalogListAdapter;
                    listAdapter.notifyDataSetChanged();
                }
            });

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }
}
