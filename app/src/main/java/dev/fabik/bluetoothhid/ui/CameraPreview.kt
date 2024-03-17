package dev.fabik.bluetoothhid.ui

import android.annotation.SuppressLint
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import dev.fabik.bluetoothhid.BuildConfig
import dev.fabik.bluetoothhid.ui.model.CameraViewModel
import dev.fabik.bluetoothhid.utils.BarCodeAnalyser
import dev.fabik.bluetoothhid.utils.ComposableLifecycle
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.RequiresModuleInstallation
import dev.fabik.bluetoothhid.utils.getPreference
import dev.fabik.bluetoothhid.utils.rememberPreference
import kotlinx.coroutines.flow.map
import java.util.concurrent.Executors


@Composable
fun CameraArea(
    onCameraReady: (Camera) -> Unit,
    onBarCodeReady: (String) -> Unit
) = with(viewModel<CameraViewModel>()) {
    val context = LocalContext.current

    val cameraResolution by rememberPreference(PreferenceStore.SCAN_RESOLUTION)
    val useRawValue by rememberPreference(PreferenceStore.RAW_VALUE)
    val fullyInside by rememberPreference(PreferenceStore.FULL_INSIDE)
    val scanRegex by rememberPreference(PreferenceStore.SCAN_REGEX)
    val previewMode by rememberPreference(PreferenceStore.PREVIEW_PERFORMANCE_MODE)
    val autoZoom by rememberPreference(PreferenceStore.AUTO_ZOOM)

    val regex = remember(scanRegex) {
        if (scanRegex.isBlank())
            return@remember null
        runCatching {
            scanRegex.toRegex()
        }.getOrNull()
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

    // Sets up the Barcode scanner
    val cameraReadyCB: (CameraController) -> Unit = remember(scanFormats) {
        { camera ->
            // Callback for the auto-zoom feature
            val zoomCallback: (Float) -> Boolean = cb@{ zoom ->
                if (!autoZoom) return@cb false
                // Reduce the zoom ratio by 20% to avoid the camera being too close
                camera.setZoomRatio((zoom * 0.8f).coerceAtLeast(1f))
                true
            }

            // Scanner options
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(0, *scanFormats)
                .enableAllPotentialBarcodes()
                .setZoomSuggestionOptions(
                    ZoomSuggestionOptions.Builder(zoomCallback)
                        .setMaxSupportedZoomRatio(camera.zoomState.value!!.maxZoomRatio)
                        .build()
                )
                .build()

            // Setup the camera analysis
            val analyzer = BarCodeAnalyser(
                scanDelay = scanFrequency,
                scannerOptions = options,
                onAnalyze = { updateCameraFPS() }
            ) { barcodes, source ->
                updateDetectorFPS()
                updateScale(source, previewView)

                filterBarCodes(barcodes, fullyInside, useRawValue, regex)?.let {
                    onBarCodeReady(it)
                }
            }

            // Set the image analysis use case
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
        }
    }

    RequiresModuleInstallation {
        CameraPreview(previewView, cameraReadyCB)
    }

    OverlayCanvas()
}

@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraViewModel.CameraPreview(
    previewView: PreviewView,
    onCameraReady: (CameraController) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val frontCamera by rememberPreference(PreferenceStore.FRONT_CAMERA)
    val fixExposure by rememberPreference(PreferenceStore.FIX_EXPOSURE)
    val focusMode by rememberPreference(PreferenceStore.FOCUS_MODE)

    val cameraController = remember { LifecycleCameraController(context) }
    var initialized by remember { mutableStateOf(false) }

    ComposableLifecycle(lifecycleOwner) { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                cameraController.bindToLifecycle(lifecycleOwner)

                cameraController.tapToFocusState.observe(lifecycleOwner) {
                    isFocusing = when (it) {
                        CameraController.TAP_TO_FOCUS_STARTED -> true
                        else -> false
                    }

                    Log.d("CameraPreview", "Focusing: $isFocusing ($it)")
                }

                cameraController.initializationFuture.addListener({
                    initialized = true

                    // Enable only the image analysis use case
                    cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)

                    // Attach PreviewView after we know the camera is available.
                    previewView.controller = cameraController
                    previewView.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN)
                            focusTouchPoint = Offset(event.x, event.y)
                        false
                    }

                    // Camera is ready
                    onCameraReady(cameraController)
                }, ContextCompat.getMainExecutor(context))
            }

            Lifecycle.Event.ON_PAUSE -> {
                cameraController.clearImageAnalysisAnalyzer()
                cameraController.unbind()
                cameraController.tapToFocusState.removeObservers(lifecycleOwner)
                initialized = false
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
            Camera2CameraControl.from(cameraController.cameraControl!!)
                .setCaptureRequestOptions(setupFocusMode(fixExposure, focusMode))
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { previewView }
    )
}

@Composable
fun CameraViewModel.OverlayCanvas() {
    val overlayType by rememberPreference(PreferenceStore.OVERLAY_TYPE)
    val restrictArea by rememberPreference(PreferenceStore.RESTRICT_AREA)
    val showPossible by rememberPreference(PreferenceStore.SHOW_POSSIBLE)
    // val highlightType by rememberPreferenceNull(PreferenceStore.HIGHLIGHT_TYPE)

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
        if (BuildConfig.DEBUG) {
            drawDebugOverlay(drawContext.canvas.nativeCanvas)
        }
    }
}

fun CameraViewModel.drawDebugOverlay(canvas: NativeCanvas) {
    val y = canvas.height * 0.6f

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
        "Detector latency: $detectorLatency ms",
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
}
