package app.bumboard.toolbar

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
import app.bumboard.bumHost
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * Every shortcut as a grid over the keys, like Gboard's; the shortcuts button opens and closes
 * it and the bar above shows its title and an edit button (see [ToolbarView]). Tap a shortcut
 * to use it.
 *
 * Editing: the grid holds the shortcuts not on the bar. Touch and hold one to drag it: along
 * the grid to reorder, or up onto the bar to put it there; shortcuts dragged down from the bar
 * land here, taken off it.
 */
class ShortcutGalleryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    @Suppress("DEPRECATION")
    private val store = ShortcutStore(PreferenceManager.getDefaultSharedPreferences(context))
    private val palette = M3Colors.of(context)
    private val grid = RecyclerView(context)
    private val adapter = Adapter()
    private var editing = false

    init {
        isClickable = true
        setBackgroundColor(palette.surface)
        grid.layoutManager = GridLayoutManager(context, COLUMNS)
        grid.adapter = adapter
        grid.setPadding(dp(8), dp(8), dp(8), dp(8))
        grid.clipToPadding = false
        grid.setOnDragListener { _, event -> onDrag(event) }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    private fun toolbar(): ToolbarView? = rootView.findViewById(R.id.bum_toolbar)

    fun show() {
        visibility = VISIBLE
        setEditing(false)
        toolbar()?.let { bar ->
            bar.onEditToggle = { setEditing(!editing) }
            bar.onChanged = { load() }
        }
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        // the bar shows the gallery's title and edit button only while it is open
        if (visibility != VISIBLE) {
            editing = false
            toolbar()?.mode = ToolbarView.Mode.NORMAL
        }
    }

    private fun setEditing(on: Boolean) {
        editing = on
        toolbar()?.mode = if (on) ToolbarView.Mode.EDIT else ToolbarView.Mode.GALLERY
        load()
    }

    /** All shortcuts (bar ones first), or while editing only those not on the bar. */
    private fun load() {
        val bar = store.toolbar()
        val rest = store.order().filter { it !in bar }
        adapter.items = (if (editing) rest else bar + rest).toMutableList()
        adapter.notifyDataSetChanged()
    }

    /** Saves the bar (unchanged) and the grid's order, after a drag within or into the grid. */
    private fun saveGrid() {
        val bar = store.toolbar().filter { it !in adapter.items }
        store.setOrder(bar + adapter.items)
        store.setPinned(bar.toSet())
    }

    private fun onDrag(event: DragEvent): Boolean {
        val source = event.localState as? ToolbarView.DragSource ?: return false
        if (!editing) return false
        when (event.action) {
            DragEvent.ACTION_DRAG_LOCATION -> if (!source.fromBar) {
                // the dragged shortcut moves through the grid under the finger
                val target = grid.findChildViewUnder(event.x, event.y)?.let { grid.getChildAdapterPosition(it) } ?: -1
                val from = adapter.items.indexOf(source.shortcut)
                if (target >= 0 && from >= 0 && target != from) {
                    adapter.items.add(target, adapter.items.removeAt(from))
                    adapter.notifyItemMoved(from, target)
                }
            }
            DragEvent.ACTION_DROP -> {
                if (source.fromBar) {
                    // off the bar: it lands where it was dropped
                    val target = grid.findChildViewUnder(event.x, event.y)?.let { grid.getChildAdapterPosition(it) }
                        ?: adapter.items.size
                    val at = target.coerceIn(0, adapter.items.size)
                    adapter.items.add(at, source.shortcut)
                    adapter.notifyItemInserted(at)
                }
                saveGrid()
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                // dropped on the bar: it leaves the grid
                if (!source.fromBar && source.shortcut in store.toolbar()) {
                    val at = adapter.items.indexOf(source.shortcut)
                    if (at >= 0) {
                        adapter.items.removeAt(at)
                        adapter.notifyItemRemoved(at)
                    }
                }
                adapter.notifyItemRangeChanged(0, adapter.items.size)
            }
        }
        return true
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: MutableList<Shortcut> = mutableListOf()

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                background = context.getDrawable(R.drawable.bum_m3_suggestion)
                addView(ImageView(context).apply {
                    id = ICON
                    scaleType = ImageView.ScaleType.CENTER
                }, LinearLayout.LayoutParams(dp(56), dp(44)))
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
            return object : RecyclerView.ViewHolder(tile) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val sc = items[position]
            val tile = holder.itemView
            tile.alpha = 1f
            val icon = tile.findViewById<ImageView>(ICON)
            icon.setImageResource(sc.icon)
            icon.imageTintList = ColorStateList.valueOf(palette.onSecondaryContainer)
            icon.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(palette.secondaryContainer)
            }
            tile.findViewById<TextView>(LABEL).setText(sc.label)
            tile.contentDescription = context.getString(sc.label)
            tile.setOnClickListener {
                if (editing) return@setOnClickListener
                visibility = GONE
                bumHost()?.bumRunShortcut(sc)
            }
            tile.setOnLongClickListener { v ->
                if (!editing) return@setOnLongClickListener false
                v.startDragAndDrop(ClipData.newPlainText("shortcut", sc.id), View.DragShadowBuilder(v),
                    ToolbarView.DragSource(sc, fromBar = false), 0)
                v.alpha = 0.3f
                true
            }
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        const val COLUMNS = 4
        val ICON = android.R.id.icon
        val LABEL = android.R.id.text1
    }
}
