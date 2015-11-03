package com.android2.calculator3;

import android.content.Context;
import android.preference.PreferenceManager;

public class CalculatorSettings {
    static void setRadiansEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("USE_RADIANS", enabled).commit();
    }

    static boolean useRadians(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("USE_RADIANS", true);
    }

    public static boolean showWidgetBackground(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("SHOW_WIDGET_BACKGROUND", false);
    }
}
