package dev.fabik.bluetoothhid.ui

import android.view.MotionEvent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import dev.fabik.bluetoothhid.utils.BarCodeAnalyser
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.rememberPreferenceDefault
import dev.fabik.bluetoothhid.utils.rememberPreferenceNull
import kotlinx.coroutines.launch

var sourceRes: android.util.Size? = null
var scale = 1f
var transX = 0f
var transY = 0f
var scanRect = Rect.Zero

fun updateScale(sw: Float, sh: Float, vw: Float, vh: Float) {
    val viewAspectRatio = vw / vh
    val sourceAspectRatio = sw / sh

    if (sourceAspectRatio > viewAspectRatio) {
        scale = vh / sh
        transX = (sw * scale - vw) / 2
        transY = 0f
    } else {
        scale = vw / sw
        transX = 0f
        transY = (sh * scale - vh) / 2
    }
}

@Composable
fun CameraPreview(
    onCameraReady: (Camera) -> Unit,
    onBarCodeReady: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var lastBarCodeValue by remember { mutableStateOf<String?>(null) }
    var currentBarCode by remember { mutableStateOf<Barcode?>(null) }

    val circleAlpha = remember { Animatable(0f) }
    var focusTouchPoint by remember { mutableStateOf<Offset?>(null) }

    val cameraResolution by rememberPreferenceNull(PrefKeys.SCAN_RESOLUTION)
    val frontCamera by rememberPreferenceNull(PrefKeys.FRONT_CAMERA)
    val restrictArea by rememberPreferenceNull(PrefKeys.RESTRICT_AREA)
    val useRawValue by rememberPreferenceDefault(PrefKeys.RAW_VALUE)
    val fullyInside by rememberPreferenceDefault(PrefKeys.FULL_INSIDE)

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val barcodeAnalyser = BarCodeAnalyser(context, onNothing = {
                    currentBarCode = null
                }) { barcodes, source ->
                    if (sourceRes != source) {
                        updateScale(
                            source.width.toFloat(),
                            source.height.toFloat(),
                            previewView.width.toFloat(),
                            previewView.height.toFloat()
                        )
                        sourceRes = source
                    }

                    val filtered = barcodes.filter {
                        it.cornerPoints?.map { p ->
                            Offset(p.x * scale - transX, p.y * scale - transY)
                        }?.forEach { o ->
                            if (fullyInside) {
                                if (!scanRect.contains(o)) {
                                    return@filter false
                                }
                            } else {
                                if (scanRect.contains(o)) {
                                    return@filter true
                                }
                            }
                        }
                        fullyInside
                    }

                    filtered.firstOrNull().let { barcode ->
                        val value = if (useRawValue) {
                            barcode?.rawValue
                        } else {
                            barcode?.displayValue
                        }
                        value?.let { barcodeValue ->
                            if (lastBarCodeValue != barcodeValue) {
                                onBarCodeReady(barcodeValue)
                                lastBarCodeValue = barcodeValue
                            }
                        }
                        currentBarCode = barcode
                    }
                }
                val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(
                        when (cameraResolution) {
                            2 -> android.util.Size(1080, 1440)
                            1 -> android.util.Size(720, 960)
                            else -> android.util.Size(480, 640)
                        }
                    )
                    .setOutputImageRotationEnabled(true)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                        it.setAnalyzer(executor, barcodeAnalyser)
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(
                        when (frontCamera) {
                            true -> CameraSelector.LENS_FACING_FRONT
                            else -> CameraSelector.LENS_FACING_BACK
                        }
                    )
                    .build()

                val useCaseGroup = UseCaseGroup.Builder()
                    .setViewPort(previewView.viewPort!!)
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis)
                    .build()

                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    useCaseGroup
                )

                onCameraReady(camera)

                previewView.setOnTouchListener { view, event ->
                    return@setOnTouchListener when (event.action) {
                        MotionEvent.ACTION_DOWN -> true
                        MotionEvent.ACTION_UP -> {
                            if (focusTouchPoint == null) {
                                focusTouchPoint = Offset(event.x, event.y)
                                scope.launch {
                                    circleAlpha.animateTo(1f, tween(100))
                                }
                                val factory = DisplayOrientedMeteringPointFactory(
                                    previewView.display,
                                    camera.cameraInfo,
                                    previewView.width.toFloat(),
                                    previewView.height.toFloat()
                                )
                                val focusPoint = factory.createPoint(event.x, event.y)
                                camera.cameraControl.startFocusAndMetering(
                                    FocusMeteringAction.Builder(
                                        focusPoint,
                                        FocusMeteringAction.FLAG_AF
                                    ).apply {
                                        disableAutoCancel()
                                    }.build()
                                ).addListener({
                                    scope.launch {
                                        circleAlpha.animateTo(0f, tween(100))
                                        focusTouchPoint = null
                                    }
                                }, executor)
                            }
                            view.performClick()
                        }
                        else -> false
                    }
                }
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )

    val overlayType by rememberPreferenceNull(PrefKeys.OVERLAY_TYPE)

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
                drawRect(
                    color = Color.Black,
                    topLeft = Offset.Zero,
                    size = size,
                    alpha = 0.5f
                )
            }

            drawPath(markerPath, color = Color.White, style = Stroke(5f))
        } else {
            scanRect = Rect(Offset(0f, 0f), size)
        }

        currentBarCode?.let {
            it.cornerPoints?.forEach { p ->
                drawCircle(
                    color = Color.Red,
                    radius = 8f,
                    center = Offset(p.x * scale - transX, p.y * scale - transY)
                )
            }
        }

        focusTouchPoint?.let {
            drawCircle(
                color = Color.White,
                radius = 80f,
                center = it,
                style = Stroke(5f),
                alpha = circleAlpha.value
            )
        }
    }
}
