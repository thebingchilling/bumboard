package app.lychee.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceRomanizerTest {
    @Test
    fun kanaInHepburn() {
        assertEquals("kyouto", SentenceRomanizer.kana("きょうと"))
        assertEquals("gakkou", SentenceRomanizer.kana("がっこう"))
        assertEquals("matchi", SentenceRomanizer.kana("マッチ"))
        assertEquals("raamen", SentenceRomanizer.kana("ラーメン"))
    }

    @Test
    fun japaneseSentencesWordByWord() {
        val readings = mapOf("私" to "わたし", "学生" to "がくせい", "です" to "です")
        assertEquals("watashi wa gakusei desu.", SentenceRomanizer.japanese("私は学生です。") { readings[it] })
    }

    @Test
    fun koreanInRevisedRomanization() {
        assertEquals("hangugeo", SentenceRomanizer.korean("한국어"))
        assertEquals("gamsahamnida", SentenceRomanizer.korean("감사합니다"))
        assertEquals("annyeonghaseyo", SentenceRomanizer.korean("안녕하세요"))
        assertEquals("silla", SentenceRomanizer.korean("신라"))
        assertEquals("annyeong, chingu!", SentenceRomanizer.korean("안녕, 친구!"))
    }
}
