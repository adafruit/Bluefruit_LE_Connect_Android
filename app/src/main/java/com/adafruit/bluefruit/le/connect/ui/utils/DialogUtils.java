package com.adafruit.bluefruit.le.connect.ui.utils;

import android.app.Dialog;
import android.view.WindowManager;

public class DialogUtils {

    // Prevent dialog dismiss when orientation changes
    // http://stackoverflow.com/questions/7557265/prevent-dialog-dismissal-on-screen-rotation-in-android
    public static void keepDialogOnOrientationChanges(Dialog dialog){
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(lp);
    }
}
