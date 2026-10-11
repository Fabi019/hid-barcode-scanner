package dev.fabik.bluetoothhid.output

data class ScanEvent(
    val value: String,
    val source: String = "SCAN",
    val timestamp: Long = System.currentTimeMillis(),
    val barcodeType: String? = null,
    val imageName: String? = null,
    val regexGroups: List<String> = emptyList(),
    val withExtraKeys: Boolean = true,
)