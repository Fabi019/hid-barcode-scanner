package dev.fabik.bluetoothhid.utils

import android.util.Base64
import android.util.Log
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized template processor for both RFCOMM and HID modes.
 *
 * This processor handles dynamic template replacement with support for:
 * - Basic placeholders: {DATE}, {TIME}, {SPACE}, {CR}, {LF}, {TAB}, {ENTER}
 * - Advanced CODE placeholders with flexible component ordering: {CODE}, {CODE_XML}, {JSON_CODE_HEX}, etc.
 * - XML/JSON serialization with timestamps, barcode types, and scanner identification
 * - Base64 and hexadecimal encoding transformations
 * - Mode-specific formatting for RFCOMM vs HID output
 *
 * The processor uses a unified data structure approach where XML and JSON formats
 * are generated from the same data definition, ensuring consistency and reducing
 * maintenance overhead.
 *
 * Data Structure Design:
 * The serialization system is designed to support both single barcode transmission
 * and batch operations (e.g., history export, multiple barcode sending):
 * - Single mode: SerializationData with 1 BarcodeEntry (current real-time usage)
 * - Batch mode: SerializationData with multiple BarcodeEntry items (future feature? or for history entries export using RFCOMM)
 *
 * This architecture enables future enhancements such as:
 * - Exporting entire scan history as structured XML/JSON files
 * - Sending multiple selected barcodes from history in one transmission
 * - Batch processing for analytics or external system integration
 */
object TemplateProcessor {
    private const val TAG = "TemplateProcessor"

    /**
     * Template processing modes that determine output formatting and placeholder behavior.
     */
    enum class TemplateMode {
        RFCOMM, // Text output for RFCOMM network transmission (supports all placeholders)
        HID     // Text output for HID key conversion (TAB/ENTER handled by KeyTranslator)
    }


    /**
     * Parses and processes CODE template components with flexible ordering support.
     *
     * Template Format: {CODE[_XML|_JSON][_HEX|_B64]...}
     * Components can appear in any order: {XML_CODE_HEX} = {CODE_XML_HEX} = {HEX_XML_CODE}
     *
     * Processing Rules:
     * - CODE component is mandatory
     * - XML and JSON are mutually exclusive (validation error if both present)
     * - HEX and B64 encoding can be combined, processed in order of appearance
     * - Raw data → XML/JSON serialization → encoding transformations
     *
     * @param data The barcode content to process
     * @param codeTemplate The template string (e.g., "{CODE_XML_HEX}")
     * @param hexFormat Hex formatting string ("%02X" or "%02x")
     * @param timestamp Current timestamp for serialization
     * @param from Data source identifier
     * @param scanTimestamp Original scan timestamp (optional)
     * @param scannerId Scanner hardware identifier (optional)
     * @param barcodeType Barcode type string (e.g., "QR_CODE", "CODE_128")
     * @return Processed string according to template specification
     */
    private fun parseAndProcessCode(data: String,
                                    codeTemplate: String,
                                    hexFormat: String,
                                    timestamp: String,
                                    from: String = "SCAN",
                                    scanTimestamp: String? = null,
                                    scannerId: String? = null,
                                    barcodeType: String? = null): String {
        // Extract the content between { and }
        val content = codeTemplate.removePrefix("{").removeSuffix("}")
        val parts = content.split("_")

        // Validate CODE component presence
        if (!parts.contains("CODE")) {
            Log.e(TAG, "CODE component is required in template: $codeTemplate")
            return data
        }

        // Check for mutually exclusive format specifications
        val hasXml = parts.contains("XML")
        val hasJson = parts.contains("JSON")
        if (hasXml && hasJson) {
            Log.e(TAG, "XML and JSON are mutually exclusive in template: $codeTemplate")
            return data
        }

        // Start processing pipeline
        var result = data

        // Use provided barcode type or fallback to UNKNOWN
        val typeValue = barcodeType ?: "UNKNOWN"

        // Step 1: Apply format transformation (XML/JSON serialization or keep plain text)
        if (hasXml || hasJson) {
            val serializationData = Serializer.buildSingleBarcodeData(
                barcodeValue = result,
                currentTimestamp = timestamp,
                barcodeType = typeValue,
                protocolVersion = "1.0",
                dataSource = from,
                originalScanTimestamp = scanTimestamp,
                hardwareID = scannerId
            )
            result = if (hasXml) {
                Serializer.toXml(serializationData)
            } else {
                Serializer.toJson(serializationData)
            }
        }

        // Step 2: Apply encoding transformations in order of appearance
        val encodingParts = parts.filter { it == "HEX" || it == "B64" }
        for (encoding in encodingParts) {
            when (encoding) {
                "HEX" -> {
                    // Convert to hexadecimal representation
                    result = result.toByteArray(Charsets.UTF_8).joinToString("") { hexFormat.format(it) }
                }
                "B64" -> {
                    // Convert to Base64 encoding
                    result = Base64.encodeToString(result.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                }
            }
        }

        return result
    }

    /**
     * Processes template string by replacing placeholders with actual data.
     *
     * This is the main entry point for template processing. It handles two types of placeholders:
     * 1. Basic placeholders: {DATE}, {TIME}, {SPACE}, {CR}, {LF}, {TAB}, {ENTER}
     * 2. Dynamic CODE placeholders: {CODE}, {CODE_XML}, {JSON_B64_CODE}, etc.
     *
     * The processing mode determines formatting differences:
     * - RFCOMM: All placeholders supported, ISO date format, uppercase hex
     * - HID: TAB/ENTER excluded (handled by KeyTranslator), localized dates, lowercase hex
     *
     * @param data The barcode content to be processed
     * @param template Template string containing placeholders
     * @param mode Processing mode (RFCOMM or HID)
     * @param from Data source identifier (SCAN, HISTORY, MANUAL)
     * @param scanTimestamp Original scan timestamp in milliseconds (optional)
     * @param scannerId Scanner hardware identifier (optional)
     * @param barcodeType Barcode type string (e.g., "QR_CODE", "CODE_128", "UNKNOWN")
     * @return Processed template with all placeholders replaced
     */
    fun processTemplate(
        data: String,
        template: String,
        mode: TemplateMode,
        from: String = "SCAN",
        scanTimestamp: Long? = null,
        scannerId: String? = null,
        barcodeType: String? = null
    ): String {
        // Validation - ensure template contains at least one CODE placeholder
        val codeRegex = Regex("\\{[^{}]*CODE[^{}]*\\}")
        if (!codeRegex.containsMatchIn(template)) {
            Log.e(TAG, "Template must contain at least one {CODE...} placeholder")
            return data // Fallback to raw data
        }

        // Create single Date instance to ensure timestamp consistency across all formatters
        val now = Date()

        // Generate current date and time with mode-specific formatting
        // RFCOMM uses ISO format for consistency, HID uses system locale for compatibility
        val (currentDate, currentTime) = when (mode) {
            TemplateMode.RFCOMM -> {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
                Pair(date, time)
            }
            TemplateMode.HID -> {
                val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
                val timeFormat = DateFormat.getTimeInstance()
                val date = dateFormat.format(now)
                val time = timeFormat.format(now)
                Pair(date, time)
            }
        }

        // Hex format selection (mode-specific for backward compatibility)
        val hexFormat = when (mode) {
            TemplateMode.RFCOMM -> "%02X" // Uppercase for RFCOMM
            TemplateMode.HID -> "%02x"    // Lowercase for HID (KeyTranslator compatibility)
        }

        // Generate high-precision timestamp for XML/JSON serialization
        val isoTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(now)

        // Convert scanTimestamp to string format if provided
        val scanTimestampString = scanTimestamp?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(it))
        }

        // Define basic placeholder mappings (common to both modes)
        val basicPlaceholders = mutableMapOf(
            "{DATE}" to currentDate,
            "{TIME}" to currentTime,
            "{SPACE}" to " ",
            "{CR}" to "\r",
            "{LF}" to "\n"
        )

        // Add mode-specific placeholders (handle mode conflicts)
        val modeSpecificPlaceholders = when (mode) {
            TemplateMode.RFCOMM -> mapOf(
                "{TAB}" to "\t",
                "{ENTER}" to "\r\n"
            )
            TemplateMode.HID -> emptyMap()
                // For HID mode, TAB and ENTER are handled as special keys
                // in the KeyTranslator, so we don't process them as text here
        }

        // Combine all static placeholders
        val allPlaceholders = basicPlaceholders + modeSpecificPlaceholders

        // Begin template processing
        var processedTemplate = template

        // Phase 1: Replace all basic (static) placeholders
        allPlaceholders.forEach { (placeholder, replacement) ->
            processedTemplate = processedTemplate.replace(placeholder, replacement)
        }

        // Phase 2: Process dynamic CODE placeholders using regex matching
        val dynamicCodeRegex = Regex("\\{[^{}]*CODE[^{}]*\\}")
        processedTemplate = dynamicCodeRegex.replace(processedTemplate) { matchResult ->
            val codeTemplate = matchResult.value
            parseAndProcessCode(data, codeTemplate, hexFormat, isoTimestamp, from, scanTimestampString, scannerId, barcodeType)
        }

        Log.d(TAG, "Processed template for $mode: '$template' -> '$processedTemplate'")
        return processedTemplate
    }
}