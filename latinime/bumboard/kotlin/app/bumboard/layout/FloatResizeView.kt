package app.bumboard.layout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import app.bumboard.bumHost
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.utils.ResourceUtils

/**
 * Resizing the floating keyboard with handles on the keyboard itself: drag the top edge for
 * the height, either side for the width; the outline shows the new size as you drag. Done
 * applies it (the keys are rebuilt at the new size), Back leaves it as it was.
 *
 * It covers the whole input view while open, so the outline can grow past the keyboard; the
 * keyboard's current bounds come from [show].
 */
class FloatResizeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
        color = palette.primary
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.primary and 0x00FFFFFF or 0x22000000 }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.primary }
    /** The keyboard's bounds when resizing started, and the outline being dragged. */
    private val start = RectF()
    private val outline = RectF()
    private val buttons = FrameLayout(context)
    private var dragging = NONE
    private var downX = 0f
    private var downY = 0f
    private var keysHeight = 0

    init {
        setWillNotDraw(false)
        isClickable = true
        buttons.addView(BumPanels.backButton(context) { cancel() },
            LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.START))
        buttons.addView(TextView(context).apply {
            setText(R.string.bum_resize_done)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(palette.onPrimary)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(10), dp(24), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(palette.primary)
            }
            setOnClickListener { visibility = GONE }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(buttons)
        visibility = GONE
    }

    /**
     * Opens over [frame] (the floating keyboard frame); [keys] is its keyboard view, whose
     * height is what the height setting scales.
     */
    fun show(frame: View, keys: View) {
        if (visibility != VISIBLE) remember()
        this.frame = frame
        this.keys = keys
        // both are children of the input view, so the frame's layout bounds are ours too
        start.set(frame.left.toFloat(), frame.top.toFloat(), frame.right.toFloat(), frame.bottom.toFloat())
        outline.set(start)
        openedHeight = start.height()
        keysHeight = keys.height
        visibility = VISIBLE
        placeButtons()
    }

    private fun placeButtons() {
        val lp = buttons.layoutParams as LayoutParams
        lp.width = outline.width().toInt()
        lp.height = outline.height().toInt()
        lp.leftMargin = outline.left.toInt()
        lp.topMargin = outline.top.toInt()
        lp.gravity = Gravity.TOP or Gravity.START
        buttons.layoutParams = lp
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val r = dp(20).toFloat()
        canvas.drawRoundRect(outline, r, r, fill)
        canvas.drawRoundRect(outline, r, r, stroke)
        val long = dp(40).toFloat()
        val thick = dp(6).toFloat()
        val cx = outline.centerX()
        val cy = outline.centerY()
        // top, left and right handles
        canvas.drawRoundRect(cx - long / 2, outline.top - thick / 2, cx + long / 2, outline.top + thick / 2, thick, thick, handle)
        canvas.drawRoundRect(outline.left - thick / 2, cy - long / 2, outline.left + thick / 2, cy + long / 2, thick, thick, handle)
        canvas.drawRoundRect(outline.right - thick / 2, cy - long / 2, outline.right + thick / 2, cy + long / 2, thick, thick, handle)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val grab = dp(32)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = when {
                    kotlin.math.abs(event.y - outline.top) < grab && event.x in outline.left..outline.right -> TOP
                    kotlin.math.abs(event.x - outline.left) < grab && event.y in outline.top..outline.bottom -> LEFT
                    kotlin.math.abs(event.x - outline.right) < grab && event.y in outline.top..outline.bottom -> RIGHT
                    else -> NONE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val minW = ResourceUtils.getDefaultKeyboardWidth(context) * BumLayout.MIN_FLOAT_WIDTH
                val minH = openedHeight - keysHeight * (1 - MIN_KEYS / currentScale())
                when (dragging) {
                    TOP -> outline.top = (start.top + event.y - downY).coerceIn(0f, outline.bottom - minH)
                    LEFT -> outline.left = (start.left + event.x - downX).coerceIn(0f, outline.right - minW)
                    RIGHT -> outline.right = (start.right + event.x - downX).coerceIn(outline.left + minW, width.toFloat())
                }
                if (dragging != NONE) placeButtons()
            }
            MotionEvent.ACTION_UP -> {
                // let go of a handle: the keyboard takes the new size now
                if (dragging != NONE) apply()
                dragging = NONE
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = NONE
                outline.set(start)
                placeButtons()
            }
        }
        return true
    }

    private fun currentScale() = BumLayout.get(context).heightScale

    private var frame: View? = null
    private var keys: View? = null
    /** The size and place before resizing started, for Back. */
    private var before: FloatArray? = null

    private fun remember() {
        val layout = BumLayout.get(context)
        before = floatArrayOf(layout.heightScale, layout.floatWidth, layout.floatX.toFloat(), layout.floatY.toFloat())
    }

    /** Back: the size and place from before resizing. */
    private fun cancel() {
        before?.let { b ->
            BumLayout.get(context).edit()
                .putFloat(BumLayout.PREF_HEIGHT_SCALE, b[0])
                .putFloat(BumLayout.PREF_FLOAT_WIDTH, b[1])
                .putInt(BumLayout.PREF_FLOAT_X, b[2].toInt())
                .putInt(BumLayout.PREF_FLOAT_Y, b[3].toInt())
                .apply()
        }
        visibility = GONE
        bumHost()?.bumReloadLayout()
    }

    /**
     * Saves the outline as the floating keyboard's width, height and position and rebuilds the
     * keys at that size; the handles then reopen on the resized keyboard.
     */
    private fun apply() {
        val layout = BumLayout.get(context)
        val full = ResourceUtils.getDefaultKeyboardWidth(context)
        // the extra height all goes to the keys; the bar and handle keep theirs
        val grown = outline.height() - openedHeight
        val scale = (layout.heightScale * (keysHeight + grown) / keysHeight)
            .coerceIn(BumLayout.MIN_HEIGHT, BumLayout.MAX_HEIGHT)
        layout.edit()
            .putFloat(BumLayout.PREF_HEIGHT_SCALE, scale)
            .putFloat(BumLayout.PREF_FLOAT_WIDTH, (outline.width() / full).coerceIn(BumLayout.MIN_FLOAT_WIDTH, 1f))
            .putInt(BumLayout.PREF_FLOAT_X, outline.left.toInt())
            .putInt(BumLayout.PREF_FLOAT_Y, ((parent as View).height - outline.bottom).toInt())
            .apply()
        val host = bumHost() ?: return
        val frame = frame ?: return
        val keys = keys ?: return
        val saved = before
        host.bumReloadLayout() // hides the handles with the other panels
        // back on the keyboard once it has been laid out at its new size
        frame.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                frame.viewTreeObserver.removeOnGlobalLayoutListener(this)
                show(frame, keys)
                before = saved
            }
        })
    }

    /** The frame's height when resizing opened. */
    private var openedHeight = 0f

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        const val NONE = 0
        const val TOP = 1
        const val LEFT = 2
        const val RIGHT = 3
        /** Keys never shrink below this share of their standard height. */
        const val MIN_KEYS = BumLayout.MIN_HEIGHT
    }
}
