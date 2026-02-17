package dev.fabik.bluetoothhid.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SerializerTest {

    @Test
    fun test000_RobolectricWarmup() {
        // Warm-up: Initialize Robolectric Android framework shadows
        // This runs first (alphabetically) and absorbs the ~3s cold start cost
        // Subsequent JSON/XML tests will be fast (~0.03s instead of ~3s)
        val warmupJson = JSONObject().put("init", "robolectric")
        val warmupArray = JSONArray().put("warmup")

        // Verify initialization worked
        assertEquals("robolectric", warmupJson.getString("init"))
        assertEquals("warmup", warmupArray.getString(0))
    }

    @Test
    fun testCsvSerialization() {
        // Create test data
        val entries = listOf(
            Serializer.BarcodeEntry("12345", 1234567890L, "QR_CODE"),
            Serializer.BarcodeEntry("67890", 1234567900L, "EAN_13")
        )

        // Serialize to CSV
        val csv = Serializer.toCsv(entries)

        // Verify CSV contains header and data
        assertTrue(csv.contains("text;timestamp;format"))
        assertTrue(csv.contains("\"12345\""))
        assertTrue(csv.contains("\"1234567890\""))
        assertTrue(csv.contains("\"QR_CODE\""))
    }

    @Test
    fun testCsvDeserialization() {
        // Modern CSV format
        val csv = """
            "text";"timestamp";"format"
            "12345";"1234567890";"QR_CODE"
            "67890";"1234567900";"EAN_13"
        """.trimIndent()

        val entries = Serializer.fromCsv(csv)

        assertEquals(2, entries.size)
        assertEquals("12345", entries[0].text)
        assertEquals(1234567890L, entries[0].timestamp)
        assertEquals("QR_CODE", entries[0].format)
    }

    @Test
    fun testLegacyCsvDeserialization() {
        // Legacy CSV format with "type" instead of "format"
        val legacyCsv = """
            "text";"timestamp";"type"
            "12345";"1234567890";"QR_CODE"
            "67890";"1234567900";"EAN_13"
        """.trimIndent()

        val entries = Serializer.fromCsv(legacyCsv)

        assertEquals(2, entries.size)
        assertEquals("12345", entries[0].text)
        assertEquals(1234567890L, entries[0].timestamp)
        assertEquals("QR_CODE", entries[0].format) // Converted to format
    }

    @Test
    fun testJsonSerialization() {
        val entries = listOf(
            Serializer.BarcodeEntry("TEST123", 1000L, "CODE_128"),
            Serializer.BarcodeEntry("TEST", 123L, "QR_CODE")
        )

        val json = Serializer.toJson(entries)

        assertTrue(json.contains("barcodes"))
        assertTrue(json.contains("barcode"))
        assertTrue(json.contains("TEST123"))
        assertTrue(json.contains("CODE_128"))
    }

    @Test
    fun testJsonDeserialization() {
        val json = """
            {
                "barcodes": {
                    "barcode": [
                        {
                            "text": "ABC",
                            "timestamp": 2000,
                            "format": "QR_CODE"
                        }
                    ]
                }
            }
        """.trimIndent()

        val entries = Serializer.fromJson(json)

        assertEquals(1, entries.size)
        assertEquals("ABC", entries[0].text)
        assertEquals(2000L, entries[0].timestamp)
        assertEquals("QR_CODE", entries[0].format)
    }

    @Test
    fun testXmlSerialization() {
        val entries = listOf(
            Serializer.BarcodeEntry("XML_TEST", 3000L, "DATA_MATRIX")
        )

        val xml = Serializer.toXml(entries)

        assertTrue(xml.contains("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
        assertTrue(xml.contains("<barcodes>"))
        assertTrue(xml.contains("<barcode>"))
        assertTrue(xml.contains("<text>XML_TEST</text>"))
        assertTrue(xml.contains("<format>DATA_MATRIX</format>"))
    }

    @Test
    fun testXmlDeserialization() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <barcodes>
              <barcode>
                <text>XYZ</text>
                <timestamp>4000</timestamp>
                <format>PDF_417</format>
              </barcode>
            </barcodes>
        """.trimIndent()

        val entries = Serializer.fromXml(xml)

        assertEquals(1, entries.size)
        assertEquals("XYZ", entries[0].text)
        assertEquals(4000L, entries[0].timestamp)
        assertEquals("PDF_417", entries[0].format)
    }

    @Test
    fun testXmlEscaping() {
        // Test special XML characters
        val entries = listOf(
            Serializer.BarcodeEntry("<test>&\"'value", 5000L, "TYPE")
        )

        val xml = Serializer.toXml(entries)
        val decoded = Serializer.fromXml(xml)

        assertEquals(1, decoded.size)
        assertEquals("<test>&\"'value", decoded[0].text) // Should be preserved
    }

    @Test
    fun testCsvWithSpecialCharacters() {
        // Test CSV with quotes and special chars
        val entries = listOf(
            Serializer.BarcodeEntry("test\"with\"quotes", 6000L, "TYPE")
        )

        val csv = Serializer.toCsv(entries)
        val decoded = Serializer.fromCsv(csv)

        assertEquals(1, decoded.size)
        assertEquals("test\"with\"quotes", decoded[0].text)
    }

    @Test
    fun testLinesToPlaintext() {
        val entries = listOf(
            Serializer.BarcodeEntry("Line1", 1000L, "TYPE1"),
            Serializer.BarcodeEntry("Line2", 2000L, "TYPE2")
        )

        val lines = Serializer.toLines(entries)

        // Should contain only text values separated by newline
        assertTrue(lines.contains("Line1"))
        assertTrue(lines.contains("Line2"))
        assertEquals(2, lines.split(System.lineSeparator()).size)
    }

    @Test
    fun testEmptyCsvDeserialization() {
        val emptyCsv = ""
        val entries = Serializer.fromCsv(emptyCsv)
        assertEquals(0, entries.size)
    }

    @Test
    fun testCsvWithNewlines() {
        // Test CSV with newlines in barcode text (e.g., vCard, WiFi config QR codes)
        val entries = listOf(
            Serializer.BarcodeEntry("Line1\nLine2", 1000L, "QR_CODE"),
            Serializer.BarcodeEntry("Windows\r\nStyle", 2000L, "QR_CODE"),
            Serializer.BarcodeEntry("Mac\rStyle", 3000L, "DATA_MATRIX")
        )

        val csv = Serializer.toCsv(entries)

        // Verify escaping: newlines should be replaced with placeholders
        assertTrue(csv.contains("{__CSV_LF__}"))
        assertTrue(csv.contains("{__CSV_CR__}{__CSV_LF__}"))
        assertTrue(csv.contains("{__CSV_CR__}"))

        // Verify no actual newlines in data rows (only in line separators)
        val lines = csv.lines()
        assertEquals(4, lines.size) // header + 3 data rows

        // Round-trip test: deserialize should restore original values
        val decoded = Serializer.fromCsv(csv)
        assertEquals(3, decoded.size)
        assertEquals("Line1\nLine2", decoded[0].text)
        assertEquals("Windows\r\nStyle", decoded[1].text)
        assertEquals("Mac\rStyle", decoded[2].text)
    }

    @Test
    fun testCsvRoundtripMultiline() {
        // Complex multiline barcode (e.g., vCard with multiple fields)
        val vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nEND:VCARD"
        val entries = listOf(
            Serializer.BarcodeEntry(vcard, 12345L, "QR_CODE")
        )

        val csv = Serializer.toCsv(entries)
        val decoded = Serializer.fromCsv(csv)

        assertEquals(1, decoded.size)
        assertEquals(vcard, decoded[0].text)
        assertEquals(12345L, decoded[0].timestamp)
        assertEquals("QR_CODE", decoded[0].format)
    }
}
