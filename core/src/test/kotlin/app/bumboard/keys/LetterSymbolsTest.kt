package app.bumboard.keys

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LetterSymbolsTest {
    @Test
    fun hintsFollowTheNumberRow() {
        assertEquals("%", LetterSymbols.hint('q', true, "$"))
        assertEquals("1", LetterSymbols.hint('q', false, "$"))
        assertEquals("@", LetterSymbols.hint('A', false, "$"))
        assertEquals("£", LetterSymbols.hint('d', true, "£"))
        assertNull(LetterSymbols.hint('é', true, "$"))
    }

    @Test
    fun popupsStartWithTheHint() {
        assertArrayEquals(arrayOf("1", "%", "‰"), LetterSymbols.moreKeys('q', false, "$"))
        assertArrayEquals(arrayOf("%", "‰"), LetterSymbols.moreKeys('q', true, "$"))
        val d = LetterSymbols.moreKeys('d', true, "£")!!
        assertEquals("£", d[0])
        assertEquals(1, d.count { it == "£" })
        assertNull(LetterSymbols.moreKeys('ß', true, "$"))
    }
}
