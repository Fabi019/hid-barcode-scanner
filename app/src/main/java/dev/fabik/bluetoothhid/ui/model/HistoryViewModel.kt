package dev.fabik.bluetoothhid.ui.model

import androidx.annotation.StringRes
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastJoinToString
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.common.Barcode
import dev.fabik.bluetoothhid.R

class HistoryViewModel : ViewModel() {
    private var selectedHistory: Set<Int> by mutableStateOf(emptySet())

    val selectionSize by derivedStateOf { selectedHistory.size }
    val isSelecting by derivedStateOf { selectedHistory.isNotEmpty() }
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

    fun exportHistory(exportType: ExportType): String {
        val dataToExport = if (isSearching) {
            historyEntries.filter {
                it.first.rawValue.toString().contains(searchQuery, ignoreCase = true)
            }
        } else {
            historyEntries
        }
        return when (exportType) {
            ExportType.LINES -> {
                dataToExport.map {
                    it.first.rawValue.toString()
                }.fastJoinToString(System.lineSeparator())
            }

            ExportType.CSV -> {
                val header = "text,timestamp,type"
                val rows = dataToExport.map {
                    val text = it.first.rawValue.toString()
                    val timestamp = it.second
                    val type = parseBarcodeType(it.first.format)
                    "\"$text\",$timestamp,$type"
                }
                header + System.lineSeparator() + rows.fastJoinToString(System.lineSeparator())
            }

            ExportType.JSON -> {
                val entries = dataToExport.map {
                    val text = it.first.rawValue.toString()
                    val timestamp = it.second
                    val type = parseBarcodeType(it.first.format)
                    """"{"text":"$text","timestamp":$timestamp,"type":"$type"}""""
                }
                "{\"entries\":[" + entries.fastJoinToString(System.lineSeparator()) + "]}"
            }
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

    enum class ExportType(@StringRes val label: Int, @StringRes val description: Int) {
        CSV(R.string.export_csv, R.string.export_fields),
        JSON(R.string.export_json, R.string.export_fields),
        LINES(R.string.export_lines, R.string.export_lines_description),
    }
}