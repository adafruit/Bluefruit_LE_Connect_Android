package com.adafruit.bluefruit.le.connect.app;

import com.adafruit.bluefruit.le.connect.ble.BleUtils;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class UriBeaconUtils {

    public static String getSchemeFromPrefix(byte schemePrefix)
    {
        switch (schemePrefix)
        {
            case 0: return "http://www.";
            case 1: return "https://www.";
            case 2: return "http://";
            case 3: return "https://";
            case 4: return "urn:uuid:";
            default: return null;
        }
    }

    public static String getUrlEncodingFromByte(byte value)
    {
        switch (value)
        {
            case 0: return ".com/";
            case 1: return ".org/";
            case 2: return ".edu/";
            case 3: return ".net/";
            case 4: return ".info/";
            case 5: return ".biz/";
            case 6: return ".gov/";
            case 7: return ".com/";
            case 8: return ".org/";
            case 9: return ".edu/";
            case 10: return ".net/";
            case 11: return ".info/";
            case 12: return ".biz/";
            case 13: return ".gov/";
            default: return null;
        }
    }

    public static String getUriFromAdvertisingPacket(byte[] scanRecord)
    {
        byte schemeByte = scanRecord[10];
        String scheme = UriBeaconUtils.getSchemeFromPrefix(schemeByte);

        String url = "";
        if(schemeByte == 0x04)        // Special case for urn:uuid
        {
            byte[] urlBytes = Arrays.copyOfRange(scanRecord, 11, 11 + 16);
            url = BleUtils.getUuidStringFromByteArray(urlBytes);
        }
        else {
            final int length = scanRecord[4] - 6;       // 6 fixed fields bytes (uri length  is total lenght-6)
            byte[] urlBytes = Arrays.copyOfRange(scanRecord, 11, 11 + length);

            try {
                url = new String(urlBytes, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            for (int i = urlBytes.length - 1; i >= 0; i--)            // Go backwards because we are replacing single characters with strings that will change the url lenght
            {
                String urlEncoding = getUrlEncodingFromByte(urlBytes[i]);
                if (urlEncoding != null) {
                    url = new StringBuffer(url).insert(i, urlEncoding).toString();
                }
            }
        }

        String text = scheme + url;
        return text;
    }
}
