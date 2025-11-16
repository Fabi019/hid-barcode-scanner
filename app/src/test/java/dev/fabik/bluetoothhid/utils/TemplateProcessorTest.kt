package dev.fabik.bluetoothhid.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TemplateProcessorTest {

    @Test
    fun testBasicCodePlaceholder() {
        // Test simple {CODE} replacement
        val result = TemplateProcessor.processTemplate(
            data = "12345",
            template = "Barcode: {CODE}",
            mode = TemplateProcessor.TemplateMode.HID
        )
        assertEquals("Barcode: 12345", result)
    }

    @Test
    fun testCodeWithHexEncoding() {
        // Test {CODE_HEX} encoding
        val result = TemplateProcessor.processTemplate(
            data = "AB",
            template = "{CODE_HEX}",
            mode = TemplateProcessor.TemplateMode.HID
        )
        // "AB" -> hex: 4142 (lowercase in HID mode)
        assertEquals("4142", result)
    }

    @Test
    fun testCodeWithBase64Encoding() {
        // Test {CODE_B64} encoding
        val result = TemplateProcessor.processTemplate(
            data = "test",
            template = "{CODE_B64}",
            mode = TemplateProcessor.TemplateMode.HID
        )
        // "test" in base64
        assertEquals("dGVzdA==", result)
    }

    @Test
    fun testCodeWithMultipleEncodings() {
        // Test {CODE_HEX_B64} - hex then base64
        val result = TemplateProcessor.processTemplate(
            data = "A",
            template = "{CODE_HEX_B64}",
            mode = TemplateProcessor.TemplateMode.HID
        )
        // "A" -> hex: "61" -> base64
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testDateTimePlaceholders() {
        // Test that {DATE}, {TIME}, {DATETIME} get replaced
        val result = TemplateProcessor.processTemplate(
            data = "test",
            template = "{DATE} {TIME}",
            mode = TemplateProcessor.TemplateMode.HID
        )
        // Should not contain placeholders
        assertTrue(!result.contains("{DATE}"))
        assertTrue(!result.contains("{TIME}"))
    }

    @Test
    fun testCodeTypePlaceholder() {
        // Test {CODE_TYPE} replacement
        val result = TemplateProcessor.processTemplate(
            data = "123",
            template = "{CODE} Type: {CODE_TYPE}",
            mode = TemplateProcessor.TemplateMode.HID,
            barcodeType = "QR_CODE"
        )
        assertEquals("123 Type: QR_CODE", result)
    }

    @Test
    fun testScanSourcePlaceholder() {
        // Test {SCAN_SOURCE} replacement
        val result = TemplateProcessor.processTemplate(
            data = "456",
            template = "{CODE} Source: {SCAN_SOURCE}",
            mode = TemplateProcessor.TemplateMode.HID,
            from = "CAMERA"
        )
        assertEquals("456 Source: CAMERA", result)
    }

    @Test
    fun testScannerIDPlaceholder() {
        // Test {SCANNER_ID} replacement
        val result = TemplateProcessor.processTemplate(
            data = "789",
            template = "{CODE} Scanner: {SCANNER_ID}",
            mode = TemplateProcessor.TemplateMode.HID,
            scannerId = "DEVICE123"
        )
        assertEquals("789 Scanner: DEVICE123", result)
    }

    @Test
    fun testSpacePlaceholderHIDMode() {
        // Test {SPACE} in HID mode
        val result = TemplateProcessor.processTemplate(
            data = "X",
            template = "{CODE}{SPACE}{CODE}",
            mode = TemplateProcessor.TemplateMode.HID
        )
        assertEquals("X X", result)
    }

    @Test
    fun testTabPlaceholderRFCOMMMode() {
        // Test {TAB} in RFCOMM mode
        val result = TemplateProcessor.processTemplate(
            data = "Y",
            template = "{CODE}{TAB}{CODE}",
            mode = TemplateProcessor.TemplateMode.RFCOMM
        )
        assertEquals("Y\tY", result)
    }

    @Test
    fun testEnterPlaceholderRFCOMMMode() {
        // Test {ENTER} in RFCOMM mode
        val result = TemplateProcessor.processTemplate(
            data = "Z",
            template = "{CODE}{ENTER}",
            mode = TemplateProcessor.TemplateMode.RFCOMM
        )
        assertEquals("Z\r\n", result)
    }

    @Test
    fun testUnsupportedPlaceholdersInHIDMode() {
        // Test that unsupported placeholders in HID mode are replaced with markers
        val result = TemplateProcessor.processTemplate(
            data = "test",
            template = "{CODE}{TAB}",
            mode = TemplateProcessor.TemplateMode.HID,
            preserveUnsupportedPlaceholders = false
        )
        // {TAB} should be replaced with a marker in HID mode
        assertEquals("test\uFFFD{TAB}", result)
    }

    @Test
    fun testPreserveUnsupportedPlaceholders() {
        // Test preserveUnsupported flag
        val result = TemplateProcessor.processTemplate(
            data = "test",
            template = "{CODE}{TAB}",
            mode = TemplateProcessor.TemplateMode.HID,
            preserveUnsupportedPlaceholders = true
        )
        // {TAB} should be kept as-is when preserveUnsupported is true
        assertEquals("test{TAB}", result)
    }

    @Test
    fun testGlobalHexEncoding() {
        // Test {GLOBAL_HEX} applies to entire output
        val result = TemplateProcessor.processTemplate(
            data = "AB",
            template = "{GLOBAL_HEX}{CODE}",
            mode = TemplateProcessor.TemplateMode.HID
        )
        // "AB" -> hex (lowercase in HID)
        assertEquals("4142", result)
    }

    @Test
    fun testComplexTemplate() {
        // Test combination of multiple placeholders
        val result = TemplateProcessor.processTemplate(
            data = "12345",
            template = "Type:{CODE_TYPE},Code:{CODE},Source:{SCAN_SOURCE}",
            mode = TemplateProcessor.TemplateMode.RFCOMM,
            barcodeType = "EAN_13",
            from = "SCAN"
        )
        assertEquals("Type:EAN_13,Code:12345,Source:SCAN", result)
    }

    @Test
    fun testEmptyTemplate() {
        // Test that template with only CODE works
        val result = TemplateProcessor.processTemplate(
            data = "test",
            template = "{CODE}",
            mode = TemplateProcessor.TemplateMode.HID
        )
        assertEquals("test", result)
    }
}
