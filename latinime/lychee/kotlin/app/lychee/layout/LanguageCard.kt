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
 * The keyboard list (long-press on the globe or space), like Gboard's: a white card over the
 * keys, which fade behind it, listing Lychee's keyboards with the current one ticked and
 * Language settings underneath. Tapping outside the card closes it.
 */
object LanguageCard {
    private var window: PopupWindow? = null

    fun dismiss() {
        window?.dismiss()
        window = null
    }

    @JvmStatic
    fun show(keyboard: View, names: List<CharSequence>, checked: Int, onPick: (Int) -> Unit, onSettings: () -> Unit) {
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
            background = context.getDrawable(R.drawable.lychee_m3_suggestion)
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
        val background = context.getColor(R.color.lychee_m3_kb_background)
        val scrim = FrameLayout(context).apply {
            setBackgroundColor((background and 0x00FFFFFF) or 0xB3000000.toInt())
            setOnClickListener { dismiss() }
            addView(card, FrameLayout.LayoutParams(minOf(dp(320), keyboard.width - dp(32)),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val location = IntArray(2).also { keyboard.getLocationInWindow(it) }
        val popup = PopupWindow(scrim, keyboard.width, keyboard.height, false).apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            isClippingEnabled = false
        }
        window = popup
        popup.showAtLocation(keyboard, Gravity.TOP or Gravity.START, location[0], location[1])
    }
}
