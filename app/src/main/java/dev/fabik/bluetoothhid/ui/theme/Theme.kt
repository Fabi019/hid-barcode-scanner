package dev.fabik.bluetoothhid.ui.theme

import android.os.Build
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.Theme
import dev.fabik.bluetoothhid.utils.rememberEnumPreference
import dev.fabik.bluetoothhid.utils.rememberPreference

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/**
 * Configures window properties for better performance and transparency.
 * - Disables navigation bar contrast enforcement for true transparency
 * - Enables high refresh rate (90/120 Hz) if supported
 *
 * @param window The activity window to configure
 */
fun configureWindow(window: Window) {
    // Disable navigation bar contrast enforcement to allow true transparency
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }

    // Enable high refresh rate (90/120 Hz)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.context.display
        } else {
            window.windowManager.defaultDisplay
        }

        display?.let {
            val modes = it.supportedModes
            val highRefreshMode = modes.maxByOrNull { mode -> mode.refreshRate }
            highRefreshMode?.let { mode ->
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = mode.modeId
                }
            }
        }
    }
}

/**
 * Controls the appearance of system bars (status bar and navigation bar) based on theme.
 * Adjusts icon colors to be light or dark depending on background luminance.
 *
 * @param window The activity window to control
 */
@Composable
fun SystemBarsController(window: Window) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f

    SideEffect {
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = isLightTheme
        insetsController.isAppearanceLightNavigationBars = isLightTheme
    }
}

@Composable
fun BluetoothHIDTheme(
    window: Window? = null,  // Activity window for system bars control
    content: @Composable () -> Unit
) {
    // Use blocking preferences to prevent theme flash on startup
    val prefDarkTheme by rememberEnumPreference(PreferenceStore.THEME)
    val prefDynamicColor by rememberPreference(PreferenceStore.DYNAMIC_THEME)

    val dark = when (prefDarkTheme) {
        Theme.LIGHT -> false
        Theme.DARK -> true
        Theme.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        prefDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        dark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        // Control system bars appearance if window is provided
        window?.let { SystemBarsController(it) }

        content()
    }
}
