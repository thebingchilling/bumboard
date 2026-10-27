package app.bumboard.toolbar

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
import app.bumboard.bumHost
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * The shortcuts row in the strip while there are no suggestions.
 *
 * - Normal: the shortcuts button, then the shortcuts on the bar (by default next language and
 *   text editing).
 * - Gallery open: the shortcuts button (highlighted, it closes the gallery), the title and an
 *   edit button.
 * - Editing: the shortcuts on the bar, which take shortcuts dragged up from the gallery and can
 *   be dragged back down into it or along the bar; a tick ends editing.
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
        layoutTransition = LayoutTransition()
    }

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
                rebuild()
                // the strip hides its suggestions and mic while a title shows here
                (parent as? com.android.inputmethod.latin.suggestions.SuggestionStripView)?.bumRefresh()
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
            row.addView(button(R.drawable.ic_sym_arrow_back, R.string.bum_back, highlighted = true) { onBack() })
            row.addView(titleText(title), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            return
        }
        row.addView(button(R.drawable.ic_sym_grid_view, R.string.bum_sc_gallery, highlighted = mode != Mode.NORMAL) { onGallery() })
        when (mode) {
            Mode.NORMAL -> store.toolbar().forEach { sc ->
                row.addView(button(sc.icon, sc.label) { bumHost()?.bumRunShortcut(sc) })
            }
            Mode.GALLERY -> {
                row.addView(titleText(R.string.bum_gallery_title),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                row.addView(button(R.drawable.ic_sym_edit, R.string.bum_gallery_edit) { onEditToggle() })
            }
            Mode.TITLE -> Unit
            Mode.EDIT -> {
                store.toolbar().forEach { sc ->
                    row.addView(button(sc.icon, sc.label) {}.apply {
                        tag = sc
                        // drag it along the bar, or down into the gallery to take it off
                        setOnLongClickListener { v ->
                            v.startDragAndDrop(ClipData.newPlainText("shortcut", sc.id), DragShadowBuilder(v),
                                DragSource(sc, fromBar = true), 0)
                            v.alpha = 0.3f
                            true
                        }
                    })
                }
                row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                row.addView(button(R.drawable.ic_sym_check, R.string.bum_resize_done) { onEditToggle() })
            }
        }
    }

    private fun titleText(text: Int) = TextView(context).apply {
        setText(text)
        setTextColor(palette.onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
            if (child.tag !is Shortcut) continue
            if (x < child.left + child.width / 2f) return index
            index++
        }
        return index
    }

    private fun button(icon: Int, label: Int, highlighted: Boolean = false, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(if (highlighted) palette.onPrimaryContainer else palette.onSurfaceVariant)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(label)
        background = if (highlighted) GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(palette.primaryContainer)
        } else context.getDrawable(R.drawable.bum_m3_suggestion)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(36)).apply { gravity = Gravity.CENTER_VERTICAL }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
