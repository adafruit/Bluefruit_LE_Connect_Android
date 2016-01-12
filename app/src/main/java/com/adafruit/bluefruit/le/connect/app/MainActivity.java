package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.settings.SettingsActivity;
import com.adafruit.bluefruit.le.connect.app.update.FirmwareUpdater;
import com.adafruit.bluefruit.le.connect.app.update.ReleasesParser;
import com.adafruit.bluefruit.le.connect.ble.BleDevicesScanner;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ui.utils.DialogUtils;
import com.adafruit.bluefruit.le.connect.ui.utils.ExpandableHeightExpandableListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;


public class MainActivity extends AppCompatActivity implements BleManager.BleManagerListener, BleUtils.ResetBluetoothAdapterListener, FirmwareUpdater.FirmwareUpdaterListener {
    // Constants
    private final static String TAG = MainActivity.class.getSimpleName();
    private final static long kMinDelayToUpdateUI = 800;    // in milliseconds
    private static final String kGenericAttributeService = "00001801-0000-1000-8000-00805F9B34FB";
    private static final String kServiceChangedCharacteristic = "00002A05-0000-1000-8000-00805F9B34FB";

    // Components
    private final static int kComponentsNameIds[] = {
            R.string.scan_connectservice_info,
            R.string.scan_connectservice_uart,
//            R.string.scan_connectservice_pinio,
            R.string.scan_connectservice_controller,
            R.string.scan_connectservice_beacon,
    };

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_EnableBluetooth = 1;
    private static final int kActivityRequestCode_Settings = 2;
    private static final int kActivityRequestCode_ConnectedActivity = 3;

    // UI
    private ExpandableHeightExpandableListView mScannedDevicesListView;
    private ExpandableListAdapter mScannedDevicesAdapter;
    private Button mScanButton;
    private long mLastUpdateMillis;
    private TextView mNoDevicesTextView;
    private ScrollView mDevicesScrollView;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    private AlertDialog mConnectingDialog;

    // Data
    private BleManager mBleManager;
    private boolean mIsScanPaused = true;
    private BleDevicesScanner mScanner;
    private FirmwareUpdater mFirmwareUpdater;

    private ArrayList<BluetoothDeviceData> mScannedDevices;
    private BluetoothDeviceData mSelectedDeviceData;
    private Class<?> mComponentToStartWhenConnected;
    private boolean mShouldEnableWifiOnQuit = false;
    private String mLatestCheckedDeviceAddress;

    private DataFragment mRetainedDataFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init variables
        mBleManager = BleManager.getInstance(this);
        restoreRetainedDataFragment();

        // UI
        mScannedDevicesListView = (ExpandableHeightExpandableListView) findViewById(R.id.scannedDevicesListView);
        mScannedDevicesAdapter = new ExpandableListAdapter(mScannedDevices);
        mScannedDevicesListView.setAdapter(mScannedDevicesAdapter);
        mScannedDevicesListView.setExpanded(true);

        mScannedDevicesListView.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {
            @Override
            public void onGroupExpand(int groupPosition) {
            }
        });

        mScanButton = (Button) findViewById(R.id.scanButton);

        mNoDevicesTextView = (TextView) findViewById(R.id.nodevicesTextView);
        mDevicesScrollView = (ScrollView) findViewById(R.id.devicesScrollView);
        mDevicesScrollView.setVisibility(View.GONE);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        mSwipeRefreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh() {
                mScannedDevices.clear();
                startScan(null, null);

                mSwipeRefreshLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                }, 500);
            }
        });

        // Setup when activity is created for the first time
        if (savedInstanceState == null) {
            // Read preferences
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean autoResetBluetoothOnStart = sharedPreferences.getBoolean("pref_resetble", false);
            boolean disableWifi = sharedPreferences.getBoolean("pref_disableWifi", false);
            boolean updatesEnabled = sharedPreferences.getBoolean("pref_updatesenabled", true);

            // Update SoftwareUpdateManager
            if (updatesEnabled) {
                mFirmwareUpdater = new FirmwareUpdater(this, this);
                mFirmwareUpdater.refreshSoftwareUpdatesDatabase();
            }

            // Turn off wifi
            if (disableWifi) {
                final boolean isWifiEnabled = BleUtils.isWifiEnabled(this);
                if (isWifiEnabled) {
                    BleUtils.enableWifi(false, this);
                    mShouldEnableWifiOnQuit = true;
                }
            }

            // Check if bluetooth adapter is available
            final boolean wasBluetoothEnabled = manageBluetoothAvailability();
            final boolean areLocationServicesReadyForScanning = manageLocationServiceAvailabilityForScanning();

            // Reset bluetooth
            if (autoResetBluetoothOnStart && wasBluetoothEnabled && areLocationServicesReadyForScanning) {
                BleUtils.resetBluetoothAdapter(this, this);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
        } else if (id == R.id.action_settings) {
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, SettingsActivity.class);
            startActivityForResult(intent, kActivityRequestCode_Settings);
            return true;
        } else if (id == R.id.action_licenses) {
            Intent intent = new Intent(this, CommonHelpActivity.class);
            intent.putExtra("title", getString(R.string.licenses_title));
            intent.putExtra("help", "licenses.html");
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Set listener
        mBleManager.setBleListener(this);

        // Autostart scan
        if (BleUtils.getBleStatus(this) == BleUtils.STATUS_BLE_ENABLED) {
            // If was connected, disconnect
            mBleManager.disconnect();

            // Force restart scanning
            if (mScannedDevices != null) {      // Fixed a weird bug when resuming the app (this was null on very rare occasions even if it should not be)
                mScannedDevices.clear();
            }
            startScan(null, null);
        }

        // Update UI
        updateUI();
    }

    @Override
    public void onPause() {
        // Stop scanning
        if (mScanner != null && mScanner.isScanning()) {
            mIsScanPaused = true;
            stopScanning();
        }

        super.onPause();
    }

    public void onStop() {
        if (mConnectingDialog != null) {
            mConnectingDialog.cancel();
            mConnectingDialog = null;
        }

        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (mShouldEnableWifiOnQuit) {
            mShouldEnableWifiOnQuit = false;
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.settingsaction_confirmenablewifi_title))
                    .setMessage(getString(R.string.settingsaction_confirmenablewifi_message))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "enableNotification wifi");
                            BleUtils.enableWifi(true, MainActivity.this);
                            MainActivity.super.onBackPressed();
                        }

                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            MainActivity.super.onBackPressed();
                        }

                    })
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        // Stop ble adapter reset if in progress
        BleUtils.cancelBluetoothAdapterReset();

        // Retain data
        saveRetainedDataFragment();

        // Clean
        if (mConnectingDialog != null) {
            mConnectingDialog.cancel();
        }

        super.onDestroy();
    }

    private void resumeScanning() {
        if (mIsScanPaused) {
            startScan(null, null);
            mIsScanPaused = mScanner == null;
        }
    }

    private void showChooseDeviceServiceDialog(final BluetoothDevice device) {
        // Prepare dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String deviceName = device.getName();
        String title = String.format(getString(R.string.scan_connectto_dialog_title_format), deviceName != null ? deviceName : device.getAddress());
        String[] items = new String[kComponentsNameIds.length];
        for (int i = 0; i < kComponentsNameIds.length; i++) items[i] = getString(kComponentsNameIds[i]);

        builder.setTitle(title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch (kComponentsNameIds[which]) {
                            case R.string.scan_connectservice_info: {          // Info
                                mComponentToStartWhenConnected = InfoActivity.class;
                                break;
                            }
                            case R.string.scan_connectservice_uart: {           // Uart
                                mComponentToStartWhenConnected = UartActivity.class;
                                break;
                            }
                            case R.string.scan_connectservice_pinio: {        // PinIO
                                mComponentToStartWhenConnected = PinIOActivity.class;
                                break;
                            }
                            case R.string.scan_connectservice_controller: {     // Controller
                                mComponentToStartWhenConnected = ControllerActivity.class;
                                break;
                            }
                            case R.string.scan_connectservice_beacon: {         // Beacon
                                mComponentToStartWhenConnected = BeaconActivity.class;
                                break;
                            }
                        }

                        if (mComponentToStartWhenConnected != null) {
                            connect(device);            // First connect to the device, and when connected go to selected activity
                        }
                    }
                });

        // Show dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private boolean manageBluetoothAvailability() {
        boolean isEnabled = true;

        // Check Bluetooth HW status
        int errorMessageId = 0;
        final int bleStatus = BleUtils.getBleStatus(getBaseContext());
        switch (bleStatus) {
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                errorMessageId = R.string.dialog_error_no_ble;
                isEnabled = false;
                break;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE: {
                errorMessageId = R.string.dialog_error_no_bluetooth;
                isEnabled = false;      // it was already off
                break;
            }
            case BleUtils.STATUS_BLUETOOTH_DISABLED: {
                isEnabled = false;      // it was already off
                // if no enabled, launch settings dialog to enable it (user should always be prompted before automatically enabling bluetooth)
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, kActivityRequestCode_EnableBluetooth);
                // execution will continue at onActivityResult()
                break;
            }
        }
        if (errorMessageId > 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            AlertDialog dialog = builder.setMessage(errorMessageId)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show();
            DialogUtils.keepDialogOnOrientationChanges(dialog);
        }

        return isEnabled;
    }

    private boolean manageLocationServiceAvailabilityForScanning() {

        boolean areLocationServiceReady = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {        // Location services are only needed to be enabled from Android 6.0
            int locationMode = Settings.Secure.LOCATION_MODE_OFF;
            try {
                locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            areLocationServiceReady = locationMode != Settings.Secure.LOCATION_MODE_OFF;

            if (!areLocationServiceReady) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                AlertDialog dialog = builder.setMessage(R.string.dialog_error_nolocationservices_requiredforscan_marshmallow)
                        .setPositiveButton(R.string.dialog_ok, null)
                        .show();
                DialogUtils.keepDialogOnOrientationChanges(dialog);
            }
        }

        return areLocationServiceReady;
    }

    private void connect(BluetoothDevice device) {
        boolean isConnecting = mBleManager.connect(this, device.getAddress());
        if (isConnecting) {
            showConnectionStatus(true);
        }
    }

    private void startHelp() {
        // Launch app help activity
        Intent intent = new Intent(this, MainHelpActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == kActivityRequestCode_ConnectedActivity) {
            if (resultCode < 0) {
                Toast.makeText(this, R.string.scan_unexpecteddisconnect, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == kActivityRequestCode_EnableBluetooth) {
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth was enabled, resume scanning
                resumeScanning();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                AlertDialog dialog = builder.setMessage(R.string.dialog_error_no_bluetooth)
                        .setPositiveButton(R.string.dialog_ok, null)
                        .show();
                DialogUtils.keepDialogOnOrientationChanges(dialog);

            }
        } else if (requestCode == kActivityRequestCode_Settings) {
            // Return from activity settings. Update app behaviour if needed
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean updatesEnabled = sharedPreferences.getBoolean("pref_updatesenabled", true);
            if (updatesEnabled) {
                mLatestCheckedDeviceAddress = null;
                mFirmwareUpdater.refreshSoftwareUpdatesDatabase();
            } else {
                mFirmwareUpdater = null;
            }
        }
    }

    private void showConnectionStatus(boolean enable) {
        showStatusDialog(enable, R.string.scan_connecting);
    }

    private void showGettingUpdateInfoState() {
        showConnectionStatus(false);
        showStatusDialog(true, R.string.scan_gettingupdateinfo);
    }

    private void showStatusDialog(boolean show, int stringId) {
        if (show) {

            // Remove if a previous dialog was open (maybe because was clicked 2 times really quick)
            if (mConnectingDialog != null) {
                mConnectingDialog.cancel();
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(stringId);

            // Show dialog
            mConnectingDialog = builder.create();
            mConnectingDialog.setCanceledOnTouchOutside(false);

            mConnectingDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        mBleManager.disconnect();
                        mConnectingDialog.cancel();
                    }
                    return true;
                }
            });
            mConnectingDialog.show();
        } else {
            if (mConnectingDialog != null) {
                mConnectingDialog.cancel();
            }
        }
    }

    // region Actions
    public void onClickScannedDevice(final View view) {
        final int groupPosition = (Integer) view.getTag();

        if (mScannedDevicesListView.isGroupExpanded(groupPosition)) {
            mScannedDevicesListView.collapseGroup(groupPosition);
        } else {
            mScannedDevicesListView.expandGroup(groupPosition, true);

            // Force scrolling to view the children
            mDevicesScrollView.post(new Runnable() {
                @Override
                public void run() {
                    mScannedDevicesListView.scrollToGroup(groupPosition, view, mDevicesScrollView);
                }
            });
        }
    }

    public void onClickDeviceConnect(View view) {
        stopScanning();

        final int scannedDeviceIndex = (Integer) view.getTag();
        if (scannedDeviceIndex < mScannedDevices.size()) {
            mSelectedDeviceData = mScannedDevices.get(scannedDeviceIndex);
            BluetoothDevice device = mSelectedDeviceData.device;

            mBleManager.setBleListener(MainActivity.this);           // Force set listener (could be still checking for updates...)

            if (mSelectedDeviceData.type == BluetoothDeviceData.kType_Uart) {      // if is uart, show all the available activities
                showChooseDeviceServiceDialog(device);
            } else {                          // if no uart, then go directly to info
                Log.d(TAG, "No UART service found. Go to InfoActivity");
                mComponentToStartWhenConnected = InfoActivity.class;
                connect(device);
            }
        } else {
            Log.w(TAG, "onClickDeviceConnect index does not exist: " + scannedDeviceIndex);
        }
    }

    public void onClickScan(View view) {
        boolean isScanning = mScanner != null && mScanner.isScanning();
        if (isScanning) {
            stopScanning();
        } else {
            startScan(null, null);
        }
    }
    // endregion

    // region Scan
    private void startScan(final UUID[] servicesToScan, final String deviceNameToScanFor) {
        Log.d(TAG, "startScan");

        // Stop current scanning (if needed)
        stopScanning();

        // Configure scanning
        BluetoothAdapter bluetoothAdapter = BleUtils.getBluetoothAdapter(getApplicationContext());
        if (BleUtils.getBleStatus(this) != BleUtils.STATUS_BLE_ENABLED) {
            Log.w(TAG, "startScan: BluetoothAdapter not initialized or unspecified address.");
        } else {
            mScanner = new BleDevicesScanner(bluetoothAdapter, servicesToScan, new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    final String deviceName = device.getName();
                    //Log.d(TAG, "Discovered device: " + (deviceName != null ? deviceName : "<unknown>"));

                    BluetoothDeviceData previouslyScannedDeviceData = null;
                    if (deviceNameToScanFor == null || (deviceName != null && deviceName.equalsIgnoreCase(deviceNameToScanFor))) {       // Workaround for bug in service discovery. Discovery filtered by service uuid is not working on Android 4.3, 4.4
                        if (mScannedDevices == null) mScannedDevices = new ArrayList<>();       // Safeguard

                        // Check that the device was not previously found
                        for (BluetoothDeviceData deviceData : mScannedDevices) {
                            if (deviceData.device.getAddress().equals(device.getAddress())) {
                                previouslyScannedDeviceData = deviceData;
                                break;
                            }
                        }

                        BluetoothDeviceData deviceData;
                        if (previouslyScannedDeviceData == null) {
                            // Add it to the mScannedDevice list
                            deviceData = new BluetoothDeviceData();
                            mScannedDevices.add(deviceData);
                        } else {
                            deviceData = previouslyScannedDeviceData;
                        }

                        deviceData.device = device;
                        deviceData.rssi = rssi;
                        deviceData.scanRecord = scanRecord;
                        decodeScanRecords(deviceData);

                        // Update device data
                        long currentMillis = SystemClock.uptimeMillis();
                        if (previouslyScannedDeviceData == null || currentMillis - mLastUpdateMillis > kMinDelayToUpdateUI) {          // Avoid updating when not a new device has been found and the time from the last update is really short to avoid updating UI so fast that it will become unresponsive
                            mLastUpdateMillis = currentMillis;

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateUI();
                                }
                            });
                        }
                    }
                }
            });

            // Start scanning
            mScanner.start();
        }

        // Update UI
        updateUI();
    }

    private void stopScanning() {
        // Stop scanning
        if (mScanner != null) {
            mScanner.stop();
            mScanner = null;
        }

        updateUI();
    }
    // endregion

    private void decodeScanRecords(BluetoothDeviceData deviceData) {
        // based on http://stackoverflow.com/questions/24003777/read-advertisement-packet-in-android
        final byte[] scanRecord = deviceData.scanRecord;

        ArrayList<UUID> uuids = new ArrayList<>();
        byte[] advertisedData = Arrays.copyOf(scanRecord, scanRecord.length);
        int offset = 0;
        deviceData.type = BluetoothDeviceData.kType_Unknown;

        // Check if is an iBeacon ( 0x02, 0x0x1, a flag byte, 0x1A, 0xFF, manufacturer (2bytes), 0x02, 0x15)
        final boolean isBeacon = advertisedData[0] == 0x02 && advertisedData[1] == 0x01 && advertisedData[3] == 0x1A && advertisedData[4] == (byte) 0xFF && advertisedData[7] == 0x02 && advertisedData[8] == 0x15;

        // Check if is an URIBeacon
        final byte[] kUriBeaconPrefix = {0x03, 0x03, (byte) 0xD8, (byte) 0xFE};
        final boolean isUriBeacon = Arrays.equals(Arrays.copyOf(scanRecord, kUriBeaconPrefix.length), kUriBeaconPrefix) && advertisedData[5] == 0x16 && advertisedData[6] == kUriBeaconPrefix[2] && advertisedData[7] == kUriBeaconPrefix[3];

        if (isBeacon) {
            deviceData.type = BluetoothDeviceData.kType_Beacon;

            // Read uuid
            offset = 9;
            UUID uuid = BleUtils.getUuidFromByteArrayBigEndian(Arrays.copyOfRange(scanRecord, offset, offset + 16));
            uuids.add(uuid);
            offset += 16;

            // Skip major minor
            offset += 2 * 2;   // major, minor

            // Read txpower
            final int txPower = advertisedData[offset++];
            deviceData.txPower = txPower;
        } else if (isUriBeacon) {
            deviceData.type = BluetoothDeviceData.kType_UriBeacon;

            // Read txpower
            final int txPower = advertisedData[9];
            deviceData.txPower = txPower;
        } else {
            // Read standard advertising packet
            while (offset < advertisedData.length - 2) {
                // Length
                int len = advertisedData[offset++];
                if (len == 0) break;

                // Type
                int type = advertisedData[offset++];
                if (type == 0) break;

                // Data
//            Log.d(TAG, "record -> lenght: " + length + " type:" + type + " data" + data);

                switch (type) {
                    case 0x02: // Partial list of 16-bit UUIDs
                    case 0x03: {// Complete list of 16-bit UUIDs
                        while (len > 1) {
                            int uuid16 = advertisedData[offset++] & 0xFF;
                            uuid16 |= (advertisedData[offset++] << 8);
                            len -= 2;
                            uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                        }
                        break;
                    }

                    case 0x06:          // Partial list of 128-bit UUIDs
                    case 0x07: {        // Complete list of 128-bit UUIDs
                        while (len >= 16) {
                            try {
                                // Wrap the advertised bits and order them.
                                UUID uuid = BleUtils.getUuidFromByteArraLittleEndian(Arrays.copyOfRange(advertisedData, offset, offset + 16));
                                uuids.add(uuid);

                            } catch (IndexOutOfBoundsException e) {
                                Log.e(TAG, "BlueToothDeviceFilter.parseUUID: " + e.toString());
                            } finally {
                                // Move the offset to read the next uuid.
                                offset += 16;
                                len -= 16;
                            }
                        }
                        break;
                    }

                    case 0x0A: {   // TX Power
                        final int txPower = advertisedData[offset++];
                        deviceData.txPower = txPower;
                        break;
                    }

                    default: {
                        offset += (len - 1);
                        break;
                    }
                }
            }

            // Check if Uart is contained in the uuids
            boolean isUart = false;
            for (UUID uuid : uuids) {
                if (uuid.toString().equalsIgnoreCase(UartInterfaceActivity.UUID_SERVICE)) {
                    isUart = true;
                    break;
                }
            }
            if (isUart) {
                deviceData.type = BluetoothDeviceData.kType_Uart;
            }
        }

        deviceData.uuids = uuids;
    }


    private void updateUI() {
        // Scan button
        boolean isScanning = mScanner != null && mScanner.isScanning();
        mScanButton.setText(getString(isScanning ? R.string.scan_scanbutton_scanning : R.string.scan_scanbutton_scan));

        // Show list and hide "no devices" label
        final boolean isListEmpty = mScannedDevices == null || mScannedDevices.size() == 0;
        mNoDevicesTextView.setVisibility(isListEmpty ? View.VISIBLE : View.GONE);
        mDevicesScrollView.setVisibility(isListEmpty ? View.GONE : View.VISIBLE);

        // devices list
        mScannedDevicesAdapter.notifyDataSetChanged();
    }

    // region ResetBluetoothAdapterListener
    @Override
    public void resetBluetoothCompleted() {
        Log.d(TAG, "Reset completed -> Resume scanning");
        resumeScanning();
    }
    // endregion

    private void launchComponentActivity() {
        // Enable generic attribute service
        final BluetoothGattService genericAttributeService = mBleManager.getGattService(kGenericAttributeService);
        if (genericAttributeService != null) {
            Log.d(TAG, "kGenericAttributeService found. Check if kServiceChangedCharacteristic exists");

            final UUID characteristicUuid = UUID.fromString(kServiceChangedCharacteristic);
            final BluetoothGattCharacteristic dataCharacteristic = genericAttributeService.getCharacteristic(characteristicUuid);
            if (dataCharacteristic != null) {
                Log.d(TAG, "kServiceChangedCharacteristic exists. Enable indication");
                mBleManager.enableIndication(genericAttributeService, kServiceChangedCharacteristic, true);
            } else {
                Log.d(TAG, "Skip enable indications for kServiceChangedCharacteristic. Characteristic not found");
            }
        } else {
            Log.d(TAG, "Skip enable indications for kServiceChangedCharacteristic. kGenericAttributeService not found");
        }

        // Launch activity
        showConnectionStatus(false);
        if (mComponentToStartWhenConnected != null) {
            Intent intent = new Intent(MainActivity.this, mComponentToStartWhenConnected);
            if (mComponentToStartWhenConnected == BeaconActivity.class && mSelectedDeviceData != null) {
                intent.putExtra("rssi", mSelectedDeviceData.rssi);
            }
            startActivityForResult(intent, kActivityRequestCode_ConnectedActivity);
        }
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
        showConnectionStatus(false);
    }

    @Override
    public void onServicesDiscovered() {
        Log.d(TAG, "services discovered");

        // Check if there is a failed installation that was stored to retry
        boolean isFailedInstallationDetected = FirmwareUpdater.isFailedInstallationRecoveryAvailable(this, mBleManager.getConnectedDeviceAddress());
        if (isFailedInstallationDetected) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Failed installation detected");
                    // Ask user if should update
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.scan_failedupdatedetected_title)
                            .setMessage(R.string.scan_failedupdatedetected_message)
                            .setPositiveButton(R.string.scan_failedupdatedetected_ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    showConnectionStatus(false);        // hide current dialogs because software update will display a dialog
                                    stopScanning();

                                    mFirmwareUpdater.startFailedInstallationRecovery(MainActivity.this);
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    FirmwareUpdater.clearFailedInstallationRecoveryParams(MainActivity.this);
                                    launchComponentActivity();
                                }
                            })
                            .setCancelable(false)
                            .show();
                }
            });
        } else {
            // Check if a firmware update is available
            boolean isCheckingFirmware = false;
            if (mFirmwareUpdater != null) {
                // Don't bother the user waiting for checks if the latest connected device was this one too
                String deviceAddress = mBleManager.getConnectedDeviceAddress();
                if (!deviceAddress.equals(mLatestCheckedDeviceAddress)) {
                    mLatestCheckedDeviceAddress = deviceAddress;

                    // Check if should update device software
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showGettingUpdateInfoState();
                        }
                    });
                    mFirmwareUpdater.checkFirmwareUpdatesForTheCurrentConnectedDevice();        // continues asynchronously in onFirmwareUpdatesChecked
                    isCheckingFirmware = true;
                } else {
                    Log.d(TAG, "Updates: Device already checked previously. Skipping...");
                }
            }

            if (!isCheckingFirmware) {
                onFirmwareUpdatesChecked(false, null, null, null);
            }
        }
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

    // region SoftwareUpdateManagerListener
    @Override
    public void onFirmwareUpdatesChecked(boolean isUpdateAvailable, final ReleasesParser.FirmwareInfo latestRelease, FirmwareUpdater.DeviceInfoData deviceInfoData, Map<String, ReleasesParser.BoardInfo> allReleases) {
        mBleManager.setBleListener(this);           // Restore listener

        if (isUpdateAvailable) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Ask user if should update
                    String message = String.format(getString(R.string.scan_softwareupdate_messageformat), latestRelease.version);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.scan_softwareupdate_title)
                            .setMessage(message)
                            .setPositiveButton(R.string.scan_softwareupdate_install, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    showConnectionStatus(false);        // hide current dialogs because software update will display a dialog
                                    stopScanning();
                                    //BluetoothDevice device = mBleManager.getConnectedDevice();
                                    mFirmwareUpdater.downloadAndInstall(MainActivity.this, latestRelease);
                                }
                            })
                            .setNeutralButton(R.string.scan_softwareupdate_notnow, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    launchComponentActivity();
                                }
                            })
                            .setNegativeButton(R.string.scan_softwareupdate_dontask, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mFirmwareUpdater.ignoreVersion(latestRelease.version);
                                    launchComponentActivity();
                                }
                            })
                            .setCancelable(false)
                            .show();
                }
            });
        } else {
            Log.d(TAG, "onFirmwareUpdatesChecked: No software update available");
            launchComponentActivity();
        }
    }

    @Override
    public void onUpdateCancelled() {
        Log.d(TAG, "Software version installation cancelled");

        mLatestCheckedDeviceAddress = null;

        mScannedDevices.clear();
        startScan(null, null);
    }

    @Override
    public void onUpdateCompleted() {
        Log.d(TAG, "Software version installation completed successfully");

        Toast.makeText(this, R.string.scan_softwareupdate_completed, Toast.LENGTH_LONG).show();

        mScannedDevices.clear();
        startScan(null, null);
    }

    @Override
    public void onUpdateFailed(boolean isDownloadError) {
        Log.d(TAG, "Software version installation failed");
        Toast.makeText(this, isDownloadError ? R.string.scan_softwareupdate_downloaderror : R.string.scan_softwareupdate_updateerror, Toast.LENGTH_LONG).show();

        mLatestCheckedDeviceAddress = null;

        mScannedDevices.clear();
        startScan(null, null);
    }

    @Override
    public void onUpdateDeviceDisconnected() {

        // Update UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onDisconnected();

                mLatestCheckedDeviceAddress = null;

                mScannedDevices.clear();
                startScan(null, null);

            }
        });

    }
    // endregion

    // region Helpers
    private class BluetoothDeviceData {
        public BluetoothDevice device;
        public int rssi;
        public byte[] scanRecord;

        // Decoded scan record (update R.array.scan_devicetypes if this list is modified)
        public static final int kType_Unknown = 0;
        public static final int kType_Uart = 1;
        public static final int kType_Beacon = 2;
        public static final int kType_UriBeacon = 3;

        public int type;
        public int txPower;
        public ArrayList<UUID> uuids;
    }
    //endregion

    // region adapters
    private class ExpandableListAdapter extends BaseExpandableListAdapter {
        // Constants for common child
        private static final int kChildCommon_Name = 0;
        private static final int kChildCommon_Address = 1;
        private static final int kChildCommon_Services = 2;
        private static final int kChildCommon_TXPower = 3;

        // Constants for beacon child
        private static final int kChildBeacon_Name = 0;
        private static final int kChildBeacon_Address = 1;
        private static final int kChildBeacon_Manufacturer = 2;
        private static final int kChildBeacon_UUID = 3;
        private static final int kChildBeacon_Major = 4;
        private static final int kChildBeacon_Minor = 5;
        private static final int kChildBeacon_TXPower = 6;

        // Constants for uribeacon child
        private static final int kChildUriBeacon_Name = 0;
        private static final int kChildUriBeacon_Address = 1;
        private static final int kChildUriBeacon_Uri = 2;
        private static final int kChildUriBeacon_TXPower = 3;

        // Data
        private ArrayList<BluetoothDeviceData> mBluetoothDevices;

        private class GroupViewHolder {
            TextView nameTextView;
            TextView descriptionTextView;
            ImageView rssiImageView;
            TextView rssiTextView;
            Button connectButton;

        }

        public ExpandableListAdapter(ArrayList<BluetoothDeviceData> bluetoothDevices) {
            mBluetoothDevices = bluetoothDevices;
        }

        @Override
        public int getGroupCount() {
            int count = 0;
            if (mBluetoothDevices != null) {
                count = mBluetoothDevices.size();
            }
            return count;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            BluetoothDeviceData deviceData = mBluetoothDevices.get(groupPosition);
            switch (deviceData.type) {
                case BluetoothDeviceData.kType_Beacon:
                    return 7;       // Local name, Address, Manufacturer,  UUID, Major, Minor and TX Power Level

                case BluetoothDeviceData.kType_UriBeacon:
                    return 4;       // Local name, Address, Uri and TX Power Level

                default:
                    return 4;   // Local name, Address, Service UUIDs and TX Power Level
            }
        }

        @Override
        public Object getGroup(int groupPosition) {
            return mBluetoothDevices.get(groupPosition);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            BluetoothDeviceData deviceData = mBluetoothDevices.get(groupPosition);

            switch (deviceData.type) {
                case BluetoothDeviceData.kType_Beacon:
                    return getChildBeacon(deviceData, childPosition);

                case BluetoothDeviceData.kType_UriBeacon:
                    return getChildUriBeacon(deviceData, childPosition);

                default:
                    return getChildCommon(deviceData, childPosition);
            }
        }


        private Object getChildUriBeacon(BluetoothDeviceData deviceData, int childPosition) {
            switch (childPosition) {
                case kChildUriBeacon_Name: {
                    String name = deviceData.device.getName();
                    return getString(R.string.scan_device_localname) + ": " + (name == null ? "" : name);
                }
                case kChildUriBeacon_Address: {
                    String address = deviceData.device.getAddress();
                    return getString(R.string.scan_device_address) + ": " + (address == null ? "" : address);
                }
                case kChildUriBeacon_Uri: {
                    String uri = UriBeaconUtils.getUriFromAdvertisingPacket(deviceData.scanRecord);
                    return getString(R.string.scan_device_uribeacon_uri) + ": " + uri;
                }
                case kChildUriBeacon_TXPower: {
                    return getString(R.string.scan_device_txpower) + ": " + deviceData.txPower;
                }
                default:
                    return null;
            }
        }

        private Object getChildCommon(BluetoothDeviceData deviceData, int childPosition) {
            switch (childPosition) {
                case kChildCommon_Name: {
                    String name = deviceData.device.getName();
                    return getString(R.string.scan_device_localname) + ": " + (name == null ? "" : name);
                }
                case kChildCommon_Address: {
                    String address = deviceData.device.getAddress();
                    return getString(R.string.scan_device_address) + ": " + (address == null ? "" : address);
                }
                case kChildCommon_Services: {
                    StringBuilder text = new StringBuilder();
                    if (deviceData.uuids != null) {
                        int i = 0;
                        for (UUID uuid : deviceData.uuids) {
                            if (i > 0) text.append(", ");
                            text.append(uuid.toString().toUpperCase());
                            i++;
                        }
                    }
                    return getString(R.string.scan_device_services) + ": " + text;
                }
                case kChildCommon_TXPower: {
                    return getString(R.string.scan_device_txpower) + ": " + deviceData.txPower;
                }
                default:
                    return null;
            }
        }

        private Object getChildBeacon(BluetoothDeviceData deviceData, int childPosition) {
            switch (childPosition) {
                case kChildBeacon_Name: {
                    String name = deviceData.device.getName();
                    return getString(R.string.scan_device_localname) + ": " + (name == null ? "" : name);
                }
                case kChildBeacon_Address: {
                    String address = deviceData.device.getAddress();
                    return getString(R.string.scan_device_address) + ": " + (address == null ? "" : address);
                }
                case kChildBeacon_Manufacturer: {
                    final byte[] manufacturerBytes = {deviceData.scanRecord[6], deviceData.scanRecord[5]};      // Little endan
                    String manufacturer = BleUtils.bytesToHex(manufacturerBytes);

                    // Check if the manufacturer is known, and replace the id for a name
                    String kKnownManufacturers[] = getResources().getStringArray(R.array.beacon_manufacturers_ids);
                    int knownIndex = Arrays.asList(kKnownManufacturers).indexOf(manufacturer);
                    if (knownIndex >= 0) {
                        String kManufacturerNames[] = getResources().getStringArray(R.array.beacon_manufacturers_names);
                        manufacturer = kManufacturerNames[knownIndex];
                    }

                    return getString(R.string.scan_device_beacon_manufacturer) + ": " + (manufacturer == null ? "" : manufacturer);
                }

                case kChildBeacon_UUID: {
                    StringBuilder text = new StringBuilder();
                    if (deviceData.uuids != null && deviceData.uuids.size() == 1) {
                        UUID uuid = deviceData.uuids.get(0);
                        text.append(uuid.toString().toUpperCase());
                    }
                    return getString(R.string.scan_device_uuid) + ": " + text;
                }

                case kChildBeacon_Major: {
                    final byte[] majorBytes = {deviceData.scanRecord[25], deviceData.scanRecord[26]};           // Big endian
                    String major = BleUtils.bytesToHex(majorBytes);
                    return getString(R.string.scan_device_beacon_major) + ": " + major;
                }

                case kChildBeacon_Minor: {
                    final byte[] minorBytes = {deviceData.scanRecord[27], deviceData.scanRecord[28]};           // Big endian
                    String minor = BleUtils.bytesToHex(minorBytes);
                    return getString(R.string.scan_device_beacon_minor) + ": " + minor;
                }

                case kChildBeacon_TXPower: {
                    return getString(R.string.scan_device_txpower) + ": " + deviceData.txPower;
                }
                default:
                    return null;
            }
        }


        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            GroupViewHolder holder;

            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.layout_scan_item_title, parent, false);

                holder = new GroupViewHolder();

                holder.nameTextView = (TextView) convertView.findViewById(R.id.nameTextView);
                holder.descriptionTextView = (TextView) convertView.findViewById(R.id.descriptionTextView);
                holder.rssiImageView = (ImageView) convertView.findViewById(R.id.rssiImageView);
                holder.rssiTextView = (TextView) convertView.findViewById(R.id.rssiTextView);
                holder.connectButton = (Button) convertView.findViewById(R.id.connectButton);

                convertView.setTag(R.string.scan_tag_id, holder);

            } else {
                holder = (GroupViewHolder) convertView.getTag(R.string.scan_tag_id);
            }

            convertView.setTag(groupPosition);
            holder.connectButton.setTag(groupPosition);

            BluetoothDeviceData deviceData = mBluetoothDevices.get(groupPosition);
            String deviceName = deviceData.device.getName();
            holder.nameTextView.setText(deviceName != null ? deviceName : deviceData.device.getAddress());

            holder.descriptionTextView.setVisibility(deviceData.type != BluetoothDeviceData.kType_Unknown ? View.VISIBLE : View.INVISIBLE);
            holder.descriptionTextView.setText(getResources().getStringArray(R.array.scan_devicetypes)[deviceData.type]);
            holder.rssiTextView.setText(deviceData.rssi == 127 ? getString(R.string.scan_device_rssi_notavailable) : String.valueOf(deviceData.rssi));

            int rrsiDrawableResource = getDrawableIdForRssi(deviceData.rssi);
            holder.rssiImageView.setImageResource(rrsiDrawableResource);

            return convertView;
        }

        private int getDrawableIdForRssi(int rssi) {
            int index;
            if (rssi == 127 || rssi <= -84) {       // 127 reserved for RSSI not available
                index = 0;
            } else if (rssi <= -72) {
                index = 1;
            } else if (rssi <= -60) {
                index = 2;
            } else if (rssi <= -48) {
                index = 3;
            } else {
                index = 4;
            }

            final int kSignalDrawables[] = {
                    R.drawable.signalstrength0,
                    R.drawable.signalstrength1,
                    R.drawable.signalstrength2,
                    R.drawable.signalstrength3,
                    R.drawable.signalstrength4};
            return kSignalDrawables[index];
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.layout_scan_item_child, parent, false);
            }

            // We don't expect many items so for clarity just find the views each time instead of using a ViewHolder
            TextView textView = (TextView) convertView.findViewById(R.id.textView);
            String text = (String) getChild(groupPosition, childPosition);
            textView.setText(text);

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }
    //endregion

    // region DataFragment
    public static class DataFragment extends Fragment {
        private ArrayList<BluetoothDeviceData> mScannedDevices;
        private Class<?> mComponentToStartWhenConnected;
        private boolean mShouldEnableWifiOnQuit;
        private FirmwareUpdater mFirmwareUpdater;
        private String mLatestCheckedDeviceAddress;
        private BluetoothDeviceData mSelectedDeviceData;

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
            fm.beginTransaction().add(mRetainedDataFragment, TAG).commitAllowingStateLoss();        // http://stackoverflow.com/questions/7575921/illegalstateexception-can-not-perform-this-action-after-onsaveinstancestate-h

            mScannedDevices = new ArrayList<>();

        } else {
            // Restore status
            mScannedDevices = mRetainedDataFragment.mScannedDevices;
            mComponentToStartWhenConnected = mRetainedDataFragment.mComponentToStartWhenConnected;
            mShouldEnableWifiOnQuit = mRetainedDataFragment.mShouldEnableWifiOnQuit;
            mFirmwareUpdater = mRetainedDataFragment.mFirmwareUpdater;
            mLatestCheckedDeviceAddress = mRetainedDataFragment.mLatestCheckedDeviceAddress;
            mSelectedDeviceData = mRetainedDataFragment.mSelectedDeviceData;

            if (mFirmwareUpdater != null) {
                mFirmwareUpdater.changedParentActivity(this);       // set the new activity
            }
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mScannedDevices = mScannedDevices;
        mRetainedDataFragment.mComponentToStartWhenConnected = mComponentToStartWhenConnected;
        mRetainedDataFragment.mShouldEnableWifiOnQuit = mShouldEnableWifiOnQuit;
        mRetainedDataFragment.mFirmwareUpdater = mFirmwareUpdater;
        mRetainedDataFragment.mLatestCheckedDeviceAddress = mLatestCheckedDeviceAddress;
        mRetainedDataFragment.mSelectedDeviceData = mSelectedDeviceData;
    }
    // endregion
}