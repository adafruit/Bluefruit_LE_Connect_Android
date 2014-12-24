package com.adafruit.bluefruit.le.connect.app;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleServiceListener;

public class PadActivity extends UartInterfaceActivity implements BleServiceListener{
    // Log
    private final static String TAG = PadActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pad);

        mBleManager = BleManager.getInstance(this);


        // UI
        ImageButton upArrowImageButton = (ImageButton)findViewById(R.id.upArrowImageButton);
        upArrowImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton leftArrowImageButton = (ImageButton)findViewById(R.id.leftArrowImageButton);
        leftArrowImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton rightArrowImageButton = (ImageButton)findViewById(R.id.rightArrowImageButton);
        rightArrowImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton bottomArrowImageButton = (ImageButton)findViewById(R.id.bottomArrowImageButton);
        bottomArrowImageButton.setOnTouchListener(mPadButtonTouchListener);

        ImageButton button1ImageButton = (ImageButton)findViewById(R.id.button1ImageButton);
        button1ImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton button2ImageButton = (ImageButton)findViewById(R.id.button2ImageButton);
        button2ImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton button3ImageButton = (ImageButton)findViewById(R.id.button3ImageButton);
        button3ImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton button4ImageButton = (ImageButton)findViewById(R.id.button4ImageButton);
        button4ImageButton.setOnTouchListener(mPadButtonTouchListener);

        // Start services
        onServicesDiscovered();
    }

    View.OnTouchListener mPadButtonTouchListener =  new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            final int tag = new Integer((String)view.getTag());
            if (event.getAction() == MotionEvent.ACTION_DOWN ) {
                sendTouchEvent(tag, true);
                return true;
            }
            else if (event.getAction() == MotionEvent.ACTION_UP ) {
                sendTouchEvent(tag, false);
                return true;
            }
            return false;
        }
    };

    private void sendTouchEvent(int tag, boolean pressed) {
        String data = "!B"+tag+(pressed?"1":"0");
        /*
        ByteBuffer buffer = ByteBuffer.allocate(data.length()+4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put(data.getBytes());
        */
        sendData(data.getBytes());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_pad, menu);
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Set full screen mode
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    }

/*
    public void onClickArrow(View view) {
        int tag = new Integer((String)view.getTag());

    }

    public void onClickPadButton(View view) {
        int tag = new Integer((String)view.getTag());
    }
*/
    public void onClickExit(View view) {
        finish();
    }


    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onServicesDiscovered() {
        mUartService = mBleManager.getGattService(UUID_SERVICE);
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {

    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }
}
