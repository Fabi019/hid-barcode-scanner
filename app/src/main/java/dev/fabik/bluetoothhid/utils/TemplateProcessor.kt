package dev.fabik.bluetoothhid.utils

import android.util.Base64
import android.util.Log
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Centralized template processor for both RFCOMM and HID modes.
 * Handles all string template replacement in a unified way.
 */
object TemplateProcessor {
    private const val TAG = "TemplateProcessor"

    enum class TemplateMode {
        RFCOMM, // Text output for RFCOMM network transmission
        HID     // Text output for HID key conversion
    }

    /**
     * Process template string with data placeholders.
     * Mode determines which conflicting placeholders to use (TAB, ENTER).
     */
    fun processTemplate(
        data: String,
        template: String,
        mode: TemplateMode
    ): String {
        // Validation - template must contain at least one CODE placeholder
        val codeRegex = Regex("\\{CODE(_B64|_HEX)?\\}")
        if (!codeRegex.containsMatchIn(template)) {
            Log.e(TAG, "Template must contain {CODE}, {CODE_B64}, or {CODE_HEX}")
            return data // Fallback to raw data
        }

        // Generate current date and time
        // For RFCOMM: use consistent ISO format
        // For HID: use same format as KeyTranslator for backward compatibility
        val (currentDate, currentTime) = when (mode) {
            TemplateMode.RFCOMM -> {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                Pair(date, time)
            }
            TemplateMode.HID -> {
                val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
                val timeFormat = DateFormat.getTimeInstance()
                val date = dateFormat.format(Calendar.getInstance().time)
                val time = timeFormat.format(Calendar.getInstance().time)
                Pair(date, time)
            }
        }

        // Basic placeholders (mode-specific formatting for backward compatibility)
        val hexFormat = when (mode) {
            TemplateMode.RFCOMM -> "%02X" // Uppercase for RFCOMM
            TemplateMode.HID -> "%02x"    // Lowercase for HID (KeyTranslator compatibility)
        }

        val basicPlaceholders = mapOf(
            "{CODE}" to data,
            "{CODE_B64}" to Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
            "{CODE_HEX}" to data.toByteArray(Charsets.UTF_8).joinToString("") { hexFormat.format(it) },
            "{DATE}" to currentDate,
            "{TIME}" to currentTime,
            "{SPACE}" to " ",
            "{CR}" to "\r",
            "{LF}" to "\n"
        )

        // Mode-specific placeholders (handle conflicts)
        val modeSpecificPlaceholders = when (mode) {
            TemplateMode.RFCOMM -> mapOf(
                "{TAB}" to "\t",
                "{ENTER}" to "\r\n"
            )
            TemplateMode.HID -> emptyMap()
                // For HID mode, TAB and ENTER will be handled as special keys
                // in the KeyTranslator, so we don't process them as text here
        }

        // Combine all placeholders
        val allPlaceholders = basicPlaceholders + modeSpecificPlaceholders

        // Sequential replacement
        var processedTemplate = template
        allPlaceholders.forEach { (placeholder, replacement) ->
            processedTemplate = processedTemplate.replace(placeholder, replacement)
        }

        Log.d(TAG, "Processed template for $mode: '$template' -> '$processedTemplate'")
        return processedTemplate
    }
}