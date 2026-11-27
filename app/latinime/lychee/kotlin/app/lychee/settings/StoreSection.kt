package app.lychee.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lychee.store.Store
import com.android.inputmethod.latin.R

/** The listing whose page is open (Store.Product), any part of Add-ons may open one. */
object ProductSheet {
    var open by mutableStateOf<Store.Product?>(null)
}

/**
 * The store in Settings > Add-ons (PAID.md): a short, honest introduction (a student developer,
 * one-time purchases, no ads, no tracking), the bundles (Complete first, as the best value), then
 * every add-on by category. Each opens its own page (ProductSheetHost) with its price, what it
 * gives, a worked example, its free trial, sources, licences and privacy. Only the UI exists:
 * there is no billing, so everything is free for now and says so.
 */
@Composable
fun StoreSection() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val unavailable = remember { Store.unavailableReason(context) }

    SectionDivider()
    GroupHeader("Go further")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Typing in every language, readings, meanings and handwriting are free and always will be. " +
                "Add-ons go deeper, for learners who want more. Lychee is made by one student developer: " +
                "add-ons are one-time purchases that pay for its development. No subscriptions, no ads, " +
                "no tracking, and Lychee never goes online.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
        )
        if (Store.FREE_FOR_NOW) {
            Note("Everything is free while Lychee is in testing. The prices show what each add-on will cost.",
                colors.tertiaryContainer, colors.onTertiaryContainer)
        }
        unavailable?.let { Note(it, colors.errorContainer, colors.onErrorContainer) }
        BundleCard(Store.FULL, best = true)
        Store.packs.forEach { BundleCard(it, best = false) }
        Text("Own a pack? Its price comes off ${app.lychee.store.Store.FULL.name}. Own two, and it's yours.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
    for ((category, title, about) in listOf(
        Triple(Store.Category.DICTIONARIES, "Dictionaries", "The complete word card, one language each"),
        Triple(Store.Category.FEATURES, "Features", "For every language"),
        Triple(Store.Category.FONTS, "Fonts", "Every font and emoji style"),
    )) {
        SectionDivider()
        GroupHeader(title)
        Text(about, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp))
        Store.addons.filter { it.category == category }.forEach { ProductRow(it) }
    }
}

/**
 * Pleco-style registration (restores purchases on another phone, with billing): at the very bottom
 * of Add-ons, out of the way.
 */
@Composable
fun RegistrationSection() {
    val colors = MaterialTheme.colorScheme
    SectionDivider()
    GroupHeader("Registration")
    Text(
        "Your purchases will belong to a Lychee registration. Enter its key on a new phone, or a " +
            "family member's, to bring everything back. Comes with paid add-ons.",
        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        FilledTonalButton(onClick = {}, enabled = false) { Text("Enter registration key") }
    }
}

/** A bundle as a compact card: its tile, name, what it is, price and saving; a tap opens its page. */
@Composable
private fun BundleCard(p: Store.Product, best: Boolean) {
    val colors = MaterialTheme.colorScheme
    val surface = if (best) colors.primaryContainer else colors.surfaceContainerLow
    val on = if (best) colors.onPrimaryContainer else colors.onSurface
    val value = Store.value(p)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .clickable { ProductSheet.open = p }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductTile(p, 52)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (best) {
                Text("Best value", style = MaterialTheme.typography.labelMedium, color = colors.primary,
                    fontWeight = FontWeight.Medium)
            }
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = on)
            Text(p.tagline, style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.8f))
            Text("${p.includes.size} add-ons, ${Store.price(value)} value · save ${Store.price(value - p.cents)}",
                style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
        Spacer(Modifier.size(12.dp))
        Text(Store.price(p.cents), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = on)
    }
}

/** An add-on as a store row: tile, name, what it is, price and packs; a tap opens its page. */
@Composable
private fun ProductRow(p: Store.Product) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    @Suppress("UNUSED_VARIABLE") val refresh = PackRefresh.tick
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { ProductSheet.open = p }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductTile(p, 48)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(p.tagline, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (p.needsApp) {
                val has = app.lychee.handwriting.GoogleHandwriting.installed(context)
                Text(if (has) "Lychee handwriting app installed" else "Needs the free Lychee handwriting app",
                    style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
            }
            Text(
                Store.price(p.cents) + " · in " + Store.includedIn(p.id).joinToString(", ") { it.name.removeSuffix(" Pack") },
                style = MaterialTheme.typography.labelSmall, color = colors.primary,
            )
        }
        Spacer(Modifier.size(12.dp))
        Icon(painterResource(R.drawable.ic_sym_chevron_right), null, tint = colors.onSurfaceVariant)
    }
}

/** A listing's tile: its character on a tonal rounded square. */
@Composable
fun ProductTile(p: Store.Product, size: Int) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(colors.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(p.tile, fontSize = (size * 0.45f).sp, color = colors.onSecondaryContainer)
    }
}

/** A short tinted note (testing, or why paid add-ons won't be available here). */
@Composable
fun Note(text: String, background: Color, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(12.dp),
    )
}
