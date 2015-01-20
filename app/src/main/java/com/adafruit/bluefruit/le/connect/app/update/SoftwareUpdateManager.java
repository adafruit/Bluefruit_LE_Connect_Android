package com.adafruit.bluefruit.le.connect.app.update;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.BuildConfig;
import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleServiceListener;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

import no.nordicsemi.android.error.GattError;


public class SoftwareUpdateManager implements DownloadTaskListener, BleServiceListener {
    // Config
    public static final String kDefaultUpdateServerUrl = "https://raw.githubusercontent.com/adafruit/Adafruit_BluefruitLE_Firmware/master/latest.txt";

    private static final String kManufacturer = "Adafruit Industries";
    private static final String kModelNumber = "BLEFRIEND";

    // Constants
    private final static String TAG = SoftwareUpdateManager.class.getSimpleName();
    private static final String kNordicDeviceFirmwareUpdateService = "00001530-1212-EFDE-1523-785FEABCD123";
    private static final String kDeviceInformationService = "0000180A-0000-1000-8000-00805F9B34FB";
    private static final String kModelNumberCharacteristic = "00002A24-0000-1000-8000-00805F9B34FB";
    private static final String kManufacturerNameCharacteristic = "00002A29-0000-1000-8000-00805F9B34FB";
    private static final String kSoftwareRevisionCharacteristic = "00002A28-0000-1000-8000-00805F9B34FB";

    private static final int kDownloadOperation_Version = 0;
    private static final int kDownloadOperation_Software = 1;

    // Data
    private static SoftwareUpdateManager mInstance = null;

    private Context mContext;
    private String mLatestVersion;
    private String mLatestVersionUrl;
    private DownloadTask mDownloadTask;
    private DeviceInfoData mDeviceInfoData;
    private SoftwareUpdateManagerListener mListener;
    private ProgressDialog mProgressDialog;
    private BluetoothDevice mSelectedDevice;

    private String mLastestChechedDeviceAddress;           // To avoid waiting to check device if we have already checked it this session

    public interface SoftwareUpdateManagerListener {
        void onSoftwareUpdateChecked(boolean isUpdateAvailable, String latestSoftwareVersion);

        void onInstallCancelled();

        void onInstallCompleted();

        void onInstallFailed(boolean isDownloadError);
    }

    private class DeviceInfoData {
        BleServiceListener previousListener;
        String manufacturer;
        String softwareVersion;
        String modelNumber;
    }

    private final BroadcastReceiver mDfuUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            // DFU is in progress or an error occurred
            final String action = intent.getAction();
            Log.d(TAG, "Update broadcast action received:" +action);

            if (DfuService.BROADCAST_PROGRESS.equals(action)) {
                final int progress = intent.getIntExtra(DfuService.EXTRA_DATA, 0);
                final int currentPart = intent.getIntExtra(DfuService.EXTRA_PART_CURRENT, 1);
                final int totalParts = intent.getIntExtra(DfuService.EXTRA_PARTS_TOTAL, 1);
                Log.d(TAG, "Update broadcast progress received "+progress+" ("+currentPart+"/"+totalParts+")");
                updateProgressBar(progress, currentPart, totalParts, false);
            } else if (DfuService.BROADCAST_ERROR.equals(action)) {
                final int error = intent.getIntExtra(DfuService.EXTRA_DATA, 0);
                Log.d(TAG, "Update broadcast error received: "+error);
                updateProgressBar(error, 0, 0, true);

                // We have to wait a bit before canceling notification. This is called before DfuService creates the last notification.
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // if this activity is still open and upload process was completed, cancel the notification
                        final NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                        manager.cancel(DfuService.NOTIFICATION_ID);
                    }
                }, 200);
            }
        }
    };

    private void updateProgressBar(final int progress, final int part, final int total, final boolean error) {
        switch (progress) {
            case DfuService.PROGRESS_CONNECTING:
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_connecting));
                break;

            case DfuService.PROGRESS_STARTING:
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_starting));
                break;

            case DfuService.PROGRESS_VALIDATING:
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_validating));
                break;

            case DfuService.PROGRESS_DISCONNECTING:
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_disconnecting));
                break;

            case DfuService.PROGRESS_COMPLETED:
                mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_completed));
                // let's wait a bit until we cancel the notification. When canceled immediately it will be recreated by service again.
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onInstallCompleted();
                        mProgressDialog.dismiss();
                        mProgressDialog = null;

                        // if this activity is still open and upload process was completed, cancel the notification
                        final NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                        manager.cancel(DfuService.NOTIFICATION_ID);
                    }
                }, 200);
                break;

            case DfuService.PROGRESS_ABORTED:
                mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_aborted));
                // let's wait a bit until we cancel the notification. When canceled immediately it will be recreated by service again.
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onInstallCancelled();
                        mProgressDialog.dismiss();
                        mProgressDialog = null;

                        // if this activity is still open and upload process was completed, cancel the notification
                        final NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                        manager.cancel(DfuService.NOTIFICATION_ID);
                    }
                }, 200);
                break;

            default:
                mProgressDialog.setIndeterminate(false);
                if (error) {
                    Toast.makeText(mContext, "Upload failed: " + GattError.parse(progress) + " (" + (progress & ~(DfuService.ERROR_MASK | DfuService.ERROR_REMOTE_MASK)) + ")", Toast.LENGTH_LONG).show();
                    mListener.onInstallFailed(false);
                    mLastestChechedDeviceAddress = null;
                    mProgressDialog.dismiss();
                    mProgressDialog = null;
                } else {
                    mProgressDialog.setProgress(progress);
                    mProgressDialog.setMessage(mContext.getString(R.string.dfu_service_progress, progress));
                    if (total > 1)
                        mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_uploading_part, part, total));
                    else
                        mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_uploading));
                }
                break;
        }
    }

    public void onResumeListenerActivity() {
        Log.d(TAG, "Register mDfuUpdateReceiver");
        // We are using LocalBroadcastReceiver instead of normal BroadcastReceiver for optimization purposes
        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(mContext);
        broadcastManager.registerReceiver(mDfuUpdateReceiver, makeDfuUpdateIntentFilter());
    }

    public void onPauseListenerActivity() {
        Log.d(TAG, "Unregister mDfuUpdateReceiver");
        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(mContext);
        broadcastManager.unregisterReceiver(mDfuUpdateReceiver);
    }

    private static IntentFilter makeDfuUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DfuService.BROADCAST_PROGRESS);
        intentFilter.addAction(DfuService.BROADCAST_ERROR);
        intentFilter.addAction(DfuService.BROADCAST_LOG);
        return intentFilter;
    }

    public static SoftwareUpdateManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new SoftwareUpdateManager(context);
        }
        return mInstance;
    }

    public SoftwareUpdateManager(Context context) {
        mContext = context.getApplicationContext();
        mDeviceInfoData = new DeviceInfoData();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mLatestVersion = sharedPreferences.getString("updatemanager_latestVersion", "0");
        mLatestVersionUrl = sharedPreferences.getString("updatemanager_latestVersionUrl", "");
    }

    public void updateInfoFromServer() {
        if (mDownloadTask != null) {
            mDownloadTask.cancel(true);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String updateServer = sharedPreferences.getString("pref_updateserver", SoftwareUpdateManager.kDefaultUpdateServerUrl);
        Log.d(TAG, "Get latest software version data from: "+updateServer);

        mDownloadTask = new DownloadTask(mContext, this, kDownloadOperation_Version);
        mDownloadTask.execute(updateServer);
    }

    public void setListener(SoftwareUpdateManagerListener listener) {
        mListener = listener;

        BleManager bleManager = BleManager.getInstance(mContext);
        mDeviceInfoData.previousListener = bleManager.getBleListener();         // Save current listener to restore it when we finish checking information
    }

    public boolean checkIfNewSoftwareVersionIsAvailable() {
        // Check if the user chose to ignore the latest version
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String versionToIgnore = sharedPreferences.getString("updatemanager_ignoredVersion", "0");
        if (versionCompare(mLatestVersion, versionToIgnore) != 0) {

            BleManager bleManager = BleManager.getInstance(mContext);
            String deviceAddress = bleManager.getConnectedDeviceAddress();
            if (!deviceAddress.equals(mLastestChechedDeviceAddress)) {
                mLastestChechedDeviceAddress = deviceAddress;

                // Check if the device is an adafruit updateable device
                if (mListener == null) Log.w(TAG, "Trying to verify software version without listener!!");

                boolean hasDFUService = bleManager.getGattService(kNordicDeviceFirmwareUpdateService) != null;
                if (hasDFUService) {
                    boolean checkBleFriendDevice = sharedPreferences.getBoolean("pref_updatesblefriendcheck", true);
                    if (checkBleFriendDevice) {
                        BluetoothGattService deviceInformationService = bleManager.getGattService(kDeviceInformationService);
                        boolean hasDISService = deviceInformationService != null;
                        if (hasDISService) {
                            mDeviceInfoData.manufacturer = null;
                            mDeviceInfoData.modelNumber = null;
                            mDeviceInfoData.softwareVersion = null;
                            bleManager.setBleListener(this);

                            bleManager.readCharacteristic(deviceInformationService, kManufacturerNameCharacteristic);
                            bleManager.readCharacteristic(deviceInformationService, kModelNumberCharacteristic);
                            bleManager.readCharacteristic(deviceInformationService, kSoftwareRevisionCharacteristic);

                            // Data will be received asynchronously (onDataAvailable)
                            return true;
                        }
                        else {
                            Log.d(TAG, "Updates unavailable: No DIS service found");                        }

                    } else {
                        // Update anything with a DFU service
                        mListener.onSoftwareUpdateChecked(true, mLatestVersion);
                        return true;
                    }
                }
            }
            else {
                Log.d(TAG, "Version for connected device was already checked this session. Skipping check");
            }
        } else {
            Log.d(TAG, "User asked to ignore version: " + versionToIgnore);
        }

        return false;
    }

    public void ignoreCurrentVersion() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor sharedPreferencesEdit = sharedPreferences.edit();
        sharedPreferencesEdit.putString("updatemanager_ignoredVersion", mLatestVersion);
        sharedPreferencesEdit.apply();
    }

    public void installLatestVersion(Activity activity, BluetoothDevice selectedDevice) {
        mSelectedDevice = selectedDevice;

        if (mDownloadTask != null) {
            mDownloadTask.cancel(true);
        }

        mProgressDialog = new ProgressDialog(activity);
        mProgressDialog.setMessage(mContext.getString(R.string.softwareupdate_downloading));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);
        mProgressDialog.show();

        Log.d(TAG, "Downloading " + mLatestVersionUrl);
        mDownloadTask = new DownloadTask(mContext, this, kDownloadOperation_Software);
        mDownloadTask.execute(mLatestVersionUrl);

        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mDownloadTask.cancel(true);
                mListener.onInstallCancelled();
            }
        });
    }

    private void updateDeviceFirmware(String path) {
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setMessage(mContext.getString(R.string.softwareupdate_startupdate));
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);
        mProgressDialog.show();

        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mListener.onInstallCancelled();
            }
        });

        final int fileType = DfuService.TYPE_APPLICATION;
        String fileStreamUri = null;

        Log.d(TAG, "update from: " + path);

        final Intent service = new Intent(mContext, DfuService.class);

        service.putExtra(DfuService.EXTRA_DEVICE_ADDRESS, mSelectedDevice.getAddress());
        service.putExtra(DfuService.EXTRA_DEVICE_NAME, mSelectedDevice.getName());
        service.putExtra(DfuService.EXTRA_FILE_MIME_TYPE, fileType == DfuService.TYPE_AUTO ? DfuService.MIME_TYPE_ZIP : DfuService.MIME_TYPE_HEX);
        service.putExtra(DfuService.EXTRA_FILE_TYPE, fileType);
        service.putExtra(DfuService.EXTRA_FILE_PATH, path);
        service.putExtra(DfuService.EXTRA_FILE_URI, fileStreamUri);
        ComponentName serviceName = mContext.startService(service);
        Log.d(TAG, "Service started: " + serviceName);
        if (serviceName == null) {
            Log.e(TAG, "Error starting DFU service " + service.getPackage() + ":" + service.getAction());
            mListener.onInstallFailed(false);
            mLastestChechedDeviceAddress = null;
        }
    }

    @Override
    public void onDownloadProgress(int operationId, int progress) {
        if (operationId == kDownloadOperation_Software) {
            Log.d(TAG, "download progress: " + progress + "%%");
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setProgress(progress);
        }
    }

    public void onDownloadCompleted(int operationId, ByteArrayOutputStream result) {
        mDownloadTask = null;

        if (operationId == kDownloadOperation_Version) {

            if (result == null) {
                Log.w(TAG, "Error downloading latest software version info");
            } else {

                String contentString = null;
                try {
                    contentString = result.toString("UTF-8");
                } catch (UnsupportedEncodingException e) {
                }

                if (contentString != null) {
                    String[] lines = contentString.split(System.getProperty("line.separator"));

                    String softwareVersion = null, softwareUrl;
                    if (lines.length >= 2) {
                        softwareVersion = lines[0];
                        softwareUrl = lines[1];

                        if (versionCompare(softwareVersion, mLatestVersion) > 0) {
                            Log.d(TAG, "Newer version found: " + softwareVersion + " > " + mLatestVersion);
                            mLatestVersion = softwareVersion;
                            mLatestVersionUrl = softwareUrl;

                            // Save in settings
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
                            SharedPreferences.Editor sharedPreferencesEdit = sharedPreferences.edit();
                            sharedPreferencesEdit.putString("updatemanager_latestVersion", mLatestVersion);
                            sharedPreferencesEdit.putString("updatemanager_latestVersionUrl", mLatestVersionUrl);
                            sharedPreferencesEdit.apply();
                        }
                    }

                    Log.d(TAG, "Latest software version: " + softwareVersion);
                }
            }
        } else if (operationId == kDownloadOperation_Software) {
            mProgressDialog.dismiss();
            //mProgressDialog = null;

            if (result == null) {
                Log.w(TAG, "Error downloading software version: " + mLatestVersionUrl);
                mListener.onInstallFailed(true);
                mLastestChechedDeviceAddress = null;
            } else {
                Log.d(TAG, "Downloaded version: " + mLatestVersion + " size: " + result.size());

                File file = new File(mContext.getCacheDir(), "update.hex");

                BufferedOutputStream bos = null;
                boolean success = true;
                try {
                    bos = new BufferedOutputStream(new FileOutputStream(file));
                    bos.write(result.toByteArray());
                    bos.flush();
                    bos.close();
                } catch (IOException e) {
                    success = false;
                }

                if (success) {
                    updateDeviceFirmware(file.getAbsolutePath());
                } else {
                    mListener.onInstallFailed(true);
                    mLastestChechedDeviceAddress = null;
                }

            }
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

    }

    @Override
    public void onServicesDiscovered() {

    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(kDeviceInformationService)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(kManufacturerNameCharacteristic)) {
                mDeviceInfoData.manufacturer = characteristic.getStringValue(0);
            }
            if (characteristic.getUuid().toString().equalsIgnoreCase(kModelNumberCharacteristic)) {
                mDeviceInfoData.modelNumber = characteristic.getStringValue(0);
            }
            if (characteristic.getUuid().toString().equalsIgnoreCase(kSoftwareRevisionCharacteristic)) {
                mDeviceInfoData.softwareVersion = characteristic.getStringValue(0);
            }

            // Check if we have received all data to check if a software update is needed
            if (mDeviceInfoData.manufacturer != null && mDeviceInfoData.modelNumber != null && mDeviceInfoData.softwareVersion != null) {
                BleManager bleManager = BleManager.getInstance(mContext);
                bleManager.setBleListener(mDeviceInfoData.previousListener);

                if (mListener != null) {
                    boolean isManufacturerCorrect = mDeviceInfoData.manufacturer.equalsIgnoreCase(kManufacturer);
                    boolean isModelNumberCorrect = mDeviceInfoData.modelNumber.equalsIgnoreCase(kModelNumber);
                    boolean isNewerVersion = versionCompare(mLatestVersion, mDeviceInfoData.softwareVersion) > 0;

                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
                    boolean showUpdateOnlyForNewerVersions = sharedPreferences.getBoolean("pref_updatesversioncheck", true);

                    boolean isUpdateAvailable = (isManufacturerCorrect && isModelNumberCorrect && (isNewerVersion || !showUpdateOnlyForNewerVersions));
                    mListener.onSoftwareUpdateChecked(isUpdateAvailable, mLatestVersion);

                    if (BuildConfig.DEBUG) {
                        if (isManufacturerCorrect && isModelNumberCorrect && !isNewerVersion) {
                            Log.d(TAG, "Blefriend detected but version is already latest: "+mLatestVersion);
                        }
                    }

                }
            }
        }
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    private class DownloadTask extends AsyncTask<String, Integer, ByteArrayOutputStream> {

        private Context context;
        private PowerManager.WakeLock mWakeLock;
        private DownloadTaskListener listener;
        private int operationId;

        public DownloadTask(Context context, DownloadTaskListener listener, int operationId) {
            this.context = context;
            this.listener = listener;
            this.operationId = operationId;
        }

        @Override
        protected ByteArrayOutputStream doInBackground(String... sUrl) {
            InputStream input = null;
            ByteArrayOutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return null;
                }

                // this will be useful to display download percentage  might be -1: server did not report the length
                int fileLength = connection.getContentLength();
//                Log.d(TAG, "\tFile size: "+fileLength);

                // download the file
                input = connection.getInputStream();
                output = new ByteArrayOutputStream();

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;

                    // publishing the progress....
                    if (fileLength > 0) {// only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    }
                    output.write(data, 0, count);
                }

            } catch (Exception e) {
                Log.w(TAG, "Error downloading latest version info: " + e);
                return null;
            } finally {
                try {
                    if (output != null) {
                        output.close();
                    }
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException ignored) {
                }

                if (connection != null) {
                    connection.disconnect();
                }
            }
            return output;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // take CPU lock to prevent CPU from going off if the user  presses the power button during download
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    getClass().getName());
            mWakeLock.acquire();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);

            listener.onDownloadProgress(operationId, progress[0]);
        }

        @Override
        protected void onPostExecute(ByteArrayOutputStream result) {
            mWakeLock.release();

            listener.onDownloadCompleted(operationId, result);
        }
    }


    /**
     * Compares two version strings.
     * Based on http://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
     * <p/>
     * Use this instead of String.compareTo() for a non-lexicographical
     * comparison that works for version strings. e.g. "1.10".compareTo("1.6").
     *
     * @param str1 a string of ordinal numbers separated by decimal points.
     * @param str2 a string of ordinal numbers separated by decimal points.
     * @return The result is a negative integer if str1 is _numerically_ less than str2.
     * The result is a positive integer if str1 is _numerically_ greater than str2.
     * The result is zero if the strings are _numerically_ equal.
     * @note It does not work if "1.10" is supposed to be equal to "1.10.0".
     */
    private Integer versionCompare(String str1, String str2) {

        // Remove chars after spaces
        int spaceIndex1 = str1.indexOf(" ");
        if (spaceIndex1 >= 0) str1 = str1.substring(0, spaceIndex1);
        int spaceIndex2 = str2.indexOf(" ");
        if (spaceIndex2 >= 0) str2 = str2.substring(0, spaceIndex2);

        // Check version
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i].replaceAll("\\D+","")).compareTo(Integer.valueOf(vals2[i].replaceAll("\\D+","")));                  /// .replaceAll("\\D+","") to remove all characteres not numbers
            return Integer.signum(diff);
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        else {
            return Integer.signum(vals1.length - vals2.length);
        }
    }

}

interface DownloadTaskListener {
    void onDownloadProgress(int operationId, int progress);

    void onDownloadCompleted(int operationId, ByteArrayOutputStream result);
}
