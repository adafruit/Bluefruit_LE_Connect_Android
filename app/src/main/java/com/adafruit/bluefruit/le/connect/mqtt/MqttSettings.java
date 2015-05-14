package com.adafruit.bluefruit.le.connect.mqtt;

import android.content.Context;
import android.content.SharedPreferences;

public class MqttSettings {
    // Log
    private final static String TAG = MqttSettings.class.getSimpleName();

    // Singleton
    private static MqttSettings mInstance = null;

    // Constants
    public final static int kDefaultServerPort = 1883;
    public final static String kDefaultPublishTopic = "uart_output";
    public final static String kDefaultSubscribeTopic = "uart_input";

    private final static String kPreferences = "MqttSettings_prefs";
    private final static String kPreferences_serveraddress = "serveraddress";
    private final static String kPreferences_serverport = "serverport";
    private final static String kPreferences_publishtopic = "publishtopic";
    private final static String kPreferences_publishqos = "publishqos";
    private final static String kPreferences_publishenabled = "publishenabled";
    private final static String kPreferences_subscribetopic = "subscribetopic";
    private final static String kPreferences_subscribeqos = "subscribeqos";
    private final static String kPreferences_subscribeenabled = "subscribeenabled";
    private final static String kPreferences_connected = "connected";

    // Data
    private Context mContext;

    public static MqttSettings getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MqttSettings(context);
        }
        return mInstance;
    }

    public MqttSettings(Context context) {
        mContext = context.getApplicationContext();
    }

    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
    }

    private SharedPreferences.Editor getSharedPreferencesEditor() {
        return mContext.getSharedPreferences(kPreferences, Context.MODE_PRIVATE).edit();
    }

    public String getServerAddress() {
        return getSharedPreferences().getString(kPreferences_serveraddress, null);
    }

    public void setServerAddress(String address) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putString(kPreferences_serveraddress, address);
        editor.apply();
    }

    public int getServerPort() {
        return getSharedPreferences().getInt(kPreferences_serverport, kDefaultServerPort);
    }

    public void setServerPort(String port) {
        int portInt = kDefaultServerPort;
        try {
            portInt = Integer.parseInt(port);
        } catch (NumberFormatException e) {
        }
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putInt(kPreferences_serverport, portInt);
        editor.apply();
    }

    public boolean isConnected() {
        return getSharedPreferences().getBoolean(kPreferences_connected, false);
    }

    public void setConnectedEnabled(boolean enabled) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putBoolean(kPreferences_connected, enabled);
        editor.apply();
    }

    public boolean isPublishEnabled() {
        return getSharedPreferences().getBoolean(kPreferences_publishenabled, true);
    }

    public void setPublishEnabled(boolean enabled) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putBoolean(kPreferences_publishenabled, enabled);
        editor.apply();
    }

    public boolean isSubscribeEnabled() {
        return getSharedPreferences().getBoolean(kPreferences_subscribeenabled, true);
    }
    public void setSubscribeEnabled(boolean enabled) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putBoolean(kPreferences_subscribeenabled, enabled);
        editor.apply();
    }

    public int getPublishQos() {
        return getSharedPreferences().getInt(kPreferences_publishqos, 0);
    }

    public void setPublishQos(int qos) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putInt(kPreferences_publishqos, qos);
        editor.apply();
    }

    public int getSubscribeQos() {
        return getSharedPreferences().getInt(kPreferences_subscribeqos, 0);
    }

    public void setSubscribeQos(int qos) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putInt(kPreferences_subscribeqos, qos);
        editor.apply();
    }


    public String getPublishTopic() {
        return getSharedPreferences().getString(kPreferences_publishtopic, kDefaultPublishTopic);
    }

    public void setPublishTopic(String topic) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putString(kPreferences_publishtopic, topic);
        editor.apply();
    }

    public String getSubscribeTopic() {
        return getSharedPreferences().getString(kPreferences_subscribetopic, kDefaultSubscribeTopic);
    }

    public void setSubscribeTopic(String topic) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putString(kPreferences_subscribetopic, topic);
        editor.apply();
    }

}
