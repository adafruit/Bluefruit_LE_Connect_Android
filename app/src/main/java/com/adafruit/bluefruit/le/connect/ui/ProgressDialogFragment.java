// Original source: http://blog.callumtaylor.net/handlingasynctasksanddialogfragmentswithorientationchangingjava-android

package com.adafruit.bluefruit.le.connect.ui;


import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;

public class ProgressDialogFragment extends DialogFragment
{
    private String dialogTag;
    private DialogInterface.OnCancelListener cancelListener;

    /**
     * Builder for progress dialog fragment
     **/
    public static class Builder
    {
        private String title;
        private String message;
        private boolean cancelableOnTouchOutside = true;

        public ProgressDialogFragment.Builder setTitle(String title)
        {
            this.title = title;
            return this;
        }

        public ProgressDialogFragment.Builder setMessage(String message)
        {
            this.message = message;
            return this;
        }

        public ProgressDialogFragment.Builder setCancelableOnTouchOutside(boolean cancelable)
        {
            this.cancelableOnTouchOutside = cancelable;
            return this;
        }

        public ProgressDialogFragment build()
        {
            return ProgressDialogFragment.newInstance(title, message, cancelableOnTouchOutside);
        }
    }

    protected static ProgressDialogFragment newInstance()
    {
        return newInstance("", "", true);
    }

    protected static ProgressDialogFragment newInstance(String title, String message, boolean cancelableOnTouchOutside)
    {
        ProgressDialogFragment frag = new ProgressDialogFragment();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("message", message);
        args.putBoolean("cancelableOnTouchOutside", cancelableOnTouchOutside);
        frag.setArguments(args);
        return frag;
    }

    @Override public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
    }

    @Override public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        ProgressDialog dialog = new ProgressDialog(getActivity());

        String title = getArguments().getString("title");
        String message = getArguments().getString("message");
        dialog.setCanceledOnTouchOutside(getArguments().getBoolean("cancelableOnTouchOutside"));

        if (!TextUtils.isEmpty(title))
        {
            dialog.setTitle(title);
        }

        if (!TextUtils.isEmpty(message))
        {
            dialog.setMessage(message);
        }

        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setMax(100);
        return dialog;
    }

    /**
     * Sets the progress of the dialog, we need to make sure we get the right dialog reference here
     * which is why we obtain the dialog fragment manually from the fragment manager
     * @param manager
     * @param progress
     */
    public void setProgress(FragmentManager manager, int progress)
    {
        ProgressDialogFragment dialog = (ProgressDialogFragment)manager.findFragmentByTag(dialogTag);
        if (dialog != null)
        {
            ((ProgressDialog)dialog.getDialog()).setProgress(progress);
        }
    }

    /**
     * Dismisses the dialog from the fragment manager. We need to make sure we get the right dialog reference
     * here which is why we obtain the dialog fragment manually from the fragment manager
     * @param manager
     */
    public void dismiss(FragmentManager manager)
    {
        ProgressDialogFragment dialog = (ProgressDialogFragment)manager.findFragmentByTag(dialogTag);
        if (dialog != null)
        {
            dialog.dismiss();
        }
    }

    @Override public void show(FragmentManager manager, String tag)
    {
        dialogTag = tag;
        super.show(manager, dialogTag);
    }

    public void setIndeterminate(FragmentManager manager, boolean indeterminate) {


        ProgressDialogFragment dialog = (ProgressDialogFragment)manager.findFragmentByTag(dialogTag);
        if (dialog != null)
        {
            ((ProgressDialog)dialog.getDialog()).setIndeterminate(indeterminate);
        }
    }

    @Override
    public void onCancel(DialogInterface dialog)
    {
        if (cancelListener != null) {
            cancelListener.onCancel(dialog);
        }

        super.onCancel(dialog);


    }

    @Override
    public void onDismiss(DialogInterface dialog)
    {

        super.onDismiss(dialog);


    }

    public void setOnCancelListener(FragmentManager manager, DialogInterface.OnCancelListener listener) {
        ProgressDialogFragment dialog = (ProgressDialogFragment)manager.findFragmentByTag(dialogTag);
        if (dialog != null)
        {
            //ProgressDialog progressDialog = (ProgressDialog)dialog.getDialog();
            //progressDialog.setOnCancelListener(listener);
            cancelListener = listener;
        }
    }

    public void setMessage(FragmentManager manager, String text) {
        ProgressDialogFragment dialog = (ProgressDialogFragment)manager.findFragmentByTag(dialogTag);
        if (dialog != null)
        {
            ((ProgressDialog)dialog.getDialog()).setMessage(text);
        }
    }
}