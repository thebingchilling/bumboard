package app.lychee.screens

import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.lychee.chinese.DictionaryPopup
import app.lychee.chinese.DictionaryPopup.HeaderStyle
import app.lychee.chinese.DictionaryPopup.Page
import app.lychee.chinese.Readings
import app.lychee.core.Annotator
import app.lychee.core.Dictionary
import app.lychee.core.DisplayPrefs
import app.lychee.core.Headword
import app.lychee.core.Lang
import app.lychee.core.Resolved
import app.lychee.core.Sense
import app.lychee.settings.KeyboardPreviewView
import org.junit.Rule
import org.junit.Test

/**
 * The dictionary card above the keyboard, at the height the keyboard gives it, in each header
 * style and on each tab. A small sample dictionary stands in for Lychee's (built in CI only).
 */
class CardScreens {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

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
    )
    private val annotator = Annotator(Dictionary { form -> words.filter { it.traditional == form || it.simplified == form } })

    private fun card(name: String, text: String, style: HeaderStyle, page: Page? = null) {
        Readings.useForScreens(annotator)
        val ctx = paparazzi.context
        val dm = ctx.resources.displayMetrics
        fun dp(v: Int) = (v * dm.density).toInt()
        val screen = FrameLayout(ctx).apply { setBackgroundColor(Color.parseColor("#ECE3E5")) }
        // the keyboard (with its bar) at the bottom, as tall as it really is
        val keyboard = KeyboardPreviewView(ctx)
        keyboard.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val kbHeight = keyboard.measuredHeight
        screen.addView(keyboard, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, kbHeight, android.view.Gravity.BOTTOM))
        // the card's room above it, as the keyboard works it out
        val room = dm.heightPixels - kbHeight - dp(24) - dp(12)
        val height = minOf(room, maxOf(dp(260), (room * 0.6f).toInt()), dp(420))
        val host = FrameLayout(ctx)
        screen.addView(host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height + dp(30), android.view.Gravity.BOTTOM).apply {
            bottomMargin = kbHeight
        })
        val popup = DictionaryPopup(ctx, standalone = host, standaloneRoom = height, asKeyboard = true)
        popup.headerStyle = style
        popup.startPage = page
        val r = annotator.resolve(text, "", app.lychee.core.InputMode.Cantonese)!!
        popup.show(host, text, r, DisplayPrefs(), peek = false, onSpeak = { _, _ -> }) {}
        for (i in 0 until host.childCount) host.getChildAt(i).apply { alpha = 1f; scaleX = 1f; scaleY = 1f; translationY = 0f }
        paparazzi.snapshot(screen, name)
    }

    @Test fun headerCurrent() = card("1-header-current", "學校", HeaderStyle.TALL)
    @Test fun headerA() = card("2-header-A-compact", "學校", HeaderStyle.COMPACT)
    @Test fun headerB() = card("3-header-B-bottom-bar", "學校", HeaderStyle.BOTTOM_BAR)
    @Test fun headerC() = card("4-header-C-one-line", "學校", HeaderStyle.ONE_LINE)
    @Test fun readings() = card("5-readings-hang", "行", HeaderStyle.COMPACT, Page.READINGS)
    @Test fun definitions() = card("6-definitions-hang", "行", HeaderStyle.COMPACT, Page.DEFINITIONS)
    @Test fun parts() = card("7-parts-xuexiao", "學校", HeaderStyle.COMPACT, Page.PARTS)
}
