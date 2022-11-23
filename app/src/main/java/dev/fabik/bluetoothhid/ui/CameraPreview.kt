package dev.fabik.bluetoothhid.ui

import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.ui.model.CameraViewModel
import dev.fabik.bluetoothhid.utils.*
import kotlinx.coroutines.flow.map
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraArea(
    onCameraReady: (Camera) -> Unit,
    onBarCodeReady: (String) -> Unit
) = with(viewModel<CameraViewModel>()) {
    val context = LocalContext.current

    val cameraResolution by rememberPreferenceNull(PreferenceStore.SCAN_RESOLUTION)
    val useRawValue by rememberPreferenceDefault(PreferenceStore.RAW_VALUE)
    val fullyInside by rememberPreferenceDefault(PreferenceStore.FULL_INSIDE)

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

    RequiresModuleInstallation {
        CameraPreview(onCameraReady, previewView) {
            val barcodeAnalyser = BarCodeAnalyser(
                scanDelay = scanFrequency,
                formats = scanFormats,
                onNothing = { currentBarCode = null }
            ) { barcodes, source ->
                updateScale(source, previewView)

                filterBarCodes(barcodes, fullyInside, useRawValue)?.let {
                    onBarCodeReady(it)
                }
            }

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
                .build().apply {
                    setAnalyzer(Executors.newSingleThreadExecutor(), barcodeAnalyser)
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

    val frontCamera by rememberPreferenceDefault(PreferenceStore.FRONT_CAMERA)
    val hasFrontCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }

    val preview = remember { Preview.Builder().build() }

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

            runCatching {
                val useCaseGroup = UseCaseGroup.Builder()
                    .setViewPort(previewView.viewPort!!)
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis())
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

    val transition = updateTransition(targetState = isFocusing, label = "focusCircle")

    val focusCircleAlpha by transition.animateFloat(
        transitionSpec = {
            if (true isTransitioningTo false) {
                tween(durationMillis = 100)
            } else {
                tween(durationMillis = 50)
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
            val points =
                it.cornerPoints?.map { p -> p.x * scale - transX to p.y * scale - transY }

            val path = Path().apply {
                points?.forEach { (x, y) ->
                    if (isEmpty)
                        moveTo(x, y)
                    lineTo(x, y)
                }
                close()
            }

            drawPath(path, color = Color.Blue, style = Stroke(5f))

            points?.forEach { (x, y) ->
                drawCircle(
                    color = Color.Red,
                    radius = 8f,
                    center = Offset(x * scale - transX, y * scale - transY)
                )
            }
        }

        focusTouchPoint?.let {
            if (focusCircleAlpha != 0f) {
                drawCircle(
                    color = Color.White,
                    radius = focusCircleScale * 100f,
                    center = it,
                    style = Stroke(5f),
                    alpha = focusCircleAlpha
                )
            }
        }
    }
}
