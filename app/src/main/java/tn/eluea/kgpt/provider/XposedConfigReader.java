/*
 * Copyright (c) 2025 Amr Aldeeb @Eluea
 * GitHub: https://github.com/Eluea
 * Telegram: https://t.me/Eluea
 *
 * Licensed under the GPLv3.
 */
package tn.eluea.kgpt.provider;

import android.util.Log;
import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Modern Config Reader with dynamic XSharedPreferences reflection fallback.
 */
public class XposedConfigReader {
    private static final String TAG = "KGPT_XposedConfig";
    private static final String KGPT_PACKAGE = "tn.eluea.kgpt";
    private static final String PREF_NAME = "keyboard_gpt";
    
    private static final String MIRROR_DIR =
        "/data/misc/1c91858f-5536-4f19-a481-80701e1e7a8d/prefs/" + KGPT_PACKAGE;
    
    private static final String DBG_PATH =
        "/sdcard/Android/data/org.futo.inputmethod.latin/files/kgpt_debug.log";

    private static Object xPrefs = null;
    private static boolean prefsAvailable = false;
    private static boolean initialized = false;
    private static final Map<String, Object> cache = new HashMap<>();
    private static long lastReload = 0;
    private static final long RELOAD_INTERVAL = 1000;

    private static Method reloadMethod;
    private static Method getStringMethod;
    private static Method getBooleanMethod;
    private static Method getIntMethod;
    private static Method getFileMethod;
    

private static synchronized void initPrefs() {
    if (initialized) {
        return;
    }
    initialized = true;

    try {
 Class<?> xPrefsClass = Class.forName("de.robv.android.xposed.XSharedPreferences");
reloadMethod = xPrefsClass.getMethod("reload");
getStringMethod = xPrefsClass.getMethod("getString", String.class, String.class);
getBooleanMethod = xPrefsClass.getMethod("getBoolean", String.class, boolean.class);
getIntMethod = xPrefsClass.getMethod("getInt", String.class, int.class);
getFileMethod = xPrefsClass.getMethod("getFile");

        xPrefs = xPrefsClass.getConstructor(String.class, String.class)
                .newInstance(KGPT_PACKAGE, PREF_NAME);

        File file = (File) getFileMethod.invoke(xPrefs);
        dbg("XSP file=" + (file != null ? file.getAbsolutePath() : "null")
        + " canRead=" + (file != null && file.canRead()));

        if (file != null && file.canRead()) {
            prefsAvailable = true;
            reloadMethod.invoke(xPrefs);
            Log.d(TAG, "XSP initialized OK");
        } else {
            prefsAvailable = false;
            dbg("XSP file not readable, prefsAvailable=false");
        }
    } catch (Throwable e) {
    prefsAvailable = false;
    dbg("XSP init FAILED: " + e + " — switching to direct file read");
    }
    try {
    File f = new File(MIRROR_DIR, PREF_NAME + ".xml");
    dbg("fallback file=" + f.getAbsolutePath() + " canRead=" + f.canRead());
    if (f.canRead()) {
        parseXmlIntoCache(new String(java.nio.file.Files.readAllBytes(f.toPath())));
        prefsAvailable = true;
        dbg("fallback OK, keys=" + cache.size());
    }
} catch (Throwable e) {
    dbg("fallback FAILED: " + e);
}
}
    
private static void reloadIfNeeded() {
    if (!prefsAvailable) return;

    long now = System.currentTimeMillis();
    if (now - lastReload <= RELOAD_INTERVAL) return;

    if (xPrefs != null && reloadMethod != null) {
        // штатный XSP-канал
        try {
            reloadMethod.invoke(xPrefs);
            if (getFileMethod != null) {
                File file = (File) getFileMethod.invoke(xPrefs);
                prefsAvailable = file != null && file.canRead();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to reload: " + e.getMessage());
        }
    } else {
        // fallback: перечитываем зеркальный XML напрямую
        try {
            File f = new File(MIRROR_DIR, PREF_NAME + ".xml");
            if (f.canRead()) {
                parseXmlIntoCache(new String(
                        java.nio.file.Files.readAllBytes(f.toPath())));
                dbg("fallback reloaded, keys=" + cache.size());
            } else {
                dbg("fallback reload: file not readable: " + f.getAbsolutePath());
            }
        } catch (Throwable e) {
            dbg("fallback reload FAILED: " + e);
        }
    }
    if (xPrefs != null && reloadMethod != null) {
        cache.clear();
    }
    lastReload = now;

}
    
    private static void parseXmlIntoCache(String xml) {
    cache.clear();
    java.util.regex.Matcher mi = java.util.regex.Pattern
            .compile("<int name=\"([^\"]+)\" value=\"(-?\\d+)\"").matcher(xml);
    while (mi.find()) cache.put(mi.group(1), Integer.valueOf(mi.group(2)));
    java.util.regex.Matcher mb = java.util.regex.Pattern
            .compile("<boolean name=\"([^\"]+)\" value=\"(true|false)\"").matcher(xml);
    while (mb.find()) cache.put(mb.group(1), Boolean.valueOf(mb.group(2)));
    java.util.regex.Matcher ms = java.util.regex.Pattern
            .compile("<string name=\"([^\"]+)\">([^<]*)</string>").matcher(xml);
    while (ms.find()) cache.put(ms.group(1), ms.group(2));
}
    
    private static void dbg(String s) {
    try {
        File f = new File(DBG_PATH);
        f.getParentFile().mkdirs();
        try (java.io.FileWriter w = new java.io.FileWriter(f, true)) {
            w.append(new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(new java.util.Date()))
             .append(" XSP: ").append(s).append("\n");
        }
    } catch (Throwable t) { /* игнорируем: лог не должен ломать хук */ }
}
    
    public static void forceReload() {
        if (xPrefs == null) {
            initPrefs();
            return;
        }
        try {
            if (reloadMethod != null) {
                reloadMethod.invoke(xPrefs);
            }
            cache.clear();
            lastReload = System.currentTimeMillis();
        } catch (Exception e) {
            Log.e(TAG, "Force reload failed: " + e.getMessage());
        }
    }

    public static String getString(String key, String defaultValue) {
        initPrefs();
        if (!prefsAvailable || xPrefs == null || getStringMethod == null) {
            return defaultValue;
        }
        reloadIfNeeded();
        if (cache.containsKey(key)) {
            Object value = cache.get(key);
            return value != null ? value.toString() : defaultValue;
        }
        try {
            String value = (String) getStringMethod.invoke(xPrefs, key, null);
            if (value != null) {
                cache.put(key, value);
                return value;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read string: " + key, e);
        }
        return defaultValue;
    }
    
    public static boolean getBoolean(String key, boolean defaultValue) {
        initPrefs();
        if (!prefsAvailable || xPrefs == null || getBooleanMethod == null) {
            return defaultValue;
        }
        reloadIfNeeded();
        if (cache.containsKey(key)) {
            Object value = cache.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        }
        try {
            Boolean value = (Boolean) getBooleanMethod.invoke(xPrefs, key, defaultValue);
            if (value != null) {
                cache.put(key, value);
                return value;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read boolean: " + key, e);
        }
        return defaultValue;
    }
    
public static int getInt(String key, int defaultValue) {
    initPrefs();
    if (!prefsAvailable) {
        dbg("getInt(" + key + ") -> EARLY RETURN, avail=false, returning default=" + defaultValue);
        return defaultValue;
    }
    reloadIfNeeded();
    if (cache.containsKey(key)) {
        Object value = cache.get(key);
        if (value instanceof Number) {
            dbg("getInt(" + key + ") from cache=" + value);
            return ((Number) value).intValue();
        }
    }
    if (xPrefs != null && getIntMethod != null) {
        // штатный XSP-канал
        try {
            Integer value = (Integer) getIntMethod.invoke(xPrefs, key, defaultValue);
            if (value != null) {
                cache.put(key, value);
                dbg("getInt(" + key + ") from XSP=" + value);
                return value;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read int: " + key, e);
            dbg("getInt(" + key + ") EXCEPTION=" + e);
        }
        dbg("getInt(" + key + ") -> FALLTHROUGH, returning default=" + defaultValue);
        return defaultValue;
    }
    // fallback-режим: кеш не содержит ключ (например, тип не int) — отдать дефолт
    dbg("getInt(" + key + ") fallback miss, returning default=" + defaultValue);
    return defaultValue;
}


    
    public static synchronized boolean isAvailable() {
        initPrefs();
        return prefsAvailable;
    }

    public static String getDebugInfo() {
        initPrefs();
        return "XSharedPreferences available: " + prefsAvailable;
    }
    
    public static void clearCache() {
        cache.clear();
        lastReload = 0;
    }
}
