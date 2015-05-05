package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ui.keyboard.CustomEditTextFormatter;
import com.adafruit.bluefruit.le.connect.ui.keyboard.CustomKeyboard;

import java.util.UUID;


public class IBeaconFragment extends Fragment {
    // Log
    private final static String TAG = IBeaconFragment.class.getSimpleName();

    // Constants
    private final static boolean kPersistValues = true;
    private final static String kPreferences = "IBeaconFragment_prefs";
    private final static String kPreferences_vendor = "vendor";
    private final static String kPreferences_uuid = "uuid";
    private final static String kPreferences_major = "major";
    private final static String kPreferences_minor = "minor";


    // UI
    private EditText mVendorEditText;
    private EditText mUuidEditText;
    private EditText mMajorEditText;
    private EditText mMinorEditText;
    private EditText mRssiEditText;

    // Keyboard
    private CustomKeyboard mCustomKeyboard;

    // Data
    private int mRssi;
    private OnFragmentInteractionListener mListener;

    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";


    public static IBeaconFragment newInstance(int rssi) {
        IBeaconFragment fragment = new IBeaconFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PARAM1, rssi);
        fragment.setArguments(args);
        return fragment;
    }

    public IBeaconFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mRssi = getArguments().getInt(ARG_PARAM1);
        }
    }

    public boolean onBackPressed() {
        if (mCustomKeyboard.isCustomKeyboardVisible()) {
            mCustomKeyboard.hideCustomKeyboard();
            return true;
        }
        return false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_ibeacon, container, false);

        // UI
        mVendorEditText = (EditText) rootView.findViewById(R.id.vendorEditText);
        mUuidEditText = (EditText) rootView.findViewById(R.id.uuidEditText);
        mMajorEditText = (EditText) rootView.findViewById(R.id.majorEditText);
        mMinorEditText = (EditText) rootView.findViewById(R.id.minorEditText);
        mRssiEditText = (EditText) rootView.findViewById(R.id.rssiEditText);

        Button chooseButton = (Button) rootView.findViewById(R.id.chooseManufacturerButton);
        chooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onChooseVendor(v);
            }
        });

        Button randomUuidButton = (Button) rootView.findViewById(R.id.randomUuidButton);
        randomUuidButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickRandomUuid(v);
            }
        });


        Button rssiRefreshButton = (Button) rootView.findViewById(R.id.rssiButton);
        rssiRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickRefreshRssi(v);
            }
        });

        Button enableButton = (Button) rootView.findViewById(R.id.enableButton);
        enableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickEnable(v);
            }
        });

        Button disableButton = (Button) rootView.findViewById(R.id.disableButton);
        disableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onDisable();
            }
        });


        // Custom keyboard
        if (mCustomKeyboard == null) {
            mCustomKeyboard = new CustomKeyboard(getActivity());
        }

        mCustomKeyboard.attachToEditText(mVendorEditText, R.xml.keyboard_hexadecimal);
        CustomEditTextFormatter.attachToEditText(mVendorEditText, 4, "", 4);

        mCustomKeyboard.attachToEditText(mUuidEditText, R.xml.keyboard_hexadecimal);
        CustomEditTextFormatter.attachToEditText(mUuidEditText, 32, "-", 2);

        mCustomKeyboard.attachToEditText(mMajorEditText, R.xml.keyboard_hexadecimal);
        CustomEditTextFormatter.attachToEditText(mMajorEditText, 4, "", 4);

        mCustomKeyboard.attachToEditText(mMinorEditText, R.xml.keyboard_hexadecimal);
        CustomEditTextFormatter.attachToEditText(mMinorEditText, 4, "", 4);

        mCustomKeyboard.attachToEditText(mRssiEditText, R.xml.keyboard_decimal);
        CustomEditTextFormatter.attachToEditText(mRssiEditText, 3, "", 3);

        // Generate initial state
        String manufacturers[] = getResources().getStringArray(R.array.beacon_manufacturers_ids);

        SharedPreferences preferences = getActivity().getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
        mVendorEditText.setText(preferences.getString(kPreferences_vendor, manufacturers[0]));
        String uuid = preferences.getString(kPreferences_uuid, null);
        if (uuid == null) {
            onClickRandomUuid(null);
        }
        else {
            mUuidEditText.setText(uuid);
        }
        mMajorEditText.setText(preferences.getString(kPreferences_major, "0000"));
        mMinorEditText.setText(preferences.getString(kPreferences_minor, "0000"));
        mRssiEditText.setText("" + mRssi);

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;

        // Preserver values
        if (kPersistValues) {
            SharedPreferences settings = getActivity().getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(kPreferences_vendor, mVendorEditText.getText().toString());
            editor.putString(kPreferences_uuid, mUuidEditText.getText().toString());
            editor.putString(kPreferences_major, mMajorEditText.getText().toString());
            editor.putString(kPreferences_minor, mMinorEditText.getText().toString());
            editor.commit();
        }
    }


    public void onClickRandomUuid(View view) {
        final String randomUuid = UUID.randomUUID().toString().replaceAll("-", "");
        String result = CustomEditTextFormatter.formatText(randomUuid.toString(), 32, "-", 2);
        mUuidEditText.setText(result);

        // set cursor at the end
        mUuidEditText.requestFocus();
        mUuidEditText.setSelection(result.length());
    }

    public void onClickRefreshRssi(View view) {
        BleManager bleManager = BleManager.getInstance(getActivity());
        boolean waiting = bleManager.readRssi(); // Wait for callback
        if (waiting) {

        }
    }

    public void onChooseVendor(final View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
        builder.setTitle(R.string.beacon_manufacturer_choose_title)
                .setItems(R.array.beacon_manufacturers_names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String manufacturers[] = view.getContext().getResources().getStringArray(R.array.beacon_manufacturers_ids);
                        String manufacturerId = manufacturers[which];
                        mVendorEditText.setText(manufacturerId);
                    }
                });
        builder.create().show();
    }


    public void onClickEnable(View view) {
        String manufacturerId = "0x" + getVendor();
        String uuid = getUuid();
        String major = getMajor();
        String minor = getMinor();
        String rssi = getRssi();

        mListener.onEnable(manufacturerId, uuid, major, minor, rssi);
    }

    public String getVendor() {
        return mVendorEditText.getText().toString();
    }

    public String getUuid() {
        return mUuidEditText.getText().toString();
    }

    public String getMajor() {
        String major = mMajorEditText.getText().toString();
        return bigEndianString4Chars(major);
    }

    public String getMinor() {
        String minor =  mMinorEditText.getText().toString();
        return bigEndianString4Chars(minor);
    }

    private String bigEndianString4Chars(String value)
    {
        // Ensure that we have 4 characters
        while (value.length() < 4) {
            value = "0" + value;
        }
        // Change order to big endian
        return "0x"+value.charAt(2)+value.charAt(3)+value.charAt(0)+value.charAt(1);
    }

    public String getRssi() {
        return mRssiEditText.getText().toString();
    }

    public void setRssi(int rssi) {
        mRssiEditText.setText("" + rssi);
    }

    public interface OnFragmentInteractionListener {
        void onEnable(String vendor, String uuid, String major, String minor, String rssi);

        void onDisable();
    }

}
