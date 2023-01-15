package dev.fabik.bluetoothhid.utils

import android.annotation.SuppressLint
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("UnsafeOptInUsageError")
class BarCodeAnalyser(
    private val scanDelay: Int,
    private val formats: IntArray,
    private val onNothing: () -> Unit,
    private val onAnalyze: () -> Unit,
    private val onBarcodeDetected: (barcodes: List<Barcode>, sourceImage: Size) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        const val TAG = "BarcodeAnalyser"
    }

    private var lastAnalyzedTimeStamp = 0L
    private var isBusy = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        val deltaTime = currentTimestamp - lastAnalyzedTimeStamp
        if (deltaTime > scanDelay && isBusy.compareAndSet(false, true)) {
            image.image?.let { imageToAnalyze ->
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(0, *formats)
                    .build()
                val barcodeScanner = BarcodeScanning.getClient(options)
                val imageToProcess =
                    InputImage.fromMediaImage(imageToAnalyze, image.imageInfo.rotationDegrees)

                barcodeScanner.process(imageToProcess)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            onBarcodeDetected(barcodes, Size(image.width, image.height))
                        } else {
                            onNothing()
                        }
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