package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.shortener.BitlyShortenerAsyncTask;
import com.adafruit.bluefruit.le.connect.app.shortener.ShortenerAsyncTask;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;


public class URIBeaconFragment extends Fragment implements ShortenerAsyncTask.ShortenerListener {
    // Log
    private final static String TAG = URIBeaconFragment.class.getSimpleName();

    // Constants
    private final static boolean kPersistValues = true;
    private final static String kPreferences = "URIBeaconFragment_prefs";
    private final static String kPreferences_uri = "uri";
    private final static String kPreferences_shorten = "shorten";

    // UI
    private EditText mUriEditText;
    private TextView mUriEncodeMessage;
    private Button mEnableButton;
    private String mCurrentUri;
    private Switch mUseShortenerSwitch;

    // Data
    private BitlyShortenerAsyncTask mCurrentShorteningTask;
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
        mUriEncodeMessage = (TextView) rootView.findViewById(R.id.uriEncodeMessage);
        mUseShortenerSwitch = (Switch) rootView.findViewById(R.id.shortenerSwitch);
        mUseShortenerSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onUriChanged(mCurrentUri, false);
            }
        });

        mUriEditText = (EditText) rootView.findViewById(R.id.uriEditText);

        mUriEditText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                onUriChanged(mUriEditText.getText().toString(), false);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });


        mEnableButton = (Button) rootView.findViewById(R.id.enableButton);
        mEnableButton.setEnabled(false);
        mEnableButton.setOnClickListener(new View.OnClickListener() {
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

        if (kPersistValues) {
            SharedPreferences preferences = getActivity().getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            String savedUri = preferences.getString(kPreferences_uri, "http://");
            mUriEditText.setText(savedUri);
            onUriChanged(savedUri, false);
            mUseShortenerSwitch.setChecked(preferences.getBoolean(kPreferences_shorten, true));
        }

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

        // Preserve values
        if (kPersistValues) {
            SharedPreferences settings = getActivity().getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(kPreferences_uri, mCurrentUri);
            editor.putBoolean(kPreferences_shorten, mUseShortenerSwitch.isChecked());
            editor.commit();
        }
    }

    private void onEnable(View view) {
        mListener.onEnable(mCurrentUri);

    }

    private void onUriChanged(String uri, boolean isShortened) {
        mCurrentUri = uri;

        int uriStatus = verifyUriBeaconUri(uri);
        mEnableButton.setEnabled(uriStatus == kUriVerify_Ok);

        if (isShortened && uriStatus != kUriVerify_Ok) {
            mUriEncodeMessage.setText("Shortening error");
        } else if (uriStatus == kUriVerify_InvalidUri) {
            mUriEncodeMessage.setText("");
        } else if (uriStatus == kUriVerify_UnknownPrefix) {
            mUriEncodeMessage.setText(getString(R.string.beacon_uribeacon_invalidprefix));
        } else if (uriStatus == kUriVerify_TooLong && !isShortened && isNetworkAvailable() && mUseShortenerSwitch.isChecked()) {
            mUriEncodeMessage.setText("Shortening...");
            if (mCurrentShorteningTask != null) {
                mCurrentShorteningTask.cancel(true);
            }
            mCurrentShorteningTask = new BitlyShortenerAsyncTask(this);
            mCurrentShorteningTask.execute(uri);
        } else if (uriStatus == kUriVerify_TooLong) {
            mUriEncodeMessage.setText("Uri too long");
        } else if (uriStatus == kUriVerify_Ok) {
            String message = isShortened ? "Shortened uri: " + uri : "Valid URI";
            mUriEncodeMessage.setText(message);

        }
    }


    // region Utils
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    // endregion


    /*
        Encodes a uri for URIBeacon
        Returns null encoding was correct, or error message
     */
    private final int kUriVerify_Ok = 0;
    private final int kUriVerify_TooLong = 1;
    private final int kUriVerify_InvalidUri = 2;
    private final int kUriVerify_UnknownPrefix = 3;

    private int verifyUriBeaconUri(String input) {

        String flags = "-00";
        String txpower = "-00";
        String prefix = "";
        int len = 6;  // Payload len counter, minus header1

        // Make sure we have a valid string
        if (input == null || input.length() < 1) {

            return kUriVerify_InvalidUri;
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

            return kUriVerify_UnknownPrefix;
        }

        // Get the total payload length so far (minus the header and the len byte)
        len += input.length();

        // Generate the full URI payload (minus the header and len byte)
        String uri = flags + txpower + prefix + BleUtils.stringToHex(input);

        // See if we can chop off the suffix in the Uri
        if (input.indexOf(".com/") > -1) {

            len -= 4;
        } else if (input.indexOf(".org/") > -1) {

            len -= 4;
        } else if (input.indexOf(".edu/") > -1) {

            len -= 4;
        } else if (input.indexOf(".net/") > -1) {

            len -= 4;
        } else if (input.indexOf(".info/") > -1) {

            len -= 5;
        } else if (input.indexOf(".biz/") > -1) {

            len -= 4;
        } else if (input.indexOf(".gov/") > -1) {
            len -= 4;
        } else if (input.indexOf(".com") > -1) {
            len -= 3;
        } else if (input.indexOf(".org") > -1) {
            len -= 3;
        } else if (input.indexOf(".edu") > -1) {
            len -= 3;
        } else if (input.indexOf(".net") > -1) {
            len -= 3;
        } else if (input.indexOf(".info") > -1) {
            len -= 4;
        } else if (input.indexOf(".biz") > -1) {
            len -= 3;
        } else if (input.indexOf(".gov") > -1) {
            len -= 3;
        }

        // Add header1 and the payload len byte to the len count
        len += 5;

        // Make sure we don't exceed the 27 byte advertising payload limit
        if (len > 27) {

            return kUriVerify_TooLong;
        }

        return kUriVerify_Ok;
    }

    @Override
    public void onUriShortened(String shortenedUri) {
        mCurrentShorteningTask = null;
        onUriChanged(shortenedUri, true);
    }

    public interface OnFragmentInteractionListener {
        public void onEnable(String encodedUri);

        public void onDisable();
    }

}
