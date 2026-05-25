package dev.fabik.bluetoothhid.bt

import dev.fabik.bluetoothhid.utils.TemplateProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KeyTranslatorTest {

    private lateinit var translator: KeyTranslator

    @Before
    fun setUp() {
        translator = KeyTranslator(RuntimeEnvironment.getApplication())
    }

    // --- countTypedChars ---

    @Test
    fun `plain string - count equals string length`() {
        assertEquals(6, translator.countTypedChars("ABC123", "us"))
    }

    @Test
    fun `empty string - count is zero`() {
        assertEquals(0, translator.countTypedChars("", "us"))
    }

    @Test
    fun `ENTER template - counts as one character`() {
        // {ENTER} produces one newline on the host → one backspace needed
        assertEquals(1, translator.countTypedChars("{ENTER}", "us"))
    }

    @Test
    fun `TAB template - counts as one character`() {
        assertEquals(1, translator.countTypedChars("{TAB}", "us"))
    }

    @Test
    fun `BKSP template - counts as one character`() {
        // {BKSP} sends a backspace, which deletes one character → one backspace to re-type it
        assertEquals(1, translator.countTypedChars("{BKSP}", "us"))
    }

    @Test
    fun `F1 template - counts as zero characters`() {
        // F-keys produce no visible text → no backspace needed
        assertEquals(0, translator.countTypedChars("{F1}", "us"))
    }

    @Test
    fun `F12 template - counts as zero characters`() {
        assertEquals(0, translator.countTypedChars("{F12}", "us"))
    }

    @Test
    fun `arrow key template - counts as zero characters`() {
        assertEquals(0, translator.countTypedChars("{LEFT}", "us"))
        assertEquals(0, translator.countTypedChars("{RIGHT}", "us"))
        assertEquals(0, translator.countTypedChars("{UP}", "us"))
        assertEquals(0, translator.countTypedChars("{DOWN}", "us"))
    }

    @Test
    fun `ESC template - counts as zero characters`() {
        assertEquals(0, translator.countTypedChars("{ESC}", "us"))
    }

    @Test
    fun `WAIT template - counts as zero characters`() {
        assertEquals(0, translator.countTypedChars("{WAIT:500}", "us"))
    }

    @Test
    fun `CODE template - counts as one character`() {
        // {CODE} is a text-producing placeholder (barcode value)
        assertEquals(1, translator.countTypedChars("{CODE}", "us"))
    }

    @Test
    fun `mixed plain and ENTER - correct total`() {
        // "Hello" (5) + {ENTER} (1) = 6
        assertEquals(6, translator.countTypedChars("Hello{ENTER}", "us"))
    }

    @Test
    fun `mixed plain and F-key - F-key not counted`() {
        // "ABC" (3) + {F1} (0) + "XY" (2) = 5
        assertEquals(5, translator.countTypedChars("ABC{F1}XY", "us"))
    }

    @Test
    fun `multiple text-producing templates`() {
        // {ENTER}(1) + {TAB}(1) + {BKSP}(1) = 3
        assertEquals(3, translator.countTypedChars("{ENTER}{TAB}{BKSP}", "us"))
    }

    @Test
    fun `template with modifier prefix - still zero for F-key`() {
        // {+F1} = Ctrl+F1 → no visible text
        assertEquals(0, translator.countTypedChars("{+F1}", "us"))
    }

    @Test
    fun `real-world barcode template with ENTER suffix`() {
        // Typical CUSTOM template output: barcode + {ENTER}
        // e.g. barcode "1234567890" → processedString = "1234567890{ENTER}"
        assertEquals(11, translator.countTypedChars("1234567890{ENTER}", "us"))
    }

    @Test
    fun `real-world template with F-key prefix and suffix ENTER`() {
        // e.g. {F2}barcode{ENTER}: F2 start signal + data + Enter
        assertEquals(6, translator.countTypedChars("{F2}HELLO{ENTER}", "us"))
    }

    @Test
    fun `template with WAIT between characters - WAIT not counted`() {
        // "AB{WAIT:100}CD" → 4 visible chars
        assertEquals(4, translator.countTypedChars("AB{WAIT:100}CD", "us"))
    }

    // --- translateStringWithTemplate: sentinel / expandCode pipeline ---
    //
    // These tests verify the full TemplateProcessor → KeyTranslator pipeline for the two
    // expandCode paths.  TemplateProcessor.processTemplate is responsible for escaping braces
    // in barcode data when expandCode=false; translateStringWithTemplate is responsible for
    // decoding the sentinels back to { } before keymap lookup.
    //
    // The assertions below use the HID TAB keycode (0x2B) from staticTemplates rather than
    // regular characters like 'A' or 'B'.  staticTemplates are hardcoded in KeyTranslator
    // and do not depend on keymap lookup, making them reliable anchors for these tests
    // regardless of how keymaps happen to be loaded in the test environment.
    // countTypedChars is purely regex-based and also needs no keymap.

    /** HID TAB keycode (0x2B, no modifier) from KeyTranslator.staticTemplates. */
    private val HID_TAB_CODE: UByte = 0x2Bu
    private val NO_MODIFIER: UByte = 0u

    @Test
    fun `expandCode false - escaped TAB in barcode NOT emitted as HID Tab keypress`() {
        // TemplateProcessor with expandCode=false escapes "A{TAB}B" to "ATABB"
        // (where  and  are the PUA sentinels).
        // translateStringWithTemplate must decode the sentinels and pass "A{TAB}B" to
        // translateString as plain characters — so {TAB} is NOT matched as a HID template.
        val esc = TemplateProcessor.ESCAPED_OPEN_BRACE
        val escClose = TemplateProcessor.ESCAPED_CLOSE_BRACE
        val escaped = "A${esc}TAB${escClose}B"

        val keys = translator.translateStringWithTemplate(escaped, "us")

        assertFalse(
            "HID Tab keycode (0x2B, no modifier) must not appear — {TAB} was barcode data with expandCode=false",
            keys.any { it.second == HID_TAB_CODE && it.first == NO_MODIFIER }
        )
    }

    @Test
    fun `expandCode true - unescaped TAB in barcode emitted as real HID Tab keypress`() {
        // TemplateProcessor with expandCode=true leaves "A{TAB}B" as-is.
        // translateStringWithTemplate matches {TAB} via the template regex and emits the
        // real HID Tab keycode (0x2B, no modifier) from staticTemplates — no keymap needed.
        val keys = translator.translateStringWithTemplate("A{TAB}B", "us")

        assertTrue(
            "HID Tab keycode (0x2B, no modifier) must appear — {TAB} from barcode should expand",
            keys.count { it.second == HID_TAB_CODE && it.first == NO_MODIFIER } == 1
        )
    }

    @Test
    fun `expandCode false - countTypedChars counts each sentinel as one visible character`() {
        // Each sentinel (ESCAPED_OPEN_BRACE / ESCAPED_CLOSE_BRACE) maps to one typed
        // character on the host ({ or }), so countTypedChars must treat them as plain chars.
        // "ATABB" (7 chars) should count as 7 — one per visible host character.
        val esc = TemplateProcessor.ESCAPED_OPEN_BRACE
        val escClose = TemplateProcessor.ESCAPED_CLOSE_BRACE
        assertEquals(7, translator.countTypedChars("A${esc}TAB${escClose}B", "us"))
    }

    @Test
    fun `expandCode false - countTypedChars gives correct undo count for escaped barcode plus ENTER`() {
        // For barcode "A{TAB}B" (escapeed, expandCode=false) with {ENTER} from the template:
        // countTypedChars must return 8 (7 literal chars + 1 for {ENTER}).
        // This is purely regex-based and works without keymap assets.
        val esc = TemplateProcessor.ESCAPED_OPEN_BRACE
        val escClose = TemplateProcessor.ESCAPED_CLOSE_BRACE
        val processedString = "A${esc}TAB${escClose}B{ENTER}"
        assertEquals(8, translator.countTypedChars(processedString, "us"))
    }

}