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
import com.android.inputmethod.latin.R

/**
 * Skin tones for an emoji in a card above it, like Gboard's, springing up from the emoji. One
 * person: yellow and the five tones in a row. Two people: yellow and the chosen pair side by
 * side (tap either to type it), then a row of tone swatches for each person, labelled Left and
 * Right; the pair updates as the swatches are tapped. Holding any of its emoji shows what it is.
 * It takes only the room the emoji need.
 */
class TonePicker(private val ctx: Context) {
    private var window: PopupWindow? = null

    fun dismiss() {
        window?.dismiss()
        window = null
    }

    /**
     * [onInfo]: holding one of the picker's emoji (yellow, a tone, the chosen pair) shows what
     * it is.
     */
    fun show(anchor: View, emoji: Emoji, selected: String, onInfo: ((View, String) -> Unit)? = null, onPick: (String) -> Unit) {
        val tones = emoji.tones ?: return
        dismiss()
        val c = M3Colors.of(ctx)
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // only as much room as the emoji need
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHigh)
                cornerRadius = dp(20).toFloat()
            }
            elevation = dp(8).toFloat()
        }
        lateinit var popup: PopupWindow
        fun pick(text: String) {
            popup.dismiss()
            onPick(text)
        }
        fun cell(text: String, size: Int = 46, onClick: () -> Unit = { pick(text) }) = EmojiView(ctx).apply {
            // drawn whole and centred by the emoji renderer
            this.text = text
            fill = 0.68f
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            background = pressable(if (text == selected) c.primaryContainer else Color.TRANSPARENT, size / 3, c.onSurface)
            setOnClickListener { onClick() }
            if (onInfo != null) setOnLongClickListener { onInfo(this, this.text.toString()); true }
        }
        if (emoji.twoPeople) {
            // the tones of the emoji shown now, or none chosen yet
            val now = tones.indexOf(selected)
            var first = if (now >= 0) now / 5 else 0
            var second = if (now >= 0) now % 5 else 0
            val preview = cell(tones[first * 5 + second], 52) {}
            preview.background = pressable(c.primaryContainer, 20, c.onPrimaryContainer)
            preview.contentDescription = ctx.getString(R.string.lychee_tone_type)
            preview.setOnClickListener { pick(tones[first * 5 + second]) }
            val swatches = Array(2) { arrayOfNulls<View>(5) }
            fun refresh(animate: Boolean) {
                val pair = tones[first * 5 + second]
                if (preview.text.toString() != pair) {
                    preview.text = pair
                    if (animate) bounce(preview)
                }
                for (person in 0..1) {
                    val tone = if (person == 0) first else second
                    for (t in 0 until 5) {
                        val v = swatches[person][t] ?: continue
                        val chosen = tone == t
                        v.background = swatch(t, chosen, c.primary, c.surfaceContainerHigh)
                        v.animate().scaleX(if (chosen) 1.12f else 1f).scaleY(if (chosen) 1.12f else 1f)
                            .setDuration(if (animate) 160L else 0L).start()
                    }
                }
            }
            // the two outputs, centred: yellow, and the chosen tones (tap either to type it)
            content.addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER
                addView(cell(emoji.base, 52).apply { background = pressable(c.surfaceContainer, 16, c.onSurface) })
                addView(View(ctx), LinearLayout.LayoutParams(dp(8), 1))
                addView(preview)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            content.addView(View(ctx).apply { setBackgroundColor(c.outlineVariant) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(dp(2), dp(6), dp(2), dp(2)) })
            for (person in 0..1) {
                content.addView(LinearLayout(ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    // which person the row is for, in words (no extra person drawn)
                    addView(TextView(ctx).apply {
                        setText(if (person == 0) R.string.lychee_tone_person1 else R.string.lychee_tone_person2)
                        setTextColor(c.onSurfaceVariant)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
                        includeFontPadding = false
                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    }, LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(4) })
                    for (t in 0 until 5) {
                        addView(View(ctx).apply {
                            contentDescription = ctx.getString(TONE_NAMES[t])
                            setOnClickListener {
                                if (person == 0) first = t else second = t
                                refresh(animate = true)
                            }
                            swatches[person][t] = this
                        }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) })
                    }
                })
            }
            refresh(animate = false)
        } else {
            content.addView(LinearLayout(ctx).apply {
                addView(cell(emoji.base))
                // a hairline between yellow and the tones
                addView(View(ctx).apply { setBackgroundColor(c.outlineVariant) },
                    LinearLayout.LayoutParams(dp(1), dp(28)).apply { gravity = Gravity.CENTER_VERTICAL; setMargins(dp(2), 0, dp(2), 0) })
                tones.forEach { addView(cell(it)) }
            })
        }
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        // exactly the measured size: the rows that fill the card's width would otherwise fill
        // the screen's
        popup = PopupWindow(content, content.measuredWidth, content.measuredHeight, false).apply {
            animationStyle = com.android.inputmethod.latin.R.style.LycheePopupAnimation
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setOnDismissListener { if (window === this) window = null }
        }
        window = popup
        // centred above the emoji, kept on screen by PopupWindow; it springs up from the emoji
        val x = (anchor.width - content.measuredWidth) / 2
        popup.showAsDropDown(anchor, x, -(anchor.height + content.measuredHeight))
        app.lychee.layout.LycheeMotion.cardIn(content, content.measuredWidth / 2f, content.measuredHeight.toFloat())
    }

    private fun bounce(view: View) {
        view.scaleX = 0.82f
        view.scaleY = 0.82f
        view.animate().scaleX(1f).scaleY(1f).setDuration(200)
            .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
    }

    /** A round skin-tone swatch; the chosen one is ringed, with a gap, in the accent colour. */
    private fun swatch(tone: Int, chosen: Boolean, ring: Int, gap: Int): android.graphics.drawable.Drawable {
        val face = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(SWATCHES[tone])
        }
        if (!chosen) return android.graphics.drawable.InsetDrawable(face, dp(3))
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(gap)
            setStroke(dp(2), ring)
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(outer, face)).apply { setLayerInset(1, dp(4), dp(4), dp(4), dp(4)) }
    }

    /** A rounded tile that ripples when pressed. */
    private fun pressable(color: Int, radius: Int, ripple: Int): android.graphics.drawable.Drawable {
        val shape = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(radius).toFloat()
        }
        return android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(ripple, 0x33)), shape, mask,
        )
    }

    private fun dp(v: Int) = (ctx.resources.displayMetrics.density * v).toInt()

    private companion object {
        /** Fitzpatrick 1-2 .. 6, as Unicode's emoji modifiers are usually drawn. */
        val SWATCHES = intArrayOf(
            0xFFF7D7C4.toInt(), 0xFFE0B794.toInt(), 0xFFBF8B5E.toInt(), 0xFF9B643D.toInt(), 0xFF5E4235.toInt(),
        )
        val TONE_NAMES = intArrayOf(
            R.string.lychee_tone_1, R.string.lychee_tone_2, R.string.lychee_tone_3, R.string.lychee_tone_4, R.string.lychee_tone_5,
        )
    }
}
