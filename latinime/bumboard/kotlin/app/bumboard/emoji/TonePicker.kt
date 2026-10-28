package app.bumboard.emoji

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
import com.android.inputmethod.latin.R

/**
 * Skin tones for an emoji in a popup above it, like Gboard's. One person: yellow and the five
 * tones in a row. Two people: yellow and a preview side by side, then a row of swatches for
 * the left and the right person; tap the preview to type it.
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
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHigh)
                cornerRadius = dp(20).toFloat()
            }
            elevation = dp(6).toFloat()
        }
        lateinit var popup: PopupWindow
        fun pick(text: String) {
            popup.dismiss()
            onPick(text)
        }
        fun cell(text: String, size: Int = 46, onClick: () -> Unit = { pick(text) }) = TextView(ctx).apply {
            this.text = text
            typeface = app.bumboard.fonts.BumFonts.emoji(context)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(size) * 0.62f)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            if (text == selected) background = rounded(c.primaryContainer, 12)
            setOnClickListener { onClick() }
        }
        if (emoji.twoPeople) {
            // the tones of the emoji shown now, or none chosen yet
            val now = tones.indexOf(selected)
            var first = if (now >= 0) now / 5 else 0
            var second = if (now >= 0) now % 5 else 0
            val preview = cell(tones[first * 5 + second], 64) {}
            preview.background = rounded(c.primaryContainer, 16)
            preview.contentDescription = ctx.getString(R.string.bum_tone_type)
            preview.setOnClickListener { pick(tones[first * 5 + second]) }
            val rows = Array(2) { LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL } }
            fun refresh() {
                preview.text = tones[first * 5 + second]
                rows.forEachIndexed { person, row ->
                    for (t in 0 until 5) {
                        val chosen = if (person == 0) first == t else second == t
                        (row.getChildAt(t + 1) as View).background = swatch(t, chosen, c.primary)
                    }
                }
            }
            rows.forEachIndexed { person, row ->
                row.addView(TextView(ctx).apply {
                    setText(if (person == 0) R.string.bum_tone_person1 else R.string.bum_tone_person2)
                    setTextColor(c.onSurfaceVariant)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }, LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT))
                for (t in 0 until 5) {
                    row.addView(View(ctx).apply {
                        contentDescription = ctx.getString(TONE_NAMES[t])
                        setOnClickListener {
                            if (person == 0) first = t else second = t
                            refresh()
                        }
                    }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
                }
            }
            // the two outputs, centred: yellow, and the chosen tones (tap either to type it)
            content.addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER
                addView(cell(emoji.base, 64))
                addView(View(ctx), LinearLayout.LayoutParams(dp(16), 1))
                addView(preview)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            rows.forEach { content.addView(it) }
            refresh()
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

    /** A round skin-tone swatch, ringed when chosen. */
    private fun swatch(tone: Int, chosen: Boolean, ring: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(SWATCHES[tone])
        if (chosen) setStroke(dp(3), ring)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(v: Int) = (ctx.resources.displayMetrics.density * v).toInt()

    private companion object {
        /** Fitzpatrick 1-2 .. 6, as Unicode's emoji modifiers are usually drawn. */
        val SWATCHES = intArrayOf(
            0xFFF7D7C4.toInt(), 0xFFE0B794.toInt(), 0xFFBF8B5E.toInt(), 0xFF9B643D.toInt(), 0xFF5E4235.toInt(),
        )
        val TONE_NAMES = intArrayOf(
            R.string.bum_tone_1, R.string.bum_tone_2, R.string.bum_tone_3, R.string.bum_tone_4, R.string.bum_tone_5,
        )
    }
}
