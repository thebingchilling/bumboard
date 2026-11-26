package app.lychee.typing

import android.content.Context
import android.provider.UserDictionary

/**
 * The English words a person saves (the ☆ on a word no dictionary knows: a name, an email
 * address, a number): Android's own personal dictionary, which the English keyboards read for
 * suggestions. Such a word is offered, never put in by itself in place of what was typed
 * (Suggest leaves the personal dictionary out of autocorrection).
 */
object EnglishWords {
    /** Whether [word] is saved. */
    fun saved(context: Context, word: String): Boolean = runCatching {
        context.contentResolver.query(UserDictionary.Words.CONTENT_URI, arrayOf(UserDictionary.Words.WORD),
            "${UserDictionary.Words.WORD} = ?", arrayOf(word), null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    /** Saves [word] (for every English keyboard); false when the phone refused. */
    fun save(context: Context, word: String): Boolean = runCatching {
        if (!saved(context, word)) UserDictionary.Words.addWord(context, word, 250, null, null)
        true
    }.getOrDefault(false)

    /** Takes [word] out again. */
    fun remove(context: Context, word: String) {
        runCatching {
            context.contentResolver.delete(UserDictionary.Words.CONTENT_URI, "${UserDictionary.Words.WORD} = ?", arrayOf(word))
        }
    }
}
