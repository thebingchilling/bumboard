package app.bumboard.layout

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.bumboard.bumHost
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * Resizing, like Gboard's: drag the top edge to change the height (and, when floating, the
 * right edge to change the width), then Done. Reset goes back to the standard size.
 */
class ResizePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    /** The keyboard's outline, anchored at the bottom; the handles and buttons ride on it. */
    private val outline = FrameLayout(context)
    private val topHandle = View(context)
    private val sideHandle = View(context)
    /** The keys' exact size in pixels, updated while dragging. */
    private val sizeLabel = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(palette.onPrimaryContainer)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(4), dp(12), dp(4))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(palette.primaryContainer)
        }
    }
    private var startHeight = 0
    private var startWidth = 0
    private var downY = 0f
    private var downX = 0f
    /** The outline's size while dragging, as a fraction of the keys' size when the panel opened. */
    private var heightFactor = 1f
    private var widthFactor = 1f

    init {
        isClickable = true
        setBackgroundColor(palette.surface and 0x00FFFFFF or (0xE6 shl 24))
        outline.background = GradientDrawable().apply {
            setStroke(dp(2), palette.primary)
            cornerRadius = dp(12).toFloat()
        }
        addView(outline, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.BOTTOM or Gravity.START))
        topHandle.background = pill()
        outline.addView(topHandle, LayoutParams(dp(48), dp(6), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(6) })
        outline.addView(sizeLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(20) })
        outline.addView(BumPanels.backButton(context) { visibility = GONE },
            LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.START))
        sideHandle.background = pill()
        outline.addView(sideHandle, LayoutParams(dp(6), dp(48), Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(6) })
        outline.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(button(R.string.bum_resize_reset, false) { reset() })
            addView(button(R.string.bum_resize_done, true) { done() }, LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(12) })
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        visibility = GONE
    }

    /**
     * Opens with the outline at the keys' current size ([keysWidth] x [keysHeight]); the panel
     * itself is laid out taller by the host so the outline has room to grow upwards.
     */
    fun show(keysWidth: Int, keysHeight: Int) {
        heightFactor = 1f
        widthFactor = 1f
        startWidth = keysWidth
        startHeight = keysHeight
        sideHandle.visibility = if (BumLayout.get(context).mode == BumLayout.Mode.FLOATING) VISIBLE else GONE
        visibility = VISIBLE
        updateOutline()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                val outlineTop = height - outline.height
                dragging = when {
                    event.y < outlineTop + dp(40) -> TOP
                    sideHandle.visibility == VISIBLE && event.x > outline.width - dp(40) -> SIDE
                    else -> NONE
                }
            }
            MotionEvent.ACTION_MOVE -> when (dragging) {
                TOP -> {
                    heightFactor = ((startHeight - (event.y - downY)) / startHeight)
                        .coerceIn(MIN_FACTOR, minOf(MAX_FACTOR, height.toFloat() / startHeight))
                    updateOutline()
                }
                SIDE -> {
                    widthFactor = ((startWidth + (event.x - downX)) / startWidth)
                        .coerceIn(0.6f, width.toFloat() / startWidth)
                    updateOutline()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = NONE
        }
        return true
    }

    private var dragging = NONE

    private fun updateOutline() {
        val lp = outline.layoutParams as LayoutParams
        lp.height = (startHeight * heightFactor).toInt()
        lp.width = (startWidth * widthFactor).toInt()
        outline.layoutParams = lp
        sizeLabel.text = if (sideHandle.visibility == VISIBLE) {
            context.getString(R.string.bum_resize_size_wh, lp.width, lp.height)
        } else {
            context.getString(R.string.bum_resize_size_h, lp.height)
        }
    }

    private fun done() {
        val layout = BumLayout.get(context)
        val editor = layout.edit().putFloat(BumLayout.PREF_HEIGHT_SCALE,
            (layout.heightScale * heightFactor).coerceIn(BumLayout.MIN_HEIGHT, BumLayout.MAX_HEIGHT))
        if (layout.mode == BumLayout.Mode.FLOATING) {
            editor.putFloat(BumLayout.PREF_FLOAT_WIDTH, (layout.floatWidth * widthFactor).coerceIn(BumLayout.MIN_FLOAT_WIDTH, 1f))
        }
        editor.apply()
        visibility = GONE
        bumHost()?.bumReloadLayout()
    }

    private fun reset() {
        BumLayout.get(context).edit()
            .remove(BumLayout.PREF_HEIGHT_SCALE)
            .remove(BumLayout.PREF_FLOAT_WIDTH)
            .apply()
        visibility = GONE
        bumHost()?.bumReloadLayout()
    }

    private fun button(label: Int, filled: Boolean, onClick: () -> Unit) = TextView(context).apply {
        setText(label)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (filled) palette.onPrimary else palette.primary)
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(10), dp(24), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            if (filled) setColor(palette.primary) else setStroke(dp(1), palette.outline)
        }
        setOnClickListener { onClick() }
    }

    private fun pill() = GradientDrawable().apply {
        setColor(palette.primary)
        cornerRadius = dp(3).toFloat()
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        const val NONE = 0
        const val TOP = 1
        const val SIDE = 2
        const val MIN_FACTOR = 0.6f
        const val MAX_FACTOR = 1.6f

        /** How much taller than the keys the host makes this panel. */
        const val ROOM = MAX_FACTOR
    }
}
