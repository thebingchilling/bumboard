package app.lychee.screens

import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.lychee.chinese.DictionaryPopup
import app.lychee.core.DisplayPrefs
import app.lychee.core.Headword
import app.lychee.core.Lang
import app.lychee.core.Resolved
import app.lychee.core.Sense
import org.junit.Rule
import org.junit.Test

/**
 * The dictionary card as it is drawn (Look up in Lychee's standalone card, the same card the
 * keyboard shows), with a small sample entry: Lychee's real dictionary is only built in CI.
 */
class CardScreens {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    private val xuexiao = Headword(
        1, "學校", "学校", 900,
        mapOf(Lang.Cantonese to listOf("hok6 haau6"), Lang.Mandarin to listOf("xue2 xiao4")),
        listOf(
            Sense(Lang.Cantonese, "hok6 haau6", "school; educational institution"),
            Sense(Lang.Mandarin, "xue2 xiao4", "school; CL:所[suo3]"),
            Sense(Lang.Cantonese, "hok6 haau6", "(attributive) school-; academic, e.g. 學校生活 school life"),
        ),
    )

    private fun card(text: String, r: Resolved) {
        val host = FrameLayout(paparazzi.context)
        val popup = DictionaryPopup(paparazzi.context, standalone = host, standaloneRoom = 1600)
        popup.show(host, text, r, DisplayPrefs(), peek = false, onSpeak = { _, _ -> }) {}
        paparazzi.snapshot(host)
    }

    @Test
    fun word() = card("學校", Resolved(Lang.Cantonese, "hok6 haau6", "xue2 xiao4", "school", listOf(xuexiao), composed = false))
}
