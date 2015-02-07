package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;


public class URIBeaconFragment extends Fragment {
    // Log
    private final static String TAG = URIBeaconFragment.class.getSimpleName();

    // UI
    private EditText mUriEditText;

    private OnFragmentInteractionListener mListener;

    public static URIBeaconFragment newInstance() {
        URIBeaconFragment fragment = new URIBeaconFragment();
        Bundle args = new Bundle();

        fragment.setArguments(args);
        return fragment;
    }

    public URIBeaconFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {

        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_uribeacon, container, false);

        // UI
        mUriEditText = (EditText) rootView.findViewById(R.id.uriEditText);


        Button enableButton = (Button) rootView.findViewById(R.id.enableButton);
        enableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onEnable(v);
            }
        });

        Button disableButton = (Button) rootView.findViewById(R.id.disableButton);
        disableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onDisable();
            }
        });

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
    }

    private void onEnable(View view) {

        StringBuilder encodedString = new StringBuilder();
        String errorMessage = encodeUriBeaconUri(mUriEditText.getText().toString(), encodedString);
        if (errorMessage == null) {
            mListener.onEnable(encodedString.toString());
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
            builder.setMessage(errorMessage).setPositiveButton(android.R.string.ok, null);
            builder.create().show();
        }
    }

    /*
        Encodes a uri for URIBeacon
        Returns null encoding was correct, or error message
     */
    private String encodeUriBeaconUri(String input, StringBuilder result) {

        final String header1 = "03-03-D8-FE"; // Complete 16-bit Service List Flag, UUID = 0xFED8
        final String header2 = "-16-D8-FE";   // Service Data AD Type and UUID again

        String flags = "-00";
        String txpower = "-00";
        String prefix = "";
        int len = 6;  // Payload len counter, minus header1

        // Make sure we have a valid string
        if (input.length() < 1) {
            String errorMessage = "Invalid URI";
            Log.w(TAG, errorMessage);
            return errorMessage;
        }

        // See if we can chop off the Uri prefix
        if (input.indexOf("urn:uuid:") > -1) {
            prefix = "-04";
            input = input.substring(9);
        } else if (input.indexOf("http://www.") > -1) {
            prefix = "-00";
            input = input.substring(11);
        } else if (input.indexOf("https://www.") > -1) {
            prefix = "-01";
            input = input.substring(12);
        } else if (input.indexOf("http://") > -1) {
            prefix = "-02";
            input = input.substring(7);
        } else if (input.toLowerCase().indexOf("https://") > -1) {
            prefix = "-03";
            input = input.substring(8);
        } else {
            String errorMessage = "Prefix must be one of the following values:\n\nhttp://www.\nhttps://www.\nhttp://\nhttps://\nurn:uuid:";
            Log.w(TAG, errorMessage);
            return errorMessage;
        }

        // Get the total payload length so far (minus the header and the len byte)
        len += input.length();

        // Generate the full URI payload (minus the header and len byte)
        String uri = flags + txpower + prefix + BleUtils.stringToHex(input);

        // See if we can chop off the suffix in the Uri
        if (input.indexOf(".com/") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".com/"), "-00");
            len -= 4;
        } else if (input.indexOf(".org/") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".org/"), "-01");
            len -= 4;
        } else if (input.indexOf(".edu/") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".edu/"), "-02");
            len -= 4;
        } else if (input.indexOf(".net/") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".net/"), "-03");
            len -= 4;
        } else if (input.indexOf(".info/") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".info/"), "-04");
            len -= 5;
        } else if (input.indexOf(".biz/") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".biz/"), "-05");
            len -= 4;
        } else if (input.indexOf(".gov/") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".gov/"), "-06");
            len -= 4;
        } else if (input.indexOf(".com") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".com"), "-07");
            len -= 3;
        } else if (input.indexOf(".org") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".org"), "-08");
            len -= 3;
        } else if (input.indexOf(".edu") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".edu"), "-09");
            len -= 3;
        } else if (input.indexOf(".net") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".net"), "-0A");
            len -= 3;
        } else if (input.indexOf(".info") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".info"), "-0B");
            len -= 4;
        } else if (input.indexOf(".biz") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".biz"), "-0C");
            len -= 3;
        } else if (input.indexOf(".gov") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".gov"), "-0D");
            len -= 3;
        }

        // Generate the len string (zero-padding if necessary)
        String lenStr = "";
        if (len < 16) {
            lenStr = "-0" + Integer.toHexString(len).toUpperCase();
        } else {
            lenStr = "-" + Integer.toHexString(len).toUpperCase();
        }

        // Add header1 and the payload len byte to the len count
        len += 5;

        // Make sure we don't exceed the 27 byte advertising payload limit
        if (len > 27) {
            String errorMessage = "Invalid payload length. The encoded UriBeacon payload must be <= 27 bytes (16 characters max for the custom URI segment).\n\n" +
                    "The current payload is " + Integer.toString(len) + " bytes long.";

            Log.w(TAG, errorMessage);
            return errorMessage;
        }

        // fully encoded UriBeacon payload
        result.setLength(0);
        result.append(header1 + lenStr + header2 + uri);
        return null;
    }

    public interface OnFragmentInteractionListener {
        public void onEnable(String encodedUri);

        public void onDisable();
    }

}
