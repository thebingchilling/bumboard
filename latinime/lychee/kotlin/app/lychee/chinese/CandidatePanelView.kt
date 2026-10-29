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
 * Every candidate over the keys, like Gboard's expanded candidates, with the room the row
 * lacks: a scrolling grid of the words with their readings and meanings, each candidate as wide
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
        color = palette.outlineVariant
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
                for (i in 0 until parent.childCount) {
                    val v = parent.getChildAt(i)
                    c.drawLine(v.left.toFloat(), v.bottom.toFloat(), v.right.toFloat(), v.bottom.toFloat(), divider)
                    c.drawLine(v.right.toFloat(), v.top + v.height * 0.25f, v.right.toFloat(), v.bottom - v.height * 0.25f, divider)
                }
            }
        })
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    @Volatile
    private var generation = 0

    /**
     * Shows [candidates] (by Rime's index) that the phone can draw; [annotate], when given,
     * adds readings and meanings, looked up off the main thread.
     */
    fun show(
        candidates: List<Candidate>,
        scale: Float,
        annotate: ((Candidate) -> Annotation?)? = null,
        style: CandidateBarView.Style = CandidateBarView.Style(),
    ) {
        val gen = ++generation
        adapter.items = candidates.withIndex().filter { CandidateBarView.renderable(it.value.text) }
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
        val chars = text.codePointCount(0, text.length)
        return when {
            chars <= 1 -> 1
            chars <= 3 -> 2
            else -> 3
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
                repeat(3) { i ->
                    addView(TextView(context).apply {
                        gravity = Gravity.CENTER
                        isSingleLine = true
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        includeFontPadding = false
                        if (i > 0) visibility = GONE
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            }
            return object : RecyclerView.ViewHolder(cell) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val (index, candidate) = items[position]
            val cell = holder.itemView as LinearLayout
            cell.layoutParams.height = dp(if (annotated) 76 else 54)
            val word = cell.getChildAt(0) as TextView
            val reading = cell.getChildAt(1) as TextView
            val meaning = cell.getChildAt(2) as TextView
            val color = if (position == 0) palette.primary else palette.onSurface
            word.text = candidate.text
            word.typeface = app.lychee.fonts.LycheeFonts.forText(cell.context, candidate.text, style.fontLang)
                ?: android.graphics.Typeface.DEFAULT
            word.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f * scale)
            word.setTextColor(color)
            val a = annotations[position]
            val line = a?.lines?.firstOrNull()
            reading.visibility = if (line != null) VISIBLE else GONE
            if (line != null && a != null) {
                reading.text = if (style.toneColours) ToneColours.apply(line, a.tones.firstOrNull(), palette.onSurface) else line
                reading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scale)
                reading.setTextColor(palette.onSurfaceVariant)
            }
            meaning.visibility = if (a?.meaning != null) VISIBLE else GONE
            a?.meaning?.let {
                meaning.text = it
                meaning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * scale)
                meaning.setTextColor(palette.onSurfaceVariant)
            }
            cell.setOnClickListener {
                visibility = GONE
                onPick(index)
            }
            cell.setOnLongClickListener { v -> onLongPick(index, v); true }
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        const val COLUMNS = 6
        val worker: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}
