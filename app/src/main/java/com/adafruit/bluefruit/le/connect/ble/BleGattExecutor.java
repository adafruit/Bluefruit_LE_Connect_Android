// Original source code: https://github.com/StevenRudenko/BleSensorTag. MIT License (Steven Rudenko)


package com.adafruit.bluefruit.le.connect.ble;


import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import java.util.LinkedList;
import java.util.UUID;

// Encapsulate a list of actions to execute. Actions should be queued and executed sequentially to avoid problems
public class BleGattExecutor extends BluetoothGattCallback {
    // Log
    private final static String TAG = BleGattExecutor.class.getSimpleName();

    // Constants
    private static String CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    public interface ServiceAction {
        public static final ServiceAction NULL = new ServiceAction() {
            @Override
            public boolean execute(BluetoothGatt bluetoothGatt) {
                // it is null action. do nothing.
                return true;
            }
        };

        /**
         * Executes action.
         *
         * @param bluetoothGatt
         * @return true - if action was executed instantly. false if action is waiting for feedback.
         */
        public boolean execute(BluetoothGatt bluetoothGatt);
    }

    private final LinkedList<BleGattExecutor.ServiceAction> mQueue = new LinkedList<ServiceAction>();        // list of actions to execute
    private volatile ServiceAction mCurrentAction;

    protected void read(BluetoothGattService gattService, String characteristicUUID, String descriptorUUID) {
        ServiceAction action = serviceReadAction(gattService, characteristicUUID, descriptorUUID);
        mQueue.add(action);
    }

    private BleGattExecutor.ServiceAction serviceReadAction(final BluetoothGattService gattService, final String characteristicUuidString, final String descriptorUuidString) {
        return new BleGattExecutor.ServiceAction() {
            @Override
            public boolean execute(BluetoothGatt bluetoothGatt) {
                final UUID characteristicUuid = UUID.fromString(characteristicUuidString);
                final BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(characteristicUuid);
                if (characteristic != null) {
                    if (descriptorUuidString == null) {
                        // Read Characteristic
                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                            bluetoothGatt.readCharacteristic(characteristic);
                            return false;
                        } else {
                            Log.w(TAG, "read: characteristic not readable: " + characteristicUuidString);
                            return true;
                        }
                    } else {
                        // Read Descriptor
                        final UUID descriptorUuid = UUID.fromString(descriptorUuidString);
                        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUuid);
                        if (descriptor != null) {
                            bluetoothGatt.readDescriptor(descriptor);
                            return false;
                        } else {
                            Log.w(TAG, "read: descriptor not found: " + descriptorUuidString);
                            return true;
                        }
                    }
                } else {
                    Log.w(TAG, "read: characteristic not found: " + characteristicUuidString);
                    return true;
                }
            }
        };
    }

    protected void enable(BluetoothGattService gattService, String characteristicUUID, boolean enable) {
        ServiceAction action = serviceNotifyAction(gattService, characteristicUUID, enable);
        mQueue.add(action);
    }

    private BleGattExecutor.ServiceAction serviceNotifyAction(final BluetoothGattService gattService, final String characteristicUuidString, final boolean enable) {
        return new BleGattExecutor.ServiceAction() {
            @Override
            public boolean execute(BluetoothGatt bluetoothGatt) {
                if (characteristicUuidString != null) {
                    final UUID characteristicUuid = UUID.fromString(characteristicUuidString);
                    final BluetoothGattCharacteristic dataCharacteristic = gattService.getCharacteristic(characteristicUuid);

                    if (dataCharacteristic == null) {
                        Log.w(TAG, "Characteristic with UUID " + characteristicUuidString + "not found");
                        return true;
                    }

                    final UUID clientCharacteristicConfiguration = UUID.fromString(CHARACTERISTIC_CONFIG);
                    final BluetoothGattDescriptor config = dataCharacteristic.getDescriptor(clientCharacteristicConfiguration);
                    if (config == null)
                        return true;

                    // enable/disable locally
                    bluetoothGatt.setCharacteristicNotification(dataCharacteristic, enable);
                    // enable/disable remotely
                    config.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(config);

                    return false;
                } else {
                    Log.w(TAG, "Characteristic with UUID " + characteristicUuidString + "not found");
                    return true;
                }
            }
        };
    }


    protected void write(BluetoothGattService gattService, String uuid, byte[] value) {
        ServiceAction action = serviceWriteAction(gattService, uuid, value);
        mQueue.add(action);
    }


    private BleGattExecutor.ServiceAction serviceWriteAction(final BluetoothGattService gattService, final String uuid, final byte[] value) {
        return new BleGattExecutor.ServiceAction() {
            @Override
            public boolean execute(BluetoothGatt bluetoothGatt) {
                final UUID characteristicUuid = UUID.fromString(uuid);
                final BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(characteristicUuid);
                if (characteristic != null) {
                    characteristic.setValue(value);
                    bluetoothGatt.writeCharacteristic(characteristic);
                    return false;
                } else {
                    Log.w(TAG, "write: characteristic not found: " + uuid);
                    return true;
                }
            }
        };
    }

    protected void execute(BluetoothGatt gatt) {
        if (mCurrentAction == null) {
            while (!mQueue.isEmpty()) {
                final BleGattExecutor.ServiceAction action = mQueue.pop();
                mCurrentAction = action;
                if (!action.execute(gatt))
                    break;
                mCurrentAction = null;
            }
        }
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorRead(gatt, descriptor, status);

        mCurrentAction = null;
        execute(gatt);
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);

        mCurrentAction = null;
        execute(gatt);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);

        mCurrentAction = null;
        execute(gatt);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            mQueue.clear();
            mCurrentAction = null;
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);

        mCurrentAction = null;
        execute(gatt);
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
    }



    // Helper function to create a Gatt Executor with a custom listener
    protected static BleGattExecutor createExecutor(final BleExecutorListener listener) {
        return new BleGattExecutor() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                listener.onConnectionStateChange(gatt, status, newState);
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                listener.onServicesDiscovered(gatt, status);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                listener.onCharacteristicRead(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                listener.onCharacteristicChanged(gatt, characteristic);
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
                listener.onDescriptorRead(gatt, descriptor, status);
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
                listener.onReadRemoteRssi(gatt, rssi, status);
            }

        };
    }

    public static interface BleExecutorListener {

        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState);

        public void onServicesDiscovered(BluetoothGatt gatt, int status);

        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status);

        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);

        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status);

        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status);

    }
}
