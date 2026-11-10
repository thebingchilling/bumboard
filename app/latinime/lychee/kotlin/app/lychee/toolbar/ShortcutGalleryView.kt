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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lychee.lycheeHost
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * Every shortcut over the keys, like Gboard's menu: a grid of icons with their names under
 * them that wraps to as many columns as fit (fewer in one-handed mode) and scrolls up and
 * down, Settings in the left corner and a pencil in the right one. The menu button on the bar (now ✕) closes it. Tap a shortcut to use it.
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
    private val grid = GridLayoutManager(context, COLUMNS)
    private val adapter = TileAdapter()
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
    /** Settings, in the corner opposite the pencil. */
    private val settings = ImageView(context).apply {
        setImageResource(R.drawable.ic_sym_settings)
        imageTintList = ColorStateList.valueOf(palette.onSurface)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.lychee_sc_settings)
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), palette.outline)
        }
        setOnClickListener {
            this@ShortcutGalleryView.visibility = GONE
            lycheeHost()?.lycheeRunShortcut(Shortcut.SETTINGS)
        }
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
        pages.layoutManager = grid
        pages.adapter = adapter
        pages.overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        pages.isVerticalScrollBarEnabled = true
        pages.clipToPadding = false
        pages.setPadding(dp(8), dp(6), dp(8), dp(6))
        // as many columns as fit (tiles about 80dp wide): fewer in one-handed mode, so the tiles
        // wrap onto more rows rather than squeezing or running off the side
        pages.addOnLayoutChangeListener { v, l, _, r, _, ol, _, or_, _ ->
            if (r - l != or_ - ol) {
                val width = (r - l - v.paddingLeft - v.paddingRight).coerceAtLeast(1)
                val columns = (width / dp(80)).coerceIn(3, 6)
                if (grid.spanCount != columns) v.post { grid.spanCount = columns }
            }
        }
        pages.setOnDragListener { _, event -> onDrag(event) }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(pages, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(FrameLayout(context).apply {
                addView(settings, LayoutParams(dp(64), dp(36), Gravity.START or Gravity.CENTER_VERTICAL)
                    .apply { leftMargin = dp(12) })
                addView(hint, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START)
                    .apply { leftMargin = dp(16) })
                addView(pencil, LayoutParams(dp(64), dp(36), Gravity.END or Gravity.CENTER_VERTICAL)
                    .apply { rightMargin = dp(12) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    private fun toolbar(): ToolbarView? = rootView.findViewById(R.id.lychee_toolbar)

    fun show() {
        visibility = VISIBLE
        // the Unicode search's names, read now so its first search is instant
        app.lychee.emoji.UnicodeNames.warmUp(context)
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
        // editing: the hint along the bottom, the pencil hidden, the menu outlined
        hint.visibility = if (on) VISIBLE else GONE
        pencil.visibility = if (on) GONE else VISIBLE
        settings.visibility = if (on) GONE else VISIBLE
        foreground = if (on) GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(2), palette.outline)
        } else null
        load()
    }

    /** The shortcuts not already on the bar (those are one tap away there). */
    private fun load() {
        val bar = store.toolbar()
        val engine = lycheeHost()?.lycheeEngine()
        // Settings has its own corner button
        // hidden shortcuts only show (faded) while editing, to bring back
        val hidden = store.hidden()
        items = store.order().filter {
            it !in bar && it != Shortcut.SETTINGS && Shortcuts.available(it, engine) &&
                (editing || it !in hidden)
        }.toMutableList()
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) VISIBLE else GONE
    }

    /** Saves the bar (unchanged) and the menu's order, after a drag within or into it. */
    private fun saveMenu() {
        val bar = store.toolbar().filter { it !in items }
        store.setOrder(bar + items)
        store.setPinned(bar.toSet())
    }

    /** The index in [items] of the tile under ([x], [y]) in the pages' coordinates, or -1. */
    private fun indexAt(x: Float, y: Float): Int {
        val tile = pages.findChildViewUnder(x, y) ?: return -1
        return pages.getChildAdapterPosition(tile).takeIf { it in items.indices } ?: -1
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

    /**
     * One shortcut: a rounded square tile drawn like the keys (raised with a shadow, or flat,
     * as the key style), its icon over its name. While editing, a corner button hides it from
     * the menu or brings it back.
     */
    private fun tile(index: Int): View {
        val sc = items[index]
        val isHidden = sc in store.hidden()
        val face = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = app.lychee.layout.LycheePanels.letterKey(context)
            alpha = if (isHidden) 0.4f else 1f
            addView(ImageView(context).apply {
                setImageResource(sc.icon)
                imageTintList = ColorStateList.valueOf(palette.onSurface)
                scaleType = ImageView.ScaleType.CENTER
            }, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(TextView(context).apply {
                setText(sc.label)
                setTextColor(palette.onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = app.lychee.fonts.LycheeFonts.ui(context, 400)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(4), dp(4), dp(4), 0)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        return FrameLayout(context).apply {
            tag = index
            clipChildren = false
            // the face inset a little, so the corner button sits on its corner and inside the tile
            addView(face, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                .apply { setMargins(dp(4), dp(6), dp(4), dp(2)) })
            if (editing) {
                addView(ImageView(context).apply {
                    setImageResource(if (isHidden) R.drawable.ic_sym_visibility_off else R.drawable.ic_sym_visibility)
                    imageTintList = ColorStateList.valueOf(palette.onPrimaryContainer)
                    scaleType = ImageView.ScaleType.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(palette.primaryContainer)
                    }
                    contentDescription = context.getString(if (isHidden) R.string.lychee_gallery_show else R.string.lychee_gallery_hide)
                    setOnClickListener {
                        store.setHidden(sc, !isHidden)
                        load()
                    }
                }, LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END))
            }
            contentDescription = context.getString(sc.label)
            face.setOnClickListener {
                if (editing) return@setOnClickListener
                this@ShortcutGalleryView.visibility = GONE
                lycheeHost()?.lycheeRunShortcut(sc)
            }
            face.setOnLongClickListener { v ->
                if (!editing) return@setOnLongClickListener false
                v.startDragAndDrop(ClipData.newPlainText("shortcut", sc.id), View.DragShadowBuilder(v),
                    ToolbarView.DragSource(sc, fromBar = false), 0)
                v.alpha = 0.3f
                true
            }
        }
    }

    /** The tiles, wrapping onto as many rows as they need; the list scrolls up and down. */
    private inner class TileAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(TILE_HEIGHT))
                setPadding(dp(2), 0, dp(2), dp(4))
                clipToPadding = false
                clipChildren = false
            }) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val cell = holder.itemView as FrameLayout
            cell.removeAllViews()
            cell.addView(tile(position), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        /** columns before the first layout; then as many as fit */
        const val COLUMNS = 4
        /** a tile's height (dp), with its gap */
        const val TILE_HEIGHT = 76
    }
}
