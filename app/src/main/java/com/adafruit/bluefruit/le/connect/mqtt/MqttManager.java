package com.adafruit.bluefruit.le.connect.mqtt;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.R;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.android.service.MqttTraceHandler;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.UUID;

public class MqttManager implements IMqttActionListener, MqttCallback, MqttTraceHandler {
    // Log
    private final static String TAG = MqttManager.class.getSimpleName();

    // Singleton
    private static MqttManager mInstance = null;

    // Types
    public enum MqqtConnectionStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED,
        ERROR,
        NONE
    }

    public static int MqqtQos_AtMostOnce = 0;
    public static int MqqtQos_AtLeastOnce = 1;
    public static int MqqtQos_ExactlyOnce = 2;

    // Data
    private MqttAndroidClient mMqttClient;
    private MqttManagerListener mListener;
    private MqqtConnectionStatus mMqqtClientStatus = MqqtConnectionStatus.NONE;
    private Context mContext;

    public static MqttManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MqttManager(context);
        }
        return mInstance;
    }

    public MqttManager(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public void finalize() throws Throwable {

        try {
            if (mMqttClient != null) {
                mMqttClient.unregisterResources();
            }
        } finally {
            super.finalize();
        }
    }


    public MqqtConnectionStatus getClientStatus() {
        return mMqqtClientStatus;
    }

    public void setListener(MqttManagerListener listener) {
        mListener = listener;
    }

    // region MQTT
    public void subscribe(String topic, int qos) {
        if (mMqttClient != null && mMqqtClientStatus == MqqtConnectionStatus.CONNECTED && topic != null) {
            try {
                Log.d(TAG, "Mqtt: subscribe to " + topic + " qos:" + qos);
                mMqttClient.subscribe(topic, qos);
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x subscribe error: ", e);
            }
        }
    }

    public void unsubscribe(String topic) {
        if (mMqttClient != null && mMqqtClientStatus == MqqtConnectionStatus.CONNECTED && topic != null) {
            try {
                Log.d(TAG, "Mqtt: unsubscribe from " + topic);
                mMqttClient.unsubscribe(topic);
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x unsubscribe error: ", e);
            }
        }

    }


    public void publish(String topic, String payload, int qos) {
        if (mMqttClient != null && mMqqtClientStatus == MqqtConnectionStatus.CONNECTED && topic != null) {
            boolean retained = false;

            try {
                Log.d(TAG, "Mqtt: publish " + payload + " for topic " + topic + " qos:" + qos);
                mMqttClient.publish(topic, payload.getBytes(), qos, retained, null, null);
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x publish error: ", e);
            }
        }
    }

    public void disconnect() {
        if (mMqttClient != null && mMqqtClientStatus == MqqtConnectionStatus.CONNECTED) {
            try {
                Log.d(TAG, "Mqtt: disconnect");
//                mMqqtClientStatus = MqqtConnectionStatus.DISCONNECTING;
                mMqqtClientStatus = MqqtConnectionStatus.DISCONNECTED;      // Note: it seems that the disconnected callback is never invoked. So we fake here that the final state is disconnected
                mMqttClient.disconnect(null, this);

                mMqttClient.unregisterResources();
                mMqttClient = null;
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt:x disconnection error: ", e);
            }
        }

    }

    public void connectFromSavedSettings(Context context) {
        MqttSettings settings = MqttSettings.getInstance(context);
        String host = settings.getServerAddress();
        int port = settings.getServerPort();

        String username = settings.getUsername();
        String password = settings.getPassword();
        boolean cleanSession = settings.isCleanSession();
        boolean sslConnection = settings.isSslConnection();

        connect(context, host, port, username, password, cleanSession, sslConnection);
    }

    public void connect(Context context, String host, int port, String username, String password, boolean cleanSession, boolean sslConnection) {
        String clientId = "Bluefruit_"+ UUID.randomUUID().toString();
        final int timeout = MqttConnectOptions.CONNECTION_TIMEOUT_DEFAULT;
        final int keepalive = MqttConnectOptions.KEEP_ALIVE_INTERVAL_DEFAULT;

        String message = null;
        String topic = null;
        int qos = 0;
        boolean retained = false;

        String uri;
        if (sslConnection) {
            uri = "ssl://" + host + ":" + port;

        } else {
            uri = "tcp://" + host + ":" + port;
        }

        Log.d(TAG, "Mqtt: Create client: "+clientId);
        mMqttClient = new MqttAndroidClient(context, uri, clientId);
        mMqttClient.registerResources(mContext);

        MqttConnectOptions conOpt = new MqttConnectOptions();
        Log.d(TAG, "Mqtt: clean session:" +(cleanSession?"yes":"no"));
        conOpt.setCleanSession(cleanSession);
        conOpt.setConnectionTimeout(timeout);
        conOpt.setKeepAliveInterval(keepalive);
        if (username != null && username.length() > 0) {
            Log.d(TAG, "Mqtt: username: " + username);
            conOpt.setUserName(username);
        }
        if (password != null && password.length() > 0) {
            Log.d(TAG, "Mqtt: password: " + password);
            conOpt.setPassword(password.toCharArray());
        }

        boolean doConnect = true;
        if ((message != null && message.length() > 0) || (topic != null && topic.length() > 0)) {
            // need to make a message since last will is set
            Log.d(TAG, "Mqtt: setwill");
            try {
                conOpt.setWill(topic, message.getBytes(), qos, retained);
            } catch (Exception e) {
                Log.e(TAG, "Mqtt: Can't set will", e);
                doConnect = false;
                //callback.onFailure(null, e);
            }
        }
        mMqttClient.setCallback(this);
        mMqttClient.setTraceCallback(this);

        if (doConnect) {
            MqttSettings.getInstance(mContext).setConnectedEnabled(true);

            try {
                Log.d(TAG, "Mqtt: connect to " + uri);
                mMqqtClientStatus = MqqtConnectionStatus.CONNECTING;
                mMqttClient.connect(conOpt, null, this);
            } catch (MqttException e) {
                Log.e(TAG, "Mqtt: connection error: ", e);
            }
        }
    }

    // endregion

    // region IMqttActionListener
    @Override
    public void onSuccess(IMqttToken iMqttToken) {
        if (mMqqtClientStatus == MqqtConnectionStatus.CONNECTING) {
            Log.d(TAG, "Mqtt connect onSuccess");
            mMqqtClientStatus = MqqtConnectionStatus.CONNECTED;
            if (mListener != null) mListener.onMqttConnected();

            MqttSettings settings = MqttSettings.getInstance(mContext);
            String topic = settings.getSubscribeTopic();
            int topicQos = settings.getSubscribeQos();
            if (settings.isSubscribeEnabled() && topic != null) {
                subscribe(topic, topicQos);
            }
        } else if (mMqqtClientStatus == MqqtConnectionStatus.DISCONNECTING) {
            Log.d(TAG, "Mqtt disconnect onSuccess");
            mMqqtClientStatus = MqqtConnectionStatus.DISCONNECTED;
            if (mListener != null) mListener.onMqttDisconnected();
        } else {
            Log.d(TAG, "Mqtt unknown onSuccess");
        }
    }

    @Override
    public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
        Log.d(TAG, "Mqtt onFailure. " + throwable);

        // Remove the auto-connect till the failure is solved
        if (mMqqtClientStatus == MqqtConnectionStatus.CONNECTING) {
            MqttSettings.getInstance(mContext).setConnectedEnabled(false);
        }

        // Set as an error
        mMqqtClientStatus = MqqtConnectionStatus.ERROR;
        String errorText = mContext.getString(R.string.mqtt_connection_failed)+". "+throwable.getLocalizedMessage();
        Toast.makeText(mContext, errorText, Toast.LENGTH_LONG).show();

        // Call listener
        if (mListener != null) mListener.onMqttDisconnected();
    }
    // endregion

    // region MqttCallback
    @Override
    public void connectionLost(Throwable throwable) {
        Log.d(TAG, "Mqtt connectionLost. " + throwable);

        if (throwable != null) {        // if disconnected because a reason show toast. Standard disconnect will have a null throwable
            Toast.makeText(mContext, R.string.mqtt_connection_lost, Toast.LENGTH_LONG).show();
        }

        mMqqtClientStatus = MqqtConnectionStatus.DISCONNECTED;

        if (mListener != null) {
            mListener.onMqttDisconnected();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        String message = new String(mqttMessage.getPayload());

        if (message.length() > 0) {      // filter cleared messages (to avoid duplicates)

            Log.d(TAG, "Mqtt messageArrived from topic: " + topic + " message: " + message + " isDuplicate: " + (mqttMessage.isDuplicate() ? "yes" : "no"));
            if (mListener != null) {
                mListener.onMqttMessageArrived(topic, mqttMessage);
            }

            // Fix duplicated messages clearing the received payload and processing only non null messages
            mqttMessage.clearPayload();
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        Log.d(TAG, "Mqtt deliveryComplete");

    }

    // endregion

    // region MqttTraceHandler
    @Override
    public void traceDebug(String source, String message) {
        Log.d(TAG, "Mqtt traceDebug");

    }

    @Override
    public void traceError(String source, String message) {
        Log.d(TAG, "Mqtt traceError");

    }

    @Override
    public void traceException(String source, String message, Exception e) {
        Log.d(TAG, "Mqtt traceException");

    }

    // endregion


    public interface MqttManagerListener {
        void onMqttConnected();

        void onMqttDisconnected();

        void onMqttMessageArrived(String topic, MqttMessage mqttMessage);
    }
}
