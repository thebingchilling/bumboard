package app.bumboard.emoji

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.bumboard.ui.M3Colors
import com.android.inputmethod.keyboard.KeyboardActionListener
import com.android.inputmethod.keyboard.KeyboardTheme
import com.android.inputmethod.keyboard.internal.KeyVisualAttributes
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.utils.ResourceUtils

/**
 * BumBoard's emoji panel, in place of LatinIME's EmojiPalettesView: every Unicode 18 emoji in
 * one vertically scrolling grid with a header per category, category tabs that jump and follow
 * the scroll, recents, skin tones on long-press (two-person emoji included) and search.
 * Emoji are drawn with the bundled Noto Color Emoji font so new emoji show on older phones.
 */
class EmojiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    /** Called when the search box is tapped. */
    var onSearch: () -> Unit = {}

    private var listener: KeyboardActionListener? = null
    @Suppress("DEPRECATION")
    private val store = EmojiStore(PreferenceManager.getDefaultSharedPreferences(context))
    private val tonePicker = TonePicker(context)
    private val palette = M3Colors.of(context)
    private val adapter = Adapter()
    private val layoutManager = GridLayoutManager(context, 8)
    private val grid = RecyclerView(context)
    private val tabs = LinearLayout(context)
    private val tabViews = mutableListOf<ImageView>()
    private var sections: List<Section> = emptyList()
    private val repeat = Handler(Looper.getMainLooper())

    private class Section(val id: String, val title: Int, val icon: Int, val emoji: List<Emoji>, val forms: List<String>?)
    private sealed class Row {
        class Header(val title: Int) : Row()
        class Cell(val emoji: Emoji, val form: String?, val section: Int) : Row()
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(palette.surface)
        addView(searchBox(), LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        grid.layoutManager = layoutManager
        grid.adapter = adapter
        grid.itemAnimator = null
        grid.setHasFixedSize(true)
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = followScroll()
        })
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (adapter.rows.getOrNull(position) is Row.Header) layoutManager.spanCount else 1
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        pinchToResize()
        addView(bottomBar(), LayoutParams(LayoutParams.MATCH_PARENT, dp(50)))
    }

    // ------------------------------------------------------------ LatinIME's EmojiPalettesView API

    fun setKeyboardActionListener(listener: KeyboardActionListener) {
        this.listener = listener
    }

    @Suppress("UNUSED_PARAMETER")
    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        // Plain Views; nothing to do.
    }

    @Suppress("UNUSED_PARAMETER")
    fun startEmojiPalettes(switchToAlphaLabel: String?, keyVisualAttr: KeyVisualAttributes?, iconSet: KeyboardIconsSet?) {
        applyColumns()
        EmojiData.load(context) { rebuild() }
    }

    fun stopEmojiPalettes() {
        tonePicker.dismiss()
        repeat.removeCallbacksAndMessages(null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Same size as the keyboard plus its suggestion strip, like LatinIME's emoji palette.
        val res = context.resources
        val layout = app.bumboard.layout.BumLayout.get(context)
        val full = ResourceUtils.getDefaultKeyboardWidth(context)
        // one-handed: the panel spans the whole width; floating: the floating keyboard's size
        val width = (if (layout.mode == app.bumboard.layout.BumLayout.Mode.FLOATING) layout.keyboardWidth(full) else full) +
            paddingLeft + paddingRight
        val height = layout.keyboardHeight(ResourceUtils.getDefaultKeyboardHeight(res)) +
            res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height) + paddingTop + paddingBottom
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyColumns()
    }

    /**
     * Columns: the saved emoji size, or as many 46 dp cells as fit. Each emoji fills its cell,
     * so fewer columns means bigger emoji.
     */
    private fun applyColumns() {
        val w = grid.width.takeIf { it > 0 } ?: (width - paddingLeft - paddingRight)
        if (w <= 0) return
        @Suppress("DEPRECATION")
        val saved = PreferenceManager.getDefaultSharedPreferences(context).getInt(PREF_COLUMNS, 0)
        val columns = if (saved > 0) saved else (w / dp(46)).coerceAtLeast(6)
        cellSize = w / columns
        if (layoutManager.spanCount != columns) layoutManager.spanCount = columns
        adapter.notifyDataSetChanged()
    }

    /** Pinch the grid to change the emoji size (the number of columns), like a photo grid. */
    private fun pinchToResize() {
        val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var total = 1f
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                total = 1f
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                total *= detector.scaleFactor
                val step = when {
                    total > 1.18f -> -1
                    total < 0.85f -> 1
                    else -> 0
                }
                if (step != 0) {
                    total = 1f
                    val columns = (layoutManager.spanCount + step).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
                    @Suppress("DEPRECATION")
                    PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREF_COLUMNS, columns).apply()
                    applyColumns()
                }
                return true
            }
        })
        grid.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                detector.onTouchEvent(e)
                return e.pointerCount > 1
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                detector.onTouchEvent(e)
            }
        })
    }

    /** Width (and height) of one emoji cell, in pixels. */
    private var cellSize = 0

    // ------------------------------------------------------------ content

    private fun rebuild() {
        val recents = store.recents().filter { EmojiData.byForm.containsKey(it) }
        sections = buildList {
            if (recents.isNotEmpty()) {
                add(Section("recents", R.string.bum_emoji_recents, R.drawable.ic_sym_schedule,
                    recents.map { EmojiData.byForm.getValue(it) }, recents))
            }
            EmojiData.groups.forEach { g ->
                val (title, icon) = GROUP_INFO[g.id] ?: return@forEach
                add(Section(g.id, title, icon, g.items, null))
            }
        }
        val rows = mutableListOf<Row>()
        sections.forEachIndexed { s, section ->
            rows += Row.Header(section.title)
            section.emoji.forEachIndexed { i, e -> rows += Row.Cell(e, section.forms?.get(i), s) }
        }
        adapter.rows = rows
        adapter.notifyDataSetChanged()
        buildTabs()
        layoutManager.scrollToPositionWithOffset(0, 0)
        selectTab(0)
    }

    private fun buildTabs() {
        tabs.removeAllViews()
        tabViews.clear()
        sections.forEachIndexed { s, section ->
            val tab = ImageView(context).apply {
                setImageResource(section.icon)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = context.getString(section.title)
                setOnClickListener { jumpTo(s) }
            }
            tabViews += tab
            tabs.addView(tab, LayoutParams(dp(44), dp(36)).apply { gravity = Gravity.CENTER_VERTICAL })
        }
    }

    private fun jumpTo(section: Int) {
        val position = adapter.rows.indexOfFirst { it is Row.Cell && it.section == section } - 1
        layoutManager.scrollToPositionWithOffset(position.coerceAtLeast(0), 0)
        selectTab(section)
    }

    private fun followScroll() {
        val first = layoutManager.findFirstVisibleItemPosition()
        val row = adapter.rows.getOrNull(first) ?: return
        val section = when (row) {
            is Row.Cell -> row.section
            is Row.Header -> (adapter.rows.getOrNull(first + 1) as? Row.Cell)?.section ?: return
        }
        selectTab(section)
    }

    private fun selectTab(section: Int) {
        tabViews.forEachIndexed { i, tab ->
            val selected = i == section
            tab.imageTintList = ColorStateList.valueOf(if (selected) palette.onSecondaryContainer else palette.onSurfaceVariant)
            tab.background = if (selected) pill(palette.secondaryContainer, 18) else null
        }
    }

    private fun type(text: String) {
        listener?.onTextInput(text)
        store.addRecent(text)
    }

    // ------------------------------------------------------------ chrome

    private fun searchBox() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(16), 0)
        background = pill(palette.surfaceContainerHigh, 24)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sym_search)
            imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
        })
        addView(TextView(context).apply {
            text = context.getString(R.string.bum_emoji_search)
            setTextColor(palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(12), 0, 0, 0)
        })
        setOnClickListener { onSearch() }
    }.let { box ->
        // inset so it reads as a pill rather than a bar
        LinearLayout(context).apply {
            setPadding(dp(8), dp(6), dp(8), dp(2))
            addView(box, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bottomBar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(2), dp(6), dp(4))
        // ABC and delete are drawn as the keyboard's own functional keys, flat or raised.
        addView(TextView(context).apply {
            text = context.getString(R.string.bum_emoji_abc)
            setTextColor(palette.onSecondaryContainer)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            background = functionalKey()
            setOnClickListener { sendCode(Constants.CODE_ALPHA_FROM_EMOJI) }
        }, LayoutParams(dp(64), dp(40)))
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sym_backspace)
            imageTintList = ColorStateList.valueOf(palette.onSecondaryContainer)
            scaleType = ImageView.ScaleType.CENTER
            background = functionalKey()
            contentDescription = context.getString(R.string.bum_emoji_delete)
            setOnTouchListener { view, event ->
                view.isPressed = event.actionMasked == MotionEvent.ACTION_DOWN ||
                    event.actionMasked == MotionEvent.ACTION_MOVE
                onDeleteTouch(event)
                true
            }
        }, LayoutParams(dp(64), dp(40)))
    }

    private fun onDeleteTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                delete()
                repeat.postDelayed(object : Runnable {
                    override fun run() {
                        delete()
                        repeat.postDelayed(this, 50)
                    }
                }, 400)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> repeat.removeCallbacksAndMessages(null)
        }
    }

    private fun delete() = sendCode(Constants.CODE_DELETE)

    private fun sendCode(code: Int) {
        val l = listener ?: return
        l.onPressKey(code, 0, true)
        l.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        l.onReleaseKey(code, false)
    }

    /** The keyboard's functional key background in the selected key style. */
    private fun functionalKey() = context.getDrawable(
        if (KeyboardTheme.getKeyboardTheme(context).mThemeId == KeyboardTheme.THEME_ID_BUM_RAISED)
            R.drawable.bum_m3_key_functional_raised
        else R.drawable.bum_m3_key_functional_flat
    )

    private fun pill(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    // ------------------------------------------------------------ grid

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var rows: List<Row> = emptyList()

        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = TextView(context).apply {
                gravity = if (viewType == 0) Gravity.CENTER_VERTICAL else Gravity.CENTER
                if (viewType == 0) {
                    setTextColor(palette.onSurfaceVariant)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setPadding(dp(14), dp(10), dp(14), dp(4))
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                } else {
                    typeface = EmojiData.typeface
                    includeFontPadding = false
                    background = selectable()
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
                }
            }
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val view = holder.itemView as TextView
            when (val row = rows[position]) {
                is Row.Header -> view.text = context.getString(row.title)
                is Row.Cell -> {
                    // the emoji fills its cell
                    val size = if (cellSize > 0) cellSize else dp(46)
                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * EMOJI_FILL)
                    val lp = view.layoutParams
                    if (lp.height != size) {
                        lp.height = size
                        view.layoutParams = lp
                    }
                    val shown = row.form ?: store.shown(row.emoji)
                    view.text = shown
                    view.contentDescription = row.emoji.name
                    view.setOnClickListener { type(shown) }
                    view.setOnLongClickListener {
                        if (row.emoji.tones == null) return@setOnLongClickListener false
                        tonePicker.show(view, row.emoji, shown) { picked ->
                            store.choose(row.emoji, picked)
                            type(picked)
                            notifyItemChanged(holder.bindingAdapterPosition)
                        }
                        true
                    }
                }
            }
        }

        private fun selectable() = TypedValue().let { value ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
            context.getDrawable(value.resourceId)
        }
    }

    companion object {
        /** Saved number of emoji columns (0: as many 46 dp cells as fit). */
        const val PREF_COLUMNS = "bum_emoji_columns"
        const val MIN_COLUMNS = 5
        const val MAX_COLUMNS = 12
        /** Emoji glyph size as a share of its cell. */
        private const val EMOJI_FILL = 0.74f

        private val GROUP_INFO = mapOf(
            "smileys" to (R.string.bum_emoji_smileys to R.drawable.ic_sym_mood),
            "people" to (R.string.bum_emoji_people to R.drawable.ic_sym_emoji_people),
            "animals" to (R.string.bum_emoji_animals to R.drawable.ic_sym_pets),
            "food" to (R.string.bum_emoji_food to R.drawable.ic_sym_restaurant),
            "travel" to (R.string.bum_emoji_travel to R.drawable.ic_sym_directions_car),
            "activities" to (R.string.bum_emoji_activities to R.drawable.ic_sym_sports_soccer),
            "objects" to (R.string.bum_emoji_objects to R.drawable.ic_sym_lightbulb),
            "symbols" to (R.string.bum_emoji_symbols to R.drawable.ic_sym_emoji_symbols),
            "flags" to (R.string.bum_emoji_flags to R.drawable.ic_sym_flag),
        )
    }
}
