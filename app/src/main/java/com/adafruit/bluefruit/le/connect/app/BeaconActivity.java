package com.adafruit.bluefruit.le.connect.app;

import android.app.AlertDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.settings.ConnectedSettingsActivity;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ui.tabs.SlidingTabLayout;

import java.nio.charset.Charset;

public class BeaconActivity extends UartInterfaceActivity implements BleManager.BleManagerListener, IBeaconFragment.OnFragmentInteractionListener, URIBeaconFragment.OnFragmentInteractionListener {
    // Log
    private final static String TAG = BeaconActivity.class.getSimpleName();

    // Constants
    private final static int kOperation_BeaconNoOperation = -1;
    private final static int kOperation_BeaconDisable = 0;
    private final static int kOperation_iBeaconEnable = 1;
    private final static int kOperation_UriBeaconEnable = 2;

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_ConnectedSettingsActivity = 0;

    // Data
    BeaconPagerAdapter mAdapterViewPager;
    private int mCurrentTab;
    private int mCurrentOperation = kOperation_BeaconNoOperation;
    private AlertDialog mDialog;

    private DataFragment mRetainedDataFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beacon);

        // Ble
        mBleManager = BleManager.getInstance(this);
        restoreRetainedDataFragment();

        // Setup when activity is created for the first time
//        if (savedInstanceState == null) {
        // Get params
        Intent intent = getIntent();
        int rssi = intent.getIntExtra("rssi", 0);
        //      }

        // UI
        ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
        mAdapterViewPager = new BeaconPagerAdapter(getSupportFragmentManager(), getApplicationContext(), rssi);
        viewPager.setAdapter(mAdapterViewPager);

        SlidingTabLayout slidingTabLayout = (SlidingTabLayout) findViewById(R.id.sliding_tabs);
        slidingTabLayout.setDistributeEvenly(true);
        slidingTabLayout.setViewPager(viewPager);


        // Attach the page change listener inside the activity
        slidingTabLayout.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageSelected(int position) {
                mCurrentTab = position;
                dismissKeyboard();
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

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
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }

        // Retain data
        saveRetainedDataFragment();

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        boolean result = false;

        if (mCurrentTab == 0) {
            // if pressed back check if we need to dimiss custom keyboard used on iBeacon Activity
            Fragment currentFragment = mAdapterViewPager.getCurrentFragment();
            result = ((IBeaconFragment) currentFragment).onBackPressed();
        }

        if (result == false) {
            super.onBackPressed();
        }
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        dismissKeyboard();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_beacon, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_connected_settings) {
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

    private void testATParser() {
        String uartCommand = "AT\\r\\n";
        Log.d(TAG, "send command: " + uartCommand);
        sendData(uartCommand);

    }

    private void dismissKeyboard() {

        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

            if (imm != null && imm.isAcceptingText()) { // verify if the soft keyboard is open
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception e) {
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
        Log.d(TAG, "Disconnected. Back to previous activity");
        setResult(-1);      // Unexpected Disconnect
        finish();
    }

    @Override
    public void onServicesDiscovered() {
        mUartService = mBleManager.getGattService(UUID_SERVICE);
        mBleManager.enableService(mUartService, UUID_RX, true);

        // Test AT parser
        testATParser();
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        // UART RX
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                final String data = new String(characteristic.getValue(), Charset.forName("UTF-8")).trim();
                Log.d(TAG, "received: " + data);

                String message = null;
                if (data.equalsIgnoreCase("OK")) {      // All good!

                    switch(mCurrentOperation) {
                        case kOperation_BeaconDisable: break;//message = getString(R.string.beacon_beacon_disabled); break;
                        case kOperation_iBeaconEnable: onBeaconEnabled(); break;//message = getString(R.string.beacon_beacon_enabled); break;
                        case kOperation_UriBeaconEnable: onBeaconEnabled(); break;//message = getString(R.string.beacon_beacon_enabled); break;
                        default:
                            break;
                    }
                } else  // Error received
                {
                    mCurrentOperation = kOperation_BeaconNoOperation;
                    message = data;
                }

                if (message != null) {
                    final String finalMessage = message;
                    // Update UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mDialog != null)
                            {
                                mDialog.dismiss();
                            }

                            // Alert
                            AlertDialog.Builder builder = new AlertDialog.Builder(BeaconActivity.this);
                            builder.setMessage(finalMessage).setPositiveButton(android.R.string.ok, null);
                            mDialog = builder.create();
                            mDialog.show();

                        }
                    });
                }
            }
        }
    }

    private void onBeaconEnabled() {
        finish();
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }
    // endregion


    public static class BeaconPagerAdapter extends FragmentPagerAdapter {
        private static int kNumItems = 2;

        private Context mContext;
        private int mRssi;
        private Fragment mCurrentFragment;

        public BeaconPagerAdapter(FragmentManager fragmentManager, Context context, int rssi) {
            super(fragmentManager);

            mContext = context;
            mRssi = rssi;
        }

        public Fragment getCurrentFragment() {
            return mCurrentFragment;
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            if (getCurrentFragment() != object) {
                mCurrentFragment = ((Fragment) object);
            }
            super.setPrimaryItem(container, position, object);
        }

        // Returns total number of pages
        @Override
        public int getCount() {
            return kNumItems;
        }

        // Returns the fragment to display for that page
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0: // Fragment # 0
                    return IBeaconFragment.newInstance(mRssi);
                case 1: // Fragment # 1
                    return URIBeaconFragment.newInstance();
                default:
                    return null;
            }
        }

        // Returns the page title for the top indicator
        @Override
        public CharSequence getPageTitle(int position) {
            return mContext.getResources().getStringArray(R.array.beacon_page_titles)[position];
        }
    }

    @Override
    public void onEnable(String vendor, String uuid, String major, String minor, String rssi) {
        mCurrentOperation = kOperation_iBeaconEnable;

        // iBeacon Enable
        String uartCommand = String.format("+++\r\nAT+BLEBEACON=%s,%s,%s,%s,%s\r\nATZ\r\n+++\r\n", vendor, uuid, major, minor, rssi);
        Log.d(TAG, "send command: " + uartCommand);
        sendData(uartCommand);

        // Alert
        if (mDialog != null) {
            mDialog.dismiss();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(BeaconActivity.this);
        builder.setMessage(R.string.beacon_beacon_enabling);//.setPositiveButton(android.R.string.ok, null);
        mDialog = builder.create();
        mDialog.show();
    }

    @Override
    public void onEnable(String encodedUri) {
        mCurrentOperation = kOperation_UriBeaconEnable;

        // URIBeacon enable
        String uartCommand = String.format("+++\r\nAT+BLEURIBEACON=%s\r\nATZ\n+++\r\n", encodedUri);
        Log.d(TAG, "send command: " + uartCommand);
        sendData(uartCommand);

        // Alert
        if (mDialog != null) {
            mDialog.dismiss();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(BeaconActivity.this);
        builder.setMessage(R.string.beacon_beacon_enabling);//.setPositiveButton(android.R.string.ok, null);
        mDialog = builder.create();
        mDialog.show();

    }

    @Override
    public void onDisable() {
        mCurrentOperation = kOperation_BeaconDisable;

        // Disable
        String uartCommand = "+++\r\nAT+FACTORYRESET\r\n+++\r\n";
        Log.d(TAG, "send command: " + uartCommand);
        sendData(uartCommand);
    }

    // region DataFragment
    public static class DataFragment extends android.app.Fragment {


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }
    }

    private void restoreRetainedDataFragment() {
        // find the retained fragment
        android.app.FragmentManager fm = getFragmentManager();
        mRetainedDataFragment = (DataFragment) fm.findFragmentByTag(TAG);

        if (mRetainedDataFragment == null) {
            // Create
            mRetainedDataFragment = new DataFragment();
            fm.beginTransaction().add(mRetainedDataFragment, TAG).commit();


        } else {
            // Restore status

        }
    }

    private void saveRetainedDataFragment() {

    }
    // endregion
}
