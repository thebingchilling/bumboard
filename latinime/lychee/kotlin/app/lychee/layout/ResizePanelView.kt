package app.lychee.layout

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
import app.lychee.lycheeHost
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * Resizing, like Gboard's: drag the top edge to change the height, then Done. Reset goes
 * back to the standard size.
 */
class ResizePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    /** The keyboard's outline, anchored at the bottom; the handles and buttons ride on it. */
    private val outline = FrameLayout(context)
    private val topHandle = View(context)
    /** The keys' exact size in pixels, updated while dragging. */
    private val sizeLabel = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
        setTextColor(palette.onPrimaryContainer)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(4), dp(12), dp(4))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(palette.primaryContainer)
        }
    }
    private var startHeight = 0
    private var downY = 0f
    /** The outline's size while dragging, as a fraction of the keys' size when the panel opened. */
    private var heightFactor = 1f

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
        outline.addView(LycheePanels.backButton(context) { visibility = GONE },
            LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.START))
        outline.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(button(R.string.lychee_resize_reset, false) { reset() })
            addView(button(R.string.lychee_resize_done, true) { done() }, LinearLayout.LayoutParams(
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
        startHeight = keysHeight
        (outline.layoutParams as LayoutParams).width = keysWidth
        visibility = VISIBLE
        updateOutline()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                val outlineTop = height - outline.height
                dragging = event.y < outlineTop + dp(40)
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                heightFactor = ((startHeight - (event.y - downY)) / startHeight)
                    .coerceIn(MIN_FACTOR, minOf(MAX_FACTOR, height.toFloat() / startHeight))
                updateOutline()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    private var dragging = false

    private fun updateOutline() {
        val lp = outline.layoutParams as LayoutParams
        lp.height = (startHeight * heightFactor).toInt()
        outline.layoutParams = lp
        sizeLabel.text = context.getString(R.string.lychee_resize_size_h, lp.height)
    }

    private fun done() {
        val layout = LycheeLayout.get(context)
        layout.edit().putFloat(LycheeLayout.PREF_HEIGHT_SCALE,
            (layout.heightScale * heightFactor).coerceIn(LycheeLayout.MIN_HEIGHT, LycheeLayout.MAX_HEIGHT)).apply()
        visibility = GONE
        lycheeHost()?.lycheeReloadLayout()
    }

    private fun reset() {
        LycheeLayout.get(context).edit()
            .remove(LycheeLayout.PREF_HEIGHT_SCALE)
            .apply()
        visibility = GONE
        lycheeHost()?.lycheeReloadLayout()
    }

    private fun button(label: Int, filled: Boolean, onClick: () -> Unit) = TextView(context).apply {
        setText(label)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
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

    companion object {
        private const val MIN_FACTOR = 0.6f
        private const val MAX_FACTOR = 1.6f

        /** How much taller than the keys the host makes this panel. */
        const val ROOM = MAX_FACTOR
    }
}
