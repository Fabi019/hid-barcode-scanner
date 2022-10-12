package dev.fabik.bluetoothhid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.ui.NavGraph
import dev.fabik.bluetoothhid.ui.Routes
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.RequiresBluetoothPermission
import dev.fabik.bluetoothhid.utils.rememberPreferenceDefault

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

                        NavGraph(navHostController, bluetoothController)

                        val showConnectionState by rememberPreferenceDefault(PrefKeys.SHOW_STATE)

                        DisposableEffect(bluetoothController) {
                            if (!bluetoothController.bluetoothEnabled()) {
                                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }

                            bluetoothController.register()

                            bluetoothController.registerListener { device, state ->
                                runOnUiThread {
                                    if (showConnectionState) {
                                        Toast.makeText(
                                            this@MainActivity, "$device ${
                                                when (state) {
                                                    BluetoothProfile.STATE_CONNECTING -> "connecting..."
                                                    BluetoothProfile.STATE_CONNECTED -> "connected!"
                                                    BluetoothProfile.STATE_DISCONNECTING -> "disconnecting..."
                                                    BluetoothProfile.STATE_DISCONNECTED -> "disconnected!"
                                                    else -> "unknown ($state)"
                                                }
                                            }", Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    if (device != null && state == BluetoothProfile.STATE_CONNECTED) {
                                        navHostController.navigate(Routes.Main) {
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navHostController.navigate(Routes.Devices) {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }

                            onDispose {
                                bluetoothController.unregister()
                            }
                        }
                    }
                }
            }
        }
    }

}