package app.lychee.emoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.util.LruCache
import android.view.View

/**
 * How Lychee draws an emoji, everywhere it shows one on its own (the panel, recents, the
 * skin-tone picker, search results, an emoji's card): in the chosen emoji font only (Settings >
 * Fonts; Noto Color Emoji by default, Noto for what another font lacks), its picture measured
 * and scaled to fit its box, centred. Drawing emoji as a line of text clipped any picture taller
 * than the font's line (Noto 3D's were cut off) and set some off-centre; this never does.
 */
object EmojiRenderer {
    /** The size each emoji is measured at. */
    private const val BASE = 128f

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = Rect()
    /** Each emoji's picture bounds at [BASE], by font and emoji. */
    private val bounds = LruCache<String, RectF>(1024)

    /** Draws [text] in [box], filling [fill] of its shorter side. */
    @JvmStatic
    fun draw(context: Context, canvas: Canvas, text: CharSequence, box: RectF, fill: Float = 0.78f, alpha: Int = 255) {
        if (text.isEmpty() || box.width() <= 0f || box.height() <= 0f) return
        val face = app.lychee.fonts.LycheeFonts.emoji(context)
        paint.typeface = face
        paint.textSize = BASE
        val key = "${System.identityHashCode(face)}\u0000$text"
        val b = bounds.get(key) ?: measure(text).also { bounds.put(key, it) }
        val size = maxOf(b.width(), b.height()).takeIf { it > 0f } ?: BASE
        val scale = minOf(box.width(), box.height()) * fill / size
        paint.textSize = BASE * scale
        paint.alpha = alpha
        canvas.drawText(text, 0, text.length, box.centerX() - b.centerX() * scale, box.centerY() - b.centerY() * scale, paint)
        paint.alpha = 255
    }

    /** The picture's bounds at [BASE]: the glyph's own box, else its advance and the font's line. */
    private fun measure(text: CharSequence): RectF {
        val s = text.toString()
        paint.getTextBounds(s, 0, s.length, rect)
        if (!rect.isEmpty) return RectF(rect)
        val fm = paint.fontMetrics
        return RectF(0f, fm.ascent, paint.measureText(s), fm.descent)
    }

    /** Forgets the measurements (the emoji font changed). */
    @JvmStatic
    fun clear() = bounds.evictAll()
}

/** One emoji drawn by [EmojiRenderer], filling [fill] of the view. */
class EmojiView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var text: CharSequence = ""
        set(value) {
            if (field != value) {
                field = value
                contentDescription = value
                invalidate()
            }
        }
    var fill = 0.78f
    private val box = RectF()

    override fun onDraw(canvas: Canvas) {
        box.set(paddingLeft.toFloat(), paddingTop.toFloat(), (width - paddingRight).toFloat(), (height - paddingBottom).toFloat())
        EmojiRenderer.draw(context, canvas, text, box, fill)
    }
}
