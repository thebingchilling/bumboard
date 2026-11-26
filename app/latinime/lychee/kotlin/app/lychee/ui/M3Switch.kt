package app.lychee.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Material 3's switch for the keyboard's own views (which aren't Compose): a 52 × 32 dp track,
 * outlined with a small thumb while off, filled in the accent colour with a large thumb while
 * on; the thumb slides and grows between them. [onCheckedChange] gets taps (not [isChecked] set
 * by code).
 */
class M3Switch(context: Context) : View(context) {
    private val c = M3Colors.of(context)
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d }
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    /** 0 off, 1 on, between while it moves. */
    private var position = 0f
    private var animator: ValueAnimator? = null

    var onCheckedChange: (Boolean) -> Unit = {}

    var isChecked = false
        set(value) {
            if (field == value) return
            field = value
            animator?.cancel()
            position = if (value) 1f else 0f
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            val on = !isChecked
            isChecked = on
            // the thumb slides across (at once with the phone's animations off)
            if (app.lychee.layout.LycheePanels.animate(context)) {
                position = if (on) 0f else 1f
                animator = ValueAnimator.ofFloat(position, if (on) 1f else 0f).apply {
                    duration = 180
                    addUpdateListener { position = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
            onCheckedChange(on)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // the track, with room round it for the touch target (48 dp tall)
        setMeasuredDimension((60 * d).toInt(), (48 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = 52 * d
        val h = 32 * d
        val left = (width - w) / 2
        val top = (height - h) / 2
        rect.set(left, top, left + w, top + h)
        val blend = { off: Int, on: Int -> androidx.core.graphics.ColorUtils.blendARGB(off, on, position) }
        track.color = blend(c.surfaceContainerHigh, c.primary)
        canvas.drawRoundRect(rect, h / 2, h / 2, track)
        if (position < 1f) {
            outline.color = androidx.core.graphics.ColorUtils.setAlphaComponent(c.outline, ((1 - position) * 255).toInt())
            rect.inset(d, d)
            canvas.drawRoundRect(rect, h / 2 - d, h / 2 - d, outline)
        }
        // the thumb: 16 dp off, 24 dp on
        val radius = (8 + 4 * position) * d
        val cx = left + h / 2 + (w - h) * position
        thumb.color = blend(c.outline, c.onPrimary)
        canvas.drawCircle(cx, top + h / 2, radius, thumb)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Switch::class.java.name
        info.isCheckable = true
        info.isChecked = isChecked
    }
}
