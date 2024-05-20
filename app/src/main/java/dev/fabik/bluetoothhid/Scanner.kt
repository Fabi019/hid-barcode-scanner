package dev.fabik.bluetoothhid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.camera.core.TorchState
import androidx.camera.view.CameraController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.bt.KeyTranslator
import dev.fabik.bluetoothhid.ui.CameraArea
import dev.fabik.bluetoothhid.ui.DialogState
import dev.fabik.bluetoothhid.ui.Dropdown
import dev.fabik.bluetoothhid.ui.InfoDialog
import dev.fabik.bluetoothhid.ui.LocalNavigation
import dev.fabik.bluetoothhid.ui.RequiresCameraPermission
import dev.fabik.bluetoothhid.ui.Routes
import dev.fabik.bluetoothhid.ui.theme.Neutral95
import dev.fabik.bluetoothhid.ui.theme.Typography
import dev.fabik.bluetoothhid.ui.tooltip
import dev.fabik.bluetoothhid.utils.DeviceInfo
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberPreference
import dev.fabik.bluetoothhid.utils.rememberPreferenceDefault
import kotlinx.coroutines.launch

/**
 * Scanner screen with camera preview.
 *
 * @param currentDevice the device that is currently connected, can be null if no device is connected
 * @param sendText callback to send text to the current device
 */
@Composable
fun Scanner(
    currentDevice: BluetoothDevice?,
    sendText: (String) -> Unit
) {
    var currentBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<CameraController?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val fullScreen by rememberPreference(PreferenceStore.SCANNER_FULL_SCREEN)

    Scaffold(
        topBar = {
            ScannerAppBar(camera, currentDevice, fullScreen)
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            currentDevice?.let {
                SendToDeviceFAB(currentBarcode, sendText)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .padding(if (fullScreen) PaddingValues(0.dp) else padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            RequiresCameraPermission {
                CameraPreviewArea(
                    onCameraReady = { camera = it }
                ) { value, send ->
                    currentBarcode = value
                    if (send) {
                        sendText(value)
                    }
                }
            }
        }
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            BarcodeValue(currentBarcode)
            CapsLockWarning()
            camera?.let {
                ZoomStateInfo(it)
            }
            KeepScreenOn()
        }
    }
}

/**
 * Area for the camera preview.
 *
 * @param onCameraReady callback to be called when the camera is ready
 * @param onBarcodeDetected callback to be called when a barcode is detected
 */
@Composable
private fun CameraPreviewArea(
    onCameraReady: (CameraController) -> Unit,
    onBarcodeDetected: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    val playSound by rememberPreferenceDefault(PreferenceStore.PLAY_SOUND)

    val toneGenerator = remember {
        runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
        }.onFailure {
            Log.e("Scanner", "Error initializing tone generator", it)
            Toast.makeText(context, "Error initializing tone generator", Toast.LENGTH_SHORT).show()
        }.getOrNull()
    }

    // Clean up tone generator after use
    DisposableEffect(toneGenerator) {
        onDispose {
            toneGenerator?.release()
        }
    }

    val autoSend by rememberPreferenceDefault(PreferenceStore.AUTO_SEND)
    val vibrate by rememberPreferenceDefault(PreferenceStore.VIBRATE)

    CameraArea(onCameraReady) {
        onBarcodeDetected(it, autoSend)

        if (playSound) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 75)
        }

        if (vibrate && vibrator.hasVibrator()) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }
}

/**
 * Text showing the current barcode value. If the value is null, a generic message is shown instead.
 *
 * @param currentBarcode the current barcode value
 */
@Composable
private fun BoxScope.BarcodeValue(currentBarcode: String?) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val privateMode by rememberPreference(PreferenceStore.PRIVATE_MODE)

    Column(
        Modifier
            .fillMaxHeight(0.3f)
            .fillMaxWidth()
            .align(Alignment.TopStart),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val copiedString = stringResource(R.string.copied_to_clipboard)
        var hideText by remember(privateMode, currentBarcode) {
            mutableStateOf(privateMode)
        }

        currentBarcode?.let {
            val text = AnnotatedString(
                if (hideText) "*".repeat(it.length) else it,
                SpanStyle(Neutral95),
                ParagraphStyle(TextAlign.Center)
            )

            ClickableText(
                text,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            ) {
                if (privateMode) {
                    hideText = !hideText
                } else {
                    clipboardManager.setText(text)
                    Toast.makeText(context, copiedString, Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Text(
                stringResource(R.string.scan_code_to_start),
                color = Neutral95,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Floating action button to send the current barcode to the connected device.
 * If the currentBarcode is null, the button is hidden.
 *
 * @param currentBarcode the current barcode value
 * @param onClick callback to send text to the current device
 */
@Composable
private fun SendToDeviceFAB(
    currentBarcode: String?,
    onClick: (String) -> Unit
) {
    currentBarcode?.let {
        val controller = LocalController.current
        val disabled = controller.isSending

        val (containerColor, contentColor) = if (!disabled) {
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) to
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
        }

        val noRippleTheme = remember {
            object : RippleTheme {
                @Composable
                override fun defaultColor() = Color.Unspecified

                @Composable
                override fun rippleAlpha(): RippleAlpha = RippleAlpha(0.0f, 0.0f, 0.0f, 0.0f)
            }
        }

        CompositionLocalProvider(
            LocalRippleTheme provides
                    if (!disabled) LocalRippleTheme.current else noRippleTheme
        ) {
            ExtendedFloatingActionButton(
                text = {
                    Text(stringResource(R.string.send_to_device))
                },
                icon = {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                },
                contentColor = contentColor,
                containerColor = containerColor,
                onClick = { if (!disabled) onClick(it) }
            )
        }
    }
}

/**
 * Scanner app bar with a toggle flash button and a disconnect button.
 *
 * @param camera the camera to toggle the flash on
 * @param currentDevice the device that is currently connected, can be null if no device is connected
 * @param transparent whether the app bar should be transparent or not
 */
@SuppressLint("MissingPermission")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ScannerAppBar(
    camera: CameraController?,
    currentDevice: BluetoothDevice?,
    transparent: Boolean,
) {
    val navigation = LocalNavigation.current

    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.scanner))
                currentDevice?.let {
                    Text(
                        stringResource(R.string.connected_with, it.name ?: it.address),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        actions = {
            camera?.let {
                if (it.cameraInfo?.hasFlashUnit() == true) {
                    ToggleFlashButton(it)
                }
            }
            IconButton(onClick = {
                navigation.navigate(Routes.History)
            }, Modifier.tooltip("History")) {
                Icon(Icons.Default.History, "History")
            }
//            IconButton(onDisconnect, Modifier.tooltip(stringResource(R.string.disconnect))) {
//                Icon(Icons.Default.BluetoothDisabled, "Disconnect")
//            }
            Dropdown()
        },
        colors = if (transparent) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        } else {
            TopAppBarDefaults.topAppBarColors()
        },
    )
}

/**
 * Toggle flash button to toggle the flash on the camera.
 *
 * @param camera the camera to toggle the flash on
 */
@Composable
fun ToggleFlashButton(camera: CameraController) {
    val torchState by camera.torchState.observeAsState()

    IconButton(
        onClick = {
            camera.enableTorch(
                when (torchState) {
                    TorchState.OFF -> true
                    else -> false
                }
            )
        },
        modifier = Modifier.tooltip(stringResource(R.string.toggle_flash))
    ) {
        Icon(
            when (torchState) {
                TorchState.OFF -> Icons.Default.FlashOn
                else -> Icons.Default.FlashOff
            }, "Flash"
        )
    }
}

/**
 * Component that warns the user if the caps lock key is activated.
 * Clicking on the card will send a caps lock key press to the
 * connected device and disables it.
 */
@Composable
fun BoxScope.CapsLockWarning() {
    val controller = LocalController.current
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        controller.isCapsLockOn, Modifier
            .padding(12.dp)
            .align(Alignment.TopCenter)
    ) {
        ElevatedCard(
            onClick = {
                scope.launch {
                    controller.keyboardSender?.sendKey(KeyTranslator.CAPS_LOCK_KEY)
                }
            }
        ) {
            Row(
                Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, "Warning")
                Column {
                    Text(stringResource(R.string.caps_lock_activated))
                    Text(stringResource(R.string.click_to_turn_off), style = Typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Displays the current zoom-factor as a text in the top-start corner.
 * If the factor is equal to 1.0 the text is hidden.
 *
 * @param camera the camera to get the zoom-factor from
 */
@Composable
fun BoxScope.ZoomStateInfo(camera: CameraController) {
    val zoomState by camera.zoomState.observeAsState()
    zoomState?.let {
        if (it.zoomRatio > 1.0f) {
            Text(
                "%.2fx".format(it.zoomRatio),
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
    }
}

/**
 * Dialog showing information about the current device.
 *
 * @param dialogState state whether the dialog is open or not
 * @param device the device to show information about
 */
@SuppressLint("MissingPermission")
@Composable
fun DeviceInfoDialog(
    dialogState: DialogState,
    device: BluetoothDevice
) {
    InfoDialog(dialogState, stringResource(R.string.info)) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.name) + ":", fontWeight = FontWeight.Bold)
                Text(device.name ?: "")
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
                        DeviceInfo.deviceClassString(this)
                    }
                    Text("$classString (${this})")
                }
            }

            item {
                Text(stringResource(R.string.services) + ":", fontWeight = FontWeight.Bold)
                val serviceInfo = remember(device) {
                    DeviceInfo.deviceServiceInfo(device.bluetoothClass)
                }
                serviceInfo.forEach {
                    Text(it)
                }
            }

//            item {
//                Text(stringResource(R.string.uuids) + ":", fontWeight = FontWeight.Bold)
//                device.uuids?.forEach {
//                    Text(it.toString())
//                }
//            }
        }
    }
}

@Composable
fun KeepScreenOn() {
    val keepScreenOn by rememberPreference(PreferenceStore.KEEP_SCREEN_ON)
    val currentView = LocalView.current

    DisposableEffect(keepScreenOn) {
        currentView.keepScreenOn = keepScreenOn
        onDispose {
            currentView.keepScreenOn = false
        }
    }
}
