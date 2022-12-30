package dev.fabik.bluetoothhid

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.ui.NavGraph
import dev.fabik.bluetoothhid.ui.RequiresBluetoothPermission
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.utils.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val bluetoothController by lazy { BluetoothController(this) }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothHIDTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RequiresBluetoothPermission {
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()

                        NavGraph(bluetoothController)

                        ComposableLifecycle { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_START -> {
                                    scope.launch {
                                        if (!bluetoothController.register()) {
                                            Toast.makeText(
                                                context,
                                                getString(R.string.bt_proxy_error),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                Lifecycle.Event.ON_STOP -> bluetoothController.unregister()
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
