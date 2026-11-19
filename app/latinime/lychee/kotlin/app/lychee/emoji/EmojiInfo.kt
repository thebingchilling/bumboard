package app.lychee.emoji

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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
        if (emoji) {
            // the emoji large, drawn whole by the emoji renderer
            card.addView(EmojiView(ctx).apply {
                this.text = text
                fill = 0.92f
            }, android.widget.LinearLayout.LayoutParams(dp(80), dp(80)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        } else {
            card.addView(TextView(ctx).apply {
                this.text = text
                typeface = app.lychee.fonts.LycheeFonts.ui(ctx)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
                setTextColor(c.onSurface)
                includeFontPadding = false
                gravity = Gravity.CENTER
            })
        }
        // as wide as the screen allows (a margin each side), so a long name wraps, never cut off
        val screen = ctx.resources.displayMetrics.widthPixels
        val widest = (screen - dp(32)).coerceAtMost(dp(320))
        val textWidth = widest - dp(36)
        name?.takeIf { it.isNotBlank() }?.let { n ->
            card.addView(TextView(ctx).apply {
                this.text = n.replaceFirstChar { it.uppercase() }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(c.onSurface)
                typeface = app.lychee.fonts.LycheeFonts.ui(ctx, 500)
                gravity = Gravity.CENTER
                maxWidth = textWidth
                setPadding(0, dp(8), 0, 0)
            })
        }
        card.addView(TextView(ctx).apply {
            this.text = codePoints(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(c.onSurfaceVariant)
            typeface = app.lychee.fonts.LycheeFonts.ui(ctx)
            gravity = Gravity.CENTER
            maxWidth = textWidth
            setPadding(0, dp(2), 0, 0)
        })
        card.measure(View.MeasureSpec.makeMeasureSpec(widest, View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED)
        // its exact size, unclipped: the window manager never squeezes it (cutting the name off)
        val w = card.measuredWidth
        val h = card.measuredHeight
        val popup = PopupWindow(card, w, h, false).apply {
            isClippingEnabled = false
            animationStyle = com.android.inputmethod.latin.R.style.LycheePopupAnimation
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setOnDismissListener { if (window === this) window = null }
        }
        window = popup
        // centred above what was held, kept on the screen
        val at = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val left = (at[0] + (anchor.width - w) / 2).coerceIn(dp(8), (screen - w - dp(8)).coerceAtLeast(dp(8)))
        val top = (at[1] - h - dp(6)).coerceAtLeast(0)
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, left, top)
        app.lychee.layout.LycheeMotion.cardIn(card, (at[0] + anchor.width / 2f - left).coerceIn(0f, w.toFloat()), h.toFloat())
    }

    private fun dp(v: Int) = (ctx.resources.displayMetrics.density * v).toInt()

    companion object {
        /** "U+1F44B U+1F3FD" for the code points of [text] (variation selectors left out). */
        fun codePoints(text: String): String = text.codePoints().toArray()
            .filter { it != 0xFE0F && it != 0xFE0E }
            .joinToString(" ") { "U+%04X".format(it) }
    }
}
