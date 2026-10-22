package app.bumboard.emoji

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import app.bumboard.ui.M3Colors

/**
 * Skin tones for an emoji, in a popup above it. One person: the plain emoji and five tones in
 * a row. Two people: the plain emoji, then a 5×5 grid (row = first person, column = second).
 */
class TonePicker(private val ctx: Context) {
    private var window: PopupWindow? = null

    fun dismiss() {
        window?.dismiss()
        window = null
    }

    fun show(anchor: View, emoji: Emoji, selected: String, onPick: (String) -> Unit) {
        val tones = emoji.tones ?: return
        dismiss()
        val c = M3Colors.of(ctx)
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHigh)
                cornerRadius = dp(20).toFloat()
            }
            elevation = dp(6).toFloat()
        }
        lateinit var popup: PopupWindow
        fun cell(text: String) = TextView(ctx).apply {
            this.text = text
            typeface = EmojiData.typeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            if (text == selected) {
                background = GradientDrawable().apply {
                    setColor(c.secondaryContainer)
                    cornerRadius = dp(12).toFloat()
                }
            }
            setOnClickListener {
                popup.dismiss()
                onPick(text)
            }
        }
        if (emoji.twoPeople) {
            content.addView(LinearLayout(ctx).apply { addView(cell(emoji.base)) })
            for (row in 0 until 5) {
                content.addView(LinearLayout(ctx).apply {
                    for (col in 0 until 5) addView(cell(tones[row * 5 + col]))
                })
            }
        } else {
            content.addView(LinearLayout(ctx).apply {
                addView(cell(emoji.base))
                tones.forEach { addView(cell(it)) }
            })
        }
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup = PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(6).toFloat()
            setOnDismissListener { if (window === this) window = null }
        }
        window = popup
        // centred above the emoji, kept on screen by PopupWindow
        val x = (anchor.width - content.measuredWidth) / 2
        popup.showAsDropDown(anchor, x, -(anchor.height + content.measuredHeight))
    }

    private fun dp(v: Int) = (ctx.resources.displayMetrics.density * v).toInt()
}
