package app.lychee.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
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

/**
 * The store in Settings > Add-ons (PAID.md): "Pick a bundle, or buy add-ons individually", Full
 * first as the best value, the three language packs (one price for every language), then every
 * add-on by category with its price and the packs that include it. Only the UI exists: there is
 * no billing, so everything is free for now and every button says so. Where paid add-ons won't
 * be available on this phone, a note says plainly why.
 */
@Composable
fun StoreSection() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val unavailable = remember { Store.unavailableReason(context) }

    SectionDivider()
    GroupHeader("Store")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pick a bundle, or buy add-ons individually. One-time purchases: buy once, keep forever.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        if (Store.FREE_FOR_NOW) {
            Note("Everything is free while Lychee is in testing. The prices show what each add-on will cost.",
                colors.secondaryContainer, colors.onSecondaryContainer)
        }
        unavailable?.let { Note(it, colors.errorContainer, colors.onErrorContainer) }
        BundleCard(Store.FULL, best = true)
        Store.packs.forEach { BundleCard(it, best = false) }
        Text("Own a pack? Its price comes off Full. Own two, and Full is yours.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
    for ((category, title, about) in listOf(
        Triple(Store.Category.DICTIONARIES, "Dictionaries", "The full word card for one language"),
        Triple(Store.Category.FEATURES, "Features", "For every language"),
        Triple(Store.Category.FONTS, "Fonts", "Every font and emoji style"),
    )) {
        SectionDivider()
        GroupHeader(title)
        Text(about, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp))
        Store.addons.filter { it.category == category }.forEach { AddonRow(it) }
    }
    // Pleco-style registration: restores purchases on another phone (with billing)
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

/** A bundle: Full (highlighted as the best value) or a language pack. */
@Composable
private fun BundleCard(p: Store.Product, best: Boolean) {
    val colors = MaterialTheme.colorScheme
    val surface = if (best) colors.primaryContainer else colors.surfaceContainerLow
    val on = if (best) colors.onPrimaryContainer else colors.onSurface
    val value = Store.value(p)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (best) {
                    Text("Best value", style = MaterialTheme.typography.labelMedium, color = colors.primary,
                        fontWeight = FontWeight.Medium)
                }
                Text(p.name, style = MaterialTheme.typography.titleLarge, color = on)
                Text(p.tagline, style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
            }
            Text(Store.price(p.cents), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = on)
        }
        Text(
            "${p.includes.size} add-ons, ${Store.price(value)} value · save ${Store.price(value - p.cents)}",
            style = MaterialTheme.typography.labelLarge, color = colors.primary,
        )
        p.lines.forEach { Bullet(it, on) }
        p.bonus?.let { Bullet(Store.Line("Bonus: $it (only in this pack, or Full)"), on) }
        BuyButton(p)
    }
}

/** An individual add-on: its name, what it is, its price and the packs that include it. */
@Composable
private fun AddonRow(p: Store.Product) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(p.tagline, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(
                Store.price(p.cents) + " · in " + Store.includedIn(p.id).joinToString(", ") { it.name.removePrefix("Lychee ") },
                style = MaterialTheme.typography.labelSmall, color = colors.primary,
            )
            p.lines.filter { it.coming }.takeIf { it.isNotEmpty() }?.let {
                Text("Coming: " + it.joinToString(", ") { l -> l.text.replaceFirstChar { c -> c.lowercase() } },
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(8.dp))
        BuyButton(p, compact = true)
    }
}

/** What a product gives, one line; what isn't built yet says so. */
@Composable
private fun Bullet(line: Store.Line, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(painterResource(R.drawable.ic_sym_check), null, tint = color.copy(alpha = if (line.coming) 0.5f else 1f),
            modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            if (line.coming) "${line.text} (coming)" else line.text,
            style = MaterialTheme.typography.bodyMedium,
            color = color.copy(alpha = if (line.coming) 0.6f else 1f),
        )
    }
}

/** No billing yet: every button says "Free for now", and can't be pressed. */
@Composable
private fun BuyButton(p: Store.Product, compact: Boolean = false) {
    val context = LocalContext.current
    val owned = Store.owns(context, p.id)
    FilledTonalButton(
        onClick = {},
        enabled = false,
        contentPadding = if (compact) androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            else androidx.compose.material3.ButtonDefaults.ContentPadding,
    ) {
        Text(if (Store.FREE_FOR_NOW || owned) "Free for now" else "Buy ${Store.price(p.cents)}", maxLines = 1, softWrap = false)
    }
}

/** A short tinted note (testing, or why paid add-ons won't be available here). */
@Composable
private fun Note(text: String, background: Color, color: Color) {
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
