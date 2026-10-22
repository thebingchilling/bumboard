package app.bumboard.layout

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.utils.ResourceUtils

/**
 * Arranges the keyboard frame for [BumLayout.mode]: full width; one-handed with a side panel
 * on the free side; or a floating card at its saved position with a drag handle on top.
 * The keys themselves are already sized by KeyboardSwitcher.
 */
object LayoutApplier {

    @JvmStatic
    fun apply(inputView: View) {
        val ctx = inputView.context
        val layout = BumLayout.get(ctx)
        val frame = inputView.findViewById<View>(R.id.main_keyboard_frame) ?: return
        val left = inputView.findViewById<OneHandedSideView>(R.id.bum_one_hand_left)
        val right = inputView.findViewById<OneHandedSideView>(R.id.bum_one_hand_right)
        val handle = inputView.findViewById<View>(R.id.bum_float_handle)
        val emoji = inputView.findViewById<View>(R.id.emoji_palettes_view)
        val background = ctx.getColor(R.color.bum_m3_kb_background)
        val full = ResourceUtils.getDefaultKeyboardWidth(ctx)
        val keys = layout.keyboardWidth(full)

        left?.visibility = View.GONE
        right?.visibility = View.GONE
        handle?.visibility = View.GONE
        val lp = frame.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.BOTTOM
        lp.leftMargin = 0
        lp.bottomMargin = 0
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        frame.background = ColorDrawable(background)
        frame.elevation = 0f

        when (layout.mode) {
            BumLayout.Mode.NORMAL -> Unit
            BumLayout.Mode.ONE_HANDED -> {
                val side = if (layout.oneHandedRight) left else right
                side?.let {
                    it.visibility = View.VISIBLE
                    it.layoutParams = (it.layoutParams as LinearLayout.LayoutParams).apply { width = full - keys }
                    it.setKeysOnRight(layout.oneHandedRight)
                }
            }
            BumLayout.Mode.FLOATING -> {
                handle?.visibility = View.VISIBLE
                lp.width = keys
                lp.gravity = Gravity.BOTTOM or Gravity.START
                val dm = ctx.resources.displayMetrics
                lp.leftMargin = if (layout.floatX >= 0) layout.floatX else (full - keys) / 2
                lp.bottomMargin = if (layout.floatY >= 0) layout.floatY else (dm.density * 96).toInt()
                frame.background = GradientDrawable().apply {
                    setColor(background)
                    cornerRadius = dm.density * 20
                }
                frame.clipToOutline = true
                frame.elevation = dm.density * 8
            }
        }
        frame.layoutParams = lp
        // the emoji panel takes the keyboard's place, size and position
        emoji?.let {
            val elp = it.layoutParams as FrameLayout.LayoutParams
            elp.gravity = lp.gravity
            elp.leftMargin = lp.leftMargin
            elp.bottomMargin = lp.bottomMargin
            it.layoutParams = elp
            it.elevation = frame.elevation
            it.clipToOutline = layout.mode == BumLayout.Mode.FLOATING
            if (layout.mode == BumLayout.Mode.FLOATING) it.background = frame.background.constantState?.newDrawable()
            else it.setBackgroundColor(background)
        }
    }
}
