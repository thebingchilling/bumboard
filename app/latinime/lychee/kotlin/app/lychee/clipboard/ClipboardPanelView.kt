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
 * paste it, hold one for Pin and Delete. The title bar has, at its right, ✎ and the on/off
 * switch, as Gboard's: ✎ is edit mode, where a tap ticks copies and Add, Pin and Delete take
 * the switch's place in the title bar (Pin and Delete for the ticked ones; Add puts what is in
 * the text field in as a new copy); ✓ leaves it.
 * Out of edit mode the bottom bar says how many there are and clears the unpinned ones. Empty or off, a picture, a title and a line about it (and, while
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
    /** Edit mode (✎): taps tick copies instead of pasting them. */
    private var editing = false
    private val ticked = LinkedHashSet<String>()
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

    /**
     * The clipboard on or off, at the right of the panel's title: off forgets every copy kept
     * (nothing copied is kept while it is off).
     */
    private val onOff = app.lychee.ui.M3Switch(context).apply {
        contentDescription = context.getString(R.string.lychee_clipboard_switch)
        onCheckedChange = { on ->
            if (on != ClipboardStore.enabled(context)) {
                ClipboardStore.setEnabled(context, on)
                if (on) ClipboardStore.capture(context)
                load()
            }
        }
    }

    /** ✎ (or ✓ in edit mode), left of the switch. */
    private val editButton = ImageView(context).apply {
        setImageResource(R.drawable.ic_sym_edit)
        imageTintList = ColorStateList.valueOf(palette.onSurface)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.lychee_clipboard_edit)
        background = TypedValue().let { v ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, v, true)
            context.getDrawable(v.resourceId)
        }
        app.lychee.layout.LycheeMotion.springy(this)
        setOnClickListener { setEditing(!editing) }
    }

    /** Screenshots only: edit mode with the first copies ticked. */
    internal fun editForScreens(tick: Int) {
        setEditing(true)
        items.take(tick).forEach { ticked += it.text }
        load()
    }

    private fun setEditing(on: Boolean) {
        editing = on && ClipboardStore.enabled(context)
        ticked.clear()
        actionsFor = null
        editButton.setImageResource(if (editing) R.drawable.ic_sym_check else R.drawable.ic_sym_edit)
        editButton.contentDescription = context.getString(if (editing) R.string.lychee_clipboard_done else R.string.lychee_clipboard_edit)
        load()
    }

    // edit mode's actions in the title bar: Add, Pin and Delete (the last two for the ticked copies)
    private val addAction = barAction(R.drawable.ic_sym_add, R.string.lychee_clipboard_add) {
        // what is selected in the text field, or else all of it, as a new copy
        val ic = lycheeHost()?.lycheeInputConnection()
        val text = runCatching { ic?.getSelectedText(0)?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching { ic?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.text?.toString() }.getOrNull()
        if (!text.isNullOrBlank()) {
            ClipboardStore.add(context, text.take(5000))
            load()
        } else android.widget.Toast.makeText(context, R.string.lychee_clipboard_add_nothing, android.widget.Toast.LENGTH_SHORT).show()
    }
    private val pinAction = barAction(R.drawable.lychee_ic_pin, R.string.lychee_clipboard_pin) {
        // all ticked pinned, unless they all were already (then unpinned)
        val all = items.filter { it.text in ticked }
        val pin = !all.all { it.pinned }
        all.forEach { ClipboardStore.setPinned(context, it.text, pin) }
        ticked.clear()
        load()
    }
    private val deleteAction = barAction(R.drawable.lychee_ic_delete, R.string.lychee_clipboard_delete) {
        ticked.toList().forEach { ClipboardStore.delete(context, it) }
        ticked.clear()
        load()
    }
    /**
     * The title bar's right: ✎ and the switch; in edit mode Add, Pin and Delete in the switch's
     * place, then ✓.
     */
    private val titleEnd = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (a in listOf(addAction, pinAction, deleteAction)) addView(a, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(editButton, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(4) })
        // clear of the bar's right edge, as the other buttons are
        addView(onOff, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
    }

    /** One of edit mode's actions: an icon button like ✎ beside it, named for TalkBack. */
    private fun barAction(icon: Int, label: Int, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(palette.onSurface)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(label)
        tooltipText = contentDescription
        visibility = GONE
        background = TypedValue().let { v ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, v, true)
            context.getDrawable(v.resourceId)
        }
        app.lychee.layout.LycheeMotion.springy(this)
        setOnClickListener { onClick() }
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
    /** The bottom bar: how many, and Clear (not in edit mode, whose actions are in the title bar). */
    private val bottom = FrameLayout(context).apply {
        addView(bottomBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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
        list.setPadding(dp(8), dp(2), dp(8), dp(16))
        column.addView(FrameLayout(context).apply {
            addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(emptyState, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            // no hard lines: the cards fade out softly under the bar above and into Clear all below
            addView(fade(GradientDrawable.Orientation.TOP_BOTTOM), LayoutParams(LayoutParams.MATCH_PARENT, dp(12), Gravity.TOP))
            addView(fade(GradientDrawable.Orientation.BOTTOM_TOP), LayoutParams(LayoutParams.MATCH_PARENT, dp(16), Gravity.BOTTOM))
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(bottom, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    private fun toolbar(): ToolbarView? = rootView.findViewById(R.id.lychee_toolbar)

    /** The panel's colour fading to clear, from the [from] edge: list content softly disappears under it. */
    private fun fade(from: GradientDrawable.Orientation) = View(context).apply {
        background = GradientDrawable(from, intArrayOf(palette.surface, palette.surface and 0x00FFFFFF))
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun show() {
        actionsFor = null
        editing = false
        ticked.clear()
        editButton.setImageResource(R.drawable.ic_sym_edit)
        ClipboardStore.capture(context)
        load()
        // the bottom bar a key row of the keyboard's height, where its bottom row is
        val row = app.lychee.layout.LycheePanels.keyRow(this)
        (bottom.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.height = row.height
            lp.bottomMargin = row.bottomPadding
            bottom.layoutParams = lp
        }
        visibility = VISIBLE
        toolbar()?.showTitle(R.string.lychee_sc_clipboard, end = titleEnd) { visibility = GONE }
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
        if (onOff.isChecked != on) onOff.isChecked = on
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
        if (!on) editing = false
        editButton.visibility = if (on) VISIBLE else GONE
        bottomBar.visibility = if (on && items.isNotEmpty() && !editing) VISIBLE else GONE
        bottom.visibility = if (editing) GONE else VISIBLE
        // edit mode: Add, Pin and Delete where the switch was
        onOff.visibility = if (editing) GONE else VISIBLE
        for (a in listOf(addAction, pinAction, deleteAction)) a.visibility = if (editing) VISIBLE else GONE
        // Pin and Delete act on ticked copies: dimmed until one is ticked
        for (a in listOf(pinAction, deleteAction)) {
            a.isEnabled = ticked.isNotEmpty()
            a.alpha = if (ticked.isNotEmpty()) 1f else 0.38f
        }
        if (on && editing && items.isEmpty()) emptyState.visibility = VISIBLE
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
            app.lychee.layout.LycheeMotion.springy(this)
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
            setPadding(dp(12), dp(8), dp(12), dp(8))
            minimumHeight = dp(64)
        }
        // what it is and when it was copied, small, over the text
        column.addView(TextView(context).apply {
            val age = if (item.pinned) context.getString(R.string.lychee_clipboard_pinned)
                // under a minute: "Just now", never "0 min. ago"
                else if (System.currentTimeMillis() - item.time < DateUtils.MINUTE_IN_MILLIS) context.getString(R.string.lychee_clipboard_just_now)
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
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.1f)
            setPadding(0, dp(3), 0, 0)
        })
        box.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (actionsFor == item.text) {
            // Pin (or Unpin) and Delete over the card
            box.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(palette.surfaceContainerHigh, 0xF4))
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
        if (editing) {
            val on = item.text in ticked
            box.addView(ImageView(context).apply {
                setImageResource(if (on) R.drawable.ic_sym_check_circle else R.drawable.ic_sym_radio_button_unchecked)
                imageTintList = ColorStateList.valueOf(if (on) palette.primary else palette.onSurfaceVariant)
            }, LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(8), 0) })
            if (on) box.foreground = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), palette.primary)
            }
        }
        box.setOnClickListener {
            if (editing) {
                if (!ticked.remove(item.text)) ticked += item.text
                load()
                return@setOnClickListener
            }
            if (actionsFor != null) {
                actionsFor = null
                adapter.notifyDataSetChanged()
            } else {
                lycheeHost()?.lycheeType(item.text)
            }
        }
        box.setOnLongClickListener {
            if (editing) return@setOnLongClickListener false
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
        setPadding(dp(8), dp(4), dp(8), 0)
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
