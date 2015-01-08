package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleServiceListener;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.ble.KnownUUIDs;
import com.adafruit.bluefruit.le.connect.ui.ExpandableHeightListView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class InfoActivity extends ActionBarActivity implements BleServiceListener {
    // Log
    private final static String TAG = InfoActivity.class.getSimpleName();

    // Constants
    private final static int kDataFormatCount = 2;

    // UI
    private ExpandableListView mInfoListView;
    private ExpandableListAdapter mInfoListAdapter;
    private View mWaitView;

    // Data
    private BleManager mBleManager;
    private List<ElementPath> mServicesList;                             // List with service names
    private Map<String, List<ElementPath>> mCharacteristicsMap;          // Map with characteristics for service keys
    private Map<String, List<ElementPath>> mDescriptorsMap;              // Map with descriptors for characteristic keys
    private Map<String, byte[]> mValuesMap;                              // Map with values for characteristic and descriptor keys¡

    private DataFragment mRetainedDataFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        mBleManager = BleManager.getInstance(this);

        // Init variables
        restoreRetainedDataFragment();

        // UI
        mWaitView = findViewById(R.id.waitView);

        mInfoListView = (ExpandableListView) findViewById(R.id.infoListView);
        mInfoListAdapter = new ExpandableListAdapter(this, mServicesList, mCharacteristicsMap, mDescriptorsMap, mValuesMap);
        mInfoListView.setAdapter(mInfoListAdapter);

        BluetoothDevice device = mBleManager.getConnectedDevice();
        if (device != null) {
            TextView nameTextView = (TextView) findViewById(R.id.nameTextView);
            boolean isNameDefined = device.getName() != null;
            nameTextView.setText(device.getName());
            nameTextView.setVisibility(isNameDefined ? View.VISIBLE : View.GONE);

            TextView addressTextView = (TextView) findViewById(R.id.addressTextView);
            addressTextView.setText(getString(R.string.scan_device_address) + ": " + device.getAddress());

            onServicesDiscovered();
        } else {
            finish();       // Device disconnected for unknown reason
        }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_info, menu);
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
        intent.putExtra("title", getString(R.string.info_help_title));
        intent.putExtra("help", "info_help.html");
        startActivity(intent);
    }

    // region Actions

    //endregion

    public void onClickInfoService(View view) {
        int groupPosition = (Integer) view.getTag();
        if (mInfoListView.isGroupExpanded(groupPosition)) {
            mInfoListView.collapseGroup(groupPosition);
        } else {
            // Expand this, Collapse the rest
            int len = mInfoListAdapter.getGroupCount();
            for (int i = 0; i < len; i++) {
                if (i != groupPosition) {
                    mInfoListView.collapseGroup(i);
                }
            }

            mInfoListView.expandGroup(groupPosition, true);
        }
    }


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

        // Remove old data
        mServicesList.clear();
        mCharacteristicsMap.clear();
        mDescriptorsMap.clear();
        mValuesMap.clear();

        // Services
        List<BluetoothGattService> services = mBleManager.getSupportedGattServices();
        for (BluetoothGattService service : services) {
            String serviceUuid = service.getUuid().toString();
            int instanceId = service.getInstanceId();
            String serviceName = KnownUUIDs.getServiceName(serviceUuid);
            String finalServiceName = serviceName != null ? serviceName : serviceUuid;
            ElementPath serviceElementPath = new ElementPath(serviceUuid, instanceId, null, null, finalServiceName, serviceUuid);
            mServicesList.add(serviceElementPath);

            // Characteristics
            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            List<ElementPath> characteristicNamesList = new ArrayList<>(characteristics.size());
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                String characteristicUuid = characteristic.getUuid().toString();
                String characteristicName = KnownUUIDs.getCharacteristicName(characteristicUuid);
                String finalCharacteristicName = characteristicName != null ? characteristicName : characteristicUuid;
                ElementPath characteristicElementPath = new ElementPath(serviceUuid, instanceId, characteristicUuid, null, finalCharacteristicName, characteristicUuid);
                characteristicNamesList.add(characteristicElementPath);

                // Read characteristic
                if (mBleManager.isCharacteristicReadable(service, characteristicUuid)) {
                    mBleManager.readCharacteristic(service, characteristicUuid);
                }

                // Descriptors
                List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
                List<ElementPath> descriptorNamesList = new ArrayList<>(descriptors.size());
                for (BluetoothGattDescriptor descriptor : descriptors) {
                    String descriptorUuid = descriptor.getUuid().toString();
                    String descriptorName = KnownUUIDs.getDescriptorName(descriptorUuid);
                    String finalDescriptorName = descriptorName != null ? descriptorName : descriptorUuid;
                    descriptorNamesList.add(new ElementPath(serviceUuid, instanceId, characteristicUuid, descriptorUuid, finalDescriptorName, descriptorUuid));

                    // Read descriptor
                    mBleManager.readDescriptor(service, characteristicUuid, descriptorUuid);
                }

                mDescriptorsMap.put(characteristicElementPath.getKey(), descriptorNamesList);
            }
            mCharacteristicsMap.put(serviceElementPath.getKey(), characteristicNamesList);

        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUI();

            }
        });
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        BluetoothGattService service = characteristic.getService();
        String key = new ElementPath(service.getUuid().toString(), service.getInstanceId(), characteristic.getUuid().toString(), null, null, null).getKey();
        mValuesMap.put(key, characteristic.getValue());

        // Update UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUI();

            }
        });
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {
        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
        BluetoothGattService service = characteristic.getService();
        String key = new ElementPath(service.getUuid().toString(), service.getInstanceId(), characteristic.getUuid().toString(), descriptor.toString(), null, null).getKey();
        mValuesMap.put(key, descriptor.getValue());

        // Update UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUI();
            }
        });

    }

    private void updateUI() {
        mInfoListAdapter.notifyDataSetChanged();

        // Show progress view if data is not ready yet
        final boolean isDataEmpty = mInfoListView.getChildCount()==0;
        mWaitView.setVisibility(isDataEmpty?View.VISIBLE:View.GONE);
    }

    // region adapters
    private class ElementPath {
        public String serviceUUID;
        public int serviceInstance;
        public String characteristicUUID;
        public String descriptorUUID;
        public String name;
        public String uuid;
        public boolean isShowingName = true;

        public int dataFormat = 0;

        public ElementPath(String serviceUUID, int serviceInstance, String characteristicUUID, String descriptorUUID, String name, String uuid) {
            this.serviceUUID = serviceUUID;
            this.serviceInstance = serviceInstance;
            this.characteristicUUID = characteristicUUID;
            this.descriptorUUID = descriptorUUID;
            this.name = name;
            this.uuid = uuid;
        }

        public String getKey() {
            return serviceUUID + serviceInstance + characteristicUUID + descriptorUUID;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private class ExpandableListAdapter extends BaseExpandableListAdapter {
        private Activity mActivity;
        private List<ElementPath> mServices;
        private Map<String, List<ElementPath>> mCharacteristics;
        private Map<String, List<ElementPath>> mDescriptors;
        private Map<String, byte[]> mValuesMap;

        public ExpandableListAdapter(Activity activity, List<ElementPath> services, Map<String, List<ElementPath>> characteristics, Map<String, List<ElementPath>> descriptors, Map<String, byte[]> valuesMap) {
            mActivity = activity;
            mServices = services;
            mCharacteristics = characteristics;
            mDescriptors = descriptors;
            mValuesMap = valuesMap;
        }

        @Override
        public int getGroupCount() {
            return mServices.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            List<ElementPath> items = mCharacteristics.get(mServices.get(groupPosition).getKey());
            int count = 0;
            if (items != null) {
                count = items.size();
            }
            return count;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return mServices.get(groupPosition);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return mCharacteristics.get(mServices.get(groupPosition).getKey()).get(childPosition);
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
            if (convertView == null) {
                convertView = mActivity.getLayoutInflater().inflate(R.layout.layout_info_item_service, parent, false);
            }

            // Tag
            convertView.setTag(groupPosition);

            // UI
            TextView item = (TextView) convertView.findViewById(R.id.nameTextView);
            ElementPath elementPath = (ElementPath) getGroup(groupPosition);
            item.setText(elementPath.name);

            return convertView;
        }

        @Override
        public View getChildView(final int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            ElementPath elementPath = (ElementPath) getChild(groupPosition, childPosition);

            if (convertView == null) {
                convertView = mActivity.getLayoutInflater().inflate(R.layout.layout_info_item_characteristic, parent, false);
            }

            BluetoothGattService service = mBleManager.getGattService(elementPath.serviceUUID, elementPath.serviceInstance);
            boolean isReadable = elementPath.characteristicUUID != null && mBleManager.isCharacteristicReadable(service, elementPath.characteristicUUID);

            // Tag
            convertView.setTag(elementPath);

            // Name
            TextView nameTextView = (TextView) convertView.findViewById(R.id.nameTextView);
            nameTextView.setText(elementPath.isShowingName ? elementPath.name : elementPath.uuid);

            // Value
            TextView valueTextView = (TextView) convertView.findViewById(R.id.valueTextView);
            byte[] value = mValuesMap.get(elementPath.getKey());
            String valueString = getValueFormatted(value, elementPath.dataFormat);
            valueTextView.setText(valueString);
            valueTextView.setVisibility(valueString == null ? View.GONE : View.VISIBLE);

            // Update button
            ImageButton updateButton = (ImageButton) convertView.findViewById(R.id.updateButton);
            updateButton.setVisibility(isReadable ? View.VISIBLE : View.GONE);
            updateButton.setTag(elementPath);

            // Notify button
            ImageButton notifyButton = (ImageButton) convertView.findViewById(R.id.notifyButton);
            boolean isNotifiable = elementPath.characteristicUUID != null && elementPath.descriptorUUID == null && mBleManager.isCharacteristicNotifiable(service, elementPath.characteristicUUID);
            notifyButton.setVisibility(isNotifiable ? View.VISIBLE : View.GONE);
            notifyButton.setTag(elementPath);

            // List setup
            ExpandableHeightListView listView = (ExpandableHeightListView) convertView.findViewById(R.id.descriptorsListView);
            listView.setExpanded(true);

            // Descriptors
            List<ElementPath> descriptorNamesList = mDescriptors.get(elementPath.getKey());
            DescriptorAdapter adapter = new DescriptorAdapter(mActivity, R.layout.layout_info_item_descriptor, descriptorNamesList);
            listView.setAdapter(adapter);

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }

    private class DescriptorAdapter extends ArrayAdapter<ElementPath> {
        Activity mActivity;

        public DescriptorAdapter(Activity activity, int resource, List<ElementPath> items) {
            super(activity, resource, items);

            mActivity = activity;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ElementPath elementPath = getItem(position);

            if (convertView == null) {
                convertView = mActivity.getLayoutInflater().inflate(R.layout.layout_info_item_descriptor, parent, false);
            }

            // Tag
            convertView.setTag(elementPath);

            // Name
            TextView nameTextView = (TextView) convertView.findViewById(R.id.nameTextView);
            nameTextView.setText(elementPath.isShowingName ? elementPath.name : elementPath.uuid);

            // Value
            TextView valueTextView = (TextView) convertView.findViewById(R.id.valueTextView);
            byte[] value = mValuesMap.get(elementPath.getKey());
            String valueString = getValueFormatted(value, elementPath.dataFormat);
            valueTextView.setText(valueString);
            valueTextView.setVisibility(valueString == null ? View.GONE : View.VISIBLE);

            // Update button
            ImageButton updateButton = (ImageButton) convertView.findViewById(R.id.updateButton);
            updateButton.setTag(elementPath);

            return convertView;
        }
    }
    //endregion

    //region Utils
    private String getValueFormatted(byte[] value, int dataFormat) {

        String valueString = null;
        if (value != null) {
            if (dataFormat == 0) {
                valueString = new String(value);
            } else {
                String hexString = BleUtils.bytesToHex(value);
                String[] hexGroups = splitStringEvery(hexString, 2);
                valueString = TextUtils.join("-", hexGroups);
            }
        }

        return valueString;
    }

    private static String[] splitStringEvery(String s, int interval) {         // based on: http://stackoverflow.com/questions/12295711/split-a-string-at-every-nth-position
        int arrayLength = (int) Math.ceil(((s.length() / (double) interval)));
        String[] result = new String[arrayLength];

        int j = 0;
        int lastIndex = result.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            result[i] = s.substring(j, j + interval);
            j += interval;
        }
        if (lastIndex >= 0) {
            result[lastIndex] = s.substring(j);
        }

        return result;
    }
    //endregion

    //region Actions
    public void onClickCharacteristic(View view) {
        ElementPath elementPath = (ElementPath) view.getTag();

        if (elementPath != null) {
            // Check if is a characteristic
            if (elementPath.characteristicUUID != null && elementPath.descriptorUUID == null) {
                BluetoothGattService service = mBleManager.getGattService(elementPath.serviceUUID, elementPath.serviceInstance);

                if (mBleManager.isCharacteristicReadable(service, elementPath.characteristicUUID)) {
                    Log.d(TAG, "Read char");
                    mBleManager.readCharacteristic(service, elementPath.characteristicUUID);
                }
            }
        }
    }

    public void onClickNotifyCharacteristic(View view) {
        ElementPath elementPath = (ElementPath) view.getTag();

        if (elementPath != null) {
            // Check if is a characteristic
            if (elementPath.characteristicUUID != null && elementPath.descriptorUUID == null) {
                BluetoothGattService service = mBleManager.getGattService(elementPath.serviceUUID, elementPath.serviceInstance);
                if (mBleManager.isCharacteristicNotifiable(service, elementPath.characteristicUUID)) {
                    Log.d(TAG, "Notify char");
                    ImageButton imageButton = (ImageButton) view;
                    final boolean selected = !imageButton.isSelected();
                    imageButton.setSelected(selected);
                    mBleManager.enableService(service, elementPath.characteristicUUID, selected);

                    // Button color effect when pressed
                    imageButton.setImageResource(selected ? R.drawable.ic_sync_white_24dp : R.drawable.ic_sync_black_24dp);
                }
            }
        }
    }

    public void onClickDescriptor(View view) {
        ElementPath elementPath = (ElementPath) view.getTag();

        if (elementPath != null) {
            // Check if is a descriptor
            if (elementPath.characteristicUUID != null && elementPath.descriptorUUID != null) {
                BluetoothGattService service = mBleManager.getGattService(elementPath.serviceUUID, elementPath.serviceInstance);
                Log.d(TAG, "Read desc");
                mBleManager.readDescriptor(service, elementPath.characteristicUUID, elementPath.descriptorUUID);
            }
        }
    }

    public void onClickDataFormat(View view) {
        ElementPath elementPath = (ElementPath) view.getTag();

        if (elementPath != null) {
            Log.d(TAG, "Toggle data format");
            elementPath.dataFormat = (elementPath.dataFormat + 1) % kDataFormatCount;

            mInfoListAdapter.notifyDataSetChanged();
        }
    }

    // endregion

    // region DataFragment
    public static class DataFragment extends Fragment {
        private List<ElementPath> mServicesList;
        private Map<String, List<ElementPath>> mCharacteristicsMap;
        private Map<String, List<ElementPath>> mDescriptorsMap;
        private Map<String, byte[]> mValuesMap;

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

            mServicesList = new ArrayList<>();
            mCharacteristicsMap = new LinkedHashMap<>();
            mDescriptorsMap = new LinkedHashMap<>();
            mValuesMap = new LinkedHashMap<>();
        } else {
            // Restore status
            mServicesList = mRetainedDataFragment.mServicesList;
            mCharacteristicsMap = mRetainedDataFragment.mCharacteristicsMap;
            mDescriptorsMap = mRetainedDataFragment.mDescriptorsMap;
            mValuesMap = mRetainedDataFragment.mValuesMap;
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mServicesList = mServicesList;
        mRetainedDataFragment.mCharacteristicsMap = mCharacteristicsMap;
        mRetainedDataFragment.mDescriptorsMap = mDescriptorsMap;
        mRetainedDataFragment.mValuesMap = mValuesMap;
    }
    // endregion
}