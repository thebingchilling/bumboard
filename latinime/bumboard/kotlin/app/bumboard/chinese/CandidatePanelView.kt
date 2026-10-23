package app.bumboard.chinese

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.bumboard.core.Annotation
import app.bumboard.layout.BumPanels
import app.bumboard.rime.Candidate
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R
import java.util.concurrent.Executors

/**
 * Every candidate over the keys, opened from the ⌄ at the end of the candidate row: roomy cards
 * with the characters, all the readings chosen in Settings and the full meaning. Tap a card to
 * type it; Back (bottom left) returns to the keys. Readings and meanings are looked up off the
 * main thread and filled in as they arrive.
 */
class CandidatePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onPick: (Int) -> Unit = {}

    private val palette = M3Colors.of(context)
    private val grid = RecyclerView(context)
    private val adapter = Adapter()
    private var annotations = arrayOfNulls<Annotation>(0)
    @Volatile
    private var generation = 0

    init {
        orientation = VERTICAL
        isClickable = true
        setBackgroundColor(palette.surface)
        grid.layoutManager = GridLayoutManager(context, COLUMNS)
        grid.adapter = adapter
        grid.setPadding(dp(6), dp(6), dp(6), dp(6))
        grid.clipToPadding = false
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        // Back in the bottom-left corner
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(2))
            addView(BumPanels.backButton(context) { visibility = GONE }, LayoutParams(dp(48), dp(48)))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(50)))
        visibility = GONE
    }

    /** Shows [candidates]; [annotate] (null when readings are off) runs in the background. */
    fun show(candidates: List<Candidate>, annotate: ((Candidate) -> Annotation?)?, scale: Float) {
        val gen = ++generation
        adapter.candidates = candidates
        adapter.scale = scale
        annotations = arrayOfNulls(candidates.size)
        adapter.notifyDataSetChanged()
        grid.scrollToPosition(0)
        visibility = VISIBLE
        if (annotate == null) return
        worker.execute {
            candidates.forEachIndexed { i, candidate ->
                if (gen != generation) return@execute
                val a = runCatching { annotate(candidate) }.getOrNull() ?: return@forEachIndexed
                post {
                    if (gen == generation && i < annotations.size) {
                        annotations[i] = a
                        adapter.notifyItemChanged(i)
                    }
                }
            }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var candidates: List<Candidate> = emptyList()
        var scale = 1f

        override fun getItemCount() = candidates.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val card = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                minimumHeight = dp(72)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            }
            return object : RecyclerView.ViewHolder(card) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val card = holder.itemView as LinearLayout
            card.removeAllViews()
            val first = position == 0
            card.background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (first) palette.primaryContainer else palette.surfaceContainer)
            }
            val color = if (first) palette.onPrimaryContainer else palette.onSurface
            card.addView(text(candidates[position].text, 24f * scale, color, bold = false, lines = 2))
            annotations.getOrNull(position)?.let { a ->
                a.lines.forEach { card.addView(text(it, 12f * scale, ColorUtils.setAlphaComponent(color, 0xD0), bold = true, lines = 2)) }
                a.meaning?.let { card.addView(text(it, 11.5f * scale, ColorUtils.setAlphaComponent(color, 0xB0), bold = false, lines = 4)) }
            }
            card.setOnClickListener {
                val at = holder.bindingAdapterPosition
                if (at >= 0) {
                    visibility = GONE
                    onPick(at)
                }
            }
        }
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean, lines: Int) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        gravity = Gravity.CENTER
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        const val COLUMNS = 3
        val worker = Executors.newSingleThreadExecutor()
    }
}
