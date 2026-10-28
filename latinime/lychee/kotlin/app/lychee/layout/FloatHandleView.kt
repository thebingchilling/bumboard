package app.lychee.layout

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import app.lychee.lycheeHost
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.utils.ResourceUtils

/**
 * The bar on top of the floating keyboard: drag it to move the keyboard (and the emoji panel
 * with it); the button at the end docks the keyboard again.
 */
class FloatHandleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.outline }
    private val pillRect = RectF()
    private var downX = 0f
    private var downY = 0f
    private var startLeft = 0
    private var startBottom = 0

    init {
        setWillNotDraw(false)
        // left: resize; right: leave floating mode, filled so it stands out
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sym_open_in_full)
            imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = context.getString(R.string.lychee_sc_resize)
            background = context.getDrawable(R.drawable.lychee_m3_suggestion)
            setOnClickListener { lycheeHost()?.lycheeRunShortcut(app.lychee.toolbar.Shortcut.RESIZE) }
        }, LayoutParams(dp(40), dp(32), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(6) })
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sym_close_fullscreen)
            imageTintList = ColorStateList.valueOf(palette.onPrimaryContainer)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = context.getString(R.string.lychee_float_dock)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(palette.primaryContainer)
            }
            setOnClickListener {
                LycheeLayout.get(context).setMode(LycheeLayout.Mode.NORMAL)
                lycheeHost()?.lycheeReloadLayout()
            }
        }, LayoutParams(dp(48), dp(32), Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(6) })
    }

    override fun onDraw(canvas: Canvas) {
        val w = dp(36).toFloat()
        val h = dp(4).toFloat()
        pillRect.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        canvas.drawRoundRect(pillRect, h / 2, h / 2, pill)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val frame = parent as? View ?: return false
        val root = frame.parent as? View ?: return false
        val lp = frame.layoutParams as LayoutParams
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startLeft = lp.leftMargin
                startBottom = lp.bottomMargin
            }
            MotionEvent.ACTION_MOVE -> {
                val full = ResourceUtils.getDefaultKeyboardWidth(context)
                lp.leftMargin = (startLeft + (event.rawX - downX).toInt()).coerceIn(0, (full - frame.width).coerceAtLeast(0))
                lp.bottomMargin = (startBottom - (event.rawY - downY).toInt()).coerceIn(0, (root.height - frame.height).coerceAtLeast(0))
                frame.layoutParams = lp
                root.findViewById<View>(R.id.emoji_palettes_view)?.let { emoji ->
                    val elp = emoji.layoutParams as LayoutParams
                    elp.leftMargin = lp.leftMargin
                    elp.bottomMargin = lp.bottomMargin
                    emoji.layoutParams = elp
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                LycheeLayout.get(context).edit()
                    .putInt(LycheeLayout.PREF_FLOAT_X, lp.leftMargin)
                    .putInt(LycheeLayout.PREF_FLOAT_Y, lp.bottomMargin)
                    .apply()
            }
        }
        return true
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
