package com.adafruit.bluefruit.le.connect.app.update;


import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;

public class ProgressFragmentDialog extends DialogFragment {
    private ProgressDialog mDialog;
    private DialogInterface.OnCancelListener mCancelListener;

    private String mMessage;
    private int mProgress;
    private boolean mIndeterminate;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setRetainInstance(true);

        mMessage = getArguments().getString("message");

        mDialog = new ProgressDialog(getActivity());
        mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDialog.setMax(100);
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.setCancelable(true);

        updateUI();

        return mDialog;
    }

    @Override
    public void onDestroyView()
    {
        Dialog dialog = getDialog();

        // Work around bug: http://code.google.com/p/android/issues/detail?id=17423
        if ((dialog != null) && getRetainInstance())
            dialog.setDismissMessage(null);

        super.onDestroyView();
    }

    @Override
    public void onCancel(DialogInterface dialog) {          // to avoid problems with setting oncancellistener after dialog has been created
        if (mCancelListener != null) {
            mCancelListener.onCancel(dialog);
        }

        super.onCancel(dialog);
    }


    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        mCancelListener = listener;
    }

    /*
    public ProgressDialog getDialog() {
        return mDialog;
    }
    */

    public void setMessage(String message) {
        mMessage = message;
        mDialog.setMessage(message);
    }

    public void setProgress(int progress) {
        mProgress = progress;
        mDialog.setProgress(progress);
    }

    public void setIndeterminate(boolean indeterminate) {
        mIndeterminate = indeterminate;
        mDialog.setIndeterminate(indeterminate);
    }

    private void updateUI() {
        mDialog.setMessage(mMessage);
        mDialog.setProgress(mProgress);
        mDialog.setIndeterminate(mIndeterminate);
    }


}
