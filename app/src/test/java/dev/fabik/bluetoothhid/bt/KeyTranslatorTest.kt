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

    /**
     * Counts visible characters produced by [str] through the production pipeline
     * ([translateStringWithTemplateDetailed]).  This is exactly what [KeyboardSender] counts
     * in-loop to determine how many backspaces are needed for undo.
     *
     * Plain characters are tested using space `' '`, tab `'\t'`, and newline `'\n'` — these
     * are always available in [KeyTranslator.baseMap] regardless of keymap asset loading.
     * Template tokens ({ENTER}, {F1}, etc.) are handled by staticTemplates and also need
     * no keymap.
     */
    private fun countChars(str: String) =
        translator.translateStringWithTemplateDetailed(str, "us").count { (_, c) -> c }

    // --- visible-character counting via translateStringWithTemplateDetailed ---

    @Test
    fun `plain string - each space from baseMap counts as one visible key`() {
        // Space is hardcoded in KeyTranslator.baseMap; no keymap asset needed
        assertEquals(6, countChars("      "))
    }

    @Test
    fun `empty string - count is zero`() {
        assertEquals(0, countChars(""))
    }

    @Test
    fun `ENTER template - counts as one character`() {
        // {ENTER} produces one newline on the host → one backspace needed
        assertEquals(1, countChars("{ENTER}"))
    }

    @Test
    fun `TAB template - counts as one character`() {
        assertEquals(1, countChars("{TAB}"))
    }

    @Test
    fun `BKSP template - counts as zero characters`() {
        // {BKSP} *removes* a character on the host — it does not add one.
        // Counting it as visible would cause undo to send one extra backspace per {BKSP},
        // deleting a character the user never typed.  Undo simply ignores backspace keypresses.
        assertEquals(0, countChars("{BKSP}"))
    }

    @Test
    fun `F1 template - counts as zero characters`() {
        // F-keys produce no visible text → no backspace needed
        assertEquals(0, countChars("{F1}"))
    }

    @Test
    fun `F12 template - counts as zero characters`() {
        assertEquals(0, countChars("{F12}"))
    }

    @Test
    fun `arrow key template - counts as zero characters`() {
        assertEquals(0, countChars("{LEFT}"))
        assertEquals(0, countChars("{RIGHT}"))
        assertEquals(0, countChars("{UP}"))
        assertEquals(0, countChars("{DOWN}"))
    }

    @Test
    fun `ESC template - counts as zero characters`() {
        assertEquals(0, countChars("{ESC}"))
    }

    @Test
    fun `WAIT template - counts as zero characters`() {
        assertEquals(0, countChars("{WAIT:500}"))
    }

    @Test
    fun `CODE template unsubstituted - does not crash, returns non-empty or empty list`() {
        // In production, TemplateProcessor always substitutes {CODE} before KeyTranslator sees
        // the string.  If {CODE} somehow survives (bug in caller), KeyTranslator logs a warning
        // and types the literal letters C, O, D, E rather than throwing an exception.
        // We can't assert on count (letter availability depends on loaded keymap assets),
        // but we can assert no exception is thrown and the result has at most 4 keys (C,O,D,E).
        val result = translator.translateStringWithTemplateDetailed("{CODE}", "us")
        assertTrue("Fallback must produce at most 4 keys (C,O,D,E)", result.size <= 4)
    }

    @Test
    fun `mixed plain spaces and ENTER - correct total`() {
        // 5 spaces (baseMap) + {ENTER} (1) = 6
        assertEquals(6, countChars("     {ENTER}"))
    }

    @Test
    fun `mixed plain spaces and F-key - F-key not counted`() {
        // "   " (3) + {F1} (0) + "  " (2) = 5
        assertEquals(5, countChars("   {F1}  "))
    }

    @Test
    fun `multiple text-producing templates`() {
        // {ENTER}(1) + {TAB}(1) + {BKSP}(0) = 2  — BKSP removes, does not add
        assertEquals(2, countChars("{ENTER}{TAB}{BKSP}"))
    }

    @Test
    fun `template with modifier prefix - still zero for F-key`() {
        // {+F1} = Ctrl+F1 → no visible text
        assertEquals(0, countChars("{+F1}"))
    }

    @Test
    fun `modifier plus text-producing static template - completesChar is false`() {
        // Bug regression: before the fix, {+ENTER}/{+TAB} were incorrectly counted as visible
        // characters because isTextProducingKeycode only checked the keycode, not whether extra
        // modifiers turned it into a control combo.
        // Ctrl+Enter / Ctrl+Tab do NOT produce visible text on the host.
        assertEquals("Ctrl+Enter must not count as visible char", 0, countChars("{+ENTER}"))
        assertEquals("Ctrl+Tab must not count as visible char",   0, countChars("{+TAB}"))
        // {+BKSP} is also 0 — both because Backspace is excluded from visible range (it removes
        // rather than adds a char) and because the modifier makes it a control combo anyway.
        assertEquals("Ctrl+Bksp must not count as visible char",  0, countChars("{+BKSP}"))
    }

    @Test
    fun `modifier plus text-producing static template - completesChar flag is false`() {
        // Verify the completesChar flag itself, not just the count.
        listOf("{+ENTER}", "{+TAB}", "{+BKSP}").forEach { token ->
            val keys = translator.translateStringWithTemplateDetailed(token, "us")
            assertEquals("$token should produce exactly 1 key event", 1, keys.size)
            assertFalse("$token completesChar must be false (modifier turns it into a control combo)",
                keys.single().second)
        }
    }

    @Test
    fun `F13 to F24 templates - all count as zero characters`() {
        // F13–F24 added by WinLin97; keycodes 0x68–0x73 are outside the 0x04..0x38 visible range.
        (13..24).forEach { n ->
            assertEquals("F$n should not count as visible char", 0, countChars("{F$n}"))
        }
    }

    @Test
    fun `WAIT with zero count - produces no key events`() {
        // {WAIT:0} → nCopies(0, Key(0u, 0u)) = empty list → zero events, zero visible chars
        val keys = translator.translateStringWithTemplateDetailed("{WAIT:0}", "us")
        assertEquals("WAIT:0 must produce no key events", 0, keys.size)
        assertEquals(0, countChars("{WAIT:0}"))
    }

    @Test
    fun `real-world barcode with ENTER suffix - correct visible count`() {
        // Typical CUSTOM template: 10-char barcode + {ENTER} → 11 visible chars for undo
        assertEquals(11, countChars("          {ENTER}"))
    }

    @Test
    fun `real-world template with F-key prefix and suffix ENTER`() {
        // {F2}(0) + 5 plain chars (5) + {ENTER}(1) = 6
        assertEquals(6, countChars("{F2}     {ENTER}"))
    }

    @Test
    fun `template with WAIT between chars - WAIT not counted`() {
        // "  " (2) + {WAIT:100} (0) + "  " (2) = 4
        assertEquals(4, countChars("  {WAIT:100}  "))
    }

    // --- completesChar flag correctness ---

    @Test
    fun `ENTER key completesChar is true`() {
        val keys = translator.translateStringWithTemplateDetailed("{ENTER}", "us")
        assertEquals(1, keys.size)
        assertTrue("ENTER should complete a visible char", keys.single().second)
    }

    @Test
    fun `F1 key completesChar is false`() {
        val keys = translator.translateStringWithTemplateDetailed("{F1}", "us")
        assertEquals(1, keys.size)
        assertFalse("F1 should not complete a visible char", keys.single().second)
    }

    @Test
    fun `ESC key completesChar is false`() {
        val keys = translator.translateStringWithTemplateDetailed("{ESC}", "us")
        assertEquals(1, keys.size)
        assertFalse("ESC should not complete a visible char", keys.single().second)
    }

    @Test
    fun `WAIT keys completesChar is false`() {
        val keys = translator.translateStringWithTemplateDetailed("{WAIT:2}", "us")
        assertTrue("All WAIT keys should not complete visible chars", keys.all { !it.second })
    }

    // --- translateStringWithTemplateDetailed: sentinel / expandCode pipeline ---
    //
    // These tests verify the full TemplateProcessor → KeyTranslator pipeline for the two
    // expandCode paths.  TemplateProcessor.processTemplate is responsible for escaping braces
    // in barcode data when expandCode=false; translateStringWithTemplateDetailed is responsible
    // for decoding the sentinels back to { } before keymap lookup.
    //
    // Assertions use the HID TAB keycode (0x2B) from staticTemplates — hardcoded in
    // KeyTranslator and available without any keymap asset.

    /** HID TAB keycode (0x2B, no modifier) from KeyTranslator.staticTemplates. */
    private val HID_TAB_CODE: UByte = 0x2Bu
    private val NO_MODIFIER: UByte = 0u

    @Test
    fun `expandCode false - escaped TAB in barcode NOT emitted as HID Tab keypress`() {
        // TemplateProcessor with expandCode=false escapes "A{TAB}B" to "ATABB"
        // (where  and  are the PUA sentinels).
        // translateStringWithTemplateDetailed must decode the sentinels and pass "{TAB}"
        // to translateStringDetailed as plain characters — NOT matched as an HID template.
        val esc = TemplateProcessor.ESCAPED_OPEN_BRACE
        val escClose = TemplateProcessor.ESCAPED_CLOSE_BRACE
        val escaped = "A${esc}TAB${escClose}B"

        val keys = translator.translateStringWithTemplateDetailed(escaped, "us")

        assertFalse(
            "HID Tab keycode (0x2B, no modifier) must not appear — {TAB} was barcode data with expandCode=false",
            keys.any { (key, _) -> key.second == HID_TAB_CODE && key.first == NO_MODIFIER }
        )
    }

    @Test
    fun `expandCode true - unescaped TAB in barcode emitted as real HID Tab keypress`() {
        // TemplateProcessor with expandCode=true leaves "A{TAB}B" as-is.
        // translateStringWithTemplateDetailed matches {TAB} via the template regex and emits
        // the real HID Tab keycode (0x2B, no modifier) from staticTemplates.
        val keys = translator.translateStringWithTemplateDetailed("A{TAB}B", "us")

        assertTrue(
            "HID Tab keycode (0x2B, no modifier) must appear — {TAB} from barcode should expand",
            keys.count { (key, _) -> key.second == HID_TAB_CODE && key.first == NO_MODIFIER } == 1
        )
    }

    @Test
    fun `expandCode false - sentinel chars completesChar=true, not treated as template markers`() {
        // Sentinels from expandCode=false decode back to { and }.  Crucially they must NOT
        // be re-matched as HID template brackets — the surrounding space chars (baseMap,
        // keymap-independent) confirm the non-template path through translateStringDetailed.
        val esc = TemplateProcessor.ESCAPED_OPEN_BRACE
        val escClose = TemplateProcessor.ESCAPED_CLOSE_BRACE

        // " {sentinel}TAB{sentinel} " → sentinels become { and } after restore,
        // so the string is processed as plain text " {TAB} " (spaces + literal braces + TAB letters)
        val result = translator.translateStringWithTemplateDetailed(
            " ${esc}TAB${escClose} ", "us"
        )

        // The real HID Tab must NOT appear — {TAB} was literal barcode data
        assertFalse(
            "Sentinel-wrapped TAB must not emit HID Tab keycode",
            result.any { (key, _) -> key.second == HID_TAB_CODE && key.first == NO_MODIFIER }
        )
        // The 2 surrounding spaces (baseMap) must be counted as visible chars
        assertEquals(
            "Surrounding space chars must be completesChar=true",
            2,
            result.count { (key, completesChar) -> key.second == 0x2C.toUByte() && completesChar }
        )
    }

    @Test
    fun `expandCode false - sentinel barcode plus ENTER suffix gives correct undo count`() {
        // Simulate: spaces (barcode proxy) + sentinel-wrapped content + {ENTER}.
        // Spaces count reliably (baseMap); ENTER counts from staticTemplates.
        val esc = TemplateProcessor.ESCAPED_OPEN_BRACE
        val escClose = TemplateProcessor.ESCAPED_CLOSE_BRACE

        // 2 spaces + sentinel brackets + 3 letters + sentinel + space + {ENTER}
        // Spaces (3 total) + {ENTER} (1) = at least 4 visible chars regardless of keymap
        val result = translator.translateStringWithTemplateDetailed(
            "  ${esc}TAB${escClose} {ENTER}", "us"
        )
        val visible = result.count { (_, c) -> c }

        // At minimum: 3 spaces + 1 ENTER = 4 (braces/letters may add more if keymap loaded)
        assertTrue("At least spaces + ENTER must be visible chars (got $visible)", visible >= 4)
        // The {TAB} in sentinel must NOT have become a real HID Tab
        assertFalse(
            "Sentinel-wrapped TAB must not emit HID Tab keycode",
            result.any { (key, _) -> key.second == HID_TAB_CODE && key.first == NO_MODIFIER }
        )
    }
}
