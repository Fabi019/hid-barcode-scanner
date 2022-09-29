package dev.fabik.bluetoothhid.ui

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Log
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toPointF
import com.google.mlkit.vision.barcode.common.Barcode
import dev.fabik.bluetoothhid.utils.BarCodeAnalyser
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.getPreferenceState

var scale = 1f
var transX = 0f
var transY = 0f
var markerRect = Rect(0f, 0f, 0f, 0f);

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
@Composable
fun CameraPreview(
    onBarCodeReady: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var lastBarCode by remember { mutableStateOf<Barcode?>(null) }
    var currentBarCode by remember { mutableStateOf<Barcode?>(null) }

    val cameraResolution by context.getPreferenceState(PrefKeys.SCAN_RESOLUTION)
    val frontCamera by context.getPreferenceState(PrefKeys.FRONT_CAMERA)

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

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
                    val sw = source.width.toFloat()
                    val sh = source.height.toFloat()

                    val vw = previewView.width.toFloat()
                    val vh = previewView.height.toFloat()

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

                    val filtered = barcodes.filter {
                        it.cornerPoints?.forEach { p ->
                            val px = p.x * scale - transX
                            val py = p.y * scale - transY
                            if (!markerRect.contains(Offset(px, py))) {
                                return@filter false
                            }
                        }
                        true
                    }

                    filtered.firstOrNull().let { barcode ->
                        barcode?.rawValue?.let { barcodeValue ->
                            if (lastBarCode == null || lastBarCode!!.rawValue != barcodeValue) {
                                Toast.makeText(context, barcodeValue, Toast.LENGTH_SHORT).show()
                                onBarCodeReady(barcodeValue)
                                lastBarCode = barcode
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
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    useCaseGroup
                )
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )

    Canvas(
        Modifier.fillMaxSize()
    ) {
        val x = this.size.width / 2
        val y = this.size.height / 2
        val length = (x * 1.5f).coerceAtMost(y * 1.5f)
        val radius = 30f

        markerRect = Rect(Offset(x - length / 2, y - length / 2), Size(length, length))

        val markerPath = Path().apply {
            addRoundRect(RoundRect(markerRect, CornerRadius(radius)))
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

        currentBarCode?.let {
            val corners = it.cornerPoints

            translate(-transX, -transY) {
                corners?.let { points ->
                    points.map { p -> p.toPointF() }.forEach { p ->
                        drawCircle(
                            color = Color.Red,
                            radius = 8f,
                            center = Offset(p.x * scale, p.y * scale)
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
fun getSupportedSizes(camera: Camera): Array<out android.util.Size>? {
    val cInfo = camera.cameraInfo
    val camChars = Camera2CameraInfo.extractCameraCharacteristics(cInfo)
    val configs: StreamConfigurationMap? =
        camChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    if (configs == null) {
        Log.i("CameraPreview", "get camera out sizes fail, config empty")
        return arrayOf()
    }
    return configs.getOutputSizes(ImageFormat.JPEG)
}