package app.bumboard.keys

import app.bumboard.keys.LetterSymbols.Style
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
        assertEquals("$", LetterSymbols.hint('d', true, "£", Style.PINYIN))
        assertEquals("、", LetterSymbols.hint('z', true, "$", Style.CHINESE))
        assertNull(LetterSymbols.hint('é', true, "$"))
    }

    @Test
    fun popupsStartWithTheHint() {
        assertArrayEquals(arrayOf("1", "%"), LetterSymbols.moreKeys('q', false, "$"))
        assertArrayEquals(arrayOf("%"), LetterSymbols.moreKeys('q', true, "$"))
        assertArrayEquals(arrayOf("\"", "x", "X"), LetterSymbols.moreKeys('x', true, "$", Style.CHINESE))
        assertArrayEquals(arrayOf("@", "a", "A", "ā", "á", "ǎ", "à"), LetterSymbols.moreKeys('a', true, "$", Style.PINYIN))
        assertNull(LetterSymbols.moreKeys('ß', true, "$"))
    }
}
