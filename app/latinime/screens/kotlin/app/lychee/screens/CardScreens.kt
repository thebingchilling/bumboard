package app.lychee.screens

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.lychee.chinese.CandidateBarView
import app.lychee.chinese.DictionaryPopup
import app.lychee.rime.Candidate
import app.lychee.chinese.DictionaryPopup.Page
import app.lychee.chinese.Readings
import app.lychee.core.Annotator
import app.lychee.core.CantoneseRomanization
import app.lychee.core.Dictionary
import app.lychee.core.DisplayPrefs
import app.lychee.core.Headword
import app.lychee.core.InputMode
import app.lychee.core.Lang
import app.lychee.core.Sense
import app.lychee.dict.WordEntry
import app.lychee.settings.KeyboardPreviewView
import com.android.resources.NightMode
import org.junit.Rule
import org.junit.Test

/** A small sample dictionary standing in for Lychee's (which is built in CI only). */
private fun hw(id: Long, t: String, s: String, yue: List<String>, cmn: List<String>, vararg senses: Sense) =
    Headword(id, t, s, 900, mapOf(Lang.Cantonese to yue, Lang.Mandarin to cmn), senses.toList())

private val words = listOf(
    hw(1, "學校", "学校", listOf("hok6 haau6"), listOf("xue2 xiao4"),
        Sense(Lang.Cantonese, "hok6 haau6", "school; educational institution"),
        Sense(Lang.Cantonese, "hok6 haau6", "(attributive) school-; academic, e.g. 學校生活 school life"),
        Sense(Lang.Mandarin, "xue2 xiao4", "school; CL:所[suo3]")),
    hw(2, "學", "学", listOf("hok6"), listOf("xue2"),
        Sense(Lang.Cantonese, "hok6", "to learn; to study"), Sense(Lang.Mandarin, "xue2", "to learn; to study; science; -ology")),
    hw(3, "校", "校", listOf("haau6", "gaau3"), listOf("xiao4", "jiao4"),
        Sense(Lang.Cantonese, "haau6", "school; military officer"), Sense(Lang.Cantonese, "gaau3", "to check; to proofread"),
        Sense(Lang.Mandarin, "xiao4", "school"), Sense(Lang.Mandarin, "jiao4", "to proofread; to compare")),
    hw(4, "行", "行", listOf("haang4", "hong4", "hang6"), listOf("xing2", "hang2"),
        Sense(Lang.Cantonese, "haang4", "to walk; to go; to travel"), Sense(Lang.Cantonese, "hong4", "row; line; profession; firm"),
        Sense(Lang.Cantonese, "hang6", "conduct; behaviour"), Sense(Lang.Mandarin, "xing2", "to walk; to go; capable; OK"),
        Sense(Lang.Mandarin, "hang2", "row; line; commercial firm")),
    hw(5, "我", "我", listOf("ngo5"), listOf("wo3"), Sense(Lang.Mandarin, "wo3", "I; me; my")),
    hw(6, "今日", "今日", listOf("gam1 jat6"), listOf("jin1 ri4"), Sense(Lang.Cantonese, "gam1 jat6", "today")),
    hw(7, "去", "去", listOf("heoi3"), listOf("qu4"), Sense(Lang.Cantonese, "heoi3", "to go; to leave")),
    hw(8, "嘢", "嘢", listOf("je5"), listOf("ye3")),
    hw(9, "打", "打", listOf("daa2", "daa1"), listOf("da3", "da2"),
        Sense(Lang.Cantonese, "daa2", "to hit; to strike; to beat"), Sense(Lang.Cantonese, "daa2", "to fight; to attack"),
        Sense(Lang.Cantonese, "daa2", "to make a phone call; to send a telegram"), Sense(Lang.Cantonese, "daa2", "to play (a ball game, cards, mahjong)"),
        Sense(Lang.Cantonese, "daa2", "to type; to print; to draw up (a plan)"), Sense(Lang.Cantonese, "daa2", "to work at (a job), e.g. 打工 to work for someone"),
        Sense(Lang.Cantonese, "daa2", "to break; to smash (dishes, eggs)"), Sense(Lang.Cantonese, "daa2", "to open; to dig (a well); to build"),
        Sense(Lang.Cantonese, "daa1", "dozen (loanword from English)"),
        Sense(Lang.Mandarin, "da3", "to beat; to strike; to hit; to break; to type; to mix up; to build; to fight; to fetch; to make; to tie up; to issue; to shoot; to calculate; to play (a game); since; from"),
        Sense(Lang.Mandarin, "da2", "dozen (loanword)")),
    hw(10, "子", "子", listOf("zi2"), listOf("zi3"), Sense(Lang.Cantonese, "zi2", "child; son; seed")),
    hw(11, "冖", "冖", listOf("mik6"), listOf("mi4"), Sense(Lang.Cantonese, "mik6", "cover; radical 14")),
    hw(12, "覺", "觉", listOf("gok3"), listOf("jue2"), Sense(Lang.Cantonese, "gok3", "to feel; to find that")),
    hw(13, "爻", "爻", listOf("ngaau4"), listOf("yao2"), Sense(Lang.Cantonese, "ngaau4", "lines of a hexagram")),
)

/** Stand-in stroke orders for the screenshots (the strokes pack is built in CI): a few plain strokes. */
private fun sampleStrokes(ch: String): app.lychee.dict.CharStrokes {
    val all = listOf(
        listOf(300f to 820f, 380f to 700f), listOf(500f to 840f, 500f to 700f), listOf(700f to 820f, 620f to 700f),
        listOf(200f to 640f, 824f to 640f), listOf(200f to 640f, 200f to 540f), listOf(824f to 640f, 780f to 540f),
        listOf(320f to 480f, 700f to 480f, 540f to 360f), listOf(512f to 360f, 512f to 60f, 440f to 100f), listOf(220f to 260f, 804f to 260f),
    )
    val n = 5 + (ch.codePointAt(0) % 5)
    return app.lychee.dict.CharStrokes(ch, emptyList(), all.take(n), mapOf("學" to "⿳爻冖子")[ch], null, null, null)
}
private val annotator = Annotator(Dictionary { form -> words.filter { it.traditional == form || it.simplified == form } })

/** A Galaxy S23: 1080 × 2340 px at 425 dpi (about 411 × 891 dp), a regular phone. */
val S23 = DeviceConfig.PIXEL_6.copy(screenWidth = 1080, screenHeight = 2340, xdpi = 425, ydpi = 425,
    density = com.android.resources.Density.create(420))

/** What the candidate bar shows while the card is open: the letters typed, the candidates and their lines. */
class BarSample(val typed: String, val candidates: List<String>, val lines: CandidateBarView.Lines,
                val fontLang: String, val script: Boolean, val annotate: (Candidate) -> app.lychee.core.Annotation?)

private val chineseBar by lazy {
    BarSample("hokhaau", listOf("學校", "學", "鶴", "學好", "學效", "殼", "确"), CandidateBarView.Lines(2, true), "yue", script = true) { c ->
        annotator.annotate(c.text, "", InputMode.Cantonese, prefs)
    }
}

/**
 * Draws a card above the keyboard as the phone shows it (the whole phone: status bar, the app, the
 * candidate bar in full, the keys); the card gets the room the real code gives it.
 */
private fun drawCard(paparazzi: Paparazzi, name: String, tall: Boolean = false, bar: BarSample? = chineseBar,
                     show: (DictionaryPopup, FrameLayout) -> Unit) {
    Readings.useForScreens(annotator)
    app.lychee.dict.PackData.forScreensStrokes = { ch -> sampleStrokes(ch) }
    app.lychee.dict.PackData.forScreensContaining = { ch -> if (ch == "學") listOf("覺", "黌", "斆", "澩", "鷽", "嶨") else emptyList() }
    drawPhone(paparazzi, name, if (bar != null) TopBar.Candidates(bar) else TopBar.Shortcuts) { phone ->
        val ctx = paparazzi.context
        val dm = ctx.resources.displayMetrics
        fun dp(v: Int) = (v * dm.density).toInt()
        val space = phone.keyboardTop - phone.statusBar - dp(12)
        val height = if (tall) space else DictionaryPopup.cardHeight(space, dm.density)
        val host = FrameLayout(ctx)
        phone.screen.addView(host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height + dp(30), Gravity.BOTTOM).apply {
            bottomMargin = dm.heightPixels - phone.keyboardTop
        })
        val popup = DictionaryPopup(ctx, standalone = host, standaloneRoom = height, asKeyboard = true)
        show(popup, host)
    }
}

/** Every card is drawn with Yale for Cantonese and the Mandarin reading shown too (the owner's choice for screens). */
private val prefs = DisplayPrefs(cantonese = CantoneseRomanization.Yale, showOtherLanguage = true)

private fun chinese(paparazzi: Paparazzi, name: String, text: String, page: Page? = null, mode: InputMode = InputMode.Cantonese,
                    tall: Boolean = false, peek: Boolean = false) =
    drawCard(paparazzi, name, tall) { popup, host ->
        popup.startPage = page
        val r = annotator.resolve(text, "", mode)!!
        popup.show(host, text, r, prefs, peek = peek, onSpeak = { _, _ -> }) {}
    }

private val japaneseBar = BarSample("taberu", listOf("食べる", "たべる", "食べ", "食", "タベル"), CandidateBarView.Lines(1, true), "ja", script = false) { c ->
    mapOf("食べる" to ("taberu" to "to eat"), "たべる" to ("taberu" to "to eat"), "食べ" to ("tabe" to "eating"), "食" to ("shoku" to "food"),
        "タベル" to ("taberu" to null)
    )[c.text]?.let { (r, m) -> app.lychee.core.Annotation(listOf(r), m, null) }
}
private val koreanBar = BarSample("hakgyo", listOf("학교", "學校", "학교에", "학교의", "학구"), CandidateBarView.Lines(1, true), "ko", script = false) { c ->
    mapOf("학교" to ("hakgyo" to "school"), "學校" to ("hakgyo" to "school"), "학교에" to ("hakgyoe" to "to school"),
        "학교의" to ("hakgyoui" to "school's"), "학구" to ("hakgu" to "study"))[c.text]?.let { (r, m) -> app.lychee.core.Annotation(listOf(r), m, null) }
}

private fun japaneseCard(paparazzi: Paparazzi, name: String, tall: Boolean = false, page: Page? = null) = drawCard(paparazzi, name, tall, japaneseBar) { popup, host ->
    popup.startPage = page
    popup.showWord(host, "食べる", "たべる  taberu",
        listOf(WordEntry("食べる", "食べる", "たべる", null, "Ichidan verb, transitive", listOf("to eat", "to live on (e.g. a salary); to live off; to subsist on"))),
        listOf("毎朝パンを食べる。" to "I eat bread every morning."), peek = false, onSpeak = { _, _ -> }, lang = "ja",
        onSpeakAs = { _, _ -> }, badges = listOf("JLPT N5"), pronunciation = "Pitch  たべ̅ꜜる  ·  Nakadaka (falls in the middle)",
        characters = listOf(DictionaryPopup.CharReading("食", listOf("On" to listOf("ショク" to "shoku"), "Kun" to listOf("た(べる)" to "ta(beru)")), "たべる", "eat; food")),
        charactersTitle = "Kanji") {}
}

private fun koreanCard(paparazzi: Paparazzi, name: String, tall: Boolean = false) = drawCard(paparazzi, name, tall, koreanBar) { popup, host ->
    popup.showWord(host, "학교", "hakgyo",
        listOf(WordEntry("학교", "학교", "hakgyo", "學校", "noun", listOf("school", "an institution where students are taught"))),
        listOf("저는 학교에 가요." to "I go to school."), peek = false, onSpeak = { _, _ -> }, lang = "ko", onSpeakAs = { _, _ -> },
        badges = listOf("Beginner level"), pronunciation = "Said  [학꾜] hakkyo",
        characters = listOf(DictionaryPopup.CharReading("學", listOf("Korean" to listOf("학" to "hak")), "학", "learn"),
            DictionaryPopup.CharReading("校", listOf("Korean" to listOf("교" to "gyo")), "교", "school")),
        charactersTitle = "Hanja") {}
}


/** The dictionary card in light mode, Chinese, Japanese, Korean and English, on each tab. */
class CardScreens {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = S23, theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun summary() = chinese(paparazzi, "1-summary", "學校")
    @Test fun readings() = chinese(paparazzi, "2-readings-hang", "行", Page.READINGS)
    @Test fun definitions() = chinese(paparazzi, "3-definitions-hang", "行", Page.DEFINITIONS)
    @Test fun parts() = chinese(paparazzi, "4-parts-xuexiao", "學校", Page.PARTS)
    @Test fun phrase() = chinese(paparazzi, "5-phrase-not-in-dictionary", "我今日去學校")
    @Test fun phraseParts() = chinese(paparazzi, "6-phrase-parts", "我今日去學校", Page.PARTS)

    @Test fun summaryHang() = chinese(paparazzi, "10-summary-hang-many-readings", "行")
    @Test fun longEntry() = chinese(paparazzi, "11-definitions-da-long", "打", Page.DEFINITIONS)
    @Test fun noDefinition() = chinese(paparazzi, "12-no-definition-je", "嘢")
    @Test fun mandarin() = chinese(paparazzi, "13-summary-mandarin-keyboard", "學校", mode = InputMode.Mandarin)
    @Test fun backFromPart() = drawCard(paparazzi, "14-part-opened-with-back") { popup, host ->
        popup.show(host, "學校", annotator.resolve("學校", "", InputMode.Cantonese)!!, prefs, peek = false, onSpeak = { _, _ -> }) {}
        popup.openForScreens {
            popup.show(host, "學", annotator.resolve("學", "", InputMode.Cantonese)!!, prefs, peek = false, onSpeak = { _, _ -> }) {}
        }
    }

    @Test fun japanese() = japaneseCard(paparazzi, "7-japanese-taberu")
    @Test fun japaneseStrokes() = japaneseCard(paparazzi, "17-japanese-strokes", page = Page.STROKES)
    @Test fun strokes() = chinese(paparazzi, "15-strokes-xuexiao", "學校", Page.STROKES)
    @Test fun partsChar() = chinese(paparazzi, "16-parts-xue-made-of-used-in", "學", Page.PARTS)
    @Test fun peek() = chinese(paparazzi, "18-peek-with-lock-bar", "學校", peek = true)
    @Test fun korean() = koreanCard(paparazzi, "8-korean-hakgyo")

    @Test fun english() = drawCard(paparazzi, "9-english-school", bar = null) { popup, host ->
        popup.showWord(host, "school", null,
            listOf(WordEntry("school", "school", "/skuːl/ (UK) · /skul/ (US)", null, "noun",
                listOf("An institution dedicated to teaching and learning", "A group of fish swimming together"))),
            emptyList(), peek = false, onSpeak = { _, _ -> }, lang = "en", onSpeakAs = { _, _ -> }) {}
    }
}

/** A phone tall enough to show a whole page at once, to check everything on it. */
class CardScreensTall {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = S23.copy(screenHeight = 4200), theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun summary() = chinese(paparazzi, "t1-summary-xuexiao-whole", "學校", tall = true)
    @Test fun summaryHang() = chinese(paparazzi, "t2-summary-hang-whole", "行", tall = true)
    @Test fun phrase() = chinese(paparazzi, "t3-summary-phrase-whole", "我今日去學校", tall = true)
    @Test fun japanese() = japaneseCard(paparazzi, "t4-summary-japanese-whole", tall = true)
    @Test fun korean() = koreanCard(paparazzi, "t5-summary-korean-whole", tall = true)
}

/** The same card in dark mode. */
class CardScreensDark {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = S23.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    @Test fun summary() = chinese(paparazzi, "d1-summary-dark", "學校")
    @Test fun readings() = chinese(paparazzi, "d2-readings-dark", "行", Page.READINGS)
}
