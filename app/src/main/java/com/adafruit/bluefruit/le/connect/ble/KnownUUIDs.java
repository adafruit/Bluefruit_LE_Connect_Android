package com.adafruit.bluefruit.le.connect.ble;


import com.adafruit.bluefruit.le.connect.app.UartInterfaceActivity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class KnownUUIDs extends StandardUUIDs {

    // Service UUIDs
    private static final Map<String, String> sServiceUUIDs;
    static {
        Map<String, String> aMap = new HashMap<>();

        aMap.put("0000febb-0000-1000-8000-00805f9b34fb".toUpperCase(), "Adafruit Unified Sensor");
        aMap.put(UartInterfaceActivity.UUID_SERVICE.toUpperCase(), "Nordic UART");
        aMap.put("00001530-1212-efde-1523-785feabcd123".toUpperCase(), "Nordic Device Firmware Update Service");

        sServiceUUIDs = Collections.unmodifiableMap(aMap);
    }

    // Characteristic UUIDs
    private static final Map<String, String> sCharacteristicUUIDs;
    static {
        Map<String, String> aMap = new HashMap<>();

        // Unified
        aMap.put("B71E0102-7E57-4AFE-EB1E-5CA1AB1E1DEA".toUpperCase(), "Static Sensor Info");
        aMap.put("B71E0103-7E57-4AFE-EB1E-5CA1AB1E1DEA".toUpperCase(), "Dynamic Sensor Info");
        aMap.put("b71e0104-7e57-4afe-eb1e-5ca1ab1e1dea".toUpperCase(), "Sensor Data");
        aMap.put("00002a24-0000-1000-8000-00805f9b34fb".toUpperCase(), "Model Number");
        aMap.put("00001530-1212-efde-1523-785feabcd123".toUpperCase(), "Nordic Device Firmware Update Service");

        // DFU
        aMap.put("00001532-1212-efde-1523-785feabcd123".toUpperCase(), "DFU Packet");
        aMap.put("00001531-1212-efde-1523-785feabcd123".toUpperCase(), "DFU Control Point");
        aMap.put("00001534-1212-efde-1523-785feabcd123".toUpperCase(), "DFU Version");

        // Uart
        aMap.put(UartInterfaceActivity.UUID_RX.toUpperCase(), "RX Buffer");
        aMap.put(UartInterfaceActivity.UUID_TX.toUpperCase(), "TX Buffer");
        aMap.put(UartInterfaceActivity.UUID_DFU.toUpperCase(), "DFU Service");

        sCharacteristicUUIDs = Collections.unmodifiableMap(aMap);
    }

    // Descriptors UUIDs
    private static final Map<String, String> sDescriptorUUIDs;
    static {
        Map<String, String> aMap = new HashMap<>();

        sDescriptorUUIDs = Collections.unmodifiableMap(aMap);
    }


    // Public Getters
    public static String getServiceName(String uuid) {
        String result;

        uuid = uuid.toUpperCase();  // To avoid problems with lowercase/uppercase
        result = sServiceUUIDs.get(uuid);
        if (result == null) {
            result = StandardUUIDs.getServiceName(uuid);
        }

        return result;
    }

    public static String getCharacteristicName(String uuid) {
        String result;

        uuid = uuid.toUpperCase();  // To avoid problems with lowercase/uppercase
        result = sCharacteristicUUIDs.get(uuid);
        if (result == null) {
            result = StandardUUIDs.getCharacteristicName(uuid);
        }

        return result;
    }

    public static String getDescriptorName(String uuid) {
        String result;

        uuid = uuid.toUpperCase();  // To avoid problems with lowercase/uppercase
        result = sDescriptorUUIDs.get(uuid);
        if (result == null) {
            result = StandardUUIDs.getDescriptorName(uuid);
        }

        return result;
    }
}
