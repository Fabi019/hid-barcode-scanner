package dev.fabik.bluetoothhid.ui


import android.annotation.SuppressLint
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.NativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import dev.fabik.bluetoothhid.LocalJsEngineService
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.model.CameraViewModel
import dev.fabik.bluetoothhid.ui.model.DevicesViewModel
import dev.fabik.bluetoothhid.utils.BarCodeAnalyser
import dev.fabik.bluetoothhid.utils.ComposableLifecycle
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.RequiresModuleInstallation
import dev.fabik.bluetoothhid.utils.getPreference
import dev.fabik.bluetoothhid.utils.rememberPreference
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun CameraArea(
    onCameraReady: (CameraController) -> Unit,
    onBarCodeReady: (String) -> Unit
) = with(viewModel<CameraViewModel>()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentOnCameraReady by rememberUpdatedState(onCameraReady)
    val currentOnBarcodeReady by rememberUpdatedState(onBarCodeReady)

    val cameraResolution by rememberPreference(PreferenceStore.SCAN_RESOLUTION)
    val useRawValue by rememberPreference(PreferenceStore.RAW_VALUE)
    val fullyInside by rememberPreference(PreferenceStore.FULL_INSIDE)
    val scanRegex by rememberPreference(PreferenceStore.SCAN_REGEX)
    val previewMode by rememberPreference(PreferenceStore.PREVIEW_PERFORMANCE_MODE)
    val autoZoom by rememberPreference(PreferenceStore.AUTO_ZOOM)
    val jsEnabled by rememberPreference(PreferenceStore.ENABLE_JS)
    val jsCode by rememberPreference(PreferenceStore.JS_CODE)
    val developerMode by rememberPreference(PreferenceStore.DEVELOPER_MODE)

    val regex = remember(scanRegex) {
        if (scanRegex.isBlank()) return@remember null
        runCatching { scanRegex.toRegex() }.getOrNull()
    }

    val scanFrequency by remember {
        context.getPreference(PreferenceStore.SCAN_FREQUENCY).map {
            when (it) {
                0 -> 0
                1 -> 100
                3 -> 1000
                else -> 500
            }
        }
    }.collectAsState(500)

    val scanFormats by remember {
        context.getPreference(PreferenceStore.CODE_TYPES).map {
            it.map { v -> 1 shl v.toInt() }.toIntArray()
        }
    }.collectAsState(intArrayOf(0))

    val previewView = remember(previewMode) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode =
                if (previewMode) PreviewView.ImplementationMode.PERFORMANCE
                else PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val jsEngineService = LocalJsEngineService.current

    val devicesViewModel: DevicesViewModel = viewModel()
    val isDeviceConnected by remember { derivedStateOf { devicesViewModel.pairedDevices.isNotEmpty() } }

    // Sets up the Barcode scanner
    val cameraReadyCB: (CameraController) -> Unit = remember(scanFormats) {
        { camera ->
            val zoomCallback: (Float) -> Boolean = cb@{ zoom ->
                if (!autoZoom) return@cb false
                camera.setZoomRatio(
                    (zoom * 0.8f).coerceAtLeast(1f)
                        .coerceAtMost(camera.zoomState.value?.maxZoomRatio ?: 1.0f)
                )
                true
            }

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(0, *scanFormats)
                .enableAllPotentialBarcodes()
                .setZoomSuggestionOptions(
                    ZoomSuggestionOptions.Builder(zoomCallback)
                        .setMaxSupportedZoomRatio(camera.zoomState.value?.maxZoomRatio ?: 1.0f)
                        .build()
                )
                .build()

            val analyzer = BarCodeAnalyser(
                scanDelay = scanFrequency,
                scannerOptions = options,
                onAnalyze = {
                    if (developerMode) {
                        updateCameraFPS()
                    }
                }
            ) { barcodes, source ->
                if (developerMode) {
                    updateDetectorFPS()
                    updateScale(source, previewView)
                }

                filterBarCodes(barcodes, fullyInside, useRawValue, regex)?.let {
                    scope.launch {
                        val value = if (jsEnabled) {
                            mapWithJs(jsEngineService, currentBarCode!!, it, jsCode)
                        } else {
                            it
                        }
                        currentOnBarcodeReady(value)
                    }
                }
            }

            camera.imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        when (cameraResolution) {
                            3 -> CameraViewModel.UHD_2160P
                            2 -> CameraViewModel.FHD_1080P
                            1 -> CameraViewModel.HD_720P
                            else -> CameraViewModel.SD_480P
                        },
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                    )
                )
                .build()
            camera.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            camera.setImageAnalysisAnalyzer(Executors.newSingleThreadExecutor(), analyzer)

            currentOnCameraReady(camera)
        }
    }

    RequiresModuleInstallation {
        Column(modifier = Modifier.fillMaxSize()) {

            CameraPreview(previewView, cameraReadyCB)
        }
    }

    OverlayCanvas()
}




@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraViewModel.CameraPreview(
    previewView: PreviewView,
    onCameraReady: (CameraController) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val currentOnCameraReady by rememberUpdatedState(onCameraReady)

    val frontCamera by rememberPreference(PreferenceStore.FRONT_CAMERA)
    val fixExposure by rememberPreference(PreferenceStore.FIX_EXPOSURE)
    val focusMode by rememberPreference(PreferenceStore.FOCUS_MODE)

    val cameraController = remember { LifecycleCameraController(context) }
    var initialized by remember { mutableStateOf(false) }

    val errorDialog = rememberDialogState()

    ComposableLifecycle(lifecycleOwner) { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                Log.d("CameraPreview", "Initializing camera")

                if (initialized) {
                    Log.w("CameraPreview", "Camera already initialized")
                    return@ComposableLifecycle
                }

                runCatching {
                    cameraController.bindToLifecycle(lifecycleOwner)

                    // Enable only the image analysis use case
                    cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                }.onFailure {
                    Log.e("CameraPreview", "Failed to bind camera", it)
                    errorDialog.open()
                    return@ComposableLifecycle
                }

                cameraController.tapToFocusState.observe(lifecycleOwner) {
                    isFocusing = when (it) {
                        CameraController.TAP_TO_FOCUS_STARTED -> true
                        else -> false
                    }

                    Log.d("CameraPreview", "Focusing: $isFocusing ($it)")
                }

                Futures.addCallback(
                    cameraController.initializationFuture,
                    object : FutureCallback<Void> {
                        override fun onSuccess(result: Void?) {
                            runCatching {
                                // Attach PreviewView after we know the camera is available.
                                previewView.controller = cameraController
                                previewView.setOnTouchListener { _, event ->
                                    if (event.action == MotionEvent.ACTION_DOWN)
                                        focusTouchPoint = Offset(event.x, event.y)
                                    false
                                }

                                // Camera is ready
                                currentOnCameraReady(cameraController)

                                initialized = true
                            }.onFailure {
                                Log.e("CameraPreview", "Failed to bind preview", it)
                                errorDialog.open()
                            }
                        }

                        override fun onFailure(t: Throwable) {
                            Log.e("CameraPreview", "Failed to initialize camera", t)
                            errorDialog.open()
                        }
                    },
                    context.mainExecutor
                )
            }

            Lifecycle.Event.ON_PAUSE -> {
                Log.d("CameraPreview", "Closing camera")
                initialized = false
                cameraController.tapToFocusState.removeObservers(lifecycleOwner)
                previewView.controller = null
                cameraController.clearImageAnalysisAnalyzer()
                cameraController.unbind()
            }

            else -> Unit
        }
    }

    LaunchedEffect(initialized, frontCamera) {
        if (initialized) {
            cameraController.cameraSelector = when {
                frontCamera && cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }
        }
    }

    LaunchedEffect(initialized, focusMode, fixExposure) {
        if (initialized) {
            cameraController.cameraControl?.let {
                setupFocusMode(it, fixExposure, focusMode)
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { previewView }
    )

    InfoDialog(
        dialogState = errorDialog, title = stringResource(R.string.camera_error),
        icon = {
            Icon(
                Icons.Filled.Error, null,
                tint = MaterialTheme.colorScheme.error
            )
        }
    ) {
        Text(stringResource(R.string.camera_error_desc))
    }
}

@Composable
fun CameraViewModel.OverlayCanvas() {
    val overlayType by rememberPreference(PreferenceStore.OVERLAY_TYPE)
    val restrictArea by rememberPreference(PreferenceStore.RESTRICT_AREA)
    val showPossible by rememberPreference(PreferenceStore.SHOW_POSSIBLE)
    // val highlightType by rememberPreferenceNull(PreferenceStore.HIGHLIGHT_TYPE)
    val developerMode by rememberPreference(PreferenceStore.DEVELOPER_MODE)

    val transition = updateTransition(targetState = isFocusing, label = "focusCircle")

    val focusCircleAlpha by transition.animateFloat(
        transitionSpec = {
            if (true isTransitioningTo false) {
                tween(100)
            } else {
                tween(50)
            }
        }, label = "focusCircleAlpha"
    ) { state -> if (state) 1f else 0f }

    val focusCircleScale by transition.animateFloat(
        transitionSpec = {
            if (true isTransitioningTo false) {
                tween(100)
            } else {
                spring(Spring.DampingRatioMediumBouncy)
            }
        }, label = "focusCircleScale"
    ) { state -> if (state) 1.0f else 1.5f }

    Canvas(Modifier.fillMaxSize()) {
        val x = this.size.width / 2
        val y = this.size.height / 2
        val landscape = this.size.width > this.size.height

        // Draws the scanner area
        if (restrictArea) {
            scanRect = when (overlayType) {
                // Rectangle optimized for barcodes
                1 -> {
                    val length = this.size.width * 0.8f
                    val height = (length * 0.45f).coerceAtMost(y * 0.8f)
                    Rect(
                        Offset(x - length / 2, y - height / 2),
                        Size(length, height)
                    )
                }

                2 -> {
                    val pos = overlayPosition
                    val size = overlaySize

                    if (pos != null && size != null)
                        Rect(
                            pos + Offset(
                                x - size.width.absoluteValue,
                                y - size.height.absoluteValue
                            ),
                            pos + Offset(
                                x + size.width.absoluteValue,
                                y + size.height.absoluteValue
                            )
                        )
                    else Rect.Zero
                }

                // Square for scanning qr codes
                else -> {
                    val length = if (landscape) this.size.height * 0.6f else this.size.width * 0.8f
                    Rect(Offset(x - length / 2, y - length / 2), Size(length, length))
                }
            }

            val markerPath = Path().apply {
                addRoundRect(RoundRect(scanRect, CornerRadius(30f)))
            }

            clipPath(markerPath, clipOp = ClipOp.Difference) {
                drawRect(color = Color.Black, topLeft = Offset.Zero, size = size, alpha = 0.5f)
            }

            drawPath(markerPath, color = Color.White, style = Stroke(5f))
        } else {
            scanRect = Rect(Offset(0f, 0f), size)
        }

        // Draw the possible barcodes (with dots)
        if (showPossible) {
            possibleBarcodes.forEach {
                val points =
                    it.cornerPoints?.map { p -> p.x * scale - transX to p.y * scale - transY }

                // Draw only the corner points
                points?.forEach { (x, y) ->
                    drawCircle(color = Color.Red, radius = 5f, center = Offset(x, y))
                }
            }
        }

        // Highlights the current barcode on screen (with a rectangle)
        currentBarCode?.let {
            // Map the bar code position to the canvas
            val points =
                it.cornerPoints?.map { p -> p.x * scale - transX to p.y * scale - transY }

            // Draw a rectangle around the barcode
            val path = Path().apply {
                points?.forEach { (x, y) ->
                    if (isEmpty)
                        moveTo(x, y)
                    lineTo(x, y)
                }
                close()
            }

            drawPath(path, color = Color.Blue, style = Stroke(5f))

            // Draw horizontal line
            points?.let {
                val leftMiddle =
                    Offset((it[0].first + it[3].first) / 2f, (it[0].second + it[3].second) / 2f)
                val rightMiddle =
                    Offset((it[1].first + it[2].first) / 2f, (it[1].second + it[2].second) / 2f)
                val hDist = (rightMiddle - leftMiddle).getDistanceSquared()

                val topMiddle =
                    Offset((it[0].first + it[1].first) / 2f, (it[0].second + it[1].second) / 2f)
                val bottomMiddle =
                    Offset((it[3].first + it[2].first) / 2f, (it[3].second + it[2].second) / 2f)
                val vDist = (bottomMiddle - topMiddle).getDistanceSquared()

                val relDiff = (hDist - vDist) / (hDist + vDist)
                if (relDiff > 0.05) {
                    drawLine(
                        Color.Red,
                        leftMiddle,
                        rightMiddle
                    )
                } else if (relDiff < -0.05) {
                    drawLine(
                        Color.Red,
                        topMiddle,
                        bottomMiddle
                    )
                }

//                drawCircle(Color.Yellow, 10f, leftMiddle)
//                drawCircle(Color.Green, 10f, rightMiddle)
//                drawCircle(Color.Cyan, 10f, topMiddle)
//                drawCircle(Color.Magenta, 10f, bottomMiddle)
            }
        }

        // Draw the focus circle if currently focusing
        focusTouchPoint?.let {
            if (focusCircleAlpha > 0f && focusCircleScale > 0f) {
                drawCircle(
                    color = Color.White,
                    radius = focusCircleScale * 100f,
                    center = it,
                    style = Stroke(5f),
                    alpha = focusCircleAlpha.coerceAtMost(1f) // should not be needed
                )
            }
        }

        // Draw debug overlay
        if (developerMode) {
            drawDebugOverlay(drawContext.canvas.nativeCanvas, this.size)
        }
    }

    // Show the adjust buttons
    if (restrictArea && overlayType == 2) {
        CustomOverlayButtons()
    }
}

@Composable
private fun CameraViewModel.CustomOverlayButtons() {
    val context = LocalContext.current
    var posOffsetX by remember {
        mutableFloatStateOf(runBlocking {
            context.getPreference(
                PreferenceStore.OVERLAY_POS_X
            ).first()
        })
    }
    var posOffsetY by remember {
        mutableFloatStateOf(runBlocking {
            context.getPreference(
                PreferenceStore.OVERLAY_POS_Y
            ).first()
        })
    }
    var sizeOffsetX by remember {
        mutableFloatStateOf(runBlocking {
            context.getPreference(
                PreferenceStore.OVERLAY_WIDTH
            ).first()
        })
    }
    var sizeOffsetY by remember {
        mutableFloatStateOf(runBlocking {
            context.getPreference(
                PreferenceStore.OVERLAY_HEIGHT
            ).first()
        })
    }

    LaunchedEffect(Unit) {
        overlayPosition = Offset(posOffsetX, posOffsetY)
        overlaySize = Size(sizeOffsetX, sizeOffsetY)
    }

    fun saveState() {
        runBlocking {
            context.setPreference(PreferenceStore.OVERLAY_POS_X, posOffsetX)
            context.setPreference(PreferenceStore.OVERLAY_POS_Y, posOffsetY)
            context.setPreference(PreferenceStore.OVERLAY_WIDTH, sizeOffsetX)
            context.setPreference(PreferenceStore.OVERLAY_HEIGHT, sizeOffsetY)
        }
    }

    fun reset() {
        posOffsetX = PreferenceStore.OVERLAY_POS_X.defaultValue
        posOffsetY = PreferenceStore.OVERLAY_POS_Y.defaultValue
        sizeOffsetX = PreferenceStore.OVERLAY_WIDTH.defaultValue
        sizeOffsetY = PreferenceStore.OVERLAY_HEIGHT.defaultValue

        overlayPosition = Offset(posOffsetX, posOffsetY)
        overlaySize = Size(sizeOffsetX, sizeOffsetY)

        saveState()
    }

    IconButton(
        onClick = { reset() },
        colors = IconButtonDefaults.iconButtonColors(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        modifier = Modifier
            .absoluteOffset {
                IntOffset(
                    (posOffsetX + sizeOffsetX).roundToInt(),
                    (posOffsetY + sizeOffsetY).roundToInt()
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = {
                    saveState()
                }) { change, dragAmount ->
                    change.consume()
                    sizeOffsetX += dragAmount.x
                    sizeOffsetY += dragAmount.y
                    overlaySize = Size(sizeOffsetX, sizeOffsetY)
                }
            }
    ) {
        Icon(Icons.Default.OpenInFull, "Modify size")
    }

    IconButton(
        onClick = { reset() },
        colors = IconButtonDefaults.iconButtonColors(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        modifier = Modifier
            .absoluteOffset {
                IntOffset(
                    (posOffsetX - sizeOffsetX).roundToInt(),
                    (posOffsetY + sizeOffsetY).roundToInt()
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = {
                    saveState()
                }) { change, dragAmount ->
                    change.consume()
                    posOffsetX += dragAmount.x
                    posOffsetY += dragAmount.y
                    overlayPosition = Offset(posOffsetX, posOffsetY)
                }
            }
    ) {
        Icon(Icons.Default.DragIndicator, "Modify position")
    }
}

fun CameraViewModel.drawDebugOverlay(canvas: NativeCanvas, size: Size) {
    // Using canvas.width/height returns fullscreen instead of the real size
    val y = size.height * 0.6f

    // Draw the camera fps
    canvas.drawText(
        "FPS: $fpsCamera, Frame latency: $latencyCamera ms",
        10f,
        y,
        Paint().apply {
            textSize = 50f
            color = Color.White.toArgb()
        }
    )

    // Draw the detector stats
    canvas.drawText(
        "Detector latency: $detectorLatency ms (Delta: ${detectorLatency - latencyCamera} ms)",
        10f,
        y + 50f,
        Paint().apply {
            textSize = 50f
            color = Color.White.toArgb()
        }
    )

    // Draw the input image size
    canvas.drawText(
        "Image size: ${lastSourceRes?.width}x${lastSourceRes?.height}",
        10f,
        y + 100f,
        Paint().apply {
            color = Color.White.toArgb()
            textSize = 50f
        }
    )

    // Draw the preview image size
    canvas.drawText(
        "Preview size: ${lastPreviewRes?.width}x${lastPreviewRes?.height}",
        10f,
        y + 150f,
        Paint().apply {
            color = Color.White.toArgb()
            textSize = 50f
        }
    )

    // Draw the custom selection
    canvas.drawText(
        "Selector size: ${overlaySize?.width?.roundToInt()}x${overlaySize?.height?.roundToInt()} at (${overlayPosition?.x?.roundToInt()}, ${overlayPosition?.y?.roundToInt()})",
        10f,
        y + 200f,
        Paint().apply {
            color = Color.White.toArgb()
            textSize = 50f
        }
    )

    // Draw the histogram
    fun drawHistogram(values: Iterable<Float>, increment: Float, paint: Paint) {
        val path = android.graphics.Path()

        values.forEachIndexed { index, value ->
            if (index == 0) {
                path.moveTo(0f, size.height - value.coerceAtMost(size.height))
            } else {
                path.lineTo(index * increment, size.height - value.coerceAtMost(size.height))
            }
        }

        canvas.drawPath(path, paint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            alpha = 100
        })
    }

    drawHistogram(
        detectorLatencies,
        size.width / (detectorLatencies.maxSize - 1),
        Paint().apply {
            color = Color.Green.toArgb()
        }
    )

    drawHistogram(
        cameraLatencies,
        size.width / (cameraLatencies.maxSize - 1),
        Paint().apply {
            color = Color.Red.toArgb()
        }
    )
}
