package dev.fabik.bluetoothhid.utils

import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.collection.arrayMapOf
import zxingcpp.BarcodeReader
import zxingcpp.BarcodeReader.Options

class ZXingAnalyzer(
    options: Options = Options(),
    var scanDelay: Int,
    private val onAnalyze: () -> Unit,
    private val onResult: (barcodes: List<BarcodeReader.Result>, sourceImage: Size) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        // Order based on the mlkit order to preserve indexes
        private val FORMATS = arrayMapOf(
            BarcodeReader.Format.CODE_128 to "CODE_128",
            BarcodeReader.Format.CODE_39 to "CODE_39",
            BarcodeReader.Format.CODE_93 to "CODE_93",
            BarcodeReader.Format.CODABAR to "CODABAR",
            BarcodeReader.Format.DATA_MATRIX to "DATA_MATRIX",
            BarcodeReader.Format.EAN_13 to "EAN_13",
            BarcodeReader.Format.EAN_8 to "EAN_8",
            BarcodeReader.Format.ITF to "ITF",
            BarcodeReader.Format.QR_CODE to "QR_CODE",
            BarcodeReader.Format.UPC_A to "UPC_A",
            BarcodeReader.Format.UPC_E to "UPC_E",
            BarcodeReader.Format.PDF_417 to "PDF417",
            BarcodeReader.Format.AZTEC to "AZTEC",
            BarcodeReader.Format.DATA_BAR to "DATA_BAR",
            BarcodeReader.Format.DATA_BAR_EXPANDED to "DATA_BAR_EXPANDED",
            BarcodeReader.Format.DATA_BAR_LIMITED to "DATA_BAR_LIMITED",
            BarcodeReader.Format.DX_FILM_EDGE to "DX_FILM_EDGE",
            BarcodeReader.Format.MAXICODE to "MAXICODE",
            BarcodeReader.Format.MICRO_QR_CODE to "MICRO_QR_CODE",
            BarcodeReader.Format.RMQR_CODE to "RMQR_CODE"
        )

        fun index2Format(index: Int?): BarcodeReader.Format {
            if ((index ?: return BarcodeReader.Format.NONE) >= FORMATS.keys.size)
                return BarcodeReader.Format.NONE
            return FORMATS.keyAt(index)
        }

        fun index2String(index: Int?): String {
            if ((index ?: return "UNKNOWN") >= FORMATS.values.size)
                return "UNKNOWN"
            return FORMATS.valueAt(index)
        }

        fun format2Index(format: BarcodeReader.Format): Int {
            return FORMATS.indexOfKey(format)
        }

        fun format2String(format: BarcodeReader.Format): String {
            return FORMATS.get(format) ?: "UNKNOWN"
        }
    }

    private val reader = BarcodeReader(options)
    private var lastAnalyzedTimeStamp = 0L

    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastAnalyzedTimeStamp

        // Close image directly if wait time has not passed
        if (deltaTime < scanDelay) {
            image.close()
        } else {
            lastAnalyzedTimeStamp = currentTime
            val results = image.use {
                reader.read(image)
            }
            onResult(results, Size(image.width, image.height))
        }

        onAnalyze()
    }
}