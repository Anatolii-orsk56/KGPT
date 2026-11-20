/*
 * Copyright (c) 2025 Amr Aldeeb @Eluea
 * GitHub: https://github.com/Eluea
 * Telegram: https://t.me/Eluea
 *
 * Licensed under the GPLv3.
 */
package tn.eluea.kgpt.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

/**
 * Utility class for safely accessing SharedPreferences during Direct Boot (locked state)
 * without triggering IllegalStateException on Android 7.0+ (API 24+).
 */
public class SafePrefs {
    private static final String TAG = "KGPT_SafePrefs";

    /**
     * Safely gets SharedPreferences. Returns null if device is locked on API 24+ or if an exception occurs.
     * 
     * @param context Application or component context
     * @param name    Preferences file name
     * @return SharedPreferences instance or null if inaccessible
     */
    public static SharedPreferences get(Context context, String name) {
        if (context == null) {
            return null;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
                if (userManager != null && !userManager.isUserUnlocked()) {
                    Log.w(TAG, "Device is locked (Direct Boot), SharedPreferences not available for: " + name);
                    return null;
                }
            }
            return context.getSharedPreferences(name, Context.MODE_PRIVATE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get SharedPreferences: " + name, e);
            return null;
        }
    }

    /**
     * Safely reads a String preference, returning defValue if prefs are null or exception occurs.
     * 
     * @param context  Application or component context
     * @param name     Preferences file name
     * @param key      Preference key
     * @param defValue Default value if key not found or storage unavailable
     * @return Preference string value or defValue
     */
    public static String getString(Context context, String name, String key, String defValue) {
        SharedPreferences prefs = get(context, name);
        if (prefs == null) {
            return defValue;
        }
        try {
            return prefs.getString(key, defValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read string key " + key + " from " + name, e);
            return defValue;
        }
    }
}
