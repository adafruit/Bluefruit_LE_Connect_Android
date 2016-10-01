package com.adafruit.bluefruit.le.connect.app.update;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;


class DownloadTask extends AsyncTask<String, Integer, ByteArrayOutputStream> {
    // Constants
    private final static String TAG = DownloadTask.class.getSimpleName();

    // Data
    private Context mContext;
    private PowerManager.WakeLock mWakeLock;
    private DownloadTaskListener mListener;
    private int mOperationId;
    private String mUrlAddress;
    private Object mTag;

    DownloadTask(Context context, DownloadTaskListener listener, int operationId) {
        mContext = context;
        mListener = listener;
        mOperationId = operationId;
    }

    @Override
    protected ByteArrayOutputStream doInBackground(String... sUrl) {
        InputStream input = null;
        ByteArrayOutputStream output = null;
        HttpURLConnection connection = null;
        try {
            mUrlAddress = sUrl[0];

            int fileLength = 0;
            Uri uri = Uri.parse(sUrl[0]);
            String uriScheme = uri.getScheme();
            //Log.d(TAG, "Downloading from "+uriScheme);
            boolean shouldBeConsideredAsInputStream = (uriScheme.equalsIgnoreCase("file") || uriScheme.equalsIgnoreCase("content"));
            if (shouldBeConsideredAsInputStream) {
                input = mContext.getContentResolver().openInputStream(uri);
            } else {
                URL url = new URL(mUrlAddress);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return null;
                }

                // this will be useful to display download percentage  might be -1: server did not report the length
                fileLength = connection.getContentLength();

                // download the file
                input = connection.getInputStream();
            }
//          Log.d(TAG, "\tFile size: "+fileLength);

            // download the file
            output = new ByteArrayOutputStream();

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                // allow canceling
                if (isCancelled()) {
                    input.close();
                    return null;
                }
                total += count;

                // publishing the progress....
                if (fileLength > 0) {// only if total length is known
                    publishProgress((int) (total * 100 / fileLength));
                }
                output.write(data, 0, count);
            }

        } catch (Exception e) {
            Log.w(TAG, "Error DownloadTask " + e);
            return null;
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
                if (input != null) {
                    input.close();
                }
            } catch (IOException ignored) {
            }

            if (connection != null) {
                connection.disconnect();
            }
        }
        return output;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // take CPU lock to prevent CPU from going off if the user  presses the power button during download
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        mWakeLock.acquire();
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        super.onProgressUpdate(progress);

        mListener.onDownloadProgress(mOperationId, progress[0]);
    }

    @Override
    protected void onPostExecute(ByteArrayOutputStream result) {
        mWakeLock.release();

        mListener.onDownloadCompleted(mOperationId, mUrlAddress, result);
    }

    static interface DownloadTaskListener {
        void onDownloadProgress(int operationId, int progress);

        void onDownloadCompleted(int operationId, String url, ByteArrayOutputStream result);
    }

    public Object getTag() {
        return mTag;
    }

    public void setTag(Object tag) {
        this.mTag = tag;
    }
}