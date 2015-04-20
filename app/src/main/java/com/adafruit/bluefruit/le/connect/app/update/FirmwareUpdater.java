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
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.BuildConfig;
import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import no.nordicsemi.android.error.GattError;

/*
    Manages updates for firmware and bootloader
    Note: during updates is important that Activity that starts the update process calls changedParentActivity if is destroyed and recreated (for example on config changes)

 */
public class FirmwareUpdater implements DownloadTask.DownloadTaskListener, BleManager.BleManagerListener {
    // Config
    public static final String kDefaultUpdateServerUrl = "https://raw.githubusercontent.com/adafruit/Adafruit_BluefruitLE_Firmware/master/releases.xml";

    private final static String kPreferences = "FirmwareUpdater_prefs";
    private static final String kManufacturer = "Adafruit Industries";
    private static final String kDefaultBootloaderVersion = "0.0";

    // Constants
    private final static String TAG = FirmwareUpdater.class.getSimpleName();
    private static final String kNordicDeviceFirmwareUpdateService = "00001530-1212-EFDE-1523-785FEABCD123";
    private static final String kDeviceInformationService = "0000180A-0000-1000-8000-00805F9B34FB";
    private static final String kModelNumberCharacteristic = "00002A24-0000-1000-8000-00805F9B34FB";
    private static final String kManufacturerNameCharacteristic = "00002A29-0000-1000-8000-00805F9B34FB";
    private static final String kSoftwareRevisionCharacteristic = "00002A28-0000-1000-8000-00805F9B34FB";
    private static final String kFirmwareRevisionCharacteristic = "00002A26-0000-1000-8000-00805F9B34FB";

  //  private static final String kDfuVersionCharacteristic = "00001534-1212-EFDE-1523-785FEABCD123";

    private static final int kDownloadOperation_VersionsDatabase = 0;
    private static final int kDownloadOperation_Software_Hex = 1;
    private static final int kDownloadOperation_Software_Ini = 2;

    // Data
    private Context mContext;
    private DownloadTask mDownloadTask;
    private DeviceInfoData mDeviceInfoData;
    private FirmwareUpdaterListener mListener;
    private PowerManager.WakeLock mWakeLock;

    //private ProgressDialogFragment mProgressDialog;
    private ProgressFragmentDialog mProgressDialog;
    private Activity mParentActivity;

    public interface FirmwareUpdaterListener {
        void onFirmwareUpdatesChecked(boolean isUpdateAvailable, ReleasesParser.FirmwareInfo latestRelease, DeviceInfoData deviceInfoData, Map<String, ReleasesParser.BoardInfo> allReleases);

        void onUpdateCancelled();

        void onUpdateCompleted();

        void onUpdateFailed(boolean isDownloadError);

        void onUpdateDeviceDisconnected();          // Warning: this can be called from a non-ui thread
    }

    public class DeviceInfoData {
        public String manufacturer;
        public String modelNumber;
        public String firmwareRevision;
        public String softwareRevision;

        public String getBootloaderVersion() {
            String result = kDefaultBootloaderVersion;
            if (firmwareRevision != null) {
                int index = firmwareRevision.indexOf(", ");
                if (index >= 0) {
                    String bootloaderVersion = firmwareRevision.substring(index + 2);
                    result = bootloaderVersion;
                }
            }
            return result;
        }
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
        mDeviceInfoData = new DeviceInfoData();     // Clear device info data
        if (isNetworkAvailable()) {
            // Check if the device is an adafruit updateable device
            if (mListener == null) Log.w(TAG, "Trying to verify software version without a listener!!");

            boolean hasDFUService = hasCurrentConnectedDeviceDFUService();
            if (hasDFUService) {
                BleManager bleManager = BleManager.getInstance(mContext);
                BluetoothGattService deviceInformationService = bleManager.getGattService(kDeviceInformationService);
                boolean hasDISService = deviceInformationService != null;
                if (hasDISService) {
                    bleManager.setBleListener(this);

                    final boolean isDISReadable = bleManager.isCharacteristicReadable(deviceInformationService, kManufacturerNameCharacteristic)
                            && bleManager.isCharacteristicReadable(deviceInformationService, kModelNumberCharacteristic)
                            && bleManager.isCharacteristicReadable(deviceInformationService, kSoftwareRevisionCharacteristic)
                            && bleManager.isCharacteristicReadable(deviceInformationService, kFirmwareRevisionCharacteristic);

                    if (isDISReadable) {

                        bleManager.readCharacteristic(deviceInformationService, kManufacturerNameCharacteristic);
                        bleManager.readCharacteristic(deviceInformationService, kModelNumberCharacteristic);
                        bleManager.readCharacteristic(deviceInformationService, kSoftwareRevisionCharacteristic);
                        bleManager.readCharacteristic(deviceInformationService, kFirmwareRevisionCharacteristic);

                        // Data will be received asynchronously (onDataAvailable)
                        return true;        // returns true that means that the process is still working
                    }
                } else {
                    Log.d(TAG, "Updates: No DIS service found");
                }
            }
        } else {
            Log.d(TAG, "Updates: Internet connection not detected. Skipping version check...");
        }

        mListener.onFirmwareUpdatesChecked(false, null, mDeviceInfoData, null);
        return false;       // Returns false, meaning the checking has finished
    }


    public void ignoreVersion(String version) {
        // Remembers that the user doesn't want to be notified about the this version anymore
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor sharedPreferencesEdit = sharedPreferences.edit();
        sharedPreferencesEdit.putString("pref_ignoredversion", version);
        sharedPreferencesEdit.apply();
    }

    public void changedParentActivity(Activity activity) {      // method to refresh parent activity if a configchange is detected
        if (mParentActivity != null) {      // only save the activity if we were using it
            mParentActivity = activity;
        }
    }

    public void downloadAndInstall(Activity activity, int type, String hexUri, String iniUri) {
        ReleasesParser.BasicVersionInfo release = new ReleasesParser.BasicVersionInfo();
        release.fileType = type;
        release.hexFileUrl = hexUri;
        release.iniFileUrl = iniUri;
        downloadAndInstall(activity, release);
    }

    public void downloadAndInstall(Activity activity, ReleasesParser.BasicVersionInfo originalRelease) {
        // Hack to use only hex files if the detected bootloader version is 0x0000
        String bootloaderVersion = mDeviceInfoData.getBootloaderVersion();
        boolean useHexOnly = bootloaderVersion.equals(kDefaultBootloaderVersion);

        ReleasesParser.BasicVersionInfo release;
        if (useHexOnly) {
            // Copy minimum fields required (and don't use the init file)
            release = new ReleasesParser.BasicVersionInfo();
            release.fileType = originalRelease.fileType;
            release.hexFileUrl = originalRelease.hexFileUrl;
        } else {
            release = originalRelease;
        }

        // Cancel previous download task if still running
        if (mDownloadTask != null) {
            mDownloadTask.cancel(true);
        }

        // Download files
        if (isNetworkAvailable()) {
            mParentActivity = activity;

            mProgressDialog = new ProgressFragmentDialog();
            Bundle arguments = new Bundle();
            arguments.putString("message", mContext.getString(release.fileType == DfuService.TYPE_APPLICATION ? R.string.firmware_downloading : R.string.bootloader_downloading));          // message should be set before oncreate
            mProgressDialog.setArguments(arguments);

            mProgressDialog.show(activity.getFragmentManager(), null);
            activity.getFragmentManager().executePendingTransactions();

            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    mDownloadTask.cancel(true);
                    cleanInstallationAttempt(false);
                    mListener.onUpdateCancelled();
                }
            });

            Log.d(TAG, "Downloading " + release.hexFileUrl);
            mDownloadTask = new DownloadTask(mContext, this, kDownloadOperation_Software_Hex);
            mDownloadTask.setTag(release);
            mDownloadTask.execute(release.hexFileUrl);          // calls onDownloadCompleted when finished
        } else {
            Log.w(TAG, "Can't install latest version. Internet connection not found");
            Toast.makeText(mContext, mContext.getString(R.string.firmware_connectionnotavailable), Toast.LENGTH_LONG).show();
        }
    }


    public void installSoftware(Activity activity, int fileType, String localHexPath, String localIniPath, String hexStreamUri, String iniStreamUri) {          // Set one of the parameters: either localPath if the file is in the local filesystem or fileStreamUri if the has to be downloaded
        if (localHexPath == null && hexStreamUri == null) {
            Log.w(TAG, "Error: trying to installSoftware with null parameters");
            return;
        }

        BluetoothDevice device = BleManager.getInstance(mContext).getConnectedDevice();     // current connected device
        if (device != null) {                                                               // Check that we are still connected to the device
            mParentActivity = activity;

            mProgressDialog = new ProgressFragmentDialog();
            Bundle arguments = new Bundle();
            arguments.putString("message", mContext.getString(R.string.firmware_startupdate));          // message should be set before oncreate
            mProgressDialog.setArguments(arguments);
            mProgressDialog.show(activity.getFragmentManager(), null);
            activity.getFragmentManager().executePendingTransactions();
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    // Abort dfu library process
                    final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(mContext);
                    final Intent pauseAction = new Intent(DfuService.BROADCAST_ACTION);
                    pauseAction.putExtra(DfuService.EXTRA_ACTION, DfuService.ACTION_ABORT);
                    manager.sendBroadcast(pauseAction);

                    // Cancel dialog
                    //cleanInstallationAttempt(false);      // cleaning is performed by the dfu-library listener
                    mListener.onUpdateCancelled();
                }
            });


            Uri hexUriPath = null;
            Uri iniUriPath = null;
            if (hexStreamUri != null) {
                hexUriPath = Uri.parse(hexStreamUri);
                if (iniStreamUri != null) {
                    iniUriPath = Uri.parse(iniStreamUri);
                }
            }

            Log.d(TAG, "update " + (fileType == DfuService.TYPE_APPLICATION ? "firmware" : "bootloader") + " from hex: " + (localHexPath != null ? localHexPath + (localIniPath != null ? " ini:" + localIniPath : "") : hexUriPath.toString() + (iniUriPath != null ? " ini:" + iniUriPath.toString() : "")));

            saveFailedInstallationRecoveryParams(mContext, device.getAddress(), fileType, localHexPath, localIniPath);        // Save info to retry update if something fails

            // take CPU lock to prevent CPU from going off if the user  presses the power button during download
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
            mWakeLock.acquire();

            // Register as dfu listener
            registerAsDfuListener(true);

            // Start dfu update service
            final Intent service = new Intent(mContext, DfuService.class);

            service.putExtra(DfuService.EXTRA_DEVICE_ADDRESS, device.getAddress());
            service.putExtra(DfuService.EXTRA_DEVICE_NAME, device.getName());
            service.putExtra(DfuService.EXTRA_FILE_MIME_TYPE, fileType == DfuService.TYPE_AUTO ? DfuService.MIME_TYPE_ZIP : DfuService.MIME_TYPE_OCTET_STREAM);
            service.putExtra(DfuService.EXTRA_FILE_TYPE, fileType);
            service.putExtra(DfuService.EXTRA_FILE_PATH, localHexPath);
            service.putExtra(DfuService.EXTRA_FILE_URI, hexUriPath);
            service.putExtra(DfuService.EXTRA_RESTORE_BOND, true);          // Always try to restore bond if the device was bonded when the update starts

            if (localIniPath != null) {
                service.putExtra(DfuService.EXTRA_INIT_FILE_PATH, localIniPath);
            }
            if (iniStreamUri != null) {
                service.putExtra(DfuService.EXTRA_INIT_FILE_URI, iniUriPath);
            }

            ComponentName serviceName = mContext.startService(service);
            Log.d(TAG, "Service started: " + serviceName);
            if (serviceName == null) {
                Log.e(TAG, "Error starting DFU service " + service.getPackage() + ":" + service.getAction());
                cleanInstallationAttempt(false);
                clearFailedInstallationRecoveryParams(mContext);
                mListener.onUpdateFailed(false);
            }
        } else {
            Log.d(TAG, "Updates: bluetooth device not ready");
            Toast.makeText(mContext, R.string.firmware_noconnecteddevice, Toast.LENGTH_LONG).show();
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

        if (mProgressDialog != null) {
            try {
                mProgressDialog.dismiss();
            }catch(Exception e) {};
            mProgressDialog = null;
        }

        mParentActivity = null;
    }


    // region Recover Failed updates
    private static final String kFailedDeviceAddressPrefKey = "updatemanager_failedDeviceAddress";
    private static final String kFailedTypePrefKey = "updatemanager_failedType";
    private static final String kFailedHexPrefKey = "updatemanager_failedHex";
    private static final String kFailedIniPrefKey = "updatemanager_failedInit";

    private static void saveFailedInstallationRecoveryParams(Context context, String deviceAddress, int type, String hexFilename, String iniFilename) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor sharedPreferencesEdit = sharedPreferences.edit();
        sharedPreferencesEdit.putString(kFailedDeviceAddressPrefKey, deviceAddress);
        sharedPreferencesEdit.putInt(kFailedTypePrefKey, type);
        sharedPreferencesEdit.putString(kFailedHexPrefKey, hexFilename);
        sharedPreferencesEdit.putString(kFailedIniPrefKey, iniFilename);
        sharedPreferencesEdit.apply();
    }

    public static void clearFailedInstallationRecoveryParams(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor sharedPreferencesEdit = sharedPreferences.edit();
        sharedPreferencesEdit.putString(kFailedDeviceAddressPrefKey, null);
        sharedPreferencesEdit.putInt(kFailedTypePrefKey, -1);
        sharedPreferencesEdit.putString(kFailedHexPrefKey, null);
        sharedPreferencesEdit.putString(kFailedIniPrefKey, null);
        sharedPreferencesEdit.apply();
    }

    public static boolean isFailedInstallationRecoveryAvailable(Context context, String deviceAddress) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String storedDeviceAddress = sharedPreferences.getString(kFailedDeviceAddressPrefKey, null);
        int type = sharedPreferences.getInt(kFailedTypePrefKey, -1);
        String hexFile = sharedPreferences.getString(kFailedHexPrefKey, null);

        return storedDeviceAddress != null && storedDeviceAddress.equalsIgnoreCase(deviceAddress) && type >=0 && hexFile != null;
    }

    public boolean startFailedInstallationRecovery(Activity activity) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String deviceAddress = sharedPreferences.getString(kFailedDeviceAddressPrefKey, null);
        int type = sharedPreferences.getInt(kFailedTypePrefKey, -1);
        String hexFile = sharedPreferences.getString(kFailedHexPrefKey, null);
        String iniFile = sharedPreferences.getString(kFailedIniPrefKey, null);

        if (deviceAddress != null && type >= 0 && hexFile != null) {
            installSoftware(activity, type, hexFile, iniFile, null, null);
            return true;
        } else {
            Log.w(TAG, "Error: no data to startFailedInstallationRecovery");
            return false;
        }
    }
    // endregion

    // region DownloadTaskListener
    @Override
    public void onDownloadProgress(int operationId, int progress) {
        if (operationId == kDownloadOperation_Software_Hex || operationId == kDownloadOperation_Software_Ini) {
            Log.d(TAG, "download (" + operationId + ") progress: " + progress + "%%");
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgress(progress);
        }
    }


    private static final String kDefaultHexFilename = "firmware.hex";
    private static final String kDefaultIniFilename = "firmware.ini";

    public void onDownloadCompleted(int operationId, String urlAddress, ByteArrayOutputStream result) {

        if (operationId == kDownloadOperation_VersionsDatabase) {
            onDownloadVersionsDatabaseCompleted(urlAddress, result);
            mDownloadTask = null;
        } else if (operationId == kDownloadOperation_Software_Hex) {
            clearFailedInstallationRecoveryParams(mContext);
            File file = writeSoftwareDownload(urlAddress, result, kDefaultHexFilename);

            final boolean success = file != null;
            if (success) {

                // Check if we also need to download an ini file, or we are good
                ReleasesParser.BasicVersionInfo release = (ReleasesParser.BasicVersionInfo) mDownloadTask.getTag();
                if (release.iniFileUrl == null || release.iniFileUrl.length() == 0) {
                    // No init file so, go to install firmware
                    installSoftware(mParentActivity, release.fileType, file.getAbsolutePath(), null, null, null);
                    mDownloadTask = null;
                } else {
                    // We have to download the ini file too
                    Log.d(TAG, "Downloading " + release.iniFileUrl);
                    mDownloadTask = new DownloadTask(mContext, this, kDownloadOperation_Software_Ini);
                    mDownloadTask.setTag(release);
                    mDownloadTask.execute(release.iniFileUrl);          // calls onDownloadCompleted when finished
                }
            } else {
                cleanInstallationAttempt(false);
                mListener.onUpdateFailed(true);
                mDownloadTask = null;
            }
        } else if (operationId == kDownloadOperation_Software_Ini) {
            clearFailedInstallationRecoveryParams(mContext);
            File file = writeSoftwareDownload(urlAddress, result, kDefaultIniFilename);
            final boolean success = file != null;
            if (success) {
                // We already had the hex file downloaded, and now we also have the ini file. Let's go
                ReleasesParser.BasicVersionInfo release = (ReleasesParser.BasicVersionInfo) mDownloadTask.getTag();
                String hexLocalFile = new File(mContext.getCacheDir(), kDefaultHexFilename).getAbsolutePath();          // get path from the previously downloaded hex file
                installSoftware(mParentActivity, release.fileType, hexLocalFile, file.getAbsolutePath(), null, null);
                mDownloadTask = null;
            } else {
                cleanInstallationAttempt(false);
                mListener.onUpdateFailed(true);
                mDownloadTask = null;
            }
        }
    }

    private void onDownloadVersionsDatabaseCompleted(String urlAddress, ByteArrayOutputStream result) {
        if (result == null) {
            Log.w(TAG, "Error downloading xml");
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
        }
    }


    private Map<String, ReleasesParser.BoardInfo> getReleases() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String releasesXml = sharedPreferences.getString("updatemanager_releasesxml", null);

        return ReleasesParser.parseReleasesXml(releasesXml);
    }


    private File writeSoftwareDownload(String urlAddress, ByteArrayOutputStream result, String filename) {
        mProgressDialog.dismiss();

        File resultFile = null;
        if (result == null) {
            Log.w(TAG, "Error downloading: " + urlAddress);
            cleanInstallationAttempt(false);
            mListener.onUpdateFailed(true);
        } else {
            Log.d(TAG, "Downloaded version: " + urlAddress + " size: " + result.size());

            File file = new File(mContext.getCacheDir(), filename);

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

            resultFile = success ? file : null;
        }

        return resultFile;
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
        if (mListener != null) {
            mListener.onUpdateDeviceDisconnected();
        }
    }

    @Override
    public void onServicesDiscovered() {

    }


    private String readCharacteristicValueAsString(BluetoothGattCharacteristic characteristic) {
        String string = "";     // Not null
        try {
            string = characteristic.getStringValue(0);
        } catch (Exception e) {
            Log.w(TAG, "readCharacteristicValueAsString" + e);
        }
        return string;
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {

        // Process DIS characteristics
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(kDeviceInformationService)) {
            final String charUuid = characteristic.getUuid().toString();
            if (charUuid.equalsIgnoreCase(kManufacturerNameCharacteristic)) {
                mDeviceInfoData.manufacturer = readCharacteristicValueAsString(characteristic);
                Log.d(TAG, "Updates: received manufacturer:" + mDeviceInfoData.manufacturer);
            } else if (charUuid.equalsIgnoreCase(kModelNumberCharacteristic)) {
                mDeviceInfoData.modelNumber = readCharacteristicValueAsString(characteristic);
                Log.d(TAG, "Updates: received modelNumber:" + mDeviceInfoData.modelNumber);
            } else if (charUuid.equalsIgnoreCase(kSoftwareRevisionCharacteristic)) {
                mDeviceInfoData.softwareRevision = readCharacteristicValueAsString(characteristic);
                Log.d(TAG, "Updates: received softwareRevision:" + mDeviceInfoData.softwareRevision);
            } else if (charUuid.equalsIgnoreCase(kFirmwareRevisionCharacteristic)) {
                mDeviceInfoData.firmwareRevision = readCharacteristicValueAsString(characteristic);
                Log.d(TAG, "Updates: received firmwareRevision:" + mDeviceInfoData.firmwareRevision);
            }
        }

        // Check if we have all data required to check if a software update is needed
        if (mDeviceInfoData.manufacturer != null && mDeviceInfoData.modelNumber != null && mDeviceInfoData.firmwareRevision != null && mDeviceInfoData.softwareRevision != null) {
            if (mListener != null) {
                boolean isFirmwareUpdateAvailable = false;

                Map<String, ReleasesParser.BoardInfo> allReleases = getReleases();
                ReleasesParser.FirmwareInfo latestRelease = null;

                boolean isManufacturerCorrect = mDeviceInfoData.manufacturer.equalsIgnoreCase(kManufacturer);
                if (isManufacturerCorrect) {
                    ReleasesParser.BoardInfo boardInfo = allReleases.get(mDeviceInfoData.modelNumber);
                    if (boardInfo != null) {
                        List<ReleasesParser.FirmwareInfo> modelReleases = boardInfo.firmwareReleases;
                        if (modelReleases != null && modelReleases.size() > 0) {
                            // Get the latest release
                            latestRelease = modelReleases.get(0);

                            // Check if the bootloader is compatible with this version
                            if (mDeviceInfoData.getBootloaderVersion().compareToIgnoreCase(latestRelease.minBootloaderVersion) >= 0) {

                                // Check if the user chose to ignore this version
                                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
                                String versionToIgnore = sharedPreferences.getString("pref_ignoredversion", "");
                                if (ReleasesParser.versionCompare(latestRelease.version, versionToIgnore) != 0) {

                                    final boolean isNewerVersion = ReleasesParser.versionCompare(latestRelease.version, mDeviceInfoData.firmwareRevision) > 0;
                                    final boolean showUpdateOnlyForNewerVersions = sharedPreferences.getBoolean("pref_updatesversioncheck", true);

                                    isFirmwareUpdateAvailable = (isNewerVersion || !showUpdateOnlyForNewerVersions);

                                    if (BuildConfig.DEBUG) {
                                        if (isNewerVersion) {
                                            Log.d(TAG, "Updates: New version found. Ask the user to install: " + latestRelease.version);
                                        } else {
                                            Log.d(TAG, "Updates: Device has already latest version: " + mDeviceInfoData.firmwareRevision);

                                            if (isFirmwareUpdateAvailable) {
                                                Log.d(TAG, "Updates: user asked to show old versions too");
                                            }
                                        }
                                    }
                                } else {
                                    Log.d(TAG, "Updates: User ignored version: " + versionToIgnore + ". Skipping...");
                                }
                            } else {
                                Log.d(TAG, "Updates: Bootloader version " + mDeviceInfoData.getBootloaderVersion() + " below minimum needed: " + latestRelease.minBootloaderVersion);
                            }
                        } else {
                            Log.d(TAG, "Updates: No firmware releases found for model: " + mDeviceInfoData.modelNumber);
                        }
                    } else {
                        Log.d(TAG, "Updates: No releases found for model: " + mDeviceInfoData.modelNumber);
                    }
                } else {
                    Log.d(TAG, "Updates: No updates for unknown manufacturer " + mDeviceInfoData.manufacturer);
                }

                // Send results to listener
                mListener.onFirmwareUpdatesChecked(isFirmwareUpdateAvailable, latestRelease, mDeviceInfoData, allReleases);
            } else {
                Log.d(TAG, "Updates: No listener. Skipping version check...");
            }
        }

    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }
    // endregion

    // region Utils
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
            //Log.d(TAG, "Update broadcast action received:" + action);

            if (DfuService.BROADCAST_PROGRESS.equals(action)) {
                final int progress = intent.getIntExtra(DfuService.EXTRA_DATA, 0);
                final int currentPart = intent.getIntExtra(DfuService.EXTRA_PART_CURRENT, 1);
                final int totalParts = intent.getIntExtra(DfuService.EXTRA_PARTS_TOTAL, 1);
                //Log.d(TAG, "Update broadcast progress received " + progress + " (" + currentPart + "/" + totalParts + ")");
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
                    mProgressDialog.setIndeterminate(true);
                    mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_connecting));
                }
                break;

            case DfuService.PROGRESS_STARTING:
                if (mProgressDialog != null) {
                    mProgressDialog.setIndeterminate(true);
                    mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_starting));
                }
                break;

            case DfuService.PROGRESS_ENABLING_DFU_MODE:
                if (mProgressDialog != null) {
                    mProgressDialog.setIndeterminate(true);
                    mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_switching_to_dfu));
                }
                break;

            case DfuService.PROGRESS_VALIDATING:
                if (mProgressDialog != null) {
                    mProgressDialog.setIndeterminate(true);
                    mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_validating));
                }
                break;

            case DfuService.PROGRESS_DISCONNECTING:
                if (mProgressDialog != null) {
                    mProgressDialog.setIndeterminate(true);
                    mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_disconnecting));
                    mProgressDialog.setProgress(100);
                }
                break;

            case DfuService.PROGRESS_COMPLETED:
                if (mProgressDialog != null) {
                    mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_completed));
                }
                // let's wait a bit until we cancel the notification. When canceled immediately it will be recreated by service again.
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        cleanInstallationAttempt(true);
                        clearFailedInstallationRecoveryParams(mContext);
                        mListener.onUpdateCompleted();

                        // if this activity is still open and upload process was completed, cancel the notification
                        final NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                        manager.cancel(DfuService.NOTIFICATION_ID);
                    }
                }, 200);
                break;

            case DfuService.PROGRESS_ABORTED:
                if (mProgressDialog != null) {
                    mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_aborted));
                }
                // let's wait a bit until we cancel the notification. When canceled immediately it will be recreated by service again.
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        cleanInstallationAttempt(false);
                        clearFailedInstallationRecoveryParams(mContext);
                        mListener.onUpdateCancelled();

                        // if this activity is still open and upload process was completed, cancel the notification
                        final NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                        manager.cancel(DfuService.NOTIFICATION_ID);
                    }
                }, 200);
                break;

            default:
                if (mProgressDialog != null) {
                    mProgressDialog.setIndeterminate(false);
                }
                if (error) {
                    String message = String.format(mContext.getString(R.string.firmware_updatefailed_format), GattError.parse(progress) + " (" + (progress & ~(DfuService.ERROR_MASK | DfuService.ERROR_REMOTE_MASK)) + ")");
                    Log.w(TAG, message);
                    Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
                    cleanInstallationAttempt(false);
                    mListener.onUpdateFailed(false);
                } else {
                    if (mProgressDialog != null) {
                        mProgressDialog.setProgress(progress);
                        mProgressDialog.setMessage(mContext.getString(R.string.dfu_service_progress, progress));
                        if (total > 1)
                            mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_uploading_part, part, total));
                        else
                            mProgressDialog.setMessage(mContext.getString(R.string.dfu_status_uploading));
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

