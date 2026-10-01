package com.rdevzph.fpsmeter.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Lightweight per-app language helper that does not depend on AppCompat.
 *
 * The selected language is stored in SharedPreferences and applied by wrapping the
 * base context (see [wrap]) inside every Activity/Service via `attachBaseContext`.
 * "Follow System" simply returns the untouched base context so the OS locale wins.
 */
object LocaleHelper {

    const val LANG_SYSTEM = "system"
    const val LANG_ENGLISH = "en"
    const val LANG_CHINESE = "zh"

    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    /**
     * Returns a context whose resources use the user-selected language, or the
     * original context when following the system.
     */
    fun wrap(base: Context): Context {
        val lang = getLanguage(base)
        val locale = when (lang) {
            LANG_CHINESE -> Locale.SIMPLIFIED_CHINESE
            LANG_ENGLISH -> Locale.ENGLISH
            else -> return base
        }

        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            config.setLayoutDirection(locale)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        @Suppress("DEPRECATION")
        return base.createConfigurationContext(config)
    }
}
