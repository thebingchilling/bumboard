package app.lychee.toolbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
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
 * A number pad over the keys: a narrow scrolling column of operators on the left (its
 * scrollbar showing), big digits in a phone-style grid, !?#, space and backspace on the right,
 * and ABC , 0 . Enter along the bottom, lined up with the columns above (0 is a digit like the
 * others). Four rows of the same height. It opens from the "12/34" key on the symbols page and
 * changes like the keyboard does; ABC goes back to the letters and !?# to the symbols.
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
        // four rows of equal height, like the keyboard's own rows; the bottom row lines up with
        // the columns above it, so 0 is a digit like the others
        val top = LinearLayout(context).apply { orientation = HORIZONTAL }
        // operators: a narrow column that scrolls, its scrollbar always showing
        val operators = LinearLayout(context).apply { orientation = VERTICAL }
        // one rounded panel holding the operators as a list (not a stack of keys), its
        // scrollbar inside the panel's edge
        val scroll = android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            setPadding(0, dp(2), dp(4), dp(2))
            clipToPadding = false
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(palette.surfaceContainerHigh)
            }
            clipToOutline = true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                verticalScrollbarThumbDrawable = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(2).toFloat()
                    setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(palette.onSurfaceVariant, 0x99))
                    setSize(dp(3), dp(24))
                }
                verticalScrollbarTrackDrawable = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(2).toFloat()
                    setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(palette.onSurfaceVariant, 0x22))
                    setSize(dp(3), dp(24))
                }
            }
            addView(operators)
        }
        top.addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, COLUMN_OPERATORS).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        scroll.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob ->
            if (b - t != ob - ot) {
                // three and a bit showing, so it's plain there's more below
                val itemHeight = ((b - t) / 3.4f).toInt()
                for (i in 0 until operators.childCount) {
                    val child = operators.getChildAt(i)
                    val lp = child.layoutParams as LayoutParams
                    val h = if (child is TextView) itemHeight else lp.height
                    if (lp.height != h) {
                        lp.height = h
                        child.layoutParams = lp
                    }
                }
            }
        }
        OPERATORS.forEachIndexed { i, op ->
            if (i > 0) operators.addView(View(context).apply {
                setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(palette.outlineVariant, 0x80))
            }, LayoutParams(LayoutParams.MATCH_PARENT, 1).apply { setMargins(dp(10), 0, dp(10), 0) })
            operators.addView(operatorItem(op), LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        }
        val digits = LinearLayout(context).apply { orientation = VERTICAL }
        for (row in listOf("123", "456", "789")) {
            digits.addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEach { d -> addView(digit(d.toString()), keyParams(1f)) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        top.addView(digits, LayoutParams(0, LayoutParams.MATCH_PARENT, COLUMN_DIGIT * 3))
        val right = LinearLayout(context).apply { orientation = VERTICAL }
        right.addView(key("!?#", letter = false, size = 15f) {
            // the symbols page is underneath (this, not the column the key sits in)
            this@NumpadPanelView.visibility = GONE
        }, keyParams(1f, vertical = true))
        right.addView(spaceKey(), keyParams(1f, vertical = true))
        right.addView(icon(R.drawable.ic_sym_backspace, R.string.lychee_emoji_delete, repeats = true) { send(Constants.CODE_DELETE) },
            keyParams(1f, vertical = true))
        top.addView(right, LayoutParams(0, LayoutParams.MATCH_PARENT, COLUMN_SIDE))
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, 0, 3f))
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(abcKey(), keyParams(COLUMN_OPERATORS))
            addView(text(","), keyParams(COLUMN_DIGIT))
            addView(digit("0"), keyParams(COLUMN_DIGIT))
            addView(text("."), keyParams(COLUMN_DIGIT))
            addView(enterKey(), keyParams(COLUMN_SIDE))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        visibility = GONE
    }

    private fun keyParams(weight: Float, vertical: Boolean = false) =
        (if (vertical) LayoutParams(LayoutParams.MATCH_PARENT, 0, weight) else LayoutParams(0, LayoutParams.MATCH_PARENT, weight))
            .apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }

    /** ABC back to the letters, like the keyboard's ?123 key. */
    private fun abcKey() = key(context.getString(R.string.lychee_emoji_abc), letter = false, size = 15f) {
        visibility = GONE
        lycheeHost()?.lycheeSendCode(Constants.CODE_SWITCH_ALPHA_SYMBOL)
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE) repeat.removeCallbacksAndMessages(null)
        super.setVisibility(visibility)
    }

    private fun digit(d: String) = key(d, letter = true, size = 26f) { lycheeHost()?.lycheeSendCode(d[0].code) }

    private fun text(t: String) = key(t, letter = false) { lycheeHost()?.lycheeSendCode(t[0].code) }

    /** An operator in the scrolling list: plain, rippling when tapped. */
    private fun operatorItem(t: String) = TextView(context).apply {
        text = t
        setTextColor(palette.onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
        gravity = Gravity.CENTER
        background = TypedValue().let { v ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
            context.getDrawable(v.resourceId)
        }
        setOnClickListener { lycheeHost()?.lycheeSendCode(t[0].code) }
    }


    private fun spaceKey() = icon(R.drawable.ic_sym_space_bar, R.string.lychee_numpad_space) { send(Constants.CODE_SPACE) }

    private fun enterKey() = ImageView(context).apply {
        setImageResource(R.drawable.ic_sym_keyboard_return)
        imageTintList = ColorStateList.valueOf(palette.onPrimary)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.lychee_numpad_enter)
        // in the accent colour, like the keyboard's Enter
        background = LycheePanels.accentKey(context)
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
        val OPERATORS = listOf("+", "-", "*", "/", "=", "%", "(", ")", ":", ";", "#", "@", "~", "^")
        /** the columns' widths: the (narrow) operators, each digit, the right-hand keys */
        const val COLUMN_OPERATORS = 0.8f
        const val COLUMN_DIGIT = 1.25f
        const val COLUMN_SIDE = 1.1f
    }
}
