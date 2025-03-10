package dev.fabik.bluetoothhid.utils

import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import zxingcpp.BarcodeReader
import zxingcpp.BarcodeReader.Options

class ZXingAnalyzer(
    private val scanDelay: Int,
    private val onAnalyze: () -> Unit,
    options: Options = Options(),
    private val onResult: (barcodes: List<BarcodeReader.Result>, sourceImage: Size) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        const val TAG = "ZXingAnalyzer"
    }

    private val reader = BarcodeReader(options)

    private var lastAnalyzedTimeStamp = 0L

    @OptIn(TransformExperimental::class)
    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastAnalyzedTimeStamp

        // Close image directly if wait time has not passed
        if (deltaTime < scanDelay) {
            image.close()
        } else {
            val results = image.use {
                reader.read(image)
            }
            onResult(results, Size(image.width, image.height))
        }

        onAnalyze()
    }
}