package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.settings.ConnectedSettingsActivity;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ui.utils.ExpandableHeightExpandableListView;
import com.adafruit.bluefruit.le.connect.ui.utils.ExpandableHeightListView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.nio.ByteBuffer;

public class ControllerActivity extends UartInterfaceActivity implements SensorEventListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    // Config
    private final static boolean kKeepUpdatingParentValuesInChildActivities = true;

    // Log
    private final static String TAG = ControllerActivity.class.getSimpleName();

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_ConnectedSettingsActivity = 0;
    private static final int kActivityRequestCode_PadActivity = 1;
    private static final int kActivityRequestCode_ColorPickerActivity = 2;

    // Constants
    private final static String kPreferences = "ControllerActivity_prefs";
    private final static String kPreferences_uartToolTip = "uarttooltip";

    // Constants
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

    private ViewGroup mUartTooltipViewGroup;

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
    private float[] mQuaternion = new float[4];

    private DataFragment mRetainedDataFragment;

    private boolean isSensorPollingEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        //Log.d(TAG, "onCreate");

        mBleManager = BleManager.getInstance(this);
        restoreRetainedDataFragment();

        // UI
        mControllerListView = (ExpandableHeightExpandableListView) findViewById(R.id.controllerListView);
        mControllerListAdapter = new ExpandableListAdapter(this, mSensorData);
        mControllerListView.setAdapter(mControllerListAdapter);
        mControllerListView.setExpanded(true);

        ExpandableHeightListView interfaceListView = (ExpandableHeightListView) findViewById(R.id.interfaceListView);
        ArrayAdapter<String> interfaceListAdapter = new ArrayAdapter<>(this, R.layout.layout_controller_interface_title, R.id.titleTextView, getResources().getStringArray(R.array.controller_interface_items));
        assert interfaceListView != null;
        interfaceListView.setAdapter(interfaceListAdapter);
        interfaceListView.setExpanded(true);
        interfaceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Intent intent = new Intent(ControllerActivity.this, PadActivity.class);
            startActivityForResult(intent, kActivityRequestCode_PadActivity);
            }
        });

        mUartTooltipViewGroup = (ViewGroup) findViewById(R.id.uartTooltipViewGroup);
        SharedPreferences preferences = getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
        final boolean showUartTooltip = preferences.getBoolean(kPreferences_uartToolTip, true);
        mUartTooltipViewGroup.setVisibility(showUartTooltip ? View.VISIBLE : View.GONE);

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

        Log.d(TAG, "onStart");
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");

        if (!kKeepUpdatingParentValuesInChildActivities) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");

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

        registerEnabledSensorListeners(true);

        // Setup send data task
        if (!isSensorPollingEnabled) {
            sendDataHandler.postDelayed(mPeriodicallySendData, kSendDataInterval);
            isSensorPollingEnabled = true;
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");

        super.onPause();

        if (!kKeepUpdatingParentValuesInChildActivities) {
            registerEnabledSensorListeners(false);

            // Remove send data task
            sendDataHandler.removeCallbacksAndMessages(null);
            isSensorPollingEnabled = false;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        if (kKeepUpdatingParentValuesInChildActivities) {
            // Remove all sensor polling
            registerEnabledSensorListeners(false);
            sendDataHandler.removeCallbacksAndMessages(null);
            isSensorPollingEnabled = false;
            mGoogleApiClient.disconnect();
        }

        // Retain data
        saveRetainedDataFragment();

        super.onDestroy();
    }

    private Runnable mPeriodicallySendData = new Runnable() {
        @Override
        public void run() {
            final String[] prefixes = {"!Q", "!A", "!G", "!M", "!L"};     // same order that kSensorType

            for (int i = 0; i < mSensorData.length; i++) {
                SensorData sensorData = mSensorData[i];

                if (sensorData.enabled && sensorData.values != null) {
                    ByteBuffer buffer = ByteBuffer.allocate(2 + sensorData.values.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);

                    // prefix
                    String prefix = prefixes[sensorData.sensorType];
                    buffer.put(prefix.getBytes());

                    // values
                    for (int j = 0; j < sensorData.values.length; j++) {
                        buffer.putFloat(sensorData.values[j]);
                    }

                    byte[] result = buffer.array();
                    Log.d(TAG, "Send data for sensor: " + i);
                    sendDataWithCRC(result);
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
        } else if (id == R.id.action_connected_settings) {
            startConnectedSettings();
            return true;
        } else if (id == R.id.action_refreshcache) {
            if (mBleManager != null) {
                mBleManager.refreshDeviceCache();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    private void startConnectedSettings() {
        // Launch connected settings activity
        Intent intent = new Intent(this, ConnectedSettingsActivity.class);
        startActivityForResult(intent, kActivityRequestCode_ConnectedSettingsActivity);
    }

    private void startHelp() {
        // Launch app help activity
        Intent intent = new Intent(this, CommonHelpActivity.class);
        intent.putExtra("title", getString(R.string.controller_help_title));
        intent.putExtra("help", "controller_help.html");
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 0) {
            if (resultCode < 0) {       // Unexpected disconnect
                setResult(resultCode);
                finish();
            }
        }
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private boolean isLocationEnabled() {
        int locationMode;
        try {
            locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);

        } catch (Settings.SettingNotFoundException e) {
            locationMode = Settings.Secure.LOCATION_MODE_OFF;
        }

        return locationMode != Settings.Secure.LOCATION_MODE_OFF;

    }

    private void registerEnabledSensorListeners(boolean register) {

        // Accelerometer
        if (register && (mSensorData[kSensorType_Accelerometer].enabled || mSensorData[kSensorType_Quaternion].enabled)) {
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            mSensorManager.unregisterListener(this, mAccelerometer);
        }

        // Gyroscope
        if (register && mSensorData[kSensorType_Gyroscope].enabled) {
            mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            mSensorManager.unregisterListener(this, mGyroscope);
        }

        // Magnetometer
        if (register && (mSensorData[kSensorType_Magnetometer].enabled || mSensorData[kSensorType_Quaternion].enabled)) {
            if (mMagnetometer == null) {
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.controller_magnetometermissing))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } else {
            mSensorManager.unregisterListener(this, mMagnetometer);
        }

        // Location
        if (mGoogleApiClient.isConnected()) {
            if (register && mSensorData[kSensorType_Location].enabled) {
                LocationRequest locationRequest = new LocationRequest();
                locationRequest.setInterval(2000);
                locationRequest.setFastestInterval(500);
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                // Location updates should have already been granted to scan for bluetooth peripherals, so we dont ask for them again
                try {
                    LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception requesting location updates: " + e);
                }
            } else {
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            }
        }
    }

    public void onClickCloseTooltip(View view) {
        SharedPreferences settings = getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(kPreferences_uartToolTip, false);
        editor.apply();

        mUartTooltipViewGroup.setVisibility(View.GONE);

    }

    public void onClickToggle(View view) {
        boolean enabled = ((ToggleButton) view).isChecked();
        int groupPosition = (Integer) view.getTag();

        // Special check for location data
        if (groupPosition == kSensorType_Location) {
            // Detect if location is enabled or warn user
            final boolean isLocationEnabled = isLocationEnabled();
            if (!isLocationEnabled) {
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.controller_location_disabled))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }

        // Enable sensor
        mSensorData[groupPosition].enabled = enabled;
        registerEnabledSensorListeners(true);

        // Expand / Collapse
        if (enabled) {
            mControllerListView.expandGroup(groupPosition, true);
        } else {
            mControllerListView.collapseGroup(groupPosition);
        }
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
            SensorManager.getOrientation(mRotation, mOrientation);

            final boolean kUse4Components = true;
            if (kUse4Components) {
                SensorManager.getQuaternionFromVector(mQuaternion, mOrientation);
                // Quaternions in Android are stored as [w, x, y, z], so we change it to [x, y, z, w]
                float w = mQuaternion[0];
                mQuaternion[0] = mQuaternion[1];
                mQuaternion[1] = mQuaternion[2];
                mQuaternion[2] = mQuaternion[3];
                mQuaternion[3] = w;

                mSensorData[kSensorType_Quaternion].values = mQuaternion;
            } else {
                mSensorData[kSensorType_Quaternion].values = mOrientation;
            }
        }
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
        finishActivity(kActivityRequestCode_PadActivity);
        finishActivity(kActivityRequestCode_ColorPickerActivity);
        finish();
    }

    /*
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
    */

    // endregion

    // region Google API Callbacks
    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Google Play Services connected");

        // Location updates should have already been granted to scan for bluetooth peripherals, so we dont ask for them again
        try {
            setLastLocation(LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient));
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception requesting location updates: " + e);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Google Play Services suspended");

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
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
        if (location != null) {
            SensorData sensorData = mSensorData[kSensorType_Location];

            float[] values = new float[3];
            values[0] = (float) location.getLatitude();
            values[1] = (float) location.getLongitude();
            values[2] = (float) location.getAltitude();
            sensorData.values = values;
        }
        mControllerListAdapter.notifyDataSetChanged();
    }


    // region ExpandableListAdapter
    private class SensorData {
        public int sensorType;
        public float[] values;
        public boolean enabled;
    }

    private class ExpandableListAdapter extends BaseExpandableListAdapter {
        private Activity mActivity;
        private SensorData[] mSensorData;

        ExpandableListAdapter(Activity activity, SensorData[] sensorData) {
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
                case kSensorType_Quaternion:
                    return 4;       // Quaternion (x, y, z, w)
                case kSensorType_Location: {
                    SensorData sensorData = mSensorData[groupPosition];
                    return sensorData.values == null ? 1 : 3;
                }
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
            enableToggleButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    // Set onclick to action_down to avoid losing state because the button is recreated when notifiydatasetchanged is called and it could be really fast (before the user has time to generate a ACTION_UP event)
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        ToggleButton button = (ToggleButton) view;
                        button.setChecked(!button.isChecked());
                        onClickToggle(view);
                        return true;
                    }
                    return false;
                }
            });

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
            } else {        // Invalid values
                if (sensorData.sensorType == kSensorType_Location) {
                    if (sensorData.values == null) {
                        valueString = getString(R.string.controller_location_unknown);
                    }
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

    // endregion

    // region DataFragment
    public static class DataFragment extends Fragment {
        private SensorData[] mSensorData;

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

            // Init
            mSensorData = new SensorData[kNumSensorTypes];
            for (int i = 0; i < kNumSensorTypes; i++) {
                SensorData sensorData = new SensorData();
                sensorData.sensorType = i;
                sensorData.enabled = false;
                mSensorData[i] = sensorData;
            }

        } else {
            // Restore status
            mSensorData = mRetainedDataFragment.mSensorData;
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mSensorData = mSensorData;
    }
    // endregion
}
