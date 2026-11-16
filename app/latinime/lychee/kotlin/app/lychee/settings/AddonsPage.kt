@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lychee.settings

import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
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
 * Settings > Add-ons: everything Lychee has or can add, the add-ons that come with it included,
 * as cards by language (then Misc), each saying plainly whether it is installed and how big it
 * is; your own fonts in a fold at the bottom. Tapping one opens its sheet, which walks through
 * getting it: download it (the browser does that; Lychee has no internet access), then open the
 * downloaded file, or choose it here. This is also where add-ons are removed.
 */
@Composable
fun AddonsPage() {
    val context = LocalContext.current
    val actions = rememberPackActions()
    var version by remember { mutableIntStateOf(0) }
    val tick = version + actions.version + PackRefresh.tick
    remember { Packs.removeUnlisted(context) }
    val total = remember(tick) { Packs.totalSize(context) }
    // a removal waiting for "Remove" in its dialog
    var confirm by remember { mutableStateOf<Pack?>(null) }
    val colors = MaterialTheme.colorScheme

    // how much is on the phone, and the ways to get more
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.lychee_addons_used, Packs.formatSize(total)), style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface)
        Text(stringResource(R.string.lychee_addons_how), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (app.lychee.packs.Updates.installed(context).isNotEmpty()) {
                FilledTonalButton(onClick = { app.lychee.packs.Updates.check(context) }) {
                    Text(stringResource(R.string.lychee_packs_updates))
                }
            }
            OutlinedButton(onClick = { actions.import(null) }) { Text(stringResource(R.string.lychee_addons_from_file)) }
        }
    }
    PackMessage(actions)
    UpdatesCard()
    val catalog = remember { Packs.catalog(context) }
    for (section in PackStorage.Section.entries) {
        if (section == PackStorage.Section.OWN) continue
        val packs = catalog.filter { PackStorage.section(it) == section }
        if (packs.isEmpty()) continue
        SectionTitle(stringResource(sectionName(section)))
        packs.forEach { pack -> AddonCard(pack, tick, actions, onRemove = { confirm = pack }) { version++ } }
    }
    // your own fonts: folded away at the bottom
    val own = catalog.filter { PackStorage.section(it) == PackStorage.Section.OWN }
    if (own.isNotEmpty()) {
        SectionDivider()
        var open by remember { mutableStateOf(own.any { Packs.isInstalled(context, it.id) }) }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.lychee_addons_fonts), style = MaterialTheme.typography.titleSmall,
                color = colors.primary, modifier = Modifier.weight(1f))
            Icon(painterResource(if (open) R.drawable.ic_sym_keyboard_arrow_up else R.drawable.ic_sym_keyboard_arrow_down),
                null, tint = colors.onSurfaceVariant)
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Column { own.forEach { pack -> AddonCard(pack, tick, actions, onRemove = { confirm = pack }) { version++ } } }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = { app.lychee.packs.Updates.openAll(context) }) {
            Icon(painterResource(R.drawable.ic_sym_open_in_new), null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.lychee_addons_page), modifier = Modifier.padding(start = 8.dp))
        }
    }
    confirm?.let { pack ->
        val needed = pack.id == "rime_ja" || pack.id == "dict_ko" || pack.id == "dict_en"
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.lychee_storage_confirm_title, addonNameOf(context, pack))) },
            text = {
                Text(stringResource(if (needed) R.string.lychee_storage_confirm_needed else R.string.lychee_storage_confirm_body,
                    Packs.formatSize(Packs.sizeOnDisk(context, pack.id))))
            },
            confirmButton = {
                TextButton(onClick = {
                    PackStorage.remove(context, pack)
                    confirm = null
                    version++
                }) { Text(stringResource(R.string.lychee_packs_remove), color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    AddonSheetHost(actions)
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(addonName(pack), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                if (pack.summary.isNotEmpty()) {
                    Text(pack.summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val status = when {
                        installed && bundled -> stringResource(R.string.lychee_addons_bundled)
                        installed -> stringResource(R.string.lychee_addons_installed)
                        bundled -> stringResource(R.string.lychee_storage_removed)
                        else -> stringResource(R.string.lychee_addons_not_installed)
                    }
                    Badge(status, strong = installed, check = installed)
                    if (size.isNotEmpty()) Badge(size, strong = false)
                }
            }
            when {
                busy -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                installed -> IconButton(onClick = onRemove) {
                    Icon(painterResource(R.drawable.lychee_ic_delete), stringResource(R.string.lychee_packs_remove), tint = colors.onSurfaceVariant)
                }
                bundled -> TextButton(onClick = {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) { PackStorage.restore(context, pack) }
                        busy = false
                        onChanged()
                        actions.refresh()
                    }
                }) { Text(stringResource(R.string.lychee_storage_restore)) }
                else -> FilledTonalButton(onClick = { AddonSheet.open = pack }) {
                    Text(stringResource(if (pack.custom) R.string.lychee_fonts_choose else R.string.lychee_packs_get))
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
            .clip(RoundedCornerShape(8.dp))
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
