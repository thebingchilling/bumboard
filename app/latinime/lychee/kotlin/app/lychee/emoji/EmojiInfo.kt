package app.lychee.emoji

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
import app.lychee.ui.M3Colors

/**
 * What an emoji is, held on one that has no skin tones: a small card above it with the emoji
 * large, its name and its code points (U+1F600), springing up from it. Also used for the
 * symbols of the Unicode search.
 */
class EmojiInfo(private val ctx: Context) {
    private var window: PopupWindow? = null

    fun dismiss() {
        window?.dismiss()
        window = null
    }

    fun show(anchor: View, text: String, name: String?, emoji: Boolean = true) {
        dismiss()
        val c = M3Colors.of(ctx)
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHigh)
                cornerRadius = dp(22).toFloat()
            }
            elevation = dp(8).toFloat()
        }
        card.addView(TextView(ctx).apply {
            this.text = text
            typeface = if (emoji) app.lychee.fonts.LycheeFonts.emoji(ctx) else app.lychee.fonts.LycheeFonts.ui(ctx)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
            setTextColor(c.onSurface)
            includeFontPadding = false
            gravity = Gravity.CENTER
        })
        name?.takeIf { it.isNotBlank() }?.let { n ->
            card.addView(TextView(ctx).apply {
                this.text = n.replaceFirstChar { it.uppercase() }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(c.onSurface)
                typeface = app.lychee.fonts.LycheeFonts.ui(ctx, 500)
                gravity = Gravity.CENTER
                maxWidth = dp(240)
                setPadding(0, dp(8), 0, 0)
            })
        }
        card.addView(TextView(ctx).apply {
            this.text = codePoints(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(c.onSurfaceVariant)
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            maxWidth = dp(240)
            setPadding(0, dp(2), 0, 0)
        })
        card.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popup = PopupWindow(card, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            animationStyle = com.android.inputmethod.latin.R.style.LycheePopupAnimation
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setOnDismissListener { if (window === this) window = null }
        }
        window = popup
        val x = (anchor.width - card.measuredWidth) / 2
        popup.showAsDropDown(anchor, x, -(anchor.height + card.measuredHeight + dp(6)))
        card.pivotX = card.measuredWidth / 2f
        card.pivotY = card.measuredHeight.toFloat()
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        card.alpha = 0f
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.3f)).start()
    }

    private fun dp(v: Int) = (ctx.resources.displayMetrics.density * v).toInt()

    companion object {
        /** "U+1F44B U+1F3FD" for the code points of [text] (variation selectors left out). */
        fun codePoints(text: String): String = text.codePoints().toArray()
            .filter { it != 0xFE0F && it != 0xFE0E }
            .joinToString(" ") { "U+%04X".format(it) }
    }
}
