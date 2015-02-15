package com.adafruit.bluefruit.le.connect.app.shortener;

import android.os.AsyncTask;

public class ShortenerAsyncTask extends AsyncTask<String, Void, String> {

    public interface ShortenerListener {
        void onUriShortened(String shortenedUri);
    }

    private ShortenerListener mListener;

    public ShortenerAsyncTask(ShortenerListener listener) {
        mListener = listener;
    }

    @Override
    protected String doInBackground(String... urls) {

        // Default implementation: no shortening
        String originalUrl = urls[0];

        return originalUrl;
    }

    protected void onPostExecute(String result) {
        if (mListener != null) {
            mListener.onUriShortened(result);
        }
    }
}
