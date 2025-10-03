package dev.fabik.bluetoothhid.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * Barcode data serialization utilities.
 *
 * Handles conversion of barcode history data to structured formats (XML/JSON).
 * Uses unified data structure approach - one definition, two renderers.
 *
 * Features:
 * - Shared data structure (DataNode) ensures XML and JSON have identical structure
 * - Simple format matching history CSV: text, timestamp, type
 * - Proper XML character escaping
 * - Thread-safe (stateless object)
 */
object Serializer {

    /**
     * Single barcode entry matching history format.
     *
     * @param text The barcode content
     * @param timestamp Scan timestamp in milliseconds
     * @param type Barcode type (QR_CODE, CODE_128, etc. or numeric format ID)
     */
    data class BarcodeEntry(
        val text: String,
        val timestamp: Long,
        val type: String
    )

    /**
     * Universal data structure that can be rendered to both XML and JSON.
     * This ensures both formats have identical structure and content.
     */
    private sealed class DataNode {
        /**
         * Container node with child nodes (like XML elements or JSON objects).
         */
        data class Container(
            val name: String,
            val children: List<DataNode> = emptyList()
        ) : DataNode()

        /**
         * Leaf node containing a value (XML text content, JSON string/number values).
         */
        data class Value(
            val name: String,
            val content: Any  // Can be String or Long
        ) : DataNode()
    }

    /**
     * Builds universal data structure from barcode entries.
     * This structure can be rendered to both XML and JSON.
     */
    private fun buildDataStructure(entries: List<BarcodeEntry>): DataNode.Container {
        val barcodeNodes = entries.map { entry ->
            DataNode.Container(
                name = "barcode",
                children = listOf(
                    DataNode.Value("text", entry.text),
                    DataNode.Value("timestamp", entry.timestamp),
                    DataNode.Value("type", entry.type)
                )
            )
        }
        return DataNode.Container("barcodes", barcodeNodes)
    }

    /**
     * Serializes barcode entries to XML format.
     *
     * Structure:
     * ```xml
     * <?xml version="1.0" encoding="utf-8"?>
     * <barcodes>
     *     <barcode>
     *         <text>value</text>
     *         <timestamp>1234567890</timestamp>
     *         <type>QR_CODE</type>
     *     </barcode>
     * </barcodes>
     * ```
     */
    fun toXml(entries: List<BarcodeEntry>): String {
        val dataStructure = buildDataStructure(entries)
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + dataNodeToXml(dataStructure)
    }

    /**
     * Serializes barcode entries to JSON format.
     *
     * Structure:
     * ```json
     * [
     *   {"text":"value","timestamp":1234567890,"type":"QR_CODE"},
     *   {"text":"value2","timestamp":1234567891,"type":"CODE_128"}
     * ]
     * ```
     */
    fun toJson(entries: List<BarcodeEntry>): String {
        val dataStructure = buildDataStructure(entries)
        // For JSON, we return the array of barcodes, not the wrapper
        return when (val jsonData = dataNodeToJson(dataStructure)) {
            is JSONObject -> jsonData.getJSONArray("barcode").toString()
            else -> JSONArray().toString()
        }
    }

    /**
     * Renders DataNode structure to XML.
     */
    private fun dataNodeToXml(node: DataNode, indent: String = ""): String {
        return when (node) {
            is DataNode.Value -> {
                val escapedContent = when (node.content) {
                    is String -> escapeXml(node.content)
                    else -> node.content.toString()
                }
                "$indent<${node.name}>$escapedContent</${node.name}>"
            }
            is DataNode.Container -> {
                if (node.children.isNotEmpty()) {
                    val childrenXml = node.children.joinToString("\n") {
                        dataNodeToXml(it, "$indent    ")
                    }
                    "$indent<${node.name}>\n$childrenXml\n$indent</${node.name}>"
                } else {
                    "$indent<${node.name}/>"
                }
            }
        }
    }

    /**
     * Renders DataNode structure to JSON.
     */
    private fun dataNodeToJson(node: DataNode): Any {
        return when (node) {
            is DataNode.Value -> node.content
            is DataNode.Container -> {
                // Group children by name
                val childrenByName = node.children.groupBy {
                    when (it) {
                        is DataNode.Value -> it.name
                        is DataNode.Container -> it.name
                    }
                }

                val jsonObj = JSONObject()
                childrenByName.forEach { (name, children) ->
                    when {
                        children.size == 1 -> {
                            val child = children.first()
                            jsonObj.put(name, dataNodeToJson(child))
                        }
                        children.size > 1 -> {
                            val jsonArray = JSONArray()
                            children.forEach { child ->
                                jsonArray.put(dataNodeToJson(child))
                            }
                            jsonObj.put(name, jsonArray)
                        }
                    }
                }
                jsonObj
            }
        }
    }

    /**
     * Escapes special XML characters to ensure well-formed output.
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
