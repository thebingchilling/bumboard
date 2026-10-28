package app.lychee.emoji

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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lychee.ui.M3Colors
import com.android.inputmethod.keyboard.KeyboardActionListener
import com.android.inputmethod.keyboard.internal.KeyVisualAttributes
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.utils.ResourceUtils

/**
 * Lychee's emoji panel, in place of LatinIME's EmojiPalettesView: every Unicode 18 emoji in
 * one vertically scrolling grid with a header per category, category tabs that jump and follow
 * the scroll, recents, skin tones on long-press (two-person emoji included) and search.
 * Emoji are drawn with the bundled Noto Color Emoji font so new emoji show on older phones.
 * Swipe sideways on the grid, or slide along the tabs, to move between categories.
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
    /** The category tabs; sliding a finger along them moves through the categories. */
    private val tabs = object : LinearLayout(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent) = true

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until childCount) {
                        val child = getChildAt(i)
                        if (ev.x >= child.left && ev.x < child.right) {
                            val section = tabViews.indexOf(child)
                            if (section >= 0 && section != currentSection) jumpTo(section)
                            break
                        }
                    }
                }
            }
            return true
        }
    }
    /** The category the grid is showing. */
    private var currentSection = 0
    /** One per section; null for recents, which has no tab (it is the top of the grid). */
    private val tabViews = mutableListOf<ImageView?>()
    private var sections: List<Section> = emptyList()
    private val repeat = Handler(Looper.getMainLooper())
    /** Delete, floating over the grid's bottom corner. */
    private val floatingDelete by lazy { deleteKey() }

    private class Section(val id: String, val title: Int, val icon: Int, val emoji: List<Emoji>, val forms: List<String>?)
    private sealed class Row {
        class Header(val title: Int) : Row()
        class Cell(val emoji: Emoji, val form: String?, val section: Int) : Row() {
            val kaomoji get() = emoji.keywords == KAOMOJI
        }
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
            // the floating delete steps aside while the grid scrolls, and comes back when it stops
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) =
                showFloatingDelete(newState == RecyclerView.SCROLL_STATE_IDLE)
        })
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                when (val row = adapter.rows.getOrNull(position)) {
                    is Row.Header -> layoutManager.spanCount
                    // kaomoji are wide: three to a row
                    is Row.Cell -> if (row.kaomoji) (layoutManager.spanCount / 3).coerceAtLeast(1) else 1
                    else -> 1
                }
        }
        // room around the emoji, like Gboard's: at the sides and above the bottom bar
        // delete floats over the grid's corner, so the categories get the whole bottom bar
        addView(FrameLayout(context).apply {
            addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(floatingDelete.apply { elevation = dp(3).toFloat() },
                FrameLayout.LayoutParams(dp(56), dp(44), Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = dp(4); bottomMargin = dp(4)
                })
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(dp(GRID_SIDE_DP), 0, dp(GRID_SIDE_DP), dp(6))
        })
        pinchToResize()
        swipeBetweenCategories()
        addView(bottomBar(), LayoutParams(LayoutParams.MATCH_PARENT, dp(50)).apply {
            setMargins(dp(6), 0, dp(6), dp(6))
        })
        // loaded ahead, so the panel opens full at once
        EmojiData.load(context) {}
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
        floatingDelete.animate().cancel()
        floatingDelete.apply { alpha = 1f; translationY = 0f; visibility = VISIBLE; isEnabled = true }
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
        val layout = app.lychee.layout.LycheeLayout.get(context)
        val full = ResourceUtils.getDefaultKeyboardWidth(context)
        // one-handed: as wide as the keys, plus the free side's padding (LayoutApplier)
        val width = layout.keyboardWidth(full) + paddingLeft + paddingRight
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
        val w = grid.width.takeIf { it > 0 } ?: (width - paddingLeft - paddingRight - 2 * dp(GRID_SIDE_DP))
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

    /** A sideways swipe on the grid goes to the next (left) or previous (right) category. */
    private fun swipeBetweenCategories() {
        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        grid.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            private var downX = 0f
            private var downY = 0f
            private var sideways = false

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.x
                        downY = e.y
                        sideways = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = kotlin.math.abs(e.x - downX)
                        val dy = kotlin.math.abs(e.y - downY)
                        if (e.pointerCount == 1 && rv.scrollState != RecyclerView.SCROLL_STATE_DRAGGING &&
                            dx > slop * 2 && dx > dy * 2) {
                            sideways = true
                        }
                    }
                }
                return sideways
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (e.actionMasked != MotionEvent.ACTION_UP) return
                val dx = e.x - downX
                if (sideways && kotlin.math.abs(dx) > dp(40)) {
                    val next = (currentSection + if (dx < 0) 1 else -1).coerceIn(0, sections.lastIndex)
                    if (next != currentSection) jumpTo(next)
                }
                sideways = false
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
                add(Section("recents", R.string.lychee_emoji_recents, R.drawable.ic_sym_schedule,
                    recents.map { EmojiData.byForm.getValue(it) }, recents))
            }
            EmojiData.groups.forEach { g ->
                val (title, icon) = GROUP_INFO[g.id] ?: return@forEach
                add(Section(g.id, title, icon, g.items, null))
            }
            add(Section(KAOMOJI, R.string.lychee_emoji_kaomoji, R.drawable.lychee_ic_kaomoji,
                Kaomoji.all.map { Emoji(it, it, KAOMOJI, null) }, Kaomoji.all))
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
            if (section.id == "recents") {
                tabViews += null
                return@forEachIndexed
            }
            val tab = ImageView(context).apply {
                setImageResource(section.icon)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = context.getString(section.title)
                setOnClickListener { jumpTo(s) }
            }
            tabViews += tab
            // the tabs share the bar between ABC and delete, so every category fits
            tabs.addView(tab, LayoutParams(0, dp(36), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
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
        currentSection = section
        tabViews.forEachIndexed { i, tab ->
            if (tab == null) return@forEachIndexed
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
            text = context.getString(R.string.lychee_emoji_search)
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
            text = context.getString(R.string.lychee_emoji_abc)
            setTextColor(palette.onSecondaryContainer)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
            gravity = Gravity.CENTER
            background = functionalKey()
            setOnClickListener { sendCode(Constants.CODE_ALPHA_FROM_EMOJI) }
        }, LayoutParams(dp(50), dp(40)))
        tabs.gravity = Gravity.CENTER_VERTICAL
        addView(tabs, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
    }

    private fun showFloatingDelete(show: Boolean) {
        val target = if (show) 1f else 0f
        if (floatingDelete.alpha == target && floatingDelete.isEnabled == show) return
        floatingDelete.isEnabled = show
        floatingDelete.animate().cancel()
        floatingDelete.animate().alpha(target).translationY(if (show) 0f else dp(16).toFloat())
            .setDuration(150).withEndAction { floatingDelete.visibility = if (show) VISIBLE else INVISIBLE }
            .withStartAction { if (show) floatingDelete.visibility = VISIBLE }.start()
        if (!show) repeat.removeCallbacksAndMessages(null)
    }

    private fun deleteKey() = ImageView(context).apply {
        setImageResource(R.drawable.ic_sym_backspace)
        imageTintList = ColorStateList.valueOf(palette.onSecondaryContainer)
        scaleType = ImageView.ScaleType.CENTER
        background = functionalKey()
        contentDescription = context.getString(R.string.lychee_emoji_delete)
        setOnTouchListener { view, event ->
            view.isPressed = event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_MOVE
            onDeleteTouch(event)
            true
        }
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
    private fun functionalKey() = app.lychee.layout.LycheePanels.functionalKey(context)

    private fun pill(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    // ------------------------------------------------------------ grid

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var rows: List<Row> = emptyList()

        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = when (val row = rows[position]) {
            is Row.Header -> 0
            is Row.Cell -> if (row.kaomoji) 2 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = EmojiCell(context).apply {
                gravity = if (viewType == 0) Gravity.CENTER_VERTICAL else Gravity.CENTER
                if (viewType == 0) {
                    setTextColor(palette.onSurfaceVariant)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
                    setPadding(dp(14), dp(10), dp(14), dp(4))
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                } else if (viewType == 2) {
                    setTextColor(palette.onSurface)
                    isSingleLine = true
                    includeFontPadding = false
                    background = selectable()
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
                } else {
                    typeface = app.lychee.fonts.LycheeFonts.emoji(context)
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
                is Row.Header -> {
                    view.text = context.getString(row.title)
                    (view as EmojiCell).hasTones = false
                }
                is Row.Cell -> {
                    // the emoji fills its cell
                    val size = if (cellSize > 0) cellSize else dp(46)
                    if (row.kaomoji) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    else view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * EMOJI_FILL)
                    val lp = view.layoutParams
                    if (lp.height != size) {
                        lp.height = size
                        view.layoutParams = lp
                    }
                    val shown = row.form ?: store.shown(row.emoji)
                    view.text = shown
                    view.contentDescription = row.emoji.name
                    (view as EmojiCell).hasTones = row.emoji.tones != null
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

    /** An emoji in the grid; one with skin tones has a small marker in its corner, like Gboard. */
    private inner class EmojiCell(context: Context) : TextView(context) {
        var hasTones = false
            set(value) {
                if (field != value) { field = value; invalidate() }
            }
        private val marker = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurfaceVariant
            alpha = 0x90
        }
        private val path = android.graphics.Path()

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            if (!hasTones) return
            val s = dp(6).toFloat()
            val inset = dp(4).toFloat()
            val r = width - inset
            val b = height - inset
            path.reset()
            path.moveTo(r, b - s)
            path.lineTo(r, b)
            path.lineTo(r - s, b)
            path.close()
            canvas.drawPath(path, marker)
        }
    }

    companion object {
        /** Saved number of emoji columns (0: as many 46 dp cells as fit). */
        const val PREF_COLUMNS = "lychee_emoji_columns"
        const val MIN_COLUMNS = 5
        const val MAX_COLUMNS = 12
        /** Emoji glyph size as a share of its cell. */
        private const val EMOJI_FILL = 0.74f
        private const val GRID_SIDE_DP = 8
        /** The kaomoji section's id, and the keywords that mark its cells. */
        private const val KAOMOJI = "kaomoji"

        private val GROUP_INFO = mapOf(
            "smileys" to (R.string.lychee_emoji_smileys to R.drawable.ic_sym_mood),
            "people" to (R.string.lychee_emoji_people to R.drawable.ic_sym_emoji_people),
            "animals" to (R.string.lychee_emoji_animals to R.drawable.ic_sym_pets),
            "food" to (R.string.lychee_emoji_food to R.drawable.ic_sym_restaurant),
            "travel" to (R.string.lychee_emoji_travel to R.drawable.ic_sym_directions_car),
            "activities" to (R.string.lychee_emoji_activities to R.drawable.ic_sym_sports_soccer),
            "objects" to (R.string.lychee_emoji_objects to R.drawable.ic_sym_lightbulb),
            "symbols" to (R.string.lychee_emoji_symbols to R.drawable.ic_sym_emoji_symbols),
            "flags" to (R.string.lychee_emoji_flags to R.drawable.ic_sym_flag),
        )
    }
}
