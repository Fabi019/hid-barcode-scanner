package dev.fabik.bluetoothhid.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * Barcode data serialization utilities.
 *
 * Handles conversion of barcode scan data to structured formats (XML/JSON).
 * Supports both single barcode transmission and batch operations for history export.
 *
 * Features:
 * - Unified data structure approach (same data definition for XML and JSON)
 * - Automatic JSON array handling for multiple barcodes
 * - Proper XML character escaping
 * - Thread-safe (stateless object)
 */
object Serializer {

    /**
     * Transmission metadata for XML/JSON serialization.
     * Contains only protocol information and scanner identification.
     *
     * @param protocolVersion Protocol version
     * @param hardwareID Hardware identifier (Bluetooth device name) [MAC addresses are randomized in Android 10+ and cannot be used]
     */
    data class TransmissionMetadata(
        val protocolVersion: String,
        val hardwareID: String?
    )

    /**
     * Single barcode entry with scan information.
     * Represents one scanned/processed barcode with all its metadata.
     *
     * @param value The barcode content
     * @param scannedAt Original scan timestamp (when barcode was scanned)
     * @param sentAt Current transmission timestamp (when data is being sent)
     * @param type Barcode type (QR_CODE, CODE_128, etc.)
     * @param source Data source (SCAN, HISTORY, MANUAL)
     */
    data class BarcodeEntry(
        val value: String,
        val scannedAt: String,
        val sentAt: String,
        val type: String,
        val source: String
    )

    /**
     * Complete data structure for XML/JSON serialization.
     * Supports both single barcode transmission and batch operations (e.g., history export).
     * This unified approach ensures both XML and JSON formats contain identical data.
     *
     * Usage examples:
     * - Single barcode: barcodes list with 1 entry (current real-time scanning)
     * - Batch export: barcodes list with multiple entries (history export, multi-scan transmission)
     *
     * @param metadata Transmission metadata (protocol version, hardware ID)
     * @param barcodes List of barcode entries (single or multiple)
     */
    data class SerializationData(
        val metadata: TransmissionMetadata,
        val barcodes: List<BarcodeEntry>
    )

    /**
     * Builds serialization data structure for a single barcode.
     * Handles dual timestamp logic where scan time can differ from send time.
     * This is a convenience wrapper for single-barcode operations.
     *
     * @param barcodeValue The barcode content to serialize
     * @param currentTimestamp Current timestamp for send time
     * @param barcodeType Detected barcode type
     * @param version Protocol version
     * @param dataSource Data source identifier (SCAN/HISTORY/MANUAL)
     * @param originalScanTimestamp Original scan timestamp (null uses current timestamp)
     * @param hardwareID Scanner hardware identifier (Bluetooth device name)
     * @return Complete serialization data structure with single barcode entry
     */
    fun buildSingleBarcodeData(
        barcodeValue: String,
        currentTimestamp: String,
        barcodeType: String = "UNKNOWN",
        protocolVersion: String = "1.0",
        dataSource: String = "SCAN",
        originalScanTimestamp: String? = null,
        hardwareID: String? = null
    ): SerializationData {
        val scannedAt = originalScanTimestamp ?: currentTimestamp  // Use original scan timestamp or fallback to current
        val sentAt = currentTimestamp                              // Always use current time for send timestamp

        return SerializationData(
            metadata = TransmissionMetadata(
                protocolVersion = protocolVersion,
                hardwareID = hardwareID
            ),
            barcodes = listOf(
                BarcodeEntry(
                    value = barcodeValue,
                    scannedAt = scannedAt,
                    sentAt = sentAt,
                    type = barcodeType,
                    source = dataSource
                )
            )
        )
    }

    /**
     * Serializes data to XML format using the unified data structure.
     * Produces well-formed XML with declaration and proper formatting.
     *
     * @param serializationData Complete data to serialize
     * @return Formatted XML string with declaration
     */
    fun toXml(serializationData: SerializationData): String {
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
    fun toJson(serializationData: SerializationData): String {
        val dataStructure = buildDataStructure(serializationData)
        val rootObject = JSONObject()
        rootObject.put(dataStructure.name, dataNodeToJson(dataStructure))
        return rootObject.toString()
    }

    // === Internal implementation ===

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

    private fun buildBtscannerHeader(metadata: TransmissionMetadata): Map<String, String> {
        val attributes = mutableMapOf("protocolVersion" to metadata.protocolVersion)
        metadata.hardwareID?.let { attributes["hardwareID"] = it }
        return attributes
    }

    private fun buildBarcodeData(entry: BarcodeEntry): List<DataNode.Value> {
        return listOf(
            DataNode.Value("value", entry.value),
            DataNode.Value("scannedAt", entry.scannedAt),
            DataNode.Value("sentAt", entry.sentAt),
            DataNode.Value("type", entry.type),
            DataNode.Value("source", entry.source)
        )
    }

    private fun buildDataStructure(serializationData: SerializationData): DataNode.Container {
        val btscannerHeader = buildBtscannerHeader(serializationData.metadata)
        val barcodeNodes = serializationData.barcodes.map { entry ->
            val barcodeData = buildBarcodeData(entry)
            DataNode.Container("barcode", children = barcodeData)
        }
        return DataNode.Container("btscanner", btscannerHeader, barcodeNodes)
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun dataNodeToXml(node: DataNode, indent: String = ""): String {
        return when (node) {
            is DataNode.Value -> {
                val escapedContent = escapeXml(node.content)
                "$indent<${node.name}>$escapedContent</${node.name}>"
            }
            is DataNode.Container -> {
                val attrs = if (node.attributes.isNotEmpty()) {
                    " " + node.attributes.map { (k, v) -> """$k="${escapeXml(v)}"""" }.joinToString(" ")
                } else ""

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

    private fun dataNodeToJson(node: DataNode): Any {
        return when (node) {
            is DataNode.Value -> node.content
            is DataNode.Container -> {
                val jsonObj = JSONObject()

                // Add container attributes as top-level properties
                node.attributes.forEach { (key, value) ->
                    jsonObj.put(key, value)
                }

                // Group children by name to handle multiple entries (e.g., multiple <barcode> elements)
                val childrenByName = node.children.groupBy { child ->
                    when (child) {
                        is DataNode.Value -> child.name
                        is DataNode.Container -> child.name
                    }
                }

                // Add children as properties, nested objects, or arrays
                childrenByName.forEach { (name, children) ->
                    when {
                        children.size == 1 -> {
                            // Single child: add as regular property
                            val child = children.first()
                            when (child) {
                                is DataNode.Value -> jsonObj.put(name, child.content)
                                is DataNode.Container -> jsonObj.put(name, dataNodeToJson(child))
                            }
                        }
                        children.size > 1 -> {
                            // Multiple children with same name: create JSONArray
                            val jsonArray = JSONArray()
                            children.forEach { child ->
                                when (child) {
                                    is DataNode.Value -> jsonArray.put(child.content)
                                    is DataNode.Container -> jsonArray.put(dataNodeToJson(child))
                                }
                            }
                            jsonObj.put(name, jsonArray)
                        }
                    }
                }

                jsonObj
            }
        }
    }
}
