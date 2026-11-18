package app.lychee.settings

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import android.view.View
import app.lychee.ui.KeyStyle
import app.lychee.ui.LycheeColorStyle
import com.android.inputmethod.latin.R

/**
 * The keyboard drawn as it really is, for Settings' previews: the same key drawables (raised or
 * flat, from the chosen palette, light or dark as Settings > Theme says), the same colours, the
 * keyboard's own proportions (its height, Gboard's gaps and paddings, the row offsets and
 * the bottom row's widths), the same icons and letter sizes, edge to edge like the keyboard,
 * with the bar above it. Every setting it shows can be given live (while a slider moves).
 */
class KeyboardPreviewView(context: Context) : View(context) {
    var palette = LycheeColorStyle.NEUTRAL
    var roundness = KeyStyle.DEFAULT_ROUNDNESS
    var borders = true
    var raised = true
    var heightScale = 1f
    var numberRow = false
    var oneHanded = false
    var spacebar: String? = null
    var comma = "combined"
    /** Whether the bar shows the voice key (Settings > Keys and feedback > Voice input). */
    var voice = true

    /** Settings changed: rebuilds the drawables and redraws. */
    fun refresh() {
        prepared = null
        requestLayout()
        invalidate()
    }

    private class Look(
        val letter: Drawable, val functional: Drawable, val action: Drawable,
        val background: Int, val onKey: Int, val onFunctional: Int, val onAccent: Int, val onKeyVariant: Int,
    )

    private var prepared: Look? = null
    private val density = resources.displayMetrics.density
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 400)
    }

    private fun attr(c: Context, id: Int): Int {
        val a = c.obtainStyledAttributes(intArrayOf(id))
        val v = a.getColor(0, 0)
        a.recycle()
        return v
    }

    /** The key drawables and colours, as the keyboard has them. */
    private fun look(): Look {
        prepared?.let { return it }
        // light or dark as the keyboard is (Settings > Theme > Appearance), not as Settings is
        val night = KeyStyle.night(context)
        val base = if (night == null) context else context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        })
        val themed = ContextThemeWrapper(base, LycheeColorStyle.overlayOf(palette))
        val keyRes = if (raised) R.drawable.lychee_m3_key_raised else R.drawable.lychee_m3_key_flat
        val fnRes = if (raised) R.drawable.lychee_m3_key_functional_raised else R.drawable.lychee_m3_key_functional_flat
        fun load(res: Int, state: IntArray) = round(themed.getDrawable(res)!!.mutate()).apply { this.state = state }
        return Look(
            letter = load(keyRes, intArrayOf()),
            functional = load(fnRes, intArrayOf()),
            // Enter: the key background in its active (accent) state, as LatinIME draws action keys
            action = load(keyRes, intArrayOf(android.R.attr.state_active)),
            background = attr(themed, R.attr.lycheeColorBackground),
            onKey = attr(themed, R.attr.lycheeColorOnKey),
            onFunctional = attr(themed, R.attr.lycheeColorOnFunctional),
            onAccent = attr(themed, R.attr.lycheeColorOnAccent),
            onKeyVariant = attr(themed, R.attr.lycheeColorOnKeyVariant),
        ).also { prepared = it }
    }

    /** The corners at [roundness] %, as KeyStyle rounds the keyboard's (pills stay pills). */
    private fun round(d: Drawable): Drawable {
        val radius = roundness / 100f * 24f * density
        val pill = 50 * density
        fun walk(x: Drawable) {
            when (x) {
                is DrawableContainer -> (x.constantState as? DrawableContainer.DrawableContainerState)
                    ?.children?.filterNotNull()?.forEach(::walk)
                is LayerDrawable -> for (i in 0 until x.numberOfLayers) walk(x.getDrawable(i))
                is GradientDrawable -> if (x.cornerRadius < pill) x.cornerRadius = radius
            }
        }
        walk(d)
        return d
    }

    private val rows: Int get() = if (numberRow) 5 else 4
    private val stripHeight get() = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
    /** The keys' height: the keyboard's own (a row more with the number row), scaled as set. */
    private val keysHeight: Int
        get() {
            val four = resources.getDimension(R.dimen.config_default_keyboard_height) * heightScale
            return (four * rows / 4f).toInt()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), stripHeight + keysHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val look = look()
        canvas.drawColor(look.background)
        drawStrip(canvas, look)
        val fullWidth = width.toFloat()
        val h = keysHeight.toFloat()
        // one-handed: the keys narrowed to the right, as the keyboard does
        val kbWidth = if (oneHanded) fullWidth * 0.82f else fullWidth
        val left = fullWidth - kbWidth
        val top = stripHeight.toFloat()
        // Gboard's measurements, as the keyboard theme has them (Keyboard.LycheeM3)
        val topPad = h * 0.022f
        val bottomPad = h * 0.04f
        val vGap = h * 0.035f
        val hGap = kbWidth * 0.016f
        val sidePad = kbWidth * 0.006f
        val pitch = (h - topPad - bottomPad) / rows
        val keyH = pitch - vGap
        val unit = (kbWidth - sidePad * 2) / 10f
        var y = top + topPad + vGap / 2
        fun key(x: Float, w: Float, kind: Int, label: String? = null, icon: Int? = null, small: Boolean = false) {
            val l = x + hGap / 2
            val r = x + w - hGap / 2
            val d = when (kind) { 0 -> look.letter; 1 -> look.functional; else -> look.action }
            // with key borders off, the letter keys have no background (as on the keyboard)
            if (kind != 0 || borders || label == " ") {
                d.setBounds(l.toInt(), y.toInt(), r.toInt(), (y + keyH).toInt())
                d.draw(canvas)
            }
            val color = when (kind) { 0 -> look.onKey; 1 -> look.onFunctional; else -> look.onAccent }
            if (icon != null) {
                context.getDrawable(icon)?.mutate()?.let { ic ->
                    val s = (keyH * 0.42f).toInt()
                    val cx = ((l + r) / 2).toInt()
                    val cy = (y + keyH / 2).toInt()
                    ic.setTint(color)
                    ic.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
                    ic.draw(canvas)
                }
            } else if (label != null && label != " ") {
                text.color = color
                text.textSize = keyH * if (small) 0.30f else 0.48f
                val baseline = y + keyH / 2 - (text.descent() + text.ascent()) / 2
                canvas.drawText(label, (l + r) / 2, baseline, text)
            }
        }
        val start = left + sidePad
        if (numberRow) {
            "1234567890".forEachIndexed { i, c -> key(start + unit * i, unit, 0, c.toString()) }
            y += pitch
        }
        "qwertyuiop".forEachIndexed { i, c -> key(start + unit * i, unit, 0, c.toString()) }
        y += pitch
        // the middle row sits half a key in
        "asdfghjkl".forEachIndexed { i, c -> key(start + unit * (i + 0.5f), unit, 0, c.toString()) }
        y += pitch
        key(start, unit * 1.5f, 1, icon = R.drawable.ic_sym_shift)
        "zxcvbnm".forEachIndexed { i, c -> key(start + unit * (1.5f + i), unit, 0, c.toString()) }
        key(start + unit * 8.5f, unit * 1.5f, 1, icon = R.drawable.ic_sym_backspace)
        y += pitch
        // the bottom row: ?123, the comma (and its neighbour), space, the period, Enter
        var x = start
        key(x, unit * 1.5f, 1, "?123", small = true); x += unit * 1.5f
        key(x, unit, 1, ","); x += unit
        val beside = comma == "emoji" || comma == "language"
        if (beside) {
            key(x, unit, 1, icon = if (comma == "emoji") R.drawable.ic_sym_mood else R.drawable.ic_sym_language)
            x += unit
        }
        val spaceW = unit * (if (beside) 4f else 5f)
        key(x, spaceW, 0, " ")
        spacebar?.takeIf { it.isNotEmpty() }?.let { name ->
            text.color = look.onKeyVariant
            text.textSize = keyH * 0.28f
            canvas.drawText(name, x + spaceW / 2, y + keyH / 2 - (text.descent() + text.ascent()) / 2, text)
        }
        x += spaceW
        key(x, unit, 1, "."); x += unit
        key(x, unit * 1.5f, 2, icon = R.drawable.ic_sym_keyboard_return)
    }

    /** The bar above the keys, with the toolbar's shortcuts and the mic, as when nothing is typed. */
    private fun drawStrip(canvas: Canvas, look: Look) {
        val icons = listOf(R.drawable.ic_sym_apps_outlined, R.drawable.ic_sym_mood, R.drawable.ic_sym_content_paste,
            R.drawable.ic_sym_translate, R.drawable.ic_sym_settings)
        val s = (24 * density).toInt()
        val cy = stripHeight / 2
        val slot = width / 7f
        icons.forEachIndexed { i, res ->
            context.getDrawable(res)?.mutate()?.let { ic ->
                ic.setTint(look.onKeyVariant)
                val cx = (slot * (i + 0.5f)).toInt()
                ic.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
                ic.draw(canvas)
            }
        }
        if (voice) context.getDrawable(R.drawable.ic_sym_mic)?.mutate()?.let { ic ->
            ic.setTint(look.onKeyVariant)
            val cx = (width - slot / 2).toInt()
            ic.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
            ic.draw(canvas)
        }
    }
}
