package dev.fabik.bluetoothhid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.bt.KeyboardSender
import dev.fabik.bluetoothhid.ui.Dropdown
import dev.fabik.bluetoothhid.ui.NavGraph
import dev.fabik.bluetoothhid.ui.Routes
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.ui.theme.Typography
import dev.fabik.bluetoothhid.utils.RequestPermissions

class MainActivity : ComponentActivity() {

    private var keyboardSender: KeyboardSender? = null
    private val bluetoothController by lazy { BluetoothController(this) }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BluetoothHIDTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    RequestPermissions {
                        val navHostController = rememberNavController()
                        val context = LocalContext.current

                        NavGraph(navHostController, bluetoothController)

                        LaunchedEffect(bluetoothController) {
                            if (!bluetoothController.bluetoothEnabled()) {
                                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }

                            bluetoothController.register(context, listener = { hid, dev ->
                                keyboardSender = if (hid != null && dev != null) {
                                    runOnUiThread { navHostController.navigate(Routes.Main) }
                                    KeyboardSender(hid, dev)
                                } else {
                                    runOnUiThread { navHostController.navigate(Routes.Devices) }
                                    null
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return keyboardSender?.sendKeyEvent(keyCode, event) ?: super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        bluetoothController.unregister()
        super.onDestroy()
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navHostController: NavHostController,
    bluetoothController: BluetoothController,
    context: Context = LocalContext.current
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("BluetoothHID") }, actions = {
            IconButton(onClick = {
                val inputMethodManager =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.toggleSoftInput(
                    InputMethodManager.SHOW_FORCED, 0
                )
            }) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Toggle Keyboard"
                )
            }
            IconButton(onClick = {
                bluetoothController.disconnect()
            }) {
                Icon(
                    imageVector = Icons.Default.BluetoothDisabled,
                    contentDescription = "Disconnect"
                )
            }
            Dropdown(navHostController)
        })
    }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Text(
                "Connected!",
                color = MaterialTheme.colorScheme.primary,
                style = Typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}