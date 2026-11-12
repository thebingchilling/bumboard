package app.lychee.handwriting

import android.content.Context
import android.content.SharedPreferences

/**
 * How often each character was picked from the handwriting guesses, on this phone only: the
 * characters someone writes often rank a little higher for them. Nothing is counted while
 * incognito; the few hundred most recent characters are kept.
 */
object HandwritingPicks {
    private const val FILE = "lychee_handwriting_picks"
    private const val MAX = 400
    private var cache: MutableMap<String, Int>? = null

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    private fun counts(context: Context): MutableMap<String, Int> = cache ?: LinkedHashMap<String, Int>().also { m ->
        prefs(context).all.forEach { (k, v) -> if (v is Int) m[k] = v }
        cache = m
    }

    @Synchronized
    fun count(context: Context, ch: String): Int = counts(context)[ch] ?: 0

    @Synchronized
    fun picked(context: Context, text: String) {
        if (app.lychee.typing.Incognito.active) return
        val m = counts(context)
        val edit = prefs(context).edit()
        text.codePoints().forEach { cp ->
            val ch = String(Character.toChars(cp))
            val n = (m.remove(ch) ?: 0) + 1
            m[ch] = n // most recent last
            edit.putInt(ch, n)
        }
        while (m.size > MAX) {
            val oldest = m.keys.first()
            m.remove(oldest)
            edit.remove(oldest)
        }
        edit.apply()
    }
}
