package dev.fabik.bluetoothhid.ui

import android.content.pm.PackageManager
import android.graphics.Paint
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Rational
import android.view.ViewGroup
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.BuildConfig
import dev.fabik.bluetoothhid.ui.model.CameraViewModel
import dev.fabik.bluetoothhid.utils.*
import kotlinx.coroutines.flow.map
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
fun CameraArea(
    onCameraReady: (Camera) -> Unit,
    onBarCodeReady: (String) -> Unit
) = with(viewModel<CameraViewModel>()) {
    val context = LocalContext.current

    val cameraResolution by rememberPreference(PreferenceStore.SCAN_RESOLUTION)
    val useRawValue by rememberPreference(PreferenceStore.RAW_VALUE)
    val fullyInside by rememberPreference(PreferenceStore.FULL_INSIDE)
    val autoFocus by rememberPreference(PreferenceStore.AUTO_FOCUS)

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

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val barcodeAnalyzer = remember(scanFrequency, scanFormats) {
        BarCodeAnalyser(
            scanDelay = scanFrequency,
            formats = scanFormats,
            onAnalyze = { updateCameraFPS() }
        ) { barcodes, source ->
            updateDetectorFPS()
            updateScale(source, previewView)

            filterBarCodes(barcodes, fullyInside, useRawValue)?.let {
                onBarCodeReady(it)
            }
        }
    }

    RequiresModuleInstallation {
        CameraPreview(onCameraReady, previewView) {
            ImageAnalysis.Builder()
                .setTargetResolution(
                    when (cameraResolution) {
                        2 -> CameraViewModel.FHD_1080P
                        1 -> CameraViewModel.HD_720P
                        else -> CameraViewModel.SD_480P
                    }
                )
                .setOutputImageRotationEnabled(true)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .apply {
                    val ext = Camera2Interop.Extender(this)
                    if (!autoFocus) {
                        // Set the focus mode to auto in order to disable continuous focusing
                        ext.setCaptureRequestOption(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_AUTO
                        )
                    }
                }
                .build().apply {
                    setAnalyzer(Executors.newSingleThreadExecutor(), barcodeAnalyzer)
                }
        }
    }

    OverlayCanvas()
}

@Composable
fun CameraViewModel.CameraPreview(
    onCameraReady: (Camera) -> Unit,
    previewView: PreviewView,
    imageAnalysis: () -> ImageAnalysis,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val frontCamera by rememberPreference(PreferenceStore.FRONT_CAMERA)
    val hasFrontCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }

    val preview = remember {
        Preview.Builder().build()
    }

    val cameraProvider by produceState<ProcessCameraProvider?>(null) {
        value = suspendCoroutine { cont ->
            ProcessCameraProvider.getInstance(context).apply {
                addListener({
                    cont.resume(get())
                }, ContextCompat.getMainExecutor(context))
            }
        }
    }

    val camera = remember(cameraProvider, imageAnalysis) {
        cameraProvider?.let {
            val cameraSelector = when {
                frontCamera && hasFrontCamera -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }

            val viewPort = ViewPort.Builder(
                Rational(previewView.width, previewView.height),
                previewView.display.rotation
            ).build()

            runCatching {
                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis())
                    .setViewPort(viewPort)
                    .build()

                it.unbindAll()
                it.bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup).also {
                    onCameraReady(it)
                }
            }.onFailure {
                Log.e("CameraPreview", "Use case binding failed", it)
            }.getOrNull()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(camera, previewView) {
                camera?.let {
                    focusOnTap(it.cameraControl, previewView)
                }
            }
            .pointerInput(camera) {
                camera?.let {
                    zoomGesture(it.cameraInfo, it.cameraControl)
                }
            },
        factory = {
            previewView.also {
                preview.setSurfaceProvider(it.surfaceProvider)
            }
        }
    )
}

@Composable
fun CameraViewModel.OverlayCanvas() {
    val overlayType by rememberPreferenceNull(PreferenceStore.OVERLAY_TYPE)
    val restrictArea by rememberPreferenceNull(PreferenceStore.RESTRICT_AREA)
    val highlightType by rememberPreferenceNull(PreferenceStore.HIGHLIGHT_TYPE)

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
        val length = (x * 1.5f).coerceAtMost(y * 1.5f)
        val radius = 30f


        if (restrictArea == true) {
            scanRect = when (overlayType) {
                1 -> Rect(Offset(x - length / 2, y - length / 4), Size(length, length / 2))
                else -> Rect(Offset(x - length / 2, y - length / 2), Size(length, length))
            }

            val markerPath = Path().apply {
                addRoundRect(RoundRect(scanRect, CornerRadius(radius)))
            }

            clipPath(markerPath, clipOp = ClipOp.Difference) {
                drawRect(color = Color.Black, topLeft = Offset.Zero, size = size, alpha = 0.5f)
            }

            drawPath(markerPath, color = Color.White, style = Stroke(5f))
        } else {
            scanRect = Rect(Offset(0f, 0f), size)
        }

        currentBarCode?.let {
            // Map the bar code position to the canvas
            val points =
                it.cornerPoints?.map { p -> p.x * scale - transX to p.y * scale - transY }

            when (highlightType) {
                1 -> {
                    // Draw only the corner points
                    points?.forEach { (x, y) ->
                        drawCircle(color = Color.Red, radius = 10f, center = Offset(x, y))
                    }
                }
                else -> {
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
                    alpha = focusCircleAlpha
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
    val y = canvas.height * 0.75f

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
