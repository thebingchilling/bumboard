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
    private const val ROW_DP = 48
    private const val TITLE_DP = 36
    private const val MIN_ROW_DP = 36
    /** The gap kept between the card and the edges of the keys. */
    private const val EDGE_DP = 8

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
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = LycheeFonts.ui(context, if (ticked) 500 else 400)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val mark = if (ticked) R.drawable.ic_sym_check else icon
            if (mark != null) addView(ImageView(context).apply {
                setImageResource(mark)
                imageTintList = ColorStateList.valueOf(if (ticked) palette.primary else palette.onSurfaceVariant)
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            setOnClickListener { dismiss(); onClick() }
        }
        // the card has the keys and the bar above them to itself (more room than the keys alone)
        val bar = keyboard.rootView.findViewById<View>(R.id.lychee_top_bar)
        val keysLoc = IntArray(2).also { keyboard.getLocationInWindow(it) }
        val extra = bar?.takeIf { it.isShown }?.let { b ->
            (keysLoc[1] - IntArray(2).also { b.getLocationInWindow(it) }[1]).coerceIn(0, dp(120))
        } ?: 0
        val areaHeight = keyboard.height + extra
        val anchorY = if (anchorTop > 0) anchorTop + extra else -1

        // a title, every keyboard (never scrolling: the rows get shorter, then go into two
        // columns, so all of them fit), then Language settings under them
        val n = names.size.coerceAtLeast(1)
        val area = areaHeight - 2 * dp(EDGE_DP)
        // the card's fixed parts: its padding, the divider and Language settings (the title too
        // while there is room for it)
        val fixedNoTitle = dp(8 + 8 + 9 + ROW_DP)
        val withTitle = fixedNoTitle + dp(TITLE_DP) + dp(ROW_DP) * n <= area
        val fixed = fixedNoTitle + if (withTitle) dp(TITLE_DP) else 0
        // one column of rows as tall as 48dp, no shorter than 36dp; two columns when even that
        // is too tall
        val columns = if (fixed + dp(MIN_ROW_DP) * n <= area) 1 else 2
        val rowsTall = (n + columns - 1) / columns
        val rowHeight = ((area - fixed) / rowsTall).coerceIn(dp(MIN_ROW_DP).coerceAtMost(dp(ROW_DP)), dp(ROW_DP))
        val listHeight = rowHeight * rowsTall
        // above the key held when it all fits there; otherwise centred over the keys
        val roomAbove = if (anchorY > 0) anchorY - dp(6) - dp(EDGE_DP) else 0
        val fitsAbove = anchorY > 0 && fixed + listHeight <= roomAbove
        if (withTitle) card.addView(TextView(context).apply {
            setText(R.string.lychee_language_card_title)
            setTextColor(palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = LycheeFonts.ui(context, 500)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(TITLE_DP)))
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until rowsTall) {
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until columns) {
                val i = r * columns + col
                if (i < names.size) {
                    line.addView(row(names[i], i == checked, null) { onPick(i) }.apply {
                        if (columns > 1) setPadding(dp(14), 0, dp(10), 0)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                } else {
                    line.addView(View(context), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                }
            }
            list.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight))
        }
        card.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight))
        card.addView(View(context).apply { setBackgroundColor(palette.outlineVariant) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(dp(16), dp(4), dp(16), dp(4)) })
        card.addView(row(context.getString(R.string.lychee_language_settings), false, R.drawable.ic_sym_settings) { onSettings() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ROW_DP)))

        // the keys fade behind the card; a tap on them closes it
        val background = app.lychee.ui.LycheeColorStyle.color(context, R.attr.lycheeColorBackground)
        // two columns get a wider card
        val width = minOf(dp(if (columns > 1) 400 else 320), keyboard.width - 2 * dp(EDGE_DP))
        val anchored = anchorX >= 0 && fitsAbove
        val scrim = FrameLayout(context).apply {
            setBackgroundColor((background and 0x00FFFFFF) or 0xB3000000.toInt())
            setOnClickListener { dismiss() }
            addView(card, if (anchored) {
                // just above the key held, centred on it, kept inside the keys
                FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                    leftMargin = (anchorX - width / 2).coerceIn(dp(EDGE_DP), (keyboard.width - width - dp(EDGE_DP)).coerceAtLeast(dp(EDGE_DP)))
                    bottomMargin = areaHeight - anchorY + dp(6)
                }
            } else {
                FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            })
        }
        val popup = PopupWindow(scrim, keyboard.width, areaHeight, false).apply {
            animationStyle = com.android.inputmethod.latin.R.style.LycheePopupAnimation
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            isClippingEnabled = false
        }
        window = popup
        this.card = card
        this.scrim = scrim
        popup.showAtLocation(keyboard, Gravity.TOP or Gravity.START, keysLoc[0], keysLoc[1] - extra)
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
