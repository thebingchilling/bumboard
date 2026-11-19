@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lychee.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lychee.packs.Pack
import app.lychee.packs.PackKind
import app.lychee.packs.PackStorage
import app.lychee.packs.Packs
import com.android.inputmethod.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The add-on whose sheet is open (any page can open it, e.g. Storage's "Download again"). */
object AddonSheet {
    var open by mutableStateOf<Pack?>(null)
}

/**
 * Settings > Add-ons, a store: a header with what is on the phone and the ways to get more,
 * Google handwriting, then every add-on as a store row by language (Chinese, Japanese, Korean,
 * English, then Others): a picture of what it is, its name and what it is (grey once
 * installed), its size and version on the line under, and Get, Restore or remove. Free and paid add-ons sit side by
 * side; for now all are free (no billing yet). Tapping one opens its sheet, which walks through
 * getting it: download it (the browser does that; Lychee has no internet access), then open the
 * downloaded file, or choose it here. Your own fonts are chosen in Settings > Fonts, not here.
 */
@Composable
fun AddonsPage() {
    val context = LocalContext.current
    val actions = rememberPackActions()
    var version by remember { mutableIntStateOf(0) }
    val tick = version + actions.version + PackRefresh.tick
    remember { Packs.removeUnlisted(context) }
    val total = remember(tick) { Packs.totalSize(context) }
    val colors = MaterialTheme.colorScheme
    val catalog = remember { Packs.catalog(context) }

    // the store's header: a tinted banner with what is on the phone, updates and all add-ons
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.lychee_store_tagline), style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface)
        Text(stringResource(R.string.lychee_addons_used, Packs.formatSize(total)), style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
        // short labels with their icons, on one line (never wrapping)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (app.lychee.packs.Updates.installed(context).isNotEmpty()) {
                Button(onClick = { app.lychee.packs.Updates.check(context) }) {
                    Icon(painterResource(R.drawable.ic_sym_refresh), null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.lychee_addons_updates_short), maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
            // every add-on there is, installed or not, on the packs page
            FilledTonalButton(onClick = { app.lychee.packs.Updates.openAll(context) }) {
                Icon(painterResource(R.drawable.ic_sym_open_in_new), null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.lychee_addons_page), maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    PackMessage(actions)
    UpdatesCard()
    // the higher-quality handwriting, first in the store
    GoogleHandwritingCard()

    // by language (Chinese, Japanese, Korean, English), then the others
    val sections = PackStorage.Section.entries.filter { sec -> sec != PackStorage.Section.OWN && catalog.any { PackStorage.section(it) == sec } }
    for (section in sections) {
        val packs = catalog.filter { PackStorage.section(it) == section }
        SectionTitle(stringResource(sectionName(section)))
        packs.forEach { pack -> AddonCard(pack, tick, actions, onRemove = { PackStorage.remove(context, pack); version++ }) { version++ } }
    }
    // (your own fonts are chosen where fonts are picked: Settings > Fonts)
    Spacer(Modifier.height(16.dp))
    AddonSheetHost(actions)
}

/**
 * The add-ons store's banner at the top of Settings: emoji, kana, Hangul and a character on
 * tonal tiles over a gradient, the store's name and what is in it; a tap opens the store.
 */
@Composable
fun StoreBanner(onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .background(colors.surfaceContainerLow)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.lychee_addons_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Text(stringResource(R.string.lychee_store_tagline), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
                FilledTonalButton(onClick = onOpen, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.lychee_store_browse))
                }
            }
            // a little stack of what is inside
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { BannerTile("\uD83D\uDE00"); BannerTile("\u3042") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { BannerTile("\uD55C"); BannerTile("\u6C38") }
            }
        }
    }
}

@Composable
private fun BannerTile(text: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = colors.onSurface, fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)) }
}

/**
 * Google handwriting: ML Kit's recognition through Lychee's companion app. Says plainly that
 * Google's ML Kit licence is why a companion app is needed (and its size), then lets you get it
 * (the browser downloads it), or, once installed, open it for its models and updates, or
 * remove it.
 */
@Composable
private fun GoogleHandwritingCard() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    // re-checked whenever the page comes back (after installing or removing it)
    @Suppress("UNUSED_VARIABLE") val refresh = PackRefresh.tick
    val installed = app.lychee.handwriting.GoogleHandwriting.installed(context)
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text("\u270D\uFE0F", fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)) }
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.lychee_google_hw), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge(stringResource(R.string.lychee_store_free), strong = false)
                        Badge(stringResource(R.string.lychee_google_hw_quality), strong = false)
                        if (installed) Badge(stringResource(R.string.lychee_addons_installed), strong = true, check = true)
                    }
                }
            }
            Text(stringResource(R.string.lychee_google_hw_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            Text(stringResource(R.string.lychee_google_hw_licence, stringResource(R.string.lychee_companion_size)),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (!installed) {
                Text(stringResource(R.string.lychee_google_hw_steps), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Button(onClick = { app.lychee.handwriting.GoogleHandwriting.download(context) }) {
                    Icon(painterResource(R.drawable.ic_sym_download), null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.lychee_google_hw_get), maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { app.lychee.handwriting.GoogleHandwriting.openSetup(context) }) {
                        Icon(painterResource(R.drawable.ic_sym_refresh), null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.lychee_google_hw_setup), maxLines = 1, softWrap = false,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = { app.lychee.handwriting.GoogleHandwriting.openAppInfo(context) }) {
                        Icon(painterResource(R.drawable.lychee_ic_delete), null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.lychee_google_hw_remove), maxLines = 1, softWrap = false,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** What an add-on is, in a few characters for its picture (emoji, kana, Hangul, a character). */
private fun sampleOf(pack: Pack): String = when {
    pack.kind == PackKind.FONT_EMOJI -> "\uD83D\uDE00"
    pack.kind == PackKind.FONT_JA || pack.id == "rime_ja" -> "\u3042"
    pack.kind == PackKind.FONT_KO || pack.id == "dict_ko" -> "\uD55C"
    pack.kind == PackKind.FONT_KEYS || pack.id == "dict_en" -> "Aa"
    pack.id == "handwriting" -> "\u270D\uFE0F"
    pack.id == "examples" -> "\u201C\u201D"
    else -> "\u6C38"
}

/** An add-on's picture: a rounded tonal tile with [sampleOf] in it. */
@Composable
private fun AddonTile(pack: Pack, size: androidx.compose.ui.unit.Dp, fontSize: androidx.compose.ui.unit.TextUnit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(colors.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(sampleOf(pack), color = colors.onSecondaryContainer, fontSize = fontSize)
    }
}

/** A section's heading on the add-ons page. */
@Composable
private fun SectionTitle(title: String) {
    // a Material 2 list's heading, a divider above it
    SectionDivider()
    GroupHeader(title)
}

/** [Settings > Add-ons] for screens that still link to the old Storage page. */
@Composable
fun StoragePage() = AddonsPage()

/** A display name for a pack: "your own" fonts say which language they are for. */
@Composable
fun addonName(pack: Pack): String = if (!pack.custom) pack.name else when (pack.kind) {
    PackKind.FONT_ZH -> stringResource(R.string.lychee_addons_own_font, stringResource(R.string.lychee_fonts_chinese))
    PackKind.FONT_JA -> stringResource(R.string.lychee_addons_own_font, stringResource(R.string.lychee_fonts_japanese))
    PackKind.FONT_KO -> stringResource(R.string.lychee_addons_own_font, stringResource(R.string.lychee_fonts_korean))
    PackKind.FONT_KEYS -> stringResource(R.string.lychee_addons_own_font, stringResource(R.string.lychee_fonts_keyboard))
    PackKind.FONT_EMOJI -> stringResource(R.string.lychee_addons_own_font, stringResource(R.string.lychee_fonts_emoji))
    else -> pack.name
}

/**
 * One add-on as a card: its name and what it is; under them a badge saying where it stands
 * (installed, comes with Lychee, removed, or not installed) and its size (on the phone, or to
 * download); at the end, remove it, bring it back, or get it.
 */
@Composable
private fun AddonCard(pack: Pack, tick: Int, actions: PackActions, onRemove: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val installed = remember(pack.id, tick) { Packs.isInstalled(context, pack.id) }
    val bundled = remember(pack.id) { app.lychee.packs.BundledPacks.isBundled(context, pack.id) }
    val size = remember(pack.id, tick, installed) { if (installed) Packs.formatSize(Packs.sizeOnDisk(context, pack.id)) else pack.size }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.material3.Surface(
        onClick = { AddonSheet.open = pack },
        // a Material 2 list row: full width, no card
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AddonTile(pack, 48.dp, androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp))
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // installed: quieter, in grey; still to get: in full colour
                Text(addonName(pack), style = MaterialTheme.typography.bodyLarge,
                    color = if (installed) colors.onSurfaceVariant else colors.onSurface, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (pack.summary.isNotEmpty()) {
                    Text(pack.summary, style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant.copy(alpha = if (installed) 0.7f else 1f), maxLines = 2)
                }
                // the size and version on a line of their own; where it stands before them
                val update = if (installed) app.lychee.packs.Updates.newer(context, pack) else null
                val status = when {
                    update != null -> stringResource(R.string.lychee_addons_update_to, update)
                    installed && bundled -> stringResource(R.string.lychee_addons_bundled)
                    installed -> stringResource(R.string.lychee_addons_installed)
                    bundled -> stringResource(R.string.lychee_storage_removed)
                    else -> pack.price ?: stringResource(R.string.lychee_store_free)
                }
                val shownVersion = if (installed) Packs.installedVersion(context, pack.id) else null
                Text(
                    listOfNotNull(status, size.takeIf { it.isNotEmpty() }, shownVersion?.takeIf { it.isNotEmpty() }?.let { "v$it" })
                        .joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (installed && update == null) colors.onSurfaceVariant else colors.primary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            when {
                busy -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                // a newer version (Updates found it): Update, one file at a time
                installed && app.lychee.packs.Updates.hasUpdate(context, pack) -> FilledTonalButton(
                    onClick = { AddonSheet.open = pack },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_sym_refresh), null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.lychee_addons_update_short), maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(start = 6.dp))
                }
                // removing asks first, in a menu dropped from the bin (not a dialog)
                installed -> Box {
                    var asking by remember { mutableStateOf(false) }
                    IconButton(onClick = { asking = true }) {
                        Icon(painterResource(R.drawable.lychee_ic_delete), stringResource(R.string.lychee_packs_remove), tint = colors.onSurfaceVariant)
                    }
                    M3Menu(expanded = asking, onDismissRequest = { asking = false }) {
                        val needed = pack.id == "rime_ja" || pack.id == "dict_ko" || pack.id == "dict_en"
                        Text(
                            stringResource(if (needed) R.string.lychee_storage_confirm_needed else R.string.lychee_storage_confirm_body,
                                Packs.formatSize(Packs.sizeOnDisk(context, pack.id))),
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        MenuItem(stringResource(R.string.lychee_packs_remove), icon = R.drawable.ic_sym_delete, color = colors.error) {
                            asking = false
                            onRemove()
                        }
                        MenuItem(stringResource(android.R.string.cancel), icon = R.drawable.ic_sym_close) { asking = false }
                    }
                }
                bundled -> TextButton(onClick = {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) { PackStorage.restore(context, pack) }
                        busy = false
                        onChanged()
                        actions.refresh()
                    }
                }) { Text(stringResource(R.string.lychee_storage_restore), maxLines = 1, softWrap = false) }
                // short, with its icon, never wrapping
                else -> FilledTonalButton(onClick = { AddonSheet.open = pack },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Icon(painterResource(if (pack.custom) R.drawable.ic_sym_folder_open else R.drawable.ic_sym_download), null,
                        modifier = Modifier.size(18.dp))
                    Text(stringResource(if (pack.custom) R.string.lychee_addons_choose_short else R.string.lychee_addons_get_short),
                        maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** A small rounded label: [strong] for installed (filled, with a tick), else a quiet outline. */
@Composable
private fun Badge(text: String, strong: Boolean, check: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (strong) colors.secondaryContainer else colors.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (check) Icon(painterResource(R.drawable.ic_sym_check), null, tint = colors.onSecondaryContainer,
            modifier = Modifier.size(14.dp).padding(end = 2.dp))
        Text(text, style = MaterialTheme.typography.labelMedium,
            color = if (strong) colors.onSecondaryContainer else colors.onSurfaceVariant)
    }
}

/** A section's title. */
private fun sectionName(section: PackStorage.Section) = when (section) {
    PackStorage.Section.CHINESE -> R.string.lychee_fonts_chinese
    PackStorage.Section.JAPANESE -> R.string.lychee_fonts_japanese
    PackStorage.Section.KOREAN -> R.string.lychee_fonts_korean
    PackStorage.Section.ENGLISH -> R.string.lychee_storage_english
    PackStorage.Section.MISC -> R.string.lychee_addons_misc
    PackStorage.Section.OWN -> R.string.lychee_addons_fonts
}

/**
 * The sheet for [AddonSheet.open]: what the add-on is (a picture of it when there is one), and
 * the steps to get it. 1 Download: the browser saves the file. 2 Install: open the file from the
 * browser's notice, or choose it here. Once it is installed the sheet says so (the page re-reads
 * the packs when Lychee comes back from the browser). Your own fonts have one step: choose it.
 */
@Composable
fun AddonSheetHost(actions: PackActions) {
    val pack = AddonSheet.open ?: return
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val refresh = actions.version + PackRefresh.tick
    val installed = Packs.isInstalled(context, pack.id)
    var downloaded by remember(pack.id) { mutableStateOf(Packs.pending(context)?.id == pack.id) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = { AddonSheet.open = null }, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(addonName(pack), style = MaterialTheme.typography.titleLarge)
            if (pack.summary.isNotEmpty()) Text(pack.summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            val picture = remember(pack.preview) {
                pack.preview?.let { path ->
                    runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
                }
            }
            if (picture != null) {
                Image(picture, null, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxWidth().height(48.dp))
            }
            val facts = listOf(pack.size, pack.license).filter { it.isNotEmpty() }.joinToString(" · ")
            if (facts.isNotEmpty()) Text(facts, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (actions.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            // an installed add-on with a newer version gets the download steps again
            if (installed && !app.lychee.packs.Updates.hasUpdate(context, pack)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_sym_check_circle), null, tint = colors.primary, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.lychee_addons_installed), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { AddonSheet.open = null }) { Text(stringResource(R.string.lychee_addons_done)) }
                    OutlinedButton(onClick = { actions.remove(pack) }) { Text(stringResource(R.string.lychee_packs_remove)) }
                }
            } else if (pack.custom) {
                Step(1, stringResource(R.string.lychee_addons_step_choose), active = true) {
                    Button(onClick = { actions.import(pack) }, enabled = !actions.busy) {
                        Text(stringResource(R.string.lychee_fonts_choose))
                    }
                }
            } else {
                // the file named as what it is and its version, so it is easy to find and choose
                val file = app.lychee.packs.Updates.downloadFile(context, pack)
                app.lychee.packs.Updates.newer(context, pack)?.takeIf { installed }?.let { v ->
                    Text(stringResource(R.string.lychee_addons_update_to, v), style = MaterialTheme.typography.titleMedium,
                        color = colors.primary)
                }
                Step(1, stringResource(R.string.lychee_addons_step_download_file, file, pack.size.let { if (it.isEmpty()) "" else " ($it)" }),
                    active = !downloaded, done = downloaded) {
                    Button(onClick = {
                        actions.get(pack)
                        downloaded = true
                    }) { Text(stringResource(R.string.lychee_packs_get)) }
                }
                // installed from here only: choose the downloaded file
                Step(2, stringResource(R.string.lychee_addons_step_choose_file, file), active = downloaded) {
                    FilledTonalButton(onClick = { actions.import(pack) }, enabled = downloaded && !actions.busy) {
                        Text(stringResource(R.string.lychee_packs_choose_file))
                    }
                }
            }
            actions.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.primary) }
        }
    }
}

/** One numbered step of getting an add-on; [active] is the one to do now. */
@Composable
private fun Step(number: Int, text: String, active: Boolean, done: Boolean = false, action: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (active || done) colors.primary else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(painterResource(R.drawable.ic_sym_check), null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
            } else {
                Text("$number", style = MaterialTheme.typography.labelLarge,
                    color = if (active) colors.onPrimary else colors.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge,
                color = if (active || done) colors.onSurface else colors.onSurfaceVariant)
            action()
        }
    }
}

/** [pack]'s display name outside composition (the dialog's title). */
private fun addonNameOf(context: android.content.Context, pack: Pack): String =
    if (pack.custom) context.getString(R.string.lychee_fonts_own) else pack.name

private fun areaName(area: PackStorage.Area) = when (area) {
    PackStorage.Area.CHINESE -> R.string.lychee_fonts_chinese
    PackStorage.Area.JAPANESE -> R.string.lychee_fonts_japanese
    PackStorage.Area.KOREAN -> R.string.lychee_fonts_korean
    PackStorage.Area.ENGLISH -> R.string.lychee_storage_english
    PackStorage.Area.SHARED -> R.string.lychee_storage_shared
    PackStorage.Area.EMOJI -> R.string.lychee_fonts_emoji
    PackStorage.Area.KEYBOARD -> R.string.lychee_fonts_keyboard
    PackStorage.Area.VOICE -> R.string.lychee_packs_voice
    PackStorage.Area.OTHER -> R.string.lychee_storage_other
}

private fun kindName(kind: PackStorage.Kind) = when (kind) {
    PackStorage.Kind.FONT -> R.string.lychee_storage_kind_font
    PackStorage.Kind.EMOJI -> R.string.lychee_storage_kind_emoji
    PackStorage.Kind.DICTIONARY -> R.string.lychee_storage_kind_dictionary
    PackStorage.Kind.STROKES -> R.string.lychee_storage_kind_strokes
    PackStorage.Kind.EXAMPLES -> R.string.lychee_storage_kind_examples
    PackStorage.Kind.HANDWRITING -> R.string.lychee_storage_kind_handwriting
    PackStorage.Kind.VOICE -> R.string.lychee_storage_kind_voice
    PackStorage.Kind.DATA -> R.string.lychee_storage_kind_data
}
