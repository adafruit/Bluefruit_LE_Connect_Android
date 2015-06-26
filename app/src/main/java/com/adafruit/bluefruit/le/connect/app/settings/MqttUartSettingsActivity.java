package com.adafruit.bluefruit.le.connect.app.settings;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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

public class MqttUartSettingsActivity extends ActionBarActivity implements MqttManager.MqttManagerListener {
    // Log
    private final static String TAG = MqttUartSettingsActivity.class.getSimpleName();

    // Constants
    private static final int kNumPublishFeeds = 2;
    public static final int kPublishFeed_RX = 0;
    public static final int kPublishFeed_TX = 1;

    public static final int kSubscribeBehaviour_LocalOnly = 0;      // note: should have the same order than strings/mqtt_uart_subscribe_behaviour
    public static final int kSubscribeBehaviour_Transmit = 1;

    // UI
    private EditText mServerAddressEditText;
    private EditText mServerPortEditText;
    private EditText mSubscribeTopicEditText;
    private Switch mSubscribeSwitch;
    private Button mConnectButton;
    private ProgressBar mConnectProgressBar;
    private TextView mStatusTextView;
    private Spinner mSubscribeSpinner;
    private EditText mUsernameEditText;
    private EditText mPasswordEditText;
    private Switch mCleanSessionSwitch;
    private Switch mSslConnectionSwitch;

    // Data
    private String mPreviousSubscriptionTopic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mqttsettings);

        // Data
        final MqttSettings settings = MqttSettings.getInstance(this);

        // UI - Server
        mServerAddressEditText = (EditText) findViewById(R.id.serverAddressEditText);
        mServerAddressEditText.setText(settings.getServerAddress());
        mServerAddressEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    final String serverAddress = mServerAddressEditText.getText().toString();
                    settings.setServerAddress(serverAddress);
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

        // UI - Publish
        Switch publishSwitch = (Switch) findViewById(R.id.publishSwitch);
        publishSwitch.setChecked(settings.isPublishEnabled());
        publishSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setPublishEnabled(isChecked);
            }
        });

        final int kPublishTopicEditTextsIds[] = {R.id.publish0TopicEditText, R.id.publish1TopicEditText};
        final int kPublishTopicSpinnerIds[] = {R.id.publish0Spinner, R.id.publish1Spinner};
        for (int i = 0; i < kNumPublishFeeds; i++) {
            final int index = i;

            final EditText publishTopicEditText = (EditText) findViewById(kPublishTopicEditTextsIds[i]);
            publishTopicEditText.setText(settings.getPublishTopic(index));
            publishTopicEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (!hasFocus) {
                        settings.setPublishTopic(index, publishTopicEditText.getText().toString());
                    }
                }
            });

            Spinner publishSpinner = (Spinner) findViewById(kPublishTopicSpinnerIds[i]);
            publishSpinner.setSelection(settings.getPublishQos(index));
            publishSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    settings.setPublishQos(index, position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
        }

        // UI - Subscribe
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

        Spinner subscribeBehaviourSpinner = (Spinner) findViewById(R.id.subscribeBehaviourSpinner);
        subscribeBehaviourSpinner.setSelection(settings.getSubscribeBehaviour());
        subscribeBehaviourSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean isInitializing = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing) {
                    settings.setSubscribeBehaviour(position);
                }
                isInitializing = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });


        // UI - Advanced
        mUsernameEditText = (EditText) findViewById(R.id.usernameEditText);
        mUsernameEditText.setText(settings.getUsername());
        mUsernameEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    final String username = mUsernameEditText.getText().toString();
                    settings.setUsername(username);
                }
            }
        });

        mPasswordEditText = (EditText) findViewById(R.id.passwordEditText);
        mPasswordEditText.setText(settings.getPassword());
        mPasswordEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    final String password = mPasswordEditText.getText().toString();
                    settings.setPassword(password);
                }
            }
        });

        mCleanSessionSwitch = (Switch) findViewById(R.id.cleanSessionSwitch);
        mCleanSessionSwitch.setChecked(settings.isCleanSession());
        mCleanSessionSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setCleanSession(isChecked);
            }
        });

        mSslConnectionSwitch = (Switch) findViewById(R.id.sslConnectionSwitch);
        mSslConnectionSwitch.setChecked(settings.isSslConnection());
        mSslConnectionSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setSslConnection(isChecked);
            }
        });

        mConnectButton = (Button) findViewById(R.id.connectButton);
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Force remove focus from last field to take into account the changes
                View currentFocusView = getCurrentFocus();
                if (currentFocusView != null) {
                    currentFocusView.clearFocus();
                }

                // Dismiss keyboard
                Context context = MqttUartSettingsActivity.this;
                dismissKeyboard(v);

                // Connect / Disconnect
                MqttManager mqttManager = MqttManager.getInstance(context);
                MqttManager.MqqtConnectionStatus status = mqttManager.getClientStatus();
                Log.d(TAG, "current mqtt status: " + status);
                if (status == MqttManager.MqqtConnectionStatus.DISCONNECTED || status == MqttManager.MqqtConnectionStatus.NONE || status == MqttManager.MqqtConnectionStatus.ERROR) {
                    mqttManager.connectFromSavedSettings(context);
                } else {
                    mqttManager.disconnect();
                }

                // Update UI
                updateStatusUI();
            }
        });
        mConnectProgressBar = (ProgressBar) findViewById(R.id.connectProgressBar);
        mStatusTextView = (TextView) findViewById(R.id.statusTextView);
        updateStatusUI();
    }

    private void dismissKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @Override
    public void onResume() {
        super.onResume();

        MqttManager mqttManager = MqttManager.getInstance(this);
        mqttManager.setListener(this);
    }

    private void subscriptionChanged(String newTopic, int qos, String previousTopic) {
        Log.d(TAG, "subscription changed from: " + previousTopic + " to: " + newTopic + " qos: " + qos);
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
        mConnectButton.setVisibility(showWait ? View.INVISIBLE : View.VISIBLE);
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
            case ERROR:
                statusStringId = R.string.mqtt_status_error;
                break;
            default:
                statusStringId = R.string.mqtt_status_disconnected;
                break;
        }
        mStatusTextView.setText(getString(statusStringId));
    }

    /*
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
    */

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
