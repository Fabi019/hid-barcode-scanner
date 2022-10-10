package dev.fabik.bluetoothhid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import dev.fabik.bluetoothhid.utils.*

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

                        LaunchedEffect(bluetoothController) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navHostController: NavHostController,
    bluetoothController: BluetoothController,
    onSendText: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var currentBarcode by remember { mutableStateOf<String?>(null) }

    val playSound by rememberPreferenceDefault(PrefKeys.PLAY_SOUND)

    val toneGenerator = remember {
        ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
    }

    val autoSend by rememberPreferenceDefault(PrefKeys.AUTO_SEND)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.scanner)) }, actions = {
                IconButton(onClick = {
                    val inputMethodManager =
                        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.toggleSoftInput(
                        InputMethodManager.SHOW_FORCED, 0
                    )
                }) {
                    Icon(Icons.Default.Keyboard, "Keyboard")
                }
                IconButton(onClick = {
                    if (!bluetoothController.disconnect()) {
                        navHostController.navigateUp()
                    }
                }) {
                    Icon(Icons.Default.BluetoothDisabled, "Disconnect")
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
                    Text(stringResource(R.string.send_to_device))
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            RequiresCameraPermission {
                CameraPreview {
                    currentBarcode = it
                    if (playSound) {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 75)
                    }
                    if (autoSend) {
                        onSendText(it)
                    }
                }

                Column(
                    Modifier
                        .fillMaxHeight(0.3f)
                        .fillMaxWidth()
                        .align(Alignment.TopStart),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (currentBarcode != null) {
                        val text = AnnotatedString(
                            currentBarcode!!,
                            SpanStyle(MaterialTheme.colorScheme.onBackground),
                            ParagraphStyle(TextAlign.Center)
                        )
                        ClickableText(
                            text,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        ) {
                            clipboardManager.setText(text)
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT)
                                .show()
                        }
                    } else {
                        Text(
                            stringResource(R.string.scan_code_to_start),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(230.dp)
                        )
                    }
                }
            }
        }
    }
}