package dev.fabik.bluetoothhid

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.TorchState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
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
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.ui.CameraPreview
import dev.fabik.bluetoothhid.ui.Dropdown
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.RequiresCameraPermission
import dev.fabik.bluetoothhid.utils.rememberPreferenceDefault

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Scanner(
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

    var camera by remember { mutableStateOf<Camera?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scanner)) },
                actions = {
                    camera?.let {
                        if (it.cameraInfo.hasFlashUnit()) {
                            val torchState by it.cameraInfo.torchState.observeAsState()

                            IconButton(onClick = {
                                it.cameraControl.enableTorch(
                                    when (torchState) {
                                        TorchState.OFF -> true
                                        else -> false
                                    }
                                )
                            }) {
                                Icon(
                                    when (torchState) {
                                        TorchState.OFF -> Icons.Default.FlashOn
                                        else -> Icons.Default.FlashOff
                                    }, "Flash"
                                )
                            }
                        }
                    }
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
                }
            )
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
                CameraPreview(onCameraReady = { camera = it }) {
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