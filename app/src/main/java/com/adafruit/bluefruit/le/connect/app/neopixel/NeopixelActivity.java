package com.adafruit.bluefruit.le.connect.app.neopixel;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.utils.FileUtils;
import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.UartInterfaceActivity;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ui.utils.MetricsUtils;
import com.adafruit.bluefruit.le.connect.ui.utils.TwoDimensionScrollView;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NeopixelActivity extends UartInterfaceActivity {
    // Log
    private final static String TAG = NeopixelActivity.class.getSimpleName();

    // Contants
    private final static String kPreferences = "NeopixelActivity_prefs";
    private final static String kPreferences_showSketchTooltip = "showSketchTooltip";


    // Config
    private static final int kDefaultLedColor = Color.WHITE;
    private final static float kMinBoardScale = 0.1f;
    private final static float kMaxBoardScale = 10f;
    private final static float kLedPixelSize = 44;

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_NeopixelColorPickerActivity = 0;
    private static final int kActivityRequestCode_NeopixelBoardSelectorActivity = 1;
    private static final int kActivityRequestCode_NeopixelBoardTypeActivity = 2;

    // UI
    private TextView mStatusTextView;
    private Button mConnectButton;
    private ViewGroup mBoardContentView;
    private TwoDimensionScrollView mCustomPanningView;
    private ViewGroup mRotationViewGroup;
    //    private ViewGroup mBoardControlsViewGroup;
    private Button mColorPickerButton;

    // Data
    private List<Integer> mDefaultPalette;
    private NeopixelBoard mBoard;
    private List<Integer> mBoardCachedColors;
    private int mCurrentColor = Color.RED;
    //private Rect mBoardMargin = new Rect();

    private float mBoardScale = 1f;
    private float mBoardRotation = 0f;
    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;

    // Neopixel
    private boolean mIsSketchChecked = false;
    private boolean mIsSketchDetected = false;


    private DataFragment mRetainedDataFragment;
    private boolean isSketchTooltipAlreadyShown = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_neopixel);

        // Start bluetooth
        mBleManager = BleManager.getInstance(this);
        restoreRetainedDataFragment();

        // Read palette data
        String boardsJsonString = FileUtils.readAssetsFile("neopixel" + File.separator + "NeopixelDefaultPalette.json", getAssets());
        try {
            mDefaultPalette = new ArrayList<>();
            JSONArray paletteArray = new JSONArray(boardsJsonString);
            for (int i = 0; i < paletteArray.length(); i++) {
                String colorString = paletteArray.getString(i);
                int color = Color.parseColor("#" + colorString);
                mDefaultPalette.add(color);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error decoding default palette");
        }


        // UI
        mStatusTextView = (TextView) findViewById(R.id.statusTextView);
        mConnectButton = (Button) findViewById(R.id.connectButton);
        mBoardContentView = (ViewGroup) findViewById(R.id.boardContentView);
        RecyclerView paletteRecyclerView = (RecyclerView) findViewById(R.id.paletteRecyclerView);
        if (paletteRecyclerView != null) {
            paletteRecyclerView.setHasFixedSize(true);
            paletteRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            RecyclerView.Adapter paletteAdapter = new PaletteAdapter();
            paletteRecyclerView.setAdapter(paletteAdapter);
        }


        mRotationViewGroup = (ViewGroup) findViewById(R.id.rotationViewGroup);
        mCustomPanningView = (TwoDimensionScrollView) findViewById(R.id.customPanningView);
        if (mCustomPanningView != null) {
            mCustomPanningView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                                                                                   @Override
                                                                                   public void onGlobalLayout() {
                                                                                       mCustomPanningView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                                                                                       createBoardUI();
                                                                                       restoreCachedBoardColors();
                                                                                   }
                                                                               }
            );
        }

        setupZoomGesture();

        //mBoardControlsViewGroup = (ViewGroup) findViewById(R.id.boardControlsViewGroup);
        mColorPickerButton = (Button) findViewById(R.id.colorPickerButton);
        setViewBackgroundColor(mColorPickerButton, mCurrentColor);
        SeekBar brightnessSeekBar = (SeekBar) findViewById(R.id.brightnessSeekBar);
        if (brightnessSeekBar != null) {
            brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    setBrightness(seekBar.getProgress() / 100.0f);

                }
            });
        }

        updateStatusUI();

        // Continue
        onServicesDiscovered();


        // Tooltip
        final SharedPreferences preferences = getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
        boolean showSketchTooltip = preferences.getBoolean(kPreferences_showSketchTooltip, true);

        if (!isSketchTooltipAlreadyShown && showSketchTooltip) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dialog_notice).setMessage(R.string.neopixel_sketch_tooltip)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(R.string.dialog_dontshowagain, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putBoolean(kPreferences_showSketchTooltip, false);
                            editor.apply();
                        }
                    });
            builder.create().show();

            isSketchTooltipAlreadyShown = true;

        }


    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");

        super.onResume();

        // Setup listeners
        mBleManager.setBleListener(this);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");

        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        // Retain data
        saveRetainedDataFragment();

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_neopixel, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_help) {
            startHelp();
            return true;
        } else if (id == R.id.action_boardSelector) {
            Intent intent = new Intent(this, NeopixelBoardSelectorActivity.class);
            startActivityForResult(intent, kActivityRequestCode_NeopixelBoardSelectorActivity);
            return true;
        } else if (id == R.id.action_boardType) {
            if (mBoard != null) {
                Intent intent = new Intent(this, NeopixelBoardTypeActivity.class);
                intent.putExtra(NeopixelBoardTypeActivity.kActivityParameter_CurrentType, mBoard.type);
                startActivityForResult(intent, kActivityRequestCode_NeopixelBoardTypeActivity);
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }


    private void setupZoomGesture() {
        mGestureDetector = new GestureDetector(this, new GestureListener());

        mScaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scale = 1 - detector.getScaleFactor();

                mBoardScale += scale;

                if (mBoardScale < kMinBoardScale) mBoardScale = kMinBoardScale;
                if (mBoardScale > kMaxBoardScale) mBoardScale = kMaxBoardScale;

                mBoardContentView.setScaleX(1f / mBoardScale);
                mBoardContentView.setScaleY(1f / mBoardScale);
                return true;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        super.dispatchTouchEvent(event);

        boolean scaleResult = mScaleDetector.onTouchEvent(event);
        boolean gestureResult = mGestureDetector.onTouchEvent(event);

        return scaleResult || gestureResult;
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        // event when double tap occurs
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            resetScaleAndPanning();
            return true;
        }
    }

    private void createBoardUI() {
        if (mBoard != null) {
            final ViewGroup canvasView = mBoardContentView;

            canvasView.removeAllViews();

            final int kLedSize = (int) MetricsUtils.convertDpToPixel(this, kLedPixelSize);
            final int canvasViewWidth = canvasView.getWidth();
            final int canvasViewHeight = canvasView.getHeight();
            final int boardWidth = mBoard.width * kLedSize;
            final int boardHeight = mBoard.height * kLedSize;

            final int marginLeft = (canvasViewWidth - boardWidth) / 2;
            final int marginTop = (canvasViewHeight - boardHeight) / 2;

            for (int j = 0, k = 0; j < mBoard.height; j++) {
                for (int i = 0; i < mBoard.width; i++, k++) {
                    View ledView = LayoutInflater.from(this).inflate(R.layout.layout_neopixel_led, canvasView, false);
                    Button ledButton = (Button) ledView.findViewById(R.id.ledButton);
                    RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(kLedSize, kLedSize);
                    layoutParams.leftMargin = i * kLedSize + marginLeft;//mBoardMargin.left;
                    layoutParams.topMargin = j * kLedSize + marginTop;//mBoardMargin.top;
                    ledView.setLayoutParams(layoutParams);
                    ledButton.setTag(k);

                    setViewBackgroundColor(ledButton, kDefaultLedColor);
                    canvasView.addView(ledView);
                }
            }



            // Setup initial scroll and scale
            resetScaleAndPanning();
        }
    }

    private void resetScaleAndPanning() {
        final int kLedSize = (int) MetricsUtils.convertDpToPixel(this, kLedPixelSize);
        final int canvasViewWidth = mBoardContentView.getWidth();
        final int canvasViewHeight = mBoardContentView.getHeight();
        final int boardWidth = mBoard.width * kLedSize;
        final int boardHeight = mBoard.height * kLedSize;

        int panningViewWidth = mCustomPanningView.getWidth();
        mBoardScale = 1f/Math.min(1f, (panningViewWidth/(float)boardWidth)*0.85f)+0;
        mBoardContentView.setScaleX(1f / mBoardScale);
        mBoardContentView.setScaleY(1f / mBoardScale);
        mRotationViewGroup.setRotation(0);
        Log.d(TAG, "Initial scale: "+mBoardScale);


        int offsetX = Math.max(0, (canvasViewWidth - boardWidth) / 2);
        int offsetY = Math.max(0, (canvasViewHeight - boardHeight) / 2);
        mCustomPanningView.scrollTo(offsetX, offsetY);

    }

    public void onLedClicked(View view) {

        if (mIsSketchChecked) {
            int tag = (Integer) view.getTag();
            byte x = (byte) (tag % mBoard.width);
            byte y = (byte) (tag / mBoard.width);

            Log.d(TAG, "Led (" + x + "," + y + ")");

            setViewBackgroundColor(view, mCurrentColor);
            setPixelColor(mCurrentColor, x, y);

            mBoardCachedColors.set(y * mBoard.width + x, mCurrentColor);
        }
    }

    private void setViewBackgroundColor(View view, int color) {
        setViewBackgroundColor(view, color, 0, 0);
    }

    private void setViewBackgroundColor(View view, int color, int borderColor, int borderWidth) {
        GradientDrawable backgroundDrawable = (GradientDrawable) view.getBackground();
        backgroundDrawable.setColor(color);
        backgroundDrawable.setStroke(borderWidth, borderColor);
    }
    // endregion

    // region BleManagerListener
    @Override
    public void onDisconnected() {
        super.onDisconnected();
        Log.d(TAG, "Disconnected. Back to previous activity");
        setResult(-1);      // Unexpected Disconnect
        finish();
    }

    @Override
    public void onServicesDiscovered() {
        super.onServicesDiscovered();
        enableRxNotifications();
    }
    // endregion


    private void updateStatusUI() {
        mConnectButton.setEnabled((!mIsSketchDetected || !mIsSketchChecked) && !isWaitingForSendDataResponse());

        mCustomPanningView.setAlpha(mIsSketchDetected ? 1.0f : 0.2f);
//        mBoardControlsViewGroup.setAlpha(mIsSketchDetected ? 1.0f : 0.2f);

        int statusMessageId;
        if (!mIsSketchChecked) {
            statusMessageId = R.string.neopixel_status_readytoconnect;
        } else {
            if (!mIsSketchDetected) {
                statusMessageId = R.string.neopixel_status_notdetected;
            } else {
                if (mBoard == null) {
                    if (isWaitingForSendDataResponse()) {
                        statusMessageId = R.string.neopixel_status_waitingsetup;
                    } else {
                        statusMessageId = R.string.neopixel_status_readyforsetup;
                    }
                } else {
                    statusMessageId = R.string.neopixel_status_connected;
                }
            }
        }

        mStatusTextView.setText(statusMessageId);
    }

    public void onClickConnect(View view) {
        checkSketchVersion();
    }

    public void onClickColorPicker(View view) {
        Intent intent = new Intent(this, NeopixelColorPickerActivity.class);
        intent.putExtra(NeopixelColorPickerActivity.kActivityParameter_SelectedColorKey, mCurrentColor);
        startActivityForResult(intent, kActivityRequestCode_NeopixelColorPickerActivity);

    }

    public void onClickRotate(View view) {
        mBoardRotation = (mBoardRotation + 90) % 360;
        mRotationViewGroup.setRotation(mBoardRotation);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (resultCode == RESULT_OK) {
            if (requestCode == kActivityRequestCode_NeopixelColorPickerActivity && intent != null) {
                final int color = intent.getIntExtra(NeopixelColorPickerActivity.kActivityResult_SelectedColorResultKey, Color.WHITE);
                mCurrentColor = color;
                updatePickerColorButton(true);
            } else if (requestCode == kActivityRequestCode_NeopixelBoardSelectorActivity && intent != null) {
                final int boardIndex = intent.getIntExtra(NeopixelBoardSelectorActivity.kActivityResult_BoardIndexResultKey, -1);
                if (boardIndex >= 0) {
                    NeopixelBoard board = new NeopixelBoard(this, boardIndex, NeopixelBoard.kDefaultType);
                    changeBoard(board);
                } else {
                    final int lineStripLength = intent.getIntExtra(NeopixelBoardSelectorActivity.kActivityResult_LineStripResultKey, 0);
                    if (lineStripLength > 0) {
                        NeopixelBoard board = new NeopixelBoard("1x" + lineStripLength, (byte) lineStripLength, (byte) 1, (byte) 3, (byte) lineStripLength, NeopixelBoard.kDefaultType);
                        changeBoard(board);
                    }
                }
            } else if (requestCode == kActivityRequestCode_NeopixelBoardTypeActivity && intent != null) {
                final short boardType = (short) intent.getShortExtra(NeopixelBoardTypeActivity.kActivityResult_BoardTypeResultKey, NeopixelBoard.kDefaultType);

                if (boardType != mBoard.type) {
                    mBoard.type = boardType;
                    changeBoard(mBoard);
                }
            }
        }
    }

    private void changeBoard(NeopixelBoard board) {
        mBoard = board;
        if (mIsSketchDetected) {
            setupNeopixel(mBoard);
        }
        createBoardUI();
        updateStatusUI();
    }

    public void onClickClear(View view) {

        for (int i = 0; i < mBoardContentView.getChildCount(); i++) {
            View ledView = mBoardContentView.getChildAt(i);
            Button ledButton = (Button) ledView.findViewById(R.id.ledButton);
            setViewBackgroundColor(ledButton, mCurrentColor);
        }

        final int boardSize = Math.max(0, mBoard.width * mBoard.height);
        mBoardCachedColors = new ArrayList<>(Collections.nCopies(boardSize, mCurrentColor));
        clearBoard(mCurrentColor);
    }

    private void restoreCachedBoardColors() {
        for (int i = 0; i < mBoardContentView.getChildCount(); i++) {
            View ledView = mBoardContentView.getChildAt(i);
            Button ledButton = (Button) ledView.findViewById(R.id.ledButton);

            int color = mBoardCachedColors.get(i);
            setViewBackgroundColor(ledButton, color);
        }
    }

    private void checkSketchVersion() {
        // Send version command and check if returns a valid response
        Log.d(TAG, "Command: get Version");

        mIsSketchChecked = true;
        byte command = 0x56;
        byte[] data = {command};
        sendData(data, new SendDataCompletionHandler() {
            @Override
            public void sendDataResponse(String data) {
                boolean isSketchDetected = false;
                if (data != null) {
                    isSketchDetected = data.startsWith("Neopixel");
                }

                Log.d(TAG, "isNeopixelAvailable: " + (isSketchDetected ? "yes" : "no"));
                onSketchDetected(isSketchDetected);
            }
        });
    }

    private void onSketchDetected(boolean isSketchDetected) {
        this.mIsSketchDetected = isSketchDetected;

        if (isSketchDetected) {
            setupNeopixel(mBoard);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateStatusUI();
            }
        });
    }

    private void setupNeopixel(NeopixelBoard device) {
        Log.d(TAG, "Command: Setup");

        int pixelType = device.type;
        byte[] data = {0x53, device.width, device.height, device.components, device.stride, (byte) pixelType, (byte) ((byte) (pixelType >> 8) & 0xff)};
        sendData(data, new SendDataCompletionHandler() {
            @Override
            public void sendDataResponse(String data) {
                boolean success = false;
                if (data != null) {
                    success = data.startsWith("OK");
                }
                Log.d(TAG, "setup success: " + (success ? "yes" : "no"));

                onNeopixelSetupFinished(success);
            }
        });
    }

    private void onNeopixelSetupFinished(boolean success) {
        if (success) {
            clearBoard(kDefaultLedColor);
            mBoardCachedColors = new ArrayList<>(Collections.nCopies(mBoard.width * mBoard.height, kDefaultLedColor));
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateStatusUI();
            }
        });
    }

    private void clearBoard(int color) {
        Log.d(TAG, "Command: Clear");


        if (mBoard != null && mBoard.components == 3) {

            byte red = (byte) Color.red(color);
            byte green = (byte) Color.green(color);
            byte blue = (byte) Color.blue(color);

            byte[] data = {0x43, red, green, blue};
            sendData(data);
        }
    }

    private void setPixelColor(int color, byte x, byte y) {
        Log.d(TAG, "Command: set Pixel");


        if (mBoard != null && mBoard.components == 3) {
            byte red = (byte) Color.red(color);
            byte green = (byte) Color.green(color);
            byte blue = (byte) Color.blue(color);

            byte[] data = {0x50, x, y, red, green, blue};
            sendData(data);
        }
    }

    private void setBrightness(float brightness) {
        Log.d(TAG, "Command: set Brightness: " + brightness);

        byte brightnessValue = (byte) (brightness * 255);

        byte[] data = {0x42, brightnessValue};
        sendData(data);
    }


    // region Toolbar
    private void startHelp() {
        // Launch app help activity
        Intent intent = new Intent(this, NeopixelHelpActivity.class);
        intent.putExtra("title", getString(R.string.neopixel_help_title));
        intent.putExtra("help", "neopixel_help.html");
        startActivity(intent);
    }

    // endregion

    // region Palette

    public class PaletteAdapter extends RecyclerView.Adapter<PaletteAdapter.ViewHolder> {

        public class ViewHolder extends RecyclerView.ViewHolder {
            public Button mColorButton;

            public ViewHolder(ViewGroup view) {
                super(view);
                mColorButton = (Button) view.findViewById(R.id.colorButton);
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ViewGroup paletteColorView = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_neopixel_palette_item, parent, false);

            final ViewHolder viewHolder = new ViewHolder(paletteColorView);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final int color = mDefaultPalette.get(position);
            setViewBackgroundColor(holder.mColorButton, color);

            holder.mColorButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
//                    Log.d(TAG, "Palette color: " + index);
                    mCurrentColor = color;
                    updatePickerColorButton(false);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mDefaultPalette.size();
        }
    }

    // endregion

    void updatePickerColorButton(boolean isSelected) {
        final int borderWidth = (int) MetricsUtils.convertDpToPixel(this, isSelected ? 4f : 2f);
        setViewBackgroundColor(mColorPickerButton, mCurrentColor, borderWidth, isSelected ? Color.WHITE : darkerColor(mCurrentColor, 0.5f));
    }

    private static int darkerColor(int color, float factor) {
        final int a = Color.alpha(color);
        final int r = Color.red(color);
        final int g = Color.green(color);
        final int b = Color.blue(color);

        return Color.argb(a,
                Math.max((int) (r * factor), 0),
                Math.max((int) (g * factor), 0),
                Math.max((int) (b * factor), 0));
    }


    // region DataFragment
    public static class DataFragment extends Fragment {
        private NeopixelBoard mBoard;
        private List<Integer> mBoardCachedColors;
        private int mCurrentColor = Color.RED;
        private boolean mIsSketchChecked = false;
        private boolean mIsSketchDetected = false;


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }
    }

    private void restoreRetainedDataFragment() {
        // find the retained fragment
        FragmentManager fm = getFragmentManager();
        mRetainedDataFragment = (DataFragment) fm.findFragmentByTag(TAG);

        if (mRetainedDataFragment == null) {
            // Create
            mRetainedDataFragment = new DataFragment();
            fm.beginTransaction().add(mRetainedDataFragment, TAG).commit();

            mBoard = new NeopixelBoard(this, 0, NeopixelBoard.kDefaultType);
            mBoardCachedColors = new ArrayList<>(Collections.nCopies(mBoard.width * mBoard.height, Color.WHITE));

        } else {
            // Restore status
            mBoard = mRetainedDataFragment.mBoard;
            mCurrentColor = mRetainedDataFragment.mCurrentColor;
            mIsSketchChecked = mRetainedDataFragment.mIsSketchChecked;
            mIsSketchDetected = mRetainedDataFragment.mIsSketchDetected;
            mBoardCachedColors = mRetainedDataFragment.mBoardCachedColors;
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mBoard = mBoard;
        mRetainedDataFragment.mCurrentColor = mCurrentColor;
        mRetainedDataFragment.mIsSketchChecked = mIsSketchChecked;
        mRetainedDataFragment.mIsSketchDetected = mIsSketchDetected;
        mRetainedDataFragment.mBoardCachedColors = mBoardCachedColors;
    }
    // endregion
}