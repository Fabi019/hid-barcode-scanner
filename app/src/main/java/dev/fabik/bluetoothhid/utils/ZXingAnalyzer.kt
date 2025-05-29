package dev.fabik.bluetoothhid.utils

import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader

class ZXingAnalyzer(
    initialOptions: BarcodeReader.Options = BarcodeReader.Options(),
    var scanDelay: Int,
    private val onAnalyze: () -> Unit,
    private val onResult: (barcodes: List<BarcodeReader.Result>, sourceImage: Size) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "ZXingAnalyzer"

        // Order based on the mlkit order to preserve indexes
        // Needs to be kept in sync with the "code_types_values" string array resource
        private val FORMATS = arrayOf(
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
            return FORMATS.getOrNull(index ?: return BarcodeReader.Format.NONE)?.first
                ?: BarcodeReader.Format.NONE
        }

        fun format2Index(format: BarcodeReader.Format): Int {
            return FORMATS.indexOfFirst {
                it.first == format
            }
        }

        fun format2String(format: BarcodeReader.Format): String {
            return FORMATS.firstOrNull {
                it.first == format
            }?.second ?: "UNKNOWN"
        }
    }

    private val reader = BarcodeReader(initialOptions)
    private var lastAnalyzedTimeStamp = 0L

    fun setOptions(options: BarcodeReader.Options) {
        reader.options = options
    }

    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastAnalyzedTimeStamp

        // Close image directly if wait time has not passed
        if (deltaTime < scanDelay) {
            image.close()
        } else {
            lastAnalyzedTimeStamp = currentTime

            runCatching {
                val results = image.use {
                    reader.read(image)
                }
                onResult(results, Size(image.width, image.height))
            }.onFailure {
                Log.e(TAG, "Error analyzing image!", it)
            }
        }

        onAnalyze()
    }

}