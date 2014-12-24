// Original source code: https://github.com/StevenRudenko/BleSensorTag. MIT License (Steven Rudenko)

package com.adafruit.bluefruit.le.connect.ble;


import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

public interface BleExecutorListener {

    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState);

    public void onServicesDiscovered(BluetoothGatt gatt, int status);

    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status);

    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);

    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status);

}
