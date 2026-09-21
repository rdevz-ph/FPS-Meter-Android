package com.rdevzph.fpsmeter.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode(val title: String) {
    SYSTEM("Follow System"),
    DARK("Dark"),
    LIGHT("Light")
}

data class AppThemeSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val amoledDark: Boolean = false,
    val dynamicColor: Boolean = true
) {
    companion object {
        private const val PREFS_NAME = "app_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AMOLED_DARK = "amoled_dark"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"

        fun load(context: Context): AppThemeSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val modeName = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
            val mode = try { AppThemeMode.valueOf(modeName) } catch (e: Exception) { AppThemeMode.SYSTEM }
            val amoled = prefs.getBoolean(KEY_AMOLED_DARK, false)
            val dynamic = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
            return AppThemeSettings(mode, amoled, dynamic)
        }

        fun save(context: Context, settings: AppThemeSettings) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                putString(KEY_THEME_MODE, settings.themeMode.name)
                putBoolean(KEY_AMOLED_DARK, settings.amoledDark)
                putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
                apply()
            }
        }
    }
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80FF72),
    onPrimary = Color(0xFF003A00),
    primaryContainer = Color(0xFF005200),
    onPrimaryContainer = Color(0xFF9BFF8A),
    secondary = Color(0xFFB5CCAB),
    onSecondary = Color(0xFF213420),
    secondaryContainer = Color(0xFF374B35),
    onSecondaryContainer = Color(0xFFD1E8C6),
    background = Color(0xFF0E1410),
    onBackground = Color(0xFFDDE5D8),
    surface = Color(0xFF141A14),
    onSurface = Color(0xFFDDE5D8),
    surfaceVariant = Color(0xFF3D4B3C),
    onSurfaceVariant = Color(0xFFBDCAB9),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AmoledDarkColors = darkColorScheme(
    primary = Color(0xFF80FF72),
    onPrimary = Color(0xFF003A00),
    primaryContainer = Color(0xFF004400),
    onPrimaryContainer = Color(0xFF9BFF8A),
    secondary = Color(0xFFB5CCAB),
    onSecondary = Color(0xFF213420),
    secondaryContainer = Color(0xFF202620),
    onSecondaryContainer = Color(0xFFD1E8C6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEEEEEE),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFC0C8BF),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6E17),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9BFF8A),
    onPrimaryContainer = Color(0xFF002200),
    secondary = Color(0xFF4E6B4C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8C6),
    onSecondaryContainer = Color(0xFF0B210D),
    background = Color(0xFFF6FBF1),
    onBackground = Color(0xFF181D17),
    surface = Color(0xFFF6FBF1),
    onSurface = Color(0xFF181D17),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun FpsMeterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isAmoled -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                dynamicDarkColorScheme(context).copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceVariant = Color(0xFF141414),
                    surfaceContainer = Color(0xFF0A0A0A),
                    surfaceContainerHigh = Color(0xFF121212),
                    surfaceContainerHighest = Color(0xFF1A1A1A)
                )
            } else {
                AmoledDarkColors
            }
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
