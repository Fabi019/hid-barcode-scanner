package dev.fabik.bluetoothhid.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun testHidTemplateFromUserTemplatePassedThrough() {
        // {TAB} in the user's own template must reach KeyTranslator as-is (no \uFFFD prefix,
        // no escaping) so it is expanded as a real Tab keypress.
        // Phase 5 was removed because it incorrectly prepended \uFFFD and broke countTypedChars.
        val result = TemplateProcessor.processTemplate(
            data = "test",
            template = "{CODE}{TAB}",
            mode = TemplateProcessor.TemplateMode.HID,
            preserveUnsupportedPlaceholders = false
        )
        assertEquals("test{TAB}", result)
    }

    @Test
    fun testPreserveUnsupportedPlaceholders() {
        // preserveUnsupportedPlaceholders only affects RFCOMM mode (whether unsupported HID
        // placeholders are encoded as literal text or removed).  In HID mode the flag has
        // no observable effect since Phase 5 was removed; {TAB} passes through regardless.
        val result = TemplateProcessor.processTemplate(
            data = "test",
            template = "{CODE}{TAB}",
            mode = TemplateProcessor.TemplateMode.HID,
            preserveUnsupportedPlaceholders = true
        )
        assertEquals("test{TAB}", result)
    }

    // --- expandCode tests ---

    @Test
    fun testExpandCodeFalse_bracesInBarcodeEscaped() {
        // When expandCode=false, { } embedded in the barcode value must be escaped with
        // Private Use Area sentinels so KeyTranslator types them as literal characters
        // rather than expanding e.g. {TAB} as a real Tab keypress.
        val esc = TemplateProcessor.ESCAPED_OPEN_BRACE
        val escClose = TemplateProcessor.ESCAPED_CLOSE_BRACE
        val result = TemplateProcessor.processTemplate(
            data = "A{TAB}B",
            template = "{CODE}",
            mode = TemplateProcessor.TemplateMode.HID,
            expandCode = false
        )
        assertEquals("A${esc}TAB${escClose}B", result)
    }

    @Test
    fun testExpandCodeTrue_bracesInBarcodeNotEscaped() {
        // When expandCode=true, {TAB} from barcode data is left as-is so KeyTranslator
        // expands it as a real Tab keypress.
        val result = TemplateProcessor.processTemplate(
            data = "A{TAB}B",
            template = "{CODE}",
            mode = TemplateProcessor.TemplateMode.HID,
            expandCode = true
        )
        assertEquals("A{TAB}B", result)
    }

    @Test
    fun testExpandCodeFalse_templateHIDKeysNotEscaped() {
        // {ENTER} that comes from the USER'S TEMPLATE (not the barcode) must never be escaped,
        // regardless of expandCode.  It should always reach KeyTranslator as {ENTER}.
        val esc = TemplateProcessor.ESCAPED_OPEN_BRACE
        val escClose = TemplateProcessor.ESCAPED_CLOSE_BRACE
        val result = TemplateProcessor.processTemplate(
            data = "hello",
            template = "{CODE}{ENTER}",
            mode = TemplateProcessor.TemplateMode.HID,
            expandCode = false
        )
        // barcode "hello" has no braces \u2192 no escaping in it; {ENTER} from template stays
        assertEquals("hello{ENTER}", result)
    }

    @Test
    fun testExpandCodeFalse_codeHexNotAffected() {
        // {CODE_HEX} encodes the barcode value to hex; the encoding naturally eliminates
        // { } so escaping must NOT be applied before encoding (it would corrupt the sentinel
        // codepoints into the hex output instead of the original brace bytes 7b/7d).
        val result = TemplateProcessor.processTemplate(
            data = "A{TAB}B",
            template = "{CODE_HEX}",
            mode = TemplateProcessor.TemplateMode.HID,
            expandCode = false
        )
        // "A{TAB}B" = 0x41, 0x7b, 0x54, 0x41, 0x42, 0x7d, 0x42
        assertEquals("417b5441427d42", result)
    }

    @Test
    fun testExpandCodeFalse_globalHexDecodedCorrectly() {
        // {GLOBAL_HEX}{CODE} with expandCode=false: the barcode value is escaped (sentinels)
        // during Phase 3.  applyGlobalEncodingHID must decode the sentinels back to { }
        // before encoding so the hex output reflects the true bytes 7b/7d.
        val result = TemplateProcessor.processTemplate(
            data = "A{TAB}B",
            template = "{GLOBAL_HEX}{CODE}",
            mode = TemplateProcessor.TemplateMode.HID,
            expandCode = false
        )
        // "A{TAB}B" in hex (plain text including the literal braces)
        assertEquals("417b5441427d42", result)
    }

    @Test
    fun testExpandCodeFalse_f2PrefixAndEnterSuffix() {
        // Template {F2}{CODE}{ENTER}: F2 and ENTER from the template must never be escaped;
        // only the barcode value substituted via {CODE} is subject to expandCode escaping.
        val esc = TemplateProcessor.ESCAPED_OPEN_BRACE
        val escClose = TemplateProcessor.ESCAPED_CLOSE_BRACE
        val result = TemplateProcessor.processTemplate(
            data = "ABC",
            template = "{F2}{CODE}{ENTER}",
            mode = TemplateProcessor.TemplateMode.HID,
            expandCode = false
        )
        // "ABC" has no braces \u2192 no escaping; template keys pass through unchanged
        assertEquals("{F2}ABC{ENTER}", result)
    }

    @Test
    fun testExpandCodeTrue_f2PrefixAndEnterSuffix() {
        // Same scenario with expandCode=true: identical result because "ABC" has no braces.
        val result = TemplateProcessor.processTemplate(
            data = "ABC",
            template = "{F2}{CODE}{ENTER}",
            mode = TemplateProcessor.TemplateMode.HID,
            expandCode = true
        )
        assertEquals("{F2}ABC{ENTER}", result)
    }

    @Test
    fun testExpandCodeFalse_codeB64NotAffected() {
        // {CODE_B64} encodes the barcode value to base64; encoding must receive RAW data,
        // not sentinel-escaped data, so the result reflects the true byte values.
        // Base64 output never contains { or }, so no escaping is needed after encoding.
        val result = TemplateProcessor.processTemplate(
            data = "A{TAB}B",
            template = "{CODE_B64}",
            mode = TemplateProcessor.TemplateMode.HID,
            expandCode = false
        )
        // "A{TAB}B" = bytes [0x41,0x7b,0x54,0x41,0x42,0x7d,0x42] → base64 "QXtUQUJ9Qg=="
        assertEquals("QXtUQUJ9Qg==", result)
        // Verify no sentinel chars leaked into the output
        assertFalse(result.contains(TemplateProcessor.ESCAPED_OPEN_BRACE))
        assertFalse(result.contains(TemplateProcessor.ESCAPED_CLOSE_BRACE))
    }

    @Test
    fun testExpandCodeFalse_globalB64DecodedCorrectly() {
        // {GLOBAL_B64}{CODE} with expandCode=false: sentinels in the CODE value must be
        // decoded back to { } before base64 encoding so the output reflects "A{TAB}B".
        val result = TemplateProcessor.processTemplate(
            data = "A{TAB}B",
            template = "{GLOBAL_B64}{CODE}",
            mode = TemplateProcessor.TemplateMode.HID,
            expandCode = false
        )
        // Same bytes as CODE_B64 case
        assertEquals("QXtUQUJ9Qg==", result)
    }

    @Test
    fun testExpandCodeFalse_globalHexWithTemplateEnterPreserved() {
        // When the user's template combines {GLOBAL_HEX} with a {ENTER} key suffix, the
        // barcode text must be hex-encoded (including any literal braces) while {ENTER}
        // from the TEMPLATE is preserved as a key placeholder for KeyTranslator.
        val result = TemplateProcessor.processTemplate(
            data = "A{TAB}B",
            template = "{GLOBAL_HEX}{CODE}{ENTER}",
            mode = TemplateProcessor.TemplateMode.HID,
            expandCode = false
        )
        // Text part "A{TAB}B" hex-encoded → "417b5441427d42"; {ENTER} from template preserved
        assertEquals("417b5441427d42{ENTER}", result)
    }

    @Test
    fun testExpandCodeFalse_rfcommModeUnchanged() {
        // expandCode has no effect in RFCOMM mode; {ENTER} in barcode data should be
        // substituted literally and then expanded by the RFCOMM phase 2 replacement.
        val result = TemplateProcessor.processTemplate(
            data = "A{ENTER}B",
            template = "{CODE}",
            mode = TemplateProcessor.TemplateMode.RFCOMM,
            expandCode = false
        )
        // RFCOMM mode: {ENTER} in the barcode stays as literal text; it was never a template
        // in RFCOMM context (Phase 2 only replaces {ENTER} that appear in the template string,
        // not inside the already-substituted {CODE} value).
        assertEquals("A{ENTER}B", result)
    }

    @Test
    fun testExpandCodeFalse_regexGroupEscaped() {
        // {CODE%1} with expandCode=false: the regex group value should also have braces escaped.
        val esc = TemplateProcessor.ESCAPED_OPEN_BRACE
        val escClose = TemplateProcessor.ESCAPED_CLOSE_BRACE
        val result = TemplateProcessor.processTemplate(
            data = "full",
            template = "{CODE%1}",
            mode = TemplateProcessor.TemplateMode.HID,
            expandCode = false,
            regexGroups = listOf("X{TAB}Y")
        )
        assertEquals("X${esc}TAB${escClose}Y", result)
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

    // --- {CODE%N} regex capture group tests ---

    @Test
    fun testRegexGroupFirst() {
        // {CODE%1} should return first capture group
        val result = TemplateProcessor.processTemplate(
            data = "ABC",
            template = "{CODE%1}",
            mode = TemplateProcessor.TemplateMode.HID,
            regexGroups = listOf("ABC", "123")
        )
        assertEquals("ABC", result)
    }

    @Test
    fun testRegexGroupSecond() {
        // {CODE%2} should return second capture group
        val result = TemplateProcessor.processTemplate(
            data = "ABC",
            template = "{CODE%2}",
            mode = TemplateProcessor.TemplateMode.HID,
            regexGroups = listOf("ABC", "123")
        )
        assertEquals("123", result)
    }

    @Test
    fun testRegexGroupMixedWithCode() {
        // {CODE} and {CODE%N} can be used together
        val result = TemplateProcessor.processTemplate(
            data = "ABC",
            template = "{CODE} / {CODE%2}",
            mode = TemplateProcessor.TemplateMode.HID,
            regexGroups = listOf("ABC", "123")
        )
        assertEquals("ABC / 123", result)
    }

    @Test
    fun testRegexGroupOutOfRange() {
        // {CODE%5} when only 2 groups exist → fallback to data
        val result = TemplateProcessor.processTemplate(
            data = "ABC",
            template = "{CODE%5}",
            mode = TemplateProcessor.TemplateMode.HID,
            regexGroups = listOf("ABC", "123")
        )
        assertEquals("ABC", result)
    }

    @Test
    fun testRegexGroupNoGroups() {
        // {CODE%1} when no groups provided → fallback to data
        val result = TemplateProcessor.processTemplate(
            data = "ABC",
            template = "{CODE%1}",
            mode = TemplateProcessor.TemplateMode.HID,
            regexGroups = emptyList()
        )
        assertEquals("ABC", result)
    }

    @Test
    fun testRegexGroupValidationAcceptsCodeN() {
        // Template with only {CODE%N} should pass validation (not return raw data)
        val result = TemplateProcessor.processTemplate(
            data = "fallback",
            template = "{CODE%2}",
            mode = TemplateProcessor.TemplateMode.HID,
            regexGroups = listOf("part1", "part2")
        )
        assertEquals("part2", result)
    }

    @Test
    fun testRegexGroupRFCOMM() {
        // {CODE%N} works in RFCOMM mode too
        val result = TemplateProcessor.processTemplate(
            data = "full",
            template = "A:{CODE%1} B:{CODE%2}{ENTER}",
            mode = TemplateProcessor.TemplateMode.RFCOMM,
            regexGroups = listOf("hello", "world")
        )
        assertEquals("A:hello B:world\r\n", result)
    }
}
