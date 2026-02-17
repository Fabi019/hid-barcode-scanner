package dev.fabik.bluetoothhid.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * Barcode data serialization utilities.
 *
 */
object Serializer {

    // -----------------------------
    // -------- Data Models --------
    // -----------------------------

    data class BarcodeEntry(
        val text: String,
        val timestamp: Long,
        val format: String
    )

    // -------- Container names --------
    private object N {
        const val ROOT = "barcodes"
        const val ITEM = "barcode"
    }

    private data class Field<T>(
        val name: String,
        val getter: ((T) -> Any?)? = null
    )

    private val barcodeFields = listOf(
        Field<BarcodeEntry>("text") { it.text },
        Field<BarcodeEntry>("timestamp") { it.timestamp },
        Field<BarcodeEntry>("format") { it.format },
        Field<BarcodeEntry>("type")
    )


    private fun toBarcodeEntry(map: Map<String, Any?>): BarcodeEntry =
        BarcodeEntry(
            text = map["text"]?.toString() ?: "",
            timestamp = when (val v = map["timestamp"]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: 0L
                else -> 0L
            },
            format = (map["format"] ?: map["type"])?.toString() ?: "",
        )

    // -----------------------------
    // -------- Format: XML --------
    // -----------------------------

    fun toXml(entries: List<BarcodeEntry>): String {
        val items = entries.joinToString("\n") { e ->
            val children = barcodeFields.filter { it.getter != null }.joinToString("\n") { f ->
                val v = f.getter?.let { it(e) }?.toString() ?: ""
                val content = escapeXml(v)
                "    <${f.name}>$content</${f.name}>"
            }
            toXmlTag(N.ITEM, children, indent = "  ").trimEnd()
        }
        val root = toXmlTag(N.ROOT, items.prependIndent("  "))
        return """<?xml version="1.0" encoding="utf-8"?>""" + "\n" + root.trimEnd()
    }

    fun fromXml(xml: String): List<BarcodeEntry> {
        val itemRegex = Regex("<${N.ITEM}>(.*?)</${N.ITEM}>", RegexOption.DOT_MATCHES_ALL)
        val results = mutableListOf<BarcodeEntry>()

        itemRegex.findAll(xml).forEach { m ->
            val block = m.groupValues[1]
            val map = mutableMapOf<String, Any?>()
            barcodeFields.forEach { f ->
                val r = Regex("<${f.name}>(.*?)</${f.name}>", RegexOption.DOT_MATCHES_ALL)
                val value = r.find(block)?.groupValues?.get(1)
                map[f.name] = value?.let { unescapeXml(it) }
            }
            runCatching { results.add(toBarcodeEntry(map)) }
        }
        return results
    }

    private fun toXmlTag(name: String, body: String, indent: String = ""): String =
        buildString {
            appendLine("$indent<$name>")
            append(body)
            appendLine("$indent</$name>")
        }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun unescapeXml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    // ------------------------------
    // -------- Format: JSON --------
    // ------------------------------

    fun toJson(entries: List<BarcodeEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            val obj = JSONObject()
            barcodeFields.filter { it.getter != null }
                .forEach { f -> obj.put(f.name, f.getter?.let { it(e) }) }
            arr.put(obj)
        }

        val inner = JSONObject().put(N.ITEM, arr) // {"barcode":[...]}
        val root = JSONObject().put(N.ROOT, inner) // {"barcodes": {...}}
        return root.toString()
    }

    fun fromJson(json: String): List<BarcodeEntry> {
        val root = JSONObject(json)
        val wrapped = root.optJSONObject(N.ROOT)
            ?: error("JSON must contain object '${N.ROOT}'")
        val value = wrapped.opt(N.ITEM)
            ?: error("JSON must contain '${N.ROOT}.${N.ITEM}'")

        val arr: JSONArray = when (value) {
            is JSONArray -> value
            is JSONObject -> JSONArray().put(value) // single item tolerance
            else -> error("'${N.ROOT}.${N.ITEM}' must be array or object")
        }

        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val map = mutableMapOf<String, Any?>()
                barcodeFields.forEach { f ->
                    if (o.has(f.name) && !o.isNull(f.name)) {
                        map[f.name] = o.get(f.name)
                    }
                }
                add(toBarcodeEntry(map))
            }
        }
    }

    // ------------------------------
    // -------- Format: CSV --------
    // ------------------------------

    fun toCsv(entries: List<BarcodeEntry>): String {
        val header = barcodeFields.filter { it.getter != null }.joinToString(";") { it.name }
        val rows = entries.map { e ->
            barcodeFields.filter { it.getter != null }.joinToString(";") { f ->
                val raw = f.getter?.let { it(e) }?.toString() ?: ""
                // Escape newlines first (CRLF before individual CR/LF), then quotes
                val escaped = raw
                    .replace("\r\n", "{__CSV_CR__}{__CSV_LF__}")
                    .replace("\r", "{__CSV_CR__}")
                    .replace("\n", "{__CSV_LF__}")
                    .replace("\"", "\"\"")
                "\"$escaped\""
            }
        }
        return header + System.lineSeparator() + rows.joinToString(System.lineSeparator())
    }

    fun fromCsv(csv: String): List<BarcodeEntry> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()

        val delimiter = detectCsvDelimiter(csv)
        val header = parseCsvLine(lines.first(), delimiter)

        return fromCsv(delimiter, lines, header)
    }

    private fun fromCsv(
        delimiter: Char,
        lines: List<String>,
        header: List<String>
    ): List<BarcodeEntry> {
        val nameToIndex = header.withIndex().associate { it.value to it.index }

        return lines.drop(1).mapNotNull { line ->
            val cols = parseCsvLine(line, delimiter)
            val map = mutableMapOf<String, Any?>()
            barcodeFields.forEach { f ->
                nameToIndex[f.name]?.let { map[f.name] = cols.getOrNull(it) }
            }
            runCatching { toBarcodeEntry(map) }.getOrNull()
        }
    }

    private fun parseCsvLine(line: String, delimiter: Char = ';'): List<String> {
        val out = mutableListOf<String>()
        var sb = StringBuilder()
        var inQ = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQ = !inQ
                c == delimiter && !inQ -> { out.add(sb.toString()); sb = StringBuilder() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())

        // Unescape newline placeholders (CRLF first, then individual CR/LF)
        return out.map {
            it.replace("{__CSV_CR__}{__CSV_LF__}", "\r\n")
                .replace("{__CSV_LF__}", "\n")
                .replace("{__CSV_CR__}", "\r")
        }
    }

    private fun detectCsvDelimiter(csv: String): Char {
        val firstLine = csv.lineSequence().firstOrNull() ?: return ';'
        var inQuotes = false
        var semicolonCount = 0
        var commaCount = 0

        for (c in firstLine) {
            when {
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ';' -> semicolonCount++
                !inQuotes && c == ',' -> commaCount++
            }
        }

        return if (semicolonCount > 0) ';' else ','
    }

    // -------------------------------------------
    // -------- Format: Lines (Plaintext) --------
    // -------------------------------------------

    fun toLines(entries: List<BarcodeEntry>): String =
        entries.joinToString(System.lineSeparator()) { it.text }
}
