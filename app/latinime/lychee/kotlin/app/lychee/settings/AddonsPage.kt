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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * Settings > Add-ons: everything Lychee can add, by kind. Tapping one opens its sheet, which
 * walks through getting it: download it (the browser does that; Lychee has no internet access),
 * then open the downloaded file, or choose it here; the sheet notices once it is installed.
 */
@Composable
fun AddonsPage(openStorage: () -> Unit) {
    val context = LocalContext.current
    val actions = rememberPackActions()
    @Suppress("UNUSED_VARIABLE") val refresh = actions.version + PackRefresh.tick
    remember { Packs.removeUnlisted(context) }
    Text(
        stringResource(R.string.lychee_addons_how),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    PackMessage(actions)
    UpdatesCard()
    // every add-on, installed or not, those that come with Lychee too: by language, then
    // everything else, then your own files
    val catalog = remember { Packs.catalog(context) }
    for (section in PackStorage.Section.entries) {
        val packs = catalog.filter { PackStorage.section(it) == section }
        Group(stringResource(sectionName(section))) {
            packs.forEach { pack -> row { AddonRow(pack, actions) { AddonSheet.open = pack } } }
        }
    }
    Group(stringResource(R.string.lychee_section_more)) {
        // the packs page checks every installed add-on, those that come with Lychee included
        if (app.lychee.packs.Updates.installed(context).isNotEmpty()) {
            row {
                NavRow(it, stringResource(R.string.lychee_packs_updates), stringResource(R.string.lychee_packs_updates_summary),
                    R.drawable.ic_sym_download) { app.lychee.packs.Updates.check(context) }
            }
        }
        row {
            NavRow(it, stringResource(R.string.lychee_addons_page), stringResource(R.string.lychee_addons_page_summary),
                R.drawable.ic_sym_open_in_new) { app.lychee.packs.Updates.openAll(context) }
        }
        row {
            NavRow(it, stringResource(R.string.lychee_packs_import_any), stringResource(R.string.lychee_packs_import_any_summary),
                R.drawable.lychee_ic_input) { actions.import(null) }
        }
        row {
            NavRow(it, stringResource(R.string.lychee_storage_title),
                stringResource(R.string.lychee_packs_storage_used, Packs.formatSize(Packs.totalSize(context))),
                R.drawable.ic_sym_grid_view, openStorage)
        }
    }
    AddonSheetHost(actions)
}

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
 * One add-on: its name, what it is, and where it stands: its size to download, or installed
 * (with its size on the phone; "comes with Lychee" for those in the app) and a delete button,
 * or removed with Restore (one that comes with Lychee).
 */
@Composable
private fun AddonRow(pack: Pack, actions: PackActions, onClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = actions.version + PackRefresh.tick
    val installed = remember(pack.id, tick) { Packs.isInstalled(context, pack.id) }
    val bundled = remember(pack.id) { app.lychee.packs.BundledPacks.isBundled(context, pack.id) }
    val size = remember(pack.id, tick, installed) { if (installed) Packs.formatSize(Packs.sizeOnDisk(context, pack.id)) else "" }
    var busy by remember { mutableStateOf(false) }
    val detail = listOf(
        pack.summary,
        when {
            installed && bundled -> stringResource(R.string.lychee_addons_bundled) + " · " + size
            installed -> stringResource(R.string.lychee_packs_installed_short) + " · " + size
            bundled -> stringResource(R.string.lychee_storage_removed)
            else -> pack.size
        },
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        CompactRow(null, addonName(pack), detail, summaryColor = if (installed) MaterialTheme.colorScheme.primary else null, trailing = {
            when {
                busy -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                installed -> IconButton(onClick = {
                    PackStorage.remove(context, pack)
                    actions.refresh()
                }) { Icon(painterResource(R.drawable.lychee_ic_delete), stringResource(R.string.lychee_packs_remove)) }
                bundled -> TextButton(onClick = {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) { PackStorage.restore(context, pack) }
                        busy = false
                        actions.refresh()
                    }
                }) { Text(stringResource(R.string.lychee_storage_restore)) }
                else -> FilledTonalButton(onClick = onClick) {
                    Text(stringResource(if (pack.custom) R.string.lychee_fonts_choose else R.string.lychee_packs_get))
                }
            }
        })
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
            Text(addonName(pack), style = MaterialTheme.typography.headlineSmall)
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
            if (installed) {
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
                Step(1, stringResource(R.string.lychee_addons_step_download, pack.size.ifEmpty { "" }.let { if (it.isEmpty()) "" else " ($it)" }),
                    active = !downloaded, done = downloaded) {
                    Button(onClick = {
                        actions.get(pack)
                        downloaded = true
                    }) { Text(stringResource(R.string.lychee_packs_get)) }
                }
                Step(2, stringResource(R.string.lychee_addons_step_install), active = downloaded) {
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

// ---------------------------------------------------------------- storage

/**
 * Settings > Storage: everything Lychee keeps, by language (Chinese, Japanese, Korean, English,
 * what they share) and kind (emoji, keyboard fonts, voices), with sizes. Remove one thing, or a
 * whole language at once; a removed pack can be brought back (restored from the app, or
 * downloaded again from its add-on sheet).
 */
@Composable
fun StoragePage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = rememberPackActions()
    var version by remember { mutableIntStateOf(0) }
    val tick = version + actions.version + PackRefresh.tick
    val entries = remember(tick) { PackStorage.entries(context) }
    val total = remember(tick) { Packs.totalSize(context) }
    // a removal waiting for "Remove" in its dialog
    var confirm by remember { mutableStateOf<Pair<String, List<Pack>>?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(Packs.formatSize(total), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.lychee_storage_body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    }
    if (entries.isEmpty()) {
        Text(stringResource(R.string.lychee_storage_empty), style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp))
    }
    // by language, then everything else, then your own files; each removed on its own
    val bySection = entries.values.flatten().groupBy { PackStorage.section(it.pack) }.toSortedMap(compareBy { it.ordinal })
    for ((section, list) in bySection) {
        val title = stringResource(sectionName(section))
        val installed = list.filter { it.installed }
        Group(title + " · " + Packs.formatSize(installed.sumOf { it.bytes })) {
            list.forEach { entry ->
                row {
                    CompactRow(
                        null, addonName(entry.pack),
                        if (entry.installed) stringResource(kindName(PackStorage.kind(entry.pack))) + " · " + Packs.formatSize(entry.bytes)
                        else stringResource(R.string.lychee_storage_removed),
                        trailing = {
                            when {
                                entry.installed -> IconButton(onClick = { confirm = addonNameOf(context, entry.pack) to listOf(entry.pack) }) {
                                    Icon(painterResource(R.drawable.lychee_ic_delete), stringResource(R.string.lychee_packs_remove))
                                }
                                entry.bundled -> TextButton(onClick = {
                                    busy = true
                                    scope.launch {
                                        withContext(Dispatchers.IO) { PackStorage.restore(context, entry.pack) }
                                        busy = false
                                        version++
                                    }
                                }) { Text(stringResource(R.string.lychee_storage_restore)) }
                                else -> TextButton(onClick = { AddonSheet.open = entry.pack }) {
                                    Text(stringResource(R.string.lychee_storage_download_again))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    confirm?.let { (name, packs) ->
        val needed = packs.any { it.id == "rime_ja" || it.id == "dict_ko" || it.id == "dict_en" }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.lychee_storage_confirm_title, name)) },
            text = {
                Text(stringResource(if (needed) R.string.lychee_storage_confirm_needed else R.string.lychee_storage_confirm_body,
                    Packs.formatSize(packs.sumOf { Packs.sizeOnDisk(context, it.id) })))
            },
            confirmButton = {
                TextButton(onClick = {
                    packs.forEach { PackStorage.remove(context, it) }
                    confirm = null
                    version++
                }) { Text(stringResource(R.string.lychee_packs_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    AddonSheetHost(actions)
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
