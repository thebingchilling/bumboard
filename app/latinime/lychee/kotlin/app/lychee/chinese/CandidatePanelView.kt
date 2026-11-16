package app.lychee.chinese

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lychee.core.Annotation
import app.lychee.rime.Candidate
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * The candidates the row has no room for, over the keys, like Gboard's expanded candidates:
 * it goes on from the row's last candidate (none repeated), a scrolling grid of the words with
 * all their readings and meanings, as on the row, each candidate as wide
 * as it needs (1 to 3 columns), thin dividers between. Opened and closed with the ⌄ / ⌃ at the
 * end of the candidate row, or a swipe down on the row (the notch); tap one to type it.
 * Candidates the phone cannot draw are left out.
 */
class CandidatePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** A candidate was tapped; the index is Rime's. */
    var onPick: (Int) -> Unit = {}
    /** A candidate was held: its dictionary entry. */
    var onLongPick: (Int, android.view.View) -> Unit = { _, _ -> }

    private val palette = M3Colors.of(context)
    private val grid = RecyclerView(context)
    private val adapter = Adapter()
    private val layoutManager = GridLayoutManager(context, COLUMNS)
    private val divider = Paint().apply {
        color = androidx.core.graphics.ColorUtils.setAlphaComponent(palette.outlineVariant, 0xB0)
        strokeWidth = resources.displayMetrics.density
    }

    init {
        isClickable = true
        setBackgroundColor(palette.surface)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = adapter.items.getOrNull(position)?.let { spanFor(it.value.text) } ?: 1
        }
        grid.layoutManager = layoutManager
        grid.adapter = adapter
        grid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                // a plain grid: a full line under every cell and between cells in a row
                for (i in 0 until parent.childCount) {
                    val v = parent.getChildAt(i)
                    c.drawLine(v.left.toFloat(), v.bottom.toFloat(), v.right.toFloat(), v.bottom.toFloat(), divider)
                    if (v.right < parent.width - parent.paddingRight - 1) {
                        c.drawLine(v.right.toFloat(), v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat(), divider)
                    }
                }
            }
        })
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    @Volatile
    private var generation = 0
    private var pickOverride: ((Int) -> Unit)? = null
    private var longPickOverride: ((Int, android.view.View) -> Unit)? = null

    /**
     * Shows [candidates] (by Rime's index) that the phone can draw; [annotate], when given,
     * adds readings and meanings, looked up off the main thread.
     */
    fun show(
        candidates: List<Candidate>,
        scale: Float,
        annotate: ((Candidate) -> Annotation?)? = null,
        style: CandidateBarView.Style = CandidateBarView.Style(),
        /** Rime's index of the last candidate the bar shows: the panel goes on from the next. */
        after: Int = -1,
        /** For this list only, in place of [onPick] and [onLongPick] (the English dropdown). */
        onPicked: ((Int) -> Unit)? = null,
        onLongPicked: ((Int, android.view.View) -> Unit)? = null,
    ) {
        pickOverride = onPicked
        longPickOverride = onLongPicked
        val gen = ++generation
        adapter.items = candidates.withIndex().filter { it.index > after && CandidateBarView.renderable(it.value.text) }
        adapter.scale = scale
        adapter.style = style
        adapter.annotations.clear()
        adapter.annotated = annotate != null
        adapter.notifyDataSetChanged()
        grid.scrollToPosition(0)
        visibility = VISIBLE
        if (annotate == null) return
        val items = adapter.items
        worker.execute {
            items.forEachIndexed { position, item ->
                if (gen != generation) return@execute
                val a = runCatching { annotate(item.value) }.getOrNull() ?: return@forEachIndexed
                post {
                    if (gen == generation) {
                        adapter.annotations[position] = a
                        adapter.notifyItemChanged(position)
                    }
                }
            }
        }
    }

    private fun spanFor(text: String): Int {
        // English: three words a row, each the same width, like the suggestion bar's
        // an even grid of four: a long word takes two cells, a very long one the row
        if (adapter.style.fontLang == "en") return 2
        val chars = text.codePointCount(0, text.length)
        return when {
            chars <= 2 -> 1
            chars <= 5 -> 2
            else -> COLUMNS
        }
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<IndexedValue<Candidate>> = emptyList()
        var scale = 1f
        var style = CandidateBarView.Style()
        var annotated = false
        val annotations = HashMap<Int, Annotation>()

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), 0, dp(4), 0)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
                background = app.lychee.ui.LycheeColorStyle.themed(context).getDrawable(R.drawable.lychee_m3_suggestion)
                // as on the bar: the readings above, the word, the meaning under it
                repeat(3) { i ->
                    addView(TextView(context).apply {
                        gravity = Gravity.CENTER
                        isSingleLine = true
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        includeFontPadding = false
                        if (i != 1) visibility = GONE
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }
            return object : RecyclerView.ViewHolder(cell) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val (index, candidate) = items[position]
            val cell = holder.itemView as LinearLayout
            val english = style.fontLang == "en"
            // every cell the same height (the bar's lines), so the rows line up whatever arrives
            cell.layoutParams.height = cellHeight(english)
            val reading = cell.getChildAt(0) as TextView
            val word = cell.getChildAt(1) as TextView
            val meaning = cell.getChildAt(2) as TextView
            val color = if (index == 0 && style.fontLang != "en") palette.primary else palette.onSurface
            word.text = candidate.text
            word.typeface = app.lychee.fonts.LycheeFonts.forText(cell.context, candidate.text, style.fontLang)
                ?: android.graphics.Typeface.DEFAULT
            word.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (english) 17f else CandidateBarView.CHAR_SP * scale)
            word.setTextColor(color)
            val a = annotations[position]
            // every reading the bar shows (Jyutping or Yale, and the other language's when on), in full
            val lines = a?.lines.orEmpty()
            // a candidate without readings or a meaning keeps their room, so all cells match
            reading.visibility = if (lines.isNotEmpty()) VISIBLE else if (annotated) INVISIBLE else GONE
            if (a != null && lines.isNotEmpty()) {
                val text = android.text.SpannableStringBuilder()
                lines.forEachIndexed { i, line ->
                    if (i > 0) text.append('\n')
                    text.append(if (style.toneColours) ToneColours.apply(line, a.tones.getOrNull(i), palette.onSurface) else line)
                }
                reading.isSingleLine = false
                reading.maxLines = lines.size
                reading.text = text
                reading.setTextSize(TypedValue.COMPLEX_UNIT_SP, CandidateBarView.READING_SP * scale)
                reading.setTextColor(palette.primary)
            }
            meaning.visibility = if (a?.meaning != null) VISIBLE else if (annotated) INVISIBLE else GONE
            a?.meaning?.let {
                meaning.text = it
                meaning.setTextSize(TypedValue.COMPLEX_UNIT_SP, CandidateBarView.MEANING_SP * scale)
                meaning.setTextColor(palette.onSurfaceVariant)
            }
            cell.setOnClickListener {
                visibility = GONE
                (pickOverride ?: onPick)(index)
            }
            cell.setOnLongClickListener { v -> (longPickOverride ?: onLongPick)(index, v); true }
        }
    }

    /** Lines of readings each cell keeps room for (the bar's), and whether a meaning follows. */
    var lines: CandidateBarView.Lines? = null

    private fun cellHeight(english: Boolean): Int {
        if (english) return dp(48)
        val sp = resources.displayMetrics.scaledDensity
        val l = lines
        val readings = if (adapter.annotated) (l?.readings ?: 1) else 0
        val meaning = adapter.annotated && (l?.meaning ?: true)
        val h = CandidateBarView.CHAR_SP * adapter.scale * sp * 1.25f +
            readings * CandidateBarView.READING_SP * adapter.scale * sp * 1.3f +
            (if (meaning) CandidateBarView.MEANING_SP * adapter.scale * sp * 1.3f else 0f)
        return (h + dp(16)).toInt()
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        const val COLUMNS = 4
        val worker: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}
