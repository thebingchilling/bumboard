package app.lychee.dict

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * The stroke order's speed: an upright bar with set stops (0.1×, 0.25×, 0.5×, 1×, 2×, fastest
 * at the top), the stop's label beside each; drag the knob or tap a stop, and it settles on the
 * nearest one. [onChange] gets the speed chosen.
 */
class SpeedBar(
    context: Context,
    private val accent: Int,
    private val track: Int,
    private val label: Int,
) : View(context) {

    val stops = floatArrayOf(2f, 1f, 0.5f, 0.25f, 0.1f)
    private val names = arrayOf("2×", "1×", "½×", "¼×", "0.1×")
    var index = 1
        private set
    var onChange: (Float) -> Unit = {}

    private val d = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track; strokeWidth = 4 * d; strokeCap = Paint.Cap.ROUND }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.RIGHT
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 600)
    }
    /** Where the knob is while dragged (between stops), or NaN when resting on one. */
    private var dragY = Float.NaN

    init {
        contentDescription = "Speed"
        isClickable = true
    }

    fun select(speed: Float) {
        index = stops.indices.minByOrNull { kotlin.math.abs(stops[it] - speed) } ?: 1
        invalidate()
    }

    private val top get() = paddingTop + 10 * d
    private val bottom get() = height - paddingBottom - 10 * d
    private val lineX get() = width - paddingRight - 12 * d
    private fun yOf(i: Int) = top + (bottom - top) * i / (stops.size - 1)

    override fun onDraw(canvas: Canvas) {
        canvas.drawLine(lineX, top, lineX, bottom, linePaint)
        for (i in stops.indices) {
            val y = yOf(i)
            canvas.drawCircle(lineX, y, 3 * d, dotPaint)
            textPaint.color = if (i == index && dragY.isNaN()) accent else label
            canvas.drawText(names[i], lineX - 12 * d, y - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
        }
        val knobY = if (dragY.isNaN()) yOf(index) else dragY.coerceIn(top, bottom)
        canvas.drawCircle(lineX, knobY, 8 * d, knobPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // the card's tabs and scrolling leave the drag to the bar
                parent?.requestDisallowInterceptTouchEvent(true)
                dragY = e.y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                dragY = e.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val y = e.y.coerceIn(top, bottom)
                val i = stops.indices.minByOrNull { kotlin.math.abs(yOf(it) - y) } ?: index
                dragY = Float.NaN
                if (i != index) {
                    index = i
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    onChange(stops[i])
                }
                invalidate()
                if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }
}
