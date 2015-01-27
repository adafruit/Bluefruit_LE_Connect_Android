package com.adafruit.bluefruit.le.connect.app.update;

import android.app.Activity;
import android.app.NotificationManager;
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.BuildConfig;
import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ui.ProgressDialogFragment;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import no.nordicsemi.android.error.GattError;


public class FirmwareUpdater implements DownloadTask.DownloadTaskListener, BleManager.BleManagerListener {
    // Config
//    public static final String kDefaultUpdateServerUrl = "https://raw.githubusercontent.com/adafruit/Adafruit_BluefruitLE_Firmware/master/latest.txt";
    public static final String kDefaultUpdateServerUrl = "https://raw.githubusercontent.com/adafruit/Adafruit_BluefruitLE_Firmware/master/releases.xml";

    private static final String kManufacturer = "Adafruit Industries";

    // Constants
    private final static String TAG = FirmwareUpdater.class.getSimpleName();
    private static final String kNordicDeviceFirmwareUpdateService = "00001530-1212-EFDE-1523-785FEABCD123";
    private static final String kDeviceInformationService = "0000180A-0000-1000-8000-00805F9B34FB";
    private static final String kModelNumberCharacteristic = "00002A24-0000-1000-8000-00805F9B34FB";
    private static final String kManufacturerNameCharacteristic = "00002A29-0000-1000-8000-00805F9B34FB";
    private static final String kFirmwareRevisionCharacteristic = "00002A26-0000-1000-8000-00805F9B34FB";

    private static final int kDownloadOperation_VersionsDatabase = 0;
    private static final int kDownloadOperation_Software = 1;

    // Data
    private Context mContext;
    private DownloadTask mDownloadTask;
    private DeviceInfoData mDeviceInfoData;
    private FirmwareUpdaterListener mListener;
    private ProgressDialogFragment mProgressDialog;
    private PowerManager.WakeLock mWakeLock;
    private Activity mParentActivity;

    public static interface FirmwareUpdaterListener {
        void onFirmwareUpdatesChecked(boolean isUpdateAvailable, ReleaseInfo latestRelease, DeviceInfoData deviceInfoData, Map<String, List<ReleaseInfo>> allReleases);

        void onFirmwareUpdateCancelled();

        void onFirmwareUpdateCompleted();

        void onFirmwareUpdateFailed(boolean isDownloadError);
    }

    public class DeviceInfoData {
        public String manufacturer;
        public String firmwareRevision;
        public String modelNumber;
    }

    // region Initialization
    public FirmwareUpdater(Context context, FirmwareUpdaterListener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
        mDeviceInfoData = new DeviceInfoData();
    }

    private void registerAsDfuListener(boolean register) {
        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(mContext);
        if (register) {
            broadcastManager.registerReceiver(mDfuUpdateReceiver, makeDfuUpdateIntentFilter());
        } else {
            broadcastManager.unregisterReceiver(mDfuUpdateReceiver);
        }
    }
    // endregion

    public void refreshSoftwareUpdatesDatabase() {
        // Cancel previous downloads
        if (mDownloadTask != null) {
            mDownloadTask.cancel(true);
        }

        if (isNetworkAvailable()) {
            // Get server url from preferences
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
            String updateServer = sharedPreferences.getString("pref_updateserver", FirmwareUpdater.kDefaultUpdateServerUrl);
            Log.d(TAG, "Get latest software version data from: " + updateServer);

            // Download from server (result will be received on DownloadTaskListener)
            mDownloadTask = new DownloadTask(mContext, this, kDownloadOperation_VersionsDatabase);
            mDownloadTask.execute(updateServer);
        } else {
            Log.d(TAG, "Can't update latest software info from server. Connection not available");
        }
    }

    public boolean hasCurrentConnectedDeviceDFUService() {
        BleManager bleManager = BleManager.getInstance(mContext);
        return bleManager.getGattService(kNordicDeviceFirmwareUpdateService) != null;
    }

    public boolean checkFirmwareUpdatesForTheCurrentConnectedDevice() {
        // This function returns asynchronously using the onFirmwareUpdatesChecked callback.
        // Warning: this function may change the BleManager listener. So restore the listener on your onFirmwareUpdatesChecked callback

        // Only makes sense to check for a newer version if the user can download it (even if we could check the version number because is stored locally)
        if (isNetworkAvailable()) {

            // Check if the device is an adafruit updateable device
            if (mListener == null) Log.w(TAG, "Trying to verify software version without a listener!!");

            boolean hasDFUService = hasCurrentConnectedDeviceDFUService();
            if (hasDFUService) {
                BleManager bleManager = BleManager.getInstance(mContext);
                BluetoothGattService deviceInformationService = bleManager.getGattService(kDeviceInformationService);
                boolean hasDISService = deviceInformationService != null;
                if (hasDISService) {
                    mDeviceInfoData.manufacturer = null;
                    mDeviceInfoData.modelNumber = null;
                    mDeviceInfoData.firmwareRevision = null;
                    bleManager.setBleListener(this);

                    bleManager.readCharacteristic(deviceInformationService, kManufacturerNameCharacteristic);
                    bleManager.readCharacteristic(deviceInformationService, kModelNumberCharacteristic);
                    bleManager.readCharacteristic(deviceInformationService, kFirmwareRevisionCharacteristic);

                    // Data will be received asynchronously (onDataAvailable)
                    return true;        // returns true that means that the process is still working
                } else {
                    Log.d(TAG, "Updates unavailable: No DIS service found");
                }

            }

        } else {
            Log.d(TAG, "No update available. Internet connection not detected");
        }

        mListener.onFirmwareUpdatesChecked(false, null, null, null);

        return false;       // Returns false, meaning the checking has finished
    }


    public void ignoreVersion(String version) {
        // Remembers that the user doesn't want to be notified about the this version anymore
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor sharedPreferencesEdit = sharedPreferences.edit();
        sharedPreferencesEdit.putString("updatemanager_ignoredVersion", version);
        sharedPreferencesEdit.apply();
    }


    public void changedParentActivity(Activity activity) {
        if (mParentActivity != null) {      // only save the activity if we are using it
            mParentActivity = activity;
        }
    }


    public void downloadAndInstallFirmware(Activity activity, String uri) {
        ReleaseInfo release = new ReleaseInfo();
        release.hexFileUrl = uri;
        downloadAndInstallFirmware(activity, release);
    }

    public void downloadAndInstallFirmware(Activity activity, ReleaseInfo release) {
        if (mDownloadTask != null) {
            mDownloadTask.cancel(true);
        }

        if (isNetworkAvailable()) {
            mParentActivity = activity;

            mProgressDialog = new ProgressDialogFragment.Builder()
                    .setMessage(mContext.getString(R.string.softwareupdate_downloading)).setCancelableOnTouchOutside(false).build();
            mProgressDialog.show(activity.getFragmentManager(), "progress_download");
            activity.getFragmentManager().executePendingTransactions();

            mProgressDialog.setIndeterminate(activity.getFragmentManager(), true);
            mProgressDialog.setCancelable(true);
            mProgressDialog.setOnCancelListener(activity.getFragmentManager(), new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    mDownloadTask.cancel(true);
                    cleanInstallationAttempt(false);
                    mListener.onFirmwareUpdateCancelled();
                }
            });

            Log.d(TAG, "Downloading " + release.hexFileUrl);
            mDownloadTask = new DownloadTask(mContext, this, kDownloadOperation_Software);
            mDownloadTask.execute(release.hexFileUrl);
        } else {
            Log.w(TAG, "Cant install latest version. Internet connection not found");
            Toast.makeText(mContext, mContext.getString(R.string.softwareupdate_connectionnotavailable), Toast.LENGTH_LONG).show();
        }
    }


    public void installFirmware(Activity activity, String localPath, String fileStreamUri) {          // Set one of the parameters: either localPath if the file is in the local filesystem or fileStreamUri if the has to be downloaded
        if (localPath == null && fileStreamUri == null) {
            Log.w(TAG, "installFirmware with null parameters");
            return;
        }

        mParentActivity = activity;

        mProgressDialog = new ProgressDialogFragment.Builder()
                .setMessage(mContext.getString(R.string.softwareupdate_downloading)).setCancelableOnTouchOutside(false).build();
        mProgressDialog.show(mParentActivity.getFragmentManager(), "progress_update");
        mParentActivity.getFragmentManager().executePendingTransactions();

        mProgressDialog.setIndeterminate(mParentActivity.getFragmentManager(), true);
        mProgressDialog.setMessage(mParentActivity.getFragmentManager(), mContext.getString(R.string.softwareupdate_startupdate));
        mProgressDialog.setCancelable(true);

        mProgressDialog.setOnCancelListener(mParentActivity.getFragmentManager(), new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                // Abort dfu library process
                final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(mContext);
                final Intent pauseAction = new Intent(DfuService.BROADCAST_ACTION);
                pauseAction.putExtra(DfuService.EXTRA_ACTION, DfuService.ACTION_ABORT);
                manager.sendBroadcast(pauseAction);

                // Cancel dialog
                //cleanInstallationAttempt(false);      // cleaning is performed by the dfu-library listener
                mListener.onFirmwareUpdateCancelled();
            }
        });


        final int fileType = DfuService.TYPE_APPLICATION;
        Uri uriPath = null;
        if (fileStreamUri != null) {
            uriPath = Uri.parse(fileStreamUri);
        }
        Log.d(TAG, "update from: " + (localPath!=null?"path "+localPath:"uri "+uriPath.toString()));

        // take CPU lock to prevent CPU from going off if the user  presses the power button during download
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        mWakeLock.acquire();

        // Register as dfu listener
        registerAsDfuListener(true);


        // Start dfu update service
        BluetoothDevice device = BleManager.getInstance(mContext).getConnectedDevice();     // current connected device
        final Intent service = new Intent(mContext, DfuService.class);

        service.putExtra(DfuService.EXTRA_DEVICE_ADDRESS, device.getAddress());
        service.putExtra(DfuService.EXTRA_DEVICE_NAME, device.getName());
        service.putExtra(DfuService.EXTRA_FILE_MIME_TYPE, fileType == DfuService.TYPE_AUTO ? DfuService.MIME_TYPE_ZIP : DfuService.MIME_TYPE_OCTET_STREAM);
        service.putExtra(DfuService.EXTRA_FILE_TYPE, fileType);
        service.putExtra(DfuService.EXTRA_FILE_PATH, localPath);
        service.putExtra(DfuService.EXTRA_FILE_URI, uriPath);
        ComponentName serviceName = mContext.startService(service);
        Log.d(TAG, "Service started: " + serviceName);
        if (serviceName == null) {
            Log.e(TAG, "Error starting DFU service " + service.getPackage() + ":" + service.getAction());
            cleanInstallationAttempt(false);
            mListener.onFirmwareUpdateFailed(false);
        }
    }

    private void cleanInstallationAttempt(boolean successful) {
        // unregister as dfu listener
        registerAsDfuListener(false);

        // release wakelock
        try {
            mWakeLock.release();
        } catch (Exception e) {
        }

        // dismiss progress dialog
        mProgressDialog.dismiss(mParentActivity.getFragmentManager());
        mProgressDialog = null;

        mParentActivity = null;
    }

    // region DownloadTaskListener
    @Override
    public void onDownloadProgress(int operationId, int progress) {
        if (operationId == kDownloadOperation_Software) {
            Log.d(TAG, "download progress: " + progress + "%%");
            mProgressDialog.setIndeterminate(mParentActivity.getFragmentManager(), false);
            mProgressDialog.setProgress(mParentActivity.getFragmentManager(), progress);
        }
    }

    public void onDownloadCompleted(int operationId, String urlAddress, ByteArrayOutputStream result) {
        mDownloadTask = null;

        if (operationId == kDownloadOperation_VersionsDatabase) {
            onDownloadVersionsDatabaseCompleted(urlAddress, result);
        } else if (operationId == kDownloadOperation_Software) {
            onDownloadSoftwareCompleted(urlAddress, result);
        }
    }

    private void onDownloadVersionsDatabaseCompleted(String urlAddress, ByteArrayOutputStream result) {
        if (result == null) {
            Log.w(TAG, "Error downloading releases.xml");
        } else {

            String contentString = null;
            try {
                contentString = result.toString("UTF-8");
            } catch (UnsupportedEncodingException e) {
            }

            if (contentString != null) {
                // Save in settings
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
                SharedPreferences.Editor sharedPreferencesEdit = sharedPreferences.edit();
                sharedPreferencesEdit.putString("updatemanager_releasesxml", contentString);
                sharedPreferencesEdit.apply();
            } else {
                Log.w(TAG, "Error processing releases.xml");
            }


            /*  Old text file processing
            String contentString = null;
            try {
                contentString = result.toString("UTF-8");
            } catch (UnsupportedEncodingException e) {
            }

            if (contentString != null) {
                String[] lines = contentString.split(System.getProperty("line.separator"));

                String firmwareRevision = null, softwareUrl;
                if (lines.length >= 2) {
                    firmwareRevision = lines[0];
                    softwareUrl = lines[1];

                    if (versionCompare(firmwareRevision, mLatestVersion) > 0) {
                        Log.d(TAG, "Newer version found: " + firmwareRevision + " > " + mLatestVersion);
                        mLatestVersion = firmwareRevision;
                        mLatestVersionUrl = softwareUrl;

                        // Save in settings
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
                        SharedPreferences.Editor sharedPreferencesEdit = sharedPreferences.edit();
                        sharedPreferencesEdit.putString("updatemanager_latestVersion", mLatestVersion);
                        sharedPreferencesEdit.putString("updatemanager_latestVersionUrl", mLatestVersionUrl);
                        sharedPreferencesEdit.apply();
                    }
                }

                Log.d(TAG, "Latest software version: " + firmwareRevision);
            }
            */
        }
    }


    public class ReleaseInfo {
        public String description;
        public String hexFileUrl;
        public String iniFileUrl;
        public String version;
    }


    private Map<String, List<ReleaseInfo>> getReleases() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String releasesXml = sharedPreferences.getString("updatemanager_releasesxml", null);

        return parseReleasesXml(releasesXml);
    }

    private Map<String, List<ReleaseInfo>> parseReleasesXml(String xmlString) {
        Map<String, List<ReleaseInfo>> boardReleases = new LinkedHashMap<>();

        Element blefruitleNode = null;
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(new InputSource(new StringReader(xmlString)));
            blefruitleNode = (Element) document.getElementsByTagName("bluefruitle").item(0);

        } catch (Exception e) {
            Log.w(TAG, "Error reading versions.xml: " + e.getMessage());
        }

        if (blefruitleNode != null) {
            NodeList boardNodes = blefruitleNode.getElementsByTagName("board");

            for (int i = 0; i < boardNodes.getLength(); i++) {
                Node boardNode = boardNodes.item(i);
                if (boardNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element boardElement = (Element) boardNode;
                    String boardName = boardElement.getAttribute("name");
                    //Log.d(TAG, "\tboard: " + boardName);

                    List<ReleaseInfo> boardReleasesInfo = new ArrayList<>();
                    boardReleases.put(boardName, boardReleasesInfo);

                    NodeList firmwareNodes = boardElement.getElementsByTagName("firmwarerelease");
                    for (int j = 0; j < firmwareNodes.getLength(); j++) {
                        Node firmwareNode = firmwareNodes.item(j);
                        if (firmwareNode.getNodeType() == Node.ELEMENT_NODE) {
                            ReleaseInfo releaseInfo = new ReleaseInfo();

                            Element firmwareElement = (Element) firmwareNode;
                            releaseInfo.version = firmwareElement.getAttribute("version");
                            releaseInfo.hexFileUrl = firmwareElement.getAttribute("hexfile");
                            releaseInfo.iniFileUrl = firmwareElement.getAttribute("initfile");
                            releaseInfo.description = boardName;

                            /*
                            Log.d(TAG, "\t\tversion: " + releaseInfo.version);
                            Log.d(TAG, "\t\thexfile: " + releaseInfo.hexFileUrl);
                            Log.d(TAG, "\t\tinitfile: " + releaseInfo.iniFileUrl);
*/
                            boardReleasesInfo.add(releaseInfo);
                        }
                    }
                }
            }
        }

        return boardReleases;
    }

    private void onDownloadSoftwareCompleted(String urlAddress, ByteArrayOutputStream result) {
        mProgressDialog.dismiss(mParentActivity.getFragmentManager());

        if (result == null) {
            Log.w(TAG, "Error downloading software version: " + urlAddress);
            cleanInstallationAttempt(false);
            mListener.onFirmwareUpdateFailed(true);
        } else {
            Log.d(TAG, "Downloaded version: " + urlAddress + " size: " + result.size());

            File file = new File(mContext.getCacheDir(), "update.hex");

            BufferedOutputStream bos;
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
                installFirmware(mParentActivity, file.getAbsolutePath(), null);
            } else {
                cleanInstallationAttempt(false);
                mListener.onFirmwareUpdateFailed(true);
            }

        }
    }

    // endregion


    // region BleManagerListener
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
            if (characteristic.getUuid().toString().equalsIgnoreCase(kFirmwareRevisionCharacteristic)) {
                mDeviceInfoData.firmwareRevision = characteristic.getStringValue(0);
            }

            // Check if we have received all data to check if a software update is needed
            if (mDeviceInfoData.manufacturer != null && mDeviceInfoData.modelNumber != null && mDeviceInfoData.firmwareRevision != null) {
                if (mListener != null) {
                    boolean isFirmwareUpdateAvailable = false;

                    Map<String, List<ReleaseInfo>> allReleases = getReleases();
                    ReleaseInfo latestRelease = null;

                    boolean isManufacturerCorrect = mDeviceInfoData.manufacturer.equalsIgnoreCase(kManufacturer);
                    if (isManufacturerCorrect) {
                        List<ReleaseInfo> modelReleases = allReleases.get(mDeviceInfoData.modelNumber);
                        if (modelReleases != null && modelReleases.size() > 0) {

                            // Get the latest release
                            latestRelease = modelReleases.get(0);

                            // Check if the user chose to ignore this version
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
                            String versionToIgnore = sharedPreferences.getString("updatemanager_ignoredVersion", "");
                            if (versionCompare(latestRelease.version, versionToIgnore) != 0) {

                                boolean isNewerVersion = versionCompare(latestRelease.version, mDeviceInfoData.firmwareRevision) > 0;

                                final boolean showUpdateOnlyForNewerVersions = sharedPreferences.getBoolean("pref_updatesversioncheck", true);

                                isFirmwareUpdateAvailable = (isNewerVersion || !showUpdateOnlyForNewerVersions);

                                if (BuildConfig.DEBUG) {
                                    if (!isNewerVersion) {
                                        Log.d(TAG, "Device has already latest version: " + latestRelease.version);
                                    }
                                }
                            }
                        } else {
                            Log.d(TAG, "No releases found for model: " + mDeviceInfoData.modelNumber);
                        }
                    }

                    mListener.onFirmwareUpdatesChecked(isFirmwareUpdateAvailable, latestRelease, mDeviceInfoData, allReleases);
                }
            }
        }
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }
    // endregion

    // region Utils

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
            try {
                int diff = Integer.valueOf(vals1[i].replaceAll("\\D+", "")).compareTo(Integer.valueOf(vals2[i].replaceAll("\\D+", "")));                  /// .replaceAll("\\D+","") to remove all characteres not numbers
                return Integer.signum(diff);
            } catch (NumberFormatException e) {
                // Not a number: compare strings
                return str1.compareToIgnoreCase(str2);
            }
        }
        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        else {
            return Integer.signum(vals1.length - vals2.length);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    // endregion

    // region DFU library
    private final BroadcastReceiver mDfuUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            // DFU is in progress or an error occurred
            final String action = intent.getAction();
            Log.d(TAG, "Update broadcast action received:" + action);

            if (DfuService.BROADCAST_PROGRESS.equals(action)) {
                final int progress = intent.getIntExtra(DfuService.EXTRA_DATA, 0);
                final int currentPart = intent.getIntExtra(DfuService.EXTRA_PART_CURRENT, 1);
                final int totalParts = intent.getIntExtra(DfuService.EXTRA_PARTS_TOTAL, 1);
                Log.d(TAG, "Update broadcast progress received " + progress + " (" + currentPart + "/" + totalParts + ")");
                updateProgressBar(progress, currentPart, totalParts, false);
            } else if (DfuService.BROADCAST_ERROR.equals(action)) {
                final int error = intent.getIntExtra(DfuService.EXTRA_DATA, 0);
                Log.d(TAG, "Update broadcast error received: " + error);
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
                if (mProgressDialog != null) {
                    mProgressDialog.setIndeterminate(mParentActivity.getFragmentManager(), true);
                    mProgressDialog.setMessage(mParentActivity.getFragmentManager(), mContext.getString(R.string.dfu_status_connecting));
                }
                break;

            case DfuService.PROGRESS_STARTING:
                if (mProgressDialog != null) {
                    mProgressDialog.setIndeterminate(mParentActivity.getFragmentManager(), true);
                    mProgressDialog.setMessage(mParentActivity.getFragmentManager(), mContext.getString(R.string.dfu_status_starting));
                }
                break;

            case DfuService.PROGRESS_ENABLING_DFU_MODE:
                if (mProgressDialog != null) {
                    mProgressDialog.setIndeterminate(mParentActivity.getFragmentManager(), true);
                    mProgressDialog.setMessage(mParentActivity.getFragmentManager(), mContext.getString(R.string.dfu_status_switching_to_dfu));
                }
                break;

            case DfuService.PROGRESS_VALIDATING:
                if (mProgressDialog != null) {
                    mProgressDialog.setIndeterminate(mParentActivity.getFragmentManager(), true);
                    mProgressDialog.setMessage(mParentActivity.getFragmentManager(), mContext.getString(R.string.dfu_status_validating));
                }
                break;

            case DfuService.PROGRESS_DISCONNECTING:
                if (mProgressDialog != null) {
                    mProgressDialog.setIndeterminate(mParentActivity.getFragmentManager(), true);
                    mProgressDialog.setMessage(mParentActivity.getFragmentManager(), mContext.getString(R.string.dfu_status_disconnecting));
                    mProgressDialog.setProgress(mParentActivity.getFragmentManager(), 100);
                }
                break;

            case DfuService.PROGRESS_COMPLETED:
                if (mProgressDialog != null) {
                    mProgressDialog.setMessage(mParentActivity.getFragmentManager(), mContext.getString(R.string.dfu_status_completed));
                }
                // let's wait a bit until we cancel the notification. When canceled immediately it will be recreated by service again.
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        cleanInstallationAttempt(true);
                        mListener.onFirmwareUpdateCompleted();

                        // if this activity is still open and upload process was completed, cancel the notification
                        final NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                        manager.cancel(DfuService.NOTIFICATION_ID);
                    }
                }, 200);
                break;

            case DfuService.PROGRESS_ABORTED:
                if (mProgressDialog != null) {
                    mProgressDialog.setMessage(mParentActivity.getFragmentManager(), mContext.getString(R.string.dfu_status_aborted));
                }
                // let's wait a bit until we cancel the notification. When canceled immediately it will be recreated by service again.
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        cleanInstallationAttempt(false);
                        mListener.onFirmwareUpdateCancelled();

                        // if this activity is still open and upload process was completed, cancel the notification
                        final NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                        manager.cancel(DfuService.NOTIFICATION_ID);
                    }
                }, 200);
                break;

            default:
                if (mProgressDialog != null) {
                    mProgressDialog.setIndeterminate(mParentActivity.getFragmentManager(), false);
                }
                if (error) {
                    Toast.makeText(mContext, "Upload failed: " + GattError.parse(progress) + " (" + (progress & ~(DfuService.ERROR_MASK | DfuService.ERROR_REMOTE_MASK)) + ")", Toast.LENGTH_LONG).show();
                    cleanInstallationAttempt(false);
                    mListener.onFirmwareUpdateFailed(false);
                } else {
                    if (mProgressDialog != null) {
                        mProgressDialog.setProgress(mParentActivity.getFragmentManager(), progress);
                        mProgressDialog.setMessage(mParentActivity.getFragmentManager(), mContext.getString(R.string.dfu_service_progress, progress));
                        if (total > 1)
                            mProgressDialog.setMessage(mParentActivity.getFragmentManager(), mContext.getString(R.string.dfu_status_uploading_part, part, total));
                        else
                            mProgressDialog.setMessage(mParentActivity.getFragmentManager(), mContext.getString(R.string.dfu_status_uploading));
                    }
                }
                break;
        }
    }

    private static IntentFilter makeDfuUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DfuService.BROADCAST_PROGRESS);
        intentFilter.addAction(DfuService.BROADCAST_ERROR);
        intentFilter.addAction(DfuService.BROADCAST_LOG);
        return intentFilter;
    }
    // endregion

}

