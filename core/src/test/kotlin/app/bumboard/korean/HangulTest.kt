package app.bumboard.korean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HangulTest {

    /** Types [jamo] and returns everything committed plus what is still being composed. */
    private fun typed(jamo: String): String {
        val c = HangulComposer()
        val out = StringBuilder()
        jamo.forEach { out.append(c.type(it)) }
        return out.append(c.current.text).toString()
    }

    @Test
    fun syllables() {
        assertEquals("한", typed("ㅎㅏㄴ"))
        assertEquals("한글", typed("ㅎㅏㄴㄱㅡㄹ"))
        assertEquals("안녕하세요", typed("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ"))
        assertEquals("없어", typed("ㅇㅓㅂㅅㅇㅓ"))
    }

    @Test
    fun compoundVowelsAndFinals() {
        assertEquals("과", typed("ㄱㅗㅏ"))
        assertEquals("뭐", typed("ㅁㅜㅓ"))
        assertEquals("의", typed("ㅇㅡㅣ"))
        assertEquals("닭", typed("ㄷㅏㄹㄱ"))
        assertEquals("값", typed("ㄱㅏㅂㅅ"))
    }

    @Test
    fun finalMovesToNextSyllable() {
        assertEquals("달가", typed("ㄷㅏㄹㄱㅏ"))
        assertEquals("가나", typed("ㄱㅏㄴㅏ"))
        assertEquals("까치", typed("ㄲㅏㅊㅣ"))
    }

    @Test
    fun loneJamo() {
        assertEquals("ㅋㅋㅋ", typed("ㅋㅋㅋ"))
        assertEquals("ㅏ", typed("ㅏ"))
        // ㄸ cannot end a syllable
        assertEquals("가ㄸ", typed("ㄱㅏㄸ"))
    }

    @Test
    fun backspaceRemovesOneJamo() {
        val c = HangulComposer()
        "ㄷㅏㄹㄱ".forEach { c.type(it) }
        assertEquals("닭", c.current.text)
        c.backspace()
        assertEquals("달", c.current.text)
        c.backspace()
        assertEquals("다", c.current.text)
        c.backspace()
        assertEquals("ㄷ", c.current.text)
        c.backspace()
        assertFalse(c.isComposing)
        assertFalse(c.backspace())
    }

    @Test
    fun backspaceAfterFinalMoved() {
        val c = HangulComposer()
        "ㄷㅏㄹㄱㅏ".forEach { c.type(it) }
        assertEquals("가", c.current.text)
        c.backspace()
        assertEquals("ㄱ", c.current.text)
    }
}
