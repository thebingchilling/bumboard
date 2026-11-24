@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lychee.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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

/** Add-ons' three tabs, like Pleco's: the store, what is on the phone, and updates. */
private enum class StoreTab(val title: String) { STORE("Store"), INSTALLED("Installed"), UPDATES("Updates") }

/** The store's categories, each a page of its own. */
private enum class Browse(val title: String, val about: String) {
    DICTIONARIES("Dictionaries", "In-depth word cards, and the free dictionaries"),
    HANDWRITING("Handwriting", "Google's ML Kit recognition for the handwriting pad"),
    LEARNING("Learning tools", "Stroke order and example sentences"),
    FONTS("Fonts & emoji", "Chinese, Japanese and Korean fonts, and emoji styles"),
    FREE("All free downloads", "Everything free, by language"),
}

/**
 * Settings > Add-ons, a store laid out like Pleco's: three tabs (Store, Installed, Updates). The
 * store: a one-line introduction, the featured bundles, then categories, each opening a page of
 * short rows (name, one line, price or size, one action at the right); every listing opens its
 * own page (ProductSheet, or a free download's sheet). The registration sits at the very bottom.
 */
@Composable
fun AddonsPage() {
    val context = LocalContext.current
    val actions = rememberPackActions()
    var version by remember { mutableIntStateOf(0) }
    val tick = version + actions.version + PackRefresh.tick
    remember { Packs.removeUnlisted(context) }
    val catalog = remember { Packs.catalog(context) }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(StoreTab.STORE) }
    var browse by remember { mutableStateOf<Browse?>(null) }
    androidx.activity.compose.BackHandler(enabled = browse != null) { browse = null }
    val colors = MaterialTheme.colorScheme

    androidx.compose.material3.PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = colors.surface) {
        StoreTab.entries.forEach { t ->
            androidx.compose.material3.Tab(selected = tab == t, onClick = { tab = t; browse = null },
                text = { Text(t.title, maxLines = 1) })
        }
    }
    PackMessage(actions)
    val remove = { pack: Pack -> PackStorage.remove(context, pack); version++ }
    when (tab) {
        StoreTab.STORE -> {
            val open = browse
            if (open == null) StoreHome { browse = it }
            else BrowsePage(open, catalog, tick, actions, onBack = { browse = null }, onRemove = remove) { version++ }
        }
        StoreTab.INSTALLED -> {
            val total = remember(tick) { Packs.totalSize(context) }
            Text(stringResource(R.string.lychee_addons_used, Packs.formatSize(total)), style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            val installed = catalog.filter { Packs.isInstalled(context, it.id) }
            if (installed.isEmpty()) {
                Text("Nothing downloaded yet. The Store has every add-on.", style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            for (section in PackStorage.Section.entries) {
                val packs = installed.filter { PackStorage.section(it) == section }
                if (packs.isEmpty()) continue
                SectionTitle(stringResource(sectionName(section)))
                packs.forEach { pack -> AddonCard(pack, tick, actions, onRemove = { remove(pack) }) { version++ } }
            }
        }
        StoreTab.UPDATES -> {
            UpdatesCard()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lychee never goes online, so updates are checked on the add-ons page in your browser: " +
                    "it lists what is newer than yours, and each update downloads as a file named with its version.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (app.lychee.packs.Updates.installed(context).isNotEmpty()) {
                        Button(onClick = { app.lychee.packs.Updates.check(context) }) {
                            Icon(painterResource(R.drawable.ic_sym_refresh), null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.lychee_addons_updates_short), maxLines = 1, softWrap = false,
                                modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    FilledTonalButton(onClick = { app.lychee.packs.Updates.openAll(context) }) {
                        Icon(painterResource(R.drawable.ic_sym_open_in_new), null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.lychee_addons_page), maxLines = 1, softWrap = false,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    AddonSheetHost(actions)
    ProductSheetHost()
}

/** The store's first page: one line about Lychee, the bundles, the categories, the registration. */
@Composable
private fun StoreHome(onBrowse: (Browse) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val unavailable = remember { app.lychee.store.Store.unavailableReason(context) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Made by one student developer. One-time purchases, no subscriptions, no ads, no tracking; " +
            "Lychee never goes online.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (app.lychee.store.Store.FREE_FOR_NOW) {
            Note("Everything is free while Lychee is in testing. Prices show what each will cost.",
                colors.tertiaryContainer, colors.onTertiaryContainer)
        }
        unavailable?.let { Note(it, colors.errorContainer, colors.onErrorContainer) }
    }
    StoreHeader("Featured")
    (listOf(app.lychee.store.Store.FULL) + app.lychee.store.Store.packs).forEach { p ->
        val value = app.lychee.store.Store.value(p)
        StoreRow(p.name, p.tagline,
            "${app.lychee.store.Store.price(p.cents)} · ${p.includes.size} add-ons, save ${app.lychee.store.Store.price(value - p.cents)}",
            StoreAction.Price(app.lychee.store.Store.price(p.cents))) { ProductSheet.open = p }
    }
    StoreHeader("Browse")
    Browse.entries.forEach { b -> StoreRow(b.title, b.about, null, StoreAction.Open) { onBrowse(b) } }
    RegistrationSection()
}

/** A category's page: its paid add-ons, then its free downloads by language. */
@Composable
private fun BrowsePage(
    browse: Browse, catalog: List<Pack>, tick: Int, actions: PackActions,
    onBack: () -> Unit, onRemove: (Pack) -> Unit, onChanged: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_sym_arrow_back), stringResource(R.string.lychee_card_back)) }
        Text(browse.title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
    }
    val S = app.lychee.store.Store
    val paid = when (browse) {
        Browse.DICTIONARIES -> S.addons.filter { it.category == app.lychee.store.Store.Category.DICTIONARIES }
        Browse.HANDWRITING -> listOf(S.HANDWRITING)
        Browse.LEARNING -> listOf(S.STROKES, S.EXAMPLES)
        Browse.FONTS -> listOf(S.FONTS)
        Browse.FREE -> emptyList()
    }
    if (paid.isNotEmpty()) {
        StoreHeader("Add-ons")
        paid.forEach { p ->
            val packs = S.includedIn(p.id).joinToString(", ") { it.name.removeSuffix(" Pack") }
            StoreRow(p.name, p.tagline, "${S.price(p.cents)} · in $packs", StoreAction.Price(S.price(p.cents))) { ProductSheet.open = p }
        }
    }
    val kinds = when (browse) {
        Browse.DICTIONARIES -> setOf(PackStorage.Kind.DICTIONARY)
        Browse.LEARNING -> setOf(PackStorage.Kind.STROKES, PackStorage.Kind.EXAMPLES)
        Browse.FONTS -> setOf(PackStorage.Kind.FONT, PackStorage.Kind.EMOJI)
        Browse.HANDWRITING -> emptySet()
        Browse.FREE -> null
    }
    val free = catalog.filter { !it.custom && (kinds == null || PackStorage.kind(it) in kinds) }
    if (free.isNotEmpty()) {
        StoreHeader("Free downloads")
        for (section in PackStorage.Section.entries) {
            val packs = free.filter { PackStorage.section(it) == section }
            if (packs.isEmpty()) continue
            SectionTitle(stringResource(sectionName(section)))
            packs.forEach { pack -> AddonCard(pack, tick, actions, onRemove = { onRemove(pack) }, onChanged = onChanged) }
        }
    }
}

/** What a store row's right-hand tile shows. */
private sealed class StoreAction {
    class Price(val text: String) : StoreAction()
    object Open : StoreAction()
}

/** A section heading of the store: the accent colour, with a thin line above. */
@Composable
private fun StoreHeader(title: String) {
    SectionDivider()
    GroupHeader(title)
}

/**
 * One row of the store, like Pleco's: the name, one line on what it is, the price (or size), and
 * a single action in a tinted tile at the right. The whole row opens the listing.
 */
@Composable
private fun StoreRow(title: String, line: String?, meta: String?, action: StoreAction, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            line?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            meta?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary) }
        }
        when (action) {
            is StoreAction.Price -> Box(
                Modifier.fillMaxHeight().widthIn(min = 76.dp).background(colors.primaryContainer).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action.text, style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer, maxLines = 1) }
            StoreAction.Open -> Icon(painterResource(R.drawable.ic_sym_chevron_right), null, tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp))
        }
    }
    androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 16.dp), color = colors.outlineVariant.copy(alpha = 0.5f))
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
                    color = if (installed) colors.onSurfaceVariant else colors.onSurface)
                if (pack.summary.isNotEmpty()) {
                    Text(pack.summary, style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant.copy(alpha = if (installed) 0.7f else 1f))
                }
                // the paid add-on this download belongs to, and the packs that include it
                app.lychee.store.Store.forPack(pack.id)?.let { product ->
                    Text(
                        product.name + " · in " + app.lychee.store.Store.includedIn(product.id)
                            .joinToString(", ") { it.name.removePrefix("Lychee ") },
                        style = MaterialTheme.typography.labelSmall, color = colors.primary, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
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
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // room between what it is and what can be done with it
            Spacer(Modifier.size(12.dp))
            when {
                busy -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                // a newer version (Updates found it): Update, one file at a time
                installed && app.lychee.packs.Updates.hasUpdate(context, pack) ->
                    androidx.compose.material3.FilledTonalIconButton(onClick = { AddonSheet.open = pack }) {
                        Icon(painterResource(R.drawable.ic_sym_refresh), stringResource(R.string.lychee_addons_update_short))
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
                // a removed pack that comes with Lychee: the download icon brings it back (from the app)
                bundled -> androidx.compose.material3.FilledTonalIconButton(onClick = {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) { PackStorage.restore(context, pack) }
                        busy = false
                        onChanged()
                        actions.refresh()
                    }
                }) { Icon(painterResource(R.drawable.ic_sym_download), stringResource(R.string.lychee_packs_get)) }
                else -> androidx.compose.material3.FilledTonalIconButton(onClick = { AddonSheet.open = pack }) {
                    Icon(painterResource(if (pack.custom) R.drawable.ic_sym_folder_open else R.drawable.ic_sym_download),
                        stringResource(if (pack.custom) R.string.lychee_addons_choose_short else R.string.lychee_addons_get_short))
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
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
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
            // the facts, as on a store page: free, its size, the version installed or to download
            val shownVersion = Packs.installedVersion(context, pack.id) ?: app.lychee.packs.Updates.latestOf(context, pack)
            val facts = listOfNotNull("Free", pack.size.takeIf { it.isNotEmpty() }, shownVersion?.let { "Version $it" },
                "Works offline").joinToString("  ·  ")
            Text(facts, style = MaterialTheme.typography.labelLarge, color = colors.primary)
            // the paid add-on it is part of, when it is one
            app.lychee.store.Store.forPack(pack.id)?.let { product ->
                Text("Part of ${product.name} (free while Lychee is in testing). Tap for its page.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.clickable { AddonSheet.open = null; ProductSheet.open = product })
            }
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
            // where it comes from, under which licence, and what stays on the phone
            if (!pack.custom) {
                Text("Source and licence", style = MaterialTheme.typography.titleSmall, color = colors.primary)
                Text(listOf(pack.credit, pack.license).filter { it.isNotEmpty() }.joinToString(". ")
                    .ifEmpty { "Its licence is in the downloaded file." },
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                Text("Privacy", style = MaterialTheme.typography.titleSmall, color = colors.primary)
                Text("The browser downloads it; Lychee itself has no internet permission. Once installed it works " +
                    "offline, and nothing you type ever leaves your phone.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            }
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
