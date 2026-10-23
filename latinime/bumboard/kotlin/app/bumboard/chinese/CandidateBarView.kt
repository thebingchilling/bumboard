package app.bumboard.chinese

import android.content.Context
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
import app.bumboard.core.Annotation
import app.bumboard.rime.Candidate
import com.android.inputmethod.latin.R
import java.util.concurrent.Executors

/**
 * A scrolling row of Chinese candidates shown in place of LatinIME's suggestion strip.
 * Characters appear at once; readings and meanings are looked up off the main thread and
 * filled in as they arrive, so typing never waits for the dictionary.
 * Colours come from the keyboard theme's suggestion strip style so it matches the keys.
 */
class CandidateBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {

    var onPick: (Int) -> Unit = {}
    var onLongPick: (Int, View) -> Unit = { _, _ -> }
    /** The finger left a candidate (after a long-press, for hold-to-peek). */
    var onRelease: () -> Unit = {}

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val textColor: Int
    private val firstColor: Int
    private val cells = mutableListOf<LinearLayout>()

    @Volatile
    private var generation = 0

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

    /** Text scale: the candidate size setting, limited so the row fits its height. */
    private var scale = 1f

    /** Compact (false): the characters and one reading. Full: every reading and the meaning. */
    private var full = false

    fun setStyle(scale: Float, full: Boolean) {
        this.scale = scale
        this.full = full
    }

    /**
     * Shows [candidates]. [annotate], when given, runs on a background thread for each
     * candidate in order; results for a superseded list are dropped.
     */
    fun setCandidates(candidates: List<Candidate>, annotate: ((Candidate) -> Annotation?)?) {
        val gen = ++generation
        row.removeAllViews()
        cells.clear()
        candidates.forEachIndexed { index, candidate ->
            val cell = cell(index, candidate)
            cells += cell
            row.addView(cell, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
        scrollTo(0, 0)
        if (annotate == null || candidates.isEmpty()) return
        annotator.execute {
            val batch = ArrayList<Pair<Int, Annotation>>()
            for ((index, candidate) in candidates.withIndex()) {
                if (gen != generation) return@execute
                val annotation = runCatching { annotate(candidate) }.getOrNull() ?: continue
                batch += index to annotation
                // the first few appear right away, the rest in larger groups
                if (index < 4 || batch.size >= 8) {
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
            results.forEach { (index, annotation) -> cells.getOrNull(index)?.let { fill(it, index, annotation) } }
        }
    }

    private fun cell(index: Int, candidate: Candidate) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(1), dp(10), dp(1))
        minimumWidth = dp(44)
        background = selectableBackground()
        setOnClickListener { onPick(index) }
        setOnLongClickListener { view -> onLongPick(index, view); true }
        setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
                event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) onRelease()
            false
        }
        addView(text(candidate.text, CHAR_SP * scale, colorFor(index)).apply { includeFontPadding = false }, wrap())
    }

    private fun fill(cell: LinearLayout, index: Int, annotation: Annotation) {
        val color = colorFor(index)
        while (cell.childCount > 1) cell.removeViewAt(1)
        (if (full) annotation.lines else annotation.lines.take(1)).forEach { line ->
            cell.addView(text(line, READING_SP * scale, ColorUtils.setAlphaComponent(color, 0xD0)).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
            }, wrap())
        }
        annotation.meaning?.takeIf { full }?.let { meaning ->
            cell.addView(text(meaning, MEANING_SP * scale, ColorUtils.setAlphaComponent(color, 0xA0)).apply {
                // the definition in full, up to a generous width
                maxWidth = dp(240)
                includeFontPadding = false
            }, wrap())
        }
    }

    private fun colorFor(index: Int) = if (index == 0) firstColor else textColor

    private fun text(value: String, sp: Float, color: Int) = TextView(context).apply {
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

    companion object {
        private val annotator = Executors.newSingleThreadExecutor()
        const val CHAR_SP = 20f
        const val READING_SP = 11.5f
        const val MEANING_SP = 10.5f

        /** The largest scale at which the compact row (characters + one reading) fits [height]. */
        fun compactFit(context: Context, height: Int): Float {
            val sp = context.resources.displayMetrics.scaledDensity
            val density = context.resources.displayMetrics.density
            return (height - density * 6) / ((CHAR_SP + READING_SP) * 1.22f * sp)
        }

        /** The bar's height for [lines] at [scale], in pixels. */
        fun heightFor(context: Context, lines: Lines, scale: Float): Int {
            val sp = context.resources.displayMetrics.scaledDensity
            val density = context.resources.displayMetrics.density
            val text = CHAR_SP + lines.readings * READING_SP + (if (lines.meaning) MEANING_SP else 0f)
            return (text * scale * sp * 1.22f + density * 8).toInt()
        }
    }
}
