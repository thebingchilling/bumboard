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
import com.android.inputmethod.latin.R
import org.junit.Rule
import org.junit.Test

/**
 * The keyboard's panels, each on the whole phone (status bar, the app, the keyboard's top bar),
 * laid over the keys where the real keyboard puts them.
 */
class PanelScreens {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = S23, theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun numpad() = drawPhone(paparazzi, "p1-number-pad", open = { it.panel<NumpadPanelView>(R.id.lychee_numpad_panel).apply {
        forced = true
        setInputType(InputType.TYPE_CLASS_NUMBER)
        visibility = android.view.View.VISIBLE
    } })

    @Test fun phonePad() = drawPhone(paparazzi, "p2-phone-pad", open = { it.panel<NumpadPanelView>(R.id.lychee_numpad_panel).apply {
        forced = true
        setInputType(InputType.TYPE_CLASS_PHONE)
        visibility = android.view.View.VISIBLE
    } })

    private fun clipboard(name: String, on: Boolean, items: List<ClipboardStore.Item>) {
        ClipboardStore.forScreens = on to items
        // the panel puts its own title and on/off switch in the bar when it shows
        drawPhone(paparazzi, name, bar = TopBar.Title { }, open = { it.panel<ClipboardPanelView>(R.id.lychee_clipboard_panel).show() })
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
        bar = TopBar.Candidates(BarSample("", listOf("你", "妳", "您", "你好", "你們", "尔", "伱"), CandidateBarView.Lines(2, true), "yue", script = false) { c ->
            mapOf("你" to Triple("néih", "nǐ", "you"), "妳" to Triple("néih", "nǐ", "you (female)"), "您" to Triple("nèihn", "nín", "you (polite)"),
                "你好" to Triple("néih hóu", "nǐ hǎo", "hello"), "你們" to Triple("néih mùhn", "nǐ men", "you (plural)"),
                "尔" to Triple("yíh", "ěr", "you (literary)"), "伱" to Triple("néih", "nǐ", "you (variant)"))[c.text]
                ?.let { (y, m, g) -> app.lychee.core.Annotation(listOf(y, m), g, null) }
        }, typed = false),
        open = { runCatching { it.panel<HandwritingPanelView>(R.id.lychee_handwriting_panel).show() } })

    /** Handwriting on the English keyboard: letters guessed in the bar. */
    @Test fun handwritingEnglish() = drawPhone(paparazzi, "p5b-handwriting-english",
        bar = TopBar.Candidates(BarSample("", listOf("h", "n", "k", "b", "li"), CandidateBarView.Lines(0, false), "en", script = false) { null }, typed = false),
        open = { runCatching { it.panel<HandwritingPanelView>(R.id.lychee_handwriting_panel).show() } })

    @Test fun candidates() = drawPhone(paparazzi, "p7-candidate-dropdown",
        bar = TopBar.Candidates(BarSample("hok", listOf("學", "學校", "鶴", "殼"), CandidateBarView.Lines(2, true), "yue", script = true) { null }),
        open = { it.panel<CandidatePanelView>(R.id.lychee_candidate_panel).also { p ->
            p.show(listOf("學校", "學", "雪", "鱈", "削", "學生", "學習", "學費", "學期", "學者", "學問", "學院", "學位", "學歷", "學術", "學業")
                .map { Candidate(it, "") }, 1f)
        } })

    /** The emoji panel in the keyboard's place (its bar and keys), the same top and bottom as the keyboard. */
    @Test fun emoji() = drawPhone(paparazzi, "p8-emoji", open = { real ->
        val panel = real.input.findViewById<app.lychee.emoji.EmojiPanelView>(R.id.emoji_palettes_view)
        real.keys.visibility = android.view.View.GONE
        runCatching { panel.startEmojiPalettes("ABC", real.keys.keyVisualAttribute, real.keys.keyboard?.mIconsSet) }
        panel.visibility = android.view.View.VISIBLE
    })

    /** A key's bubble (g pressed) beside the long-press popup (e held): the same panel, letters and shadow. */
    @Test fun keyPopups() = drawPhone(paparazzi, "p9-key-popups", extra = { f ->
        val ctx = f.screen.context
        val d = ctx.resources.displayMetrics.density
        val themed = app.lychee.ui.LycheeColorStyle.themed(ctx)
        val keyH = f.keyRow
        val textPx = keyH * 0.52f * 0.85f
        // the bubble over g (row 2, sixth key)
        f.screen.addView(android.widget.TextView(ctx).apply {
            text = "g"
            gravity = android.view.Gravity.CENTER
            setTextColor(app.lychee.ui.LycheeColorStyle.color(ctx, com.android.inputmethod.latin.R.attr.lycheeColorOnKey))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textPx)
            background = themed.getDrawable(com.android.inputmethod.latin.R.drawable.lychee_m3_key_preview)
            elevation = 2 * d
        }, android.widget.FrameLayout.LayoutParams((52 * d).toInt(), (60 * d).toInt()).apply {
            leftMargin = (f.screen.width.takeIf { it > 0 } ?: ctx.resources.displayMetrics.widthPixels) * 55 / 100 - (26 * d).toInt()
            topMargin = f.keysTop + keyH - (60 * d).toInt()
        })
        // the long-press popup over e (row 1, third key)
        val panel = android.widget.LinearLayout(ctx).apply {
            background = themed.getDrawable(com.android.inputmethod.latin.R.drawable.lychee_m3_popup_panel)
            elevation = 2 * d
            listOf("è", "é", "ê", "ë", "ē").forEachIndexed { i, t ->
                addView(android.widget.TextView(ctx).apply {
                    text = t
                    gravity = android.view.Gravity.CENTER
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textPx)
                    val sel = i == 1
                    setTextColor(app.lychee.ui.LycheeColorStyle.color(ctx, if (sel) com.android.inputmethod.latin.R.attr.lycheeColorOnAccent else com.android.inputmethod.latin.R.attr.lycheeColorOnKey))
                    background = themed.getDrawable(com.android.inputmethod.latin.R.drawable.lychee_m3_key_popup)
                    isPressed = sel
                    isClickable = true
                }, android.widget.LinearLayout.LayoutParams((40 * d).toInt(), (48 * d).toInt()))
            }
        }
        f.screen.addView(panel, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, (60 * d).toInt()).apply {
            leftMargin = (12 * d).toInt()
            topMargin = f.keysTop - (64 * d).toInt()
        })
    })

    /** The clipboard in edit mode: two copies ticked, Add, Pin and Delete along the bottom. */
    @Test fun clipboardEdit() {
        val now = System.currentTimeMillis()
        ClipboardStore.forScreens = true to listOf("See you at 7 at the station", "我哋聽日去學校", "https://lychee-keyboard.github.io",
            "+852 2345 6789").mapIndexed { i, t -> ClipboardStore.Item(t, now - i * 90_000L, pinned = i == 1) }
        drawPhone(paparazzi, "p4b-clipboard-edit", bar = TopBar.Title { }, open = { it.panel<ClipboardPanelView>(R.id.lychee_clipboard_panel).apply { show(); editForScreens(2) } })
    }

    /** The dropdown with its single-character switch and its own bottom row (?123, emoji, space, ⌫, Enter). */
    @Test fun dropdownKeys() = drawPhone(paparazzi, "p7b-candidate-dropdown-keys",
        bar = TopBar.Candidates(BarSample("hok", listOf("學", "學校", "鶴", "殼"), CandidateBarView.Lines(2, true), "yue", script = true) { null }),
        open = { it.panel<CandidatePanelView>(R.id.lychee_candidate_panel).also { p ->
            p.danzi = "單字"
            p.show(listOf("學校", "學", "雪", "鱈", "削", "學生", "學習", "學費", "學期", "學者", "學問", "學院", "學位", "學歷", "學術", "學業", "學習班", "學術界")
                .map { Candidate(it, "") }, 1f)
        } })
}
