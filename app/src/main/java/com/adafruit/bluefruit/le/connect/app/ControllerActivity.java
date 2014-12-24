package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleServiceListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.nio.ByteBuffer;

public class ControllerActivity extends UartInterfaceActivity implements BleServiceListener, SensorEventListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    // Log
    private final static String TAG = ControllerActivity.class.getSimpleName();

    private final static int kSendDataInterval = 500;   // milliseconds

    // Sensor Types
    private static final int kSensorType_Quaternion = 0;
    private static final int kSensorType_Accelerometer = 1;
    private static final int kSensorType_Gyroscope = 2;
    private static final int kSensorType_Magnetometer = 3;
    private static final int kSensorType_Location = 4;
    private static final int kNumSensorTypes = 5;

    // UI
    private ExpandableHeightExpandableListView mControllerListView;
    private ExpandableListAdapter mControllerListAdapter;

    // Data
    private Handler sendDataHandler = new Handler();
    private GoogleApiClient mGoogleApiClient;
    private SensorManager mSensorManager;
    private SensorData[] mSensorData;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mMagnetometer;

    private float[] mRotation = new float[9];
    private float[] mOrientation = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        mBleManager = BleManager.getInstance(this);
        mSensorData = new SensorData[kNumSensorTypes];
        for (int i = 0; i < kNumSensorTypes; i++) {
            SensorData sensorData = new SensorData();
            sensorData.sensorType = i;
            sensorData.enabled = false;
            mSensorData[i] = sensorData;
        }

        // UI
        mControllerListView = (ExpandableHeightExpandableListView) findViewById(R.id.controllerListView);
        mControllerListAdapter = new ExpandableListAdapter(this, mSensorData);
        mControllerListView.setAdapter(mControllerListAdapter);
        mControllerListView.setExpanded(true);

        // Sensors
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // Google Play Services (used for location updates)
        buildGoogleApiClient();

        // Start services
        onServicesDiscovered();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();

        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode == ConnectionResult.SERVICE_MISSING ||
                resultCode == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED ||
                resultCode == ConnectionResult.SERVICE_DISABLED) {

            Dialog googlePlayErrorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
            if (googlePlayErrorDialog != null) {
                googlePlayErrorDialog.show();
            }
        }

        // Setup listeners
        mBleManager.setBleListener(this);

        registerSensorListeners(true);

        // Setup send data task
        sendDataHandler.postDelayed(mPeriodicallySendData, kSendDataInterval);
    }

    @Override
    protected void onPause() {
        super.onPause();
        registerSensorListeners(false);

        // Remove send data task
        sendDataHandler.removeCallbacksAndMessages(null);
    }

    private Runnable mPeriodicallySendData = new Runnable() {
        @Override
        public void run() {
//            Log.d(TAG, "Send sensor data");
            final String[] prefixes = {"!Q", "!A", "!G", "!M", "!L"};     // same order that kSensorType

            for (int i = 0; i < mSensorData.length; i++) {
                SensorData sensorData = mSensorData[i];

                if (sensorData.enabled && sensorData.values != null) {
                    ByteBuffer buffer = ByteBuffer.allocate(3 * 4 * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);

                    // prefix
                    String prefix = prefixes[sensorData.sensorType];
                    buffer.put(prefix.getBytes());

                    // values
                    for (int j = 0; j < sensorData.values.length; j++) {
                        buffer.putFloat(sensorData.values[j]);
                    }

                    byte[] result = buffer.array();
                    sendData(result);
                }
            }

            sendDataHandler.postDelayed(this, kSendDataInterval);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_controller, menu);
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
        intent.putExtra("title", getString(R.string.controller_help_title));
        intent.putExtra("help", "controller_help.html");
        startActivity(intent);
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

    }

    private void registerSensorListeners(boolean register) {

        // Accelerometer
        if (register && (mSensorData[kSensorType_Accelerometer].enabled || mSensorData[kSensorType_Quaternion].enabled)) {
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            mSensorManager.unregisterListener(this, mAccelerometer);
//            mSensorData[kSensorType_Accelerometer].values = null;
//            mSensorData[kSensorType_Quaternion].values = null;
        }

        // Gyroscope
        if (register && mSensorData[kSensorType_Gyroscope].enabled) {
            mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            mSensorManager.unregisterListener(this, mGyroscope);
//            mSensorData[kSensorType_Gyroscope].values = null;
        }

        // Magnetometer
        if (register && (mSensorData[kSensorType_Magnetometer].enabled || mSensorData[kSensorType_Quaternion].enabled)) {
            mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            mSensorManager.unregisterListener(this, mMagnetometer);
//            mSensorData[kSensorType_Magnetometer].values = null;
//            mSensorData[kSensorType_Quaternion].values = null;

        }

        // Location
        if (mGoogleApiClient.isConnected()) {
            if (register && mSensorData[kSensorType_Location].enabled) {
                LocationRequest locationRequest = new LocationRequest();
                locationRequest.setInterval(2000);
                locationRequest.setFastestInterval(500);
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
            } else {
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
//                mSensorData[kSensorType_Location].values = null;
            }
        }
    }

    public void onClickToggle(View view) {
        // Expand / Collapse
        boolean enabled = ((ToggleButton) view).isChecked();
        int groupPosition = (Integer) view.getTag();
        mSensorData[groupPosition].enabled = enabled;
        registerSensorListeners(true);
        if (enabled) {
            mControllerListView.expandGroup(groupPosition, true);
        } else {
            mControllerListView.collapseGroup(groupPosition);
        }
    }

    public void onClickInterfacePad(View view) {
        Intent intent = new Intent(this, PadActivity.class);
        startActivity(intent);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        int sensorType = event.sensor.getType();
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            mSensorData[kSensorType_Accelerometer].values = event.values;

            updateOrientation();            // orientation depends on Accelerometer and Magnetometer
            mControllerListAdapter.notifyDataSetChanged();
        } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
            mSensorData[kSensorType_Gyroscope].values = event.values;

            mControllerListAdapter.notifyDataSetChanged();
        } else if (sensorType == Sensor.TYPE_MAGNETIC_FIELD) {
            mSensorData[kSensorType_Magnetometer].values = event.values;

            updateOrientation();            // orientation depends on Accelerometer and Magnetometer
            mControllerListAdapter.notifyDataSetChanged();
        }
    }

    private void updateOrientation() {

        float[] lastAccelerometer = mSensorData[kSensorType_Accelerometer].values;
        float[] lastMagnetometer = mSensorData[kSensorType_Magnetometer].values;
        if (lastAccelerometer != null && lastMagnetometer != null) {
            SensorManager.getRotationMatrix(mRotation, null, lastAccelerometer, lastMagnetometer);
            mSensorManager.getOrientation(mRotation, mOrientation);
            mSensorData[kSensorType_Quaternion].values = mOrientation;

        }
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
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {

    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }
    // endregion

    // region Google API Callbacks
    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Google Play Services connected");

        setLastLocation(LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient));
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Google Play Services suspended");

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Google Play Services connection failed");


    }
    // endregion

    // region LocationListener
    @Override
    public void onLocationChanged(Location location) {
        setLastLocation(location);

    }

    // endregion

    private void setLastLocation(Location location) {
        SensorData sensorData = mSensorData[kSensorType_Location];

        float[] values = new float[3];
        values[0] = (float) location.getLatitude();
        values[1] = (float) location.getLongitude();
        values[2] = (float) location.getAltitude();
        sensorData.values = values;

        mControllerListAdapter.notifyDataSetChanged();
    }


    private class SensorData {
        public int sensorType;
        public float[] values;
        public boolean enabled;
    }

    private class ExpandableListAdapter extends BaseExpandableListAdapter {
        private Activity mActivity;
        private SensorData[] mSensorData;

        public ExpandableListAdapter(Activity activity, SensorData[] sensorData) {
            mActivity = activity;
            mSensorData = sensorData;
        }

        @Override
        public int getGroupCount() {
            return kNumSensorTypes;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            switch (groupPosition) {
//                case kSensorType_Quaternion: return 4;       // Quaternion (x, y, z, w)
                default:
                    return 3;
            }
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
                convertView = mActivity.getLayoutInflater().inflate(R.layout.layout_controller_streamitem_title, parent, false);
            }

            // Tag
            convertView.setTag(groupPosition);

            // UI
            TextView nameTextView = (TextView) convertView.findViewById(R.id.nameTextView);
            String[] names = getResources().getStringArray(R.array.controller_stream_items);
            nameTextView.setText(names[groupPosition]);

            ToggleButton enableToggleButton = (ToggleButton) convertView.findViewById(R.id.enableToggleButton);
            enableToggleButton.setTag(groupPosition);
            enableToggleButton.setChecked(mSensorData[groupPosition].enabled);

            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mActivity.getLayoutInflater().inflate(R.layout.layout_controller_streamitem_child, parent, false);
            }

            // Value
            TextView valueTextView = (TextView) convertView.findViewById(R.id.valueTextView);

            String valueString = null;
            SensorData sensorData = mSensorData[groupPosition];
            if (sensorData.values != null && sensorData.values.length > childPosition) {
                if (sensorData.sensorType == kSensorType_Location) {
                    final String[] prefix = {"lat:", "long:", "alt:"};
                    valueString = prefix[childPosition] + " " + sensorData.values[childPosition];
                } else {
                    final String[] prefix = {"x:", "y:", "z:", "w:"};
                    valueString = prefix[childPosition] + " " + sensorData.values[childPosition];
                }
            }
            valueTextView.setText(valueString);

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }
}
