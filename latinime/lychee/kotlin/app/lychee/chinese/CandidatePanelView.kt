package app.lychee.chinese

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lychee.rime.Candidate
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * Every candidate over the keys, like Gboard's expanded candidates: a scrolling grid of the
 * characters alone, each candidate as wide as it needs (1 to 3 columns), thin dividers between.
 * Opened and closed with the ⌄ / ⌃ at the end of the candidate row; tap one to type it.
 * Candidates the phone cannot draw are left out.
 */
class CandidatePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** A candidate was tapped; the index is Rime's. */
    var onPick: (Int) -> Unit = {}

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

    /** Shows [candidates] (by Rime's index) that the phone can draw. */
    fun show(candidates: List<Candidate>, scale: Float) {
        adapter.items = candidates.withIndex().filter { CandidateBarView.renderable(it.value.text) }
        adapter.scale = scale
        adapter.notifyDataSetChanged()
        grid.scrollToPosition(0)
        visibility = VISIBLE
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

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val cell = TextView(context).apply {
                gravity = Gravity.CENTER
                isSingleLine = true
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
                background = context.getDrawable(R.drawable.lychee_m3_suggestion)
            }
            return object : RecyclerView.ViewHolder(cell) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val (index, candidate) = items[position]
            val cell = holder.itemView as TextView
            cell.text = candidate.text
            cell.typeface = app.lychee.fonts.LycheeFonts.chinese(cell.context) ?: android.graphics.Typeface.DEFAULT
            cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f * scale)
            cell.setTextColor(if (position == 0) palette.primary else palette.onSurface)
            cell.setOnClickListener {
                visibility = GONE
                onPick(index)
            }
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        const val COLUMNS = 6
    }
}
