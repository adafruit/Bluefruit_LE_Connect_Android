package com.adafruit.bluefruit.le.connect.ui.utils;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;

// http://stackoverflow.com/questions/4605527/converting-pixels-to-dp
public class MetricsUtils {

    public static float convertPixelsToDp(final Context context, final float px) {
        return px / context.getResources().getDisplayMetrics().density;
    }

    public static float convertDpToPixel(final Context context, final float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}
