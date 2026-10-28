package app.lychee.chinese

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.lychee.core.Annotation
import app.lychee.rime.Candidate
import com.android.inputmethod.latin.R
import java.util.concurrent.Executors

/**
 * A scrolling row of Chinese candidates in the strip: each with its characters, its readings
 * and, on one line, as many words of its meaning as fit the candidate's own width (the meaning
 * never widens a candidate; hold one for the full dictionary entry). Characters appear at
 * once; readings and meanings are looked up off the main thread and filled in as they arrive.
 * Candidates the phone has no font for are left out.
 */
class CandidateBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {

    /** A candidate was tapped; the index is Rime's, as given to [setCandidates]. */
    var onPick: (Int) -> Unit = {}
    var onLongPick: (Int, View) -> Unit = { _, _ -> }
    /** The finger left a candidate (after a long-press, for hold-to-peek). */
    var onRelease: () -> Unit = {}
    /** A swipe down on the row: open every candidate (the notch). */
    var onExpand: () -> Unit = {}
    /** A candidate's readings arrived (the first one's go in the preedit line). */
    var onAnnotated: (Int, Annotation) -> Unit = { _, _ -> }

    /** What shows around each candidate, from the Chinese settings. */
    data class Style(
        val toneColours: Boolean = true,
        val otherScript: Boolean = true,
        val badges: Boolean = true,
        /** whose level badge: HSK for Mandarin, frequency band and register for Cantonese */
        val lang: app.lychee.core.Lang? = null,
    )

    var style = Style()

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val textColor: Int
    private val firstColor: Int
    /** Shown cells by Rime's candidate index. */
    private val cells = HashMap<Int, LinearLayout>()

    @Volatile
    private var generation = 0

    /** Text scale: the candidate size setting. */
    private var scale = 1f

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        addView(row, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val a = context.obtainStyledAttributes(
            null, R.styleable.SuggestionStripView, R.attr.suggestionStripViewStyle, R.style.SuggestionStripView,
        )
        textColor = a.getColor(R.styleable.SuggestionStripView_colorSuggested, 0xFF000000.toInt())
        firstColor = a.getColor(R.styleable.SuggestionStripView_colorAutoCorrect, textColor)
        a.recycle()
    }

    /** Lines under each candidate's characters: readings, and whether a meaning follows. */
    data class Lines(val readings: Int, val meaning: Boolean)

    fun setScale(scale: Float) {
        this.scale = scale
    }

    /**
     * Shows [candidates] (skipping any the phone cannot draw). [annotate], when given, runs on
     * a background thread for each candidate in order; results for a superseded list are dropped.
     */
    fun setCandidates(candidates: List<Candidate>, annotate: ((Candidate) -> Annotation?)?) {
        val gen = ++generation
        row.removeAllViews()
        cells.clear()
        val shown = candidates.withIndex().filter { renderable(it.value.text) }
        shown.forEach { (index, candidate) ->
            val cell = cell(index, candidate, first = cells.isEmpty())
            cells[index] = cell
            row.addView(cell, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
        scrollTo(0, 0)
        if (annotate == null || shown.isEmpty()) return
        annotator.execute {
            val batch = ArrayList<Pair<Int, Annotation>>()
            for ((n, item) in shown.withIndex()) {
                if (gen != generation) return@execute
                val annotation = runCatching { annotate(item.value) }.getOrNull() ?: continue
                batch += item.index to annotation
                // the first few appear right away, the rest in larger groups
                if (n < 4 || batch.size >= 8) {
                    deliver(gen, ArrayList(batch))
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) deliver(gen, batch)
        }
    }

    private fun deliver(gen: Int, results: List<Pair<Int, Annotation>>) {
        post {
            if (gen != generation) return@post
            results.forEach { (index, annotation) ->
                cells[index]?.let { fill(it, annotation) }
                onAnnotated(index, annotation)
            }
        }
    }

    private var downX = 0f
    private var downY = 0f

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                if (dy > dp(28) && dy > 2 * kotlin.math.abs(ev.x - downX)) {
                    onExpand()
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    /** The word with, beside it, the other script on top and its badges below. */
    private fun cell(index: Int, candidate: Candidate, first: Boolean) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(2), dp(10), dp(2))
        minimumWidth = dp(44)
        tag = if (first) firstColor else textColor
        background = selectableBackground()
        setOnClickListener { onPick(index) }
        setOnLongClickListener { view -> onLongPick(index, view); true }
        setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
                event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) onRelease()
            false
        }
        val word = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        word.addView(text(candidate.text, CHAR_SP * scale, tag as Int).apply {
            includeFontPadding = false
            app.lychee.fonts.LycheeFonts.chinese(context)?.let { typeface = it }
        }, wrap())
        addView(word, wrap())
    }

    /** The small column beside the word: the other script, then the badges. */
    private fun side(annotation: Annotation, color: Int): LinearLayout? {
        val other = annotation.otherScript?.takeIf { style.otherScript }
        val badges = if (style.badges) badges(annotation) else emptyList()
        if (other == null && badges.isEmpty()) return null
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), 0, 0, 0)
            other?.let {
                addView(text(it, SIDE_SP * scale, ColorUtils.setAlphaComponent(color, 0xB0)).apply {
                    includeFontPadding = false
                    gravity = Gravity.START
                    app.lychee.fonts.LycheeFonts.chinese(context)?.let { f -> typeface = f }
                })
            }
            if (badges.isNotEmpty()) {
                addView(text(badges.joinToString(" "), BADGE_SP * scale, ColorUtils.setAlphaComponent(color, 0xB0)).apply {
                    includeFontPadding = false
                    gravity = Gravity.START
                    typeface = app.lychee.fonts.LycheeFonts.ui(context, 600)
                })
            }
        }
    }

    /** HSK 1-6 under Mandarin; colloquial or written, then the frequency band, under Cantonese. */
    private fun badges(annotation: Annotation): List<String> {
        val info = annotation.info ?: return emptyList()
        return when (style.lang) {
            app.lychee.core.Lang.Mandarin -> listOfNotNull(info.hsk?.let { "HSK$it" })
            app.lychee.core.Lang.Cantonese -> listOfNotNull(
                when (info.register) {
                    "col" -> context.getString(R.string.lychee_badge_colloquial)
                    "wri" -> context.getString(R.string.lychee_badge_written)
                    else -> null
                },
                when (info.band) {
                    1 -> context.getString(R.string.lychee_badge_common)
                    2 -> context.getString(R.string.lychee_badge_everyday)
                    3 -> context.getString(R.string.lychee_badge_advanced)
                    else -> null
                },
            )
            else -> emptyList()
        }
    }

    private fun fill(cell: LinearLayout, annotation: Annotation) {
        val color = cell.tag as Int
        while (cell.childCount > 1) cell.removeViewAt(1)
        (cell.getChildAt(0) as? LinearLayout)?.let { word ->
            while (word.childCount > 1) word.removeViewAt(1)
            side(annotation, color)?.let { word.addView(it, wrap()) }
        }
        val readingColor = ColorUtils.setAlphaComponent(color, 0xD0)
        annotation.lines.forEachIndexed { i, line ->
            val shown = if (style.toneColours) ToneColours.apply(line, annotation.tones.getOrNull(i), color) else line
            cell.addView(text(shown, READING_SP * scale, readingColor).apply {
                typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
                includeFontPadding = false
            }, wrap())
        }
        annotation.meaning?.let { meaning ->
            // as wide as the characters and readings above it, never wider
            cell.addView(FittedWordsView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, MEANING_SP * scale)
                setTextColor(ColorUtils.setAlphaComponent(color, 0xA0))
                includeFontPadding = false
                gravity = Gravity.CENTER
                fullText = meaning
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** The bar's exact height for [lines], measured from a sample candidate. */
    fun heightFor(lines: Lines): Int {
        val sample = cell(0, Candidate("漢字", ""), first = false)
        fill(sample, Annotation(List(lines.readings) { "hon3 zi6" }, if (lines.meaning) "Chinese characters" else null, null))
        sample.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        return sample.measuredHeight
    }

    private fun text(value: CharSequence, sp: Float, color: Int) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
    }

    private fun wrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { gravity = Gravity.CENTER_HORIZONTAL }

    private fun selectableBackground() = TypedValue().let { value ->
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        context.getDrawable(value.resourceId)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /**
     * One line holding as many whole words of [fullText] as fit its width (the width comes from
     * the cell, never from the text).
     */
    private class FittedWordsView(context: Context) : TextView(context) {
        var fullText: String = ""
            set(value) {
                field = value
                requestLayout()
            }

        init {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val fitted = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED || available <= 0) {
                fullText
            } else fit(available.toFloat())
            if (text.toString() != fitted) text = fitted
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        /** The longest run of whole words (split at spaces and list punctuation) that fits. */
        private fun fit(width: Float): String {
            val words = fullText.split(' ').filter { it.isNotEmpty() }
            var best = ""
            for (n in 1..words.size) {
                val candidate = words.take(n).joinToString(" ").trimEnd(',', ';', '/')
                if (paint.measureText(candidate) > width) break
                best = candidate
            }
            // not even one word fits: let the first word ellipsize
            return best.ifEmpty { words.firstOrNull().orEmpty() }
        }
    }

    companion object {
        private val annotator = Executors.newSingleThreadExecutor()
        private val glyphs = Paint()
        const val CHAR_SP = 20f
        const val READING_SP = 11.5f
        const val MEANING_SP = 10.5f
        const val SIDE_SP = 9.5f
        const val BADGE_SP = 7.5f

        /**
         * Whether the phone's fonts can draw [text] (no empty boxes). Paint.hasGlyph takes one
         * grapheme at a time (a whole word is always "missing"), so each character is checked.
         */
        fun renderable(text: String): Boolean {
            if (text.isEmpty()) return false
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val n = Character.charCount(cp)
                if (!Character.isWhitespace(cp) && !glyphs.hasGlyph(text.substring(i, i + n))) return false
                i += n
            }
            return true
        }
    }
}
