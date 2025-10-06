package dev.fabik.bluetoothhid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import dev.fabik.bluetoothhid.ui.SettingsDropdown
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.ui.tooltip
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.Theme
import dev.fabik.bluetoothhid.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class SettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Read theme synchronously to pass to BluetoothHIDTheme (prevents async recomposition flash)
        val (themeMode, useDynamicColors) = runBlocking {
            dataStore.data.map { prefs ->
                val themeOrdinal = prefs[PreferenceStore.THEME.key]
                    ?: PreferenceStore.THEME.defaultValue
                val dynamicTheme = prefs[PreferenceStore.DYNAMIC_THEME.key]
                    ?: PreferenceStore.DYNAMIC_THEME.defaultValue
                Pair(Theme.entries[themeOrdinal], dynamicTheme)
            }.first()
        }

        // Enable high refresh rate (90/120 Hz)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display
            } else {
                windowManager.defaultDisplay
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

        setContent {
            BluetoothHIDTheme(
                darkTheme = themeMode,
                dynamicColor = useDynamicColors
            ) {
                // Set system bars appearance based on theme
                val view = LocalView.current
                val colorScheme = MaterialTheme.colorScheme
                val isLightTheme = colorScheme.background.luminance() > 0.5f

                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = isLightTheme
                    insetsController.isAppearanceLightNavigationBars = isLightTheme
                }

                Surface(Modifier.fillMaxSize()) {
                    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

                    Scaffold(
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(R.string.settings)) },
                                navigationIcon = {
                                    IconButton(
                                        onClick = { finishAfterTransition() },
                                        Modifier.tooltip(stringResource(R.string.back))
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                    }
                                },
                                actions = {
                                    SettingsDropdown()
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                scrollBehavior = scrollBehavior
                            )
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            SettingsContent()
                        }
                    }
                }
            }
        }
    }

}
