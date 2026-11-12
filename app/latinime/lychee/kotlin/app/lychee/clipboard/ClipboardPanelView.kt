package app.lychee.clipboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import app.lychee.lycheeHost
import app.lychee.toolbar.ToolbarView
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * The clipboard over the keys, like Gboard's: what was copied as cards in two columns, the
 * pinned ones under "Pinned", the rest under "Recent", newest first. Each card shows what it
 * is (a link, a number, an email address, text) and how long ago it was copied; tap one to
 * paste it, hold one for Pin and Delete. The bar along the bottom says how many there are and
 * clears the unpinned ones. Empty or off, a picture, a title and a line about it (and, while
 * off, Turn on) fill the panel instead.
 */
class ClipboardPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    /** The list as shown: section titles (String) and copies. */
    private var rows: List<Any> = emptyList()
    private var items: List<ClipboardStore.Item> = emptyList()
    /** The card showing its Pin and Delete buttons, if any. */
    private var actionsFor: String? = null
    private val list = RecyclerView(context)
    private val adapter = CardAdapter()

    // the empty and off states
    private val emptyIcon = ImageView(context).apply {
        setImageResource(R.drawable.ic_sym_content_paste)
        imageTintList = ColorStateList.valueOf(palette.onPrimaryContainer)
        scaleType = ImageView.ScaleType.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.primaryContainer)
        }
    }
    private val emptyTitle = TextView(context).apply {
        setTextColor(palette.onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 600)
        gravity = Gravity.CENTER
    }
    private val emptyBody = TextView(context).apply {
        setTextColor(palette.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.15f)
    }
    private val turnOn = pill(R.string.lychee_clipboard_turn_on) {
        ClipboardStore.setEnabled(context, true)
        ClipboardStore.capture(context)
        load()
    }
    private val emptyState = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(40), dp(8), dp(40), dp(8))
        addView(emptyIcon, LinearLayout.LayoutParams(dp(64), dp(64)))
        addView(emptyTitle, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        addView(emptyBody, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        addView(turnOn, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)).apply { topMargin = dp(16) })
    }

    // the bottom bar: how many, and Clear
    private val count = TextView(context).apply {
        setTextColor(palette.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER_VERTICAL
    }
    private val clear = TextView(context).apply {
        setText(R.string.lychee_clipboard_clear)
        setTextColor(palette.primary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 600)
        gravity = Gravity.CENTER
        setPadding(dp(12), 0, dp(14), 0)
        setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.lychee_ic_clear_all, 0, 0, 0)
        compoundDrawablePadding = dp(6)
        compoundDrawableTintList = ColorStateList.valueOf(palette.primary)
        background = TypedValue().let { v ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
            context.getDrawable(v.resourceId)
        }
        setOnClickListener {
            ClipboardStore.clear(context)
            load()
        }
    }
    private val bottomBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), 0, dp(6), 0)
        addView(count, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(clear, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)))
    }

    init {
        isClickable = true
        setBackgroundColor(palette.surface)
        // two columns of cards as tall as their text (up to four lines), like Gboard's
        list.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        list.adapter = adapter
        list.clipToPadding = false
        list.setPadding(dp(8), dp(4), dp(8), dp(8))
        list.overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(FrameLayout(context).apply {
            addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(emptyState, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(View(context).apply { setBackgroundColor(palette.outlineVariant); alpha = 0.5f },
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1))
        column.addView(bottomBar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
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
        val pinned = items.filter { it.pinned }
        val recent = items.filter { !it.pinned }
        rows = buildList {
            if (pinned.isNotEmpty()) {
                add(context.getString(R.string.lychee_clipboard_pinned))
                addAll(pinned)
            }
            if (recent.isNotEmpty()) {
                if (pinned.isNotEmpty()) add(context.getString(R.string.lychee_clipboard_recent))
                addAll(recent)
            }
        }
        adapter.notifyDataSetChanged()
        emptyState.visibility = if (items.isEmpty()) VISIBLE else GONE
        list.visibility = if (items.isEmpty()) GONE else VISIBLE
        emptyTitle.setText(if (on) R.string.lychee_clipboard_empty_title else R.string.lychee_clipboard_off_title)
        emptyBody.setText(if (on) R.string.lychee_clipboard_empty else R.string.lychee_clipboard_off)
        emptyIcon.alpha = if (on) 1f else 0.6f
        turnOn.visibility = if (on) GONE else VISIBLE
        bottomBar.visibility = if (on && items.isNotEmpty()) VISIBLE else GONE
        count.text = resources.getQuantityString(R.plurals.lychee_clipboard_count, items.size, items.size)
        clear.visibility = if (recent.isNotEmpty()) VISIBLE else GONE
    }

    /** What a copy is, for its card's icon and label. */
    private fun kindOf(text: String): Pair<Int, Int> = when {
        Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE).matches(text) -> R.drawable.ic_sym_open_in_new to R.string.lychee_clipboard_link
        android.util.Patterns.EMAIL_ADDRESS.matcher(text).matches() -> R.drawable.lychee_ic_input to R.string.lychee_clipboard_email
        Regex("^[+()\\d\\s.-]{5,}$").matches(text) -> R.drawable.ic_sym_dialpad to R.string.lychee_clipboard_number
        else -> R.drawable.ic_sym_text_fields to R.string.lychee_clipboard_text
    }

    private fun card(item: ClipboardStore.Item): View {
        val box = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (item.pinned) palette.secondaryContainer else palette.surfaceContainerHigh)
            }
            foreground = TypedValue().let { v ->
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
                context.getDrawable(v.resourceId)
            }
            clipToOutline = true
        }
        val (icon, label) = kindOf(item.text)
        val onCard = if (item.pinned) palette.onSecondaryContainer else palette.onSurface
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            minimumHeight = dp(64)
        }
        // what it is and when it was copied, small, over the text
        column.addView(TextView(context).apply {
            val age = if (item.pinned) context.getString(R.string.lychee_clipboard_pinned)
                else DateUtils.getRelativeTimeSpanString(item.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE).toString()
            text = context.getString(label) + "  ·  " + age
            setTextColor(if (item.pinned) palette.onSecondaryContainer else palette.onSurfaceVariant)
            alpha = 0.85f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setCompoundDrawablesRelativeWithIntrinsicBounds(if (item.pinned) R.drawable.lychee_ic_pin else icon, 0, 0, 0)
            compoundDrawablePadding = dp(5)
            compoundDrawableTintList = ColorStateList.valueOf(if (item.pinned) palette.onSecondaryContainer else palette.primary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        column.addView(TextView(context).apply {
            text = item.text
            setTextColor(onCard)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.1f)
            setPadding(0, dp(6), 0, 0)
        })
        box.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (actionsFor == item.text) {
            // Pin (or Unpin) and Delete over the card
            box.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(palette.surfaceContainerHighest, 0xF0))
                addView(action(R.drawable.lychee_ic_pin, if (item.pinned) R.string.lychee_clipboard_unpin else R.string.lychee_clipboard_pin) {
                    ClipboardStore.setPinned(context, item.text, !item.pinned)
                    actionsFor = null
                    load()
                }, LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(action(R.drawable.lychee_ic_delete, R.string.lychee_clipboard_delete) {
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

    private fun header(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(palette.primary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 600)
        setPadding(dp(8), dp(8), dp(8), dp(2))
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

    private fun pill(label: Int, onClick: () -> Unit) = TextView(context).apply {
        setText(label)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(palette.onPrimary)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
        gravity = Gravity.CENTER
        setPadding(dp(24), 0, dp(24), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(palette.primary)
        }
        setOnClickListener { onClick() }
    }

    private inner class CardAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) = if (rows[position] is String) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(FrameLayout(context).apply {
                layoutParams = StaggeredGridLayoutManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { isFullSpan = viewType == 1 }
                if (viewType == 0) setPadding(dp(4), dp(4), dp(4), dp(4))
            }) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val cell = holder.itemView as FrameLayout
            cell.removeAllViews()
            when (val row = rows[position]) {
                is String -> cell.addView(header(row))
                is ClipboardStore.Item -> cell.addView(card(row), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
