package com.adafruit.bluefruit.le.connect.app.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
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
import com.adafruit.bluefruit.le.connect.app.update.FirmwareUpdater;
import com.adafruit.bluefruit.le.connect.ble.BleManager;

import java.util.List;
import java.util.Map;

public class ConnectedSettingsActivity extends ActionBarActivity implements FirmwareUpdater.FirmwareUpdaterListener {
    // Log
    private final static String TAG = ConnectedSettingsActivity.class.getSimpleName();

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_SelectFile = 0;

    // UI
    private ListView mReleasesListView;
    private ReleasesListAdapter mReleasesListAdapter;

    private View mReleasesDialogView;
    private View mReleasesDialogWaitView;
    private TextView mReleasesDialogTextView;
    private Button mCustomFirmwareButton;

    // Data
    private FirmwareUpdater mFirmwareUpdater;
    private Map<String, List<FirmwareUpdater.ReleaseInfo>> mAllReleases;
    FirmwareUpdater.DeviceInfoData mDeviceInfoData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connected_settings);

        // Set parent for navigation
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        // UI
        mReleasesListView = (ListView) findViewById(R.id.releasesListView);

        mReleasesDialogView = findViewById(R.id.releasesDialogView);
        mReleasesDialogWaitView = findViewById(R.id.releasesDialogWaitView);
        mReleasesDialogTextView = (TextView) findViewById(R.id.releasesDialogTextView);
        mCustomFirmwareButton = (Button) findViewById(R.id.customFirmwareButton);


        // Start
        mFirmwareUpdater = new FirmwareUpdater(this, this);
        boolean hasDFUService = mFirmwareUpdater.hasCurrentConnectedDeviceDFUService();
        if (hasDFUService) {
            showReleasesDialog(true, getString(R.string.connectedsettings_dfunotfound), true);

            mFirmwareUpdater.checkFirmwareUpdatesForTheCurrentConnectedDevice();
        } else {
            showReleasesDialog(true, getString(R.string.connectedsettings_dfunotfound), false);
            mCustomFirmwareButton.setVisibility(View.GONE);
        }
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

    public void showReleasesDialog(boolean show, String text, boolean showWait) {
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
            List<FirmwareUpdater.ReleaseInfo> releases = allReleases.get(deviceInfoData.modelNumber);
            if (releases != null) {
                mReleasesListAdapter = new ReleasesListAdapter(ConnectedSettingsActivity.this, releases);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //  Show releases
                        mReleasesListView.setAdapter(mReleasesListAdapter);
                        showReleasesDialog(false, null, false);
                    }
                });
            } else {
                // Current board was not found, so show all releases found

            }
        }

    }

    @Override
    public void onFirmwareUpdateCancelled() {

    }

    @Override
    public void onFirmwareUpdateCompleted() {
        Toast.makeText(this, R.string.scan_softwareupdate_completed, Toast.LENGTH_LONG).show();
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    public void onFirmwareUpdateFailed(boolean isDownloadError) {
        Toast.makeText(this, isDownloadError ? R.string.scan_softwareupdate_downloaderror : R.string.scan_softwareupdate_updateerror, Toast.LENGTH_LONG).show();

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
            return mReleases.size();
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
//                .setTitle(R.string.scan_softwareupdate_title)
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
        BleManager bleManager = BleManager.getInstance(this);
        BluetoothDevice device = bleManager.getConnectedDevice();
        mFirmwareUpdater.downloadAndInstallFirmware(this, release);
        //mFirmwareUpdater.installFirmware(this, null, release.hexFileUrl);
    }

    public void onClickCustomFirmware(View view) {
        openFileChooser();
    }

    // region FileExplorer


    private void openFileChooser() {

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
            startActivityForResult(intent, kActivityRequestCode_SelectFile);
        } else {
            // Aert user that no file browers app is available
            new AlertDialog.Builder(this)
                    .setTitle(R.string.connectedsettings_noexplorer_title)
                    .setMessage(R.string.connectedsettings_noexplorer_message)
                    .setCancelable(true)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (resultCode == RESULT_OK && requestCode == kActivityRequestCode_SelectFile) {
            Uri uri = data.getData();
            mFirmwareUpdater.downloadAndInstallFirmware(this, uri.toString());
        }
    }

    // endregion
}

