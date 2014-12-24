package com.adafruit.bluefruit.le.connect.ble;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BleDevicesScanner {
    private static final String TAG = BleDevicesScanner.class.getSimpleName();
    private static final long kScanPeriod = 20 * 1000; // scan period in milliseconds

    // Data
    private final BluetoothAdapter mBluetoothAdapter;
    private volatile boolean mIsScanning = false;
    private Handler mHandler;
    private List<UUID> mServicesToDiscover;
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private final LeScansPoster mLeScansPoster;
    private int mScanSession = 0;

    //
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                    synchronized (mLeScansPoster) {
                        if (mServicesToDiscover == null || !Collections.disjoint(parseUuids(scanRecord), mServicesToDiscover)) {       // only process the devices with uuids in mServicesToDiscover
                            mLeScansPoster.set(device, rssi, scanRecord);
                            mMainThreadHandler.post(mLeScansPoster);
                        }
                    }
                }
            };

    public BleDevicesScanner(BluetoothAdapter adapter, UUID[] servicesToDiscover, BluetoothAdapter.LeScanCallback callback) {
        mBluetoothAdapter = adapter;
        mServicesToDiscover = servicesToDiscover == null ? null : Arrays.asList(servicesToDiscover);
        mLeScansPoster = new LeScansPoster(callback);

        mHandler = new Handler();
    }

    public void start() {
        if (kScanPeriod > 0) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mIsScanning) {
                        Log.d(TAG, "Scan timer expired. Restart scan");
                        stop();
                        mScanSession++;
                        start();
                    }
                }
            }, kScanPeriod);
        }

        mIsScanning = true;
        Log.d(TAG, "start scanning");
        mBluetoothAdapter.startLeScan(mLeScanCallback);

    }

    public void stop() {
        if (mIsScanning) {
            mHandler.removeCallbacksAndMessages(null);      // cancel pending calls to stop
            mIsScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            Log.d(TAG, "stop scanning");
        }
    }

    public boolean isScanning() {
        return mIsScanning;
    }

    private static class LeScansPoster implements Runnable {
        private final BluetoothAdapter.LeScanCallback leScanCallback;

        private BluetoothDevice device;
        private int rssi;
        private byte[] scanRecord;

        private LeScansPoster(BluetoothAdapter.LeScanCallback leScanCallback) {
            this.leScanCallback = leScanCallback;
        }

        public void set(BluetoothDevice device, int rssi, byte[] scanRecord) {
            this.device = device;
            this.rssi = rssi;
            this.scanRecord = scanRecord;
        }

        @Override
        public void run() {
            leScanCallback.onLeScan(device, rssi, scanRecord);
        }
    }

    // Filtering by custom UUID is broken in Android 4.3 and 4.4, see:
    //   http://stackoverflow.com/questions/18019161/startlescan-with-128-bit-uuids-doesnt-work-on-native-android-ble-implementation?noredirect=1#comment27879874_18019161
    // This is a workaround function from the SO thread to manually parse advertisement data.
    private List<UUID> parseUuids(byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();

        ByteBuffer buffer = ByteBuffer.wrap(advertisedData).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() > 2) {
            byte length = buffer.get();
            if (length == 0) break;

            byte type = buffer.get();
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (length >= 2) {
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", buffer.getShort())));
                        length -= 2;
                    }
                    break;

                case 0x06: // Partial list of 128-bit UUIDs
                case 0x07: // Complete list of 128-bit UUIDs
                    while (length >= 16) {
                        long lsb = buffer.getLong();
                        long msb = buffer.getLong();
                        uuids.add(new UUID(msb, lsb));
                        length -= 16;
                    }
                    break;

                default:
                    buffer.position(buffer.position() + length - 1);
                    break;
            }
        }

        return uuids;
    }
}