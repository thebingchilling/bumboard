package app.lychee.settings

import androidx.compose.material3.TextButton
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

/**
 * The personal dictionary, as LatinIME's user dictionary is: the language, then the words as a
 * plain list (the word, what is typed for it under it). "Add a word" and a tap on a word open a
 * dialog with the two fields; a word being edited can be removed there.
 */
@Composable
fun PersonalDictionaryPage() {
    val context = LocalContext.current
    var lang by remember { mutableStateOf("yue") }
    var entries by remember(lang) { mutableStateOf(PersonalDictionary.read(context, lang)) }
    // the dialog: null when closed; the entry being edited, or ("", "") for a new word
    var editing by remember(lang) { mutableStateOf<Pair<String, String>?>(null) }
    fun save(list: List<Pair<String, String>>) {
        entries = list
        PersonalDictionary.write(context, lang, list)
    }

    PageNote(stringResource(R.string.lychee_personal_how))
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
    Group(stringResource(R.string.lychee_personal_words, entries.size)) {
        row {
            NavRow(it, stringResource(R.string.lychee_personal_add_word), icon = R.drawable.ic_sym_add) { editing = "" to "" }
        }
        if (entries.isEmpty()) {
            row { TextRow(it, stringResource(R.string.lychee_personal_none), stringResource(R.string.lychee_personal_none_hint)) }
        }
        entries.sortedBy { it.first }.forEach { entry ->
            row { NavRow(it, entry.first, entry.second) { editing = entry } }
        }
    }

    editing?.let { old ->
        val isNew = old.first.isEmpty() && old.second.isEmpty()
        var word by remember(old) { mutableStateOf(old.first) }
        var code by remember(old) { mutableStateOf(old.second) }
        val typed = PersonalDictionary.normalise(code)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = {
                Text(stringResource(if (isNew) R.string.lychee_personal_add_word else R.string.lychee_personal_edit_word))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val entry = word.trim() to typed
                        save((entries - old).filterNot { it == entry } + entry)
                        editing = null
                    },
                    enabled = word.isNotBlank() && typed.isNotEmpty(),
                ) { Text(stringResource(R.string.lychee_personal_save)) }
            },
            dismissButton = {
                Row {
                    if (!isNew) {
                        TextButton(onClick = {
                            save(entries - old)
                            editing = null
                        }) { Text(stringResource(R.string.lychee_personal_remove)) }
                    }
                    TextButton(onClick = { editing = null }) { Text(stringResource(android.R.string.cancel)) }
                }
            },
        )
    }
}
