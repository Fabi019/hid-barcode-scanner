package dev.fabik.bluetoothhid.utils

import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage


class BarCodeAnalyser(
    private val scanDelay: Int,
    private val onAnalyze: () -> Unit,
    private val scannerOptions: BarcodeScannerOptions,
    private val onResult: (barcodes: List<Barcode>, sourceImage: Size) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        const val TAG = "BarcodeAnalyser"
    }

    private var lastAnalyzedTimeStamp = 0L
    private val barcodeScanner = BarcodeScanning.getClient(scannerOptions)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastAnalyzedTimeStamp

        // Close image directly if wait time has not passed
        if (deltaTime < scanDelay) {
            Log.d(TAG, "Skipping image")
            image.close()
        } else {
            Log.d(TAG, "Processing image")
            val imageToProcess =
                InputImage.fromMediaImage(image.image!!, image.imageInfo.rotationDegrees)

            val task = barcodeScanner.process(imageToProcess)
                .addOnSuccessListener { barcodes ->
                    onResult(barcodes, Size(image.width, image.height))
                }
                .addOnFailureListener { exception ->
                    Log.d(TAG, "Something went wrong $exception")
                }
                .addOnCompleteListener {
                    lastAnalyzedTimeStamp = currentTime
                    image.close()
                    Log.d(TAG, "Image processed")
                }

            // Wait for task to complete
            Tasks.await(task)
        }

        onAnalyze()
    }
}
