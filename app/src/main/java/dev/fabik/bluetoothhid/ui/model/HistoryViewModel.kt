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
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastJoinToString
import androidx.lifecycle.ViewModel
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.model.HistoryViewModel.HistoryEntry
import kotlin.math.log2

class HistoryViewModel : ViewModel() {
    private var selectedHistory: SnapshotStateList<Int> = mutableStateListOf<Int>()

    val selectionSize by derivedStateOf { selectedHistory.size }
    val isSelecting by derivedStateOf { selectedHistory.isNotEmpty() }

    var isSearching by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    var filteredTypes = mutableStateListOf<Int>()
    var filterDateStart by mutableStateOf<Long?>(null)
    var filterDateEnd by mutableStateOf<Long?>(null)

    val filteredHistory by derivedStateOf {
        historyEntries.fastFilter { (barcode, timestamp, type) ->
            barcode.contains(searchQuery, ignoreCase = true)
                    && (filteredTypes.isEmpty() || filteredTypes.contains(type))
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
                    it.write(exportEntries(historyEntries, ExportType.CSV, numericType = true))
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

                    // Check if old barcode format used
                    val migrate = lines.next() == "text,timestamp,type"

                    for (line in lines) {
                        regex.matchEntire(line)?.let {
                            val text = it.groupValues[1]
                            val timestamp = it.groupValues[2].toLongOrNull() ?: 0

                            var type = it.groupValues[3].toIntOrNull() ?: -1
                            if (migrate) {
                                type = log2(type.toFloat()).toInt()
                            }

                            history.add(HistoryEntry(text, timestamp, type))
                        }
                    }
                }

                historyEntries.addAll(history)
            }.onFailure {
                Log.e(TAG, "Error loading custom keymap:", it)
            }
        }

        fun addHistoryItem(value: String, format: Int) {
            val currentTime = System.currentTimeMillis()
            historyEntries.add(HistoryEntry(value, currentTime, format))
        }

        fun clearHistory() {
            historyEntries.clear()
        }

        fun exportEntries(
            dataToExport: List<HistoryEntry>,
            exportType: ExportType,
            numericType: Boolean = false,
            formatNames: Array<String>? = null,
        ) = when (exportType) {
            ExportType.LINES -> {
                dataToExport.map {
                    it.value
                }.fastJoinToString(System.lineSeparator())
            }

            ExportType.CSV -> {
                val header = if (numericType) "text,timestamp,format" else "text,timestamp,type"
                val rows = dataToExport.map {
                    val text = it.value
                    val timestamp = it.timestamp
                    val type =
                        if (numericType) it.format else formatNames?.elementAtOrNull(it.format)
                            ?: "UNKNOWN"
                    "\"$text\",$timestamp,$type"
                }
                header + System.lineSeparator() + rows.fastJoinToString(System.lineSeparator())
            }

            ExportType.JSON -> {
                val entries = dataToExport.map {
                    val text = it.value
                    val timestamp = it.timestamp
                    val type =
                        if (numericType) it.format else formatNames?.elementAtOrNull(it.format)
                            ?: "UNKNOWN"
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

    fun exportHistory(
        exportType: ExportType,
        deduplicate: Boolean,
        formatNames: Array<String>
    ): String {
        var history = filteredHistory
        if (isSelecting) {
            history = history.fastFilter {
                selectedHistory.contains(it.hashCode())
            }
        }
        if (deduplicate) {
            history = history.fastDistinctBy { it.value }
        }
        return exportEntries(history, exportType, formatNames = formatNames)
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