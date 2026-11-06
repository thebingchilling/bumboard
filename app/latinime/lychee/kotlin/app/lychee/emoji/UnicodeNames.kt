package app.lychee.emoji

import java.util.Locale
import java.util.concurrent.Executors

/**
 * Every named Unicode character (its official name, from the phone's own Unicode data) for
 * the Unicode search: by words of its name ("snowman", "greek small alpha") or by its code
 * point ("U+2603", "2603"). Chinese characters and Hangul syllables, whose names are just
 * their code points, are left out of the name search. The list is read once, in the
 * background, when the search first opens (about a second).
 */
object UnicodeNames {
    private val loader = Executors.newSingleThreadExecutor()
    @Volatile
    private var codes: IntArray? = null
    @Volatile
    private var names: Array<String>? = null
    private var loading = false

    /** Reads the names (once), then runs [then] on the main thread. */
    fun load(then: () -> Unit) {
        if (codes != null) {
            then()
            return
        }
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        synchronized(this) {
            if (loading) {
                loader.execute { main.post(then) }
                return
            }
            loading = true
        }
        loader.execute {
            val cps = ArrayList<Int>(40000)
            val ns = ArrayList<String>(40000)
            var cp = 0x20
            while (cp <= 0x3FFFF) {
                if (skipped(cp)) { cp++; continue }
                val type = Character.getType(cp)
                if (type != Character.UNASSIGNED.toInt() && type != Character.SURROGATE.toInt() &&
                    type != Character.PRIVATE_USE.toInt() && type != Character.CONTROL.toInt()) {
                    val name = runCatching { Character.getName(cp) }.getOrNull()
                    if (!name.isNullOrEmpty()) {
                        cps += cp
                        ns += name
                    }
                }
                cp++
            }
            codes = cps.toIntArray()
            names = ns.toTypedArray()
            main.post(then)
        }
    }

    /** Ranges whose names are only numbers: CJK ideographs, Hangul syllables, tangut ... */
    private fun skipped(cp: Int) = cp in 0x3400..0x4DBF || cp in 0x4E00..0x9FFF || cp in 0xAC00..0xD7A3 ||
        cp in 0xD800..0xF8FF || cp in 0x17000..0x18AFF || cp in 0x20000..0x3FFFF

    /** A character and its name. */
    data class Symbol(val text: String, val code: Int, val name: String)

    private val CODE = Regex("^(?:u\\+|0x|u)?([0-9a-f]{2,6})$")

    /** Characters matching [query], best first, at most [limit]. */
    fun search(query: String, limit: Int = 40): List<Symbol> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Symbol>()
        // a code point typed: that character first
        CODE.find(q)?.let { m ->
            val cp = m.groupValues[1].toIntOrNull(16)
            if (cp != null && cp in 0..0x10FFFF && Character.isDefined(cp) && Character.getType(cp) != Character.SURROGATE.toInt()) {
                out += Symbol(String(Character.toChars(cp)), cp, runCatching { Character.getName(cp) }.getOrNull() ?: "")
            }
        }
        val cps = codes ?: return out
        val ns = names ?: return out
        val words = q.uppercase(Locale.ROOT).split(' ', '-').filter { it.isNotEmpty() }
        if (words.isEmpty()) return out
        val found = ArrayList<Pair<Int, Int>>()  // (index, rank)
        for (i in ns.indices) {
            val name = ns[i]
            if (!words.all { name.contains(it) }) continue
            // whole words and names starting with the query first, then shorter names
            var rank = name.length
            if (name == words.joinToString(" ")) rank -= 1000
            if (name.startsWith(words[0])) rank -= 200
            if (words.all { w -> name.split(' ', '-').contains(w) }) rank -= 400
            found += i to rank
        }
        found.sortBy { it.second }
        for ((i, _) in found) {
            if (out.size >= limit) break
            if (out.any { it.code == cps[i] }) continue
            out += Symbol(String(Character.toChars(cps[i])), cps[i], ns[i])
        }
        return out
    }
}
