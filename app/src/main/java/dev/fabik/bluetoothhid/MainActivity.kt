package dev.fabik.bluetoothhid

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.bt.rememberBluetoothControllerService
import dev.fabik.bluetoothhid.ui.NavGraph
import dev.fabik.bluetoothhid.ui.RequiresBluetoothPermission
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.ui.theme.configureWindow
import dev.fabik.bluetoothhid.utils.JsEngineService
import dev.fabik.bluetoothhid.utils.LocalDataStore
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.ProfileManager
import dev.fabik.bluetoothhid.utils.getPreferenceState
import dev.fabik.bluetoothhid.utils.rememberJsEngineService
import kotlinx.coroutines.runBlocking

val LocalController = staticCompositionLocalOf<BluetoothController?> {
    null
}

val LocalJsEngineService = staticCompositionLocalOf<JsEngineService.LocalBinder?> {
    null
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Configure window for high refresh rate and transparency
        configureWindow(window)

        // Load saved active profile before first composition to avoid a flash of wrong settings
        runBlocking { ProfileManager.initialize(applicationContext) }

        setContent {
            // Observe active DataStore so all composables recompose on profile switch
            val activeDataStore by remember { ProfileManager.activeStoreFlow(applicationContext) }
                .collectAsStateWithLifecycle(initialValue = ProfileManager.currentStore(applicationContext))

            CompositionLocalProvider(LocalDataStore provides activeDataStore) {
                BluetoothHIDTheme(window = window) {
                    Surface(Modifier.fillMaxSize()) {
                        val allowScreenRotation by getPreferenceState(PreferenceStore.ALLOW_SCREEN_ROTATION)

                        allowScreenRotation?.let {
                            LaunchedEffect(allowScreenRotation) {
                                requestedOrientation = if (it) {
                                    ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
                                }
                            }
                        }

                        RequiresBluetoothPermission {
                            val bluetoothService = rememberBluetoothControllerService(this)
                            val jsEngineService = rememberJsEngineService(this)

                            CompositionLocalProvider(
                                LocalController provides bluetoothService?.getController(),
                                LocalJsEngineService provides jsEngineService
                            ) {
                                NavGraph()
                            }

                            PersistHistory()
                        }
                    }
                }
            }
        }
    }
}
