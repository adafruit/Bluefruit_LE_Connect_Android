package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleDevicesScanner;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleServiceListener;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;


public class MainActivity extends ActionBarActivity implements BleServiceListener {
    // Log
    private final static String TAG = MainActivity.class.getSimpleName();
    private final static long kMinDelayToUpdateUI = 800;

    // UI
    private ExpandableHeightExpandableListView mScannedDevicesListView;
    private ExpandableListAdapter mScannedDevicesAdapter;
    private Button mScanButton;
    private TextView mConnectionStatusView;
    private long mLastUpdateMillis;

    // Data
    private BleDevicesScanner mScanner;
    private ArrayList<BluetoothDeviceData> mScannedDevices;
    private boolean mIsScanPaused;

    private Class<?> mComponentToStartWhenConnected;
    private BleManager mBleManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init variables
        mIsScanPaused = true;     // so the scanning starts automatically
        mScannedDevices = new ArrayList<BluetoothDeviceData>();

        mBleManager = BleManager.getInstance(this);

        // UI
        mScannedDevicesListView = (ExpandableHeightExpandableListView) findViewById(R.id.scannedDevicesListView);
        mScannedDevicesAdapter = new ExpandableListAdapter(this, mScannedDevices);
        mScannedDevicesListView.setAdapter(mScannedDevicesAdapter);
        mScannedDevicesListView.setExpanded(true);

        mScanButton = (Button) findViewById(R.id.scanButton);

        mConnectionStatusView = (TextView) findViewById(R.id.connectionStatusTextView);
        showConnectionStatus(false);
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
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Set listener
        mBleManager.setBleListener(this);

        // If was connected, disconnect
        mBleManager.disconnect();

        // Resume scanning if was active previously
        if (mIsScanPaused) {
            mIsScanPaused = false;
            startScan(null, null);
        }
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

    private void showChooseDeviceServiceDialog(final BluetoothDevice device) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connect to " + device.getName())
                .setItems(R.array.scan_connectservice_items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Class<?> componentClass = null;
                        switch (which) {
                            case 0: { // Info
                                mComponentToStartWhenConnected = InfoActivity.class;
                                break;
                            }
                            case 1: { // Uart
                                mComponentToStartWhenConnected = UartActivity.class;
                                break;
                            }
                            case 2: { // PinIO
                                mComponentToStartWhenConnected = PinIOActivity.class;
                                break;
                            }
                            case 3: { // Controller
                                mComponentToStartWhenConnected = ControllerActivity.class;
                                break;
                            }
                        }

                        if (mComponentToStartWhenConnected != null) {
                            connect(device);
                        }
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void connect(BluetoothDevice device) {
        boolean connecting = mBleManager.connect(device.getAddress());
        if (connecting) {
            showConnectionStatus(true);
        }
    }

    private void startHelp() {
        // Launch app hep activity
        Intent intent = new Intent(this, MainHelpActivity.class);
        startActivity(intent);
    }

    private void showConnectionStatus(boolean enable) {
        mConnectionStatusView.setVisibility(enable ? View.VISIBLE : View.GONE);
    }

    // region Actions
    public void onClickScannedDevice(View view) {
        int groupPosition = (Integer) view.getTag(R.string.scan_tag_id);
        if (mScannedDevicesListView.isGroupExpanded(groupPosition)) {
            mScannedDevicesListView.collapseGroup(groupPosition);
        } else {
            // Expand this, Collapse the rest
            int len = mScannedDevicesAdapter.getGroupCount();
            for (int i = 0; i < len; i++) {
                if (i != groupPosition) {
                    mScannedDevicesListView.collapseGroup(i);
                }
            }

            mScannedDevicesListView.expandGroup(groupPosition, true);

        }
    }

    public void onClickDeviceConnect(View view) {
        stopScanning();

        int scannedDeviceIndex = (Integer) view.getTag();
        BluetoothDevice device = mScannedDevices.get(scannedDeviceIndex).device;
        showChooseDeviceServiceDialog(device);
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
        stopScanning();

        // Configure scanning
        BluetoothAdapter bluetoothAdapter = BleUtils.getBluetoothAdapter(getApplicationContext());
        mScanner = new BleDevicesScanner(bluetoothAdapter, servicesToScan, new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                final String deviceName = device.getName();
                //Log.d(TAG, "Discovered device: " + (deviceName != null ? deviceName : "<unknown>"));

                BluetoothDeviceData previouslyScannedDeviceData = null;
                if (deviceNameToScanFor == null || (deviceName != null && deviceName.equalsIgnoreCase(deviceNameToScanFor))) {       // Workaround for bug in service discovery. Discovery filtered by service uuid is not working on Android 4.3, 4.4
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
                                // Notify adapter
                                mScannedDevicesAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
            }
        });

        // Start scanning
        mScanner.start();

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
        while (offset < advertisedData.length -2) {
            // Lenght
            int len = advertisedData[offset++];
            if (len == 0) break;

            // Type
            int type = advertisedData[offset++];
            if (type == 0) break;

            // Data
//            Log.d(TAG, "record -> lenght: " + length + " type:" + type + " data" + data);

            switch(type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: {// Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++] & 0xFF;
                        uuid16 |= (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format( "%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                }

                case 0x06:          // Partial list of 128-bit UUIDs
                case 0x07: {        // Complete list of 128-bit UUIDs
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            uuids.add(new UUID(leastSignificantBit,  mostSignificantBit));
                        } catch (IndexOutOfBoundsException e) {
                            // Defensive programming.
                            Log.e("BlueToothDeviceFilter.parseUUID", e.toString());
                            continue;
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                }

                case 0x0A: {   // TX Power
                    int low = advertisedData[offset++] & 0xFF;

//                    String dataBinary = toBinary(value);
//                    short txPower = (short) Integer.parseInt(dataBinary, 2);
                    deviceData.txPower = low;
                   // Log.d(TAG, "tx power: " + data[0] + ":" + dataBinary + ":" + txPower);
                    break;
                }

                default: {
                    offset += (len - 1);
                    break;
                }
            }

            /*
            if (type == 0x07) {                 // UUID


                try {
                    String decodedRecord = new String(data,"UTF-8");
                    Log.d(TAG, "blah: "+decodedRecord);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                String hex = BleUtils.bytesToHex(data);
                deviceData.uuidString = hex.substring(0, 0 + 8) + "-" + hex.substring(8, 8 + 4) + "-" + hex.substring(12, 12 + 4) + "-" + hex.substring(16, 16 + 4) + "-" + hex.substring(20, 20 + 12);
            } else if (type == 0x09) {          // Device name
                String name = "";
                try {
                    name = new String(data, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "name: " + name);
            } else if (type == 0x0A) {          // TX Power

                String dataBinary = toBinary(data);
                short txPower = (short) Integer.parseInt(dataBinary, 2);
                deviceData.txPower = txPower;
                Log.d(TAG, "tx power: " + data[0] + ":" + dataBinary + ":" + txPower);
            }

            // Next
            offset += length;
            */
        }


        deviceData.uuids = uuids;
    }

    private String toBinary(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * Byte.SIZE);
        for (int i = 0; i < Byte.SIZE * bytes.length; i++)
            sb.append((bytes[i / Byte.SIZE] << i % Byte.SIZE & 0x80) == 0 ? '0' : '1');
        return sb.toString();
    }

    private void updateUI() {
        // Scan button
        boolean isScanning = mScanner != null && mScanner.isScanning();
        mScanButton.setText(getString(isScanning ? R.string.scan_scanbutton_scanning : R.string.scan_scanbutton_scan));

    }

    // region BleServiceListener
    @Override
    public void onConnected() {

        // run on main thread
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showConnectionStatus(false);

                // Launch activity
                if (mComponentToStartWhenConnected != null) {
                    Intent intent = new Intent(MainActivity.this, mComponentToStartWhenConnected);
                    startActivity(intent);
                }
            }
        });
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
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {
    }

    // endregion

    // region Helpers
    private class BluetoothDeviceData {
        public BluetoothDevice device;
        public int rssi;
        byte[] scanRecord;

        // Decoded scan record
        String uuidString;
        int txPower;
        ArrayList<UUID> uuids;
    }
    //endregion

    // region adapters
    private class ExpandableListAdapter extends BaseExpandableListAdapter {
        private static final int kChild_Name = 0;
        private static final int kChild_UUIDs = 1;
        private static final int kChild_Services = 2;
        private static final int kChild_TXPower = 3;

        private Activity mActivity;
        private ArrayList<BluetoothDeviceData> mBluetoothDevices;

        private class GroupViewHolder {
            TextView nameTextView;
            TextView descriptionTextView;
            ImageView rssiImageView;
            TextView rssiTextView;
            Button connectButton;

        }

        public ExpandableListAdapter(Activity activity, ArrayList<BluetoothDeviceData> bluetoothDevices) {
            mActivity = activity;
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
            return 4;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return mBluetoothDevices.get(groupPosition);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            BluetoothDeviceData deviceData = mBluetoothDevices.get(groupPosition);
            switch (childPosition) {
                case kChild_Name:
                    return getString(R.string.scan_device_localname) + ": " + deviceData.device.getName();
                case kChild_UUIDs: {
                    String uuid = deviceData.uuidString;
                    return getString(R.string.scan_device_uuid) + ": " + (uuid == null ? "" : uuid);
                }
                case kChild_Services: {
                    StringBuilder text = new StringBuilder();
                    /*
                    ParcelUuid[] uuids = deviceData.device.getUuids();
                    if (uuids != null) {
                        for (ParcelUuid uuid : uuids) {
                            text.append(uuid);
                        }
                    }
                    */
                    if (deviceData.uuids != null) {
                        int i=0;
                        for (UUID uuid : deviceData.uuids) {
                            if (i>0) text.append(", ");
                            text.append(uuid.toString().toUpperCase());
                            i++;
                        }
                    }
                    return getString(R.string.scan_device_services) + ": " + text;
                }
                case kChild_TXPower: {
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
                convertView = mActivity.getLayoutInflater().inflate(R.layout.layout_scan_item_title, parent, false);

                holder = new GroupViewHolder();

                holder.nameTextView = (TextView) convertView.findViewById(R.id.nameTextView);
                holder.descriptionTextView = (TextView) convertView.findViewById(R.id.descriptionTextView);
                holder.rssiImageView = (ImageView) convertView.findViewById(R.id.rssiImageView);
                holder.rssiTextView = (TextView) convertView.findViewById(R.id.rssiTextView);
                holder.connectButton = (Button) convertView.findViewById(R.id.connectButton);
                holder.connectButton.setTag(new Integer(groupPosition));

                convertView.setTag(holder);

            } else {
                holder = (GroupViewHolder) convertView.getTag();
            }

            convertView.setTag(R.string.scan_tag_id, new Integer(groupPosition));

            BluetoothDeviceData deviceData = mBluetoothDevices.get(groupPosition);
            String deviceName = deviceData.device.getName();
            holder.nameTextView.setText(deviceName != null ? deviceName : deviceData.device.getAddress());

            boolean isUart = false;
            for(UUID uuid : deviceData.uuids) {
                if (uuid.toString().equalsIgnoreCase(UartInterfaceActivity.UUID_SERVICE)) {
                    isUart = true;
                    break;
                }
            }
            holder.descriptionTextView.setVisibility(isUart ? View.VISIBLE : View.INVISIBLE);
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

            final int kSignalDrawables[] = {R.drawable.signalstrength0, R.drawable.signalstrength1, R.drawable.signalstrength2, R.drawable.signalstrength3, R.drawable.signalstrength4};
            return kSignalDrawables[index];
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mActivity.getLayoutInflater().inflate(R.layout.layout_scan_item_child, parent, false);
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
    //endredgion

}
