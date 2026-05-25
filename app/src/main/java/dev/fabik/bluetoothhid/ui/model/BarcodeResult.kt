package dev.fabik.bluetoothhid.ui.model

/**
 * Represents a fully processed barcode scan result, including all data needed for template
 * processing and transmission.
 *
 * @param text     The processed barcode value (after regex/JS transforms).
 * @param format   The barcode format index (see [dev.fabik.bluetoothhid.utils.ZXingAnalyzer]).
 * @param imageName The name of the saved scan image, or null if not saved.
 * @param regexGroups Capture groups from the filter regex (1..N). Empty if no regex or no groups.
 */
data class BarcodeResult(
    val text: String,
    val format: Int,
    val imageName: String?,
    val regexGroups: List<String> = emptyList()
)
