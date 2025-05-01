package dev.fabik.bluetoothhid

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.fabik.bluetoothhid.bt.IBluetoothController
import dev.fabik.bluetoothhid.bt.rememberBluetoothControllerService
import dev.fabik.bluetoothhid.ui.NavGraph
import dev.fabik.bluetoothhid.ui.RequiresBluetoothPermission
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.utils.JsEngineService
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberJsEngineService
import dev.fabik.bluetoothhid.utils.rememberPreference

val LocalController = staticCompositionLocalOf<IBluetoothController?> {
    null
}

val LocalJsEngineService = staticCompositionLocalOf<JsEngineService.LocalBinder?> {
    null
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            BluetoothHIDTheme {
                Surface(Modifier.fillMaxSize()) {
                    val allowScreenRotation by rememberPreference(PreferenceStore.ALLOW_SCREEN_ROTATION)

                    LaunchedEffect(allowScreenRotation) {
                        requestedOrientation = if (allowScreenRotation) {
                            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
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
