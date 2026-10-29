package app.lychee.settings

import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lychee.fonts.LycheeFonts
import app.lychee.packs.Pack
import app.lychee.packs.PackImporter
import app.lychee.packs.PackKind
import app.lychee.packs.Packs
import com.android.inputmethod.latin.R

/**
 * Settings > Fonts: the app's font, the keyboard's, one each for Chinese, Japanese and Korean,
 * and the emoji font. Every choice is a card showing what it looks like: built-in and installed
 * fonts draw their own sample, emoji fonts not yet downloaded show a picture of themselves.
 */
@Composable
fun FontsPage() {
    val context = LocalContext.current
    val actions = rememberPackActions()
    @Suppress("UNUSED_VARIABLE") val refresh = actions.version + PackRefresh.tick

    Text(
        stringResource(R.string.lychee_fonts_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    PackPending(actions)
    PackMessage(actions)

    val gsf = stringResource(R.string.lychee_fonts_gsf)
    val gsfSummary = stringResource(R.string.lychee_fonts_gsf_summary)
    val wenkai = stringResource(R.string.lychee_fonts_wenkai)
    val wenkaiSummary = stringResource(R.string.lychee_fonts_wenkai_summary)
    val system = stringResource(R.string.lychee_fonts_system)
    val systemSummary = stringResource(R.string.lychee_fonts_system_summary)

    FontSection(
        stringResource(R.string.lychee_fonts_app), LycheeFonts.PREF_UI, stringResource(R.string.lychee_fonts_sample_app), actions,
        listOf(
            FontOption("", gsf, gsfSummary, face = { LycheeFonts.gsf(it) }),
            FontOption(LycheeFonts.SYSTEM, system, systemSummary, face = { Typeface.SANS_SERIF }),
        ),
        onChosen = { AppFont.system = it == LycheeFonts.SYSTEM },
    )
    FontSection(
        stringResource(R.string.lychee_fonts_keyboard), LycheeFonts.PREF_KEYS, stringResource(R.string.lychee_fonts_sample_keys), actions,
        listOf(
            FontOption("", gsf, gsfSummary, face = { LycheeFonts.gsf(it) }),
            FontOption(LycheeFonts.WENKAI, wenkai, stringResource(R.string.lychee_fonts_wenkai_keys_summary), face = { LycheeFonts.wenkaiKeys(it) }),
        ) + ownFont(context, PackKind.FONT_KEYS),
    )
    for ((title, pref, sample, kind) in listOf(
        Quad(R.string.lychee_fonts_chinese, LycheeFonts.PREF_CHINESE, R.string.lychee_fonts_sample_zh, PackKind.FONT_ZH),
        Quad(R.string.lychee_fonts_japanese, LycheeFonts.PREF_JAPANESE, R.string.lychee_fonts_sample_ja, PackKind.FONT_JA),
        Quad(R.string.lychee_fonts_korean, LycheeFonts.PREF_KOREAN, R.string.lychee_fonts_sample_ko, PackKind.FONT_KO),
    )) {
        FontSection(
            stringResource(title), pref, stringResource(sample), actions,
            listOf(
                FontOption("", wenkai, wenkaiSummary, face = { LycheeFonts.wenkai(it) }),
                FontOption(LycheeFonts.SYSTEM, system, systemSummary, face = { Typeface.DEFAULT }),
            ) + ownFont(context, kind),
        )
    }
    val emojiPacks = remember { Packs.ofKind(context, PackKind.FONT_EMOJI) }
    FontSection(
        stringResource(R.string.lychee_fonts_emoji), LycheeFonts.PREF_EMOJI, stringResource(R.string.lychee_fonts_sample_emoji), actions,
        listOf(
            FontOption("", stringResource(R.string.lychee_fonts_noto), stringResource(R.string.lychee_fonts_noto_summary),
                face = { LycheeFonts.bundledEmoji(it) }, emoji = true),
            FontOption(LycheeFonts.SYSTEM, stringResource(R.string.lychee_fonts_system_emoji),
                stringResource(R.string.lychee_fonts_system_emoji_summary), face = { Typeface.DEFAULT }, emoji = true),
        ) + emojiPacks.map { pack -> FontOption(pack.id, pack.name, pack.summary, pack = pack, emoji = true) },
    )
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/** One choice: [value] is what the preference holds; [face] draws a built-in font. */
private data class FontOption(
    val value: String,
    val title: String,
    val summary: String,
    val face: ((android.content.Context) -> Typeface?)? = null,
    val pack: Pack? = null,
    val emoji: Boolean = false,
)

/** The "your own font" choice for [kind], when the catalogue has one. */
private fun ownFont(context: android.content.Context, kind: PackKind): List<FontOption> =
    Packs.ofKind(context, kind).filter { it.custom }.map { FontOption(it.id, "", "", pack = it) }

/**
 * A titled list of font cards for one preference. Choosing a font that isn't installed yet
 * starts its download (or, for your own font, the file picker) and selects it once installed.
 */
@Composable
private fun FontSection(
    title: String,
    pref: String,
    sample: String,
    actions: PackActions,
    options: List<FontOption>,
    onChosen: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = prefs()
    val choice = prefs.stringPref(pref) { prefs.getString(pref, null) ?: "" }
    // a font waiting to be installed before it is selected
    var wanted by remember(pref) { mutableStateOf<String?>(null) }
    LaunchedEffect(actions.version, PackRefresh.tick, wanted) {
        val w = wanted ?: return@LaunchedEffect
        if (Packs.isInstalled(context, w)) {
            choice.set(w)
            onChosen(w)
            wanted = null
        }
    }
    // a chosen pack that was removed falls back to the default
    val current = choice.value.takeIf { v ->
        v.isEmpty() || options.any { it.value == v && (it.pack == null || Packs.isInstalled(context, it.pack.id)) }
    } ?: ""

    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FontCard(option, sample, selected = current == option.value, actions,
                onSelect = {
                    choice.set(option.value)
                    onChosen(option.value)
                },
                onWant = { wanted = option.value })
        }
    }
}

@Composable
private fun FontCard(
    option: FontOption,
    sample: String,
    selected: Boolean,
    actions: PackActions,
    onSelect: () -> Unit,
    onWant: () -> Unit,
) {
    val context = LocalContext.current
    val pack = option.pack
    val installed = pack == null || Packs.isInstalled(context, pack.id)
    val own = pack?.custom == true
    val title = if (own) stringResource(R.string.lychee_fonts_own) else option.title
    val summary = if (own) stringResource(R.string.lychee_fonts_own_summary) else option.summary

    // the sample: drawn in the font when it is here, a picture of it otherwise
    val family = remember(option.value, pack?.id, installed, actions.version) {
        val face = when {
            option.face != null -> option.face.invoke(context)
            pack != null && installed -> Packs.mainFile(context, pack.id)?.let { PackImporter.loadFont(it) }
            else -> null
        }
        face?.let { FontFamily(it) }
    }
    val picture: ImageBitmap? = remember(pack?.preview, installed) {
        pack?.preview?.takeIf { !installed }?.let { path ->
            runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
        }
    }
    val emojiVersion = remember(option.value, installed, actions.version) {
        if (!option.emoji) null
        else if (installed) LycheeFonts.emojiVersionOf(context, option.value) ?: pack?.emoji
        else pack?.emoji
    }
    val detail = listOfNotNull(
        emojiVersion?.let { stringResource(R.string.lychee_fonts_emoji_version, it) },
        summary.takeIf { it.isNotEmpty() },
        pack?.takeIf { !installed && !own }?.size?.takeIf { it.isNotEmpty() },
    ).joinToString(" · ")

    val select = {
        when {
            installed -> onSelect()
            own -> {
                onWant()
                actions.import(pack)
            }
            else -> {
                onWant()
                actions.get(pack!!)
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = select,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (selected) contentDescription = "$title, selected" },
    ) {
        Column(Modifier.padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)) {
            when {
                picture != null -> Image(
                    picture, contentDescription = null,
                    contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                    modifier = Modifier.height(34.dp).fillMaxWidth(),
                )
                family != null || pack == null -> Text(
                    sample,
                    fontFamily = family,
                    fontSize = if (option.emoji) 28.sp else 24.sp,
                    lineHeight = 36.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    color = colors.onSurface,
                )
                // your own font, not chosen yet
                else -> Text(
                    "Aa",
                    fontSize = 24.sp,
                    lineHeight = 36.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (selected) R.drawable.ic_sym_check_circle else R.drawable.ic_sym_radio_button_unchecked),
                    contentDescription = null,
                    tint = if (selected) colors.primary else colors.outline,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    if (detail.isNotEmpty()) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                when {
                    pack == null -> {}
                    !installed && own -> FilledTonalButton(onClick = select) { Text(stringResource(R.string.lychee_fonts_choose)) }
                    !installed -> FilledTonalButton(onClick = select) { Text(stringResource(R.string.lychee_packs_get)) }
                    else -> {
                        if (own) TextButton(onClick = { onWant(); actions.import(pack) }) { Text(stringResource(R.string.lychee_fonts_change)) }
                        else if (app.lychee.packs.Updates.hasUpdate(context, pack)) {
                            FilledTonalButton(onClick = { actions.get(pack) }) { Text(stringResource(R.string.lychee_packs_update)) }
                        }
                        TextButton(onClick = { actions.remove(pack) }) { Text(stringResource(R.string.lychee_packs_remove)) }
                    }
                }
            }
        }
    }
}
