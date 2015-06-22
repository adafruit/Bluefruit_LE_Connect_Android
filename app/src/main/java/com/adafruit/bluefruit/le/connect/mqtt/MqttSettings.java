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
    public final static String kDefaultServerAddress = "io.adafruit.com";
    public final static String kDefaultPublishTopicTx = null;//"uart_tx";
    public final static String kDefaultPublishTopicRx = null;//"uart_rx";
    public final static String kDefaultSubscribeTopic = null;//"uart_input";

    private final static String kPreferences = "MqttSettings_prefs";
    private final static String kPreferences_serveraddress = "serveraddress";
    private final static String kPreferences_serverport = "serverport";
    private final static String kPreferences_publishtopic = "publishtopic";
    private final static String kPreferences_publishqos = "publishqos";
    private final static String kPreferences_publishenabled = "publishenabled";
    private final static String kPreferences_subscribetopic = "subscribetopic";
    private final static String kPreferences_subscribeqos = "subscribeqos";
    private final static String kPreferences_subscribebehaviour = "subscribebehaviour";
    private final static String kPreferences_subscribeenabled = "subscribeenabled";
    private final static String kPreferences_connected = "connected";
    private final static String kPreferences_username = "username";
    private final static String kPreferences_password = "password";
    private final static String kPreferences_cleansession = "cleansession";


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
        return getPrefsString(kPreferences_serveraddress, kDefaultServerAddress);
    }

    public void setServerAddress(String address) {
        setPrefsString(kPreferences_serveraddress, address);
    }

    public int getServerPort() {
        return getPrefsInt(kPreferences_serverport, kDefaultServerPort);
    }

    public void setServerPort(String port) {
        int portInt = kDefaultServerPort;
        try {
            portInt = Integer.parseInt(port);
        } catch (NumberFormatException e) {
        }
        setPrefsInt(kPreferences_serverport, portInt);
    }

    public boolean isConnected() {
        return getPrefsBoolean(kPreferences_connected, false);
    }

    public void setConnectedEnabled(boolean enabled) {
        setPrefsBoolean(kPreferences_connected, enabled);
    }

    public boolean isPublishEnabled() {
        return getPrefsBoolean(kPreferences_publishenabled, true);
    }

    public void setPublishEnabled(boolean enabled) {
        setPrefsBoolean(kPreferences_publishenabled, enabled);
    }

    public boolean isSubscribeEnabled() {
        return getPrefsBoolean(kPreferences_subscribeenabled, true);
    }
    public void setSubscribeEnabled(boolean enabled) {
        setPrefsBoolean(kPreferences_subscribeenabled, enabled);
    }

    public int getPublishQos(int index) {
        return getPrefsInt(kPreferences_publishqos + index, 0);
    }

    public void setPublishQos(int index, int qos) {
        setPrefsInt(kPreferences_publishqos + index, qos);
    }

    public int getSubscribeQos() {
        return getPrefsInt(kPreferences_subscribeqos, 0);
    }

    public void setSubscribeQos(int qos) {
        setPrefsInt(kPreferences_subscribeqos, qos);
    }

    public int getSubscribeBehaviour() {
        return getPrefsInt(kPreferences_subscribebehaviour, 0);
    }

    public void setSubscribeBehaviour(int behaviour) {
        setPrefsInt(kPreferences_subscribebehaviour, behaviour);
    }

    public String getPublishTopic(int index) {
        return getPrefsString(kPreferences_publishtopic+index, index==0?kDefaultPublishTopicTx:kDefaultPublishTopicRx);
    }

    public void setPublishTopic(int index, String topic) {
        setPrefsString(kPreferences_publishtopic+index, topic);
    }

    public String getSubscribeTopic() {
        return getPrefsString(kPreferences_subscribetopic, kDefaultSubscribeTopic);
    }

    public void setSubscribeTopic(String topic) {
        setPrefsString(kPreferences_subscribetopic, topic);
    }


    public String getUsername() {
        return getPrefsString(kPreferences_username, null);
    }

    public void setUsername(String username) {
        setPrefsString(kPreferences_username, username);
    }

    public String getPassword() {
        return getPrefsString(kPreferences_password, null);
    }

    public void setPassword(String password) {
        setPrefsString(kPreferences_password, password);
    }

    public boolean isCleanSession() {
        return getPrefsBoolean(kPreferences_cleansession, true);
    }

    public void setCleanSession(boolean enabled) {
        setPrefsBoolean(kPreferences_cleansession, enabled);
    }


    // region Utils
    private String getPrefsString(String key, String defaultValue) {
        return getSharedPreferences().getString(key, defaultValue);
    }

    private int getPrefsInt(String key, int defaultValue) {
        return getSharedPreferences().getInt(key, defaultValue);
    }

    private boolean getPrefsBoolean(String key, boolean defaultValue) {
        return getSharedPreferences().getBoolean(key, defaultValue);
    }

    public void setPrefsString(String key, String value) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putString(key, value);
        editor.apply();
    }

    public void setPrefsInt(String key, int value) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putInt(key, value);
        editor.apply();
    }

    public void setPrefsBoolean(String key, boolean value) {
        SharedPreferences.Editor editor = getSharedPreferencesEditor();
        editor.putBoolean(key, value);
        editor.apply();
    }
    // endregion

}
