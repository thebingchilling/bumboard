package app.bumboard.toolbar

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import app.bumboard.bumHost
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * The shortcuts row shown in the strip while there are no suggestions: the gallery button,
 * then the pinned shortcuts (by default next language and text editing).
 */
class ToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {

    @Suppress("DEPRECATION")
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val store = ShortcutStore(prefs)
    private val palette = M3Colors.of(context)
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    /** Opens the shortcuts gallery. */
    var onGallery: () -> Unit = {}

    init {
        isHorizontalScrollBarEnabled = false
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
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
        row.addView(button(R.drawable.ic_sym_grid_view, R.string.bum_sc_gallery) { onGallery() })
        store.toolbar().forEach { sc ->
            row.addView(button(sc.icon, sc.label) { bumHost()?.bumRunShortcut(sc) })
        }
    }

    private fun button(icon: Int, label: Int, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(label)
        background = context.getDrawable(R.drawable.bum_m3_suggestion)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.MATCH_PARENT)
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
