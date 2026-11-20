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
import com.google.android.material.color.DynamicColors;

public class MaterialYouManager {
    private static final String TAG = "KGPT_MaterialYouManager";
    private static volatile MaterialYouManager instance;
    private final SharedPreferences prefs;

    public static final int DEFAULT_SEED_COLOR = 0xFF4285F4;

    private static final String PREF_NAME = "material_you_prefs";
    private static final String KEY_USE_WALLPAPER = "use_wallpaper";
    private static final String KEY_SEED_COLOR = "seed_color";
    private static final String KEY_ENABLED = "enabled";

    private static final int[] SEED_OVERLAYS_DARK = {
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedRed_Dark,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedOrange_Dark,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedAmber_Dark,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedYellow_Dark,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedLime_Dark,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedGreen_Dark,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedTeal_Dark,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedCyan_Dark,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedBlue_Dark,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedIndigo_Dark,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedPurple_Dark,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedPink_Dark
    };

    private static final int[] SEED_OVERLAYS_LIGHT = {
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedRed_Light,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedOrange_Light,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedAmber_Light,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedYellow_Light,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedLime_Light,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedGreen_Light,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedTeal_Light,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedCyan_Light,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedBlue_Light,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedIndigo_Light,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedPurple_Light,
        tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_SeedPink_Light
    };

    private static boolean isNight(android.content.Context c) {
        int mask = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static int nearestSeedIndex(int color) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        return Math.round(hsv[0] / 30f) % 12;
    }

    private static int nearestSeedOverlay(int color, boolean night) {
        int i = nearestSeedIndex(color);
        return (night ? SEED_OVERLAYS_DARK : SEED_OVERLAYS_LIGHT)[i];
    }

    private MaterialYouManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static MaterialYouManager getInstance(Context context) {
        if (instance == null) {
            synchronized (MaterialYouManager.class) {
                if (instance == null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
                            if (userManager != null && !userManager.isUserUnlocked()) {
                                Log.w(TAG, "Device is locked (Direct Boot), deferring MaterialYouManager initialization");
                                return null;
                            }
                        }
                        instance = new MaterialYouManager(context.getApplicationContext());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to initialize MaterialYouManager", e);
                        return null;
                    }
                }
            }
        }
        return instance;
    }

    public Context wrapContext(Context baseContext) {
        Context context = baseContext;
        if (isMaterialYouEnabled()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (isUseWallpaperSource()) {
                    context = DynamicColors.wrapContextIfAvailable(baseContext);
                } else {
                    // Use custom seed color for context wrapping
                    try {
                        com.google.android.material.color.DynamicColorsOptions options = new com.google.android.material.color.DynamicColorsOptions.Builder()
                                .setContentBasedSource(getSeedColor())
                                .build();
                        context = DynamicColors.wrapContextIfAvailable(baseContext, options);
                    } catch (NoClassDefFoundError | NoSuchMethodError e) {
                        // Fallback for older versions
                        context = DynamicColors.wrapContextIfAvailable(baseContext);
                    }
                }
            } else {
                // API < 31 fallback: wrap context with nearest seed overlay if custom seed is used
                if (!isUseWallpaperSource()) {
                    context = new android.view.ContextThemeWrapper(baseContext,
                            nearestSeedOverlay(getSeedColor(), isNight(baseContext)));
                }
            }
        }

        // Apply AMOLED overlay if needed
        SharedPreferences appPrefs = baseContext.getSharedPreferences("keyboard_gpt_ui", Context.MODE_PRIVATE);
        boolean isAmoled = appPrefs.getBoolean("amoled_mode", false);

        int uiMode = baseContext.getResources().getConfiguration().uiMode;
        boolean isNightModeEnv = (uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (isNightModeEnv && isAmoled) {
            context = new android.view.ContextThemeWrapper(context, tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_AMOLED);
        }

        return context;
    }

    public int getSeedColor() {
        if (prefs == null) return DEFAULT_SEED_COLOR;
        return prefs.getInt(KEY_SEED_COLOR, DEFAULT_SEED_COLOR);
    }

    public void setSeedColor(int color) {
        if (prefs != null) {
            prefs.edit().putInt(KEY_SEED_COLOR, color).apply();
        }
    }

    public boolean isUseWallpaperSource() {
        if (prefs == null) return true;
        return prefs.getBoolean(KEY_USE_WALLPAPER, true);
    }

    public void setUseWallpaperSource(boolean use) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_USE_WALLPAPER, use).apply();
        }
    }

    public boolean isMaterialYouEnabled() {
        if (prefs == null) return true;
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    public void applyTheme(android.app.Activity activity) {
        if (isMaterialYouEnabled()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (isUseWallpaperSource()) {
                    DynamicColors.applyToActivityIfAvailable(activity);
                } else {
                    try {
                        com.google.android.material.color.DynamicColorsOptions options = new com.google.android.material.color.DynamicColorsOptions.Builder()
                                .setContentBasedSource(getSeedColor())
                                .build();
                        DynamicColors.applyToActivityIfAvailable(activity, options);
                    } catch (NoClassDefFoundError | NoSuchMethodError e) {
                        DynamicColors.applyToActivityIfAvailable(activity);
                    }
                }
            } else {
                // API < 31 fallback: apply nearest seed overlay if custom seed is used
                if (!isUseWallpaperSource()) {
                    activity.getTheme().applyStyle(
                            nearestSeedOverlay(getSeedColor(), isNight(activity)), true);
                }
            }
        }

        // Apply AMOLED overlay if needed (Overrides background colors to black)
        // This is done AFTER DynamicColors so it takes precedence for background
        // surfaces
        checkAndApplyAmoledOverlay(activity);
    }

    private void checkAndApplyAmoledOverlay(android.app.Activity activity) {
        SharedPreferences appPrefs = activity.getSharedPreferences("keyboard_gpt_ui", Context.MODE_PRIVATE);
        boolean isAmoled = appPrefs.getBoolean("amoled_mode", false);

        // Check if Night Mode is actually active in the resources configuration
        // This handles explicit preference AND system default cases correctly.
        int uiMode = activity.getResources().getConfiguration().uiMode;
        boolean isNightModeEnv = (uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (isNightModeEnv && isAmoled) {
            activity.getTheme().applyStyle(tn.eluea.kgpt.R.style.ThemeOverlay_KGPT_AMOLED, true);
        }
    }

    public void setMaterialYouEnabled(boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
        }
    }
}
