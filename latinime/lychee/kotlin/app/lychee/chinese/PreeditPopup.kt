package app.lychee.chinese

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import app.lychee.ui.M3Colors

/**
 * What is being typed (pinyin, Jyutping, romaji ...) in a small chip just above the candidate
 * bar, like Gboard's: text only, not part of the bar, gone as soon as nothing is typed.
 */
class PreeditPopup(private val ctx: Context) {
    private val c = M3Colors.of(ctx)
    private val text = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(c.onSurface)
        typeface = app.lychee.fonts.LycheeFonts.ui(ctx, 500)
        isSingleLine = true
        includeFontPadding = false
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(c.surfaceContainerHigh)
        }
        elevation = dp(2).toFloat()
    }
    private val window = PopupWindow(text, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
        isTouchable = false
        isClippingEnabled = false
        elevation = dp(2).toFloat()
        setBackgroundDrawable(null)
    }

    /** Shows [value] above the top-left corner of [anchor] (the candidate bar), or hides it when empty. */
    fun show(anchor: View, value: CharSequence) {
        if (value.isEmpty() || anchor.windowToken == null || !anchor.isShown) {
            dismiss()
            return
        }
        text.text = value
        text.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val loc = IntArray(2).also { anchor.getLocationInWindow(it) }
        val x = loc[0] + dp(6)
        val y = loc[1] - text.measuredHeight - dp(4)
        val w = text.measuredWidth
        val h = text.measuredHeight
        // a window shown at one size doesn't reliably take a new one: show it afresh when the
        // text grows or shrinks, so all of it is always there
        if (window.isShowing && window.width == w && window.height == h) {
            window.update(x, y, w, h)
        } else {
            if (window.isShowing) window.dismiss()
            window.width = w
            window.height = h
            window.showAtLocation(anchor, Gravity.TOP or Gravity.START, x, y)
        }
    }

    fun dismiss() {
        if (window.isShowing) window.dismiss()
    }

    private fun dp(v: Int) = (ctx.resources.displayMetrics.density * v).toInt()
}
