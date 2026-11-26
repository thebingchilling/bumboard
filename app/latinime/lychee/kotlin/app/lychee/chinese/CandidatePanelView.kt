package app.lychee.chinese

import android.content.Context
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
 * as it needs and spaced as tightly as on the row. Opened and closed with the ⌄ / ⌃ at the
 * end of the candidate row, or a swipe down on the row (the notch); tap one to type it.
 * Candidates the phone cannot draw are left out. Six to a row, five when one is a longer word
 * (fewer for longer still), each at the bar's size. Along its bottom, exactly where the
 * keyboard's bottom row is: ?123, emoji, space, ⌫ (deleting the letters typed, the list
 * following) and Enter; at its top, a single-character switch ([danzi]: 單字, 单字, 単漢字,
 * 한 글자) shows one character at a time.
 */
class CandidatePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** A candidate was tapped; the index is Rime's. */
    var onPick: (Int) -> Unit = {}
    /** A candidate was held: its dictionary entry. */
    var onLongPick: (Int, android.view.View) -> Unit = { _, _ -> }
    /** A key of the bottom row was tapped (?123, emoji, space, ⌫, Enter): its code. */
    var onKey: (Int) -> Unit = {}
    /** The single-character switch's label for this keyboard (null: no switch). */
    var danzi: String? = null
        set(value) {
            field = value
            danziChip.text = value.orEmpty()
            header.visibility = if (value != null) VISIBLE else GONE
        }
    /** Whether only single characters show (the switch on). */
    var singleOnly = false
        set(value) {
            field = value
            paintDanzi()
        }
    /** The switch was turned on or off. */
    var onSingleOnly: (Boolean) -> Unit = {}

    private val palette = M3Colors.of(context)
    private val grid = RecyclerView(context)
    private val adapter = Adapter()
    private val layoutManager = GridLayoutManager(context, COLUMNS)
    /** The list fades out at the bottom, above the navigation buttons, instead of being cut. */
    private val fade = android.view.View(context).apply {
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(palette.surface and 0x00FFFFFF, androidx.core.graphics.ColorUtils.setAlphaComponent(palette.surface, 0xE0), palette.surface),
        )
    }

    private val danziChip = android.widget.TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 600)
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        app.lychee.layout.LycheeMotion.springy(this)
        setOnClickListener {
            singleOnly = !singleOnly
            onSingleOnly(singleOnly)
        }
    }
    private val header = FrameLayout(context).apply {
        addView(danziChip, LayoutParams(LayoutParams.WRAP_CONTENT, dp(30), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(10) })
        visibility = GONE
    }

    private fun paintDanzi() {
        danziChip.setTextColor(if (singleOnly) palette.onPrimary else palette.onSurfaceVariant)
        danziChip.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(15).toFloat()
            if (singleOnly) setColor(palette.primary) else { setColor(0); setStroke(dp(1), palette.outline) }
        }
    }

    /** ?123, emoji, space, ⌫ and Enter, where the keyboard's bottom row is and as tall. */
    private val keyBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        fun key(view: android.view.View, weight: Float) = addView(view, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight))
        key(textKey(context.getString(R.string.lychee_symbols_key)) { onKey(com.android.inputmethod.latin.common.Constants.CODE_SWITCH_ALPHA_SYMBOL) }, 15f)
        key(iconKey(R.drawable.ic_sym_mood, R.string.lychee_emoji) { onKey(com.android.inputmethod.latin.common.Constants.CODE_EMOJI) }, 10f)
        key(iconKey(R.drawable.ic_sym_space_bar, R.string.lychee_numpad_space, letter = true) { onKey(com.android.inputmethod.latin.common.Constants.CODE_SPACE) }, 50f)
        key(iconKey(R.drawable.ic_sym_backspace, R.string.lychee_emoji_delete, repeats = true) { onKey(com.android.inputmethod.latin.common.Constants.CODE_DELETE) }, 10f)
        key(android.widget.ImageView(context).apply {
            setImageResource(R.drawable.ic_sym_keyboard_return)
            imageTintList = android.content.res.ColorStateList.valueOf(palette.onPrimary)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            contentDescription = context.getString(R.string.lychee_numpad_enter)
            background = app.lychee.layout.LycheePanels.accentKey(context)
            setOnClickListener { onKey(com.android.inputmethod.latin.common.Constants.CODE_ENTER) }
        }, 15f)
    }

    private val repeater = android.os.Handler(android.os.Looper.getMainLooper())

    private fun textKey(label: String, onClick: () -> Unit) = android.widget.TextView(context).apply {
        text = label
        setTextColor(palette.onSecondaryContainer)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
        gravity = Gravity.CENTER
        background = app.lychee.layout.LycheePanels.functionalKey(context)
        setOnClickListener { onClick() }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun iconKey(icon: Int, label: Int, letter: Boolean = false, repeats: Boolean = false, onClick: () -> Unit) =
        android.widget.ImageView(context).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(if (letter) palette.onSurface else palette.onSecondaryContainer)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            contentDescription = context.getString(label)
            background = if (letter) app.lychee.layout.LycheePanels.letterKey(context) else app.lychee.layout.LycheePanels.functionalKey(context)
            if (!repeats) setOnClickListener { onClick() }
            else setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        onClick()
                        repeater.postDelayed(object : Runnable {
                            override fun run() { onClick(); repeater.postDelayed(this, 50) }
                        }, 400)
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        repeater.removeCallbacksAndMessages(null)
                    }
                }
                true
            }
        }

    /** The key bar lined up with the keyboard's bottom row: as tall, as high above the bottom, keys inset by half a gap. */
    private fun alignKeys() {
        val row = app.lychee.layout.LycheePanels.keyRow(this)
        (keyBar.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            if (lp.height != row.height || lp.bottomMargin != row.bottomPadding) {
                lp.height = row.height
                lp.bottomMargin = row.bottomPadding
                keyBar.layoutParams = lp
            }
        }
        val half = row.gap / 2
        keyBar.setPadding(half, 0, half, 0)
        for (i in 0 until keyBar.childCount) {
            val c = keyBar.getChildAt(i)
            (c.layoutParams as LinearLayout.LayoutParams).let { if (it.topMargin != half) { it.setMargins(half, half, half, half); c.layoutParams = it } }
        }
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE) repeater.removeCallbacksAndMessages(null)
        super.setVisibility(visibility)
    }

    init {
        isClickable = true
        setBackgroundColor(palette.surface)
        paintDanzi()
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = adapter.items.getOrNull(position)?.let { spanFor(it.value.text) } ?: 1
        }
        grid.layoutManager = layoutManager
        grid.adapter = adapter
        // plain cells with small faint lines between them (Dividers)
        grid.addItemDecoration(Dividers())
        grid.setPadding(dp(6), dp(2), dp(6), dp(FADE_DP))
        grid.clipToPadding = false
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(36)))
            addView(FrameLayout(context).apply {
                addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                addView(fade, LayoutParams(LayoutParams.MATCH_PARENT, dp(FADE_DP), Gravity.BOTTOM))
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            addView(keyBar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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
        val english = style.fontLang == "en"
        adapter.items = candidates.withIndex().filter { it.index > after &&
            (if (english) CandidateBarView.renderable(it.value.text) else CandidateBarView.shown(it.value.text)) &&
            // the single-character switch: one character at a time
            (english || !singleOnly || it.value.text.codePointCount(0, it.value.text.length) == 1) }
        header.visibility = if (danzi != null && !english) VISIBLE else GONE
        alignKeys()
        adapter.scale = scale
        adapter.style = style
        adapter.annotations.clear()
        adapter.annotated = annotate != null
        adapter.notifyDataSetChanged()
        grid.scrollToPosition(0)
        // room under the last row: it scrolls clear of the fade above the key row
        grid.setPadding(dp(6), dp(2), dp(6), dp(FADE_DP))
        fade.layoutParams = (fade.layoutParams as LayoutParams).apply { height = dp(FADE_DP) }
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

    /** A candidate's cell: plain (the first one's word in the accent colour), rippling when pressed. */
    private fun tile(@Suppress("UNUSED_PARAMETER") first: Boolean): android.graphics.drawable.Drawable {
        val mask = android.graphics.drawable.ColorDrawable(0xFFFFFFFF.toInt())
        return android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(palette.onSurface, 0x1F)), null, mask)
    }

    /**
     * Small, faint lines between the candidates: a short upright one between neighbours in a
     * row, and a hairline above each row but the first (inset at the sides), never boxes.
     */
    private inner class Dividers : RecyclerView.ItemDecoration() {
        private val paint = android.graphics.Paint().apply {
            color = androidx.core.graphics.ColorUtils.setAlphaComponent(palette.outline, 0x40)
            strokeWidth = maxOf(1f, resources.displayMetrics.density)
        }
        override fun onDraw(c: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val inset = dp(12).toFloat()
            val left = parent.paddingLeft.toFloat()
            val right = (parent.width - parent.paddingRight).toFloat()
            for (i in 0 until parent.childCount) {
                val v = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(v)
                if (pos == RecyclerView.NO_POSITION) continue
                val lp = v.layoutParams as GridLayoutManager.LayoutParams
                // between this cell and the next in its row
                if (lp.spanIndex + lp.spanSize < layoutManager.spanCount && pos + 1 < adapter.itemCount &&
                    layoutManager.spanSizeLookup.getSpanGroupIndex(pos + 1, layoutManager.spanCount) ==
                    layoutManager.spanSizeLookup.getSpanGroupIndex(pos, layoutManager.spanCount)) {
                    val x = v.right.toFloat()
                    c.drawLine(x, v.top + inset, x, v.bottom - inset, paint)
                }
                // above each row but the first (once, from the row's first cell)
                if (lp.spanIndex == 0 && layoutManager.spanSizeLookup.getSpanGroupIndex(pos, layoutManager.spanCount) > 0) {
                    c.drawLine(left + dp(8), v.top.toFloat(), right - dp(8), v.top.toFloat(), paint)
                }
            }
        }
    }

    /**
     * A candidate's share of a row, as on the bar: six to a row for one or two characters, five
     * for three or four, three for up to six, two or one for longer; English three to a row.
     * Each candidate stays at the bar's size (never shrunk to fit more).
     */
    private fun spanFor(text: String): Int {
        if (adapter.style.fontLang == "en") return COLUMNS / 3
        return when (text.codePointCount(0, text.length)) {
            in 0..2 -> COLUMNS / 6
            in 3..4 -> COLUMNS / 5
            in 5..6 -> COLUMNS / 3
            in 7..10 -> COLUMNS / 2
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
                setPadding(0, dp(2), 0, dp(2))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
                app.lychee.layout.LycheeMotion.springy(this)
                // the word, its readings and meaning under it, on a tile (set when bound)
                repeat(3) { i ->
                    addView(TextView(context).apply {
                        gravity = Gravity.CENTER
                        isSingleLine = true
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        includeFontPadding = false
                        if (i == 0) setPadding(dp(10), 0, dp(10), 0)
                        else {
                            visibility = GONE
                            typeface = app.lychee.fonts.LycheeFonts.appFont(context, 500)
                            setPadding(dp(2), 0, dp(2), 0)
                        }
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                // the meaning in the app's own font, like the bar's
                (getChildAt(2) as TextView).typeface = android.graphics.Typeface.DEFAULT
            }
            return object : RecyclerView.ViewHolder(cell) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val (index, candidate) = items[position]
            val cell = holder.itemView as LinearLayout
            val english = style.fontLang == "en"
            // every cell the same height (the bar's lines), so the rows line up whatever arrives
            cell.layoutParams.height = cellHeight(english)
            val word = cell.getChildAt(0) as TextView
            val reading = cell.getChildAt(1) as TextView
            val meaning = cell.getChildAt(2) as TextView
            val color = if (index == 0 && style.fontLang != "en") palette.primary else palette.onSurface
            cell.background = tile(index == 0 && !english)
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
                    text.append(if (style.toneColours) ToneColours.apply(line, a.tones.getOrNull(i), color) else line)
                }
                reading.isSingleLine = false
                reading.maxLines = lines.size
                reading.text = text
                reading.setTextSize(TypedValue.COMPLEX_UNIT_SP, CandidateBarView.READING_SP * scale)
                reading.setTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0xD0))
            }
            meaning.visibility = if (a?.meaning != null) VISIBLE else if (annotated) INVISIBLE else GONE
            a?.meaning?.let {
                meaning.text = it
                meaning.setTextSize(TypedValue.COMPLEX_UNIT_SP, CandidateBarView.MEANING_SP * scale)
                meaning.setTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0xA0))
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
        // the bar's own room round each cell, no more
        // the bar's room round each cell, and the tile's inset
        return (h + dp(12)).toInt()
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        /** A fine grid: candidates take as many columns as their width needs. */
        const val COLUMNS = 60
        /** How tall the fade at the bottom is (above any navigation buttons). */
        const val FADE_DP = 32
        val worker: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}
