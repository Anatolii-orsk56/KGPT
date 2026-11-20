package tn.eluea.kgpt.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.UserManager;
import java.util.Locale;

public class LocaleHelper {

    private static final String PREF_NAME = "keyboard_gpt_ui";
    private static final String PREF_LANGUAGE = "app_language";

    public static Context onAttach(Context context) {
        String lang = getPersistedData(context, Locale.getDefault().getLanguage());
        return setLocale(context, lang);
    }

    public static Context onAttach(Context context, String defaultLanguage) {
        String lang = getPersistedData(context, defaultLanguage);
        return setLocale(context, lang);
    }

    public static String getLanguage(Context context) {
        return getPersistedData(context, Locale.getDefault().getLanguage());
    }

    public static Context setLocale(Context context, String language) {
        // E8: only persist on actual change (was writing on every attach)
        String current = getPersistedData(context, Locale.getDefault().getLanguage());
        if (!language.equals(current)) {
            persist(context, language);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return updateResources(context, language);
        }

        return updateResourcesLegacy(context, language);
    }

    private static String getPersistedData(Context context, String defaultLanguage) {
        try {
            if (tn.eluea.kgpt.SPManager.isReady()) {
                String lang = tn.eluea.kgpt.SPManager.getInstance().getAppLanguage();
                if (lang != null) return lang;
            }
            tn.eluea.kgpt.provider.ConfigClient client = new tn.eluea.kgpt.provider.ConfigClient(context);
            String lang = client.getString(PREF_LANGUAGE, null);
            client.destroy();
            if (lang != null) return lang;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
                if (userManager != null && !userManager.isUserUnlocked()) {
                    return defaultLanguage;
                }
            }
            SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            return preferences.getString(PREF_LANGUAGE, defaultLanguage);
        } catch (Exception e) {
            return defaultLanguage;
        }
    }

    private static void persist(Context context, String language) {
        try {
            if (tn.eluea.kgpt.SPManager.isReady()) {
                tn.eluea.kgpt.SPManager.getInstance().setAppLanguage(language);
                return;
            }
            tn.eluea.kgpt.provider.ConfigClient client = new tn.eluea.kgpt.provider.ConfigClient(context);
            client.putString(PREF_LANGUAGE, language);
            client.destroy();
        } catch (Exception ignored) {
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);

        return context.createConfigurationContext(configuration);
    }

    @SuppressWarnings("deprecation")
    private static Context updateResourcesLegacy(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();

        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLayoutDirection(locale);
        }

        resources.updateConfiguration(configuration, resources.getDisplayMetrics());

        return context;
    }
}
