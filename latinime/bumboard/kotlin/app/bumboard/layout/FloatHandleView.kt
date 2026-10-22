package app.bumboard.layout

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
import app.bumboard.bumHost
import app.bumboard.ui.M3Colors
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
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sym_keyboard_arrow_down)
            imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = context.getString(R.string.bum_float_dock)
            setOnClickListener {
                BumLayout.get(context).setMode(BumLayout.Mode.NORMAL)
                bumHost()?.bumReloadLayout()
            }
        }, LayoutParams(dp(40), LayoutParams.MATCH_PARENT, Gravity.END))
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
                BumLayout.get(context).edit()
                    .putInt(BumLayout.PREF_FLOAT_X, lp.leftMargin)
                    .putInt(BumLayout.PREF_FLOAT_Y, lp.bottomMargin)
                    .apply()
            }
        }
        return true
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
