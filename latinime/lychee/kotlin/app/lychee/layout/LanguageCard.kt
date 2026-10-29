package app.lychee.layout

import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import app.lychee.fonts.LycheeFonts
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * The keyboard list (long-press on the globe or space), like Gboard's: a card that springs up
 * out of the key held (the space bar), over the keys, which fade behind it, listing Lychee's
 * keyboards with the current one ticked and Language settings underneath. Tapping outside the
 * card closes it, and it shrinks back into the key.
 */
object LanguageCard {
    private var window: PopupWindow? = null
    private var card: View? = null
    private var scrim: View? = null

    fun dismiss() {
        val w = window ?: return
        window = null
        val c = card
        val s = scrim
        if (c == null || s == null || !w.isShowing) {
            w.dismiss()
            return
        }
        s.animate().alpha(0f).setDuration(120).start()
        c.animate().scaleX(0.7f).scaleY(0.7f).alpha(0f).setDuration(120)
            .withEndAction { runCatching { w.dismiss() } }.start()
    }

    /**
     * [anchorX] and [anchorTop]: the middle and the top of the key held, in [keyboard]'s
     * coordinates (the card rises from there); -1 to centre it over the keys.
     */
    @JvmStatic
    @JvmOverloads
    fun show(keyboard: View, names: List<CharSequence>, checked: Int, onPick: (Int) -> Unit, onSettings: () -> Unit,
             anchorX: Int = -1, anchorTop: Int = -1) {
        window?.dismiss()
        window = null
        dismiss()
        val context = keyboard.context
        val palette = M3Colors.of(context)
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (density * v).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(palette.surfaceContainer)
            }
            elevation = dp(6).toFloat()
        }
        fun row(text: CharSequence, ticked: Boolean, icon: Int?, onClick: () -> Unit) = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            background = app.lychee.ui.LycheeColorStyle.themed(context).getDrawable(R.drawable.lychee_m3_suggestion)
            addView(TextView(context).apply {
                this.text = text
                setTextColor(palette.onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = LycheeFonts.ui(context, if (ticked) 500 else 400)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val mark = if (ticked) R.drawable.ic_sym_check else icon
            if (mark != null) addView(ImageView(context).apply {
                setImageResource(mark)
                imageTintList = ColorStateList.valueOf(if (ticked) palette.primary else palette.onSurfaceVariant)
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            setOnClickListener { dismiss(); onClick() }
        }
        // the keyboards scroll when they do not all fit over the keys
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        names.forEachIndexed { i, name ->
            list.addView(row(name, i == checked, null) { onPick(i) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }
        val room = keyboard.height - dp(32) - dp(48 + 9 + 16)
        card.addView(android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, minOf(dp(48) * names.size, room.coerceAtLeast(dp(48)))))
        card.addView(View(context).apply { setBackgroundColor(palette.outlineVariant) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(dp(16), dp(4), dp(16), dp(4)) })
        card.addView(row(context.getString(R.string.lychee_language_settings), false, R.drawable.ic_sym_settings) { onSettings() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        // the keys fade behind the card; a tap on them closes it
        val background = app.lychee.ui.LycheeColorStyle.color(context, R.attr.lycheeColorBackground)
        val width = minOf(dp(320), keyboard.width - dp(32))
        val anchored = anchorX >= 0 && anchorTop > 0
        val scrim = FrameLayout(context).apply {
            setBackgroundColor((background and 0x00FFFFFF) or 0xB3000000.toInt())
            setOnClickListener { dismiss() }
            addView(card, if (anchored) {
                // just above the key held, centred on it, kept inside the keys
                FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                    leftMargin = (anchorX - width / 2).coerceIn(dp(16), keyboard.width - width - dp(16))
                    bottomMargin = (keyboard.height - anchorTop + dp(6)).coerceAtMost(keyboard.height / 3)
                }
            } else {
                FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            })
        }
        val location = IntArray(2).also { keyboard.getLocationInWindow(it) }
        val popup = PopupWindow(scrim, keyboard.width, keyboard.height, false).apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            isClippingEnabled = false
        }
        window = popup
        this.card = card
        this.scrim = scrim
        popup.showAtLocation(keyboard, Gravity.TOP or Gravity.START, location[0], location[1])
        // the scrim fades in, the card springs up out of the key
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(160).start()
        card.alpha = 0f
        card.scaleX = 0.4f
        card.scaleY = 0.4f
        card.post {
            val lp = card.layoutParams as FrameLayout.LayoutParams
            card.pivotX = if (anchored) (anchorX - lp.leftMargin).toFloat().coerceIn(0f, card.width.toFloat()) else card.width / 2f
            card.pivotY = card.height.toFloat()
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()
        }
    }
}
