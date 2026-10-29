package app.lychee.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lychee.packs.Pack
import app.lychee.packs.PackImporter
import app.lychee.packs.PackKind
import app.lychee.packs.Packs
import com.android.inputmethod.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State shared by the pack lists on a page: which pack an import is for, the file picker, and
 * the last import's outcome. [version] changes whenever packs change, so rows refresh.
 */
class PackActions internal constructor(
    val version: Int,
    val message: String?,
    val busy: Boolean,
    val get: (Pack) -> Unit,
    val import: (Pack?) -> Unit,
    val remove: (Pack) -> Unit,
    val dismiss: () -> Unit,
    val refresh: () -> Unit,
)

@Composable
fun rememberPackActions(): PackActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Pack?>(null) }
    val okText = stringResource(R.string.lychee_packs_imported)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val expected = target
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { PackImporter.import(context, uri, expected) }
            busy = false
            message = when (result) {
                is PackImporter.Result.Ok -> okText.format(result.pack.name)
                is PackImporter.Result.Error -> result.message
            }
            version++
        }
    }
    return PackActions(
        version = version,
        message = message,
        busy = busy,
        get = { pack ->
            Packs.download(context, pack)
            version++
        },
        import = { pack ->
            target = pack
            launcher.launch(arrayOf("application/zip", "application/octet-stream", "font/*", "application/x-font-ttf", "*/*"))
        },
        remove = { pack ->
            Packs.remove(context, pack.id)
            version++
        },
        dismiss = { message = null },
        refresh = { version++ },
    )
}

/** The outcome of the last import, until dismissed. */
@Composable
fun PackMessage(actions: PackActions) {
    val text = when {
        actions.busy -> stringResource(R.string.lychee_packs_importing)
        else -> actions.message
    } ?: return
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (!actions.busy) TextButton(onClick = actions.dismiss) { Text(stringResource(R.string.lychee_close)) }
        }
    }
}

/**
 * One pack, compact: its name, what it is and its size (or that it is installed), and one
 * button: Download (Update once installed). Installed packs can be removed. With [selected] and
 * [onSelect] it is also a choice (fonts), selectable once installed.
 */
@Composable
fun PackRow(
    @Suppress("UNUSED_PARAMETER") shape: Shape,
    pack: Pack,
    actions: PackActions,
    selected: Boolean? = null,
    onSelect: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val refresh = actions.version
    val installed = Packs.isInstalled(context, pack.id)
    val newer = if (installed) app.lychee.packs.Updates.newer(context, pack) else null
    val canSelect = onSelect != null && installed
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (canSelect) Modifier.clickable { onSelect?.invoke() } else Modifier)
            .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            RadioButton(selected = selected, onClick = { if (canSelect) onSelect?.invoke() }, enabled = canSelect)
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(pack.name, style = MaterialTheme.typography.bodyLarge)
            // an installed font shows itself
            if (installed && pack.kind.isFont) {
                val family = androidx.compose.runtime.remember(pack.id, actions.version) {
                    Packs.mainFile(context, pack.id)?.let { app.lychee.packs.PackImporter.loadFont(it) }
                        ?.let { androidx.compose.ui.text.font.FontFamily(it) }
                }
                if (family != null) {
                    Text(
                        if (pack.kind == app.lychee.packs.PackKind.FONT_EMOJI) "😀 🐱 🍜 🎉 ❤️" else "永 漢字 汉字 かな 한글",
                        fontFamily = family,
                        fontSize = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp),
                    )
                }
            }
            val detail = listOf(
                pack.summary,
                if (installed) stringResource(R.string.lychee_packs_installed_short) + " · " +
                    Packs.formatSize(Packs.sizeOnDisk(context, pack.id)) else pack.size,
            ).filter { it.isNotEmpty() }.joinToString(" · ")
            Text(
                detail, style = MaterialTheme.typography.bodyMedium,
                color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (newer != null) {
                Text(
                    stringResource(R.string.lychee_updates_new, newer), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (installed) {
                TextButton(
                    onClick = { actions.remove(pack) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Text(stringResource(R.string.lychee_packs_remove)) }
            }
        }
        // installed packs only update when the packs page said there is a newer version
        if (pack.download.isNotEmpty() || pack.url.isNotEmpty()) {
            if (!installed) {
                FilledTonalButton(onClick = { actions.get(pack) }) { Text(stringResource(R.string.lychee_packs_get)) }
            } else if (newer != null) {
                FilledTonalButton(onClick = { actions.get(pack) }) { Text(stringResource(R.string.lychee_packs_update)) }
            }
        }
    }
}

/**
 * After a Download: until the pack is installed, a reminder to tap Open on the browser's
 * notice, with a way to choose the downloaded file instead.
 */
@Composable
fun PackPending(actions: PackActions) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val refresh = actions.version + PackRefresh.tick
    val pack = Packs.pending(context) ?: return
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.lychee_packs_pending_title, pack.name), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.lychee_packs_pending_body), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { actions.import(pack) }, enabled = !actions.busy) {
                    Text(stringResource(R.string.lychee_packs_choose_file))
                }
                TextButton(onClick = { Packs.clearPending(context); actions.refresh() }) {
                    Text(stringResource(R.string.lychee_close))
                }
            }
        }
    }
}

/** Bumped when the settings come back to the front (e.g. from the browser), so pages re-read packs. */
object PackRefresh {
    var tick by androidx.compose.runtime.mutableIntStateOf(0)
}

/** Every pack of [kinds], under [title]. */
@Composable
fun PackGroup(title: String, kinds: Set<PackKind>, actions: PackActions) {
    val context = LocalContext.current
    // the packs that come with Lychee are not downloads
    val packs = remember { Packs.catalog(context).filter { it.kind in kinds && !it.custom && !app.lychee.packs.BundledPacks.isBundled(context, it.id) } }
    if (packs.isEmpty()) return
    Group(title) {
        packs.forEach { pack -> row { PackRow(it, pack, actions) } }
    }
}

/**
 * At the top of Downloads: the updates the packs page found for installed packs, with Update all
 * (the page downloads them) and a way to install the downloaded files; or, when nothing was
 * checked for a couple of weeks, a quiet reminder to check.
 */
@Composable
fun UpdatesCard() {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val refresh = PackRefresh.tick
    val updates = app.lychee.packs.Updates.available(context)
    val remind = updates.isEmpty() && app.lychee.packs.Updates.shouldRemind(context)
    if (updates.isEmpty() && !remind) return
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (updates.isNotEmpty()) {
                Text(
                    if (updates.size == 1) stringResource(R.string.lychee_updates_available_one)
                    else stringResource(R.string.lychee_updates_available, updates.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    updates.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.lychee_updates_available_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(onClick = { app.lychee.packs.Updates.check(context, all = true) }) {
                        Text(stringResource(R.string.lychee_updates_all))
                    }
                    TextButton(onClick = {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("lychee://updates?install=1"))
                                .setPackage(context.packageName),
                        )
                    }) { Text(stringResource(R.string.lychee_updates_install_downloaded)) }
                }
            } else {
                Text(
                    stringResource(R.string.lychee_updates_remind),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.lychee_updates_remind_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                androidx.compose.material3.Button(onClick = { app.lychee.packs.Updates.check(context) }) {
                    Text(stringResource(R.string.lychee_updates_check))
                }
            }
        }
    }
}

/** Settings > Downloads: everything Lychee can use but doesn't include, in one place. */
@Composable
fun PacksPage() {
    val context = LocalContext.current
    val actions = rememberPackActions()
    @Suppress("UNUSED_VARIABLE") val refresh = actions.version + PackRefresh.tick
    remember { Packs.removeUnlisted(context) }
    Text(
        stringResource(R.string.lychee_packs_how),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    PackPending(actions)
    PackMessage(actions)
    UpdatesCard()
    PackGroup(stringResource(R.string.lychee_packs_handwriting), setOf(PackKind.HANDWRITING), actions)
    PackGroup(stringResource(R.string.lychee_packs_dictionaries), setOf(PackKind.RIME, PackKind.DATA), actions)
    PackGroup(stringResource(R.string.lychee_packs_fonts_emoji), setOf(PackKind.FONT_EMOJI), actions)
    PackGroup(stringResource(R.string.lychee_packs_voice), setOf(PackKind.VOICE), actions)
    Group(stringResource(R.string.lychee_section_more)) {
        if (app.lychee.packs.Updates.installed(context).isNotEmpty()) {
            row {
                NavRow(it, stringResource(R.string.lychee_packs_updates), stringResource(R.string.lychee_packs_updates_summary)) {
                    app.lychee.packs.Updates.check(context)
                }
            }
        }
        row {
            NavRow(it, stringResource(R.string.lychee_packs_import_any), stringResource(R.string.lychee_packs_import_any_summary)) {
                actions.import(null)
            }
        }
        row {
            TextRow(
                it, stringResource(R.string.lychee_packs_storage),
                stringResource(R.string.lychee_packs_storage_used, Packs.formatSize(Packs.totalSize(context))),
            )
        }
    }
}
