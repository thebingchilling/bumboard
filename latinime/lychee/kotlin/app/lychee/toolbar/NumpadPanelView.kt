package app.lychee.toolbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.lychee.lycheeHost
import app.lychee.layout.LycheePanels
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants

/**
 * A number pad over the keys, laid out like Gboard's: a scrolling column of operators on the
 * left, big digits in a phone-style 3 x 3 grid, %, space and backspace on the right, and
 * ABC , !?# 0 = . Enter along the bottom. It opens from the "12/34" key on the symbols page;
 * ABC goes back to the letters and !?# to the symbols.
 */
class NumpadPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    private val repeat = Handler(Looper.getMainLooper())

    init {
        orientation = VERTICAL
        isClickable = true
        setBackgroundColor(palette.surface)
        setPadding(dp(3), dp(4), dp(3), dp(4))
        val top = LinearLayout(context).apply { orientation = HORIZONTAL }
        // operators: a column that scrolls, the first three showing
        val operators = LinearLayout(context).apply { orientation = VERTICAL }
        val scroll = android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(operators)
        }
        top.addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        scroll.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob ->
            if (b - t != ob - ot) {
                val keyHeight = (b - t) / 3
                for (i in 0 until operators.childCount) {
                    val lp = operators.getChildAt(i).layoutParams as LayoutParams
                    if (lp.height != keyHeight - dp(6)) {
                        lp.height = keyHeight - dp(6)
                        operators.getChildAt(i).layoutParams = lp
                    }
                }
            }
        }
        for (op in OPERATORS) {
            operators.addView(text(op), LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        val digits = LinearLayout(context).apply { orientation = VERTICAL }
        for (row in listOf("123", "456", "789")) {
            digits.addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEach { d -> addView(digit(d.toString()), keyParams(1f)) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        top.addView(digits, LayoutParams(0, LayoutParams.MATCH_PARENT, 3.6f))
        val right = LinearLayout(context).apply { orientation = VERTICAL }
        right.addView(text("%"), keyParams(1f, vertical = true))
        right.addView(spaceKey(), keyParams(1f, vertical = true))
        right.addView(icon(R.drawable.ic_sym_backspace, R.string.lychee_emoji_delete, repeats = true) { send(Constants.CODE_DELETE) },
            keyParams(1f, vertical = true))
        top.addView(right, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.2f))
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, 0, 3f))
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(abcKey(), keyParams(1.3f))
            addView(text(","), keyParams(0.8f))
            addView(key("!?#", letter = false, size = 15f) {
                // the symbols page is underneath
                visibility = GONE
            }, keyParams(1f))
            addView(digit("0"), keyParams(1.6f))
            addView(text("="), keyParams(0.8f))
            addView(text("."), keyParams(0.8f))
            addView(enterKey(), keyParams(1.3f))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        visibility = GONE
    }

    private fun keyParams(weight: Float, vertical: Boolean = false) =
        (if (vertical) LayoutParams(LayoutParams.MATCH_PARENT, 0, weight) else LayoutParams(0, LayoutParams.MATCH_PARENT, weight))
            .apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }

    /** ABC back to the letters: a pill, like the ?123 key. */
    private fun abcKey() = key(context.getString(R.string.lychee_emoji_abc), letter = false, size = 15f) {
        visibility = GONE
        lycheeHost()?.lycheeSendCode(Constants.CODE_SWITCH_ALPHA_SYMBOL)
    }.apply {
        background = app.lychee.ui.LycheeColorStyle.themed(context).getDrawable(R.drawable.lychee_m3_key_pill_flat)
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE) repeat.removeCallbacksAndMessages(null)
        super.setVisibility(visibility)
    }

    private fun digit(d: String) = key(d, letter = true, size = 26f) { lycheeHost()?.lycheeSendCode(d[0].code) }

    private fun text(t: String) = key(t, letter = false) { lycheeHost()?.lycheeSendCode(t[0].code) }


    private fun spaceKey() = icon(R.drawable.ic_sym_space_bar, R.string.lychee_numpad_space) { send(Constants.CODE_SPACE) }

    private fun enterKey() = ImageView(context).apply {
        setImageResource(R.drawable.ic_sym_keyboard_return)
        imageTintList = ColorStateList.valueOf(palette.onPrimary)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.lychee_numpad_enter)
        // a pill in the accent colour, like the keyboard's Enter
        background = GradientDrawable().apply {
            cornerRadius = dp(100).toFloat()
            setColor(palette.primary)
        }
        setOnClickListener { send(Constants.CODE_ENTER) }
    }

    private fun key(label: String, letter: Boolean, size: Float = 22f, action: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(if (letter) palette.onSurface else palette.onSecondaryContainer)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, if (letter) 400 else 500)
        gravity = Gravity.CENTER
        background = if (letter) LycheePanels.letterKey(context) else LycheePanels.functionalKey(context)
        setOnClickListener { action() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun icon(res: Int, label: Int, repeats: Boolean = false, action: () -> Unit) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(palette.onSecondaryContainer)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(label)
        background = LycheePanels.functionalKey(context)
        if (!repeats) {
            setOnClickListener { action() }
        } else setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    action()
                    repeat.postDelayed(object : Runnable {
                        override fun run() {
                            action()
                            repeat.postDelayed(this, 50)
                        }
                    }, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    repeat.removeCallbacksAndMessages(null)
                }
            }
            true
        }
    }

    private fun send(code: Int) = lycheeHost()?.lycheeSendCode(code)

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        val OPERATORS = listOf("+", "-", "*", "/", "(", ")", ":", ";", "#", "@", "~", "^")
    }
}
