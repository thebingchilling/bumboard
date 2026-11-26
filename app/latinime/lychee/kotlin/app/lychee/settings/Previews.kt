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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * The keyboard as it will look: the real keyboard (RealKeyboard: the keyboard's own views and
 * keys, its bar and shortcuts), with the choices on the page given over the saved settings so it
 * changes while a slider moves: the [palette], corners at [roundness] %, key borders, raised or
 * flat keys, [height]; and, where a page changes them, the number row, one-handed mode, the
 * spacebar's label ([spacebar], its setting), the key by the comma ([comma], its setting) and the
 * voice key. [themeMode] rebuilds it when light or dark changes.
 */
@Composable
fun KeyboardPreview(
    palette: String, roundness: Int, borders: Boolean, raised: Boolean, height: Float,
    numberRow: Boolean? = null, oneHanded: Boolean = false, spacebar: String? = null, comma: String? = null,
    voice: Boolean? = null, themeMode: String? = null,
) {
    val context = LocalContext.current
    val ids = context.resources.getIntArray(com.android.inputmethod.latin.R.array.keyboard_theme_ids)
    val overrides = buildMap<String, Any?> {
        put(app.lychee.ui.LycheeColorStyle.PREF, palette)
        put(app.lychee.ui.KeyStyle.PREF_ROUNDNESS, roundness)
        put(app.lychee.ui.KeyStyle.PREF_BORDERS, borders)
        put(com.android.inputmethod.keyboard.KeyboardTheme.lycheePreferenceKey(),
            (if (raised) ids.first() else ids.getOrElse(1) { ids.first() }).toString())
        put(app.lychee.layout.LycheeLayout.PREF_HEIGHT_SCALE, height)
        put(app.lychee.layout.LycheeLayout.PREF_MODE, if (oneHanded) "one_handed" else "normal")
        numberRow?.let { put(app.lychee.layout.LycheeLayout.PREF_NUMBER_ROW, it) }
        spacebar?.let { put(com.android.inputmethod.keyboard.MainKeyboardView.PREF_LYCHEE_SPACEBAR, it) }
        comma?.let { put(app.lychee.layout.LycheeLayout.PREF_COMMA_KEY, it) }
        voice?.let { put(com.android.inputmethod.latin.settings.Settings.PREF_VOICE_INPUT_KEY, it) }
        themeMode?.let { put(app.lychee.ui.KeyStyle.PREF_THEME_MODE, it) }
    }
    // in a rounded card with room round it: the theme shows in the preview, not across the page
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp))) {
        PhoneWidth(overrides) { c ->
            app.lychee.layout.RealKeyboard.inputView(app.lychee.layout.RealKeyboard.withSettings(c, overrides), null,
                c.resources.displayMetrics.widthPixels).first
        }
    }
}

/**
 * A view built at the phone's own width ([build]), as it is on the phone, shrunk to the page's
 * width; built again when [key] changes.
 */
@Composable
fun PhoneWidth(key: Any?, build: (android.content.Context) -> android.view.View) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fullWidthPx = context.resources.displayMetrics.widthPixels
    val view = remember(key) {
        build(context).also {
            it.measure(android.view.View.MeasureSpec.makeMeasureSpec(fullWidthPx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fullWidth = with(density) { fullWidthPx.toDp() }
        val fullHeight = with(density) { view.measuredHeight.toDp() }
        val scale = maxWidth / fullWidth
        Box(Modifier.fillMaxWidth().height(fullHeight * scale)) {
            androidx.compose.runtime.key(view) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { (view.parent as? android.view.ViewGroup)?.removeView(view); view },
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .requiredSize(fullWidth, fullHeight)
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        },
                )
            }
        }
    }
}

/**
 * How the chosen pronunciation reads: Mandarin words said differently in Taiwan (垃圾, 和 "and"),
 * English words said differently in the UK and the US (grass, tomato), the chosen one in colour.
 */
@Composable
fun PronunciationPreview(taiwan: Boolean, english: String) {
    val colors = MaterialTheme.colorScheme
    PreviewFrame {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PronLine("垃圾", "lājī  Mainland", "lèsè  Taiwan", !taiwan, taiwan)
            PronLine("和", "hé  Mainland", "hàn  Taiwan", !taiwan, taiwan)
            val uk = english != app.lychee.speech.Pronunciation.US
            val us = english != app.lychee.speech.Pronunciation.UK
            PronLine("grass", "/ɡrɑːs/  UK", "/ɡræs/  US", uk, us)
            PronLine("tomato", "/təˈmɑːtəʊ/  UK", "/təˈmeɪtoʊ/  US", uk, us)
        }
    }
}

@Composable
private fun PronLine(word: String, first: String, second: String, firstOn: Boolean, secondOn: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(word, fontSize = 20.sp, color = colors.onSurface, modifier = Modifier.width(88.dp))
        Text(first, fontSize = 15.sp, color = if (firstOn) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.weight(1f))
        Text(second, fontSize = 15.sp, color = if (secondOn) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.weight(1f))
    }
}

/** A word card's heading with the other script under it, as [mode] (hyphens, full or off) shows it. */
@Composable
fun OtherScriptPreview(mode: String, simplified: Boolean) {
    val colors = MaterialTheme.colorScheme
    val (word, other, diff) = if (simplified) Triple("学校", "學校", "學-") else Triple("學校", "学校", "学-")
    PreviewFrame {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(word, fontSize = 34.sp, color = colors.onSurface)
            when (mode) {
                "hyphens" -> Text(diff, fontSize = 22.sp, color = colors.onSurface, letterSpacing = 2.sp)
                "full" -> Text(other, fontSize = 22.sp, color = colors.onSurface, letterSpacing = 2.sp)
                else -> {}
            }
        }
    }
}

@Composable
private fun PreviewKey(label: String, fill: Color, ink: Color, roundness: Int, visible: Boolean, raised: Boolean, modifier: Modifier,
    small: Boolean = false) {
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
        Text(label, color = ink, fontSize = if (small) 10.sp else 13.sp, maxLines = 1)
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
                                // the bar's reading colour: the text's, a little softer (never the accent)
                                Text(line, fontSize = (CandidateBarView.READING_SP * scale).sp, color = colors.onSurface.copy(alpha = 0xD0 / 255f), maxLines = 1)
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
