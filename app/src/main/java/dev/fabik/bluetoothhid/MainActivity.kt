package dev.fabik.bluetoothhid

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.bt.rememberBluetoothControllerService
import dev.fabik.bluetoothhid.ui.NavGraph
import dev.fabik.bluetoothhid.ui.RequiresBluetoothPermission
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.utils.JsEngineService
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberJsEngineService
import dev.fabik.bluetoothhid.utils.rememberPreference

val LocalController = staticCompositionLocalOf<BluetoothController> {
    error("No BluetoothController provided")
}

val LocalJsEngineService = staticCompositionLocalOf<JsEngineService.LocalBinder?> {
    error("No JsEngineService provided")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

                        bluetoothService?.let {
                            CompositionLocalProvider(
                                LocalController provides it.getController(),
                                LocalJsEngineService provides jsEngineService
                            ) {
                                NavGraph()
                            }
                        }
                    }
                }
            }
        }
    }
}
