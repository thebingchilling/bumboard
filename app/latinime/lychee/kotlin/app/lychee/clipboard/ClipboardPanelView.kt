package app.lychee.clipboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lychee.layout.LycheePanels
import app.lychee.lycheeHost
import app.lychee.toolbar.ToolbarView
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * The clipboard over the keys, like Gboard's: recent copies as cards, newest first (pinned
 * ones first of all); tap one to paste it, hold one for Pin and Delete. Clear (bottom left)
 * removes the unpinned ones. While the clipboard is off, a note and a button to turn it on.
 */
class ClipboardPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    private var items: List<ClipboardStore.Item> = emptyList()
    /** The card showing its Pin and Delete buttons, if any. */
    private var actionsFor: String? = null
    private val list = RecyclerView(context)
    private val adapter = CardAdapter()
    private val empty = TextView(context).apply {
        setTextColor(palette.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(dp(32), 0, dp(32), 0)
    }
    private val turnOn = pill(R.string.lychee_clipboard_turn_on, filled = true) {
        ClipboardStore.setEnabled(context, true)
        ClipboardStore.capture(context)
        load()
    }
    /** Clear: an icon button, bottom left, removing the unpinned copies. */
    private val clear = ImageView(context).apply {
        setImageResource(R.drawable.lychee_ic_clear_all)
        imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.lychee_clipboard_clear)
        background = TypedValue().let { v ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, v, true)
            context.getDrawable(v.resourceId)
        }
        setOnClickListener {
            ClipboardStore.clear(context)
            load()
        }
    }

    init {
        isClickable = true
        setBackgroundColor(palette.surface)
        // two columns of cards as tall as their text (up to four lines), like Gboard's
        list.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        list.adapter = adapter
        list.clipToPadding = false
        list.setPadding(dp(8), dp(6), dp(8), dp(6))
        list.overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(FrameLayout(context).apply {
            addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(empty)
                addView(turnOn, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)).apply { topMargin = dp(16) })
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(FrameLayout(context).apply {
            addView(clear, LayoutParams(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL)
                .apply { leftMargin = dp(6) })
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    private fun toolbar(): ToolbarView? = rootView.findViewById(R.id.lychee_toolbar)

    fun show() {
        actionsFor = null
        ClipboardStore.capture(context)
        load()
        visibility = VISIBLE
        toolbar()?.showTitle(R.string.lychee_sc_clipboard) { visibility = GONE }
    }

    override fun setVisibility(visibility: Int) {
        val wasVisible = this.visibility == VISIBLE
        super.setVisibility(visibility)
        if (wasVisible && visibility != VISIBLE) {
            toolbar()?.let { if (it.mode == ToolbarView.Mode.TITLE) it.mode = ToolbarView.Mode.NORMAL }
        }
    }

    private fun load() {
        val on = ClipboardStore.enabled(context)
        items = if (on) ClipboardStore.items(context) else emptyList()
        adapter.notifyDataSetChanged()
        empty.setText(if (on) R.string.lychee_clipboard_empty else R.string.lychee_clipboard_off)
        (empty.parent as View).visibility = if (items.isEmpty()) VISIBLE else GONE
        turnOn.visibility = if (on) GONE else VISIBLE
        clear.visibility = if (items.any { !it.pinned }) VISIBLE else INVISIBLE
    }

    private fun card(item: ClipboardStore.Item): View {
        val box = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(palette.surfaceContainerHigh)
            }
            foreground = TypedValue().let { v ->
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
                context.getDrawable(v.resourceId)
            }
        }
        box.addView(TextView(context).apply {
            text = item.text
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            // room for two lines and the Pin and Delete buttons, at least
            minHeight = dp(60)
            setLineSpacing(0f, 1.1f)
            setPadding(dp(14), dp(12), dp(if (item.pinned) 30 else 14), dp(12))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (item.pinned) box.addView(ImageView(context).apply {
            setImageResource(R.drawable.lychee_ic_pin)
            imageTintList = ColorStateList.valueOf(palette.primary)
        }, LayoutParams(dp(16), dp(16), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(8), 0) })
        if (actionsFor == item.text) {
            // Pin (or Unpin) and Delete over the card
            box.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(palette.surfaceContainerHigh, 0xEE))
                addView(action(R.drawable.lychee_ic_pin, if (item.pinned) R.string.lychee_clipboard_unpin else R.string.lychee_clipboard_pin) {
                    ClipboardStore.setPinned(context, item.text, !item.pinned)
                    actionsFor = null
                    load()
                }, LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(action(R.drawable.ic_sym_close, R.string.lychee_clipboard_delete) {
                    ClipboardStore.delete(context, item.text)
                    actionsFor = null
                    load()
                }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(16) })
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        box.setOnClickListener {
            if (actionsFor != null) {
                actionsFor = null
                adapter.notifyDataSetChanged()
            } else {
                lycheeHost()?.lycheeType(item.text)
            }
        }
        box.setOnLongClickListener {
            actionsFor = item.text
            adapter.notifyDataSetChanged()
            true
        }
        return box
    }

    private fun action(icon: Int, label: Int, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(palette.onPrimaryContainer)
        scaleType = ImageView.ScaleType.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.primaryContainer)
        }
        contentDescription = context.getString(label)
        setOnClickListener { onClick() }
    }

    private fun pill(label: Int, filled: Boolean, onClick: () -> Unit) = TextView(context).apply {
        setText(label)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(if (filled) palette.onPrimary else palette.primary)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
        gravity = Gravity.CENTER
        setPadding(dp(20), 0, dp(20), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            if (filled) setColor(palette.primary) else setStroke(dp(1), palette.outline)
        }
        setOnClickListener { onClick() }
    }

    private inner class CardAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val cell = holder.itemView as FrameLayout
            cell.removeAllViews()
            cell.addView(card(items[position]), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
