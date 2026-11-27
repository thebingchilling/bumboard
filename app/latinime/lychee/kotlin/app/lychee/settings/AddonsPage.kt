@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.lychee.settings

import android.graphics.BitmapFactory
import androidx.compose.animation.togetherWith
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

/**
 * Settings > Add-ons: three tabs (Store, Installed, Updates). Every listing, everywhere, is the
 * same compact card ([ListingCard]): its tile, name and one line, small chips for where it stands
 * (price, installed, comes with Lychee, size, version), and one action at the right; a tap opens
 * its own page (ProductSheet, or a free download's sheet). The store is laid out like Installed:
 * the bundles, then a section per language with that language's add-ons and free downloads (the
 * ones that come with Lychee too), then what works in every language. The registration sits at
 * the very bottom.
 */
@Composable
fun AddonsPage() {
    val context = LocalContext.current
    val actions = rememberPackActions()
    var version by remember { mutableIntStateOf(0) }
    val tick = version + actions.version + PackRefresh.tick
    remember { Packs.removeUnlisted(context) }
    val catalog = remember { Packs.catalog(context) }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(StoreTab.entries[StoreLook.startTab]) }
    // a listing opens as a page of its own, in place of the store (cross-fading and settling in);
    // the app bar's ← (or Back) returns
    val listing: Any? = ProductSheet.open ?: AddonSheet.open
    listing?.let { l ->
        InnerPageOf(if (l is Pack) addonName(l) else (l as app.lychee.store.Store.Product).name) {
            ProductSheet.open = null; AddonSheet.open = null
        }
    }
    androidx.compose.animation.AnimatedContent(targetState = listing, label = "listing", transitionSpec = { pageTransition(targetState != null) }) { l ->
        Column {
            when (l) {
                is app.lychee.store.Store.Product -> ProductPage(l)
                is Pack -> AddonPage(l, actions)
                else -> AddonsHome(tab, { tab = it }, catalog, tick, actions, { version++ }, context)
            }
        }
    }
}

/**
 * Pages of the store moving: forward (a listing opening) slides a little in from the right as it
 * fades and springs to size; back comes from the left. Gentle, like the settings' pages.
 */
fun pageTransition(forward: Boolean): androidx.compose.animation.ContentTransform {
    val sign = if (forward) 1 else -1
    val ease = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    return (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(350, easing = ease)) { sign * it / 10 } +
        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(210, delayMillis = 60)) +
        androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.7f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow), initialScale = 0.97f))
        .togetherWith(
            androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(350, easing = ease)) { -sign * it / 10 } +
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)),
        )
}

/** The store's tabs and what each shows (Store, Installed, Updates). */
@Composable
private fun AddonsHome(tab: StoreTab, setTab: (StoreTab) -> Unit, catalog: List<Pack>, tick: Int, actions: PackActions,
                       changed: () -> Unit, context: android.content.Context) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = colors.surface) {
        StoreTab.entries.forEach { t ->
            androidx.compose.material3.Tab(selected = tab == t, onClick = { setTab(t) },
                text = { Text(t.title, maxLines = 1) })
        }
    }
    PackMessage(actions)
    val remove: (Pack) -> Unit = { pack -> PackStorage.remove(context, pack); changed() }
    // the tabs cross-fade, settling in
    androidx.compose.animation.AnimatedContent(targetState = tab, label = "tab",
        transitionSpec = { pageTransition(targetState.ordinal > initialState.ordinal) }) { shownTab ->
    Column {
    when (shownTab) {
        StoreTab.STORE -> StoreHome(catalog, tick, actions, onRemove = remove) { changed() }
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
                Listings(stringResource(sectionName(section))) {
                    packs.forEach { pack -> AddonCard(pack, tick, actions, onRemove = { remove(pack) }) { changed() } }
                }
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
    }
    }
    Spacer(Modifier.height(16.dp))
}

/** The store's sections: each language, then what works in every language. */
private val storeSections = listOf(
    PackStorage.Section.CHINESE, PackStorage.Section.JAPANESE, PackStorage.Section.KOREAN,
    PackStorage.Section.ENGLISH, PackStorage.Section.MISC,
)

/** The paid add-ons listed under a section: each language's In Depth card; the rest under every language. */
private fun paidIn(section: PackStorage.Section): List<app.lychee.store.Store.Product> {
    val S = app.lychee.store.Store
    return when (section) {
        PackStorage.Section.CHINESE -> listOf(S.CARD_ZH)
        PackStorage.Section.JAPANESE -> listOf(S.CARD_JA)
        PackStorage.Section.KOREAN -> listOf(S.CARD_KO)
        PackStorage.Section.MISC -> listOf(S.HANDWRITING, S.STROKES, S.EXAMPLES, S.FONTS)
        else -> emptyList()
    }
}

/**
 * The store, laid out like Installed: one honest line, the bundles, then a section per language
 * (its In Depth card and its free downloads, including those that come with Lychee, each saying
 * so), then what works in every language, and the registration at the bottom.
 */
@Composable
private fun StoreHome(catalog: List<Pack>, tick: Int, actions: PackActions, onRemove: (Pack) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val S = app.lychee.store.Store
    val unavailable = remember { S.unavailableReason(context) }
    // only when paid add-ons can't be had on this phone, and why
    unavailable?.let { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Note(it, colors.errorContainer, colors.onErrorContainer) } }
    Text("Made by a student developer. One-time purchases, no ads, no tracking, always offline.",
        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp))
    Listings("Bundles") {
        (listOf(S.FULL) + S.packs).forEach { p -> ProductCard(p) }
    }
    for (section in storeSections) {
        val paid = paidIn(section)
        val free = catalog.filter { !it.custom && PackStorage.section(it) == section }
        if (paid.isEmpty() && free.isEmpty()) continue
        val title = if (section == PackStorage.Section.MISC) "Every language" else stringResource(sectionName(section))
        Listings(title) {
            paid.forEach { p -> ProductCard(p) }
            free.forEach { pack -> AddonCard(pack, tick, actions, onRemove = { onRemove(pack) }, onChanged = onChanged) }
        }
    }
    Spacer(Modifier.height(16.dp))
    RegistrationSection()
}

/** A section of listings: its heading in the accent colour, then its cards, close together. */
@Composable
private fun Listings(title: String, content: @Composable () -> Unit) {
    GroupHeader(title)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    Spacer(Modifier.height(8.dp))
}

/** A small chip on a listing: its price or where it stands ([accent]), its size, its version. */
@Composable
private fun ListingChip(text: String, accent: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1,
        color = if (accent) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (accent) colors.primaryContainer else colors.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 2.dp))
}

/**
 * Every listing's card, in the store and in Installed alike: [tile], [name] and [line] (two lines
 * at most), its [chips] under them, and [action] at the right; a tap opens the listing's page.
 */
@Composable
private fun ListingCard(
    name: String, line: String, chips: List<Pair<String, Boolean>>, quiet: Boolean = false,
    tile: @Composable () -> Unit, onClick: () -> Unit, action: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    androidx.compose.material3.Surface(
        onClick = onClick, shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().pressSpring(source), interactionSource = source,
    ) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            tile()
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = if (quiet) colors.onSurfaceVariant else colors.onSurface)
                if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (chips.isNotEmpty()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    chips.forEach { (text, accent) -> ListingChip(text, accent) }
                }
            }
            Spacer(Modifier.size(8.dp))
            action()
        }
    }
}

/** A small promise: a tick and a few words, in the accent colour. */
@Composable
fun Promise(icon: Int, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), null, tint = colors.primary, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
    }
}

/**
 * A price as a button of its own: a pill in the accent's tonal colour (or filled, for the
 * largest bundle). It opens the listing, as the card does; it sinks a little when pressed.
 */
@Composable
fun PricePill(text: String, strong: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (strong) colors.primary else colors.primaryContainer,
        contentColor = if (strong) colors.onPrimary else colors.onPrimaryContainer,
        modifier = Modifier.pressSpring(source, BUTTON_PRESSED),
        interactionSource = source,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

/** A paid add-on, pack or Lychee Complete as a listing: its price, and what it holds or belongs to. */
@Composable
private fun ProductCard(p: app.lychee.store.Store.Product) {
    val S = app.lychee.store.Store
    // the price is the pill at the right; the chips say what it holds or where else it comes
    val chips = buildList {
        when (p.kind) {
            app.lychee.store.Store.Kind.ADDON -> {
                val packs = S.includedIn(p.id).filter { it.kind == app.lychee.store.Store.Kind.PACK }
                when {
                    packs.size == S.packs.size -> add("In every language pack" to false)
                    packs.isNotEmpty() -> add("Also in " + packs.joinToString(", ") { it.name } to false)
                }
            }
            app.lychee.store.Store.Kind.FULL -> add("${p.includes.size} add-ons, every language" to false)
            else -> add("${p.includes.size} add-ons" to false)
        }
    }
    ListingCard(p.name, p.tagline, chips, tile = { ProductTile(p, 40) }, onClick = { ProductSheet.open = p }) {
        PricePill(S.price(p.cents), strong = p.kind == app.lychee.store.Store.Kind.FULL) { ProductSheet.open = p }
    }
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
 * A free download (or one of your own fonts) as a listing, in the store and in Installed alike:
 * its chips say where it stands (installed, comes with Lychee, removed, an update, or free), its
 * size and version; at the right, remove it, bring it back, update it, or get it.
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
    val update = if (installed) app.lychee.packs.Updates.newer(context, pack) else null
    val status = when {
        update != null -> stringResource(R.string.lychee_addons_update_to, update)
        installed && bundled -> stringResource(R.string.lychee_addons_bundled)
        installed -> stringResource(R.string.lychee_addons_installed)
        bundled -> stringResource(R.string.lychee_storage_removed)
        else -> pack.price ?: stringResource(R.string.lychee_store_free)
    }
    val shownVersion = if (installed) Packs.installedVersion(context, pack.id) else null
    val chips = listOfNotNull(
        status to (update != null || !installed),
        size.takeIf { it.isNotEmpty() }?.let { it to false },
        shownVersion?.takeIf { it.isNotEmpty() }?.let { "v$it" to false },
    )
    ListingCard(addonName(pack), pack.summary, chips, quiet = false,
        tile = { AddonTile(pack, 40.dp, androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)) },
        onClick = { AddonSheet.open = pack }) {
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
 * [AddonSheet.open] outside Add-ons (Settings > Fonts): its page in a sheet. Inside Add-ons it is
 * a page of its own ([AddonPage]), like a paid listing's.
 */
@Composable
fun AddonSheetHost(actions: PackActions) {
    val pack = AddonSheet.open ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { AddonSheet.open = null }, sheetState = state) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).navigationBarsPadding()) {
            AddonPage(pack, actions)
        }
    }
}

/**
 * A free download's page, laid out exactly like a paid listing's (ProductPage): its tile, name
 * and line; a card where the price would be (Free, its size and version, and where it stands:
 * installed with Remove, an update, or the steps to get it: 1 download the file, 2 choose it);
 * then its own cards: the picture of it, the paid add-on it is part of, and its details (source
 * and licence, privacy).
 */
@Composable
fun AddonPage(pack: Pack, actions: PackActions) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val refresh = actions.version + PackRefresh.tick
    val installed = Packs.isInstalled(context, pack.id)
    val bundled = remember(pack.id) { app.lychee.packs.BundledPacks.isBundled(context, pack.id) }
    var downloaded by remember(pack.id) { mutableStateOf(Packs.pending(context)?.id == pack.id) }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AddonTile(pack, 56.dp, androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp))
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(addonName(pack), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
                if (pack.summary.isNotEmpty()) Text(pack.summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        // where the price would be: Free, the facts, and what can be done, straight on the page
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val shownVersion = Packs.installedVersion(context, pack.id) ?: app.lychee.packs.Updates.latestOf(context, pack)
            Text(if (pack.custom) "Your own" else (pack.price ?: stringResource(R.string.lychee_store_free)),
                style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    installed && bundled -> ListingChip(stringResource(R.string.lychee_addons_bundled), accent = true)
                    installed -> ListingChip(stringResource(R.string.lychee_addons_installed), accent = true)
                }
                pack.size.takeIf { it.isNotEmpty() }?.let { ListingChip(it) }
                shownVersion?.let { ListingChip("v$it") }
                ListingChip("Works offline")
            }
            if (actions.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (installed && !app.lychee.packs.Updates.hasUpdate(context, pack)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { actions.remove(pack) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.lychee_packs_remove), maxLines = 1)
                    }
                    Button(onClick = { AddonSheet.open = null }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.lychee_addons_done), maxLines = 1)
                    }
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
        // a picture of it (a font's letters), in its own card
        val picture = remember(pack.preview) {
            pack.preview?.let { path ->
                runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
            }
        }
        if (picture != null) Section("In Lychee") {
            Image(picture, null, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxWidth().height(56.dp))
        }
        // the paid add-on it is part of, as a row to open
        app.lychee.store.Store.forPack(pack.id)?.let { product ->
            Section("Part of") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { AddonSheet.open = null; ProductSheet.open = product }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProductTile(product, 32)
                    Spacer(Modifier.size(12.dp))
                    Text(product.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_sym_chevron_right), null, tint = colors.onSurfaceVariant)
                }
            }
        }
        if (!pack.custom) {
            EveryAddon(downloaded = true)
            SmallPrint(listOf("Source and licence: " + (listOf(pack.credit, pack.license).filter { it.isNotEmpty() }
                .joinToString(". ").ifEmpty { "its licence is in the downloaded file." })))
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
