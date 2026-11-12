package app.lychee.typing

import android.content.Context
import android.content.SharedPreferences

/**
 * What autocorrect changed, Gboard's way:
 *  - back at the end of a corrected word (backspacing to it, or tapping there), the bar offers
 *    what was typed first, then the other suggestions it had, whether or not the app kept
 *    LatinIME's suggestion marks on the text;
 *  - a word put back as typed (picked from the bar after a correction) is never corrected
 *    again; nor is a word typed again the same way after its correction was deleted;
 *  - those words are kept on the phone (the user's words, like the personal dictionary).
 */
object CorrectionMemory {
    private class Correction(val typed: String, val corrected: String, val alternatives: List<String>) {
        /** the corrected word was backspaced into: it wasn't wanted */
        var deleted = false
    }

    private val recent = ArrayDeque<Correction>()
    private var prefs: SharedPreferences? = null
    /** kept in the order they were kept, so the oldest go first when there are too many */
    private val kept = LinkedHashSet<String>()

    @JvmStatic
    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        @Suppress("DEPRECATION")
        val p = android.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs = p
        kept += p.getStringSet(PREF_KEPT, emptySet()).orEmpty()
    }

    /** Autocorrect changed [typed] to [corrected]; [alternatives] were the other suggestions. */
    @JvmStatic
    @Synchronized
    fun corrected(typed: String, corrected: String, alternatives: List<String>) {
        if (typed.isEmpty() || typed == corrected) return
        recent.removeAll { it.corrected.equals(corrected, ignoreCase = true) }
        recent.addLast(Correction(typed, corrected,
            alternatives.filter { it != typed && it != corrected }.distinct().take(3)))
        while (recent.size > RECENT) recent.removeFirst()
    }

    /**
     * The suggestions to offer for [word] when the cursor is back at its end: what was typed
     * before autocorrect changed it, then the other suggestions; empty when it wasn't corrected.
     */
    @JvmStatic
    @Synchronized
    fun offersFor(word: String): List<String> {
        val c = recent.lastOrNull { it.corrected == word } ?: return emptyList()
        return listOf(c.typed) + c.alternatives
    }

    /** A backspace with [before] left before the cursor: going into a corrected word marks it unwanted. */
    @JvmStatic
    @Synchronized
    fun onDelete(before: CharSequence?) {
        if (before.isNullOrEmpty()) return
        val c = recent.lastOrNull() ?: return
        val text = before.toString()
        // the end of the text is the start of the corrected word (some of it deleted)
        for (k in c.corrected.length - 1 downTo 1) {
            if (text.endsWith(c.corrected.substring(0, k))) {
                val start = text.length - k
                if (start == 0 || !text[start - 1].isLetterOrDigit()) c.deleted = true
                return
            }
        }
    }

    /** A suggestion was picked from the bar: picking what was typed keeps that word as it is. */
    @JvmStatic
    @Synchronized
    fun picked(word: String) {
        val c = recent.lastOrNull { it.typed == word } ?: return
        keep(c.typed)
        recent.remove(c)
    }

    /**
     * Whether [typed] must stay as typed rather than become [suggestion]: a word put back
     * before, or typed again the same way after its correction to [suggestion] was deleted.
     */
    @JvmStatic
    @Synchronized
    fun shouldKeep(typed: String, suggestion: String): Boolean {
        val key = typed.lowercase()
        if (key in kept) return true
        val c = recent.lastOrNull { it.typed.equals(typed, ignoreCase = true) } ?: return false
        if (c.deleted && c.corrected.equals(suggestion, ignoreCase = true)) {
            keep(typed)
            recent.remove(c)
            return true
        }
        return false
    }

    private fun keep(word: String) {
        // incognito: nothing kept for later
        if (Incognito.active) return
        val key = word.lowercase()
        if (!kept.add(key)) return
        // the newest few hundred
        if (kept.size > MAX_KEPT) kept.remove(kept.first())
        prefs?.edit()?.putStringSet(PREF_KEPT, HashSet(kept))?.apply()
    }

    private const val RECENT = 20
    private const val MAX_KEPT = 500
    private const val PREF_KEPT = "lychee_kept_words"
}
