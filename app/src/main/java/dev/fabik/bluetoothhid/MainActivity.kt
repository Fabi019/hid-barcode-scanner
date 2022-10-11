package dev.fabik.bluetoothhid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.bt.KeyboardSender
import dev.fabik.bluetoothhid.ui.NavGraph
import dev.fabik.bluetoothhid.ui.Routes
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.RequiresBluetoothPermission
import dev.fabik.bluetoothhid.utils.getPreference
import dev.fabik.bluetoothhid.utils.rememberPreferenceDefault

class MainActivity : ComponentActivity() {

    private var keyboardSender: KeyboardSender? = null
    private val bluetoothController by lazy { BluetoothController(this) }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current

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

                        NavGraph(navHostController, bluetoothController) {
                            keyboardSender?.sendString(it)
                        }

                        DisposableEffect(bluetoothController) {
                            if (!bluetoothController.bluetoothEnabled()) {
                                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }

                            bluetoothController.register(context, listener = { hid, dev ->
                                keyboardSender = if (hid != null && dev != null) {
                                    runOnUiThread {
                                        navHostController.navigate(Routes.Main) {
                                            launchSingleTop = true
                                        }
                                    }
                                    KeyboardSender(
                                        context.getPreference(PrefKeys.EXTRA_KEYS),
                                        context.getPreference(PrefKeys.SEND_DELAY),
                                        hid,
                                        dev
                                    )
                                } else {
                                    runOnUiThread {
                                        navHostController.navigate(Routes.Devices) {
                                            launchSingleTop = true
                                        }
                                    }
                                    null
                                }
                            })

                            onDispose {
                                bluetoothController.unregister()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return keyboardSender?.sendKeyEvent(keyCode, event) ?: false
                || super.onKeyUp(keyCode, event)
    }

}