package app.lychee.settings

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.lychee.chinese.CandidateBarView
import app.lychee.chinese.DictionaryPopup
import app.lychee.chinese.Readings
import app.lychee.core.InputMode
import app.lychee.rime.Candidate
import app.lychee.store.Store

/**
 * A listing's worked example as Lychee really draws it: the keyboard (its keys, the candidate
 * bar with readings and meanings) and the word card open above it, on the add-on's own tab,
 * shrunk to fit the page. Built from the real views and the real dictionary, so it shows what
 * the add-on does in use. Null when it can't be drawn here (the dictionary not read yet): the
 * page then shows its written example instead.
 */
object KeyboardSample {
    /** The card's height in the example, in dp (the keyboard under it is its real height). */
    private const val CARD_DP = 320
    /** Room above the card for the page's light/dark toggle, in dp. */
    const val TOGGLE_ROOM_DP = 36

    /** Whether [sample] can be drawn as a keyboard (the others keep their written example). */
    fun drawable(sample: Store.Sample): Boolean = when (sample) {
        Store.Sample.CHINESE, Store.Sample.STROKES, Store.Sample.EXAMPLES -> Readings.annotator != null
        Store.Sample.JAPANESE, Store.Sample.KOREAN -> true
        else -> false
    }

    private class Bar(val typed: String, val words: List<String>, val font: String)

    private fun barFor(sample: Store.Sample) = when (sample) {
        Store.Sample.JAPANESE -> Bar("taberu", listOf("食べる", "たべる", "食べ", "食"), "ja")
        Store.Sample.KOREAN -> Bar("hakgyo", listOf("학교", "學校", "학교에", "학구"), "ko")
        Store.Sample.STROKES -> Bar("wing", listOf("永", "榮", "泳", "詠"), "yue")
        else -> Bar("hokhaau", listOf("學校", "學", "鶴", "學好"), "yue")
    }

    /**
     * The keyboard with the card open on [sample]'s tab, at the phone's own width, light or
     * [dark] whatever Settings and the phone say (the page has its own switch): the real input
     * view (RealKeyboard) with its real candidate bar, and the real card above it.
     */
    fun build(context: Context, sample: Store.Sample, dark: Boolean): View {
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val width = context.resources.displayMetrics.widthPixels
        val settings = app.lychee.layout.RealKeyboard.withSettings(context, mapOf(
            app.lychee.ui.KeyStyle.PREF_THEME_MODE to if (dark) app.lychee.ui.KeyStyle.MODE_DARK else app.lychee.ui.KeyStyle.MODE_LIGHT))
        val bar = barFor(sample)
        val (input, _) = app.lychee.layout.RealKeyboard.inputView(settings, bar.font, width)
        // the keyboard's own context: its theme, light or dark as chosen here
        val themed = input.context
        val c = app.lychee.ui.M3Colors.of(themed)
        input.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        input.layout(0, 0, width, input.measuredHeight)
        val chinese = bar.font == "yue" || bar.font == "cmn"
        app.lychee.layout.RealKeyboard.showCandidates(input, bar.words, bar.typed, bar.font,
            CandidateBarView.Lines(if (chinese) 2 else 1, true), script = chinese) { cand -> annotate(bar.font, cand) }
        // the app's background above the keyboard, as on the phone
        val root = android.widget.LinearLayout(themed).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(androidx.core.graphics.ColorUtils.blendARGB(c.surface,
                if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE, 0.5f))
            // a strip of the app above the card, where the page's light/dark toggle sits
            setPadding(0, dp(TOGGLE_ROOM_DP), 0, 0)
        }
        // the card in a frame of its own: it brings itself to the front, which in a column
        // would move it below the keyboard
        val host = FrameLayout(themed)
        root.addView(FrameLayout(themed).apply { addView(host, FrameLayout.LayoutParams(width, dp(CARD_DP))) },
            android.widget.LinearLayout.LayoutParams(width, dp(CARD_DP)))
        root.addView(input, android.widget.LinearLayout.LayoutParams(width, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
        val popup = DictionaryPopup(themed, standalone = host, standaloneRoom = dp(CARD_DP - 20), asKeyboard = true)
        // drawn at once: it sits in a page, not opening over the keyboard
        popup.animateIn = false
        runCatching {
            when (sample) {
                Store.Sample.JAPANESE -> popup.showWord(host, "食べる", "たべる  taberu",
                    listOf(app.lychee.dict.WordEntry("食べる", "食べる", "たべる", null, "Ichidan verb, transitive",
                        listOf("to eat", "to live on (e.g. a salary); to live off; to subsist on"))),
                    listOf("毎朝パンを食べる。" to "I eat bread every morning."), peek = false, onSpeak = null, lang = "ja",
                    badges = listOf("JLPT N5"), pronunciation = "Pitch  たべ̅ꜜる  ·  Nakadaka (falls in the middle)") {}
                Store.Sample.KOREAN -> popup.showWord(host, "학교", "hakgyo",
                    listOf(app.lychee.dict.WordEntry("학교", "학교", "hakgyo", "學校", "noun", listOf("school", "an institution where students are taught"))),
                    listOf("저는 학교에 가요." to "I go to school."), peek = false, onSpeak = null, lang = "ko",
                    badges = listOf("Beginner level"), pronunciation = "Said  [학꾜] hakkyo") {}
                else -> {
                    val word = if (sample == Store.Sample.STROKES) "永" else "學校"
                    popup.startPage = when (sample) {
                        Store.Sample.STROKES -> DictionaryPopup.Page.STROKES
                        Store.Sample.EXAMPLES -> DictionaryPopup.Page.EXAMPLES
                        else -> null
                    }
                    val r = Readings.annotator?.resolve(word, "", InputMode.Cantonese)
                    if (r != null) popup.show(host, word, r, Readings.displayPrefs(
                        @Suppress("DEPRECATION") android.preference.PreferenceManager.getDefaultSharedPreferences(themed)),
                        peek = false, onSpeak = null) {}
                }
            }
        }
        return root
    }

    /** Japanese and Korean readings and meanings as the keyboard shows them (Chinese ones come from the dictionary). */
    private fun annotate(font: String, cand: Candidate): app.lychee.core.Annotation? = when (font) {
        "ja" -> mapOf("食べる" to ("taberu" to "to eat"), "たべる" to ("taberu" to "to eat"), "食べ" to ("tabe" to "eating"),
            "食" to ("shoku" to "food"))[cand.text]?.let { (r, m) -> app.lychee.core.Annotation(listOf(r), m, null) }
        "ko" -> mapOf("학교" to ("hakgyo" to "school"), "學校" to ("hakgyo" to "school"), "학교에" to ("hakgyoe" to "to school"),
            "학구" to ("hakgu" to "study"))[cand.text]?.let { (r, m) -> app.lychee.core.Annotation(listOf(r), m, null) }
        else -> null
    }
}

/**
 * [KeyboardSample] in a page: the real views at the phone's width, shrunk to the page's, light
 * to start with (not following the page), with a Light | Dark switch over it.
 */
@Composable
fun KeyboardSampleView(sample: Store.Sample) {
    var dark by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    // one small toggle in the example's corner: light, or dark while it is on
    Box(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
        PhoneWidth(sample to dark) { c -> KeyboardSample.build(c, sample, dark) }
        androidx.compose.material3.FilledTonalIconToggleButton(
            checked = dark, onCheckedChange = { dark = it },
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp),
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(com.android.inputmethod.latin.R.drawable.lychee_ic_dark_mode),
                androidx.compose.ui.res.stringResource(com.android.inputmethod.latin.R.string.lychee_sample_dark),
                Modifier.size(18.dp),
            )
        }
    }
}
