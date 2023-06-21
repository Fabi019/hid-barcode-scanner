package dev.fabik.bluetoothhid.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.common.Barcode

class HistoryViewModel : ViewModel() {

    var isSearching by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    companion object {
        var historyEntries by mutableStateOf<List<Pair<Barcode, Long>>>(emptyList())

        fun addHistoryItem(barcode: Barcode) {
            val currentTime = System.currentTimeMillis()
            historyEntries = historyEntries + (barcode to currentTime)
        }

        fun clearHistory() {
            historyEntries = emptyList()
        }
    }

    fun parseBarcodeType(format: Int): String = when (format) {
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        else -> "UNKNOWN"
    }

}