package app.lychee.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RomanizationTest {

    private fun yale(s: String) = Romanization.jyutpingToYale(s)

    @Test
    fun yaleToneMarksAndLowToneH() {
        assertEquals("sī", yale("si1"))
        assertEquals("sí", yale("si2"))
        assertEquals("si", yale("si3"))
        assertEquals("sìh", yale("si4"))
        assertEquals("síh", yale("si5"))
        assertEquals("sih", yale("si6"))
        assertEquals("yuht", yale("jyut6"))
        assertEquals("màaih", yale("maai4"))
        assertEquals("lòih", yale("loi4"))
        assertEquals("yàhn", yale("jan4"))
        assertEquals("nìhn", yale("nin4"))
        assertEquals("tàuh", yale("tau4"))
        assertEquals("móuh", yale("mou5"))
    }

    @Test
    fun yaleInitialsAndVowels() {
        assertEquals("jūng", yale("zung1"))
        assertEquals("chēung", yale("coeng1"))
        assertEquals("yeuhng", yale("joeng6"))
        assertEquals("hēung", yale("hoeng1"))
        assertEquals("seuih", yale("seoi6"))
        assertEquals("syū", yale("syu1"))
        assertEquals("yùh", yale("jyu4"))
        assertEquals("mā", yale("maa1"))
        assertEquals("gwóng", yale("gwong2"))
        assertEquals("ngóh", yale("ngo5"))
    }

    @Test
    fun yaleSyllabicNasals() {
        assertEquals("m̀h", yale("m4"))
        assertEquals("ńgh", yale("ng5"))
    }

    @Test
    fun yaleNumbered() {
        assertEquals("yut6", Romanization.jyutpingToYaleNumbered("jyut6"))
        assertEquals("jeung1", Romanization.jyutpingToYaleNumbered("zoeng1"))
        assertEquals("ma1", Romanization.jyutpingToYaleNumbered("maa1"))
    }

    @Test
    fun pinyinMarks() {
        assertEquals("zhōng", Romanization.pinyinToMarked("zhong1"))
        assertEquals("guó", Romanization.pinyinToMarked("guo2"))
        assertEquals("hǎo", Romanization.pinyinToMarked("hao3"))
        assertEquals("lǜ", Romanization.pinyinToMarked("lü4"))
        assertEquals("lǜ", Romanization.pinyinToMarked("lu:4"))
        assertEquals("nǚ", Romanization.pinyinToMarked("nv3"))
        assertEquals("ma", Romanization.pinyinToMarked("ma5"))
        assertEquals("tóu", Romanization.pinyinToMarked("tou2"))
        assertEquals("liù", Romanization.pinyinToMarked("liu4"))
        assertEquals("huì", Romanization.pinyinToMarked("hui4"))
        assertEquals("ér", Romanization.pinyinToMarked("er2"))
    }

    @Test
    fun formatWholeReading() {
        assertEquals("néih hóu", Romanization.format("nei5 hou2", Romanization.Style.YaleMarks))
        assertEquals("nǐ hǎo", Romanization.format("ni3 hao3", Romanization.Style.PinyinMarks))
        assertEquals("nei5 hou2", Romanization.format("nei5 hou2", Romanization.Style.JyutpingNumbers))
    }
}
