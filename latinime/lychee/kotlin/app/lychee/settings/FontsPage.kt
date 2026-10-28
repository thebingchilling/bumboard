package app.lychee.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lychee.fonts.LycheeFonts
import app.lychee.packs.PackImporter
import app.lychee.packs.PackKind
import app.lychee.packs.Packs
import com.android.inputmethod.latin.R

/** Settings > Fonts: the Chinese font and the emoji font Lychee draws with. */
@Composable
fun FontsPage() {
    val context = LocalContext.current
    val prefs = prefs()
    val actions = rememberPackActions()
    @Suppress("UNUSED_VARIABLE") val refresh = actions.version
    val chinese = prefs.stringPref(LycheeFonts.PREF_CHINESE) { prefs.getString(LycheeFonts.PREF_CHINESE, null) ?: "" }
    val keys = prefs.boolPref(LycheeFonts.PREF_CHINESE_KEYS) { prefs.getBoolean(LycheeFonts.PREF_CHINESE_KEYS, false) }
    val emoji = prefs.stringPref(LycheeFonts.PREF_EMOJI) { prefs.getString(LycheeFonts.PREF_EMOJI, null) ?: "" }
    val zhPacks = remember { Packs.ofKind(context, PackKind.FONT_ZH) }
    val emojiPacks = remember { Packs.ofKind(context, PackKind.FONT_EMOJI) }

    Text(
        stringResource(R.string.lychee_fonts_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
    PackMessage(actions)

    Group(stringResource(R.string.lychee_fonts_chinese)) {
        row {
            Preview(it, stringResource(R.string.lychee_fonts_preview_zh), chinese.value, actions.version)
        }
        row {
            ChoiceItem(it, stringResource(R.string.lychee_fonts_wenkai), stringResource(R.string.lychee_fonts_wenkai_summary),
                chinese.value.isEmpty()) { chinese.set("") }
        }
        row {
            ChoiceItem(it, stringResource(R.string.lychee_fonts_system), stringResource(R.string.lychee_fonts_system_summary),
                chinese.value == LycheeFonts.SYSTEM) { chinese.set(LycheeFonts.SYSTEM) }
        }
        zhPacks.forEach { pack ->
            row { PackRow(it, pack, actions, selected = chinese.value == pack.id) { chinese.set(pack.id) } }
        }
        row {
            SwitchRow(it, stringResource(R.string.lychee_fonts_on_keys), stringResource(R.string.lychee_fonts_on_keys_summary),
                keys.value, keys::set)
        }
    }
    Group(stringResource(R.string.lychee_fonts_emoji)) {
        row {
            Preview(it, stringResource(R.string.lychee_fonts_preview_emoji), emoji.value, actions.version, emojiFont = true)
        }
        row {
            ChoiceItem(it, stringResource(R.string.lychee_fonts_noto), stringResource(R.string.lychee_fonts_noto_summary),
                emoji.value.isEmpty()) { emoji.set("") }
        }
        emojiPacks.forEach { pack ->
            row { PackRow(it, pack, actions, selected = emoji.value == pack.id) { emoji.set(pack.id) } }
        }
    }
}

/** A plain choice with a radio button (the built-in fonts). */
@Composable
private fun ChoiceItem(shape: Shape, title: String, summary: String?, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        shape = shape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A sample line in the chosen font ([id] empty: the default). */
@Composable
private fun Preview(shape: Shape, sample: String, id: String, version: Int, emojiFont: Boolean = false) {
    val context = LocalContext.current
    val family = remember(id, version) {
        val file = id.takeIf { it.isNotEmpty() && it != LycheeFonts.SYSTEM }?.let { Packs.mainFile(context, it) }
        val face = file?.let { PackImporter.loadFont(it) }
            ?: when {
                emojiFont -> LycheeFonts.bundledEmoji(context)
                id.isEmpty() -> LycheeFonts.bundledChinese(context)
                else -> null
            }
        face?.let { FontFamily(it) }
    }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Text(
            sample,
            fontFamily = family,
            fontSize = 24.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        )
    }
}
