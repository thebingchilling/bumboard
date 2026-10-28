package app.lychee.layout

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import app.lychee.lycheeHost
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * The free side in one-handed mode, like Gboard's: a button back to the full-width keyboard,
 * one that moves the keys to the other side and one that resizes them.
 */
class OneHandedSideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    private val expand = button(R.drawable.ic_sym_open_with, R.string.lychee_one_handed_exit)
    private val swap = button(R.drawable.ic_sym_keyboard_arrow_left, R.string.lychee_one_handed_swap)
    private val resize = button(R.drawable.ic_sym_open_in_full, R.string.lychee_sc_resize)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(expand, LayoutParams(dp(48), dp(48)))
        addView(swap, LayoutParams(dp(48), dp(48)).apply { topMargin = dp(16) })
        addView(resize, LayoutParams(dp(48), dp(48)).apply { topMargin = dp(16) })
        resize.setOnClickListener { lycheeHost()?.lycheeRunShortcut(app.lychee.toolbar.Shortcut.RESIZE) }
        expand.setOnClickListener {
            LycheeLayout.get(context).setMode(LycheeLayout.Mode.NORMAL)
            lycheeHost()?.lycheeReloadLayout()
        }
        swap.setOnClickListener {
            val layout = LycheeLayout.get(context)
            layout.edit().putBoolean(LycheeLayout.PREF_ONE_HANDED_RIGHT, !layout.oneHandedRight).apply()
            lycheeHost()?.lycheeReloadLayout()
        }
    }

    /** Points the move button towards the side the keys would move to. */
    fun setKeysOnRight(right: Boolean) {
        swap.setImageResource(if (right) R.drawable.ic_sym_keyboard_arrow_left else R.drawable.ic_sym_keyboard_arrow_right)
    }

    private fun button(icon: Int, label: Int) = ImageView(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(palette.onSecondaryContainer)
        scaleType = ImageView.ScaleType.CENTER
        background = LycheePanels.functionalKey(context)
        contentDescription = context.getString(label)
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
