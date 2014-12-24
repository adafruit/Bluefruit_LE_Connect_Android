package com.adafruit.bluefruit.le.connect.ble;


import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

public interface BleServiceListener {

    public void onConnected();
    public void onConnecting();
    public void onDisconnected();
    public void onServicesDiscovered();

    public void onDataAvailable(BluetoothGattCharacteristic characteristic);
    public void onDataAvailable(BluetoothGattDescriptor descriptor);
}
