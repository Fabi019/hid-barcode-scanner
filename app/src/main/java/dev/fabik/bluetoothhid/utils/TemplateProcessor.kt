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
 * - Basic placeholders: {DATE}, {TIME}, {DATETIME}, {SPACE}, {CR}, {LF}, {TAB}, {ENTER}
 * - Optional time formatting: {DATE[format]}, {TIME[format]}, {DATETIME[format]}, {SCAN_TIME[format]}
 * - Metadata placeholders: {CODE_TYPE}, {SCAN_TIME}, {SCAN_SOURCE}, {SCANNER_ID}
 * - Advanced CODE placeholders with flexible component ordering: {CODE}, {CODE_HEX}, {CODE_B64}, etc.
 * - Universal encoding: _HEX and _B64 can be applied to any data placeholder in any order
 * - Global encoding placeholders: {GLOBAL_HEX}, {GLOBAL_B64}, {GLOBAL_HEX_B64}, etc.
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
     * Extracts base placeholder name and encoding components from a placeholder string.
     * Supports flexible ordering: {CODE_HEX_B64}, {HEX_B64_CODE}, etc.
     *
     * @param placeholder The placeholder string (e.g., "DATE_HEX_B64")
     * @return Pair of (basePlaceholder, List of encodings in order)
     */
    private fun extractBaseAndEncodings(placeholder: String): Pair<String, List<String>> {
        val parts = placeholder.split("_")
        val encodingKeywords = setOf("HEX", "B64")

        // Find base placeholder (first non-encoding part)
        val basePlaceholder = parts.firstOrNull { !encodingKeywords.contains(it) } ?: placeholder

        // Extract encoding transformations in order of appearance
        val encodings = parts.filter { encodingKeywords.contains(it) }

        return Pair(basePlaceholder, encodings)
    }

    /**
     * Applies encoding transformations sequentially to a value.
     *
     * @param value The value to encode
     * @param encodings List of encodings to apply in order ("HEX", "B64")
     * @param hexFormat Hex formatting string ("%02X" or "%02x")
     * @return Encoded value
     */
    private fun applyEncodings(value: String, encodings: List<String>, hexFormat: String): String {
        var result = value
        for (encoding in encodings) {
            result = when (encoding) {
                "HEX" -> result.toByteArray(Charsets.UTF_8).joinToString("") { hexFormat.format(it) }
                "B64" -> Base64.encodeToString(result.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                else -> result
            }
        }
        return result
    }

    /**
     * Extracts all global encoding transformations from template.
     * Supports {GLOBAL_HEX}, {GLOBAL_B64}, {GLOBAL_HEX_B64}, etc.
     *
     * @param template The template string
     * @return List of encodings in order of appearance
     */
    private fun extractGlobalEncodings(template: String): List<String> {
        val globalRegex = Regex("\\{GLOBAL_([HEX_B64]+)\\}")
        val allMatches = globalRegex.findAll(template)

        val encodings = mutableListOf<String>()
        for (match in allMatches) {
            val parts = match.groupValues[1].split("_")
            encodings.addAll(parts.filter { it == "HEX" || it == "B64" })
        }
        return encodings
    }

    /**
     * Applies global encoding in RFCOMM mode with optional HID placeholder handling.
     *
     * RFCOMM mode only supports {TAB} and {ENTER} - other HID placeholders like
     * {F1-24}, {ESC}, {BKSP}, etc. are not converted and would be encoded as literal text.
     *
     * @param template The template string
     * @param encodings List of encodings to apply in order ("HEX", "B64")
     * @param hexFormat Hex formatting string ("%02X" or "%02x")
     * @param preserveUnsupported If true, keep HID placeholders as text; if false, remove them
     * @return Encoded template
     */
    private fun applyGlobalEncodingRFCOMM(
        template: String,
        encodings: List<String>,
        hexFormat: String,
        preserveUnsupported: Boolean
    ): String {
        // Regex for HID-only placeholders not supported in RFCOMM
        // TAB and ENTER are excluded - they ARE supported in RFCOMM
        val hidOnlyRegex = Regex("\\{(ESC|BKSP|LEFT|RIGHT|UP|DOWN|F[1-9]|F1[0-9]|F2[0-4]|[\\^+#@]+[a-zA-Z0-9]|WAIT:\\d+)\\}")

        val unsupportedPlaceholders = hidOnlyRegex.findAll(template)
            .map { it.value }
            .distinct()
            .toList()

        val processedTemplate = if (preserveUnsupported) {
            // Keep placeholders as text (will be encoded)
            if (unsupportedPlaceholders.isNotEmpty()) {
                Log.w(TAG, "RFCOMM: HID keys placeholders kept as text: ${unsupportedPlaceholders.joinToString()}")
            }
            template
        } else {
            // Remove unsupported placeholders
            if (unsupportedPlaceholders.isNotEmpty()) {
                Log.i(TAG, "RFCOMM: Removed HID keys placeholders: ${unsupportedPlaceholders.joinToString()}")
            }
            template.replace(hidOnlyRegex, "")
        }

        return applyEncodings(processedTemplate, encodings, hexFormat)
    }

    /**
     * Applies global encoding in HID mode - encodes only text, preserves key placeholders.
     *
     * In HID mode, key placeholders like {TAB}, {ENTER}, {F1-24}, etc. are NOT converted to text
     * in TemplateProcessor - they are passed to KeyTranslator for HID key conversion.
     * Global encoding must preserve these placeholders and only encode actual text content.
     *
     * @param template The template string with text and key placeholders
     * @param encodings List of encodings to apply in order ("HEX", "B64")
     * @param hexFormat Hex formatting string ("%02X" or "%02x")
     * @return Encoded template with preserved key placeholders
     */
    private fun applyGlobalEncodingHID(template: String, encodings: List<String>, hexFormat: String): String {
        // Regex to match HID key placeholders that should NOT be encoded
        // These are handled by KeyTranslator and must remain as placeholders
        val keyPlaceholderRegex = Regex("\\{(TAB|ENTER|ESC|BKSP|LEFT|RIGHT|UP|DOWN|F[1-9]|F1[0-9]|F2[0-4]|[\\^+#@]+[a-zA-Z0-9]|WAIT:\\d+)\\}")

        // Split template into text parts and key placeholders
        val parts = mutableListOf<Pair<String, Boolean>>() // (content, isKeyPlaceholder)
        var lastIndex = 0

        keyPlaceholderRegex.findAll(template).forEach { match ->
            // Add text before this key placeholder
            if (match.range.first > lastIndex) {
                val textPart = template.substring(lastIndex, match.range.first)
                parts.add(Pair(textPart, false)) // text
            }
            // Add key placeholder
            parts.add(Pair(match.value, true)) // key placeholder
            lastIndex = match.range.last + 1
        }

        // Add remaining text after last key placeholder
        if (lastIndex < template.length) {
            parts.add(Pair(template.substring(lastIndex), false))
        }

        // Encode only text parts, preserve key placeholders
        return parts.joinToString("") { (content, isKey) ->
            if (isKey) {
                content // preserve key placeholders for KeyTranslator
            } else {
                applyEncodings(content, encodings, hexFormat) // encode text
            }
        }
    }
    /**
     * Processes template string by replacing placeholders with actual data.
     *
     * This is the main entry point for template processing. It handles multiple types of placeholders:
     * 1. Basic placeholders: {DATE}, {TIME}, {DATETIME}, {SPACE}, {CR}, {LF}, {TAB}, {ENTER}
     * 2. Time placeholders with optional formatting: {DATE[format]}, {TIME[format]}, {DATETIME[format]}, {SCAN_TIME[format]}
     * 3. Metadata placeholders: {CODE_TYPE}, {SCAN_TIME}, {SCAN_SOURCE}, {SCANNER_ID}
     * 4. Dynamic CODE placeholders: {CODE}, {CODE_HEX}, {CODE_B64}, {HEX_CODE}, etc.
     * 5. Universal encoding: Any data placeholder can have _HEX, _B64 in any order
     * 6. Global encoding: {GLOBAL_HEX}, {GLOBAL_B64}, {GLOBAL_HEX_B64}, etc.
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
     * @param preserveUnsupportedPlaceholders If true (RFCOMM only), keep unsupported HID placeholders as text
     * @return Processed template with all placeholders replaced
     */
    fun processTemplate(
        data: String,
        template: String,
        mode: TemplateMode,
        from: String = "SCAN",
        scanTimestamp: Long? = null,
        scannerId: String? = null,
        barcodeType: String? = null,
        preserveUnsupportedPlaceholders: Boolean = false
    ): String {
        // Validation - ensure template contains at least one CODE placeholder
        val codeRegex = Regex("\\{[^{}]*CODE[^{}]*\\}")
        if (!codeRegex.containsMatchIn(template)) {
            Log.e(TAG, "Template must contain at least one {CODE...} placeholder")
            return data // Fallback to raw data
        }

        // Create single Date instance to ensure timestamp consistency across all formatters
        val now = Date()
        val scanDate = scanTimestamp?.let { Date(it) } ?: now

        // Hex format selection (mode-specific for backward compatibility)
        val hexFormat = when (mode) {
            TemplateMode.RFCOMM -> "%02X" // Uppercase for RFCOMM
            TemplateMode.HID -> "%02x"    // Lowercase for HID (KeyTranslator compatibility)
        }

        // Default date/time formats (mode-specific)
        val defaultDateFormat = when (mode) {
            TemplateMode.RFCOMM -> "yyyy-MM-dd"
            TemplateMode.HID -> {
                val formatter = DateFormat.getDateInstance(DateFormat.SHORT) as? SimpleDateFormat
                formatter?.toPattern() ?: "yyyy-MM-dd"
            }
        }
        val defaultTimeFormat = when (mode) {
            TemplateMode.RFCOMM -> "HH:mm:ss"
            TemplateMode.HID -> {
                val formatter = DateFormat.getTimeInstance() as? SimpleDateFormat
                formatter?.toPattern() ?: "HH:mm:ss"
            }
        }

        // Begin template processing
        var processedTemplate = template

        // Phase 1: Extract and remove global encoding placeholders
        val globalEncodings = extractGlobalEncodings(processedTemplate)
        processedTemplate = processedTemplate.replace(Regex("\\{GLOBAL_[HEX_B64]+\\}"), "")

        // Phase 2: Add mode-specific placeholders (TAB, ENTER for RFCOMM only)
        if (mode == TemplateMode.RFCOMM) {
            processedTemplate = processedTemplate.replace("{TAB}", "\t")
            processedTemplate = processedTemplate.replace("{ENTER}", "\r\n")
        }

        // Phase 3: Process all placeholders with optional format and encoding
        // Regex: {PLACEHOLDER[optional_format]_OPTIONAL_ENCODINGS}
        val placeholderRegex = Regex("\\{([A-Z_]+)(?:\\[([^\\]]+)\\])?(?:_([HEX_B64_]+))?\\}")
        processedTemplate = placeholderRegex.replace(processedTemplate) { matchResult ->
            val fullPlaceholder = matchResult.groupValues[1]  // e.g., "DATE" or "CODE_TYPE"
            val optionalFormat = matchResult.groupValues[2]   // e.g., "dd.MM.yyyy" or empty
            val encodingSuffix = matchResult.groupValues[3]   // e.g., "HEX_B64" or empty

            // Extract base placeholder and encodings
            val (basePlaceholder, baseEncodings) = extractBaseAndEncodings(fullPlaceholder)
            val suffixEncodings = if (encodingSuffix.isNotEmpty()) {
                encodingSuffix.split("_").filter { it == "HEX" || it == "B64" }
            } else emptyList()
            val allEncodings = baseEncodings + suffixEncodings

            // Get raw value for the base placeholder
            val rawValue = try {
                when (basePlaceholder) {
                    "DATE" -> {
                        val format = optionalFormat.ifEmpty { defaultDateFormat }
                        SimpleDateFormat(format, Locale.getDefault()).format(now)
                    }
                    "TIME" -> {
                        val format = optionalFormat.ifEmpty { defaultTimeFormat }
                        SimpleDateFormat(format, Locale.getDefault()).format(now)
                    }
                    "DATETIME" -> {
                        val format = optionalFormat.ifEmpty { "yyyy-MM-dd HH:mm:ss" }
                        SimpleDateFormat(format, Locale.getDefault()).format(now)
                    }
                    "SCAN" -> {
                        // Handle {SCAN_TIME} with optional format
                        if (fullPlaceholder == "SCAN_TIME" || basePlaceholder == "SCAN") {
                            val format = optionalFormat.ifEmpty { "yyyy-MM-dd HH:mm:ss.SSS" }
                            SimpleDateFormat(format, Locale.getDefault()).format(scanDate)
                        } else {
                            matchResult.value
                        }
                    }
                    "CODE" -> data
                    "SPACE" -> " "
                    "CR" -> "\r"
                    "LF" -> "\n"
                    else -> {
                        // Handle composite placeholders like CODE_TYPE, SCAN_SOURCE, etc.
                        when (fullPlaceholder) {
                            "CODE_TYPE" -> barcodeType ?: "UNKNOWN"
                            "SCAN_TIME" -> {
                                val format = optionalFormat.ifEmpty { "yyyy-MM-dd HH:mm:ss.SSS" }
                                SimpleDateFormat(format, Locale.getDefault()).format(scanDate)
                            }
                            "SCAN_SOURCE" -> from
                            "SCANNER_ID" -> scannerId ?: ""
                            else -> matchResult.value  // Unknown placeholder, keep as-is
                        }
                    }
                }
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid date format: '$optionalFormat' in placeholder ${matchResult.value}", e)
                matchResult.value  // Return original on error
            }

            // Apply encodings if any
            if (allEncodings.isNotEmpty()) {
                applyEncodings(rawValue, allEncodings, hexFormat)
            } else {
                rawValue
            }
        }

        // Phase 4: Apply global encoding if requested
        if (globalEncodings.isNotEmpty()) {
            processedTemplate = if (mode == TemplateMode.RFCOMM) {
                // RFCOMM: encode with optional HID placeholder handling
                applyGlobalEncodingRFCOMM(
                    processedTemplate,
                    globalEncodings,
                    hexFormat,
                    preserveUnsupportedPlaceholders
                )
            } else {
                // HID: encode only text parts, preserve key placeholders for KeyTranslator
                applyGlobalEncodingHID(processedTemplate, globalEncodings, hexFormat)
            }
        }

        Log.d(TAG, "Processed template for $mode: '$template' -> '$processedTemplate'")
        return processedTemplate
    }
}