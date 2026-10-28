package app.lychee.chinese

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.graphics.ColorUtils

/**
 * Tone colours on pinyin and Jyutping, like Pleco's: each syllable of a reading line in the
 * colour of its tone. Lighter shades on a dark keyboard.
 */
object ToneColours {
    // tones 1-6; Mandarin uses 1-4, its neutral tone (0) stays the text colour
    private val light = intArrayOf(0xFFD32F2F.toInt(), 0xFFEF6C00.toInt(), 0xFF2E7D32.toInt(),
        0xFF1565C0.toInt(), 0xFF7B1FA2.toInt(), 0xFF6D4C41.toInt())
    private val dark = intArrayOf(0xFFEF9A9A.toInt(), 0xFFFFB74D.toInt(), 0xFF81C784.toInt(),
        0xFF64B5F6.toInt(), 0xFFCE93D8.toInt(), 0xFFBCAAA4.toInt())

    /** The colour of [tone] (1-6) on text drawn in [textColor]; null for no tone. */
    fun of(tone: Int, textColor: Int): Int? {
        if (tone !in 1..6) return null
        val onDark = ColorUtils.calculateLuminance(textColor) > 0.5
        return (if (onDark) dark else light)[tone - 1]
    }

    /** [line] with its space-separated syllables coloured by [tones]. */
    fun apply(line: String, tones: List<Int>?, textColor: Int): CharSequence {
        if (tones.isNullOrEmpty()) return line
        val out = SpannableString(line)
        var syllable = 0
        var i = 0
        while (i < line.length) {
            if (line[i] == ' ') { i++; continue }
            val end = line.indexOf(' ', i).let { if (it < 0) line.length else it }
            of(tones.getOrElse(syllable) { 0 }, textColor)?.let {
                out.setSpan(ForegroundColorSpan(it), i, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            syllable++
            i = end
        }
        return out
    }
}
