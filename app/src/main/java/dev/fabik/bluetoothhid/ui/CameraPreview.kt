package dev.fabik.bluetoothhid.ui

import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toPointF
import com.google.mlkit.vision.barcode.common.Barcode
import dev.fabik.bluetoothhid.utils.BarCodeAnalyser

var scale = 1f
var transX = 0f
var transY = 0f
var markerRect = Rect(0f, 0f, 0f, 0f);

@Composable
fun CameraPreview() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var currentBarCode by remember { mutableStateOf<Barcode?>(null) }

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

                val barcodeAnalyser = BarCodeAnalyser(onNothing = {
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
                            if ((currentBarCode?.rawValue ?: "") != barcodeValue) {
                                Toast.makeText(context, barcodeValue, Toast.LENGTH_SHORT).show()
                            }
                        }
                        currentBarCode = barcode
                    }
                }
                val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
                    .setOutputImageRotationEnabled(true)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                        it.setAnalyzer(executor, barcodeAnalyser)
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
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
                scale(scale, pivot = Offset.Zero) {
                    corners?.let { points ->
                        points.map { p -> p.toPointF() }.forEach { p ->
                            drawCircle(
                                color = Color.Red,
                                radius = 4f,
                                center = Offset(p.x, p.y)
                            )
                        }
                    }
                }
            }

        }
    }
}