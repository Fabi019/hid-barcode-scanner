package dev.fabik.bluetoothhid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.bt.KeyTranslator
import dev.fabik.bluetoothhid.ui.CameraPreviewContent
import dev.fabik.bluetoothhid.ui.ConfirmDialog
import dev.fabik.bluetoothhid.ui.DialogState
import dev.fabik.bluetoothhid.ui.Dropdown
import dev.fabik.bluetoothhid.ui.InfoDialog
import dev.fabik.bluetoothhid.ui.LocalNavigation
import dev.fabik.bluetoothhid.ui.RequiresCameraPermission
import dev.fabik.bluetoothhid.ui.Routes
import dev.fabik.bluetoothhid.ui.model.CameraViewModel
import dev.fabik.bluetoothhid.ui.rememberDialogState
import dev.fabik.bluetoothhid.ui.tooltip
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.DeviceInfo
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.VolumeKeyAction
import dev.fabik.bluetoothhid.utils.getPreferenceState
import dev.fabik.bluetoothhid.utils.getPreferenceStateBlocking
import dev.fabik.bluetoothhid.utils.getPreferenceStateDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Helper function to create a circular background modifier for icons in fullscreen mode.
 * Provides better visibility by adding a semi-transparent black circle behind the icon.
 */
private fun Modifier.iconBackgroundModifier(): Modifier {
    return drawBehind {
        drawCircle(
            color = Color.Black.copy(alpha = 0.5f),
            radius = size.maxDimension
        )
    }
}

@Composable
fun BoxScope.ElevatedWarningCard(
    message: String,
    subMessage: String? = null,
    onClick: () -> Unit,
    visible: Boolean
) {
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = visible, // You will control visibility dynamically
        modifier = Modifier
            .padding(12.dp)
            .align(Alignment.TopCenter)
    ) {
        ElevatedCard(
            onClick = { scope.launch { onClick() } }
        ) {
            Row(
                Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = "Warning")
                Column {
                    Text(message)
                    subMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/**
 * Scanner screen with camera preview.
 *
 * @param currentDevice the device that is currently connected, can be null if no device is connected
 * @param sendText callback to send text and format to the current device
 */
@Composable
fun Scanner(
    currentDevice: BluetoothDevice?,
    sendText: (String, Int?, String?) -> Unit
) {
    val context = LocalContext.current

    var currentBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var currentBarcodeFormat by rememberSaveable { mutableStateOf<Int?>(null) }
    var currentImageName by rememberSaveable { mutableStateOf<String?>(null) }

    val currentSendText = remember(currentBarcode, currentBarcodeFormat, currentImageName) {
        { -> currentBarcode?.let { sendText(it, currentBarcodeFormat, currentImageName) } }
    }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<CameraInfo?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val fullScreen by context.getPreferenceStateBlocking(PreferenceStore.SCANNER_FULL_SCREEN)
    AdaptSystemBarsColor(fullScreen)

    val keyboardDialog = rememberDialogState()
    val cameraVM = viewModel<CameraViewModel>()

    Scaffold(
        topBar = {
            ScannerAppBar(cameraControl, cameraInfo, currentDevice, fullScreen, keyboardDialog)
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            currentDevice?.let {
                val clearAfterSend by context.getPreferenceState(PreferenceStore.CLEAR_AFTER_SEND)

                currentBarcode?.let {
                    SendToDeviceFAB {
                        currentSendText()

                        if (clearAfterSend == true) {
                            currentBarcode = null
                            currentBarcodeFormat = null
                            currentImageName = null
                            cameraVM.lastBarcode = null
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .padding(if (fullScreen) PaddingValues(0.dp) else padding)
                .fillMaxSize()
        ) {
            RequiresCameraPermission {
                CameraPreviewArea(onCameraReady = { control, info, capt ->
                    cameraControl = control; cameraInfo = info
                }) { value, format, imageName, send ->
                    currentBarcode = value
                    currentBarcodeFormat = format
                    currentImageName = imageName
                    if (send && value?.isNotEmpty() == true) {
                        currentSendText()
                    }
                }
            }

            val volumeActionUp by context.getPreferenceState(PreferenceStore.VOLUME_ACTION_UP)
            val volumeActionDown by context.getPreferenceState(PreferenceStore.VOLUME_ACTION_DOWN)
            val volumeZoom by context.getPreferenceState(PreferenceStore.VOLUME_ZOOM_LEVEL)

            VolumeKeyHandler {
                val value =
                    if (it == KeyEvent.KEYCODE_VOLUME_UP) volumeActionUp else volumeActionDown
                val action = VolumeKeyAction.fromIndex(value ?: return@VolumeKeyHandler false)
                when (action) {
                    VolumeKeyAction.NOTHING -> return@VolumeKeyHandler false
                    VolumeKeyAction.SEND_VALUE -> currentSendText()
                    VolumeKeyAction.TOGGLE_ZOOM -> {
                        if (cameraInfo?.zoomState?.value?.linearZoom == 0.0f)
                            cameraControl?.setLinearZoom(minOf((volumeZoom ?: 100f) / 100f, 1.0f))
                        else
                            cameraControl?.setLinearZoom(0.0f)
                    }
                    VolumeKeyAction.OPEN_KEYBOARD -> keyboardDialog.open()
                    VolumeKeyAction.TOGGLE_FLASH -> cameraControl?.enableTorch(cameraInfo?.torchState?.value == TorchState.OFF)
                    VolumeKeyAction.TRIGGER_FOCUS -> cameraVM.focusAtCenter()
                    VolumeKeyAction.CLEAR_VALUE -> {
                        currentBarcode = null
                        currentBarcodeFormat = null
                        currentImageName = null
                        cameraVM.lastBarcode = null
                    }
                    VolumeKeyAction.RUN_OCR -> cameraVM.triggerOcr()
                }
                return@VolumeKeyHandler true
            }

            // Gradients for better system bars visibility in fullscreen mode
            if (fullScreen) {
                // Gradient at top (under status bar) - more concentrated
                Box(
                    Modifier
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Gradient at bottom (under navigation bar)
                Box(
                    Modifier
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
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
            DeviceStatusIndicator()

            cameraInfo?.let {
                ZoomStateInfo(it)
            }
            KeepScreenOn()
        }
    }
}

@Composable
fun AdaptSystemBarsColor(fullScreen: Boolean) {
    // Calculate light theme the same way MainActivity does
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f
    val activity = LocalActivity.current
    val view = LocalView.current

    // Manage system bars appearance only in fullscreen mode
    // In non-fullscreen mode, MainActivity handles system bars based on theme
    // Use isLightTheme as key to react to theme changes
    DisposableEffect(fullScreen, isLightTheme) {
        val insetsController = activity?.window?.let { WindowCompat.getInsetsController(it, view) }

        if (fullScreen && insetsController != null) {
            // In fullscreen mode, use white icons for better visibility on camera background
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false

            onDispose {
                // Restore proper system bars appearance based on current theme
                insetsController.isAppearanceLightStatusBars = isLightTheme
                insetsController.isAppearanceLightNavigationBars = isLightTheme
            }
        } else {
            onDispose { }
        }
    }
}

/**
 * Area for the camera preview.
 *
 * @param onCameraReady callback to be called when the camera is ready
 * @param onBarcodeDetected callback to be called when a barcode is detected (value, format, scanImageName, autoSend)
 */
@Composable
private fun CameraPreviewArea(
    onCameraReady: (CameraControl?, CameraInfo?, ImageCapture?) -> Unit,
    onBarcodeDetected: (String?, Int?, String?, Boolean) -> Unit,
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

    val playSound by context.getPreferenceStateDefault(PreferenceStore.PLAY_SOUND)

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

    val autoSend by context.getPreferenceStateDefault(PreferenceStore.AUTO_SEND)
    val vibrate by context.getPreferenceStateDefault(PreferenceStore.VIBRATE)

    Log.d(
        "Scanner",
        "Scanner initialized - vibrate: $vibrate, hasVibrator: ${vibrator.hasVibrator()}, SDK: ${Build.VERSION.SDK_INT}"
    )

    CameraPreviewContent(onCameraReady = onCameraReady) { value, format, imageName ->
        Log.d("Scanner", "Barcode detected: $value")
        onBarcodeDetected(value, format, imageName, autoSend)
        if (value == null) return@CameraPreviewContent

        if (playSound) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 75)
        }

        if (vibrate && vibrator.hasVibrator()) {
            runCatching {
                val effect = VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Use USAGE_NOTIFICATION instead of USAGE_TOUCH to bypass system
                    // "Haptic feedback" setting. USAGE_TOUCH respects that setting and may
                    // not vibrate if user disabled haptic feedback in system settings.
                    // USAGE_NOTIFICATION is for notifications and ignores haptic Android settings.
                    val attributes = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                        .build()
                    vibrator.vibrate(effect, attributes)
                    Log.d("Scanner", "Vibration triggered (new API - USAGE_NOTIFICATION)")
                } else {
                    vibrator.vibrate(effect)
                    Log.d("Scanner", "Vibration triggered (old API)")
                }
            }.onFailure {
                Log.w("Scanner", "Vibration failed", it)
            }
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
    val privateMode by context.getPreferenceStateDefault(PreferenceStore.PRIVATE_MODE)

    Column(
        Modifier
            .fillMaxHeight(0.3f)
            .fillMaxWidth()
            .align(Alignment.TopStart)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val copiedString = stringResource(R.string.copied_to_clipboard)
        var hideText by remember(privateMode, currentBarcode) {
            mutableStateOf(privateMode)
        }

        // Strong shadow for better visibility on any background
        val textShadow = Shadow(
            color = Color.Black,
            offset = Offset(0f, 0f),
            blurRadius = 8f
        )

        currentBarcode?.let {
            val text = AnnotatedString(
                if (hideText) "*".repeat(it.length) else it,
                SpanStyle(
                    color = Color.White,
                    shadow = textShadow,
                    fontSize = 18.sp
                ),
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
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(
                    shadow = textShadow
                )
            )
        }
    }
}

/**
 * Registers a KeyEventListener to capture volume up/down button presses.
 * adapted from: https://stackoverflow.com/a/77875685
 *
 * @param onPress called when the user presses vol up/down
 */
@Composable
private fun VolumeKeyHandler(onPress: (Int) -> Boolean) {
    val context = LocalContext.current
    val sendWithVolume by context.getPreferenceState(PreferenceStore.SEND_WITH_VOLUME)
    val triggerOnRelease by context.getPreferenceState(PreferenceStore.VOLUME_ON_RELEASE)

    if (sendWithVolume == true) {
        val view = LocalView.current

        DisposableEffect(context) {
            val keyEventDispatcher = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (event.repeatCount == 0
                        && (event.action == KeyEvent.ACTION_UP && triggerOnRelease == true
                                || event.action == KeyEvent.ACTION_DOWN && triggerOnRelease == false)
                    )
                        return@OnUnhandledKeyEventListenerCompat onPress(event.keyCode)
                    return@OnUnhandledKeyEventListenerCompat true
                }
                false
            }

            ViewCompat.addOnUnhandledKeyEventListener(view, keyEventDispatcher)

            onDispose {
                ViewCompat.removeOnUnhandledKeyEventListener(view, keyEventDispatcher)
            }
        }
    }
}

/**
 * Floating action button to send the current barcode to the connected device.
 * If the currentBarcode is null, the button is hidden.
 *
 * @param onClick callback to send text to the current device
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendToDeviceFAB(onClick: () -> Unit) {
    val controller = LocalController.current

    controller?.let {
        val colorScheme = MaterialTheme.colorScheme
        val isSending by controller.isSending.collectAsStateWithLifecycle()

        val (containerColor, contentColor) = remember(isSending) {
            if (isSending) {
                colorScheme.surface.copy(alpha = 0.12f) to
                        colorScheme.onSurface.copy(alpha = 0.32f)
            } else {
                colorScheme.primary to colorScheme.onPrimary
            }
        }

        ExtendedFloatingActionButton(
            text = {
                Text(stringResource(R.string.send_to_device))
            },
            icon = {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            },
            contentColor = contentColor,
            containerColor = containerColor,
            onClick = { if (!isSending) onClick() }
        )
    }
}

/**
 * Scanner app bar with a toggle flash button and a disconnect button.
 *
 * @param camera the camera to toggle the flash on
 * @param info the camera info for getting the flash state
 * @param currentDevice the device that is currently connected, can be null if no device is connected
 * @param transparent whether the app bar should be transparent or not
 */
@SuppressLint("MissingPermission")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ScannerAppBar(
    camera: CameraControl?,
    info: CameraInfo?,
    currentDevice: BluetoothDevice?,
    transparent: Boolean,
    keyboardDialog: DialogState
) {
    val navigation = LocalNavigation.current

    // Shadow for text in fullscreen mode
    val textShadow = Shadow(
        color = Color.Black,
        offset = Offset(0f, 0f),
        blurRadius = 8f
    )

    TopAppBar(
        title = {
            Column {
                Text(
                    stringResource(R.string.scanner),
                    style = if (transparent) {
                        MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            shadow = textShadow
                        )
                    } else {
                        MaterialTheme.typography.titleLarge
                    }
                )
                currentDevice?.let {
                    Text(
                        stringResource(R.string.connected_with, it.name ?: it.address),
                        style = if (transparent) {
                            MaterialTheme.typography.bodySmall.copy(
                                color = Color.White,
                                shadow = textShadow
                            )
                        } else {
                            MaterialTheme.typography.bodySmall
                        }
                    )
                }
            }
        },
        actions = {
            if (camera != null && info != null && info.hasFlashUnit()) {
                ToggleFlashButton(camera, info, transparent)
            }

            currentDevice?.let {
                IconButton(onClick = {
                    keyboardDialog.open()
                }, Modifier.tooltip(stringResource(R.string.manual_input))) {
                    Icon(
                        Icons.Default.Keyboard,
                        "Keyboard",
                        tint = if (transparent) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = if (transparent) Modifier.iconBackgroundModifier() else Modifier
                    )
                }
                KeyboardInputDialog(keyboardDialog)
            }

            IconButton(onClick = {
                navigation.navigate(Routes.History)
            }, Modifier.tooltip(stringResource(R.string.history))) {
                Icon(
                    Icons.Default.History,
                    "History",
                    tint = if (transparent) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = if (transparent) Modifier.iconBackgroundModifier() else Modifier
                )
            }
//            IconButton(onDisconnect, Modifier.tooltip(stringResource(R.string.disconnect))) {
//                Icon(Icons.Default.BluetoothDisabled, "Disconnect")
//            }
            Dropdown(transparent)
        },
        colors = if (transparent) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White
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
 * @param transparent whether to use transparent mode styling
 */
@Composable
fun ToggleFlashButton(camera: CameraControl?, info: CameraInfo, transparent: Boolean = false) {
    val torchState by info.torchState.observeAsState()

    IconButton(
        onClick = {
            camera?.enableTorch(
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
            },
            "Flash",
            tint = if (transparent) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = if (transparent) Modifier.iconBackgroundModifier() else Modifier
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

    controller?.let {
        val isCaps by controller.isCapsLockOn.collectAsStateWithLifecycle()

        // Only show Caps Lock warning when we have an active HID connection
        // keyboardSender is only available when HID device is connected
        val shouldShowWarning = isCaps && (controller.keyboardSender != null)

        ElevatedWarningCard(
            message = stringResource(R.string.caps_lock_activated),
            subMessage = stringResource(R.string.click_to_turn_off),
            onClick = {
                scope.launch {
                    controller.keyboardSender?.sendKey(KeyTranslator.CAPS_LOCK_KEY)
                }
            },
            visible = shouldShowWarning
        )
    }
}

/**
 * Component that shows device connection status for both HID and RFCOMM modes.
 * Priority: if no device selected (currentDevice == null) => always show "No device connected"
 * Otherwise in RFCOMM mode: show "Listening for connection from [ device ] when server is listening"
 */
@Composable
fun BoxScope.DeviceStatusIndicator() {
    val controller = LocalController.current
    val context = LocalContext.current
    val navigation = LocalNavigation.current

    controller?.let {
        val currentDevice by controller.currentDevice.collectAsStateWithLifecycle()
        val connectionMode by context.getPreferenceState(PreferenceStore.CONNECTION_MODE)
        val isRFCOMMListening by controller.isRFCOMMListeningFlow.collectAsStateWithLifecycle()

        when {
            currentDevice == null -> {
                // No device selected - show "No device connected" regardless of mode
                ElevatedWarningCard(
                    message = stringResource(R.string.no_device),
                    subMessage = stringResource(R.string.click_to_connect),
                    onClick = {
                        navigation.navigate(Routes.Devices)
                    },
                    visible = true
                )
            }

            connectionMode == ConnectionMode.RFCOMM.ordinal && isRFCOMMListening -> {
                // RFCOMM mode with device selected and server listening
                ElevatedWarningCard(
                    message = stringResource(R.string.rfcomm_listening),
                    subMessage = stringResource(
                        R.string.rfcomm_listening_from_device,
                        currentDevice?.name ?: currentDevice?.address ?: ""
                    ),
                    onClick = {
                        // Do nothing - this is just an informational message
                        // User has already selected a device and server is listening
                    },
                    visible = true
                )
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
fun BoxScope.ZoomStateInfo(camera: CameraInfo) {
    val zoomState by camera.zoomState.observeAsState()
    zoomState?.let {
        if (it.zoomRatio > 1.0f) {
            Text(
                "%.2fx".format(it.zoomRatio),
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(0f, 0f),
                        blurRadius = 8f
                    )
                )
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
fun KeyboardInputDialog(dialogState: DialogState) {
    val controller = LocalController.current
    val scope = rememberCoroutineScope { Dispatchers.IO }

    val currentText = rememberTextFieldState("")
    var sendingInProgress by remember(dialogState.openState) { mutableStateOf(false) }
    val (extraKeys, setExtraKeys) = rememberSaveable { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val enabled = !sendingInProgress && currentText.text.isNotBlank()

    ConfirmDialog(dialogState, stringResource(R.string.manual_input), enabled, onConfirm = {
        scope.launch {
            sendingInProgress = true
            controller?.sendString(currentText.text.toString(), extraKeys, "MANUAL", null, null)
        }.invokeOnCompletion {
            close()
        }
    }) {
        Column {
            OutlinedTextField(
                state = currentText,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            currentText.edit { selectAll() }
                        }
                    }
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier
                    .toggleable(
                        value = extraKeys,
                        role = Role.Checkbox,
                        onValueChange = setExtraKeys
                    )
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = extraKeys, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.include_extra_keys))
            }
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
fun KeepScreenOn() {
    val currentView = LocalView.current
    val context = LocalContext.current

    val keepScreenOn by context.getPreferenceState(PreferenceStore.KEEP_SCREEN_ON)
    keepScreenOn?.let {
        DisposableEffect(keepScreenOn) {
            currentView.keepScreenOn = it
            onDispose {
                currentView.keepScreenOn = false
            }
        }
    }
}