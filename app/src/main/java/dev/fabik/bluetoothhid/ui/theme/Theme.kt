package dev.fabik.bluetoothhid.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun BluetoothHIDTheme(
    darkTheme: Theme? = null,  // Initial value from Activity (prevents flash on transition)
    dynamicColor: Boolean? = null,  // Initial value from Activity (prevents flash on transition)
    content: @Composable () -> Unit
) {
    // Async preferences - will load after first composition
    val prefDarkTheme by rememberEnumPreference(PreferenceStore.THEME)
    val prefDynamicColor by rememberPreference(PreferenceStore.DYNAMIC_THEME)

    // Track if we should use initial values (only on first render to prevent flash)
    var useInitialValues by remember { mutableStateOf(darkTheme != null || dynamicColor != null) }

    // Once async preferences load, switch to using them (enables live changes)
    LaunchedEffect(prefDarkTheme, prefDynamicColor) {
        useInitialValues = false
    }

    // Hybrid approach: initial values prevent flash, then switch to reactive preferences
    val actualDarkTheme = if (useInitialValues && darkTheme != null) darkTheme else prefDarkTheme
    val actualDynamicColor = if (useInitialValues && dynamicColor != null) dynamicColor else prefDynamicColor

    val dark = when (actualDarkTheme) {
        Theme.LIGHT -> false
        Theme.DARK -> true
        Theme.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        actualDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        dark -> DarkColorScheme
        else -> LightColorScheme
    }

    // Disable ripple effects for better scroll performance on 120Hz screens
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
