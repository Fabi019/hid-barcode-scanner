package dev.fabik.bluetoothhid.ui.model

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.TableView
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.util.fastJoinToString
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.common.Barcode
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.model.HistoryViewModel.HistoryEntry

class HistoryViewModel : ViewModel() {
    private var selectedHistory: SnapshotStateList<Int> = mutableStateListOf<Int>()

    val selectionSize by derivedStateOf { selectedHistory.size }
    val isSelecting by derivedStateOf { selectedHistory.isNotEmpty() }

    var isSearching by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    var filteredTypes = mutableStateListOf<String>()
    var filterDateStart by mutableStateOf<Long?>(null)
    var filterDateEnd by mutableStateOf<Long?>(null)

    val filteredHistory by derivedStateOf {
        historyEntries.filter { (barcode, timestamp, type) ->
            barcode.contains(searchQuery, ignoreCase = true)
                    && (filteredTypes.isEmpty() || filteredTypes.contains(parseBarcodeType(type)))
                    && (filterDateStart == null || timestamp > filterDateStart!!)
                    && (filterDateEnd == null || timestamp < filterDateEnd!!)
        }
    }

    companion object {
        private const val TAG = "History"

        private const val HISTORY_FILE = "history.csv"
        private var historyFileLoaded = false

        var historyEntries: SnapshotStateList<HistoryEntry> = mutableStateListOf<HistoryEntry>()

        fun saveHistory(context: Context) {
            runCatching {
                val file = context.filesDir.resolve(HISTORY_FILE)

                // Cleanup file if history empty
                if (historyEntries.isEmpty()) {
                    context.deleteFile(file.name)
                    return
                }

                Log.d(TAG, "Saving history to: $file")

                file.bufferedWriter().use {
                    it.write(exportEntries(historyEntries, ExportType.CSV, true))
                }
            }.onFailure {
                Log.e(TAG, "Failed to store history:", it)
            }
        }

        fun restoreHistory(context: Context) {
            // guard for only loading once
            if (historyFileLoaded) {
                return
            }

            historyFileLoaded = true

            runCatching {
                val file = context.filesDir.resolve(HISTORY_FILE)

                if (!file.exists()) {
                    Log.d(TAG, "No history file exists: $file")
                    return
                }

                Log.d(TAG, "Loading history from: $file")

                val regex = "^\"(.*)\",([0-9]+),([0-9]+)$".toRegex()
                val history = mutableListOf<HistoryEntry>()

                file.useLines {
                    val lines = it.iterator()
                    lines.next() // skip header

                    for (line in lines) {
                        regex.matchEntire(line)?.let {
                            val text = it.groupValues[1]
                            val timestamp = it.groupValues[2].toLongOrNull() ?: 0
                            val type = it.groupValues[3].toIntOrNull() ?: -1

                            history.add(HistoryEntry(text, timestamp, type))
                        }
                    }
                }

                historyEntries.addAll(history)
            }.onFailure {
                Log.e(TAG, "Error loading custom keymap:", it)
            }
        }

        fun addHistoryItem(barcode: Barcode) {
            val currentTime = System.currentTimeMillis()
            historyEntries.add(barcode.toHistoryEntry(currentTime))
        }

        fun clearHistory() {
            historyEntries.clear()
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

        fun exportEntries(
            dataToExport: List<HistoryEntry>,
            exportType: ExportType,
            numericType: Boolean = false
        ) = when (exportType) {
            ExportType.LINES -> {
                dataToExport.map {
                    it.value
                }.fastJoinToString(System.lineSeparator())
            }

            ExportType.CSV -> {
                val header = "text,timestamp,type"
                val rows = dataToExport.map {
                    val text = it.value
                    val timestamp = it.timestamp
                    val type = if (numericType) it.format else parseBarcodeType(it.format)
                    "\"$text\",$timestamp,$type"
                }
                header + System.lineSeparator() + rows.fastJoinToString(System.lineSeparator())
            }

            ExportType.JSON -> {
                val entries = dataToExport.map {
                    val text = it.value
                    val timestamp = it.timestamp
                    val type = if (numericType) it.format else parseBarcodeType(it.format)
                    """{"text":"$text","timestamp":$timestamp,"type":"$type"}"""
                }
                "[" + entries.fastJoinToString("," + System.lineSeparator()) + "]"
            }
        }
    }

    fun deleteSelectedItems() {
        historyEntries.removeIf {
            selectedHistory.contains(it.hashCode())
        }
        clearSelection()
    }

    fun clearSelection() {
        selectedHistory.clear()
    }

    fun isItemSelected(item: HistoryEntry): Boolean {
        return selectedHistory.contains(item.hashCode())
    }

    fun setItemSelected(item: HistoryEntry, selected: Boolean) {
        if (selected) {
            selectedHistory.add(item.hashCode())
        } else {
            selectedHistory.remove(item.hashCode())
        }
    }

    fun exportHistory(exportType: ExportType): String {
        return exportEntries(filteredHistory, exportType)
    }

    data class HistoryEntry(val value: String, val timestamp: Long, val format: Int)

    enum class ExportType(
        @StringRes val label: Int,
        @StringRes val description: Int,
        val icon: ImageVector
    ) {
        CSV(R.string.export_csv, R.string.export_fields, Icons.Default.TableView),
        JSON(R.string.export_json, R.string.export_fields, Icons.Default.DataObject),
        LINES(R.string.export_lines, R.string.export_lines_description, Icons.Default.TableRows)
    }
}

fun Barcode.toHistoryEntry(timestamp: Long): HistoryEntry =
    HistoryEntry(rawValue.toString(), timestamp, format)