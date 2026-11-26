@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.lychee.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lychee.store.Store
import com.android.inputmethod.latin.R

/** Screenshots only: the Add-ons tab (0 Store, 1 Installed, 2 Updates) and category page to open on. */
object StoreLook {
    var startTab = 0
}

/**
 * A listing's own page, in Lychee's layout: the add-on's tile, name and line; a buying card with
 * the price, three plain promises and the buttons (Try the demo beside Buy); then "In Lychee" (a
 * worked example as the card draws it), what it gives, what is included, the free trial, about,
 * sources and licences, privacy. Honest throughout: what isn't built yet says "coming", and while
 * billing doesn't exist the buttons say why they can't buy anything. Back returns to the store.
 */
@Composable
fun ProductPage(p: Store.Product) {
    val colors = MaterialTheme.colorScheme
    @Suppress("UNUSED_VARIABLE") val refresh = PackRefresh.tick
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductTile(p, 56)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, color = colors.onSurface)
                Text(p.tagline, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        if (p.needsApp) HandwritingActions() else BuyCard(p)
        Section("In Lychee") { Sample(p.sample) }
        Section("What you get") {
            p.lines.forEach { Bullet(it) }
            p.bonus?.let { Bullet(Store.Line("Bonus: $it, only in this pack or Lychee Complete")) }
        }
        if (p.includes.isNotEmpty()) {
            Section("Included") {
                p.includes.mapNotNull { Store.byId(it) }.forEach { inc ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { ProductSheet.open = inc }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProductTile(inc, 32)
                        Spacer(Modifier.size(12.dp))
                        Text(inc.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(Store.price(inc.cents), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        if (p.demo.isNotEmpty()) Section("Try it free") { Plain(p.demo) }
        if (p.about.isNotEmpty()) Section("About") { Plain(p.about) }
        if (p.sources.isNotEmpty()) Section("Sources and licences") { Plain(p.sources) }
        Section("Updates") {
            Plain("Every update to this add-on is included. Where it is built on open data, updates follow that " +
                "data's new releases for as long as they stay free to use.")
        }
        Section("Privacy") {
            Plain(
                if (p.needsApp) "Your writing is read by Google's ML Kit on this phone, never sent anywhere. The " +
                    "Lychee handwriting app goes online to download Google's models, and ML Kit sends Google " +
                    "figures about how it runs (device model, app version, speed), as Google's terms require; " +
                    "never your handwriting. Lychee itself has no internet permission."
                else "Lychee has no internet permission: everything works offline, and nothing you type or look " +
                    "up ever leaves your phone."
            )
        }
    }
}

/**
 * The buying card: the price (and a bundle's saving), three promises, then the buttons. While
 * billing doesn't exist, a tap says so plainly instead of doing nothing.
 */
@Composable
private fun BuyCard(p: Store.Product) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    // honest when tapped: no purchase can be made yet (no billing), so say so rather than doing nothing
    val why = "Purchases aren't available yet. They'll open in a later version of Lychee."
    fun explain() = android.widget.Toast.makeText(context, why, android.widget.Toast.LENGTH_LONG).show()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerLow).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Store.price(p.cents), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Promise(R.drawable.ic_sym_check, "Always offline, like the rest of Lychee")
            Promise(R.drawable.ic_sym_check, "Yours for life, with every future update")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (p.demo.isNotEmpty()) {
                OutlinedButton(onClick = ::explain, modifier = Modifier.weight(1f)) { Text("Try the demo", maxLines = 1) }
            }
            Button(onClick = ::explain, modifier = Modifier.weight(1f)) { Text("Buy", maxLines = 1) }
        }
    }
}

/** Google Handwriting's steps: the free Lychee handwriting app (ML Kit's licence), then its models. */
@Composable
private fun HandwritingActions() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val installed = app.lychee.handwriting.GoogleHandwriting.installed(context)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerLow).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(Store.price(Store.HANDWRITING.cents),
            style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Text(
            if (installed) "The Lychee handwriting app is installed: Lychee's handwriting pad uses Google's ML Kit."
            else "Google's ML Kit runs in the free Lychee handwriting app (${context.getString(R.string.lychee_companion_size)}): " +
                "its licence keeps it in an app of its own, so Lychee itself stays offline. Install it, open it " +
                "once to get the models, and you're set.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
        )
        if (!installed) {
            Button(onClick = { app.lychee.handwriting.GoogleHandwriting.download(context) }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_sym_download), null, modifier = Modifier.size(18.dp))
                Text("Get Lychee handwriting", modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { app.lychee.handwriting.GoogleHandwriting.openSetup(context) }) {
                    Icon(painterResource(R.drawable.ic_sym_refresh), null, modifier = Modifier.size(18.dp))
                    Text("Models", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { app.lychee.handwriting.GoogleHandwriting.openAppInfo(context) }) {
                    Icon(painterResource(R.drawable.ic_sym_delete), null, modifier = Modifier.size(18.dp))
                    Text("Remove", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Fact(text: String, strong: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (strong) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (strong) colors.primaryContainer else colors.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.primary)
        content()
    }
}

@Composable
private fun Plain(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

@Composable
private fun Bullet(line: Store.Line) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Icon(painterResource(R.drawable.ic_sym_check), null,
            tint = if (line.coming) colors.outline else colors.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.size(10.dp))
        Text(if (line.coming) "${line.text} (coming)" else line.text, style = MaterialTheme.typography.bodyMedium,
            color = if (line.coming) colors.onSurfaceVariant else colors.onSurface)
    }
}

/**
 * The worked example on a listing's page: a small word card as Lychee draws it, with real data
 * for a real word, labelled "Example".
 */
@Composable
private fun Sample(sample: Store.Sample) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        @Composable
        fun big(t: String) = Text(t, fontSize = 30.sp, color = colors.onSurface)
        @Composable
        fun line(t: String, accent: Boolean = false) = Text(t, style = MaterialTheme.typography.bodyMedium,
            color = if (accent) colors.primary else colors.onSurface)
        @Composable
        fun small(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        @Composable
        fun badges(vararg b: String) = FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            b.forEach { Fact(it) }
        }
        when (sample) {
            Store.Sample.CHINESE -> {
                big("學校 · 学校")
                line("hok6 haau6  ·  xuéxiào", accent = true)
                badges("HSK 1", "Common")
                line("school; educational institution")
                small("Measure word: 間 gaan1 / 所 suǒ")
                small("學: 𦥑 two hands + 爻 + 冖 cover + 子 child")
            }
            Store.Sample.JAPANESE -> {
                big("食べる")
                line("たべる  taberu", accent = true)
                badges("JLPT N5")
                line("Pitch  た" + "べ̅ꜜる  ·  Nakadaka (falls in the middle)")
                line("to eat")
                small("Polite 食べます · Negative 食べない · Te-form 食べて · Past 食べた")
                small("食: 人 person + 良 good · Grade 2")
            }
            Store.Sample.KOREAN -> {
                big("학교")
                line("hakgyo", accent = true)
                badges("Beginner level")
                line("Said  [학꾜]  ·  hakkyo")
                line("school")
                small("Hanja 學校: 學 배울 학 (learn) · 校 학교 교 (school)")
                small("먹다: 먹어요 · 먹었어요 · 먹을 거예요 · 먹습니다")
            }
            Store.Sample.HANDWRITING -> {
                big("你")
                line("Written by hand, read as:", accent = false)
                badges("你", "你好", "你們", "妳")
                small("Whole words too: write 你好嗎 in one go")
            }
            Store.Sample.STROKES -> {
                big("永")
                line("5 strokes, drawn one by one in order", accent = true)
                small("Each stroke animates in the right direction; tap to replay")
            }
            Store.Sample.EXAMPLES -> {
                line("我哋聽日去學校。", accent = false)
                small("ngo5 dei6 ting1 jat6 heoi3 hok6 haau6 · We're going to school tomorrow.")
                line("毎日学校に行きます。")
                small("mainichi gakkō ni ikimasu · I go to school every day.")
            }
            Store.Sample.FONTS -> {
                line("Kaiti 永  ·  Song 永  ·  Brush 永")
                line("Textbook あ  ·  Mincho あ  ·  Rounded あ")
                line("Myeongjo 한  ·  Pen 한  ·  Dodum 한")
                small("Emoji: Noto 3D, Fluent, Twemoji, OpenMoji, Blobmoji, Noto Emoji")
            }
            Store.Sample.ALL -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Store.addons.forEach { ProductTile(it, 40) }
                }
                small("Every add-on, every language, every font, and whatever Lychee makes next.")
            }
        }
    }
}
