package dev.fabik.bluetoothhid.utils

import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean


class BarCodeAnalyser(
    private val scanDelay: Int,
    formats: IntArray,
    private val onAnalyze: () -> Unit,
    private val onResult: (barcodes: List<Barcode>, sourceImage: Size) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        const val TAG = "BarcodeAnalyser"
    }

    private var lastAnalyzedTimeStamp = 0L
    private var isBusy = AtomicBoolean(false)

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(0, *formats)
        .build()

    private val barcodeScanner = BarcodeScanning.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        val deltaTime = currentTimestamp - lastAnalyzedTimeStamp

        // Check if the scan delay has passed and the analyzer is not currently processing an image
        if (deltaTime > scanDelay && isBusy.compareAndSet(false, true)) {
            image.image?.let { imageToAnalyze ->
                val imageToProcess =
                    InputImage.fromMediaImage(imageToAnalyze, image.imageInfo.rotationDegrees)

                barcodeScanner.process(imageToProcess)
                    .addOnSuccessListener { barcodes ->
                        onResult(barcodes, Size(image.width, image.height))
                    }
                    .addOnFailureListener { exception ->
                        Log.d(TAG, "Something went wrong $exception")
                    }
                    .addOnCompleteListener {
                        image.close()
                        isBusy.set(false)
                    }
            }
            lastAnalyzedTimeStamp = currentTimestamp
        } else {
            image.close()
        }

        onAnalyze()
    }
}
