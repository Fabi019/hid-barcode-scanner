package dev.fabik.bluetoothhid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.rememberNavController
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.ui.NavGraph
import dev.fabik.bluetoothhid.ui.RequiresBluetoothPermission
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.utils.ComposableLifecycle
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.rememberPreferenceDefault

val LocalController =
    staticCompositionLocalOf<BluetoothController> { error("No controller injected!") }

class MainActivity : ComponentActivity() {

    private val bluetoothController by lazy { BluetoothController(this) }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme by rememberPreferenceDefault(PrefKeys.THEME)
            val useDynTheme by rememberPreferenceDefault(PrefKeys.DYNAMIC_THEME)

            BluetoothHIDTheme(
                darkTheme = when (theme) {
                    1 -> false
                    2 -> true
                    else -> isSystemInDarkTheme()
                },
                dynamicColor = useDynTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    RequiresBluetoothPermission {
                        val navHostController = rememberNavController()

                        CompositionLocalProvider(LocalController provides bluetoothController) {
                            NavGraph(navHostController)
                        }

                        ComposableLifecycle { _, event ->
                            if (event == Lifecycle.Event.ON_START && !bluetoothController.bluetoothEnabled()) {
                                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            } else if (event == Lifecycle.Event.ON_RESUME) {
                                bluetoothController.register()
                            } else if (event == Lifecycle.Event.ON_PAUSE) {
                                bluetoothController.unregister()
                            }
                        }

                        val autoConnect by rememberPreferenceDefault(PrefKeys.AUTO_CONNECT)
                        LaunchedEffect(autoConnect) {
                            bluetoothController.autoConnectEnabled = autoConnect
                        }
                    }
                }
            }
        }
    }
}