package com.adafruit.bluefruit.le.connect.ble;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class KnownUUIDs extends StandardUUIDs {

    // Service UUIDs
    private static final Map<String, String> sServiceUUIDs;
    static {
        Map<String, String> aMap = new HashMap<String, String>();
/*
        aMap.put(UnifiedService.UUID_SERVICE.toUpperCase(), "Adafruit Unified Sensor");
        aMap.put(TiHumidityService.UUID_SERVICE.toUpperCase(), "TI Humidity");
        aMap.put(TiPressureService.UUID_SERVICE.toUpperCase(), "TI Pressure");
        aMap.put(TiTemperatureService.UUID_SERVICE.toUpperCase(), "TI Temperature");
        aMap.put(UartService.UUID_SERVICE.toUpperCase(), "Nordic UART");
        */
        aMap.put("00001530-1212-efde-1523-785feabcd123".toUpperCase(), "Nordic Device Firmware Update Service");

        sServiceUUIDs = Collections.unmodifiableMap(aMap);
    }

    // Characteristic UUIDs
    private static final Map<String, String> sCharacteristicUUIDs;
    static {
        Map<String, String> aMap = new HashMap<String, String>();
/*
        // Unified
        aMap.put(UnifiedService.UUID_STATICSENSORINFO.toUpperCase(), "Static Sensor Info");
        aMap.put(UnifiedService.UUID_DYNAMICSENSORINFO.toUpperCase(), "Dynamic Sensor Info");
        aMap.put(UnifiedService.UUID_SENSORDATA.toUpperCase(), "Sensor Data");
        aMap.put(UnifiedService.UUID_MODELNUMBER.toUpperCase(), "Model Number");

        // Uart
        aMap.put(UartService.UUID_RX.toUpperCase(), "RX Buffer");
        aMap.put(UartService.UUID_TX.toUpperCase(), "TX Buffer");
*/
        sCharacteristicUUIDs = Collections.unmodifiableMap(aMap);
    }

    // Descriptors UUIDs
    private static final Map<String, String> sDescriptorUUIDs;
    static {
        Map<String, String> aMap = new HashMap<String, String>();

        sDescriptorUUIDs = Collections.unmodifiableMap(aMap);
    }


    // Public Getters
    public static String getServiceName(String uuid) {
        String result = null;

        uuid = uuid.toUpperCase();  // To avoid problems with lowercase/uppercase
        result = sServiceUUIDs.get(uuid);
        if (result == null) {
            result = StandardUUIDs.getServiceName(uuid);
        }

        return result;
    }

    public static String getCharacteristicName(String uuid) {
        String result = null;

        uuid = uuid.toUpperCase();  // To avoid problems with lowercase/uppercase
        result = sCharacteristicUUIDs.get(uuid);
        if (result == null) {
            result = StandardUUIDs.getCharacteristicName(uuid);
        }

        return result;
    }

    public static String getDescriptorName(String uuid) {
        String result = null;

        uuid = uuid.toUpperCase();  // To avoid problems with lowercase/uppercase
        result = sDescriptorUUIDs.get(uuid);
        if (result == null) {
            result = StandardUUIDs.getDescriptorName(uuid);
        }

        return result;
    }
}
