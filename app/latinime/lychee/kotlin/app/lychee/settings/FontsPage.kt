package app.lychee.settings

import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.RadioButton
import androidx.compose.ui.draw.clip
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
    PackMessage(actions)
    AddonSheetHost(actions)

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
            FontOption("", stringResource(R.string.lychee_fonts_noto_sans), gsfSummary, face = { LycheeFonts.noto(it) }),
            FontOption(LycheeFonts.GSF, gsf, gsfSummary, face = { LycheeFonts.gsf(it) }),
            FontOption(LycheeFonts.WENKAI, wenkai, stringResource(R.string.lychee_fonts_wenkai_keys_summary), face = { LycheeFonts.wenkaiKeys(it) }),
            FontOption(LycheeFonts.SYSTEM, system, systemSummary, face = { Typeface.SANS_SERIF }),
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
    // emoji: Noto Color Emoji built in at its newest; Noto 3D to download; the phone's own; your own
    FontSection(
        stringResource(R.string.lychee_fonts_emoji), LycheeFonts.PREF_EMOJI, stringResource(R.string.lychee_fonts_sample_emoji), actions,
        listOf(
            FontOption("", stringResource(R.string.lychee_fonts_noto), stringResource(R.string.lychee_fonts_noto_summary),
                face = { LycheeFonts.bundledEmoji(it) }, emoji = true),
        ) + Packs.ofKind(context, PackKind.FONT_EMOJI).filter { !it.custom }.map {
            FontOption(it.id, it.name, listOf(it.summary, it.size).filter { s -> s.isNotEmpty() }.joinToString(" · "), pack = it, emoji = true)
        } + FontOption(LycheeFonts.SYSTEM, stringResource(R.string.lychee_fonts_system_emoji), stringResource(R.string.lychee_fonts_system_emoji_summary),
            face = { Typeface.DEFAULT }, emoji = true) +
            ownFont(context, PackKind.FONT_EMOJI).map { it.copy(emoji = true) },
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

    // the section's font as it is now, large, then each choice as a row in its own font
    val shown = options.firstOrNull { it.value == current }
    Group(title) {
        row { FontPreview(shown, sample, actions.version) }
        options.forEach { option ->
            row {
                FontCard(option, sample, selected = current == option.value, actions,
                    onSelect = {
                        choice.set(option.value)
                        onChosen(option.value)
                    },
                    onWant = { wanted = option.value })
            }
        }
    }
}

/** The face of [option]: built in, or its installed pack's font; null when it isn't here. */
@Composable
private fun optionFamily(option: FontOption?, version: Int): FontFamily? {
    val context = LocalContext.current
    val pack = option?.pack
    val installed = pack == null || Packs.isInstalled(context, pack.id)
    return remember(option?.value, pack?.id, installed, version) {
        val face = when {
            option == null -> null
            option.face != null -> option.face.invoke(context)
            pack != null && installed -> Packs.mainFile(context, pack.id)?.let { PackImporter.loadFont(it) }
            else -> null
        }
        face?.let { FontFamily(it) }
    }
}

/** The chosen font's sample, large, so a change shows at once. */
@Composable
private fun FontPreview(option: FontOption?, sample: String, version: Int) {
    val family = optionFamily(option, version)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            sample, fontFamily = family,
            fontSize = if (option?.emoji == true) 34.sp else 30.sp, lineHeight = 42.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface,
        )
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
                // the add-on's sheet walks through downloading and installing it
                onWant()
                AddonSheet.open = pack
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    // a list row: the radio, the name drawn in the font itself (or a picture of it), what it
    // is, and its button
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = select)
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .semantics { if (selected) contentDescription = "$title, selected" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = select)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            when {
                picture != null -> Image(
                    picture, contentDescription = null,
                    contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                    modifier = Modifier.height(26.dp).fillMaxWidth(),
                )
                family != null -> Text(
                    title, fontFamily = family, fontSize = 17.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = colors.onSurface,
                )
                else -> Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            }
            if (picture != null) Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
            }
        }
        when {
            pack == null -> {}
            !installed && own -> TextButton(onClick = select) { Text(stringResource(R.string.lychee_fonts_choose)) }
            !installed -> FilledTonalButton(onClick = select) { Text(stringResource(R.string.lychee_packs_get)) }
            own -> TextButton(onClick = { onWant(); actions.import(pack) }) { Text(stringResource(R.string.lychee_fonts_change)) }
            app.lychee.packs.Updates.hasUpdate(context, pack) ->
                FilledTonalButton(onClick = { actions.get(pack) }) { Text(stringResource(R.string.lychee_packs_update)) }
        }
    }
}
