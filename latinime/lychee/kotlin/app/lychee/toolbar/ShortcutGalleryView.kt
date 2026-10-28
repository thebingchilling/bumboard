package app.lychee.toolbar

import android.content.ClipData
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import app.lychee.lycheeHost
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * Every shortcut over the keys, like Gboard's menu: pages of two columns of rounded buttons
 * (icon, name and › for those that open a panel), swiped sideways, with dots under them and a
 * pencil in the corner. The menu button on the bar (now ✕) closes it. Tap a shortcut to use it.
 *
 * Editing (the pencil; the bar's ✓ ends it): the menu holds the shortcuts not on the bar. Touch
 * and hold one to drag it: within the menu to reorder, or up onto the bar to put it there;
 * shortcuts dragged down from the bar land here, taken off it.
 */
class ShortcutGalleryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    @Suppress("DEPRECATION")
    private val store = ShortcutStore(PreferenceManager.getDefaultSharedPreferences(context))
    private val palette = M3Colors.of(context)
    private var editing = false
    private var items: MutableList<Shortcut> = mutableListOf()

    private val pages = RecyclerView(context)
    private val pager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    private val adapter = PageAdapter()
    private val dots = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    private val hint = TextView(context).apply {
        setText(R.string.lychee_gallery_hint)
        setTextColor(palette.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 400)
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
    }
    private val pencil = ImageView(context).apply {
        setImageResource(R.drawable.ic_sym_edit)
        imageTintList = ColorStateList.valueOf(palette.onSurface)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.lychee_gallery_edit)
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), palette.outline)
        }
        setOnClickListener { setEditing(!editing) }
    }
    /** Shown when every shortcut is on the bar. */
    private val empty = TextView(context).apply {
        setText(R.string.lychee_gallery_all_on_bar)
        setTextColor(palette.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(dp(24), 0, dp(24), 0)
        visibility = GONE
    }

    init {
        isClickable = true
        setBackgroundColor(palette.surface)
        pages.layoutManager = pager
        pages.adapter = adapter
        pages.overScrollMode = OVER_SCROLL_NEVER
        PagerSnapHelper().attachToRecyclerView(pages)
        pages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, state: Int) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) updateDots()
            }
        })
        pages.setOnDragListener { _, event -> onDrag(event) }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(pages, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(FrameLayout(context).apply {
                addView(hint, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START)
                    .apply { leftMargin = dp(16) })
                addView(dots, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
                addView(pencil, LayoutParams(dp(64), dp(36), Gravity.END or Gravity.CENTER_VERTICAL)
                    .apply { rightMargin = dp(12) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    private fun toolbar(): ToolbarView? = rootView.findViewById(R.id.lychee_toolbar)

    fun show() {
        visibility = VISIBLE
        setEditing(false)
        pages.scrollToPosition(0)
        toolbar()?.let { bar ->
            bar.onEditToggle = { setEditing(!editing) }
            bar.onChanged = { load() }
        }
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        // the bar's menu button is ✕ (✓ while editing) only while the menu is open
        if (visibility != VISIBLE) {
            editing = false
            toolbar()?.mode = ToolbarView.Mode.NORMAL
        }
    }

    private fun setEditing(on: Boolean) {
        editing = on
        toolbar()?.mode = if (on) ToolbarView.Mode.EDIT else ToolbarView.Mode.GALLERY
        // editing: the hint in place of the dots, the pencil hidden, the menu outlined
        hint.visibility = if (on) VISIBLE else GONE
        pencil.visibility = if (on) GONE else VISIBLE
        foreground = if (on) GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(2), palette.outline)
        } else null
        load()
    }

    /** The shortcuts not already on the bar (those are one tap away there). */
    private fun load() {
        val bar = store.toolbar()
        items = store.order().filter { it !in bar }.toMutableList()
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) VISIBLE else GONE
        updateDots()
    }

    private val pageCount get() = (items.size + PER_PAGE - 1) / PER_PAGE

    private fun updateDots() {
        dots.removeAllViews()
        val count = pageCount
        dots.visibility = if (count > 1 && !editing) VISIBLE else GONE
        val current = pager.findFirstCompletelyVisibleItemPosition().coerceAtLeast(0)
        repeat(count) { i ->
            dots.addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == current) palette.onSurfaceVariant else palette.outlineVariant)
                }
            }, LinearLayout.LayoutParams(dp(6), dp(6)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
    }

    /** Saves the bar (unchanged) and the menu's order, after a drag within or into it. */
    private fun saveMenu() {
        val bar = store.toolbar().filter { it !in items }
        store.setOrder(bar + items)
        store.setPinned(bar.toSet())
    }

    /** The index in [items] of the tile under ([x], [y]) in the pages' coordinates, or -1. */
    private fun indexAt(x: Float, y: Float): Int {
        val page = pages.findChildViewUnder(x, y) ?: return -1
        val pageIndex = pages.getChildAdapterPosition(page)
        if (pageIndex < 0) return -1
        val px = x - page.left
        val py = y - page.top
        val tiles = page as ViewGroup
        for (r in 0 until tiles.childCount) {
            val row = tiles.getChildAt(r) as ViewGroup
            for (c in 0 until row.childCount) {
                val tile = row.getChildAt(c)
                if (px >= row.left + tile.left && px < row.left + tile.right &&
                    py >= row.top + tile.top && py < row.top + tile.bottom) {
                    return (tile.tag as? Int) ?: -1
                }
            }
        }
        return -1
    }

    private fun onDrag(event: DragEvent): Boolean {
        val source = event.localState as? ToolbarView.DragSource ?: return false
        if (!editing) return false
        when (event.action) {
            DragEvent.ACTION_DRAG_LOCATION -> if (!source.fromBar) {
                // the dragged shortcut moves through the menu under the finger
                val target = indexAt(event.x, event.y)
                val from = items.indexOf(source.shortcut)
                if (target >= 0 && from >= 0 && target != from) {
                    items.add(target, items.removeAt(from))
                    adapter.notifyDataSetChanged()
                }
            }
            DragEvent.ACTION_DROP -> {
                if (source.fromBar) {
                    // off the bar: it lands where it was dropped
                    val target = indexAt(event.x, event.y).takeIf { it >= 0 } ?: items.size
                    items.add(target.coerceIn(0, items.size), source.shortcut)
                }
                saveMenu()
                load()
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                // dropped on the bar: it leaves the menu
                if (!source.fromBar && source.shortcut in store.toolbar()) items.remove(source.shortcut)
                load()
            }
        }
        return true
    }

    /** Shortcuts that open a panel get a › like Gboard's sub-menus. */
    private fun opensPanel(sc: Shortcut) = when (sc) {
        Shortcut.TEXT_EDIT, Shortcut.RESIZE, Shortcut.EMOJI, Shortcut.HANDWRITING,
        Shortcut.WRITTEN_CHINESE, Shortcut.SETTINGS -> true
        else -> false
    }

    private fun tile(index: Int): View {
        val sc = items[index]
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = index
            setPadding(dp(16), 0, dp(12), 0)
            background = android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(palette.surfaceContainerPressed),
                GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(palette.surfaceContainer)
                },
                null,
            )
            addView(ImageView(context).apply {
                setImageResource(sc.icon)
                imageTintList = ColorStateList.valueOf(palette.onSurface)
                scaleType = ImageView.ScaleType.CENTER
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(12) })
            addView(TextView(context).apply {
                setText(sc.label)
                setTextColor(palette.onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (opensPanel(sc)) addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_sym_chevron_right)
                imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
            }, LinearLayout.LayoutParams(dp(20), dp(20)))
            contentDescription = context.getString(sc.label)
            setOnClickListener {
                if (editing) return@setOnClickListener
                this@ShortcutGalleryView.visibility = GONE
                lycheeHost()?.lycheeRunShortcut(sc)
            }
            setOnLongClickListener { v ->
                if (!editing) return@setOnLongClickListener false
                v.startDragAndDrop(ClipData.newPlainText("shortcut", sc.id), View.DragShadowBuilder(v),
                    ToolbarView.DragSource(sc, fromBar = false), 0)
                v.alpha = 0.3f
                true
            }
        }
    }

    /** One page: up to three rows of two buttons. */
    private inner class PageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = pageCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val page = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), 0)
                layoutParams = RecyclerView.LayoutParams(parent.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT)
            }
            return object : RecyclerView.ViewHolder(page) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val page = holder.itemView as LinearLayout
            page.removeAllViews()
            val first = position * PER_PAGE
            for (r in 0 until ROWS) {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                for (c in 0 until COLUMNS) {
                    val index = first + r * COLUMNS + c
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        .apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                    row.addView(if (index < items.size) tile(index) else View(context), lp)
                }
                page.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            }
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        const val COLUMNS = 2
        const val ROWS = 3
        const val PER_PAGE = COLUMNS * ROWS
    }
}
