package com.adafruit.bluefruit.le.connect.app.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.R;

import java.io.File;

public class ApplicationFilesFragmentDialog extends DialogFragment {
    // UI
    private String mMessage;
    private TextView mHexTextView;
    private TextView mIniTextView;
    private AlertDialog mDialog;
    private int mFileType;

    // Data
    public interface ApplicationFilesDialogListener {
        public void onApplicationFilesDialogDoneClick();

        public void onApplicationFilesDialogCancelClick();
    }

    ApplicationFilesDialogListener mListener;
    Uri mHexUri, mIniUri;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (ApplicationFilesDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString() + " must implement ApplicationFilesDialogListener");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setRetainInstance(true);

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View contentView = inflater.inflate(R.layout.layout_application_files_dialog, null);
        mHexTextView = (TextView) contentView.findViewById(R.id.hexFileTextView);
        mIniTextView = (TextView) contentView.findViewById(R.id.iniFileTextView);

        mMessage = getArguments().getString("message");
        mFileType = getArguments().getInt("fileType");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(contentView);
        builder.setMessage(mMessage)
                .setPositiveButton(R.string.firmware_customfile_dialog_done, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (getHexUri() == null) {
                            Toast.makeText(getActivity(), R.string.firmware_customfile_hexundefined, Toast.LENGTH_LONG).show();
                        }

                        mListener.onApplicationFilesDialogDoneClick();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mListener.onApplicationFilesDialogCancelClick();
                    }
                });
        mDialog = builder.create();

        updateUI();

        return mDialog;
    }

    @Override
    public void onDestroyView() {
        Dialog dialog = getDialog();

        // Work around bug: http://code.google.com/p/android/issues/detail?id=17423
        if ((dialog != null) && getRetainInstance())
            dialog.setDismissMessage(null);

        super.onDestroyView();
    }

    private String filenameFromUri(Uri uri) {
        String name = "";

        if (uri != null) {
            File file = new File(uri.getPath());
            name = file.getName();
        }
        return name;
    }

    public void setHexFilename(Uri uri) {
        mHexUri = uri;
        updateUI();
    }

    public void setIniFilename(Uri uri) {
        mIniUri = uri;
        updateUI();
    }

    private void updateUI() {
        String hexName = filenameFromUri(mHexUri);
        mHexTextView.setText(hexName);
        String iniName = filenameFromUri(mIniUri);
        mIniTextView.setText(iniName);

    }

    public Uri getHexUri() {
        return mHexUri;
    }

    public Uri getIniUri() {
        return mIniUri;
    }

    public int getFileType() {
        return mFileType;
    }
}
