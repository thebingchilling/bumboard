package app.lychee.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.inputmethod.latin.R
import java.io.File

/**
 * The user's own words for each language: a word and what to type for it. Saved as a Rime
 * custom phrase table (lychee_phrases_<lang>.txt in Rime's user folder), which the schemas read
 * with their custom_phrase translator; the word comes first when its spelling is typed.
 */
object PersonalDictionary {
    val languages = listOf(
        "yue" to R.string.lychee_language_cantonese, "cmn" to R.string.lychee_language_mandarin,
        "ja" to R.string.lychee_language_japanese, "ko" to R.string.lychee_language_korean,
    )

    private fun file(context: Context, lang: String) =
        File(File(File(context.filesDir, "rime"), "user"), "lychee_phrases_$lang.txt")

    /** (word, typed as) pairs, in the order they were added. */
    fun read(context: Context, lang: String): List<Pair<String, String>> =
        runCatching {
            file(context, lang).readLines().mapNotNull { line ->
                if (line.startsWith("#")) return@mapNotNull null
                val p = line.split('\t')
                if (p.size >= 2 && p[0].isNotBlank() && p[1].isNotBlank()) p[0] to p[1] else null
            }
        }.getOrDefault(emptyList())

    fun write(context: Context, lang: String, entries: List<Pair<String, String>>) {
        val f = file(context, lang)
        f.parentFile?.mkdirs()
        f.writeText(
            "# Lychee personal dictionary: word<TAB>typed as<TAB>weight\n" +
                entries.joinToString("") { (word, code) -> "$word\t$code\t1\n" }
        )
        // the keyboard re-reads it when it next selects its schema
        @Suppress("DEPRECATION")
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putLong(PREF_CHANGED, System.currentTimeMillis()).apply()
    }

    /** What to type, as Rime matches it: lower-case letters and tone digits, no spaces. */
    fun normalise(code: String) = code.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    const val PREF_CHANGED = "lychee_phrases_changed"
}

@Composable
fun PersonalDictionaryPage() {
    val context = LocalContext.current
    var lang by remember { mutableStateOf("yue") }
    var entries by remember(lang) { mutableStateOf(PersonalDictionary.read(context, lang)) }
    var word by remember(lang) { mutableStateOf("") }
    var code by remember(lang) { mutableStateOf("") }
    fun save(list: List<Pair<String, String>>) {
        entries = list
        PersonalDictionary.write(context, lang, list)
    }

    Text(
        stringResource(R.string.lychee_personal_how),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
    Group {
        row {
            ChoiceRow(
                it, stringResource(R.string.lychee_personal_language),
                options = PersonalDictionary.languages.map { (id, name) -> id to stringResource(name) },
                selected = lang,
                onSelect = { lang = it },
            )
        }
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = word, onValueChange = { word = it }, singleLine = true,
            label = { Text(stringResource(R.string.lychee_personal_word)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = code, onValueChange = { code = it }, singleLine = true,
            label = { Text(stringResource(R.string.lychee_personal_code)) },
            supportingText = { Text(stringResource(R.string.lychee_personal_code_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        val typed = PersonalDictionary.normalise(code)
        Button(
            onClick = {
                save(entries.filterNot { it.first == word.trim() && it.second == typed } + (word.trim() to typed))
                word = ""
                code = ""
            },
            enabled = word.isNotBlank() && typed.isNotEmpty(),
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(R.string.lychee_personal_add)) }
    }
    Group(stringResource(R.string.lychee_personal_words, entries.size)) {
        if (entries.isEmpty()) {
            row { TextRow(it, stringResource(R.string.lychee_personal_none), "") }
        }
        entries.forEach { entry ->
            row {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.first, style = MaterialTheme.typography.titleMedium)
                        Text(entry.second, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        word = entry.first
                        code = entry.second
                        save(entries - entry)
                    }) { Text(stringResource(R.string.lychee_personal_edit)) }
                    TextButton(onClick = { save(entries - entry) }) {
                        Text(stringResource(R.string.lychee_personal_remove))
                    }
                }
            }
        }
    }
}
