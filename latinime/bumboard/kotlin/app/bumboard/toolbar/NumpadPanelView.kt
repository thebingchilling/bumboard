package app.bumboard.toolbar

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
import app.bumboard.bumHost
import app.bumboard.layout.BumPanels
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants

/**
 * A number pad over the keys: digits in a phone-style 3 x 3 grid with 0 below, operators on
 * the left, and backspace, space, Enter and ABC on the right.
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
        addRow(text("+"), digit("1"), digit("2"), digit("3"), icon(R.drawable.ic_sym_backspace, R.string.bum_emoji_delete, repeats = true) { send(Constants.CODE_DELETE) })
        addRow(text("-"), digit("4"), digit("5"), digit("6"), spaceKey())
        addRow(text("*"), digit("7"), digit("8"), digit("9"), enterKey())
        addRow(text("/"), text(","), digit("0"), text("."), word(R.string.bum_emoji_abc) { visibility = GONE })
        visibility = GONE
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE) repeat.removeCallbacksAndMessages(null)
        super.setVisibility(visibility)
    }

    private fun addRow(vararg keys: View) {
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            keys.forEachIndexed { i, key ->
                // the digit columns are a little wider, like a phone keypad
                val weight = if (i in 1..3) 1.2f else 1f
                addView(key, LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun digit(d: String) = key(d, letter = true) { bumHost()?.bumSendCode(d[0].code) }

    private fun text(t: String) = key(t, letter = false) { bumHost()?.bumSendCode(t[0].code) }

    private fun word(label: Int, action: () -> Unit) = key(context.getString(label), letter = false, size = 15f, action = action)

    private fun spaceKey() = icon(R.drawable.ic_sym_space_bar, R.string.bum_numpad_space) { send(Constants.CODE_SPACE) }

    private fun enterKey() = ImageView(context).apply {
        setImageResource(R.drawable.ic_sym_keyboard_return)
        imageTintList = ColorStateList.valueOf(palette.onPrimary)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.bum_numpad_enter)
        background = context.getDrawable(R.drawable.bum_m3_key_popup_action)
        setOnClickListener { send(Constants.CODE_ENTER) }
    }

    private fun key(label: String, letter: Boolean, size: Float = 22f, action: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(if (letter) palette.onSurface else palette.onSecondaryContainer)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = if (letter) Typeface.DEFAULT else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        background = if (letter) BumPanels.letterKey(context) else BumPanels.functionalKey(context)
        setOnClickListener { action() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun icon(res: Int, label: Int, repeats: Boolean = false, action: () -> Unit) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(palette.onSecondaryContainer)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(label)
        background = BumPanels.functionalKey(context)
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

    private fun send(code: Int) = bumHost()?.bumSendCode(code)

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
