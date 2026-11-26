package app.lychee.dict

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.PathParser

/**
 * One character written stroke by stroke, like a copybook: the strokes appear in order, each
 * drawn along its centre line, over a faint copy of the whole character on a 米 grid.
 * Tap to write it again (or [pause], [resume], [step] stroke by stroke, and set the [speed]: the
 * card's Stroke order page). [current] colours the stroke being written; with [numbers] set (a
 * colour), each stroke's number sits at its start once the character is written (up to
 * [MAX_NUMBERED] strokes), so it reads as a lesson rather than another copy of the word.
 */
class StrokeOrderView @JvmOverloads constructor(
    context: Context,
    private val char: CharStrokes,
    private val ink: Int,
    private val ghost: Int,
    private val grid: Int,
    private val current: Int = ink,
    private val numbers: Int = 0,
    /** Starts writing as soon as it shows. */
    private val autoPlay: Boolean = true,
) : View(context) {

    private val outlines: List<Path> = char.strokes.map { runCatching { PathParser.createPathFromPathData(it) }.getOrElse { Path() } }
    /** Japanese and Korean strokes have only centre lines: drawn with the broad brush. */
    private val linesOnly = outlines.isEmpty()
    private val centres: List<Path> = char.medians.map { pts ->
        Path().apply {
            pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
        }
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 140f
        color = ink
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = grid
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(24f, 20f), 0f)
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = numbers
        textAlign = Paint.Align.CENTER
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 600)
    }
    private val numberBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val matrix = Matrix()
    private val partial = Path()
    /** strokes done so far, with the fraction of the next one */
    private var progress = 0f
    private var animator: ValueAnimator? = null

    init {
        // centre lines alone: a pen about as broad as the Chinese strokes' outlines
        if (linesOnly) brush.strokeWidth = 78f
        setOnClickListener { play() }
        contentDescription = char.char
    }

    /** How many strokes the character has. */
    val strokeCount: Int get() = if (linesOnly) centres.size else outlines.size

    /** How fast it writes: 1 is the usual pace, 2 twice as fast, 0.5 half. */
    var speed = 1f
        set(value) {
            field = value.coerceIn(0.25f, 4f)
            // carries on from where it is, at the new pace
            if (playing) resume()
        }

    /** Whether it is writing now. */
    val playing: Boolean get() = animator?.isRunning == true

    /** The strokes written so far (whole ones). */
    val strokesDone: Int get() = progress.toInt().coerceIn(0, strokeCount)

    /** Called whenever it starts, stops or moves a stroke (for the controls). */
    var onChange: () -> Unit = {}

    /** Writes it from the first stroke. */
    fun play() {
        progress = 0f
        resume()
    }

    /** Carries on writing from where it is (from the start once it is all written). */
    fun resume() {
        animator?.cancel()
        val n = strokeCount
        if (n == 0) return
        if (progress >= n) progress = 0f
        val from = progress
        animator = ValueAnimator.ofFloat(from, n.toFloat()).apply {
            duration = ((n - from) * STROKE_MS / speed).toLong().coerceAtLeast(1)
            interpolator = LinearInterpolator()
            addUpdateListener {
                val before = progress.toInt()
                progress = it.animatedValue as Float
                invalidate()
                if (progress.toInt() != before) onChange()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = onChange()
            })
            start()
        }
        onChange()
    }

    /** Stops where it is. */
    fun pause() {
        animator?.cancel()
        animator = null
        onChange()
    }

    /** Stops and shows [delta] whole strokes more (or fewer): writing it by hand, stroke by stroke. */
    fun step(delta: Int) {
        animator?.cancel()
        animator = null
        val whole = if (delta < 0 && progress > progress.toInt()) progress.toInt() + 1 else progress.toInt()
        progress = (whole + delta).coerceIn(0, strokeCount).toFloat()
        invalidate()
        onChange()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoPlay) play() else { progress = strokeCount.toFloat(); invalidate() }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas) {
        val s = width / 1024f
        canvas.save()
        canvas.scale(s, s)
        // 米 grid
        canvas.drawRect(8f, 8f, 1016f, 1016f, gridPaint)
        canvas.drawLine(512f, 0f, 512f, 1024f, gridPaint)
        canvas.drawLine(0f, 512f, 1024f, 512f, gridPaint)
        canvas.drawLine(0f, 0f, 1024f, 1024f, gridPaint)
        canvas.drawLine(1024f, 0f, 0f, 1024f, gridPaint)
        // Make Me a Hanzi's coordinates: y up, baseline at 900
        matrix.setScale(1f, -1f)
        matrix.postTranslate(0f, 900f)
        canvas.concat(matrix)
        val done = progress.toInt()
        // within a stroke: a short rest first, then the brush eases in and out, so one stroke
        // flows into the next instead of jumping straight on
        val local = eased(progress - done)
        if (linesOnly) {
            centres.forEachIndexed { i, centre ->
                when {
                    i < done -> { brush.color = ink; canvas.drawPath(centre, brush) }
                    else -> {
                        brush.color = ghost
                        canvas.drawPath(centre, brush)
                        if (i == done && progress > done) {
                            val measure = PathMeasure(centre, false)
                            partial.reset()
                            measure.getSegment(0f, measure.length * local, partial, true)
                            brush.color = current
                            canvas.drawPath(partial, brush)
                        }
                    }
                }
            }
            canvas.restore()
            drawNumbers(canvas, s)
            return
        }
        outlines.forEachIndexed { i, outline ->
            when {
                i < done -> {
                    fill.color = ink
                    canvas.drawPath(outline, fill)
                }
                i == done && progress > done -> {
                    fill.color = ghost
                    canvas.drawPath(outline, fill)
                    // the stroke so far: a broad brush along its centre line, kept inside its outline
                    val measure = PathMeasure(centres.getOrNull(i) ?: return@forEachIndexed, false)
                    partial.reset()
                    measure.getSegment(0f, measure.length * local, partial, true)
                    canvas.save()
                    canvas.clipPath(outline)
                    brush.color = current
                    canvas.drawPath(partial, brush)
                    brush.color = ink
                    canvas.restore()
                }
                else -> {
                    fill.color = ghost
                    canvas.drawPath(outline, fill)
                }
            }
        }
        canvas.restore()
        drawNumbers(canvas, s)
    }

    /** A stroke's share of its time (0..1) to how much of it is drawn: a rest, then smoothstep. */
    private fun eased(t: Float): Float {
        val x = ((t - REST) / (1f - REST)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** Each stroke's number at its start, once every stroke is written. */
    private fun drawNumbers(canvas: Canvas, s: Float) {
        val n = centres.size
        if (numbers == 0 || n == 0 || n > MAX_NUMBERED || progress < n) return
        numberPaint.textSize = width * 0.13f
        numberBack.color = androidx.core.graphics.ColorUtils.setAlphaComponent(numbers, 0x26)
        val r = width * 0.085f
        val placed = ArrayList<Pair<Float, Float>>()
        char.medians.forEachIndexed { i, pts ->
            val (mx, my) = pts.firstOrNull() ?: return@forEachIndexed
            // Make Me a Hanzi's y is up from a baseline at 900
            var x = (mx * s).coerceIn(r, width - r)
            var y = ((900f - my) * s).coerceIn(r, height - r)
            // nudged aside when two strokes start at the same place
            placed.firstOrNull { (px, py) -> kotlin.math.hypot(px - x, py - y) < r * 1.6f }?.let {
                x = (x + r * 1.7f).coerceAtMost(width - r)
            }
            placed += x to y
            canvas.drawCircle(x, y, r, numberBack)
            canvas.drawText((i + 1).toString(), x, y - (numberPaint.descent() + numberPaint.ascent()) / 2, numberPaint)
        }
    }

    private companion object {
        const val STROKE_MS = 650
        /** The part of each stroke's time spent resting before it is drawn. */
        const val REST = 0.18f
        /** Past this many strokes the numbers would crowd the character. */
        const val MAX_NUMBERED = 14
    }
}
