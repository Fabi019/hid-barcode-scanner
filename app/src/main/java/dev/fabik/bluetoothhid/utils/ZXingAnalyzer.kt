package dev.fabik.bluetoothhid.utils

import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader
import zxingcpp.BarcodeReader.Options

class ZXingAnalyzer(
    options: Options = Options(),
    var scanDelay: Int,
    private val onAnalyze: () -> Unit,
    private val onResult: (barcodes: List<BarcodeReader.Result>, sourceImage: Size) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        fun index2Format(index: Int?): BarcodeReader.Format = when (index) {
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

        fun format2Index(format: BarcodeReader.Format): Int = when (format) {
            BarcodeReader.Format.CODE_128 -> 0
            BarcodeReader.Format.CODE_39 -> 1
            BarcodeReader.Format.CODE_93 -> 2
            BarcodeReader.Format.CODABAR -> 3
            BarcodeReader.Format.DATA_MATRIX -> 4
            BarcodeReader.Format.EAN_13 -> 5
            BarcodeReader.Format.EAN_8 -> 6
            BarcodeReader.Format.ITF -> 7
            BarcodeReader.Format.QR_CODE -> 8
            BarcodeReader.Format.UPC_A -> 9
            BarcodeReader.Format.UPC_E -> 10
            BarcodeReader.Format.PDF_417 -> 11
            BarcodeReader.Format.AZTEC -> 12
            else -> -1
        }

        fun format2String(format: BarcodeReader.Format): String = when (format) {
            BarcodeReader.Format.NONE -> "NONE"
            BarcodeReader.Format.AZTEC -> "AZTEC"
            BarcodeReader.Format.CODABAR -> "CODABAR"
            BarcodeReader.Format.CODE_39 -> "CODE_39"
            BarcodeReader.Format.CODE_93 -> "CODE_93"
            BarcodeReader.Format.CODE_128 -> "CODE_128"
            BarcodeReader.Format.DATA_BAR -> "DATA_BAR"
            BarcodeReader.Format.DATA_BAR_EXPANDED -> "DATA_BAR_EXPANDED"
            BarcodeReader.Format.DATA_BAR_LIMITED -> "DATA_BAR_LIMITED"
            BarcodeReader.Format.DATA_MATRIX -> "DATA_MATRIX"
            BarcodeReader.Format.DX_FILM_EDGE -> "DX_FILM_EDGE"
            BarcodeReader.Format.EAN_8 -> "EAN_8"
            BarcodeReader.Format.EAN_13 -> "EAN_13"
            BarcodeReader.Format.ITF -> "ITF"
            BarcodeReader.Format.MAXICODE -> "MAXICODE"
            BarcodeReader.Format.PDF_417 -> "PDF417"
            BarcodeReader.Format.QR_CODE -> "QR_CODE"
            BarcodeReader.Format.MICRO_QR_CODE -> "MICRO_QR_CODE"
            BarcodeReader.Format.RMQR_CODE -> "RMQR_CODE"
            BarcodeReader.Format.UPC_A -> "UPC_A"
            BarcodeReader.Format.UPC_E -> "UPC_E"
            else -> "UNKNOWN"
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