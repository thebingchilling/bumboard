package app.bumboard.settings

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
import androidx.compose.material3.OutlinedButton
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
import app.bumboard.packs.Pack
import app.bumboard.packs.PackImporter
import app.bumboard.packs.PackKind
import app.bumboard.packs.Packs
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
    val okText = stringResource(R.string.bum_packs_imported)
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
        get = { pack -> if (pack.url.isNotEmpty()) Packs.openPage(context, pack.url) },
        import = { pack ->
            target = pack
            launcher.launch(arrayOf("*/*"))
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
        actions.busy -> stringResource(R.string.bum_packs_importing)
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
            if (!actions.busy) TextButton(onClick = actions.dismiss) { Text(stringResource(R.string.bum_close)) }
        }
    }
}

/**
 * One pack: its name, what it is, size and licence, whether it is installed, and Get / Import /
 * Remove. With [selected] and [onSelect] it is also a choice (fonts), selectable once installed.
 */
@Composable
fun PackRow(
    shape: Shape,
    pack: Pack,
    actions: PackActions,
    selected: Boolean? = null,
    onSelect: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val refresh = actions.version
    val installed = Packs.isInstalled(context, pack.id)
    val canSelect = onSelect != null && installed
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canSelect) Modifier.clickable { onSelect?.invoke() } else Modifier),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) {
                    RadioButton(selected = selected, onClick = { if (canSelect) onSelect?.invoke() }, enabled = canSelect)
                }
                Column(Modifier.weight(1f)) {
                    Text(pack.name, style = MaterialTheme.typography.bodyLarge)
                    if (pack.summary.isNotEmpty()) {
                        Text(pack.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val status = if (installed) {
                stringResource(
                    R.string.bum_packs_installed,
                    Packs.installedVersion(context, pack.id).orEmpty(),
                    Packs.formatSize(Packs.sizeOnDisk(context, pack.id)),
                )
            } else listOf(pack.size, pack.file).filter { it.isNotEmpty() }.joinToString(" · ")
            if (status.isNotEmpty()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (pack.license.isNotEmpty()) {
                Text(pack.license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (pack.url.isNotEmpty()) {
                    FilledTonalButton(onClick = { actions.get(pack) }) {
                        Text(stringResource(if (installed) R.string.bum_packs_update else R.string.bum_packs_get))
                    }
                }
                OutlinedButton(onClick = { actions.import(pack) }, enabled = !actions.busy) {
                    Text(stringResource(R.string.bum_packs_import))
                }
                if (installed) {
                    TextButton(onClick = { actions.remove(pack) }) { Text(stringResource(R.string.bum_packs_remove)) }
                }
            }
        }
    }
}

/** Every pack of [kinds], under [title]. */
@Composable
fun PackGroup(title: String, kinds: Set<PackKind>, actions: PackActions) {
    val context = LocalContext.current
    val packs = remember { Packs.catalog(context).filter { it.kind in kinds && !it.custom } }
    if (packs.isEmpty()) return
    Group(title) {
        packs.forEach { pack -> row { PackRow(it, pack, actions) } }
    }
}

/** Settings > Packs: everything BumBoard can use but doesn't include, in one place. */
@Composable
fun PacksPage() {
    val context = LocalContext.current
    val actions = rememberPackActions()
    @Suppress("UNUSED_VARIABLE") val refresh = actions.version
    Text(
        stringResource(R.string.bum_packs_how),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
    PackMessage(actions)
    Group {
        row {
            TextRow(
                it, stringResource(R.string.bum_packs_storage),
                stringResource(R.string.bum_packs_storage_used, Packs.formatSize(Packs.totalSize(context))),
            )
        }
        row {
            NavRow(it, stringResource(R.string.bum_packs_import_any), stringResource(R.string.bum_packs_import_any_summary)) {
                actions.import(null)
            }
        }
        row {
            NavRow(it, stringResource(R.string.bum_packs_updates), stringResource(R.string.bum_packs_updates_summary)) {
                Packs.catalog(context)
                Packs.openPage(context, Packs.releasePage)
            }
        }
    }
    PackGroup(stringResource(R.string.bum_packs_fonts_zh), setOf(PackKind.FONT_ZH), actions)
    PackGroup(stringResource(R.string.bum_packs_fonts_emoji), setOf(PackKind.FONT_EMOJI), actions)
    PackGroup(stringResource(R.string.bum_packs_handwriting), setOf(PackKind.HANDWRITING), actions)
    PackGroup(stringResource(R.string.bum_packs_dictionaries), setOf(PackKind.RIME), actions)
    PackGroup(stringResource(R.string.bum_packs_voice), setOf(PackKind.VOICE), actions)
}
