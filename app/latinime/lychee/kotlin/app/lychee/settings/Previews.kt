package app.lychee.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lychee.chinese.CandidateBarView

/*
 * Live previews for the settings that change how something looks, so the change shows right
 * where it is made, without opening the keyboard: the keys (palette, corners, borders, raised or
 * flat, height), the candidates (their size, readings and meanings) and the emoji (their size).
 */

/** A frame around a preview, set apart from the list. */
@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Three rows of keys in the chosen [palette] (Settings > Theme), with its corners at
 * [roundness] %, key borders on or off, raised or flat keys, and as tall as [height] makes them.
 */
@Composable
fun KeyboardPreview(palette: String, roundness: Int, borders: Boolean, raised: Boolean, height: Float) {
    val context = LocalContext.current
    val swatch = remember(palette) { app.lychee.ui.LycheeColorStyle.swatch(context, palette) }
    val background = Color(swatch[0])
    val key = Color(swatch[1])
    val functional = Color(swatch[2])
    val accent = Color(swatch[3])
    val ink = if (androidx.core.graphics.ColorUtils.calculateLuminance(swatch[1]) > 0.5) Color(0xFF1F1F1F) else Color(0xFFE8E8E8)
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    PreviewFrame {
        val keyHeight = (38 * height).dp
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(background)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            rows.forEachIndexed { r, letters ->
                Row(
                    Modifier.fillMaxWidth().height(keyHeight),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                ) {
                    if (r == 2) PreviewKey("⇧", functional, ink, roundness, true, raised, Modifier.weight(1.5f))
                    letters.forEach { c -> PreviewKey(c.toString(), key, ink, roundness, borders, raised, Modifier.weight(1f)) }
                    if (r == 2) PreviewKey("⌫", functional, ink, roundness, true, raised, Modifier.weight(1.5f))
                    if (r == 1) PreviewKey("↵", accent, Color.White, roundness, true, raised, Modifier.weight(1.4f))
                }
            }
        }
    }
}

@Composable
private fun PreviewKey(label: String, fill: Color, ink: Color, roundness: Int, visible: Boolean, raised: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val radius = with(androidx.compose.ui.platform.LocalDensity.current) { (roundness / 100f * 24).dp.toPx() }
        Canvas(Modifier.matchParentSize()) {
            val r = minOf(radius, size.minDimension / 2)
            if (!visible) return@Canvas
            if (raised) {
                drawRoundRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(0f, 2.dp.toPx()),
                    size = Size(size.width, size.height - 2.dp.toPx()), cornerRadius = CornerRadius(r, r))
            }
            drawRoundRect(fill, size = Size(size.width, size.height - if (raised) 2.dp.toPx() else 0f),
                cornerRadius = CornerRadius(r, r))
        }
        Text(label, color = ink, fontSize = 13.sp, maxLines = 1)
    }
}

/**
 * The candidate bars as they will look, for the Cantonese and the Mandarin keyboard: each
 * candidate's readings (written the way [display] says: Jyutping or Yale, pinyin or Zhuyin or
 * both, the tone style, the other language), its characters and, with [meaning], its meaning,
 * all at [scale] (the candidate size).
 */
@Composable
fun CandidatePreview(scale: Float, display: app.lychee.core.DisplayPrefs, meaning: Boolean) {
    data class Sample(val trad: String, val simp: String, val yue: String, val cmn: String, val gloss: String)
    val samples = listOf(
        Sample("你好", "你好", "nei5 hou2", "ni3 hao3", "hello"),
        Sample("學校", "学校", "hok6 haau6", "xue2 xiao4", "school"),
        Sample("食", "食", "sik6", "shi2", "eat"),
    )
    val colors = MaterialTheme.colorScheme
    fun fmt(r: String, l: app.lychee.core.Lang) = app.lychee.core.Annotator.format(r, l, display)
    PreviewFrame {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (mandarin in listOf(false, true)) {
                Text(
                    stringResourceSafe(if (mandarin) com.android.inputmethod.latin.R.string.lychee_language_mandarin
                        else com.android.inputmethod.latin.R.string.lychee_language_cantonese),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    samples.forEach { s ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val main = if (mandarin) fmt(s.cmn, app.lychee.core.Lang.Mandarin) else fmt(s.yue, app.lychee.core.Lang.Cantonese)
                            val other = if (mandarin) fmt(s.yue, app.lychee.core.Lang.Cantonese) else fmt(s.cmn, app.lychee.core.Lang.Mandarin)
                            // pinyin and Zhuyin together are two lines, like the bar
                            (main.split("  ") + if (display.showOtherLanguage) other.split("  ") else emptyList()).forEach { line ->
                                Text(line, fontSize = (CandidateBarView.READING_SP * scale).sp, color = colors.primary, maxLines = 1)
                            }
                            Text(if (display.simplified) s.simp else s.trad, fontSize = (CandidateBarView.CHAR_SP * scale).sp,
                                color = colors.onSurface, maxLines = 1)
                            if (meaning) {
                                Text(s.gloss, fontSize = (CandidateBarView.MEANING_SP * scale).sp, color = colors.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun stringResourceSafe(id: Int): String = androidx.compose.ui.res.stringResource(id)

/** A row of the emoji panel with [columns] emoji across it, at the size they'd be. */
@Composable
fun EmojiPreview(columns: Int) {
    val context = LocalContext.current
    val family = remember { FontFamily(app.lychee.fonts.LycheeFonts.emoji(context)) }
    val emoji = listOf("😀", "😂", "🥰", "😎", "🤔", "😭", "👍", "🙏", "🎉", "❤️", "🔥", "✨", "🐱", "🍜", "🌸", "⚽")
    PreviewFrame {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = maxWidth / columns
            Row(Modifier.fillMaxWidth()) {
                emoji.take(columns).forEach { e ->
                    Box(Modifier.width(cell).height(cell), contentAlignment = Alignment.Center) {
                        Text(e, fontFamily = family, fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (cell * 0.62f).toSp() })
                    }
                }
            }
        }
    }
}

