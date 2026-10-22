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
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * Every shortcut as a grid over the keys, like Gboard's. Tap one to use it. In edit mode, tap
 * a shortcut to show or hide it on the toolbar and drag shortcuts to change the order (which
 * is also the toolbar's order).
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
    private val title = TextView(context)
    private val hint = TextView(context)
    private val editButton = TextView(context)
    private var editing = false

    init {
        orientation = VERTICAL
        isClickable = true
        setBackgroundColor(palette.surface)
        addView(header(), LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        hint.apply {
            setText(R.string.bum_gallery_edit_hint)
            setTextColor(palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(16), 0, dp(16), dp(4))
            visibility = GONE
        }
        addView(hint)
        grid.layoutManager = GridLayoutManager(context, 4)
        grid.adapter = adapter
        grid.setPadding(dp(8), 0, dp(8), dp(8))
        grid.clipToPadding = false
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0,
        ) {
            override fun isLongPressDragEnabled() = editing
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val a = from.bindingAdapterPosition
                val b = to.bindingAdapterPosition
                val list = adapter.items.toMutableList()
                list.add(b, list.removeAt(a))
                adapter.items = list
                adapter.notifyItemMoved(a, b)
                return true
            }
            override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(rv, holder)
                store.setOrder(adapter.items)
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}
        }).attachToRecyclerView(grid)
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        visibility = GONE
    }

    fun show() {
        setEditing(false)
        adapter.items = store.order()
        adapter.notifyDataSetChanged()
        visibility = VISIBLE
    }

    private fun setEditing(on: Boolean) {
        editing = on
        title.setText(if (on) R.string.bum_gallery_edit_title else R.string.bum_gallery_title)
        editButton.setText(if (on) R.string.bum_resize_done else R.string.bum_gallery_edit)
        hint.visibility = if (on) VISIBLE else GONE
        adapter.notifyDataSetChanged()
    }

    private fun header() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(4), 0)
        title.apply {
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        editButton.apply {
            setTextColor(palette.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = context.getDrawable(R.drawable.bum_m3_suggestion)
            setOnClickListener { setEditing(!editing) }
        }
        addView(editButton)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sym_close)
            imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = context.getString(R.string.bum_gallery_close)
            background = context.getDrawable(R.drawable.bum_m3_suggestion)
            setOnClickListener { visibility = GONE }
        }, LayoutParams(dp(48), dp(48)))
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<Shortcut> = emptyList()

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val tile = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                background = context.getDrawable(R.drawable.bum_m3_suggestion)
                val iconBox = FrameLayout(context)
                iconBox.addView(ImageView(context).apply {
                    id = ICON
                    scaleType = ImageView.ScaleType.CENTER
                }, FrameLayout.LayoutParams(dp(56), dp(44), Gravity.CENTER))
                iconBox.addView(ImageView(context).apply {
                    id = BADGE
                    scaleType = ImageView.ScaleType.CENTER
                }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END))
                addView(iconBox, LayoutParams(dp(60), dp(48)))
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
            val pinned = sc in store.pinned()
            val icon = tile.findViewById<ImageView>(ICON)
            icon.setImageResource(sc.icon)
            val highlighted = editing && pinned
            icon.imageTintList = ColorStateList.valueOf(
                if (highlighted) palette.onPrimaryContainer else palette.onSecondaryContainer)
            icon.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (highlighted) palette.primaryContainer else palette.secondaryContainer)
            }
            val badge = tile.findViewById<ImageView>(BADGE)
            badge.visibility = if (editing) VISIBLE else GONE
            badge.setImageResource(if (pinned) R.drawable.ic_sym_visibility else R.drawable.ic_sym_visibility_off)
            badge.imageTintList = ColorStateList.valueOf(if (pinned) palette.primary else palette.onSurfaceVariant)
            badge.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(palette.surface) }
            tile.findViewById<TextView>(LABEL).setText(sc.label)
            tile.contentDescription = context.getString(sc.label)
            tile.setOnClickListener {
                if (editing) {
                    store.setPinned(sc, !pinned)
                    notifyItemChanged(holder.bindingAdapterPosition)
                } else {
                    visibility = GONE
                    bumHost()?.bumRunShortcut(sc)
                }
            }
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        val ICON = android.R.id.icon
        val BADGE = android.R.id.icon1
        val LABEL = android.R.id.text1
    }
}
