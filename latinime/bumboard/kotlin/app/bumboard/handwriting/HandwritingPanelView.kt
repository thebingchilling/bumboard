package app.bumboard.handwriting

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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.bumboard.bumHost
import app.bumboard.fonts.BumFonts
import app.bumboard.layout.BumPanels
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants
import java.util.concurrent.Executors

/**
 * Handwriting over the keys, like Gboard's: write a character anywhere on the pad; a moment
 * after the last stroke the best guess is typed and the pad clears for the next one, so
 * characters can be written one after another. The guesses stay above the pad: tap one to
 * replace what was typed. ⌫ takes back the last stroke while writing, otherwise deletes.
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
    private val candidates = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val message = TextView(context).apply {
        setTextColor(palette.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        setPadding(dp(24), 0, dp(24), 0)
    }
    private var zinnia: Zinnia? = null
    /** What the last automatic commit typed, so a tapped candidate can replace it. */
    private var lastTyped: String? = null
    private var guesses: List<String> = emptyList()

    init {
        orientation = VERTICAL
        isClickable = true
        setBackgroundColor(palette.surface)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(candidates)
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        addView(FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(palette.surfaceContainer)
            }
            addView(pad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(dp(8), 0, dp(8), dp(4)) })
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(BumPanels.backButton(context) { this@HandwritingPanelView.visibility = GONE }, LayoutParams(dp(56), LayoutParams.MATCH_PARENT))
            addView(key(null, R.string.bum_hw_space, isSpace = true) { space() }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(key(R.drawable.ic_sym_backspace, R.string.bum_emoji_delete) { backspace() }, LayoutParams(dp(72), LayoutParams.MATCH_PARENT))
            addView(key(R.drawable.ic_sym_keyboard_return, R.string.bum_hw_enter) { commitPending(); bumHost()?.bumSendCode(Constants.CODE_ENTER) },
                LayoutParams(dp(72), LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)).apply { setMargins(dp(4), 0, dp(4), dp(4)) })
        visibility = GONE
    }

    fun show() {
        visibility = VISIBLE
        pad.clear()
        lastTyped = null
        showGuesses(emptyList())
        release()
        val model = Zinnia.modelFor(context, bumHost()?.bumEngine())
        zinnia = model?.let { Zinnia.open(it) }
        message.visibility = if (zinnia == null) VISIBLE else GONE
        message.text = context.getString(if (model == null) R.string.bum_hw_no_pack else R.string.bum_hw_bad_pack)
        message.setOnClickListener { bumHost()?.bumOpenSettings("Packs") }
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE && this.visibility == VISIBLE) {
            commitPending()
            main.removeCallbacksAndMessages(null)
            release()
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
        val strokes = pad.strokes()
        val w = pad.width
        val h = pad.height
        if (strokes.isEmpty() || w <= 0 || h <= 0) return
        worker.execute {
            val result = runCatching { z.classify(w, h, strokes) }.getOrDefault(emptyList())
            main.post { if (pad.hasInk) showGuesses(result) }
        }
    }

    /** Types the best guess for the ink on the pad and clears it for the next character. */
    private fun commitPending() {
        main.removeCallbacks(autoCommit)
        if (!pad.hasInk) return
        val best = guesses.firstOrNull()
        pad.clear()
        if (best != null) {
            bumHost()?.bumType(best)
            lastTyped = best
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
                repeat(previous.codePointCount(0, previous.length)) { bumHost()?.bumSendCode(Constants.CODE_DELETE) }
            }
        }
        bumHost()?.bumType(text)
        lastTyped = text
    }

    private fun space() {
        commitPending()
        lastTyped = null
        bumHost()?.bumSendCode(Constants.CODE_SPACE)
    }

    private fun backspace() {
        if (pad.hasInk) {
            pad.undo()
            if (pad.hasInk) recognise() else showGuesses(emptyList())
            return
        }
        lastTyped = null
        showGuesses(emptyList())
        bumHost()?.bumSendCode(Constants.CODE_DELETE)
    }

    private fun showGuesses(list: List<String>) {
        guesses = list
        candidates.removeAllViews()
        list.forEachIndexed { i, g ->
            candidates.addView(TextView(context).apply {
                text = g
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(if (i == 0) palette.primary else palette.onSurface)
                BumFonts.chinese(context)?.let { typeface = it }
                gravity = Gravity.CENTER
                minWidth = dp(48)
                setPadding(dp(10), 0, dp(10), 0)
                background = context.getDrawable(R.drawable.bum_m3_suggestion)
                setOnClickListener { pick(g) }
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
    }

    // ------------------------------------------------------------------ views

    private fun key(icon: Int?, label: Int, isSpace: Boolean = false, onClick: () -> Unit): View {
        val view: View = if (icon != null) ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(palette.onSecondaryContainer)
            scaleType = ImageView.ScaleType.CENTER
        } else TextView(context).apply {
            setText(label)
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        view.contentDescription = context.getString(label)
        view.background = if (isSpace) BumPanels.letterKey(context) else BumPanels.functionalKey(context)
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
            strokeWidth = dp(5).toFloat()
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
        val worker = Executors.newSingleThreadExecutor()
    }
}
