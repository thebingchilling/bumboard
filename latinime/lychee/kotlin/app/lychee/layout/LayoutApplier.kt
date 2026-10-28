package app.lychee.layout

import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.utils.ResourceUtils

/**
 * Arranges the keyboard frame for [LycheeLayout.mode]: full width, or one-handed with a side
 * panel on the free side. One-handed, the whole keyboard is narrower, not only the keys: the
 * top bar (with its locked edge keys) and the emoji panel are as wide as the keys and sit
 * above them. The keys themselves are already sized by KeyboardSwitcher.
 */
object LayoutApplier {

    @JvmStatic
    fun apply(inputView: View) {
        val ctx = inputView.context
        val layout = LycheeLayout.get(ctx)
        val frame = inputView.findViewById<View>(R.id.main_keyboard_frame) ?: return
        val left = inputView.findViewById<OneHandedSideView>(R.id.lychee_one_hand_left)
        val right = inputView.findViewById<OneHandedSideView>(R.id.lychee_one_hand_right)
        val topBar = inputView.findViewById<View>(R.id.lychee_top_bar)
        val emoji = inputView.findViewById<View>(R.id.emoji_palettes_view)
        val background = ctx.getColor(R.color.lychee_m3_kb_background)
        val full = ResourceUtils.getDefaultKeyboardWidth(ctx)
        val keys = layout.keyboardWidth(full)
        val oneHanded = layout.mode == LycheeLayout.Mode.ONE_HANDED
        val keysOnRight = layout.oneHandedRight

        left?.visibility = View.GONE
        right?.visibility = View.GONE
        val lp = frame.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.BOTTOM
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        lp.leftMargin = 0
        lp.bottomMargin = 0
        frame.layoutParams = lp
        frame.background = ColorDrawable(background)
        frame.elevation = 0f
        frame.clipToOutline = false

        if (oneHanded) {
            val side = if (keysOnRight) left else right
            side?.let {
                it.visibility = View.VISIBLE
                it.layoutParams = (it.layoutParams as LinearLayout.LayoutParams).apply { width = full - keys }
                it.setKeysOnRight(keysOnRight)
            }
        }
        // the top bar over the keys only
        topBar?.let {
            val tlp = it.layoutParams as LinearLayout.LayoutParams
            tlp.width = if (oneHanded) keys else ViewGroup.LayoutParams.MATCH_PARENT
            tlp.gravity = if (oneHanded && keysOnRight) Gravity.END else Gravity.START
            it.layoutParams = tlp
        }
        // the emoji panel takes the keyboard's place; one-handed, its emoji and keys keep to
        // the keys' side (its width is the keys' plus this padding, see EmojiPanelView)
        emoji?.let {
            val free = if (oneHanded) full - keys else 0
            it.setPadding(if (oneHanded && keysOnRight) free else 0, it.paddingTop,
                if (oneHanded && !keysOnRight) free else 0, it.paddingBottom)
            it.setBackgroundColor(background)
            it.elevation = 0f
            it.clipToOutline = false
            (it.layoutParams as FrameLayout.LayoutParams).let { elp ->
                elp.gravity = Gravity.BOTTOM
                elp.leftMargin = 0
                elp.bottomMargin = 0
                it.layoutParams = elp
            }
        }
    }
}
