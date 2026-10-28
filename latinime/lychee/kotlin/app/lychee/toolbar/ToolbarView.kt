package app.lychee.toolbar

import android.animation.LayoutTransition
import android.content.ClipData
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.lychee.lycheeHost
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * The shortcuts row in the strip while there are no suggestions.
 *
 * - Normal: the menu button, then the shortcuts on the bar spread across it like Gboard's (by
 *   default next language, text editing and undo).
 * - Menu open: the menu button turns into ✕ in a circle (it closes the menu); the shortcuts stay.
 * - Editing: ✓ in a circle ends it; the shortcuts on the bar sit in an outlined pill, take
 *   shortcuts dragged up from the menu and can be dragged back down into it or along the bar.
 * - Title: a panel over the keys (text editing) is open; Back and the panel's name, like the
 *   gallery's title, in place of the suggestions.
 */
class ToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {

    enum class Mode { NORMAL, GALLERY, EDIT, TITLE }

    @Suppress("DEPRECATION")
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val store = ShortcutStore(prefs)
    private val palette = M3Colors.of(context)
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    /** Shortcuts slide aside while being dragged in edit mode; otherwise the bar switches at once. */
    private val editTransition = LayoutTransition()

    /** The shortcuts button: opens the gallery, or closes it. */
    var onGallery: () -> Unit = {}
    /** The edit / done button while the gallery is open. */
    var onEditToggle: () -> Unit = {}
    /** A shortcut was dropped on the bar (pinned) or moved along it. */
    var onChanged: () -> Unit = {}

    var mode = Mode.NORMAL
        set(value) {
            if (field != value) {
                field = value
                row.layoutTransition = if (value == Mode.EDIT) editTransition else null
                rebuild()
                // the strip hides its suggestions and mic while a title shows here
                (parent as? com.android.inputmethod.latin.suggestions.SuggestionStripView)?.lycheeRefresh()
            }
        }

    private var title = 0
    private var onBack: () -> Unit = {}

    /** Shows Back and [title] for an open panel; Back runs [back]. NORMAL mode ends it. */
    fun showTitle(title: Int, back: () -> Unit) {
        this.title = title
        onBack = back
        if (mode == Mode.TITLE) rebuild() else mode = Mode.TITLE
    }

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        row.setOnDragListener { _, event -> onDrag(event) }
        rebuild()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(this)
        rebuild()
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDetachedFromWindow()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
        if (key == ShortcutStore.PREF_ORDER || key == ShortcutStore.PREF_PINNED) rebuild()
    }

    private fun rebuild() {
        row.removeAllViews()
        if (mode == Mode.TITLE) {
            row.addView(button(R.drawable.ic_sym_arrow_back, R.string.lychee_back, highlighted = true) { onBack() })
            row.addView(titleText(title), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            return
        }
        when (mode) {
            Mode.NORMAL -> row.addView(button(R.drawable.ic_sym_apps_filled, R.string.lychee_sc_gallery) { onGallery() })
            Mode.GALLERY -> row.addView(button(R.drawable.ic_sym_close, R.string.lychee_gallery_close, highlighted = true) { onGallery() })
            Mode.EDIT -> row.addView(button(R.drawable.ic_sym_check, R.string.lychee_resize_done, highlighted = true) { onEditToggle() })
            Mode.TITLE -> Unit
        }
        val shortcuts = store.toolbar().filter { Shortcuts.available(it, lycheeHost()?.lycheeEngine()) }
        // packed on the left, beside the menu button; a long list scrolls
        fun shortcutParams() = LinearLayout.LayoutParams(dp(48), dp(40)).apply { gravity = Gravity.CENTER_VERTICAL }
        if (mode == Mode.EDIT) {
            // the bar's shortcuts in an outlined pill: drag them along it, or down into the menu
            val pill = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), palette.outline)
                }
            }
            shortcuts.forEach { sc ->
                pill.addView(button(sc.icon, sc.label) {}.apply {
                    tag = sc
                    setOnLongClickListener { v ->
                        v.startDragAndDrop(ClipData.newPlainText("shortcut", sc.id), DragShadowBuilder(v),
                            DragSource(sc, fromBar = true), 0)
                        v.alpha = 0.3f
                        true
                    }
                }, shortcutParams())
            }
            row.addView(pill, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(dp(4), 0, dp(4), 0)
            })
            return
        }
        shortcuts.forEach { sc ->
            val label = if (sc == Shortcut.HANDWRITING) handwritingLabel(lycheeHost()?.lycheeEngine()) else null
            val view = if (label != null) textButton(label) { lycheeHost()?.lycheeRunShortcut(sc) }
                else button(sc.icon, sc.label) { lycheeHost()?.lycheeRunShortcut(sc) }
            row.addView(view, if (label != null) LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40))
                .apply { gravity = Gravity.CENTER_VERTICAL } else shortcutParams())
        }
    }

    /** Handwriting writes the keyboard's own language, and says so: 手寫, 手写, 手書き. */
    private fun handwritingLabel(engine: String?): String? = when (engine) {
        "yue" -> "手寫"
        "cmn" -> if (prefs.getString(app.lychee.chinese.ChineseInput.PREF_SCRIPT, "traditional") == "simplified") "手写" else "手寫"
        "ja" -> "手書き"
        else -> null
    }

    private fun textButton(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(palette.onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        app.lychee.fonts.LycheeFonts.chinese(context)?.let { typeface = it }
        gravity = Gravity.CENTER
        setPadding(dp(10), 0, dp(10), 0)
        background = context.getDrawable(R.drawable.lychee_m3_suggestion)
        contentDescription = context.getString(R.string.lychee_sc_handwriting)
        setOnClickListener { onClick() }
    }

    private fun titleText(text: Int) = TextView(context).apply {
        setText(text)
        setTextColor(palette.onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, 0, 0)
    }

    /** Where a dragged shortcut came from. */
    class DragSource(val shortcut: Shortcut, val fromBar: Boolean)

    private fun onDrag(event: DragEvent): Boolean {
        if (event.localState !is DragSource || mode != Mode.EDIT) return false
        val source = event.localState as DragSource
        when (event.action) {
            DragEvent.ACTION_DROP -> {
                val bar = store.toolbar().toMutableList()
                bar.remove(source.shortcut)
                bar.add(insertIndex(event.x).coerceIn(0, bar.size), source.shortcut)
                val rest = store.order().filter { it !in bar }
                store.setOrder(bar + rest)
                store.setPinned(bar.toSet())
                onChanged()
            }
            DragEvent.ACTION_DRAG_ENDED -> rebuild()
        }
        return true
    }

    /** The bar position for a drop at [x] (row coordinates), counting only shortcut icons. */
    private fun insertIndex(x: Float): Int {
        var index = 0
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            // the shortcuts sit in the outlined pill while editing
            val group = child as? LinearLayout ?: continue
            for (j in 0 until group.childCount) {
                val button = group.getChildAt(j)
                if (button.tag !is Shortcut) continue
                if (x < group.left + button.left + button.width / 2f) return index
                index++
            }
        }
        return index
    }

    /** A bar button; [highlighted]: a light accent circle behind it (✕ and ✓, like Gboard's). */
    private fun button(icon: Int, label: Int, highlighted: Boolean = false, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(if (highlighted) palette.onPrimaryContainer else palette.onSurface)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(label)
        background = if (highlighted) android.graphics.drawable.InsetDrawable(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.primaryContainer)
        }, dp(6), dp(2), dp(6), dp(2)) else context.getDrawable(R.drawable.lychee_m3_suggestion)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(40)).apply { gravity = Gravity.CENTER_VERTICAL }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
