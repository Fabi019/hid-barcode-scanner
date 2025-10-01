package dev.fabik.bluetoothhid.utils

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
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
 */
object TemplateProcessor {
    private const val TAG = "TemplateProcessor"

    // Cache for barcode types - maps scanned code value to its detected type (QR_CODE, CODE_128, etc.)
    private val barcodeTypeCache = mutableMapOf<String, String>()

    /**
     * Caches the detected barcode type for later use in template processing.
     * This allows templates to include the actual barcode type instead of generic "BARCODE".
     *
     * @param value The scanned barcode value
     * @param type The detected barcode type (e.g., "QR_CODE", "CODE_128", "DATA_MATRIX")
     */
    fun cacheBarcodeType(value: String, type: String) {
        barcodeTypeCache[value] = type
        Log.d(TAG, "Cached barcode type: '$value' -> '$type'")
    }

    /**
     * Retrieves cached barcode type or returns "UNKNOWN" if not found.
     *
     * @param value The barcode value to look up
     * @return The cached barcode type or "UNKNOWN"
     */
    private fun getCachedBarcodeType(value: String): String {
        return barcodeTypeCache[value] ?: "UNKNOWN"
    }

    /**
     * Template processing modes that determine output formatting and placeholder behavior.
     */
    enum class TemplateMode {
        RFCOMM, // Text output for RFCOMM network transmission (supports all placeholders)
        HID     // Text output for HID key conversion (TAB/ENTER handled by KeyTranslator)
    }

    /**
     * Common data structure containing all information needed for XML/JSON serialization.
     * This unified approach ensures both formats contain identical data.
     *
     * @param value The barcode content
     * @param tsScan Original scan timestamp (if sending from scanner/manual = tsSend, if sent from history = timestamp from history)
     * @param tsSend Current transmission timestamp (when data is being sent)
     * @param type Barcode type (QR_CODE, CODE_128, etc.)
     * @param source Data source (SCAN, HISTORY, MANUAL)
     * @param version Protocol version
     * @param hwID Hardware identifier (Bluetooth device name) [MAC address are randomized in Android 10+ and cannot be used]
     */
    private data class SerializationData(
        val value: String,
        val tsScan: String,
        val tsSend: String,
        val type: String,
        val source: String,
        val version: String,
        val hwID: String?
    )

    /**
     * Builds the common serialization data structure from input parameters.
     * Handles dual timestamp logic where scan time can differ from send time.
     *
     * @param data The barcode content to serialize
     * @param ts Current timestamp for send time
     * @param codeType Detected barcode type
     * @param version Protocol version
     * @param src Data source identifier
     * @param scanTs Original scan timestamp (null uses current timestamp)
     * @param hwId Scanner hardware identifier
     * @return Complete serialization data structure
     */
    private fun buildSerializationData(data: String, ts: String, codeType: String = "UNKNOWN", version: String = "1.0", src: String = "SCAN", scanTs: String? = null, hwId: String? = null): SerializationData {
        val tsScan = scanTs ?: ts   // Use original scan timestamp or fallback to current
        val tsSend = ts             // Always use current time for send timestamp

        return SerializationData(
            value = data,
            tsScan = tsScan,
            tsSend = tsSend,
            type = codeType,
            source = src,
            version = version,
            hwID = hwId
        )
    }

    /**
     * Universal data structure that can generate both XML and JSON formats.
     * This approach ensures both formats have identical structure and content.
     */
    private sealed class DataNode {
        /**
         * Container node with attributes and child nodes (like XML elements or JSON objects).
         *
         * @param name Element/object name
         * @param attributes Key-value pairs (XML attributes, JSON properties)
         * @param children Child nodes
         */
        data class Container(
            val name: String,
            val attributes: Map<String, String> = emptyMap(),
            val children: List<DataNode> = emptyList()
        ) : DataNode()

        /**
         * Leaf node containing string content (XML text content, JSON string values).
         *
         * @param name Element/property name
         * @param content String content
         */
        data class Value(
            val name: String,
            val content: String
        ) : DataNode()
    }

    /**
     * Builds the btscanner header attributes (protocol metadata).
     * Contains version information and optional hardware identification.
     *
     * @param serializationData Source data containing version and hardware ID
     * @return Map of header attributes
     */
    private fun buildBtscannerHeader(serializationData: SerializationData): Map<String, String> {
        val attributes = mutableMapOf("version" to serializationData.version)
        serializationData.hwID?.let { attributes["hwID"] = it }
        return attributes
    }

    /**
     * Builds the barcode data structure (actual scan information).
     * Contains the scanned value, timestamps, type, and source information.
     *
     * @param serializationData Source data containing all barcode information
     * @return List of data nodes representing barcode fields
     */
    private fun buildBarcodeData(serializationData: SerializationData): List<DataNode.Value> {
        return listOf(
            DataNode.Value("value", serializationData.value),      // The actual barcode content
            DataNode.Value("ts_scan", serializationData.tsScan),   // When barcode was scanned
            DataNode.Value("ts_send", serializationData.tsSend),   // When data is being transmitted
            DataNode.Value("type", serializationData.type),        // Barcode type (QR_CODE, etc.)
            DataNode.Value("source", serializationData.source)     // Data source (SCAN/HISTORY/MANUAL)
        )
    }

    /**
     * Builds the complete hierarchical data structure for serialization.
     * Combines header metadata with barcode data in a standardized format.
     *
     * Structure:
     * - btscanner (container with version/hwID attributes)
     *   - barcode (container)
     *     - value, ts_scan, ts_send, type, from (values)
     *
     * @param serializationData All data needed for serialization
     * @return Complete data structure ready for XML/JSON conversion
     */
    private fun buildDataStructure(serializationData: SerializationData): DataNode.Container {
        // Header: btscanner attributes (metadata)
        val btscannerHeader = buildBtscannerHeader(serializationData)

        // Data: barcode content (actual scan information)
        val barcodeData = buildBarcodeData(serializationData)
        val barcodeNode = DataNode.Container("barcode", children = barcodeData)

        // Complete structure: header + data
        return DataNode.Container("btscanner", btscannerHeader, listOf(barcodeNode))
    }

    /**
     * Converts DataNode structure to formatted XML string with proper indentation.
     * Handles XML character escaping and attribute formatting automatically.
     *
     * @param node The data node to convert
     * @param indent Current indentation level for formatting
     * @return Formatted XML string
     */
    private fun dataNodeToXml(node: DataNode, indent: String = ""): String {
        return when (node) {
            is DataNode.Value -> {
                // Escape XML special characters in content
                val escapedContent = node.content
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;")
                "$indent<${node.name}>$escapedContent</${node.name}>"
            }
            is DataNode.Container -> {
                // Build attribute string
                val attrs = if (node.attributes.isNotEmpty()) {
                    " " + node.attributes.map { (k, v) -> """$k="$v"""" }.joinToString(" ")
                } else ""

                // Generate container with or without children
                if (node.children.isNotEmpty()) {
                    val childrenXml = node.children.joinToString("\n") {
                        dataNodeToXml(it, "$indent    ")
                    }
                    "$indent<${node.name}$attrs>\n$childrenXml\n$indent</${node.name}>"
                } else {
                    "$indent<${node.name}$attrs/>"
                }
            }
        }
    }

    /**
     * Converts DataNode structure to JSON representation.
     * Container nodes become JSON objects, Value nodes become string properties.
     *
     * @param node The data node to convert
     * @return JSON representation (JSONObject for containers, String for values)
     */
    private fun dataNodeToJson(node: DataNode): Any {
        return when (node) {
            is DataNode.Value -> node.content
            is DataNode.Container -> {
                val jsonObj = JSONObject()

                // Add container attributes as top-level properties
                node.attributes.forEach { (key, value) ->
                    jsonObj.put(key, value)
                }

                // Add children as properties or nested objects
                node.children.forEach { child ->
                    when (child) {
                        is DataNode.Value -> jsonObj.put(child.name, child.content)
                        is DataNode.Container -> jsonObj.put(child.name, dataNodeToJson(child))
                    }
                }

                jsonObj
            }
        }
    }

    /**
     * Serializes data to XML format using the unified data structure.
     * Produces well-formed XML with declaration and proper formatting.
     *
     * @param serializationData Complete data to serialize
     * @return Formatted XML string with declaration
     */
    private fun serializeToXml(serializationData: SerializationData): String {
        val dataStructure = buildDataStructure(serializationData)
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + dataNodeToXml(dataStructure)
    }

    /**
     * Serializes data to JSON format using the unified data structure.
     * Produces compact JSON representation with consistent structure.
     *
     * @param serializationData Complete data to serialize
     * @return JSON string representation
     */
    private fun serializeToJson(serializationData: SerializationData): String {
        val dataStructure = buildDataStructure(serializationData)
        val rootObject = JSONObject()
        rootObject.put(dataStructure.name, dataNodeToJson(dataStructure))
        return rootObject.toString()
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
     * @return Processed string according to template specification
     */
    private fun parseAndProcessCode(data: String, codeTemplate: String, hexFormat: String, timestamp: String, from: String = "SCAN", scanTimestamp: String? = null, scannerId: String? = null): String {
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

        // Get cached barcode type for this data
        val codeType = getCachedBarcodeType(data)

        // Step 1: Apply format transformation (XML/JSON serialization or keep plain text)
        if (hasXml || hasJson) {
            val serializationData = buildSerializationData(
                data = result,
                ts = timestamp,
                codeType = codeType,
                version = "1.0",
                src = from,
                scanTs = scanTimestamp,
                hwId = scannerId
            )
            result = if (hasXml) {
                serializeToXml(serializationData)
            } else {
                serializeToJson(serializationData)
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
     * @return Processed template with all placeholders replaced
     */
    fun processTemplate(
        data: String,
        template: String,
        mode: TemplateMode,
        from: String = "SCAN",
        scanTimestamp: Long? = null,
        scannerId: String? = null
    ): String {
        // Validation - ensure template contains at least one CODE placeholder
        val codeRegex = Regex("\\{[^{}]*CODE[^{}]*\\}")
        if (!codeRegex.containsMatchIn(template)) {
            Log.e(TAG, "Template must contain at least one {CODE...} placeholder")
            return data // Fallback to raw data
        }

        // Generate current date and time with mode-specific formatting
        // RFCOMM uses ISO format for consistency, HID uses system locale for compatibility
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

        // Hex format selection (mode-specific for backward compatibility)
        val hexFormat = when (mode) {
            TemplateMode.RFCOMM -> "%02X" // Uppercase for RFCOMM
            TemplateMode.HID -> "%02x"    // Lowercase for HID (KeyTranslator compatibility)
        }

        // Generate high-precision timestamp for XML/JSON serialization
        val isoTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())

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
            parseAndProcessCode(data, codeTemplate, hexFormat, isoTimestamp, from, scanTimestampString, scannerId)
        }

        Log.d(TAG, "Processed template for $mode: '$template' -> '$processedTemplate'")
        return processedTemplate
    }
}