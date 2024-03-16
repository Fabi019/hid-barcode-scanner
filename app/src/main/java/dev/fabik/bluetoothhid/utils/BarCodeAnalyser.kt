package dev.fabik.bluetoothhid.utils

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.utils.TransformUtils.getRectToRect
import androidx.camera.core.impl.utils.TransformUtils.rotateRect
import androidx.camera.view.CameraController
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

    @SuppressLint("RestrictedApi")
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
            //val imageToProcess =
            //    InputImage.fromMediaImage(image.image!!, image.imageInfo.rotationDegrees)

            val analysisToTarget = Matrix()
            val sensorToTarget = sensorTransform ?: run {
                Log.d(TAG, "Transform is null.")
                imageProxy.close()
                return
            }

            val sensorToAnalysis =
                Matrix(imageProxy.imageInfo.sensorToBufferTransformMatrix)

            val sourceRect = RectF(
                0f, 0f, imageProxy.width.toFloat(),
                imageProxy.height.toFloat()
            )
            val bufferRect = rotateRect(
                sourceRect,
                imageProxy.imageInfo.rotationDegrees
            )
            val analysisToMlKitRotation = getRectToRect(
                sourceRect, bufferRect,
                imageProxy.imageInfo.rotationDegrees
            )

            sensorToAnalysis.postConcat(analysisToMlKitRotation)
            sensorToAnalysis.invert(analysisToTarget)
            analysisToTarget.postConcat(sensorToTarget)

            val task = barcodeScanner.process(
                imageProxy.image!!,
                imageProxy.imageInfo.rotationDegrees,
                analysisToTarget
            )
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

            runCatching {
                // Wait for task to complete
                //Tasks.await(task)
            }.onFailure { e ->
                Log.e(TAG, "Error waiting for task", e)
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
