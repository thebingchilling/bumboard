package app.lychee.handwriting

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.lychee.lycheeHost
import app.lychee.fonts.LycheeFonts
import app.lychee.layout.LycheePanels
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants
import java.util.concurrent.Executors

/**
 * Handwriting over the keys, like Gboard's: write a character anywhere on the pad; a moment
 * after the last stroke the best guess is typed and the pad clears for the next one, so
 * characters can be written one after another. The guesses show in the candidate bar, with
 * their readings: tap one to replace what was typed. After each character the bar suggests
 * what usually follows it (校, 生, 習 after 學), from the readings dictionary's words.
 * ⌫ takes back the last stroke while writing, otherwise deletes.
 *
 * Two ways in: the Handwriting shortcut opens it over any keyboard (with Back), and the
 * 手寫 Handwriting keyboard ([keyboardMode], subtype extra `LycheeHandwriting`) is this pad in place
 * of the letters, with Gboard's bottom row: ?123, emoji, space, ⌫ and Enter. ?123 shows the
 * symbols underneath; ABC there brings the pad back.
 *
 * Recognition is Zinnia with a handwriting pack's model, opened only while the pane is open.
 */
class HandwritingPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    private val main = Handler(Looper.getMainLooper())
    private val pad = Pad()
    /** "Write here", faint in the middle of the empty pad. */
    private val hint = TextView(context).apply {
        setText(R.string.lychee_hw_write_here)
        setTextColor(palette.onSurfaceVariant)
        alpha = 0.7f
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        visibility = GONE
    }
    private val bottom = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val message = TextView(context).apply {
        setTextColor(palette.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        setPadding(dp(24), 0, dp(24), 0)
    }
    private var zinnia: ZinniaSet? = null
    /** What the last automatic commit typed, so a tapped candidate can replace it. */
    private var lastTyped: String? = null
    private var guesses: List<String> = emptyList()

    init {
        orientation = VERTICAL
        isClickable = true
        setBackgroundColor(palette.surface)
        // the guesses show in the candidate bar above (lycheeShowGuesses), so the pad gets the room
        // the pad has no background of its own: write straight on the keyboard
        addView(FrameLayout(context).apply {
            addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(pad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(dp(8), dp(6), dp(8), dp(4)) })
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)).apply { setMargins(dp(4), 0, dp(4), dp(4)) })
        buildBottomRow()
        visibility = GONE
    }

    /** True on the 手寫 Handwriting keyboard: the pad stands in for the letters. */
    var keyboardMode = false
        set(value) {
            if (field == value) return
            field = value
            buildBottomRow()
            if (!value && visibility == VISIBLE) visibility = GONE
            follow()
        }

    private fun buildBottomRow() {
        bottom.removeAllViews()
        if (keyboardMode) {
            // Gboard's handwriting row: ?123, emoji, space, ⌫, Enter
            bottom.addView(key(null, R.string.lychee_hw_symbols, pill = true) {
                commitPending()
                lycheeHost()?.lycheeSendCode(Constants.CODE_SWITCH_ALPHA_SYMBOL)
            }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.5f))
            bottom.addView(key(R.drawable.ic_sym_mood, R.string.lychee_sc_emoji) {
                commitPending()
                lycheeHost()?.lycheeSendCode(Constants.CODE_EMOJI)
            }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            bottom.addView(key(null, R.string.lychee_hw_space_label, isSpace = true) { space() }, LayoutParams(0, LayoutParams.MATCH_PARENT, 4f))
        } else {
            bottom.addView(LycheePanels.backButton(context) { this@HandwritingPanelView.visibility = GONE }, LayoutParams(dp(56), LayoutParams.MATCH_PARENT))
            bottom.addView(key(null, R.string.lychee_hw_space, isSpace = true) { space() }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        val weight = if (keyboardMode) 1.25f else 0f
        bottom.addView(key(R.drawable.ic_sym_backspace, R.string.lychee_emoji_delete) { backspace() },
            if (keyboardMode) LayoutParams(0, LayoutParams.MATCH_PARENT, weight) else LayoutParams(dp(72), LayoutParams.MATCH_PARENT))
        bottom.addView(key(R.drawable.ic_sym_keyboard_return, R.string.lychee_hw_enter, enter = true) {
            commitPending(); lycheeHost()?.lycheeSendCode(Constants.CODE_ENTER)
        }, if (keyboardMode) LayoutParams(0, LayoutParams.MATCH_PARENT, weight) else LayoutParams(dp(72), LayoutParams.MATCH_PARENT))
    }

    private val layoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener { follow() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        super.onDetachedFromWindow()
    }

    /**
     * On the Handwriting keyboard: covers the letters exactly while they show (not the symbols,
     * the emoji panel or another panel).
     */
    private fun follow() {
        if (!keyboardMode) return
        val root = parent as? View ?: return
        val keys = root.findViewById<View>(R.id.keyboard_view)
        val letters = com.android.inputmethod.keyboard.KeyboardSwitcher.getInstance().keyboard?.mId?.isAlphabetKeyboard == true
        val show = keys != null && keys.isShown && keys.height > 0 && letters && !LycheePanels.anyOtherShown(root, this)
        if (!show) {
            if (visibility == VISIBLE) visibility = GONE
            return
        }
        LycheePanels.cover(this, root)
        if (visibility != VISIBLE) show()
    }

    fun show() {
        visibility = VISIBLE
        pad.clear()
        updateHint()
        lastTyped = null
        showGuesses(emptyList())
        release()
        app.lychee.chinese.Readings.open(context)
        val models = Zinnia.modelsFor(context, lycheeHost()?.lycheeEngine())
        zinnia = Zinnia.openAll(models)
        message.visibility = if (zinnia == null) VISIBLE else GONE
        updateHint()
        message.text = context.getString(if (models.isEmpty()) R.string.lychee_hw_no_pack else R.string.lychee_hw_bad_pack)
        message.setOnClickListener { lycheeHost()?.lycheeOpenSettings("Packs") }
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE && this.visibility == VISIBLE) {
            commitPending()
            main.removeCallbacksAndMessages(null)
            release()
            guesses = emptyList()
            lycheeHost()?.lycheeShowGuesses(emptyList()) {}
        }
        super.setVisibility(visibility)
    }

    /** Closes the model on the recognition thread, after any recognition still running. */
    private fun release() {
        val z = zinnia ?: return
        zinnia = null
        worker.execute { z.close() }
    }

    // ------------------------------------------------------------------ recognition

    private fun recognise() {
        val z = zinnia ?: return
        val strokes = normalise(pad.strokes())
        if (strokes.isEmpty()) return
        worker.execute {
            val result = runCatching { z.classify(BOX, BOX, strokes) }.getOrDefault(emptyList())
            main.post { if (pad.hasInk) showGuesses(result) }
        }
    }

    /**
     * The ink as Zinnia's models expect it: the character alone, centred in a square [BOX]
     * (the pad is much wider than a character, and Zinnia scales x and y by the box it is
     * given), with the points thinned out like the models' training strokes.
     */
    private fun normalise(strokes: FloatArray): FloatArray {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var i = 0
        while (i + 1 < strokes.size) {
            val x = strokes[i]; val y = strokes[i + 1]
            if (x >= 0) {
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
            }
            i += 2
        }
        if (minX > maxX) return FloatArray(0)
        // a little margin; a flat stroke (一) keeps its shape instead of being stretched
        val side = maxOf(maxX - minX, maxY - minY, dp(24).toFloat()) * 1.15f
        val scale = BOX / side
        val offX = (side - (maxX - minX)) / 2 - minX
        val offY = (side - (maxY - minY)) / 2 - minY
        val minStep = BOX * 0.025f
        val out = ArrayList<Float>(strokes.size)
        var lastX = -1f; var lastY = -1f
        var pendingX = -1f; var pendingY = -1f
        i = 0
        while (i + 1 < strokes.size) {
            val x = strokes[i]; val y = strokes[i + 1]
            if (x < 0) {
                // always keep a stroke's end
                if (pendingX >= 0) { out += pendingX; out += pendingY }
                out += -1f; out += -1f
                lastX = -1f; pendingX = -1f
            } else {
                val nx = (x + offX) * scale
                val ny = (y + offY) * scale
                if (lastX < 0 || kotlin.math.hypot(nx - lastX, ny - lastY) >= minStep) {
                    out += nx; out += ny
                    lastX = nx; lastY = ny
                    pendingX = -1f
                } else {
                    pendingX = nx; pendingY = ny
                }
            }
            i += 2
        }
        return out.toFloatArray()
    }

    /** Types the best guess for the ink on the pad and clears it for the next character. */
    private fun commitPending() {
        main.removeCallbacks(autoCommit)
        if (!pad.hasInk) return
        val best = guesses.firstOrNull()
        pad.clear()
        if (best != null) {
            lycheeHost()?.lycheeType(best)
            lastTyped = best
            showNext(best)
        }
    }

    private val autoCommit = Runnable { commitPending() }

    /** A tapped guess: replaces what was just typed, or types it when still writing. */
    private fun pick(text: String) {
        if (pad.hasInk) {
            main.removeCallbacks(autoCommit)
            pad.clear()
        } else {
            val previous = lastTyped
            if (previous != null) {
                repeat(previous.codePointCount(0, previous.length)) { lycheeHost()?.lycheeSendCode(Constants.CODE_DELETE) }
            }
        }
        lycheeHost()?.lycheeType(text)
        lastTyped = text
        showNext(text)
    }

    private fun space() {
        commitPending()
        lastTyped = null
        lycheeHost()?.lycheeSendCode(Constants.CODE_SPACE)
    }

    private fun backspace() {
        if (pad.hasInk) {
            pad.undo()
            if (pad.hasInk) recognise() else showGuesses(emptyList())
            return
        }
        lastTyped = null
        showGuesses(emptyList())
        lycheeHost()?.lycheeSendCode(Constants.CODE_DELETE)
    }

    /** "Write here" while the pad is empty and ready. */
    private fun updateHint() {
        hint.visibility = if (zinnia != null && !pad.hasInk) VISIBLE else GONE
    }

    /** The recogniser's guesses for the ink on the pad, in the candidate bar. */
    private fun showGuesses(list: List<String>) {
        guesses = list
        lycheeHost()?.lycheeShowGuesses(list) { i -> list.getOrNull(i)?.let { pick(it) } }
    }

    /** What usually follows [typed]: tapping one types it and suggests what follows that. */
    private fun showNext(typed: String) {
        val last = typed.codePointBefore(typed.length).let { String(Character.toChars(it)) }
        @Suppress("DEPRECATION")
        val simplified = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .getString(app.lychee.chinese.ChineseInput.PREF_SCRIPT, "traditional") == "simplified"
        val next = runCatching { app.lychee.chinese.Readings.db?.continuations(last, simplified) }.getOrNull().orEmpty()
        lycheeHost()?.lycheeShowGuesses(next) { i ->
            next.getOrNull(i)?.let { text ->
                lycheeHost()?.lycheeType(text)
                lastTyped = null
                showNext(text)
            }
        }
    }

    // ------------------------------------------------------------------ views

    private fun key(
        icon: Int?, label: Int, isSpace: Boolean = false, pill: Boolean = false, enter: Boolean = false,
        onClick: () -> Unit,
    ): View {
        val view: View = if (icon != null) ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(if (enter) palette.onPrimary else palette.onSecondaryContainer)
            scaleType = ImageView.ScaleType.CENTER
        } else TextView(context).apply {
            setText(label)
            setTextColor(if (isSpace) palette.onSurface else palette.onSecondaryContainer)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = app.lychee.fonts.LycheeFonts.ui(context, if (pill) 600 else 500)
            gravity = Gravity.CENTER
        }
        view.contentDescription = context.getString(label)
        view.background = when {
            // Enter and ?123 are pills, like the keyboard's
            enter -> GradientDrawable().apply {
                cornerRadius = dp(100).toFloat()
                setColor(palette.primary)
            }
            pill -> app.lychee.ui.LycheeColorStyle.themed(context).getDrawable(R.drawable.lychee_m3_key_pill_flat)
            isSpace -> LycheePanels.letterKey(context)
            else -> LycheePanels.functionalKey(context)
        }
        view.setOnClickListener { onClick() }
        return FrameLayout(context).apply {
            setPadding(dp(3), dp(3), dp(3), dp(3))
            addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    /** The writing pad: draws the ink and keeps the strokes for recognition. */
    private inner class Pad : View(context) {
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurface
            style = Paint.Style.STROKE
            // a thin pen, like Gboard's
            strokeWidth = resources.displayMetrics.density * 3.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val paths = ArrayList<Path>()
        private val points = ArrayList<FloatArray>()
        private var current: ArrayList<Float>? = null

        val hasInk get() = points.isNotEmpty() || current != null

        fun clear() {
            paths.clear()
            points.clear()
            current = null
            invalidate()
            updateHint()
        }

        fun undo() {
            if (paths.isNotEmpty()) paths.removeAt(paths.lastIndex)
            if (points.isNotEmpty()) points.removeAt(points.lastIndex)
            invalidate()
        }

        /** All strokes as x, y pairs with (-1, -1) after each. */
        fun strokes(): FloatArray {
            val total = points.sumOf { it.size + 2 }
            val out = FloatArray(total)
            var at = 0
            for (s in points) {
                s.copyInto(out, at)
                at += s.size
                out[at++] = -1f
                out[at++] = -1f
            }
            return out
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (zinnia == null) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    hint.visibility = GONE
                    main.removeCallbacks(autoCommit)
                    current = arrayListOf(event.x, event.y)
                    paths += Path().apply { moveTo(event.x, event.y) }
                }
                MotionEvent.ACTION_MOVE -> {
                    val stroke = current ?: return true
                    for (h in 0 until event.historySize) add(stroke, event.getHistoricalX(h), event.getHistoricalY(h))
                    add(stroke, event.x, event.y)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    current?.let { points += it.toFloatArray() }
                    current = null
                    recognise()
                    main.postDelayed(autoCommit, AUTO_COMMIT_MS)
                }
            }
            invalidate()
            return true
        }

        private fun add(stroke: ArrayList<Float>, x: Float, y: Float) {
            stroke += x
            stroke += y
            paths.lastOrNull()?.lineTo(x, y)
        }

        override fun onDraw(canvas: Canvas) {
            for (p in paths) canvas.drawPath(p, ink)
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        /** A pause this long after a stroke types the character. */
        const val AUTO_COMMIT_MS = 750L
        /** The square Zinnia sees each character in. */
        const val BOX = 300
        val worker = Executors.newSingleThreadExecutor()
    }
}
