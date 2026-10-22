package app.bumboard

import android.os.Build
import android.view.View
import android.view.WindowInsets
import com.android.inputmethod.latin.R

/**
 * Keeps the keys above the navigation bar. From Android 15 an input method that targets
 * SDK 35+ is drawn edge to edge, so the keyboard frame and the emoji palette get bottom
 * padding equal to the navigation bar, filled with the keyboard's own background.
 */
object KeyboardInsets {

    @JvmStatic
    fun apply(inputView: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val frame = inputView.findViewById<View>(R.id.main_keyboard_frame) ?: return
        val keyboard = inputView.findViewById<View>(R.id.keyboard_view)
        val emoji = inputView.findViewById<View>(R.id.emoji_palettes_view)
        val emojiBottom = emoji?.paddingBottom ?: 0
        inputView.setOnApplyWindowInsetsListener { _, insets ->
            val bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            if (frame.paddingBottom != bottom) {
                frame.setPadding(frame.paddingLeft, frame.paddingTop, frame.paddingRight, bottom)
                if (frame.background == null) {
                    frame.background = keyboard?.background?.constantState?.newDrawable(frame.resources)
                }
            }
            emoji?.let {
                it.setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, emojiBottom + bottom)
            }
            insets
        }
        inputView.requestApplyInsets()
    }

    /** The extra height below the keys, for LatinIME's insets calculation. */
    @JvmStatic
    fun bottomPadding(inputView: View): Int =
        inputView.findViewById<View>(R.id.main_keyboard_frame)?.paddingBottom ?: 0
}
