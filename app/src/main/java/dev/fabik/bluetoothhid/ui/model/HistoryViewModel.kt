package dev.fabik.bluetoothhid.ui.model

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.common.Barcode

class HistoryViewModel : ViewModel() {

    val selectionSize by derivedStateOf { selectedHistory.size }
    val isSelecting by derivedStateOf { selectedHistory.isNotEmpty() }
    var isSearching by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    companion object {
        var historyEntries by mutableStateOf<List<Pair<Barcode, Long>>>(emptyList())
        private var selectedHistory: Set<Int> by mutableStateOf(emptySet())

        fun addHistoryItem(barcode: Barcode) {
            val currentTime = System.currentTimeMillis()
            historyEntries = historyEntries + (barcode to currentTime)
        }

        fun clearHistory() {
            historyEntries = emptyList()
        }

        fun isItemSelected(item: Barcode): Boolean {
            return selectedHistory.contains(item.hashCode())
        }

        fun setItemSelected(item: Barcode, selected: Boolean) {
            selectedHistory = if (selected) {
                selectedHistory + item.hashCode()
            } else {
                selectedHistory - item.hashCode()
            }
        }

        fun clearSelection() {
            selectedHistory = emptySet()
        }

        fun selectAll() {
            selectedHistory = historyEntries.indices.toSet()
        }

        fun deleteSelected() {
            historyEntries = historyEntries.filterIndexed { index, _ ->
                !selectedHistory.contains(index)
            }
            clearSelection()
        }

        fun exportHistory(exportType: ExportType) {
            // TODO: Add export logic based on exportType
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

    enum class ExportType {
        TEXT,
        CSV,
        JSON,
    }
}