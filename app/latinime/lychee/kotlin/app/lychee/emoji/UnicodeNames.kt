package app.lychee.emoji

import android.content.Context
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Every named Unicode character (its official name, from the phone's own Unicode data) for
 * the Unicode search: by words of its name ("snowman", "greek small alpha") or by its code
 * point ("U+2603", "2603"). Chinese characters and Hangul syllables, whose names are just
 * their code points, are left out of the name search. The list is made once (about a second)
 * and kept in a file; it is read in the background as the keyboard starts ([warmUp]), so the
 * first search is instant.
 */
object UnicodeNames {
    private val loader = Executors.newSingleThreadExecutor()
    @Volatile
    private var codes: IntArray? = null
    @Volatile
    private var names: Array<String>? = null
    /** each name's words, upper case, for the search */
    @Volatile
    private var words: Array<Array<String>>? = null
    private var loading = false

    /** Reads (or makes) the list in the background, ahead of the first search. */
    @JvmStatic
    fun warmUp(context: Context) = load(context) {}

    /** Reads the names (once), then runs [then] on the main thread. */
    fun load(context: Context, then: () -> Unit) {
        if (codes != null) {
            then()
            return
        }
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val cache = File(context.applicationContext.cacheDir, "unicode-names-${android.os.Build.VERSION.SDK_INT}.txt")
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
            val read = runCatching {
                cache.forEachLine { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) {
                        cps += line.substring(0, tab).toInt(16)
                        ns += line.substring(tab + 1)
                    }
                }
                cps.isNotEmpty()
            }.getOrDefault(false)
            if (!read) {
                cps.clear()
                ns.clear()
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
                runCatching {
                    val tmp = File(cache.path + ".part")
                    tmp.bufferedWriter().use { w ->
                        for (i in cps.indices) w.append(Integer.toHexString(cps[i])).append('\t').append(ns[i]).append('\n')
                    }
                    tmp.renameTo(cache)
                }
            }
            names = ns.toTypedArray()
            words = Array(ns.size) { ns[it].split(' ', '-').toTypedArray() }
            codes = cps.toIntArray()
            main.post(then)
        }
    }

    /** Ranges whose names are only numbers: CJK ideographs, Hangul syllables, tangut ... */
    private fun skipped(cp: Int) = cp in 0x3400..0x4DBF || cp in 0x4E00..0x9FFF || cp in 0xAC00..0xD7A3 ||
        cp in 0xD800..0xF8FF || cp in 0x17000..0x18AFF || cp in 0x20000..0x3FFFF

    /** A character and its name. */
    data class Symbol(val text: String, val code: Int, val name: String) {
        /** How it is shown: a combining mark on a dotted circle, as charts show them. */
        val shown: String
            get() = when (Character.getType(code).toByte()) {
                Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> "◌$text"
                else -> text
            }
    }

    private val CODE = Regex("^(?:u\\+|0x|u)?([0-9a-f]{2,6})$")

    /**
     * Characters matching [query], best first, at most [limit], only those [drawable] says the
     * phone can show (a character no font has would be a blank box).
     */
    fun search(query: String, limit: Int = 40, drawable: (String) -> Boolean = { true }): List<Symbol> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Symbol>()
        // a code point typed: that character first (even one no font has: it was asked for)
        CODE.find(q)?.let { m ->
            val cp = m.groupValues[1].toIntOrNull(16)
            if (cp != null && cp in 0..0x10FFFF && Character.isDefined(cp) && Character.getType(cp) != Character.SURROGATE.toInt()) {
                out += Symbol(String(Character.toChars(cp)), cp, runCatching { Character.getName(cp) }.getOrNull() ?: "")
            }
        }
        val cps = codes ?: return out
        val ns = names ?: return out
        val ws = words ?: return out
        val terms = q.uppercase(Locale.ROOT).split(' ', '-').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return out
        val joined = terms.joinToString(" ")
        val found = ArrayList<Long>()  // rank << 32 | index, to sort without boxing pairs
        for (i in ns.indices) {
            val name = ns[i]
            var all = true
            for (t in terms) if (!name.contains(t)) { all = false; break }
            if (!all) continue
            // whole words and names starting with the query first, then shorter names
            var rank = name.length
            if (name == joined) rank -= 1000
            if (name.startsWith(terms[0])) rank -= 200
            if (terms.all { t -> ws[i].contains(t) }) rank -= 400
            found += ((rank + 2000).toLong() shl 32) or i.toLong()
        }
        found.sort()
        for (packed in found) {
            if (out.size >= limit) break
            val i = (packed and 0xFFFFFFFFL).toInt()
            if (out.any { it.code == cps[i] }) continue
            val text = String(Character.toChars(cps[i]))
            if (!drawable(text)) continue
            out += Symbol(text, cps[i], ns[i])
        }
        return out
    }
}
