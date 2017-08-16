package com.adafruit.bluefruit.le.connect.app;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;

import java.nio.ByteBuffer;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class PadActivity extends UartInterfaceActivity {
    // Log
    private final static String TAG = PadActivity.class.getSimpleName();

    // Constants
    private final static float kMinAspectRatio = 1.5f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pad_vitae);

        mBleManager = BleManager.getInstance(this);

        // UI
        ImageButton upArrowImageButton = (ImageButton) findViewById(R.id.BtnUp);
        upArrowImageButton.setOnTouchListener(mPadButtonTouchListener);
        ImageButton bottomArrowImageButton = (ImageButton) findViewById(R.id.btnDown);
        bottomArrowImageButton.setOnTouchListener(mPadButtonTouchListener);

        Button button1Button = (Button) findViewById(R.id.Btn1);
        button1Button.setOnTouchListener(mPadButtonTouchListener);
        Button button2Button = (Button) findViewById(R.id.Btn2);
        button2Button.setOnTouchListener(mPadButtonTouchListener);
        Button button3Button = (Button) findViewById(R.id.Btn3);
        button3Button.setOnTouchListener(mPadButtonTouchListener);
        Button button4Button = (Button) findViewById(R.id.Btn4);
        button4Button.setOnTouchListener(mPadButtonTouchListener);

        // initialize the graph
        GraphView graph = (GraphView) findViewById(R.id.graph);
        LineGraphSeries<DataPoint> series = new LineGraphSeries<>(new DataPoint[] {
                new DataPoint(0, 1),
                new DataPoint(1, 5),
                new DataPoint(2, 3),
                new DataPoint(3, 2),
                new DataPoint(4, 6)
        });
        graph.addSeries(series);

        // Start services
        onServicesDiscovered();
    }

    private void adjustAspectRatio() {
        /*
        ViewGroup rootLayout = (ViewGroup) findViewById(R.id.rootLayout);
        int mainWidth = rootLayout.getWidth();

        if (mainWidth > 0) {
            View topSpacerView = findViewById(R.id.topSpacerView);
            View bottomSpacerView = findViewById(R.id.bottomSpacerView);
            int mainHeight = rootLayout.getHeight() - topSpacerView.getLayoutParams().height - bottomSpacerView.getLayoutParams().height;
            if (mainHeight > 0) {
                // Add black bars if aspect ratio is below min
                float aspectRatio = mainWidth / (float) mainHeight;
                if (aspectRatio < kMinAspectRatio) {
                    final int spacerHeight = Math.round(mainHeight * (kMinAspectRatio - aspectRatio));
                    topSpacerView.getLayoutParams().height = spacerHeight / 2;
                    bottomSpacerView.getLayoutParams().height = spacerHeight / 2;
                }
            }
        }
        */
    }

    // sends a press or release
    View.OnTouchListener mPadButtonTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            final int tag = Integer.valueOf((String) view.getTag());
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.setPressed(true);
                sendTouchEvent(tag, true);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                view.setPressed(false);
                sendTouchEvent(tag, false);
                return true;
            }
            return false;
        }
    };

    // sends the tag TODO see where the bluetooth is handles from here.
    private void sendTouchEvent(int tag, boolean pressed) {
        String data = "!B" + tag + (pressed ? "1" : "0");
        ByteBuffer buffer = ByteBuffer.allocate(data.length()).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put(data.getBytes());
        sendDataWithCRC(buffer.array());
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

    /*
    @Override
    public void onConfigurationChanged (Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        adjustAspectRatio();
    }
    */

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        /*
        super.onWindowFocusChanged(hasFocus);

        // Set full screen mode
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            adjustAspectRatio();
        }
        */
    }

    public void onClickExit(View view) {
        finish();
    }

    /*
    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }
*/
    @Override
    public void onDisconnected() {
        super.onDisconnected();
        Log.d(TAG, "Disconnected. Back to previous activity");
        setResult(-1);      // Unexpected Disconnect
        finish();
    }
/*
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

    @Override
    public void onReadRemoteRssi(int rssi) {

    }

    */
}
