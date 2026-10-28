package app.lychee.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotatorTest {

    private val hang = Headword(
        1, "行", "行", 1000,
        mapOf(Lang.Mandarin to listOf("xing2", "hang2"), Lang.Cantonese to listOf("hang4", "hong4")),
        listOf(
            Sense(Lang.Mandarin, "hang2", "(bound form) row; line", alt = "hong4"),
            Sense(Lang.Mandarin, "xing2", "to walk; to go", alt = "hang4"),
            Sense(Lang.Cantonese, "hong4", "row"),
            Sense(Lang.Cantonese, "hang4", "walk"),
        )
    )
    private val neihou = Headword(
        2, "你好", "你好", 500,
        mapOf(Lang.Mandarin to listOf("ni3 hao3"), Lang.Cantonese to listOf("nei5 hou2")),
        listOf(Sense(Lang.Mandarin, "ni3 hao3", "hello; hi", alt = "nei5 hou2"))
    )
    private val ngo = Headword(
        3, "我", "我", 900,
        mapOf(Lang.Mandarin to listOf("wo3"), Lang.Cantonese to listOf("ngo5")),
        listOf(Sense(Lang.Mandarin, "wo3", "I; me"))
    )
    private val dict = Dictionary { form ->
        listOf(hang, neihou, ngo).filter { it.traditional == form || it.simplified == form }
    }
    private val annotator = Annotator(dict)

    @Test
    fun cantoneseHintPicksReadingAndMeaning() {
        val a = annotator.annotate("行", "hong4", InputMode.Cantonese, DisplayPrefs())
        assertEquals(listOf("hong4"), a.lines)
        assertEquals("row", a.meaning)
        val b = annotator.annotate("行", "hang4", InputMode.Cantonese, DisplayPrefs())
        assertEquals("walk", b.meaning)
    }

    @Test
    fun mandarinTonelessHint() {
        val a = annotator.annotate("行", "hang", InputMode.Mandarin, DisplayPrefs(showOtherLanguage = true))
        assertEquals(listOf("háng", "hong4"), a.lines)
        assertEquals("row", a.meaning)
    }

    @Test
    fun yaleAndBoth() {
        val prefs = DisplayPrefs(cantonese = CantoneseRomanization.Both)
        val a = annotator.annotate("你好", "nei5 hou2", InputMode.Cantonese, prefs)
        assertEquals(listOf("néih hóu", "nei5 hou2"), a.lines)
        assertEquals("hello", a.meaning)
    }

    @Test
    fun englishLookupIgnoresComment() {
        val a = annotator.annotate("你好", "", InputMode.EnglishToMandarin, DisplayPrefs())
        assertEquals(listOf("nǐ hǎo"), a.lines)
    }

    @Test
    fun composedFromPieces() {
        val a = annotator.annotate("我你好", "ngo5 nei5 hou2", InputMode.Cantonese, DisplayPrefs())
        assertEquals(listOf("ngo5 nei5 hou2"), a.lines)
        // every phrase has a meaning: its pieces'
        assertEquals("I + hello", a.meaning)
        assertTrue(a.resolved!!.composed)
    }

    @Test
    fun nonChineseHasNoAnnotation() {
        assertEquals(Annotation.Empty, annotator.annotate("，", "", InputMode.Cantonese, DisplayPrefs()))
        assertEquals(Annotation.Empty, annotator.annotate("ok", "", InputMode.Cantonese, DisplayPrefs()))
    }

    @Test
    fun shortenMeaning() {
        assertEquals("row", Annotator.shorten("(bound form) row; line"))
        assertEquals("hello", Annotator.shorten("hello; hi"))
    }

    @Test
    fun tonesFollowLines() {
        val a = annotator.annotate("你好", "ni hao", InputMode.Mandarin, DisplayPrefs(showOtherLanguage = true))
        assertEquals(listOf(listOf(3, 3), listOf(5, 2)), a.tones)
        assertEquals(listOf(0, 1), Annotator.tones("ma5 ma1", Lang.Mandarin))
    }

    @Test
    fun measureWords() {
        val info = WordInfo(measureWords = "個|个[ge4],本[ben3]")
        assertEquals(listOf(Triple("個", "个", "ge4"), Triple("本", "本", "ben3")), info.measureWordList())
    }
}
