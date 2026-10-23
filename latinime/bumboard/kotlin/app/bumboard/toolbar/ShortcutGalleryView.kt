package app.bumboard.toolbar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import app.bumboard.bumHost
import app.bumboard.layout.BumPanels
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * Every shortcut as a grid over the keys, like Gboard's. Tap one to use it.
 *
 * In edit mode the grid has two parts, "On the bar" and "More shortcuts". Touch and hold a
 * shortcut and drag it across to put it on the bar or take it off, or within the bar to
 * change the bar's order; tapping one moves it to the other part.
 */
class ShortcutGalleryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    @Suppress("DEPRECATION")
    private val store = ShortcutStore(PreferenceManager.getDefaultSharedPreferences(context))
    private val palette = M3Colors.of(context)
    private val grid = RecyclerView(context)
    private val adapter = Adapter()
    private val layoutManager = GridLayoutManager(context, COLUMNS)
    private val title = TextView(context)
    private val editButton = ImageView(context)
    private var editing = false
    private lateinit var dragger: ItemTouchHelper

    /** A grid entry: a section title (edit mode only) or a shortcut. */
    private sealed class Item {
        class Title(val text: Int) : Item()
        class Tile(val shortcut: Shortcut) : Item()
    }

    init {
        orientation = VERTICAL
        isClickable = true
        setBackgroundColor(palette.surface)
        addView(header(), LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (adapter.items.getOrNull(position) is Item.Title) COLUMNS else 1
        }
        grid.layoutManager = layoutManager
        grid.adapter = adapter
        grid.setPadding(dp(8), 0, dp(8), dp(8))
        grid.clipToPadding = false
        dragger = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled() = false // started from the tile's long-press

            override fun getMovementFlags(rv: RecyclerView, holder: RecyclerView.ViewHolder): Int {
                if (adapter.items.getOrNull(holder.bindingAdapterPosition) !is Item.Tile) return 0
                return makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0,
                )
            }

            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val a = from.bindingAdapterPosition
                val b = to.bindingAdapterPosition
                // the "On the bar" title always stays first
                if (b <= 0) return false
                val list = adapter.items.toMutableList()
                list.add(b, list.removeAt(a))
                adapter.items = list
                adapter.notifyItemMoved(a, b)
                return true
            }

            override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(rv, holder)
                save()
                // the tile may have crossed into the other part: redraw just its pinned look
                // (refreshing the whole grid during the drop animation can leave tiles hidden)
                val at = holder.bindingAdapterPosition
                if (at >= 0) adapter.onBindViewHolder(holder, at)
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}
        })
        dragger.attachToRecyclerView(grid)
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(2))
            addView(BumPanels.backButton(context) { visibility = GONE }, LayoutParams(dp(48), dp(48)))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(50)))
        visibility = GONE
    }

    fun show() {
        editing = false
        grid.adapter = adapter
        setEditing(false)
        visibility = VISIBLE
    }

    private fun setEditing(on: Boolean) {
        editing = on
        title.setText(if (on) R.string.bum_gallery_edit_title else R.string.bum_gallery_title)
        editButton.setImageResource(if (on) R.drawable.ic_sym_check else R.drawable.ic_sym_edit)
        editButton.contentDescription = context.getString(if (on) R.string.bum_resize_done else R.string.bum_gallery_edit)
        load()
    }

    /** Bar shortcuts first (in bar order), then the rest; titles between them while editing. */
    private fun load() {
        val pinned = store.toolbar()
        val rest = store.order().filter { it !in pinned }
        adapter.items = if (editing) {
            listOf(Item.Title(R.string.bum_gallery_on_bar)) + pinned.map { Item.Tile(it) } +
                Item.Title(R.string.bum_gallery_more) + rest.map { Item.Tile(it) }
        } else {
            (pinned + rest).map { Item.Tile(it) }
        }
        adapter.notifyDataSetChanged()
    }

    /** Saves the edit-mode grid: what is under "On the bar" is pinned, in that order. */
    private fun save() {
        val moreAt = adapter.items.indexOfLast { it is Item.Title }
        val shortcuts = adapter.items.mapIndexedNotNull { i, item -> (item as? Item.Tile)?.let { i to it.shortcut } }
        val pinned = shortcuts.filter { it.first < moreAt }.map { it.second }
        store.setOrder(shortcuts.map { it.second })
        store.setPinned(pinned.toSet())
    }

    /** Edit mode, tap: the shortcut moves to the end of the bar, or off it to the top of "More". */
    private fun toggle(position: Int) {
        val list = adapter.items.toMutableList()
        val item = list.removeAt(position)
        val moreAt = list.indexOfLast { it is Item.Title }
        val onBar = position < moreAt + 1
        list.add(if (onBar) moreAt + 1 else moreAt, item)
        adapter.items = list
        save()
        adapter.notifyDataSetChanged()
    }

    private fun header() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(4), 0)
        title.apply {
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(8), 0, 0, 0)
        }
        addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        editButton.apply {
            imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
            scaleType = ImageView.ScaleType.CENTER
            background = context.getDrawable(R.drawable.bum_m3_suggestion)
            setOnClickListener { setEditing(!editing) }
        }
        addView(editButton, LayoutParams(dp(48), dp(48)))
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<Item> = emptyList()

        override fun getItemCount() = items.size
        override fun getItemViewType(position: Int) = if (items[position] is Item.Title) TYPE_TITLE else TYPE_TILE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = if (viewType == TYPE_TITLE) TextView(context).apply {
                setTextColor(palette.onSurfaceVariant)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(8), dp(12), dp(8), dp(4))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            } else LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                background = context.getDrawable(R.drawable.bum_m3_suggestion)
                addView(FrameLayout(context).apply {
                    addView(ImageView(context).apply {
                        id = ICON
                        scaleType = ImageView.ScaleType.CENTER
                    }, FrameLayout.LayoutParams(dp(56), dp(44), Gravity.CENTER))
                }, LayoutParams(dp(60), dp(48)))
                addView(TextView(context).apply {
                    id = LABEL
                    setTextColor(palette.onSurface)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                })
            }
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is Item.Title -> (holder.itemView as TextView).setText(item.text)
                is Item.Tile -> bindTile(holder, item.shortcut, position)
            }
        }

        private fun bindTile(holder: RecyclerView.ViewHolder, sc: Shortcut, position: Int) {
            val tile = holder.itemView
            val onBar = editing && position < items.indexOfLast { it is Item.Title }
            val icon = tile.findViewById<ImageView>(ICON)
            icon.setImageResource(sc.icon)
            icon.imageTintList = ColorStateList.valueOf(if (onBar) palette.onPrimaryContainer else palette.onSecondaryContainer)
            icon.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (onBar) palette.primaryContainer else palette.secondaryContainer)
            }
            tile.findViewById<TextView>(LABEL).setText(sc.label)
            tile.contentDescription = context.getString(sc.label)
            tile.setOnClickListener {
                val at = holder.bindingAdapterPosition
                if (at < 0) return@setOnClickListener
                if (editing) toggle(at) else {
                    visibility = GONE
                    bumHost()?.bumRunShortcut(sc)
                }
            }
            tile.setOnLongClickListener {
                if (!editing) return@setOnLongClickListener false
                dragger.startDrag(holder)
                true
            }
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        const val COLUMNS = 4
        const val TYPE_TITLE = 0
        const val TYPE_TILE = 1
        val ICON = android.R.id.icon
        val LABEL = android.R.id.text1
    }
}
