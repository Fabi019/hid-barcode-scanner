package dev.fabik.bluetoothhid.utils

import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import zxingcpp.BarcodeReader
import zxingcpp.BarcodeReader.Options

class ZXingAnalyzer(
    options: Options = Options(),
    private val scanDelay: Int,
    private val onAnalyze: () -> Unit,
    private val onResult: (barcodes: List<BarcodeReader.Result>, sourceImage: Size) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        const val TAG = "ZXingAnalyzer"

        fun convertCodeTypes(types: List<Int?>): Set<BarcodeReader.Format> = types.map {
            when (it) {
                0 -> BarcodeReader.Format.CODE_128
                1 -> BarcodeReader.Format.CODE_39
                2 -> BarcodeReader.Format.CODE_93
                3 -> BarcodeReader.Format.CODABAR
                4 -> BarcodeReader.Format.DATA_MATRIX
                5 -> BarcodeReader.Format.EAN_13
                6 -> BarcodeReader.Format.EAN_8
                7 -> BarcodeReader.Format.ITF
                8 -> BarcodeReader.Format.QR_CODE
                9 -> BarcodeReader.Format.UPC_A
                10 -> BarcodeReader.Format.UPC_E
                11 -> BarcodeReader.Format.PDF_417
                12 -> BarcodeReader.Format.AZTEC
                else -> BarcodeReader.Format.NONE
            }
        }.toSet()
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