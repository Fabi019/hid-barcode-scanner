package dev.fabik.bluetoothhid.utils

import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode


class BarCodeAnalyser(
    private val scanDelay: Int,
    private val onAnalyze: () -> Unit,
    scannerOptions: BarcodeScannerOptions,
    private val onResult: (barcodes: List<Barcode>, sourceImage: Size) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        const val TAG = "BarcodeAnalyser"
    }

    private var lastAnalyzedTimeStamp = 0L
    private val barcodeScanner = BarcodeScanning.getClient(scannerOptions)
    private var sensorTransform: Matrix? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastAnalyzedTimeStamp

        // Close image directly if wait time has not passed
        if (deltaTime < scanDelay) {
            Log.d(TAG, "Skipping image")
            imageProxy.close()
        } else {
            Log.d(TAG, "Processing image")

            val analysisToTarget = Matrix()
            val sensorToTarget = sensorTransform ?: run {
                Log.d(TAG, "Transform is null.")
                imageProxy.close()
                return
            }

            val sensorToAnalysis =
                Matrix(imageProxy.imageInfo.sensorToBufferTransformMatrix)

            val sourceRect = RectF(
                0f, 0f,
                imageProxy.width.toFloat(),
                imageProxy.height.toFloat()
            )

            val bufferRect = RectF(sourceRect)
            val rotation = (imageProxy.imageInfo.rotationDegrees % 360 + 360) % 360
            if (rotation == 90 || rotation == 270) {
                bufferRect.set(
                    0f, 0f,
                    bufferRect.bottom - bufferRect.top,
                    bufferRect.right - bufferRect.left
                )
            }

            val normalizedRect = RectF(-1f, -1f, 1f, 1f)

            // Map source to normalized space.
            val analysisToMlKitRotation = Matrix().apply {
                setRectToRect(sourceRect, normalizedRect, Matrix.ScaleToFit.FILL)
                postRotate(rotation.toFloat())
            }

            // Restore the normalized space to target's coordinates.
            val normalizedToBuffer = Matrix().apply {
                setRectToRect(normalizedRect, bufferRect, Matrix.ScaleToFit.FILL)
            }

            analysisToMlKitRotation.postConcat(normalizedToBuffer)

            sensorToAnalysis.postConcat(analysisToMlKitRotation)
            sensorToAnalysis.invert(analysisToTarget)
            analysisToTarget.postConcat(sensorToTarget)

            runCatching {
                val task = barcodeScanner.process(imageProxy.image!!, rotation, analysisToTarget)
                    .addOnSuccessListener { barcodes ->
                        onResult(barcodes, Size(imageProxy.width, imageProxy.height))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error processing image", exception)
                    }
                    .addOnCompleteListener {
                        lastAnalyzedTimeStamp = currentTime
                        imageProxy.close()
                        Log.d(TAG, "Image processed")
                    }
                Tasks.await(task)
            }.onFailure { e ->
                Log.e(TAG, "Error processing image", e)
            }
        }

        onAnalyze()
    }

    override fun getTargetCoordinateSystem(): Int {
        return CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
    }

    override fun updateTransform(matrix: Matrix?) {
        sensorTransform = matrix
    }
}
