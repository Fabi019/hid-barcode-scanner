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
 * - Metadata placeholders: {CODE_TYPE}, {SCAN_TIME}, {SCAN_SOURCE}, {SCANNER_ID}
 * - Advanced CODE placeholders with flexible component ordering: {CODE}, {CODE_HEX}, {CODE_B64}, etc.
 * - Base64 and hexadecimal encoding transformations (per-CODE and global)
 * - Global encoding placeholders: {GLOBAL_HEX}, {GLOBAL_B64} for encoding entire output
 * - Mode-specific formatting for RFCOMM vs HID output
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
     * Template Format: {CODE[_HEX|_B64]...}
     * Components can appear in any order: {CODE_HEX} = {HEX_CODE} = {CODE_B64_HEX}
     *
     * Processing Rules:
     * - CODE component is mandatory
     * - HEX and B64 encoding can be combined, processed in order of appearance
     * - Processing order: Raw data → encoding transformations
     *
     * @param data The barcode content to process
     * @param codeTemplate The template string (e.g., "{CODE_HEX}")
     * @param hexFormat Hex formatting string ("%02X" or "%02x")
     * @return Processed string according to template specification
     */
    private fun parseAndProcessCode(data: String,
                                    codeTemplate: String,
                                    hexFormat: String): String {
        // Extract the content between { and }
        val content = codeTemplate.removePrefix("{").removeSuffix("}")
        val parts = content.split("_")

        // Validate CODE component presence
        if (!parts.contains("CODE")) {
            Log.e(TAG, "CODE component is required in template: $codeTemplate")
            return data
        }

        // Start processing pipeline
        var result = data

        // Apply encoding transformations in order of appearance
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
     * This is the main entry point for template processing. It handles multiple types of placeholders:
     * 1. Basic placeholders: {DATE}, {TIME}, {SPACE}, {CR}, {LF}, {TAB}, {ENTER}
     * 2. Metadata placeholders: {CODE_TYPE}, {SCAN_TIME}, {SCAN_SOURCE}, {SCANNER_ID}
     * 3. Dynamic CODE placeholders: {CODE}, {CODE_HEX}, {CODE_B64}, {HEX_CODE}, etc.
     * 4. Global encoding: {GLOBAL_HEX}, {GLOBAL_B64} (applied to entire output at the end)
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

        // Generate high-precision timestamp for SCAN_TIME placeholder
        val isoTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(now)

        // Convert scanTimestamp to string format if provided
        val scanTimestampString = scanTimestamp?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(it))
        } ?: isoTimestamp

        // Define basic placeholder mappings (common to both modes)
        val basicPlaceholders = mutableMapOf(
            "{DATE}" to currentDate,
            "{TIME}" to currentTime,
            "{SPACE}" to " ",
            "{CR}" to "\r",
            "{LF}" to "\n"
        )

        // Add metadata placeholders
        val metadataPlaceholders = mapOf(
            "{CODE_TYPE}" to (barcodeType ?: "UNKNOWN"),
            "{SCAN_TIME}" to scanTimestampString,
            "{SCAN_SOURCE}" to from,
            "{SCANNER_ID}" to (scannerId ?: "")
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
        val allPlaceholders = basicPlaceholders + metadataPlaceholders + modeSpecificPlaceholders

        // Check for global encoding flags
        val hasGlobalHex = template.contains("{GLOBAL_HEX}")
        val hasGlobalB64 = template.contains("{GLOBAL_B64}")

        // Begin template processing
        var processedTemplate = template

        // Remove global encoding placeholders (they are applied at the end)
        processedTemplate = processedTemplate.replace("{GLOBAL_HEX}", "")
        processedTemplate = processedTemplate.replace("{GLOBAL_B64}", "")

        // Phase 1: Replace all basic (static) placeholders
        allPlaceholders.forEach { (placeholder, replacement) ->
            processedTemplate = processedTemplate.replace(placeholder, replacement)
        }

        // Phase 2: Process dynamic CODE placeholders using regex matching
        val dynamicCodeRegex = Regex("\\{[^{}]*CODE[^{}]*\\}")
        processedTemplate = dynamicCodeRegex.replace(processedTemplate) { matchResult ->
            val codeTemplate = matchResult.value
            parseAndProcessCode(data, codeTemplate, hexFormat)
        }

        // Phase 3: Apply global encoding if requested (in order: HEX then B64)
        if (hasGlobalHex) {
            processedTemplate = processedTemplate.toByteArray(Charsets.UTF_8).joinToString("") { hexFormat.format(it) }
        }
        if (hasGlobalB64) {
            processedTemplate = Base64.encodeToString(processedTemplate.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }

        Log.d(TAG, "Processed template for $mode: '$template' -> '$processedTemplate'")
        return processedTemplate
    }
}