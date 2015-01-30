package com.adafruit.bluefruit.le.connect.app.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.update.ApplicationFilesFragmentDialog;
import com.adafruit.bluefruit.le.connect.app.update.FirmwareUpdater;

import java.util.List;
import java.util.Map;

public class ConnectedSettingsActivity extends ActionBarActivity implements FirmwareUpdater.FirmwareUpdaterListener, ApplicationFilesFragmentDialog.ApplicationFilesDialogListener {
    // Log
    private final static String TAG = ConnectedSettingsActivity.class.getSimpleName();

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_SelectFile_Hex = 0;
    private static final int kActivityRequestCode_SelectFile_Ini = 1;

    // UI
    private ListView mReleasesListView;
    private ReleasesListAdapter mReleasesListAdapter;

    private View mReleasesDialogView;
    private View mReleasesDialogWaitView;
    private TextView mReleasesDialogTextView;
    private Button mCustomFirmwareButton;

    // Data
    private FirmwareUpdater mFirmwareUpdater;
    private boolean mIsUpdating;
    List<FirmwareUpdater.ReleaseInfo> mReleases;

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
        mReleasesListView = (ListView) findViewById(R.id.releasesListView);
        mReleasesListAdapter = new ReleasesListAdapter(ConnectedSettingsActivity.this, mReleases);      // mReleases is still null here (except if we are restoring the activity because onConfigChanges)
        mReleasesListView.setAdapter(mReleasesListAdapter);

        mReleasesDialogView = findViewById(R.id.releasesDialogView);
        mReleasesDialogWaitView = findViewById(R.id.releasesDialogWaitView);
        mReleasesDialogTextView = (TextView) findViewById(R.id.releasesDialogTextView);
        mCustomFirmwareButton = (Button) findViewById(R.id.customFirmwareButton);

        // Start
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
        if (mReleases != null) {
            showReleasesInfo(false, null, false);
        } else {
            boolean hasDFUService = mFirmwareUpdater.hasCurrentConnectedDeviceDFUService();
            if (hasDFUService) {
                showReleasesInfo(true, getString(R.string.connectedsettings_retrievinginfo), true);
                mFirmwareUpdater.checkFirmwareUpdatesForTheCurrentConnectedDevice();            // continues on onFirmwareUpdatesChecked
            } else {
                showReleasesInfo(true, getString(R.string.connectedsettings_dfunotfound), false);
                mCustomFirmwareButton.setVisibility(View.GONE);
            }
        }
    }

    private void showReleasesInfo(boolean show, String text, boolean showWait) {
        mReleasesDialogView.setVisibility(show ? View.VISIBLE : View.GONE);
        mReleasesDialogTextView.setText(text);
        mReleasesDialogWaitView.setVisibility(showWait ? View.VISIBLE : View.GONE);

        mReleasesListView.setVisibility(!show ? View.VISIBLE : View.GONE);
    }


    // region FirmwareUpdaterListener
    @Override
    public void onFirmwareUpdatesChecked(boolean isUpdateAvailable, FirmwareUpdater.ReleaseInfo latestRelease, FirmwareUpdater.DeviceInfoData deviceInfoData, Map<String, List<FirmwareUpdater.ReleaseInfo>> allReleases) {

        if (allReleases != null) {
            // Get releases for the current board
            mReleases = allReleases.get(deviceInfoData.modelNumber);
            if (mReleases != null && mReleases.size() > 0) {
                mReleasesListAdapter = new ReleasesListAdapter(ConnectedSettingsActivity.this, mReleases);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //  Show releases
                        mReleasesListView.setAdapter(mReleasesListAdapter);
                        showReleasesInfo(false, null, false);
                    }
                });
            } else {
                // Current board was not found, so show all releases found
                final String message = String.format(getString(R.string.connectedsettings_retrievinginfoformat), deviceInfoData.modelNumber);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showReleasesInfo(true, message, false);
                    }
                });
            }
        }
    }

    @Override
    public void onFirmwareUpdateCancelled() {
        mIsUpdating = false;
    }

    @Override
    public void onFirmwareUpdateCompleted() {
        mIsUpdating = false;
        Toast.makeText(this, R.string.scan_softwareupdate_completed, Toast.LENGTH_LONG).show();
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    public void onFirmwareUpdateFailed(boolean isDownloadError) {
        mIsUpdating = false;
        Toast.makeText(this, isDownloadError ? R.string.scan_softwareupdate_downloaderror : R.string.scan_softwareupdate_updateerror, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onFirmwareUpdateDeviceDisconnected() {
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
        private List<FirmwareUpdater.ReleaseInfo> mReleases;

        public ReleasesListAdapter(Context context, List<FirmwareUpdater.ReleaseInfo> releases) {
            mContext = context;
            mReleases = releases;
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

            FirmwareUpdater.ReleaseInfo release = (FirmwareUpdater.ReleaseInfo) getItem(position);
            rowView.setTag(release);
            String versionString = String.format(getString(R.string.connectedsettings_versionformat), release.version);
            titleTextView.setText(versionString);
            subtitleTextView.setText(release.description);

            return rowView;
        }
    }

    public void onClickRelease(View view) {
        final FirmwareUpdater.ReleaseInfo release = (FirmwareUpdater.ReleaseInfo) view.getTag();

        // Ask user if should update
        String message = String.format(getString(R.string.connectedsettings_install_messageformat), release.version);
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
    }

    private void downloadAndInstallRelease(FirmwareUpdater.ReleaseInfo release) {
        mIsUpdating = true;
        mFirmwareUpdater.downloadAndInstallFirmware(this, release);
    }

    public void onClickCustomFirmware(View view) {
        mApplicationFilesDialog = new ApplicationFilesFragmentDialog();
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
        startUpdate(mApplicationFilesDialog.getHexUri(), mApplicationFilesDialog.getIniUri());
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

    private static boolean kNeedsIniFile = true;

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

    private void startUpdate(Uri hexUri, Uri iniUri) {
        if (hexUri != null) {           // hexUri should be defined
            // Start the updates
            mIsUpdating = true;

            if (hexUri.getScheme().equalsIgnoreCase("file") && (iniUri == null || iniUri.getScheme().equalsIgnoreCase("file"))) {       // if is a file in local storage bypass downloader and send the link directly to installer
                final String hexPath = hexUri.getPath();
                final String iniPath = iniUri == null ? null : iniUri.getPath();
                mFirmwareUpdater.installFirmware(this, hexPath, iniPath, null, null);
            } else {
                mFirmwareUpdater.downloadAndInstallFirmware(this, hexUri.toString(), iniUri != null ? iniUri.toString() : null);
            }
        }
    }

    // endregion

    // region DataFragment
    public static class DataFragment extends Fragment {
        private FirmwareUpdater mFirmwareUpdater;
        private boolean mIsUpdating;
        List<FirmwareUpdater.ReleaseInfo> mReleases;
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
            mReleases = mRetainedDataFragment.mReleases;
            mApplicationFilesDialog = mRetainedDataFragment.mApplicationFilesDialog;

            if (mFirmwareUpdater != null) {
                mFirmwareUpdater.changedParentActivity(this);       // set the new activity
            }
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mFirmwareUpdater = mFirmwareUpdater;
        mRetainedDataFragment.mIsUpdating = mIsUpdating;
        mRetainedDataFragment.mReleases = mReleases;
        mRetainedDataFragment.mApplicationFilesDialog = mApplicationFilesDialog;
    }
    // endregion
}

