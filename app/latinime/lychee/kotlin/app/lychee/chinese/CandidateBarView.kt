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
 * A row of Chinese candidates in the strip, as many as fit whole (it never scrolls; the rest
 * are in the panel the ⌄ opens): each with its characters, its readings
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

    /** What shows under each candidate, from the Chinese settings. Levels and the other script
     *  are in the word card only, never on the bar. */
    data class Style(
        val toneColours: Boolean = true,
        val lang: app.lychee.core.Lang? = null,
        /** The typing language ("yue", "cmn", "ja", "ko"), which picks the candidates' font. */
        val fontLang: String? = null,
    )

    var style = Style()

    /**
     * The lines under every candidate (readings, meaning), or null for none: each candidate
     * keeps a slot for each, empty when it has nothing to show there, so they all match (the
     * same height, the characters on one line) whether or not a meaning was found.
     */
    var lines: Lines? = null

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
     * Shows [candidates] (skipping any the phone cannot draw). With [annotate], the candidates
     * that can fit are read on a background thread first and the row is built once, each cell
     * measured with its readings and meaning: nothing grows after it shows (cells widening as
     * their readings arrived pushed the last ones off the bar), and the main thread never waits
     * for the dictionary, so keys typed meanwhile go straight on. The row on show stays until
     * the new one is ready; a list superseded by newer typing is dropped.
     */
    fun setCandidates(candidates: List<Candidate>, annotate: ((Candidate) -> Annotation?)?) {
        val gen = ++generation
        if (annotate == null) {
            build(candidates.withIndex().filter { renderable(it.value.text) }, emptyMap())
            return
        }
        val list = candidates.toList()
        annotator.execute {
            if (gen != generation) return@execute
            // enough candidates to fill the bar (the rest are in the dropdown)
            val drawable = ArrayList<IndexedValue<Candidate>>()
            for ((i, c) in list.withIndex()) {
                if (renderable(c.text)) drawable += IndexedValue(i, c)
                if (drawable.size >= MAX_SHOWN) break
            }
            val notes = HashMap<Int, Annotation>()
            for (item in drawable) {
                if (gen != generation) return@execute
                runCatching { annotate(item.value) }.getOrNull()?.let { notes[item.index] = it }
            }
            post {
                if (gen != generation) return@post
                build(drawable, notes)
                notes.forEach { (index, annotation) -> onAnnotated(index, annotation) }
            }
        }
    }

    /** Builds the row from [items], each cell with its [notes] filled in before it is measured. */
    private fun build(items: List<IndexedValue<Candidate>>, notes: Map<Int, Annotation>) {
        row.removeAllViews()
        cells.clear()
        val available = width - paddingLeft - paddingRight
        val shown = ArrayList<IndexedValue<Candidate>>()
        var used = 0
        for (item in items) {
            val cell = cell(item.index, item.value, first = cells.isEmpty())
            notes[item.index]?.let { fill(cell, it) }
            if (available > 0) {
                cell.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
                if (shown.isNotEmpty() && used + cell.measuredWidth > available) break
                used += cell.measuredWidth
            } else if (shown.size >= FALLBACK_COUNT) {
                break
            }
            cells[item.index] = cell
            shown += item
            row.addView(cell, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
        lastShown = shown.lastOrNull()?.index ?: -1
        scrollTo(0, 0)
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
        // no scrolling: the candidates keep their taps
        return false
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: android.view.MotionEvent): Boolean = false

    /** Candidates past the bar's width are left out of it (they are in the panel). */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val available = measuredWidth - paddingLeft - paddingRight
        if (available <= 0) return
        // where the bar is full, from each candidate's own width (shown or not), so a second
        // pass changes nothing
        var used = 0
        var cut = row.childCount
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            child.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
            used += child.measuredWidth
            // always the first, even when it alone is too wide
            if (i > 0 && used > available) { cut = i; break }
        }
        var changed = false
        for (i in 0 until row.childCount) {
            val want = if (i < cut) VISIBLE else GONE
            if (row.getChildAt(i).visibility != want) { row.getChildAt(i).visibility = want; changed = true }
        }
        if (changed) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** Rime's index of the last candidate the bar shows; the panel goes on after it. */
    var lastShown = -1
        private set

    /** How many candidates the bar shows (the rest wait in the panel). */
    val shownCount: Int
        get() = (0 until row.childCount).count { row.getChildAt(it).visibility != GONE }

    /** Rime's index of the last candidate actually visible (after fitting). */
    val lastVisible: Int
        get() {
            var last = -1
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i)
                if (child.visibility == GONE) break
                cells.entries.firstOrNull { it.value === child }?.let { last = it.key }
            }
            return last
        }

    /** The notch: a small pill at the bottom of the row; swipe down on the row to open it out. */
    private val notch = Paint(Paint.ANTI_ALIAS_FLAG)
    private val notchRect = android.graphics.RectF()

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        super.dispatchDraw(canvas)
        // no notch: it looked like a scrollbar, and the ⌄ button already opens the rest
        if (!SHOW_NOTCH || row.childCount == 0) return
        notch.color = ColorUtils.setAlphaComponent(textColor, 0x40)
        val w = dp(28).toFloat()
        val h = dp(3).toFloat()
        val cx = scrollX + width / 2f
        val bottom = height - dp(2).toFloat()
        notchRect.set(cx - w / 2, bottom - h, cx + w / 2, bottom)
        canvas.drawRoundRect(notchRect, h / 2, h / 2, notch)
    }

    /** The word, with its readings and meaning added under it by [fill]. */
    private fun cell(index: Int, candidate: Candidate, first: Boolean) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        // like Gboard's: as wide as the characters (with their side room), never wider; the
        // readings and meaning below shrink to fit that width
        setPadding(0, dp(2), 0, dp(2))
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
            setPadding(dp(10), 0, dp(10), 0)
            // in its language's own font (Settings > Fonts), never the keyboard's
            typeface = app.lychee.fonts.LycheeFonts.forText(context, candidate.text, style.fontLang) ?: android.graphics.Typeface.DEFAULT
        }, wrap())
        addView(word, wrap())
        slots(this, 0, false)
    }

    /** Empty readings and meaning lines up to [lines], after the [readings] and [meaning] shown. */
    private fun slots(cell: LinearLayout, readings: Int, meaning: Boolean) {
        val want = lines ?: return
        val color = cell.tag as Int
        repeat((want.readings - readings).coerceAtLeast(0)) {
            cell.addView(ShrinkToFitView(context, READING_SP * scale).apply {
                text = ""
                typeface = app.lychee.fonts.LycheeFonts.appFont(context, 500)
                includeFontPadding = false
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        if (want.meaning && !meaning) {
            cell.addView(FittedWordsView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, MEANING_SP * scale)
                setTextColor(ColorUtils.setAlphaComponent(color, 0xA0))
                includeFontPadding = false
                fullText = ""
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun fill(cell: LinearLayout, annotation: Annotation) {
        val color = cell.tag as Int
        while (cell.childCount > 1) cell.removeViewAt(1)
        val readingColor = ColorUtils.setAlphaComponent(color, 0xD0)
        annotation.lines.forEachIndexed { i, line ->
            val shown = if (style.toneColours) ToneColours.apply(line, annotation.tones.getOrNull(i), color) else line
            cell.addView(ShrinkToFitView(context, READING_SP * scale).apply {
                text = shown
                setTextColor(readingColor)
                typeface = app.lychee.fonts.LycheeFonts.appFont(context, 500)
                includeFontPadding = false
                gravity = Gravity.CENTER
                setPadding(dp(2), 0, dp(2), 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
        slots(cell, annotation.lines.size, annotation.meaning != null)
    }

    /** The bar's exact height for [lines], measured from a sample candidate. */
    fun heightFor(lines: Lines): Int {
        // the same for every key typed: measured once per size and lines
        val key = "$lines:$scale:${style.fontLang}"
        heights[key]?.let { return it }
        return measureHeight(lines).also { heights[key] = it }
    }

    private val heights = HashMap<String, Int>()

    private fun measureHeight(lines: Lines): Int {
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
     * One line that never widens its cell: the text shrinks (down to 70%) to fit the cell's
     * width, and past that is cut short.
     */
    private class ShrinkToFitView(context: Context, private val sp: Float) : TextView(context) {
        init {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val base = sp * resources.displayMetrics.scaledDensity
            if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && available > 0) {
                paint.textSize = base
                val wanted = paint.measureText(text.toString())
                val size = if (wanted <= available) base else maxOf(base * 0.7f, base * available / wanted)
                if (textSize != size) setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
            } else if (textSize != base) {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, base)
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

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
        /** At most this many candidates are read for the bar (more never fit). */
        private const val MAX_SHOWN = 12
        /** The pill under the row (off: it read as a scrollbar). */
        const val SHOW_NOTCH = false
        const val CHAR_SP = 20f
        /** Candidates made before the bar has a width. */
        private const val FALLBACK_COUNT = 8
        const val READING_SP = 11.5f
        const val MEANING_SP = 10.5f

        /**
         * Whether the phone's fonts can draw [text] (no empty boxes). Paint.hasGlyph takes one
         * grapheme at a time (a whole word is always "missing"), so each character is checked.
         */
        @Synchronized
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
