package app.lychee.screens

import android.text.InputType
import app.cash.paparazzi.Paparazzi
import app.lychee.chinese.CandidateBarView
import app.lychee.chinese.CandidatePanelView
import app.lychee.clipboard.ClipboardPanelView
import app.lychee.clipboard.ClipboardStore
import app.lychee.handwriting.HandwritingPanelView
import app.lychee.rime.Candidate
import app.lychee.toolbar.NumpadPanelView
import org.junit.Rule
import org.junit.Test

/**
 * The keyboard's panels, each on the whole phone (status bar, the app, the keyboard's top bar),
 * laid over the keys where the real keyboard puts them.
 */
class PanelScreens {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = S23, theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun numpad() = drawPhone(paparazzi, "p1-number-pad", overKeys = { NumpadPanelView(it.context).apply {
        forced = true
        setInputType(InputType.TYPE_CLASS_NUMBER)
    } })

    @Test fun phonePad() = drawPhone(paparazzi, "p2-phone-pad", overKeys = { NumpadPanelView(it.context).apply {
        forced = true
        setInputType(InputType.TYPE_CLASS_PHONE)
    } })

    private fun clipboard(name: String, on: Boolean, items: List<ClipboardStore.Item>) {
        ClipboardStore.forScreens = on to items
        // the panel puts its own title and on/off switch in the bar when it shows
        drawPhone(paparazzi, name, bar = TopBar.Title { }, overKeys = { host ->
            ClipboardPanelView(host.context).also { host.addView(it); it.show() }
        })
    }

    @Test fun clipboardOff() = clipboard("p3-clipboard-off", false, emptyList())

    @Test fun clipboardOn() {
        val now = System.currentTimeMillis()
        clipboard("p4-clipboard-on", true, listOf("See you at 7 at the station", "我哋聽日去學校", "https://lychee-keyboard.github.io",
            "+852 2345 6789", "Remember to bring the textbook", "明天見！")
            .mapIndexed { i, t -> ClipboardStore.Item(t, now - i * 90_000L, pinned = i == 1) })
    }

    /** Handwriting: the pad over the keys but their bottom row; its guesses in the candidate bar, with no line for typed letters. */
    @Test fun handwriting() = drawPhone(paparazzi, "p5-handwriting",
        bar = TopBar.Candidates(BarSample("", listOf("你", "妳", "您", "你好", "你們", "尔", "伱"), CandidateBarView.Lines(2, true), "yue", script = true) { c ->
            mapOf("你" to Triple("néih", "nǐ", "you"), "妳" to Triple("néih", "nǐ", "you (female)"), "您" to Triple("nèihn", "nín", "you (polite)"),
                "你好" to Triple("néih hóu", "nǐ hǎo", "hello"), "你們" to Triple("néih mùhn", "nǐ men", "you (plural)"),
                "尔" to Triple("yíh", "ěr", "you (literary)"), "伱" to Triple("néih", "nǐ", "you (variant)"))[c.text]
                ?.let { (y, m, g) -> app.lychee.core.Annotation(listOf(y, m), g, null) }
        }, typed = false),
        keepBottomRow = true,
        overKeys = { host -> HandwritingPanelView(host.context).also { host.addView(it); runCatching { it.show() } } })

    @Test fun candidates() = drawPhone(paparazzi, "p7-candidate-dropdown",
        bar = TopBar.Candidates(BarSample("hok", listOf("學", "學校", "鶴", "殼"), CandidateBarView.Lines(2, true), "yue", script = true) { null }),
        overKeys = { host -> CandidatePanelView(host.context).also { p ->
            host.addView(p)
            p.show(listOf("學校", "學", "雪", "鱈", "削", "學生", "學習", "學費", "學期", "學者", "學問", "學院", "學位", "學歷", "學術", "學業")
                .map { Candidate(it, "") }, 1f)
        } })
}
