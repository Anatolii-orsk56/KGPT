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
import java.util.regex.Pattern;

/**
 * Modern Config Reader with dynamic XSharedPreferences reflection fallback.
 */
public class XposedConfigReader {
    private static final String TAG = "KGPT_XposedConfig";
    private static final String KGPT_PACKAGE = "tn.eluea.kgpt";
    private static final String PREF_NAME = "keyboard_gpt";

    // Каталог LSPosed меняется от устройства к устройству: UUID генерируется
    // при установке LSPosed и общий для всех модулей (/data/misc/<UUID>/prefs/<package>/).
    // Поэтому путь ищем динамически, а старый захардкоженный оставляем как fallback.
    private static final String LEGACY_MIRROR_DIR =
        "/data/misc/1c91858f-5536-4f19-a481-80701e1e7a8d/prefs/" + KGPT_PACKAGE;

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static Object xPrefs = null;
    private static boolean prefsAvailable = false;
    private static boolean initialized = false;
    private static final Map<String, Object> cache = new HashMap<>();
    private static long lastReload = 0;
    private static final long RELOAD_INTERVAL = 1000;

    private static volatile File cachedMirrorDir;
    private static boolean mirrorDirResolved = false;

    private static Method reloadMethod;
    private static Method getStringMethod;
    private static Method getBooleanMethod;
    private static Method getIntMethod;
    private static Method getFileMethod;

    /**
     * Находит зеркальный каталог префов LSPosed для KGPT.
     * Каталог появляется только после активации модуля в LSPosed Manager и
     * перезагрузки, поэтому до этого момента вернёт fallback-путь или null.
     */
    private static synchronized File getMirrorDir() {
        if (mirrorDirResolved && cachedMirrorDir != null) return cachedMirrorDir;

        String[] roots = { "/data/misc/apexdata", "/data/misc" };
        for (String rootPath : roots) {
            File root = new File(rootPath);
            String[] entries = root.list();
            if (entries == null) continue; // нет доступа к корню — пробуем следующий

            for (String name : entries) {
                if (!UUID_PATTERN.matcher(name).matches()) continue;
                File prefFile = new File(root,
                        name + "/prefs/" + KGPT_PACKAGE + "/" + PREF_NAME + ".xml");
                if (prefFile.isFile()) {
                    cachedMirrorDir = new File(root, name + "/prefs/" + KGPT_PACKAGE);
                    mirrorDirResolved = true;
                    dbg("mirror dir resolved: " + cachedMirrorDir.getAbsolutePath());
                    return cachedMirrorDir;
                }
            }
        }
        // Не нашли — на своём устройстве поможет legacy-путь, на чужом вернём null
        File legacy = new File(LEGACY_MIRROR_DIR);
        File legacyFile = new File(legacy, PREF_NAME + ".xml");
        if (legacyFile.isFile()) {
            cachedMirrorDir = legacy;
            mirrorDirResolved = true;
            dbg("mirror dir resolved via legacy path");
            return legacy;
        }
        dbg("mirror dir not found yet (module not activated or no LSPosed)");
        return null;
    }

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

    // Direct file read: только если XSP-канал не заработал
    if (!prefsAvailable) {
        try {
            File dir = getMirrorDir();
            File f = (dir != null) ? new File(dir, PREF_NAME + ".xml") : null;
            dbg("fallback file=" + (f != null ? f.getAbsolutePath() : "null")
                    + " canRead=" + (f != null && f.canRead()));
            if (f != null && f.canRead()) {
                parseXmlIntoCache(new String(java.nio.file.Files.readAllBytes(f.toPath())));
                prefsAvailable = true;
                dbg("fallback OK, keys=" + cache.size());
            }
        } catch (Throwable e) {
            dbg("fallback FAILED: " + e);
        }
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
            File dir = getMirrorDir();
            File f = (dir != null) ? new File(dir, PREF_NAME + ".xml") : null;
            if (f != null && f.canRead()) {
                parseXmlIntoCache(new String(
                        java.nio.file.Files.readAllBytes(f.toPath())));
                dbg("fallback reloaded, keys=" + cache.size());
            } else {
                dbg("fallback reload: file not readable: "
                        + (f != null ? f.getAbsolutePath() : "null"));
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
    // Лог только в logcat: никаких записей в чужие приложения на /sdcard
    Log.d("KGPT-XSP", s);
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
