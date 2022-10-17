package dev.fabik.bluetoothhid

import android.bluetooth.BluetoothDevice
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.TorchState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.ui.*
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.deviceClassString
import dev.fabik.bluetoothhid.utils.deviceServiceInfo
import dev.fabik.bluetoothhid.utils.rememberPreferenceDefault

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Scanner(
    navController: NavController, bluetoothController: BluetoothController
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    var currentBarcode by remember { mutableStateOf<String?>(null) }

    val playSound by rememberPreferenceDefault(PrefKeys.PLAY_SOUND)

    val toneGenerator = remember {
        ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
    }

    val autoSend by rememberPreferenceDefault(PrefKeys.AUTO_SEND)

    val vibrate by rememberPreferenceDefault(PrefKeys.VIBRATE)

    var camera by remember { mutableStateOf<Camera?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.scanner)) }, actions = {
            camera?.let {
                if (it.cameraInfo.hasFlashUnit()) {
                    ToggleFlashButton(it)
                }
            }
            IconButton(onClick = {
                if (!bluetoothController.disconnect()) {
                    navController.navigateUp()
                }
            }) {
                Icon(Icons.Default.BluetoothDisabled, "Disconnect")
            }
            Dropdown(navController)
        })
    }, floatingActionButtonPosition = FabPosition.Center, floatingActionButton = {
        currentBarcode?.let {
            ExtendedFloatingActionButton(onClick = {
                bluetoothController.keyboardSender?.sendString(it)
            }) {
                Icon(Icons.Filled.Send, "Send")
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.send_to_device))
            }
        }
    }) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            RequiresCameraPermission {
                CameraPreview(onCameraReady = { camera = it }) {
                    currentBarcode = it
                    if (playSound) {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 75)
                    }
                    if (autoSend) {
                        bluetoothController.keyboardSender?.sendString(it)
                    }
                    if (vibrate) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            @Suppress("DEPRECATION") view.performHapticFeedback(
                                HapticFeedbackConstants.LONG_PRESS,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                            )
                        } else {
                            // Might not always work
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
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
                    currentBarcode?.let {
                        val text = AnnotatedString(
                            it,
                            SpanStyle(MaterialTheme.colorScheme.onBackground),
                            ParagraphStyle(TextAlign.Center)
                        )
                        ClickableText(
                            text, maxLines = 6, overflow = TextOverflow.Ellipsis
                        ) {
                            clipboardManager.setText(text)
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT)
                                .show()
                        }
                    } ?: run {
                        Text(
                            stringResource(R.string.scan_code_to_start),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            bluetoothController.currentDevice()?.let {
                DeviceInfoDialog(it)
            }
        }
    }
}

@Composable
fun ToggleFlashButton(camera: Camera) {
    val torchState by camera.cameraInfo.torchState.observeAsState()

    IconButton(onClick = {
        camera.cameraControl.enableTorch(
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

@Suppress("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.DeviceInfoDialog(device: BluetoothDevice) {
    val dialogState = rememberDialogState()

    ElevatedCard(
        onClick = {
            dialogState.open()
        },
        Modifier
            .padding(12.dp)
            .align(Alignment.TopCenter)
    ) {
        Row(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, "Info")
            Text(stringResource(R.string.connected_with, device.name))
        }
    }

    InfoDialog(dialogState, stringResource(R.string.info), onDismiss = { close() }) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.name) + ":", fontWeight = FontWeight.Bold)
                Text(device.name)
            }

            item {
                Text(stringResource(R.string.mac_address) + ":", fontWeight = FontWeight.Bold)
                Text(device.address)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                item {
                    Text(stringResource(R.string.alias) + ":", fontWeight = FontWeight.Bold)
                    Text(device.alias ?: "")
                }
            }

            item {
                Text(stringResource(R.string.type) + ":", fontWeight = FontWeight.Bold)
                Text(
                    when (device.type) {
                        BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                        BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                        BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "Unknown"
                        else -> "?"
                    } + " (${device.type})"
                )
            }

            item {
                Text(stringResource(R.string.clazz) + ":", fontWeight = FontWeight.Bold)
                with(device.bluetoothClass.majorDeviceClass) {
                    val classString = remember(device) {
                        deviceClassString(this)
                    }
                    Text("$classString (${this})")
                }
            }

            item {
                Text(stringResource(R.string.services) + ":", fontWeight = FontWeight.Bold)
                val serviceInfo = remember(device) {
                    deviceServiceInfo(device.bluetoothClass)
                }
                serviceInfo.forEach {
                    Text(it)
                }
            }

            item {
                Text(stringResource(R.string.uuids) + ":", fontWeight = FontWeight.Bold)
                device.uuids.forEach {
                    Text(it.toString())
                }
            }
        }
    }
}