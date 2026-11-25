package app.lychee.screens

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.lychee.chinese.DictionaryPopup
import app.lychee.chinese.DictionaryPopup.Page
import app.lychee.chinese.Readings
import app.lychee.core.Annotator
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
)
private val annotator = Annotator(Dictionary { form -> words.filter { it.traditional == form || it.simplified == form } })

/**
 * Draws a card above the keyboard as the phone shows it: the real keyboard (with its bar) at
 * the bottom, at its real height, and the card in the room the keyboard gives it.
 */
private fun drawCard(paparazzi: Paparazzi, name: String, show: (DictionaryPopup, FrameLayout) -> Unit) {
    Readings.useForScreens(annotator)
    val ctx = paparazzi.context
    val dm = ctx.resources.displayMetrics
    fun dp(v: Int) = (v * dm.density).toInt()
    val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val screen = FrameLayout(ctx).apply { setBackgroundColor(Color.parseColor(if (night) "#1C1416" else "#ECE3E5")) }
    val keyboard = KeyboardPreviewView(ctx)
    keyboard.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
    val kbHeight = keyboard.measuredHeight
    screen.addView(keyboard, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, kbHeight, Gravity.BOTTOM))
    val room = dm.heightPixels - kbHeight - dp(24) - dp(12)
    val height = minOf(room, maxOf(dp(260), (room * 0.6f).toInt()), dp(420))
    val host = FrameLayout(ctx)
    screen.addView(host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height + dp(30), Gravity.BOTTOM).apply {
        bottomMargin = kbHeight
    })
    val popup = DictionaryPopup(ctx, standalone = host, standaloneRoom = height, asKeyboard = true)
    show(popup, host)
    for (i in 0 until host.childCount) host.getChildAt(i).apply { alpha = 1f; scaleX = 1f; scaleY = 1f; translationY = 0f }
    paparazzi.snapshot(screen, name)
}

private fun chinese(paparazzi: Paparazzi, name: String, text: String, page: Page? = null) = drawCard(paparazzi, name) { popup, host ->
    popup.startPage = page
    val r = annotator.resolve(text, "", InputMode.Cantonese)!!
    popup.show(host, text, r, DisplayPrefs(), peek = false, onSpeak = { _, _ -> }) {}
}

/** The dictionary card in light mode, Chinese, Japanese, Korean and English, on each tab. */
class CardScreens {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun summary() = chinese(paparazzi, "1-summary", "學校")
    @Test fun readings() = chinese(paparazzi, "2-readings-hang", "行", Page.READINGS)
    @Test fun definitions() = chinese(paparazzi, "3-definitions-hang", "行", Page.DEFINITIONS)
    @Test fun parts() = chinese(paparazzi, "4-parts-xuexiao", "學校", Page.PARTS)
    @Test fun phrase() = chinese(paparazzi, "5-phrase-not-in-dictionary", "我今日去學校")
    @Test fun phraseParts() = chinese(paparazzi, "6-phrase-parts", "我今日去學校", Page.PARTS)

    @Test fun japanese() = drawCard(paparazzi, "7-japanese-taberu") { popup, host ->
        popup.showWord(host, "食べる", "たべる  taberu",
            listOf(WordEntry("食べる", "食べる", "たべる", null, "Ichidan verb, transitive", listOf("to eat", "to live on (e.g. a salary); to live off; to subsist on"))),
            listOf("毎朝パンを食べる。" to "I eat bread every morning."), peek = false, onSpeak = { _, _ -> }, lang = "ja",
            onSpeakAs = { _, _ -> }, badges = listOf("JLPT N5"), pronunciation = "Pitch  たべ̅ꜜる  ·  Nakadaka (falls in the middle)",
            characters = listOf(DictionaryPopup.CharReading("食", listOf("On" to listOf("ショク" to "shoku"), "Kun" to listOf("た(べる)" to "ta(beru)")), "たべる", "eat; food")),
            charactersTitle = "Kanji") {}
    }

    @Test fun korean() = drawCard(paparazzi, "8-korean-hakgyo") { popup, host ->
        popup.showWord(host, "학교", "hakgyo",
            listOf(WordEntry("학교", "학교", "hakgyo", "學校", "noun", listOf("school", "an institution where students are taught"))),
            emptyList(), peek = false, onSpeak = { _, _ -> }, lang = "ko", onSpeakAs = { _, _ -> },
            badges = listOf("Beginner level"), pronunciation = "Said  [학꾜] hakkyo",
            characters = listOf(DictionaryPopup.CharReading("學", listOf("Korean" to listOf("학" to "hak")), "학", "learn"),
                DictionaryPopup.CharReading("校", listOf("Korean" to listOf("교" to "gyo")), "교", "school")),
            charactersTitle = "Hanja") {}
    }

    @Test fun english() = drawCard(paparazzi, "9-english-school") { popup, host ->
        popup.showWord(host, "school", null,
            listOf(WordEntry("school", "school", "/skuːl/ (UK) · /skul/ (US)", null, "noun",
                listOf("An institution dedicated to teaching and learning", "A group of fish swimming together"))),
            emptyList(), peek = false, onSpeak = { _, _ -> }, lang = "en", onSpeakAs = { _, _ -> }) {}
    }
}

/** The same card in dark mode. */
class CardScreensDark {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    @Test fun summary() = chinese(paparazzi, "d1-summary-dark", "學校")
    @Test fun readings() = chinese(paparazzi, "d2-readings-dark", "行", Page.READINGS)
}
