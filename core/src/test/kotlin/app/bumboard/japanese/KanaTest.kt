package app.bumboard.japanese

import org.junit.Assert.assertEquals
import org.junit.Test

class KanaTest {

    @Test
    fun romaji() {
        assertEquals("kanji", Kana.romaji("かんじ"))
        assertEquals("gakkou", Kana.romaji("がっこう"))
        assertEquals("shashin", Kana.romaji("しゃしん"))
        assertEquals("matcha", Kana.romaji("まっちゃ"))
        assertEquals("kyou", Kana.romaji("きょう"))
        assertEquals("ra-men", Kana.romaji("らーめん"))
        assertEquals("fairu", Kana.romaji("ふぁいる"))
    }

    @Test
    fun loneNBeforeVowels() {
        assertEquals("kin'en", Kana.romaji("きんえん"))
        assertEquals("hon'ya", Kana.romaji("ほんや"))
        assertEquals("kana", Kana.romaji("かな"))
    }

    @Test
    fun smallTsuAtTheEnd() {
        assertEquals("axtsu", Kana.romaji("あっ"))
    }

    @Test
    fun modifierCycles() {
        assertEquals('が', Kana.modify('か'))
        assertEquals('か', Kana.modify('が'))
        assertEquals('ば', Kana.modify('は'))
        assertEquals('ぱ', Kana.modify('ば'))
        assertEquals('は', Kana.modify('ぱ'))
        assertEquals('っ', Kana.modify('つ'))
        assertEquals('づ', Kana.modify('っ'))
        assertEquals('ゃ', Kana.modify('や'))
        assertEquals(null, Kana.modify('ん'))
    }
}
