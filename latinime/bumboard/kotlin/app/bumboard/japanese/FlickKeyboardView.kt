package app.bumboard.japanese

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import app.bumboard.BumHost
import app.bumboard.bumHost
import app.bumboard.layout.BumPanels
import app.bumboard.toolbar.Shortcut
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The 12-key flick keyboard for Japanese, like Gboard's: tap a key for its あ-row kana, or
 * flick left, up, right or down for the い, う, え and お rows. ゛゜小 changes the last kana.
 * Kana go to Rime (as romaji) for kanji candidates; 空白 converts / types a space and 改行
 * confirms. ABC hides it to show the romaji keyboard underneath.
 */
class FlickKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** The engine the kana go to (set by LatinIME). */
    var engine: FlickTarget? = null

    interface FlickTarget {
        /** A kana or mark from a key. */
        fun flickType(text: String)
        /** ゛゜小; false when there is nothing to change. */
        fun flickModify(): Boolean
        /** Takes back the last kana; false when nothing is being written. */
        fun flickBackspace(): Boolean
    }

    private sealed class K(val col: Int, val row: Int, val rows: Int = 1) {
        class Kana(col: Int, row: Int, val index: Int) : K(col, row)
        class Action(col: Int, row: Int, val label: String, val icon: Int?, rows: Int = 1, val run: () -> Unit) : K(col, row, rows)
    }

    private val palette = M3Colors.of(context)
    private val host: BumHost? get() = bumHost()
    private val keys: List<K> = buildList {
        add(K.Action(0, 0, "", R.drawable.ic_sym_mood) { host?.bumSendCode(Constants.CODE_EMOJI); follow() })
        add(K.Action(0, 1, "", R.drawable.ic_sym_language) { host?.bumRunShortcut(Shortcut.LANGUAGE) })
        add(K.Action(0, 2, "123", null) { host?.bumSendCode(Constants.CODE_BUM_NUMPAD) })
        add(K.Action(0, 3, "ABC", null) { hiddenByUser = true; follow() })
        val grid = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8))
        grid.forEachIndexed { r, row -> row.forEachIndexed { c, i -> add(K.Kana(c + 1, r, i)) } }
        add(K.Action(1, 3, "゛゜小", null) { engine?.flickModify() })
        add(K.Kana(2, 3, 9))
        add(K.Kana(3, 3, 10))
        add(K.Action(4, 0, "", R.drawable.ic_sym_backspace) {
            if (engine?.flickBackspace() != true) host?.bumSendCode(Constants.CODE_DELETE)
        })
        add(K.Action(4, 1, "空白", null) { host?.bumSendCode(Constants.CODE_SPACE) })
        add(K.Action(4, 2, "改行", null, rows = 2) { host?.bumSendCode(Constants.CODE_ENTER) })
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurface
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSurfaceVariant
        textAlign = Paint.Align.CENTER
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.primary }
    private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onPrimary
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val letterKey: Drawable? = BumPanels.letterKey(context)
    private val functionalKey: Drawable? = BumPanels.functionalKey(context)
    private val icons = HashMap<Int, Drawable?>()

    private var pressed: K? = null
    private var downX = 0f
    private var downY = 0f
    /** 0 tap, 1 left, 2 up, 3 right, 4 down. */
    private var direction = 0

    init {
        setBackgroundColor(palette.surface)
        isClickable = true
        visibility = GONE
    }

    /** True on the flick keyboard (set by LatinIME from the subtype); ABC turns it off until then. */
    var wanted = false
        set(value) {
            field = value
            hiddenByUser = false
            follow()
        }
    private var hiddenByUser = false

    private val layoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener { follow() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        super.onDetachedFromWindow()
    }

    /** Covers the keys exactly while they show (not over the emoji panel) and this is wanted. */
    private fun follow() {
        val root = parent as? View ?: return
        val keys = root.findViewById<View>(R.id.keyboard_view)
        val show = wanted && !hiddenByUser && keys != null && keys.isShown && keys.height > 0
        if (!show) {
            if (visibility != GONE) visibility = GONE
            return
        }
        val lp = layoutParams as android.widget.FrameLayout.LayoutParams
        val rootLoc = IntArray(2).also { root.getLocationInWindow(it) }
        val keysLoc = IntArray(2).also { keys.getLocationInWindow(it) }
        val left = keysLoc[0] - rootLoc[0]
        val bottom = (rootLoc[1] + root.height) - (keysLoc[1] + keys.height)
        if (lp.width != keys.width || lp.height != keys.height || lp.leftMargin != left || lp.bottomMargin != bottom ||
            lp.gravity != (android.view.Gravity.BOTTOM or android.view.Gravity.START)) {
            lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
            lp.width = keys.width
            lp.height = keys.height
            lp.leftMargin = left
            lp.bottomMargin = bottom
            layoutParams = lp
        }
        if (visibility != VISIBLE) visibility = VISIBLE
    }

    private fun bounds(k: K, out: RectF) {
        val w = width / 5f
        val h = height / 4f
        val gap = resources.displayMetrics.density * 3
        out.set(k.col * w + gap, k.row * h + gap, (k.col + 1) * w - gap, (k.row + k.rows) * h - gap)
    }

    private val rect = RectF()
    private val irect = Rect()

    override fun onDraw(canvas: Canvas) {
        val h = height / 4f
        labelPaint.textSize = h * 0.36f
        hintPaint.textSize = h * 0.17f
        for (k in keys) {
            bounds(k, rect)
            rect.roundOut(irect)
            val bg = if (k is K.Kana) letterKey else functionalKey
            bg?.state = if (k === pressed) intArrayOf(android.R.attr.state_pressed) else intArrayOf()
            bg?.bounds = irect
            bg?.draw(canvas)
            when (k) {
                is K.Kana -> {
                    val kana = Kana.KEYS[k.index]
                    canvas.drawText(kana[0] ?: "", rect.centerX(), rect.centerY() + labelPaint.textSize * 0.36f, labelPaint)
                    val inset = hintPaint.textSize * 0.9f
                    kana[1]?.let { canvas.drawText(it, rect.left + inset, rect.centerY() + hintPaint.textSize * 0.35f, hintPaint) }
                    kana[2]?.let { canvas.drawText(it, rect.centerX(), rect.top + inset * 1.1f, hintPaint) }
                    kana[3]?.let { canvas.drawText(it, rect.right - inset, rect.centerY() + hintPaint.textSize * 0.35f, hintPaint) }
                    kana[4]?.let { canvas.drawText(it, rect.centerX(), rect.bottom - inset * 0.4f, hintPaint) }
                }
                is K.Action -> {
                    val icon = k.icon?.let { id -> icons.getOrPut(id) { context.getDrawable(id)?.mutate()?.apply { setTint(palette.onSecondaryContainer) } } }
                    if (icon != null) {
                        val s = (h * 0.4f).toInt()
                        val cx = rect.centerX().toInt()
                        val cy = rect.centerY().toInt()
                        icon.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
                        icon.draw(canvas)
                    } else {
                        val old = labelPaint.textSize
                        labelPaint.textSize = if (k.label.length > 2) h * 0.22f else h * 0.26f
                        labelPaint.color = palette.onSecondaryContainer
                        canvas.drawText(k.label, rect.centerX(), rect.centerY() + labelPaint.textSize * 0.36f, labelPaint)
                        labelPaint.textSize = old
                        labelPaint.color = palette.onSurface
                    }
                }
            }
        }
        // the kana being flicked to, in a bubble over its key
        val k = pressed
        if (k is K.Kana) {
            val text = Kana.KEYS[k.index][direction] ?: return
            bounds(k, rect)
            val r = rect.width() * 0.42f
            val cx = rect.centerX() + when (direction) { 1 -> -rect.width() * 0.6f; 3 -> rect.width() * 0.6f; else -> 0f }
            val cy = rect.centerY() + when (direction) { 2 -> -rect.height() * 0.7f; 4 -> rect.height() * 0.7f; else -> -rect.height() * 0.15f }
            canvas.drawCircle(cx, cy, r, bubblePaint)
            bubbleText.textSize = r
            canvas.drawText(text, cx, cy + r * 0.36f, bubbleText)
        }
    }

    private fun keyAt(x: Float, y: Float): K? = keys.firstOrNull { k ->
        bounds(k, rect)
        rect.contains(x, y)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = keyAt(event.x, event.y)
                downX = event.x
                downY = event.y
                direction = 0
                performHapticFeedback(HAPTIC)
            }
            MotionEvent.ACTION_MOVE -> {
                val k = pressed
                if (k is K.Kana) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val threshold = width / 5f * 0.28f
                    val d = when {
                        hypot(dx, dy) < threshold -> 0
                        abs(dx) > abs(dy) -> if (dx < 0) 1 else 3
                        else -> if (dy < 0) 2 else 4
                    }
                    if (Kana.KEYS[k.index][d] != null) direction = d
                }
            }
            MotionEvent.ACTION_UP -> {
                when (val k = pressed) {
                    is K.Kana -> Kana.KEYS[k.index][direction]?.let { engine?.flickType(it) }
                    is K.Action -> k.run()
                    null -> Unit
                }
                pressed = null
            }
            MotionEvent.ACTION_CANCEL -> pressed = null
        }
        invalidate()
        return true
    }

    private companion object {
        const val HAPTIC = android.view.HapticFeedbackConstants.KEYBOARD_TAP
    }
}
