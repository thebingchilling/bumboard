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
import app.lychee.layout.LycheeMotion
import app.lychee.layout.LycheePanels
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants

/**
 * A number pad over the keys. On the symbols page ("12/34"): a narrow scrolling column of small
 * symbols on the left, big digits in a phone-style grid, % / space / backspace on the right, and
 * a bottom bar exactly as tall as the keyboard's own bottom row (its height, gaps and bottom
 * padding), lined up with the columns above: ABC under the symbols, comma and !?# under the 7,
 * 0 under the 8, = and period under the 9, Enter under the right-hand keys. ABC goes back to the
 * letters and !?# to the symbols.
 *
 * In number, phone and date fields ([forced]) it is a plain pad as wide as the keyboard:
 * the digits, with backspace, 0 and Enter along the bottom bar (
 * phone numbers). What else the field takes ([setInputType]: − for signed numbers, a decimal
 * point for decimals, / and : for dates and times, + * # for phone numbers) is held on 0, like
 * a key's alternatives on the keyboard: hold, slide onto one and let go; a small mark in 0's
 * corner shows what is there.
 */
class NumpadPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    private val repeat = Handler(Looper.getMainLooper())
    // before init, which fills them in
    private var full: LinearLayout? = null
    private var plain: LinearLayout? = null
    private var fullBar: LinearLayout? = null
    private var plainBar: LinearLayout? = null
    private val bottomBars get() = listOfNotNull(fullBar, plainBar)
    /** The number field's extra keys: what is shown, and what it types. */
    private var extras: List<Pair<String, Char>> = emptyList()
    /** The mark in the number field's 0 showing what is held on it. */
    private var zeroHint: TextView? = null

    init {
        orientation = VERTICAL
        isClickable = true
        setBackgroundColor(palette.surface)
        setPadding(dp(3), dp(4), dp(3), dp(4))
        full = fullPad()
        plain = plainPad()
        addView(full, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(plain, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        plain?.visibility = GONE
        visibility = GONE
    }

    /** The symbols page's pad: symbols, digits and the right-hand keys over the bottom bar. */
    private fun fullPad() = LinearLayout(context).apply {
        orientation = VERTICAL
        val top = LinearLayout(context).apply { orientation = HORIZONTAL }
        top.addView(symbolsList(), LayoutParams(0, LayoutParams.MATCH_PARENT, COLUMN_SYMBOLS).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        top.addView(digitRows(), LayoutParams(0, LayoutParams.MATCH_PARENT, COLUMN_DIGIT * 3))
        val right = LinearLayout(context).apply { orientation = VERTICAL }
        right.addView(text("%"), keyParams(1f, vertical = true))
        right.addView(spaceKey(), keyParams(1f, vertical = true))
        right.addView(backspaceKey(), keyParams(1f, vertical = true))
        top.addView(right, LayoutParams(0, LayoutParams.MATCH_PARENT, COLUMN_SIDE))
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        // the bottom bar, each key under the column above it
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(abcKey(), keyParams(COLUMN_SYMBOLS))
            addView(text(","), keyParams(COLUMN_DIGIT / 2))
            addView(key("!?#", letter = false, size = 15f) {
                // the symbols page is underneath
                this@NumpadPanelView.visibility = GONE
            }, keyParams(COLUMN_DIGIT / 2))
            addView(digit("0"), keyParams(COLUMN_DIGIT))
            addView(text("="), keyParams(COLUMN_DIGIT / 2))
            addView(text("."), keyParams(COLUMN_DIGIT / 2))
            addView(enterKey(), keyParams(COLUMN_SIDE))
        }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))
        fullBar = bar
    }

    /** The number field's pad, as wide as the keyboard: the digits over ⌫ 0 Enter (its extras held on 0). */
    private fun plainPad() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        val block = LinearLayout(context).apply { orientation = VERTICAL }
        block.addView(digitRows(), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        bar.addView(backspaceKey(), keyParams(1f))
        bar.addView(zeroKey(), keyParams(1f))
        bar.addView(enterKey(), keyParams(1f))
        block.addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))
        plainBar = bar
        addView(block, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * The number field's kind ([android.text.InputType]): what is held on its 0. A plain number
     * has nothing; signed numbers −, decimals a point, dates /, times :, dates with times both,
     * phone numbers + * #.
     */
    fun setInputType(inputType: Int) {
        val cls = inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        extras = when (cls) {
            android.text.InputType.TYPE_CLASS_NUMBER -> buildList {
                if (inputType and android.text.InputType.TYPE_NUMBER_FLAG_SIGNED != 0) add("\u2212" to '-')
                if (inputType and android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL != 0) add("." to '.')
            }
            android.text.InputType.TYPE_CLASS_DATETIME -> when (variation) {
                android.text.InputType.TYPE_DATETIME_VARIATION_DATE -> listOf("/" to '/')
                android.text.InputType.TYPE_DATETIME_VARIATION_TIME -> listOf(":" to ':')
                else -> listOf("/" to '/', ":" to ':')
            }
            android.text.InputType.TYPE_CLASS_PHONE -> listOf("+" to '+', "*" to '*', "#" to '#')
            else -> emptyList()
        }
        zeroHint?.text = extras.joinToString("") { it.first }
        zeroHint?.visibility = if (extras.isEmpty()) GONE else VISIBLE
    }

    private var choices: android.widget.PopupWindow? = null
    private var choiceViews: List<TextView> = emptyList()
    private var chosen = 0

    /**
     * The number field's 0: a tap types 0; held, it shows the field's extras in a row above it
     * (grown out of the key), the first picked; sliding picks another, letting go types it.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun zeroKey(): View {
        val face = digit("0")
        val hint = TextView(context).apply {
            setTextColor(palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
            visibility = GONE
        }
        zeroHint = hint
        val hold = Runnable { if (extras.isNotEmpty()) showChoices(face) }
        face.setOnClickListener(null)
        face.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    repeat.postDelayed(hold, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> if (choices != null) pickAt(e.rawX)
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    repeat.removeCallbacks(hold)
                    if (choices != null) {
                        extras.getOrNull(chosen)?.let { send(it.second.code) }
                        hideChoices()
                    } else send('0'.code)
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    repeat.removeCallbacks(hold)
                    hideChoices()
                }
            }
            true
        }
        return android.widget.FrameLayout(context).apply {
            addView(face, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(hint, android.widget.FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END).apply { setMargins(0, dp(4), dp(8), 0) })
        }
    }

    private fun showChoices(key: View) {
        hideChoices()
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(palette.surfaceContainerHigh)
            }
            elevation = dp(6).toFloat()
        }
        val w = key.width.coerceAtMost(dp(64))
        choiceViews = extras.map { (label, _) ->
            TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                typeface = app.lychee.fonts.LycheeFonts.ui(context, 400)
                gravity = Gravity.CENTER
            }.also { row.addView(it, LayoutParams(w, key.height - dp(8))) }
        }
        chosen = 0
        paintChoices()
        val popup = android.widget.PopupWindow(row, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false).apply {
            isClippingEnabled = false
            elevation = dp(6).toFloat()
        }
        val at = IntArray(2).also { key.getLocationInWindow(it) }
        val width = w * extras.size + dp(8)
        val x = at[0] + key.width / 2 - width / 2
        val y = at[1] - key.height - dp(4)
        popup.showAtLocation(key, Gravity.NO_GRAVITY, x, y)
        choices = popup
        // grown out of the key that was held
        LycheeMotion.cardIn(row, width / 2f, key.height.toFloat())
    }

    /** Picks the choice under the finger (by its x on the screen). */
    private fun pickAt(rawX: Float) {
        val i = choiceViews.indexOfFirst { v ->
            val at = IntArray(2).also { v.getLocationOnScreen(it) }
            rawX >= at[0] && rawX < at[0] + v.width
        }
        if (i >= 0 && i != chosen) {
            chosen = i
            paintChoices()
        }
    }

    private fun paintChoices() {
        choiceViews.forEachIndexed { i, v ->
            v.setTextColor(if (i == chosen) palette.onPrimary else palette.onSurface)
            v.background = if (i == chosen) android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(palette.primary)
            } else null
        }
    }

    private fun hideChoices() {
        choices?.dismiss()
        choices = null
        choiceViews = emptyList()
    }

    /** 1 2 3 / 4 5 6 / 7 8 9, three rows sharing the height above the bottom bar. */
    private fun digitRows() = LinearLayout(context).apply {
        orientation = VERTICAL
        for (row in listOf("123", "456", "789")) {
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEach { d -> addView(digit(d.toString()), keyParams(1f)) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    /** The small symbols: one rounded panel that scrolls, its scrollbar always showing. */
    private fun symbolsList(): View {
        val list = LinearLayout(context).apply { orientation = VERTICAL }
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
            addView(list)
        }
        scroll.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob ->
            if (b - t != ob - ot) {
                // four and a bit showing, so it's plain there's more below
                val itemHeight = ((b - t) / 4.4f).toInt()
                for (i in 0 until list.childCount) {
                    val child = list.getChildAt(i)
                    val lp = child.layoutParams as LayoutParams
                    val h = if (child is TextView) itemHeight else lp.height
                    if (lp.height != h) {
                        lp.height = h
                        child.layoutParams = lp
                    }
                }
            }
        }
        SYMBOLS.forEachIndexed { i, op ->
            // a clear line between the symbols, so each reads as its own key
            if (i > 0) list.addView(View(context).apply {
                setBackgroundColor(palette.outlineVariant)
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(dp(6), 0, dp(6), 0) })
            list.addView(symbolItem(op), LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
        }
        return scroll
    }

    /**
     * The bottom bar exactly as tall as the keyboard's bottom row, with its gaps and the
     * keyboard's padding below it; the rows above share the rest.
     */
    private fun alignWithKeyboard() {
        val row = LycheePanels.keyRow(this)
        if (paddingBottom != row.bottomPadding) setPadding(paddingLeft, paddingTop, paddingRight, row.bottomPadding)
        val half = row.gap / 2
        for (bar in bottomBars) {
            val lp = bar.layoutParams as LayoutParams
            // the bar's keys keep the 3 dp margin the pad's other keys have; the row's gap is
            // made up around them
            val height = row.height + dp(3) * 2 - 2 * half
            if (lp.height != height) {
                lp.height = height
                bar.layoutParams = lp
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h != oldh) post { alignWithKeyboard() }
    }

    /** Number, phone and date fields: the plain pad (digits, backspace, Enter), centred. */
    var forced = false
        set(value) {
            field = value
            full?.visibility = if (value) GONE else VISIBLE
            plain?.visibility = if (value) VISIBLE else GONE
            requestLayout()
        }

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE) {
            repeat.removeCallbacksAndMessages(null)
            hideChoices()
        }
        super.setVisibility(visibility)
        if (visibility == VISIBLE) alignWithKeyboard()
    }

    private fun keyParams(weight: Float, vertical: Boolean = false) =
        (if (vertical) LayoutParams(LayoutParams.MATCH_PARENT, 0, weight) else LayoutParams(0, LayoutParams.MATCH_PARENT, weight))
            .apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }

    /** ABC back to the letters, like the keyboard's ?123 key. */
    private fun abcKey() = key(context.getString(R.string.lychee_emoji_abc), letter = false, size = 15f) {
        visibility = GONE
        lycheeHost()?.lycheeSendCode(Constants.CODE_SWITCH_ALPHA_SYMBOL)
    }

    private fun backspaceKey() = icon(R.drawable.ic_sym_backspace, R.string.lychee_emoji_delete, repeats = true) {
        send(Constants.CODE_DELETE)
    }

    private fun digit(d: String) = key(d, letter = true, size = 26f) { lycheeHost()?.lycheeSendCode(d[0].code) }

    private fun text(t: String, types: Char = t[0]) = key(t, letter = false) { lycheeHost()?.lycheeSendCode(types.code) }

    /** A symbol in the scrolling list: small and plain, rippling when tapped. */
    private fun symbolItem(t: String) = TextView(context).apply {
        text = t
        setTextColor(palette.onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
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
        /** The small symbols (%, =, comma and period have keys of their own). */
        val SYMBOLS = listOf("+", "-", "*", "/", "(", ")", ":", ";", "#", "@", "&", "$", "!", "?", "~", "^")
        /** the columns' widths: the (narrow) symbols, each digit, the right-hand keys */
        const val COLUMN_SYMBOLS = 0.8f
        const val COLUMN_DIGIT = 1.25f
        const val COLUMN_SIDE = 1.1f
    }
}
