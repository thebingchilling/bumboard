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

    /** The keys' height, the preview's own top bar's, and the candidate bar's, in px, at the phone's width. */
    private fun sizes(context: Context, font: String): Triple<Int, Int, Int> {
        val width = context.resources.displayMetrics.widthPixels
        val keys = KeyboardPreviewView(context)
        keys.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val strip = context.resources.getDimensionPixelSize(com.android.inputmethod.latin.R.dimen.config_suggestions_strip_height)
        val lines = CandidateBarView.Lines(if (font == "yue") 2 else 1, true)
        val bar = maxOf(strip, CandidateBarView(context).apply { style = CandidateBarView.Style(lang = null, fontLang = font) }.heightFor(lines))
        return Triple(keys.measuredHeight, strip, bar)
    }

    /** The example's whole height (card, bar and keys) at the phone's width, in px. */
    fun heightPx(context: Context, sample: Store.Sample): Int {
        val (keys, strip, bar) = sizes(context, barFor(sample).font)
        return (CARD_DP * context.resources.displayMetrics.density).toInt() + bar + keys - strip
    }

    /** The keyboard with the card open on [sample]'s tab, at the phone's own width. */
    fun build(context: Context, sample: Store.Sample): View {
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val c = app.lychee.ui.M3Colors.of(context)
        val width = context.resources.displayMetrics.widthPixels
        // the app's background above the keyboard, as on the phone
        val root = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(androidx.core.graphics.ColorUtils.blendARGB(c.surface, android.graphics.Color.WHITE, 0.5f))
        }
        val bar = barFor(sample)
        val (keysHeight, strip, barHeight) = sizes(context, bar.font)
        val candidates = CandidateBarView(context).apply {
            style = CandidateBarView.Style(lang = null, fontLang = bar.font)
            lines = CandidateBarView.Lines(if (bar.font == "yue") 2 else 1, true)
            setBackgroundColor(c.surface)
        }
        val annotate: (Candidate) -> app.lychee.core.Annotation? = { cand ->
            when (bar.font) {
                "ja" -> mapOf("食べる" to ("taberu" to "to eat"), "たべる" to ("taberu" to "to eat"), "食べ" to ("tabe" to "eating"),
                    "食" to ("shoku" to "food"))[cand.text]?.let { (r, m) -> app.lychee.core.Annotation(listOf(r), m, null) }
                "ko" -> mapOf("학교" to ("hakgyo" to "school"), "學校" to ("hakgyo" to "school"), "학교에" to ("hakgyoe" to "to school"),
                    "학구" to ("hakgu" to "study"))[cand.text]?.let { (r, m) -> app.lychee.core.Annotation(listOf(r), m, null) }
                else -> Readings.annotator?.annotate(cand.text, "", InputMode.Cantonese, app.lychee.core.DisplayPrefs(showOtherLanguage = true))
            }
        }
        // laid out at once, so the candidates are there from the start
        candidates.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(barHeight, View.MeasureSpec.EXACTLY))
        candidates.layout(0, 0, width, barHeight)
        candidates.setForScreens(bar.words.map { Candidate(it, "") }, annotate)
        // the card on top, then the bar, then the keys (the preview's own top bar under the candidates)
        // in a frame of its own: the card brings itself to the front, which would move it below
        // the keyboard in a column
        val host = FrameLayout(context)
        root.addView(FrameLayout(context).apply { addView(host, FrameLayout.LayoutParams(width, dp(CARD_DP))) },
            android.widget.LinearLayout.LayoutParams(width, dp(CARD_DP)))
        root.addView(FrameLayout(context).apply {
            addView(KeyboardPreviewView(context), FrameLayout.LayoutParams(width, keysHeight, Gravity.BOTTOM))
            addView(candidates, FrameLayout.LayoutParams(width, barHeight, Gravity.TOP))
        }, android.widget.LinearLayout.LayoutParams(width, barHeight + keysHeight - strip))
        val popup = DictionaryPopup(context, standalone = host, standaloneRoom = dp(CARD_DP - 20), asKeyboard = true)
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
                        @Suppress("DEPRECATION") android.preference.PreferenceManager.getDefaultSharedPreferences(context)),
                        peek = false, onSpeak = null) {}
                }
            }
        }
        return root
    }

}

/** [KeyboardSample] in a page: the real views at the phone's width, shrunk to the page's. */
@Composable
fun KeyboardSampleView(sample: Store.Sample) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fullWidthPx = context.resources.displayMetrics.widthPixels
    val fullHeightPx = androidx.compose.runtime.remember(sample) { KeyboardSample.heightPx(context, sample) }
    BoxWithConstraints(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
        val fullWidth = with(density) { fullWidthPx.toDp() }
        val fullHeight = with(density) { fullHeightPx.toDp() }
        val scale = maxWidth / fullWidth
        Box(Modifier.fillMaxWidth().height(fullHeight * scale)) {
            AndroidView(
                factory = { c -> KeyboardSample.build(c, sample) },
                modifier = Modifier
                    .wrapContentSize(Alignment.TopCenter, unbounded = true)
                    .requiredSize(fullWidth, fullHeight)
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
            )
        }
    }
}
