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

    /** Text scale so the characters and their [Lines] fit the strip's fixed height. */
    private var scale = 1f

    fun setLines(lines: Lines) {
        val height = layoutParams?.height?.takeIf { it > 0 }
            ?: resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        val sp = resources.displayMetrics.scaledDensity
        // natural height: the characters, each reading line, the meaning, line spacing, padding
        val natural = (CHAR_SP + lines.readings * READING_SP + (if (lines.meaning) MEANING_SP else 0f)) * sp * 1.18f +
            dp(4)
        scale = (height / natural).coerceAtMost(1f)
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
        addView(text(candidate.text, CHAR_SP * scale, colorFor(index)).apply { includeFontPadding = false }, wrap())
    }

    private fun fill(cell: LinearLayout, index: Int, annotation: Annotation) {
        val color = colorFor(index)
        while (cell.childCount > 1) cell.removeViewAt(1)
        annotation.lines.forEach { line ->
            cell.addView(text(line, READING_SP * scale, ColorUtils.setAlphaComponent(color, 0xD0)).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
            }, wrap())
        }
        annotation.meaning?.let { meaning ->
            cell.addView(text(meaning, MEANING_SP * scale, ColorUtils.setAlphaComponent(color, 0xA0)).apply {
                maxWidth = dp(132)
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
        private const val CHAR_SP = 20f
        private const val READING_SP = 11.5f
        private const val MEANING_SP = 10.5f
    }
}
