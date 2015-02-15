package com.adafruit.bluefruit.le.connect.app.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.update.ApplicationFilesFragmentDialog;
import com.adafruit.bluefruit.le.connect.app.update.DfuService;
import com.adafruit.bluefruit.le.connect.app.update.FirmwareUpdater;
import com.adafruit.bluefruit.le.connect.app.update.ReleasesParser;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ui.utils.ExpandableHeightListView;

import java.util.List;
import java.util.Map;

public class ConnectedSettingsActivity extends ActionBarActivity implements FirmwareUpdater.FirmwareUpdaterListener, ApplicationFilesFragmentDialog.ApplicationFilesDialogListener {
    // Log
    private final static String TAG = ConnectedSettingsActivity.class.getSimpleName();

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_SelectFile_Hex = 0;
    private static final int kActivityRequestCode_SelectFile_Ini = 1;

    // UI
    private View mUpdatesWaitView;
    private View mUpdatesWaitIndicatorView;
    private TextView mUpdatesWaitTextView;

    private View mFirmwareReleasesView;
    private Button mCustomFirmwareButton;
    private ExpandableHeightListView mFirmwareReleasesListView;
    private ReleasesListAdapter mFirmwareReleasesListAdapter;

    private View mBootloaderReleasesView;
    private Button mCustomBootloaderButton;
    private ExpandableHeightListView mBootloaderReleasesListView;
    private ReleasesListAdapter mBootloaderReleasesListAdapter;

    // Data
    private FirmwareUpdater mFirmwareUpdater;
    private boolean mIsUpdating;
    private ReleasesParser.BoardInfo mBoardRelease;
    private FirmwareUpdater.DeviceInfoData mDeviceInfoData;

    private ApplicationFilesFragmentDialog mApplicationFilesDialog;


    private DataFragment mRetainedDataFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connected_settings);

        // Set parent for navigation
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        // Restore state
        restoreRetainedDataFragment();

        // UI
        mUpdatesWaitView = findViewById(R.id.updatesWaitView);
        mUpdatesWaitIndicatorView = findViewById(R.id.updatesWaitIndicatorView);
        mUpdatesWaitTextView = (TextView) findViewById(R.id.updatesWaitTextView);

        mFirmwareReleasesView = findViewById(R.id.firmwareReleasesView);
        mCustomFirmwareButton = (Button) findViewById(R.id.customFirmwareButton);
        mFirmwareReleasesListView = (ExpandableHeightListView) findViewById(R.id.firmwareReleasesListView);
        mFirmwareReleasesListView.setExpanded(true);
        mFirmwareReleasesListAdapter = new ReleasesListAdapter(ConnectedSettingsActivity.this, mBoardRelease == null ? null : mBoardRelease.firmwareReleases);      // mBoardRelease is still null here (except if we are restoring the activity because onConfigChanges)
        mFirmwareReleasesListView.setAdapter(mFirmwareReleasesListAdapter);

        mBootloaderReleasesView = findViewById(R.id.bootloaderReleasesView);
        mCustomBootloaderButton = (Button) findViewById(R.id.customBootloaderButton);
        mBootloaderReleasesListView = (ExpandableHeightListView) findViewById(R.id.bootloaderReleasesListView);
        mBootloaderReleasesListView.setExpanded(true);
        mBootloaderReleasesListAdapter = new ReleasesListAdapter(ConnectedSettingsActivity.this, mBoardRelease == null ? null : mBoardRelease.bootloaderReleases);      // mBoardRelease is still null here (except if we are restoring the activity because onConfigChanges)
        mBootloaderReleasesListView.setAdapter(mBootloaderReleasesListAdapter);

        // Start
        if (mDeviceInfoData == null) {      // only do this check the first time
            // Force clear current command queue to prevent a weird state where the executor is waiting for actions that never finish
            BleManager bleManager = BleManager.getInstance(this);
            bleManager.clearExecutor();

            mFirmwareUpdater.checkFirmwareUpdatesForTheCurrentConnectedDevice();            // continues on onFirmwareUpdatesChecked
        }

        updateUI();
    }

    @Override
    public void onResume() {

        super.onResume();


    }

    @Override
    public void onDestroy() {
        // Retain data
        saveRetainedDataFragment();

        super.onDestroy();
    }

    /*
        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            // Inflate the menu; this adds items to the action bar if it is present.
            getMenuInflater().inflate(R.menu.menu_connected_settings, menu);
            return true;
        }
    */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        /*
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        */
        if (id == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateUI() {
        if (mBoardRelease != null) {
            showReleasesInfo(false, null, false);           // Show releases
        } else {
            if (mDeviceInfoData != null) {
                boolean hasDFUService = mFirmwareUpdater.hasCurrentConnectedDeviceDFUService();
                if (hasDFUService) {
                    showReleasesInfo(false, null, false);       // No releases for this device, but show custom buttons
                } else {
                    showReleasesInfo(true, getString(R.string.connectedsettings_dfunotfound), false);
                    mCustomFirmwareButton.setVisibility(View.GONE);
                    mCustomBootloaderButton.setVisibility(View.GONE);
                }
            } else {
                showReleasesInfo(true, getString(R.string.connectedsettings_retrievinginfo), true);
            }
        }
    }

    private void showReleasesInfo(boolean showWaitView, String text, boolean showWaitSpinner) {
        mUpdatesWaitView.setVisibility(showWaitView ? View.VISIBLE : View.GONE);
        mUpdatesWaitTextView.setText(text);
        mUpdatesWaitIndicatorView.setVisibility(showWaitSpinner ? View.VISIBLE : View.GONE);

        mFirmwareReleasesView.setVisibility(!showWaitView ? View.VISIBLE : View.GONE);


        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showBootloader = sharedPreferences.getBoolean("pref_showbootloaders", false);
        mBootloaderReleasesView.setVisibility(!showWaitView && showBootloader ? View.VISIBLE : View.GONE);

        //mFirmwareReleasesListView.setVisibility(!showWaitView ? View.VISIBLE : View.GONE);
    }


    // region FirmwareUpdaterListener
    @Override
    public void onFirmwareUpdatesChecked(boolean isUpdateAvailable, ReleasesParser.FirmwareInfo latestRelease, FirmwareUpdater.DeviceInfoData deviceInfoData, Map<String, ReleasesParser.BoardInfo> allReleases) {

        mDeviceInfoData = deviceInfoData;
        if (allReleases != null) {
            mBoardRelease = allReleases.get(deviceInfoData.modelNumber);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mBoardRelease != null) {
                        mFirmwareReleasesListAdapter = new ReleasesListAdapter(ConnectedSettingsActivity.this, mBoardRelease.firmwareReleases);
                        mFirmwareReleasesListView.setAdapter(mFirmwareReleasesListAdapter);
                        mBootloaderReleasesListAdapter = new ReleasesListAdapter(ConnectedSettingsActivity.this, mBoardRelease.bootloaderReleases);
                        mBootloaderReleasesListView.setAdapter(mBootloaderReleasesListAdapter);
                    }
                    updateUI();
                }
            });
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateUI();
                }
            });
        }
    }

    @Override
    public void onUpdateCancelled() {
        Log.d(TAG, "onUpdateCancelled");
        mIsUpdating = false;
    }

    @Override
    public void onUpdateCompleted() {
        Log.d(TAG, "onUpdateCompleted");

        mIsUpdating = false;
        Toast.makeText(this, R.string.scan_softwareupdate_completed, Toast.LENGTH_LONG).show();
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    public void onUpdateFailed(boolean isDownloadError) {
        mIsUpdating = false;
        Log.w(TAG, "onUpdateFailed");
        Toast.makeText(this, isDownloadError ? R.string.scan_softwareupdate_downloaderror : R.string.scan_softwareupdate_updateerror, Toast.LENGTH_LONG).show();

        if (!isDownloadError) {
            // if is a software update error, the bluetooth state could be corrupted so we force a disconnect
            BleManager.getInstance(this).disconnect();
            setResult(Activity.RESULT_OK);
            finish();
        }
    }

    @Override
    public void onUpdateDeviceDisconnected() {
        Log.w(TAG, "onUpdateDeviceDisconnected");
        if (!mIsUpdating) {         // Is normal no be disconnected during updates, so we don't take those disconnections into account
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ConnectedSettingsActivity.this, R.string.scan_unexpecteddisconnect, Toast.LENGTH_LONG).show();
                    setResult(Activity.RESULT_OK);
                    finish();
                }
            });
        }
    }
    // endregion


    private class ReleasesListAdapter extends BaseAdapter {
        private Context mContext;
        private List<ReleasesParser.BasicVersionInfo> mReleases;

        public ReleasesListAdapter(Context context, List<? extends ReleasesParser.BasicVersionInfo> releases) {
            mContext = context;
            mReleases = (List<ReleasesParser.BasicVersionInfo>) releases;
        }

        @Override
        public int getCount() {
            return mReleases == null ? 0 : mReleases.size();
        }

        @Override
        public Object getItem(int position) {
            return mReleases.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.layout_connected_settings_item_release, parent, false);

            TextView titleTextView = (TextView) rowView.findViewById(R.id.titleTextView);
            TextView subtitleTextView = (TextView) rowView.findViewById(R.id.subtitleTextView);

            ReleasesParser.BasicVersionInfo release = (ReleasesParser.BasicVersionInfo) getItem(position);
            rowView.setTag(release);
            String versionString = String.format(getString(R.string.connectedsettings_versionformat), release.version);
            titleTextView.setText(versionString);
            subtitleTextView.setText(release.description);

            return rowView;
        }
    }

    public void onClickRelease(View view) {
        final ReleasesParser.BasicVersionInfo release = (ReleasesParser.BasicVersionInfo) view.getTag();

        String message = null;
        boolean areRequirementsMet = true;

        // Process firmware release
        if (release.fileType == DfuService.TYPE_APPLICATION) {
            // Check that the minimum bootloader version requirement is met
            ReleasesParser.FirmwareInfo firmwareInfo = (ReleasesParser.FirmwareInfo) release;
            if (mDeviceInfoData.getBootloaderVersion().compareToIgnoreCase(firmwareInfo.minBootloaderVersion) >= 0) {
                message = String.format(getString(R.string.connectedsettings_firmwareinstall_messageformat), release.version);
            } else {
                areRequirementsMet = false;

                // Show 'requirements not met' dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                message = String.format(getString(R.string.firmware_bootloadertooold_format), firmwareInfo.minBootloaderVersion);
                builder.setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .create().show();
            }
        }
        // Process bootloader release
        else if (release.fileType == DfuService.TYPE_BOOTLOADER) {
            message = String.format(getString(R.string.connectedsettings_bootloaderinstall_messageformat), release.version);
        }

        // Continue the process with the selected release
        if (areRequirementsMet) {
            if (message != null) {
                // Ask user if should update
                new AlertDialog.Builder(this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                downloadAndInstallRelease(release);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .setCancelable(true)
                        .show();
            } else {
                Log.d(TAG, "onClickRelease: unknown release type");
            }
        } else {
            Log.d(TAG, "onClickRelease: requirements for update not met");
        }
    }

    private void downloadAndInstallRelease(ReleasesParser.BasicVersionInfo release) {
        mIsUpdating = true;
        mFirmwareUpdater.downloadAndInstall(this, release);
    }

    public void onClickCustomFirmware(View view) {
        mApplicationFilesDialog = new ApplicationFilesFragmentDialog();
        Bundle arguments = new Bundle();
        arguments.putString("message", getString(R.string.firmware_customfile_dialog_title_firmware));          // message should be set before oncreate
        arguments.putInt("fileType", DfuService.TYPE_APPLICATION);
        mApplicationFilesDialog.setArguments(arguments);
        mApplicationFilesDialog.show(getFragmentManager(), null);
    }

    public void onClickCustomBootloader(View view) {
        mApplicationFilesDialog = new ApplicationFilesFragmentDialog();
        Bundle arguments = new Bundle();
        arguments.putString("message", getString(R.string.firmware_customfile_dialog_title_bootloader));          // message should be set before oncreate
        arguments.putInt("fileType", DfuService.TYPE_BOOTLOADER);
        mApplicationFilesDialog.setArguments(arguments);
        mApplicationFilesDialog.show(getFragmentManager(), null);
    }

    public void onApplicationDialogChooseHex(View view) {
        openFileChooser(kActivityRequestCode_SelectFile_Hex);
    }

    public void onApplicationDialogChooseIni(View view) {
        openFileChooser(kActivityRequestCode_SelectFile_Ini);
    }

    @Override
    public void onApplicationFilesDialogDoneClick() {
        startUpdate(mApplicationFilesDialog.getFileType(), mApplicationFilesDialog.getHexUri(), mApplicationFilesDialog.getIniUri());
        mApplicationFilesDialog = null;
    }

    @Override
    public void onApplicationFilesDialogCancelClick() {
        mApplicationFilesDialog = null;
    }

    // region FileExplorer

    private void openFileChooser(int operationId) {

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        /*
        final int fileType = DfuService.TYPE_APPLICATION;
        String types = fileType == DfuService.TYPE_AUTO ? DfuService.MIME_TYPE_ZIP : DfuService.MIME_TYPE_OCTET_STREAM;
        types += ";application/mac-binhex";    // hex is recognized as this mimetype (for dropbox)
        */
        intent.setType("*/*");      // Everything to avoid problems with GoogleDrive

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            // file browser has been found on the device
            startActivityForResult(intent, operationId);
        } else {
            // Alert user that no file browser app has been found on the device
            new AlertDialog.Builder(this)
                    .setTitle(R.string.connectedsettings_noexplorer_title)
                    .setMessage(R.string.connectedsettings_noexplorer_message)
                    .setCancelable(true)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();

            if (requestCode == kActivityRequestCode_SelectFile_Hex) {
                mApplicationFilesDialog.setHexFilename(uri);
                // mApplicationFilesDialog.setPositiveButtonEnabled(uri!=null);
            } else if (requestCode == kActivityRequestCode_SelectFile_Ini) {
                mApplicationFilesDialog.setIniFilename(uri);
            }
        }
    }

    private void startUpdate(int fileType, Uri hexUri, Uri iniUri) {
        if (hexUri != null) {           // hexUri should be defined
            // Start the updates
            mIsUpdating = true;

            if (hexUri.getScheme().equalsIgnoreCase("file") && (iniUri == null || iniUri.getScheme().equalsIgnoreCase("file"))) {       // if is a file in local storage bypass downloader and send the link directly to installer
                final String hexPath = hexUri.getPath();
                final String iniPath = iniUri == null ? null : iniUri.getPath();
                mFirmwareUpdater.installSoftware(this, fileType, hexPath, iniPath, null, null);
            } else {
                mFirmwareUpdater.downloadAndInstall(this, fileType, hexUri.toString(), iniUri != null ? iniUri.toString() : null);
            }
        }
    }

    // endregion

    // region DataFragment
    public static class DataFragment extends Fragment {
        private FirmwareUpdater mFirmwareUpdater;
        private boolean mIsUpdating;
        ReleasesParser.BoardInfo mBoardRelease;
        private FirmwareUpdater.DeviceInfoData mDeviceInfoData;

        private ApplicationFilesFragmentDialog mApplicationFilesDialog;

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
            mIsUpdating = false;
            mFirmwareUpdater = new FirmwareUpdater(this, this);

        } else {
            // Restore status
            mFirmwareUpdater = mRetainedDataFragment.mFirmwareUpdater;
            mIsUpdating = mRetainedDataFragment.mIsUpdating;
            mBoardRelease = mRetainedDataFragment.mBoardRelease;
            mDeviceInfoData = mRetainedDataFragment.mDeviceInfoData;
            mApplicationFilesDialog = mRetainedDataFragment.mApplicationFilesDialog;

            if (mFirmwareUpdater != null) {
                mFirmwareUpdater.changedParentActivity(this);       // set the new activity
            }
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mFirmwareUpdater = mFirmwareUpdater;
        mRetainedDataFragment.mIsUpdating = mIsUpdating;
        mRetainedDataFragment.mBoardRelease = mBoardRelease;
        mRetainedDataFragment.mDeviceInfoData = mDeviceInfoData;
        mRetainedDataFragment.mApplicationFilesDialog = mApplicationFilesDialog;
    }
    // endregion
}

