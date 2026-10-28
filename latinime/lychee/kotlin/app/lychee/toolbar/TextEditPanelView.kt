package app.lychee.toolbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.lychee.lycheeHost
import app.lychee.layout.LycheePanels
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants

/**
 * Text editing over the keys, laid out like Gboard's: tall ◀ and ▶ either side of ▲, a Select
 * toggle and ▼ (with Select on, the arrows extend the selection), start/end of line under
 * ◀ and ▶, then select all (cut while selecting), copy, paste and delete. Arrows and delete
 * repeat while held.
 */
class TextEditPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    private val repeat = Handler(Looper.getMainLooper())
    private var selecting = false
    private lateinit var selectKey: TextView
    private lateinit var selectAllKey: LinearLayout

    init {
        orientation = HORIZONTAL
        isClickable = true
        setBackgroundColor(palette.surface)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        // left, as Gboard lays it out: tall ◀ and ▶ either side of ▲ / Select / ▼, with start of
        // line, cut and end of line underneath
        val left = LinearLayout(context).apply { orientation = HORIZONTAL }
        selectKey = label(R.string.lychee_edit_select) { toggleSelect() }
        left.addView(column(
            3 to icon(R.drawable.ic_sym_keyboard_arrow_left, R.string.lychee_edit_left, repeats = true) { move(KeyEvent.KEYCODE_DPAD_LEFT) },
            1 to icon(R.drawable.ic_sym_first_page, R.string.lychee_edit_line_start) { move(KeyEvent.KEYCODE_MOVE_HOME) },
        ), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        left.addView(column(
            1 to icon(R.drawable.ic_sym_keyboard_arrow_up, R.string.lychee_edit_up, repeats = true) { move(KeyEvent.KEYCODE_DPAD_UP) },
            2 to selectKey,
            1 to icon(R.drawable.ic_sym_keyboard_arrow_down, R.string.lychee_edit_down, repeats = true) { move(KeyEvent.KEYCODE_DPAD_DOWN) },
        ), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        left.addView(column(
            3 to icon(R.drawable.ic_sym_keyboard_arrow_right, R.string.lychee_edit_right, repeats = true) { move(KeyEvent.KEYCODE_DPAD_RIGHT) },
            1 to icon(R.drawable.ic_sym_last_page, R.string.lychee_edit_line_end) { move(KeyEvent.KEYCODE_MOVE_END) },
        ), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(left, LayoutParams(0, LayoutParams.MATCH_PARENT, 3f))
        // right: select all, copy, paste and delete
        val right = LinearLayout(context).apply { orientation = VERTICAL }
        // select all; cut in its place while selecting
        selectAllKey = labelled(R.drawable.ic_sym_select_all, R.string.lychee_edit_select_all) {
            if (selecting) {
                menu(android.R.id.cut)
                setSelecting(false)
            } else {
                menu(android.R.id.selectAll)
            }
        }
        right.addView(selectAllKey, keyParams())
        right.addView(labelled(R.drawable.ic_sym_content_copy, R.string.lychee_edit_copy) { menu(android.R.id.copy); setSelecting(false) }, keyParams())
        right.addView(labelled(R.drawable.ic_sym_content_paste, R.string.lychee_edit_paste) { menu(android.R.id.paste); setSelecting(false) }, keyParams())
        right.addView(icon(R.drawable.ic_sym_backspace, R.string.lychee_emoji_delete, repeats = true) {
            lycheeHost()?.lycheeSendCode(Constants.CODE_DELETE)
        }, keyParams())
        addView(right, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.4f))
        visibility = GONE
    }

    /** A key with an icon and its name beside it, like Gboard's Select all / Copy / Paste. */
    private fun labelled(res: Int, label: Int, action: () -> Unit) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        addView(ImageView(context).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(palette.onSecondaryContainer)
        })
        addView(TextView(context).apply {
            setText(label)
            setTextColor(palette.onSecondaryContainer)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(8), 0, 0, 0)
        })
        background = LycheePanels.functionalKey(context)
        setOnClickListener { action() }
    }

    private fun toolbar(): ToolbarView? = rootView.findViewById(R.id.lychee_toolbar)

    fun show() {
        setSelecting(false)
        visibility = VISIBLE
        // the bar above shows Back and "Text editing" in place of the suggestions
        toolbar()?.showTitle(R.string.lychee_sc_text_edit) { visibility = GONE }
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE) repeat.removeCallbacksAndMessages(null)
        val wasVisible = this.visibility == VISIBLE
        super.setVisibility(visibility)
        if (wasVisible && visibility != VISIBLE) {
            toolbar()?.let { if (it.mode == ToolbarView.Mode.TITLE) it.mode = ToolbarView.Mode.NORMAL }
        }
    }

    private fun toggleSelect() = setSelecting(!selecting)

    private fun setSelecting(on: Boolean) {
        selecting = on
        anchor = -1
        // on: the primary colour, like a key that is switched on
        selectKey.setTextColor(if (on) palette.onPrimary else palette.onSecondaryContainer)
        selectKey.background = if (on) context.getDrawable(R.drawable.lychee_m3_key_popup_action) else LycheePanels.functionalKey(context)
        if (::selectAllKey.isInitialized) {
            (selectAllKey.getChildAt(0) as ImageView).setImageResource(if (on) R.drawable.ic_sym_content_cut else R.drawable.ic_sym_select_all)
            (selectAllKey.getChildAt(1) as TextView).setText(if (on) R.string.lychee_edit_cut else R.string.lychee_edit_select_all)
        }
    }

    /** Where a selection started (absolute offset), while Select is on; -1 until the first move. */
    private var anchor = -1

    /**
     * Moves the cursor (or, with Select on, the moving end of the selection) by setting the
     * selection on the text field directly; many editors ignore arrow key events from a
     * keyboard. Falls back to key events when the field does not share its text.
     */
    private fun move(keyCode: Int) {
        val ic = lycheeHost()?.lycheeInputConnection() ?: return
        ic.finishComposingText()
        val et = ic.getExtractedText(ExtractedTextRequest(), 0)
        val text = et?.text
        if (et == null || text == null || et.selectionStart < 0) {
            val meta = if (selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            val now = SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
            return
        }
        val start = et.selectionStart
        val end = et.selectionEnd
        if (selecting && anchor < 0) anchor = start
        // the end that moves: with Select on, the one away from the anchor
        val focus = if (selecting) (if (start == anchor) end else start) else when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MOVE_HOME -> minOf(start, end)
            else -> maxOf(start, end)
        }
        // without Select, the first arrow just collapses a selection to its edge
        val collapse = !selecting && start != end &&
            (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        val target = if (collapse) focus else newOffset(text, focus, keyCode)
        if (selecting) ic.setSelection(anchor, target) else ic.setSelection(target, target)
    }

    /** The offset after moving [from] by one step in [keyCode]'s direction. */
    private fun newOffset(text: CharSequence, from: Int, keyCode: Int): Int {
        val lineStart = { at: Int -> text.lastIndexOf('\n', at - 1).let { if (it < 0) 0 else it + 1 } }
        val lineEnd = { at: Int -> text.indexOf('\n', at).let { if (it < 0) text.length else it } }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (from <= 0) 0 else Character.offsetByCodePoints(text, from, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (from >= text.length) text.length else Character.offsetByCodePoints(text, from, 1)
            KeyEvent.KEYCODE_MOVE_HOME -> lineStart(from)
            KeyEvent.KEYCODE_MOVE_END -> lineEnd(from)
            KeyEvent.KEYCODE_DPAD_UP -> {
                val start = lineStart(from)
                if (start == 0) 0 else {
                    val prevStart = lineStart(start - 1)
                    minOf(prevStart + (from - start), start - 1)
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val end = lineEnd(from)
                if (end >= text.length) text.length else {
                    val column = from - lineStart(from)
                    minOf(end + 1 + column, lineEnd(end + 1))
                }
            }
            else -> from
        }
    }

    private fun menu(id: Int) {
        lycheeHost()?.lycheeInputConnection()?.performContextMenuAction(id)
    }

    /** A column of the left grid: each key with how many rows it spans. */
    private fun column(vararg keys: Pair<Int, View>) = LinearLayout(context).apply {
        orientation = VERTICAL
        keys.forEach { (rows, key) ->
            addView(key, LayoutParams(LayoutParams.MATCH_PARENT, 0, rows.toFloat()).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
    }

    /** A single key in the right column. */
    private fun keyParams() = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }

    private fun icon(res: Int, label: Int, repeats: Boolean = false, action: () -> Unit) = FrameLayout(context).apply {
        addView(ImageView(context).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(palette.onSecondaryContainer)
            scaleType = ImageView.ScaleType.CENTER
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        contentDescription = context.getString(label)
        background = LycheePanels.functionalKey(context)
        press(this, repeats, action)
    }

    private fun label(text: Int, action: () -> Unit) = TextView(context).apply {
        setText(text)
        setTextColor(palette.onSecondaryContainer)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
        gravity = Gravity.CENTER
        background = LycheePanels.functionalKey(context)
        press(this, false, action)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun press(view: View, repeats: Boolean, action: () -> Unit) {
        if (!repeats) {
            view.setOnClickListener { action() }
            return
        }
        view.setOnTouchListener { v, event ->
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

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
