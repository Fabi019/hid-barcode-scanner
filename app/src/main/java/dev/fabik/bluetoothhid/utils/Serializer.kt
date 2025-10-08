package dev.fabik.bluetoothhid.utils

import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import androidx.annotation.Keep

/**
 * Barcode data serialization utilities.
 *
 * Single source of truth = data class BarcodeEntry (reflection-based).
 */
object Serializer {

    // -----------------------------
    // -------- Data Models --------
    // -----------------------------

        // winlin97: These data model classes are used via Kotlin reflection (primaryConstructor/memberProperties).
        // In RELEASE builds, R8 can strip Kotlin metadata and/or obfuscate names, which breaks reflection.
        // @Keep prevents shrinking/obfuscation and keeps the primary constructor + metadata intact.
        // Without it you may hit:
        //  - tapping Export in History -> app crashes
        //  - importing history -> Hint "Error: p0 must have primary constructor", comes from IllegalStateException
        //
        // Requires: import androidx.annotation.Keep

    // Current data model
    @Keep
    data class BarcodeEntry(
        val text: String,
        val timestamp: Long,
        val format: String
    )

    // Legacy model for backward compatibility (old CSV exports with "type" column)
    @Keep
    internal data class LegacyBarcodeEntry(
        val text: String,
        val timestamp: Long,
        val type: String
    )

    // -------- Container names --------
    private object N {
        const val ROOT = "barcodes"
        const val ITEM = "barcode"
    }

    // ------------------------------------
    // -------- Reflection helpers --------
    // ------------------------------------

    private data class Field<T : Any>(
        val name: String,
        val param: KParameter,
        val prop: KProperty1<T, *>
    )

    private inline fun <reified T : Any> modelFields(): List<Field<T>> {
        val k = T::class
        val ctor = k.primaryConstructor ?: error("${k.simpleName} must have primary constructor")
        val propsByName = k.memberProperties.associateBy { it.name }
        return ctor.parameters.mapNotNull { p ->
            val n = p.name ?: return@mapNotNull null
            val prop = propsByName[n] as? KProperty1<T, *> ?: return@mapNotNull null
            Field(n, p, prop)
        }
    }

    private fun convertScalar(value: Any?, target: KType): Any? {
        if (value == null) return null
        val cls = (target.classifier as? KClass<*>) ?: return value
        return when (cls) {
            String::class -> value.toString()
            Long::class -> when (value) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
            Int::class -> when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
            Double::class -> when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
            Boolean::class -> when (value) {
                is Boolean -> value
                is String -> value.equals("true", true) || value == "1"
                is Number -> value.toInt() != 0
                else -> null
            }
            else -> value
        }
    }

    private inline fun <reified T : Any> constructFromMap(values: Map<String, Any?>): T {
        val k = T::class
        val ctor = k.primaryConstructor ?: error("${k.simpleName} must have primary constructor")
        val args = ctor.parameters.associateWith { p ->
            val raw = values[p.name]
            convertScalar(raw, p.type)
        }
        return ctor.callBy(args)
    }



    // -----------------------------
    // -------- Format: XML --------
    // -----------------------------

    fun toXml(entries: List<BarcodeEntry>): String {
        val fields = modelFields<BarcodeEntry>()
        val items = entries.joinToString("\n") { e ->
            val children = fields.joinToString("\n") { f ->
                val v = f.prop.get(e)
                val content = when (v) {
                    is String -> escapeXml(v)
                    else -> v?.toString() ?: ""
                }
                "    <${f.name}>$content</${f.name}>"
            }
            toXmlTag(N.ITEM, children, indent = "  ").trimEnd()
        }
        val root = toXmlTag(N.ROOT, items.prependIndent("  "))
        return """<?xml version="1.0" encoding="utf-8"?>""" + "\n" + root.trimEnd()
    }

    fun fromXml(xml: String): List<BarcodeEntry> {
        val fields = modelFields<BarcodeEntry>()
        val itemRegex = Regex("<${N.ITEM}>(.*?)</${N.ITEM}>", RegexOption.DOT_MATCHES_ALL)
        val results = mutableListOf<BarcodeEntry>()

        itemRegex.findAll(xml).forEach { m ->
            val block = m.groupValues[1]
            val map = mutableMapOf<String, Any?>()
            fields.forEach { f ->
                val r = Regex("<${f.name}>(.*?)</${f.name}>", RegexOption.DOT_MATCHES_ALL)
                val value = r.find(block)?.groupValues?.get(1)
                map[f.name] = value?.let { v ->
                    if (f.prop.returnType.classifier == String::class) unescapeXml(v) else v
                }
            }
            runCatching { constructFromMap<BarcodeEntry>(map) }
                .onSuccess { results.add(it) }
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
        .replace("&amp;", "&") // last


    // ------------------------------
    // -------- Format: JSON --------
    // ------------------------------

    fun toJson(entries: List<BarcodeEntry>): String {
        val fields = modelFields<BarcodeEntry>()

        val arr = JSONArray()
        entries.forEach { e ->
            val obj = JSONObject()
            fields.forEach { f -> obj.put(f.name, f.prop.get(e)) }
            arr.put(obj)
        }

        val inner = JSONObject().put(N.ITEM, arr) // {"barcode":[...]}
        val root = JSONObject().put(N.ROOT, inner) // {"barcodes": {...}}
        return root.toString()
    }

    fun fromJson(json: String): List<BarcodeEntry> {
        val fields = modelFields<BarcodeEntry>()
        val byName = fields.associateBy { it.name }

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
                byName.forEach { (name, _) ->
                    if (o.has(name) && !o.isNull(name)) map[name] = o.get(name)
                }
                add(constructFromMap<BarcodeEntry>(map))
            }
        }
    }

    // ------------------------------
    // -------- Format: CSV --------
    // ------------------------------

    fun toCsv(entries: List<BarcodeEntry>): String {
        val fields = modelFields<BarcodeEntry>()
        val header = fields.joinToString(";") { "\"${it.name}\"" }
        val rows = entries.map { e ->
            fields.joinToString(";") { f ->
                val raw = f.prop.get(e)?.toString() ?: ""
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

        // Auto-detect format: legacy uses "type", new format uses "format"
        val isLegacy = header.contains("type") && !header.contains("format")

        return if (isLegacy) {
            fromLegacyCsv(csv, delimiter, lines, header)
        } else {
            fromModernCsv(delimiter, lines, header)
        }
    }

    private fun fromModernCsv(delimiter: Char, lines: List<String>, header: List<String>): List<BarcodeEntry> {
        val nameToIndex = header.withIndex().associate { it.value to it.index }
        val fields = modelFields<BarcodeEntry>()

        return lines.drop(1).mapNotNull { line ->
            val cols = parseCsvLine(line, delimiter)
            val map = mutableMapOf<String, Any?>()
            fields.forEach { f ->
                val pos = nameToIndex[f.name] ?: return@forEach
                map[f.name] = cols.getOrNull(pos)
            }
            runCatching { constructFromMap<BarcodeEntry>(map) }.getOrNull()
        }
    }

    private fun fromLegacyCsv(csv: String, delimiter: Char, lines: List<String>, header: List<String>): List<BarcodeEntry> {
        val nameToIndex = header.withIndex().associate { it.value to it.index }
        val fields = modelFields<LegacyBarcodeEntry>()

        return lines.drop(1).mapNotNull { line ->
            val cols = parseCsvLine(line, delimiter)
            val map = mutableMapOf<String, Any?>()
            fields.forEach { f ->
                val pos = nameToIndex[f.name] ?: return@forEach
                map[f.name] = cols.getOrNull(pos)
            }
            runCatching {
                val legacy = constructFromMap<LegacyBarcodeEntry>(map)
                // Convert legacy to modern format
                BarcodeEntry(legacy.text, legacy.timestamp, legacy.type)
            }.getOrNull()
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
        return out.map { value ->
            value
                .replace("{__CSV_CR__}{__CSV_LF__}", "\r\n")
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
