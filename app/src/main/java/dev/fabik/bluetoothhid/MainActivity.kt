package dev.fabik.bluetoothhid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.bt.KeyboardSender
import dev.fabik.bluetoothhid.ui.CameraPreview
import dev.fabik.bluetoothhid.ui.Dropdown
import dev.fabik.bluetoothhid.ui.NavGraph
import dev.fabik.bluetoothhid.ui.Routes
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.ui.theme.Typography
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.RequestPermissions
import dev.fabik.bluetoothhid.utils.RequiresCameraPermission
import dev.fabik.bluetoothhid.utils.getPreferenceState

class MainActivity : ComponentActivity() {

    private var keyboardSender: KeyboardSender? = null
    private val bluetoothController by lazy { BluetoothController(this) }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val useDynTheme by context.getPreferenceState(PrefKeys.DYNAMIC_THEME)
            BluetoothHIDTheme(dynamicColor = useDynTheme ?: false) {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    RequestPermissions {
                        val navHostController = rememberNavController()

                        NavGraph(navHostController, bluetoothController) {
                            keyboardSender?.sendString(it)
                        }

                        LaunchedEffect(bluetoothController) {
                            if (!bluetoothController.bluetoothEnabled()) {
                                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }

                            bluetoothController.register(context, listener = { hid, dev ->
                                keyboardSender = if (hid != null && dev != null) {
                                    runOnUiThread {
                                        navHostController.popBackStack()
                                        navHostController.navigate(Routes.Main) {
                                            launchSingleTop = true
                                        }
                                    }
                                    KeyboardSender(hid, dev)
                                } else {
                                    runOnUiThread {
                                        navHostController.popBackStack()
                                        navHostController.navigate(Routes.Devices) {
                                            launchSingleTop = true
                                        }
                                    }
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
        return keyboardSender?.sendKeyEvent(keyCode, event) ?: false
                || super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        bluetoothController.unregister()
        super.onDestroy()
    }

}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    navHostController: NavHostController,
    bluetoothController: BluetoothController,
    onSendText: (String) -> Unit,
) {
    val context = LocalContext.current

    var currentBarcode by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
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
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            currentBarcode?.let {
                ExtendedFloatingActionButton(
                    onClick = { onSendText(it) }
                ) {
                    Icon(Icons.Filled.Send, "Send")
                    Spacer(Modifier.width(8.dp))
                    Text("Send to Device")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            RequiresCameraPermission {
                CameraPreview {
                    currentBarcode = it
                }

                currentBarcode?.let {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(4.dp, 16.dp)
                    ) {
                        Text(
                            "Detected Barcode",
                            style = Typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(it, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}