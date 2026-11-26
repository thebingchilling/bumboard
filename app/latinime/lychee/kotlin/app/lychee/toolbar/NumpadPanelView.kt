package app.lychee.toolbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
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
 * The one number pad Lychee has, in number, phone and date fields ([forced]; LatinIME's own pads
 * never show, and the symbols page has no number pad key): the digits, centred, with no keys at
 * the sides, over a bottom row of three equal keys, backspace, 0 and Enter. Symbols are held on
 * 0, like a key's alternatives: what the field takes first ([setInputType]: − for signed numbers,
 * a decimal point for decimals, / and : for dates and times, + * # for phone numbers), then the
 * others a number may need; hold, slide onto one and let go. A small mark in 0's corner shows
 * there are some.
 */
class NumpadPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    private val repeat = Handler(Looper.getMainLooper())
    // before init, which fills them in
    private var plain: LinearLayout? = null
    private var plainBar: LinearLayout? = null
    private val bottomBars get() = listOfNotNull(plainBar)
    /** The number field's extra keys: what is shown, and what it types. */
    private var extras: List<Pair<String, Char>> = emptyList()
    /** The mark in the number field's 0 showing what is held on it. */
    private var zeroHint: TextView? = null

    init {
        orientation = VERTICAL
        isClickable = true
        setBackgroundColor(palette.surface)
        setPadding(dp(3), dp(4), dp(3), dp(4))
        plain = plainPad()
        addView(plain, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    /**
     * The number field's pad: the digits over ⌫ 0 Enter (its extras held on 0), centred, with space at each side.
     */
    private fun plainPad() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        // a little room at each side (narrower than the full pad's columns): the digits a touch wider
        val side = 0.6f
        addView(View(context), LayoutParams(0, LayoutParams.MATCH_PARENT, side))
        val block = LinearLayout(context).apply { orientation = VERTICAL }
        block.addView(digitRows(), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        bar.addView(backspaceKey(), keyParams(1f))
        bar.addView(zeroKey(), keyParams(1f))
        bar.addView(enterKey(), keyParams(1f))
        block.addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))
        plainBar = bar
        addView(block, LayoutParams(0, LayoutParams.MATCH_PARENT, COLUMN_DIGIT * 3))
        addView(View(context), LayoutParams(0, LayoutParams.MATCH_PARENT, side))
    }

    /**
     * The number field's kind ([android.text.InputType]): what is held on its 0. A plain number
     * has nothing; signed numbers −, decimals a point, dates /, times :, dates with times both,
     * phone numbers + * #.
     */
    fun setInputType(inputType: Int) {
        val cls = inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        val first: List<Pair<String, Char>> = when (cls) {
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
        // what the field takes first, then the other symbols a number may need
        extras = (first + MORE).distinctBy { it.second }
        zeroHint?.text = extras.take(3).joinToString("") { it.first }
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
            // beside the 0 and easy to read, so holding it is plainly worth doing
            setTextColor(palette.onSurface)
            alpha = 0.72f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = app.lychee.fonts.LycheeFonts.ui(context, 600)
            letterSpacing = 0.08f
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
                MotionEvent.ACTION_MOVE -> if (choices != null) pickAt(e.rawX, e.rawY)
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
                Gravity.CENTER_VERTICAL or Gravity.END).apply { setMargins(0, 0, dp(10), 0) })
        }
    }

    private fun showChoices(key: View) {
        hideChoices()
        // rows of up to five, the field's own symbols first (bottom row, nearest the finger)
        val perRow = PER_ROW.coerceAtMost(extras.size)
        val grid = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(palette.surfaceContainerHigh)
            }
            elevation = dp(6).toFloat()
        }
        val w = key.width.coerceAtMost(dp(56))
        val h = (key.height - dp(8)).coerceAtMost(dp(52))
        val rows = extras.chunked(perRow)
        val views = ArrayList<TextView>()
        rows.forEach { row ->
            val line = LinearLayout(context).apply { orientation = HORIZONTAL }
            row.forEach { (label, _) ->
                line.addView(TextView(context).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    typeface = app.lychee.fonts.LycheeFonts.ui(context, 400)
                    gravity = Gravity.CENTER
                }.also { views += it }, LayoutParams(w, h))
            }
            // the first row at the bottom
            grid.addView(line, 0)
        }
        choiceViews = views
        chosen = 0
        paintChoices()
        val popup = android.widget.PopupWindow(grid, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false).apply {
            isClippingEnabled = false
            elevation = dp(6).toFloat()
        }
        val at = IntArray(2).also { key.getLocationInWindow(it) }
        val width = w * perRow + dp(8)
        val height = h * rows.size + dp(8)
        val x = (at[0] + key.width / 2 - width / 2).coerceAtLeast(0)
        val y = at[1] - height - dp(4)
        popup.showAtLocation(key, Gravity.NO_GRAVITY, x, y)
        choices = popup
        // grown out of the key that was held
        LycheeMotion.cardIn(grid, width / 2f, height.toFloat())
    }

    /** Picks the choice under the finger (by where it is on the screen). */
    private fun pickAt(rawX: Float, rawY: Float) {
        val i = choiceViews.indexOfFirst { v ->
            val at = IntArray(2).also { v.getLocationOnScreen(it) }
            rawX >= at[0] && rawX < at[0] + v.width && rawY >= at[1] && rawY < at[1] + v.height
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

    /**
     * The bottom bar's keys exactly where the keyboard's bottom row keys are: as tall, and as
     * high above the bottom. Each keyboard row's gap is below its keys (LatinIME's Key), so the
     * bottom keys stand the gap plus the keyboard's padding above the bottom; the rows above
     * share the rest.
     */
    private fun alignWithKeyboard() {
        val row = LycheePanels.keyRow(this)
        val margin = dp(3)
        // the bar's keys keep the 3 dp margin the pad's other keys have; the gap below them
        // is made up by the pad's own padding
        val bottom = (row.bottomPadding + row.gap - margin).coerceAtLeast(0)
        if (paddingBottom != bottom) setPadding(paddingLeft, paddingTop, paddingRight, bottom)
        val height = row.height - row.gap + margin * 2
        for (bar in bottomBars) {
            val lp = bar.layoutParams as LayoutParams
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

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE) {
            repeat.removeCallbacksAndMessages(null)
            hideChoices()
        }
        super.setVisibility(visibility)
        if (visibility == VISIBLE) alignWithKeyboard()
    }

    private fun keyParams(weight: Float) =
        LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            .apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }

    private fun backspaceKey() = icon(R.drawable.ic_sym_backspace, R.string.lychee_emoji_delete, repeats = true) {
        send(Constants.CODE_DELETE)
    }

    private fun digit(d: String) = key(d, letter = true, size = 26f) { lycheeHost()?.lycheeSendCode(d[0].code) }

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
        /** each digit column's width, against the side room */
        const val COLUMN_DIGIT = 1.25f
        /** Held on a number field's 0 after the field's own: a row's worth at a time. */
        const val PER_ROW = 5
        val MORE = listOf("\u2212" to '-', "." to '.', "," to ',', "+" to '+', "/" to '/',
            ":" to ':', "*" to '*', "#" to '#', "(" to '(', ")" to ')')
    }
}
