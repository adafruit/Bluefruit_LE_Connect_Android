package com.adafruit.bluefruit.le.connect.app.settings;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.mqtt.MqttManager;
import com.adafruit.bluefruit.le.connect.mqtt.MqttSettings;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttSettingsActivity extends ActionBarActivity implements MqttManager.MqttManagerListener {
    // Log
    private final static String TAG = MqttSettingsActivity.class.getSimpleName();

    // UI
    private EditText mServerAddressEditText;
    private EditText mServerPortEditText;
    private EditText mPublishTopicEditText;
    private EditText mSubscribeTopicEditText;
    private Switch mPublishSwitch;
    private Switch mSubscribeSwitch;
    private Button mConnectButton;
    private ProgressBar mConnectProgressBar;
    private TextView mStatusTextView;
    private Spinner mSubscribeSpinner;

    // Data
    private String mPreviousSubscriptionTopic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mqttsettings);


        // Data
        final MqttSettings settings = MqttSettings.getInstance(this);

        // UI
        mServerAddressEditText = (EditText) findViewById(R.id.serverAddressEditText);
        mServerAddressEditText.setText(settings.getServerAddress());
        mServerAddressEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    String serverAddress = mServerAddressEditText.getText().toString();
                    Log.d(TAG, "server address " + serverAddress);
                    MqttSettings.getInstance(MqttSettingsActivity.this).setServerAddress(serverAddress);
                }
            }
        });

        mServerPortEditText = (EditText) findViewById(R.id.serverPortEditText);
        mServerPortEditText.setHint("" + MqttSettings.kDefaultServerPort);
        mServerPortEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    settings.setServerPort(mServerPortEditText.getText().toString());
                    mServerPortEditText.setText("" + settings.getServerPort());
                }
            }
        });

        mPublishTopicEditText = (EditText) findViewById(R.id.publishTopicEditText);
        mPublishTopicEditText.setText(settings.getPublishTopic());
        mPublishTopicEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    settings.setPublishTopic(mPublishTopicEditText.getText().toString());
                }
            }
        });
        mPublishSwitch = (Switch) findViewById(R.id.publishSwitch);
        mPublishSwitch.setChecked(settings.isPublishEnabled());
        mPublishSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setPublishEnabled(isChecked);
            }
        });

        Spinner publishSpinner = (Spinner) findViewById(R.id.publishSpinner);
        publishSpinner.setSelection(settings.getPublishQos());
        publishSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.setPublishQos(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });


        mSubscribeTopicEditText = (EditText) findViewById(R.id.subscribeTopicEditText);
        mSubscribeTopicEditText.setText(settings.getSubscribeTopic());
        mPreviousSubscriptionTopic = settings.getSubscribeTopic();
        mSubscribeTopicEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    String topic = mSubscribeTopicEditText.getText().toString();
                    settings.setSubscribeTopic(topic);
                    subscriptionChanged(topic, mSubscribeSpinner.getSelectedItemPosition(), mPreviousSubscriptionTopic);
                }
            }
        });


        mSubscribeSwitch = (Switch) findViewById(R.id.subscribeSwitch);
        mSubscribeSwitch.setChecked(settings.isSubscribeEnabled());
        mSubscribeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setSubscribeEnabled(isChecked);
                subscriptionChanged(null, mSubscribeSpinner.getSelectedItemPosition(), mPreviousSubscriptionTopic);
            }
        });

        mSubscribeSpinner = (Spinner) findViewById(R.id.subscribeSpinner);
        mSubscribeSpinner.setSelection(settings.getSubscribeQos());
        mSubscribeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean isInitializing = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing) {
                    settings.setSubscribeQos(position);
                    String topic = mSubscribeTopicEditText.getText().toString();
                    subscriptionChanged(topic, mSubscribeSpinner.getSelectedItemPosition(), mPreviousSubscriptionTopic);
                }
                isInitializing = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mConnectButton = (Button) findViewById(R.id.connectButton);
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context context = MqttSettingsActivity.this;
                MqttManager mqttManager = MqttManager.getInstance(context);
                MqttManager.MqqtConnectionStatus status = mqttManager.getClientStatus();
                Log.d(TAG, "current mqtt status: " + status);
                if (status == MqttManager.MqqtConnectionStatus.DISCONNECTED || status == MqttManager.MqqtConnectionStatus.NONE || status == MqttManager.MqqtConnectionStatus.ERROR) {
                    mqttManager.connectFromSavedSettings(context);
                } else {
                    mqttManager.disconnect();
                }

                updateStatusUI();
            }
        });
        mConnectProgressBar = (ProgressBar) findViewById(R.id.connectProgressBar);
        mStatusTextView = (TextView) findViewById(R.id.statusTextView);
        updateStatusUI();
    }

    @Override
    public void onResume() {
        super.onResume();

        MqttManager mqttManager = MqttManager.getInstance(this);
        mqttManager.setListener(this);
    }

    private void subscriptionChanged(String newTopic, int qos, String previousTopic) {
        Log.d(TAG, "subscription changed from: " + previousTopic + " to: " + newTopic + " qos: "+qos);
        MqttManager mqttManager = MqttManager.getInstance(this);

        mqttManager.unsubscribe(previousTopic);
        mqttManager.subscribe(newTopic, qos);
        mPreviousSubscriptionTopic = newTopic;

        /*
        MqttManager.MqqtConnectionStatus status = mqttManager.getClientStatus();

        if (status == MqttManager.MqqtConnectionStatus.CONNECTING || status == MqttManager.MqqtConnectionStatus.CONNECTED)
        {
            // Disconnect to apply the new subscription settings on connect
            mqttManager.disconnect();
        }
        */
    }


    private void updateStatusUI() {
        MqttManager mqttManager = MqttManager.getInstance(this);
        MqttManager.MqqtConnectionStatus status = mqttManager.getClientStatus();

        // Update enable-disable button
        final boolean showWait = (status == MqttManager.MqqtConnectionStatus.CONNECTING || status == MqttManager.MqqtConnectionStatus.DISCONNECTING);
        mConnectButton.setVisibility(showWait ? View.GONE : View.VISIBLE);
        mConnectProgressBar.setVisibility(showWait ? View.VISIBLE : View.GONE);

        if (!showWait) {
            int stringId = R.string.mqtt_enable;
            if (status == MqttManager.MqqtConnectionStatus.CONNECTED) {
                stringId = R.string.mqtt_disable;
            }

            mConnectButton.setText(getString(stringId));
        }

        // Update status text
        int statusStringId;
        switch (status) {
            case CONNECTED:
                statusStringId = R.string.mqtt_status_connected;
                break;
            case CONNECTING:
                statusStringId = R.string.mqtt_status_connecting;
                break;
            case DISCONNECTING:
                statusStringId = R.string.mqtt_status_disconnecting;
                break;
            default:
                statusStringId = R.string.mqtt_status_disconnected;
                break;
        }
        mStatusTextView.setText(getString(statusStringId));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_mqtt, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // region MqttManagerListener
    @Override
    public void onMqttConnected() {
        updateStatusUI();
    }

    @Override
    public void onMqttDisconnected() {
        updateStatusUI();
    }

    @Override
    public void onMqttMessageArrived(String topic, MqttMessage mqttMessage) {

    }

    // endregion
}
