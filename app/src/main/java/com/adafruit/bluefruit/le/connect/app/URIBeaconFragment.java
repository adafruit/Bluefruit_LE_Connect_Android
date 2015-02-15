package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;


public class URIBeaconFragment extends Fragment {
    // Log
    private final static String TAG = URIBeaconFragment.class.getSimpleName();
    private final static boolean kPersistValues = true;

    // Constants
    private final static String kPreferences = "URIBeaconFragment_prefs";
    private final static String kPreferences_uri = "uri";

    // Bitly
    private static final String kBitlyApiKey = "38bc9301550f6eeec36db33334701e3a551f580d";

    // UI
    private EditText mUriEditText;
    private TextView mUriEncodeMessage;
    private Button mEnableButton;
    private String mCurrentUri;

    // Data
    private BitlyAsyncTask mCurrentShorteningTask;
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
        //   mUriEncodeMessage = (TextView) rootView.findViewById(R.id.uriShortenedUrl);

        mUriEditText = (EditText) rootView.findViewById(R.id.uriEditText);

        mUriEditText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                onUriChanged(mUriEditText.getText().toString(), false);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {}
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

        // Preserver values
        if (kPersistValues) {
            SharedPreferences settings = getActivity().getSharedPreferences(kPreferences, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(kPreferences_uri, mCurrentUri);
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
        }
        else if (uriStatus == kUriVerify_InvalidUri) {
            mUriEncodeMessage.setText("");
        } else if (uriStatus == kUriVerify_UnknownPrefix) {
            mUriEncodeMessage.setText(getString(R.string.beacon_uribeacon_invalidprefix));
        } else if (uriStatus == kUriVerify_TooLong && !isShortened &&  isNetworkAvailable()) {
            mUriEncodeMessage.setText("Shortening...");
            if (mCurrentShorteningTask != null) {
                mCurrentShorteningTask.cancel(true);
            }

            mCurrentShorteningTask = new BitlyAsyncTask();
            mCurrentShorteningTask.execute(uri);

        } else if (uriStatus == kUriVerify_Ok) {
            String message = isShortened?"Shortened uri: "+uri:"Valid URI";
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



    private class BitlyAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected String doInBackground(String... urls) {
            try {
                String originalUrl = urls[0];
                String url = bitlyShorteningEndPoint(originalUrl);

                HttpGet httpGet = new HttpGet(url);
                HttpClient httpclient = new DefaultHttpClient();
                HttpResponse response = httpclient.execute(httpGet);

                final int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    HttpEntity entity = response.getEntity();
                    String data = EntityUtils.toString(entity);
                    return data;
                }


            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(String result) {
            mCurrentShorteningTask = null;
            onUriChanged(result, true);
        }
    }


    private String bitlyShorteningEndPoint(String uri) throws UnsupportedEncodingException {
        String encodedUri = URLEncoder.encode(uri, "UTF-8");
        return String.format("https://api-ssl.bitly.com/v3/shorten?access_token=%s&longUrl=%s&format=txt", kBitlyApiKey, encodedUri);
        //

    }

    /*
        Encodes a uri for URIBeacon
        Returns null encoding was correct, or error message
     */
    private final int kUriVerify_Ok = 0;
    private final int kUriVerify_TooLong = 1;
    private final int kUriVerify_InvalidUri = 2;
    private final int kUriVerify_UnknownPrefix = 3;

    private int verifyUriBeaconUri(String input) {

 //       final String header1 = "03-03-D8-FE"; // Complete 16-bit Service List Flag, UUID = 0xFED8
 //       final String header2 = "-16-D8-FE";   // Service Data AD Type and UUID again

        String flags = "-00";
        String txpower = "-00";
        String prefix = "";
        int len = 6;  // Payload len counter, minus header1

        // Make sure we have a valid string
        if (input == null ||input.length() < 1) {
         //   String errorMessage = "Invalid URI";
         //   Log.w(TAG, errorMessage);
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
//            String errorMessage = "Prefix must be one of the following values:\n\nhttp://www.\nhttps://www.\nhttp://\nhttps://\nurn:uuid:";
 //           Log.w(TAG, "invalid prefix");
            return kUriVerify_UnknownPrefix;
        }

        // Get the total payload length so far (minus the header and the len byte)
        len += input.length();

        // Generate the full URI payload (minus the header and len byte)
        String uri = flags + txpower + prefix + BleUtils.stringToHex(input);

        // See if we can chop off the suffix in the Uri
        if (input.indexOf(".com/") > -1) {
           // uri = uri.replace(BleUtils.stringToHex(".com/"), "-00");
            len -= 4;
        } else if (input.indexOf(".org/") > -1) {
           // uri = uri.replace(BleUtils.stringToHex(".org/"), "-01");
            len -= 4;
        } else if (input.indexOf(".edu/") > -1) {
           // uri = uri.replace(BleUtils.stringToHex(".edu/"), "-02");
            len -= 4;
        } else if (input.indexOf(".net/") > -1) {
         //   uri = uri.replace(BleUtils.stringToHex(".net/"), "-03");
            len -= 4;
        } else if (input.indexOf(".info/") > -1) {
          //  uri = uri.replace(BleUtils.stringToHex(".info/"), "-04");
            len -= 5;
        } else if (input.indexOf(".biz/") > -1) {
          //  uri = uri.replace(BleUtils.stringToHex(".biz/"), "-05");
            len -= 4;
        } else if (input.indexOf(".gov/") > -1) {
          //  uri = uri.replace(BleUtils.stringToHex(".gov/"), "-06");
            len -= 4;
        } else if (input.indexOf(".com") > -1) {
            uri = uri.replace(BleUtils.stringToHex(".com"), "-07");
            len -= 3;
        } else if (input.indexOf(".org") > -1) {
          //  uri = uri.replace(BleUtils.stringToHex(".org"), "-08");
            len -= 3;
        } else if (input.indexOf(".edu") > -1) {
          //  uri = uri.replace(BleUtils.stringToHex(".edu"), "-09");
            len -= 3;
        } else if (input.indexOf(".net") > -1) {
          //  uri = uri.replace(BleUtils.stringToHex(".net"), "-0A");
            len -= 3;
        } else if (input.indexOf(".info") > -1) {
          //  uri = uri.replace(BleUtils.stringToHex(".info"), "-0B");
            len -= 4;
        } else if (input.indexOf(".biz") > -1) {
          //  uri = uri.replace(BleUtils.stringToHex(".biz"), "-0C");
            len -= 3;
        } else if (input.indexOf(".gov") > -1) {
          //  uri = uri.replace(BleUtils.stringToHex(".gov"), "-0D");
            len -= 3;
        }

        /*
        // Generate the len string (zero-padding if necessary)
        String lenStr = "";
        if (len < 16) {
            lenStr = "-0" + Integer.toHexString(len).toUpperCase();
        } else {
            lenStr = "-" + Integer.toHexString(len).toUpperCase();
        }
        */

        // Add header1 and the payload len byte to the len count
        len += 5;

        // Make sure we don't exceed the 27 byte advertising payload limit
        if (len > 27) {
            /*
            String errorMessage = "Invalid payload length. The encoded UriBeacon payload must be <= 27 bytes (16 characters max for the custom URI segment).\n\n" +
                    "The current payload is " + Integer.toString(len) + " bytes long.";
*/
          //  Log.w(TAG, "Invalid prefix");
            return kUriVerify_TooLong;
        }

        // fully encoded UriBeacon payload
        //String result = header1 + lenStr + header2 + uri;
        return kUriVerify_Ok;
    }

    public interface OnFragmentInteractionListener {
        public void onEnable(String encodedUri);

        public void onDisable();
    }

}
