package app.lychee.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConjugationTest {

    private fun ja(word: String, vclass: String) =
        JapaneseConjugation.table(word, vclass)!!.associate { it.label to it.text }

    private fun ko(lemma: String, forms: List<String>, verb: Boolean = true) =
        KoreanConjugation.table(lemma, forms, verb)!!.associate { it.label to it.text }

    @Test fun ichidan() {
        val t = ja("食べる", "v1")
        assertEquals("食べます", t["Polite"])
        assertEquals("食べない", t["Negative"])
        assertEquals("食べて", t["Te-form"])
        assertEquals("食べられる", t["Potential"])
        assertEquals("食べよう", t["Volitional"])
    }

    @Test fun godan() {
        assertEquals("書いて", ja("書く", "v5k")["Te-form"])
        assertEquals("泳いだ", ja("泳ぐ", "v5g")["Past"])
        assertEquals("話した", ja("話す", "v5s")["Past"])
        assertEquals("待って", ja("待つ", "v5t")["Te-form"])
        assertEquals("死んだ", ja("死ぬ", "v5n")["Past"])
        assertEquals("飲みます", ja("飲む", "v5m")["Polite"])
        assertEquals("買わない", ja("買う", "v5u")["Negative"])
        assertEquals("帰れる", ja("帰る", "v5r")["Potential"])
        assertEquals("読めば", ja("読む", "v5m")["Conditional"])
    }

    @Test fun irregulars() {
        assertEquals("行って", ja("行く", "v5k-s")["Te-form"])
        assertEquals("ない", ja("ある", "v5r-i")["Negative"])
        assertEquals("なかった", ja("ある", "v5r-i")["Negative past"])
        assertEquals("いらっしゃいます", ja("いらっしゃる", "v5aru")["Polite"])
        assertEquals("問うて", ja("問う", "v5u-s")["Te-form"])
        assertEquals("来ない", ja("来る", "vk")["Negative"])
        assertEquals("こない", ja("くる", "vk")["Negative"])
        assertEquals("きます", ja("くる", "vk")["Polite"])
        assertEquals("しない", ja("する", "vs-i")["Negative"])
        assertEquals("勉強します", ja("勉強", "vs")["Polite"])
        assertEquals("勉強できる", ja("勉強", "vs")["Potential"])
    }

    @Test fun adjectives() {
        assertEquals("高くない", ja("高い", "adj-i")["Negative"])
        assertEquals("高かった", ja("高い", "adj-i")["Past"])
        assertEquals("よくない", ja("いい", "adj-ix")["Negative"])
        assertEquals("よかった", ja("いい", "adj-ix")["Past"])
        assertEquals("静かじゃない", ja("静か", "adj-na")["Negative"])
        assertEquals("静かな", ja("静か", "adj-na")["Before a noun"])
    }

    @Test fun noTable() {
        assertNull(JapaneseConjugation.table("本", null))
        assertNull(JapaneseConjugation.table("書く", "v5m"))
    }

    @Test fun koreanRegular() {
        val t = ko("먹다", listOf("먹는", "먹어", "먹으니"))
        assertEquals("먹어요", t["Polite"])
        assertEquals("먹습니다", t["Formal"])
        assertEquals("먹었어요", t["Past"])
        assertEquals("먹을 거예요", t["Future"])
        assertEquals("먹으세요", t["Please (-(으)세요)"])
        assertEquals("먹는", t["Before a noun"])
    }

    @Test fun koreanVowelStem() {
        val t = ko("가다", listOf("가는", "가", "가니"))
        assertEquals("가요", t["Polite"])
        assertEquals("갑니다", t["Formal"])
        assertEquals("갔어요", t["Past"])
        assertEquals("갈 거예요", t["Future"])
    }

    @Test fun koreanIrregular() {
        // ㅂ irregular: 굽다 (bake) -> 구워, 구우니
        val t = ko("굽다", listOf("굽는", "구워", "구우니"))
        assertEquals("구워요", t["Polite"])
        assertEquals("구웠어요", t["Past"])
        assertEquals("구울 거예요", t["Future"])
        assertEquals("굽습니다", t["Formal"])
        // ㄹ stem: 살다 -> 삽니다, 사세요
        val s = ko("살다", listOf("사는", "살아", "사니"))
        assertEquals("삽니다", s["Formal"])
        assertEquals("사세요", s["Please (-(으)세요)"])
        assertEquals("살 거예요", s["Future"])
    }

    @Test fun koreanAdjective() {
        val t = ko("좋다", listOf("좋아", "좋으니"), verb = false)
        assertEquals("좋아요", t["Polite"])
        assertEquals("좋은", t["Before a noun"])
        val k = ko("크다", listOf("커", "크니"), verb = false)
        assertEquals("커요", k["Polite"])
        assertEquals("큰", k["Before a noun"])
        assertEquals("컸어요", k["Past"])
    }

    @Test fun koreanHada() {
        val t = ko("공부하다", listOf("공부하는", "공부하여", "공부하니"))
        assertEquals("공부해요", t["Polite"])
        assertEquals("공부했어요", t["Past"])
        assertEquals("공부합니다", t["Formal"])
    }

    @Test fun pitch() {
        assertEquals(listOf("きょ", "う"), JapanesePitch.morae("きょう"))
        assertEquals(listOf(0, 2), JapanesePitch.numbers("0,2"))
        assertEquals(listOf(0, 3), JapanesePitch.numbers("(副)0,(名)3"))
        // 箸 はし 1: high then low; 橋 はし 2: low then high, falling after
        assertEquals("は\u0305ꜜし", JapanesePitch.marked("はし", 1))
        assertEquals("はし\u0305ꜜ", JapanesePitch.marked("はし", 2))
        assertEquals("がっ\u0305こ\u0305う\u0305", JapanesePitch.marked("がっこう", 0))
        assertEquals("Odaka (falls after the word)", JapanesePitch.typeName("はし", 2))
    }
}
