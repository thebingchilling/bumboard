package app.lychee.core

/** Minimal read-only SQL access, implemented by Android's SQLiteDatabase in the app. */
interface Sql {
    fun <T> query(sql: String, args: Array<String>, row: (Row) -> T): List<T>

    interface Row {
        fun string(i: Int): String?
        fun long(i: Int): Long
    }
}

/** [Dictionary] over readings.db (schema: lychee/data/build_data.py, Readings.write). */
class ReadingsDb(private val sql: Sql) : Dictionary {

    /** How common [form] is: its most common headword's frequency (0 when unknown). */
    fun frequency(form: String): Long = sql.query(
        "SELECT MAX(hw.freq) FROM form JOIN hw ON hw.id = form.hw WHERE form.form = ?", arrayOf(form)
    ) { it.long(0) }.firstOrNull() ?: 0L

    /**
     * Chinese words whose meaning is the English [word] ("eat" finds 食, 吃): an exact meaning
     * ("eat", "to eat") first, then one meaning among several ("eat; dine"), commonest first.
     * Traditional and Simplified, as pairs.
     */
    fun english(word: String, limit: Int = 8): List<Pair<String, String>> {
        val w = word.trim().lowercase()
        if (w.isEmpty() || w.any { it == '%' || it == '_' }) return emptyList()
        return sql.query(
            "SELECT h.trad, h.simp, MIN(s.score) AS sc, h.freq FROM (" +
                "SELECT hw, CASE " +
                "WHEN lg = ?1 OR lg = ?2 THEN 0 " +
                "WHEN lg LIKE ?3 OR lg LIKE ?4 OR lg LIKE ?5 OR lg LIKE ?6 OR lg LIKE ?7 OR lg LIKE ?8 THEN 1 " +
                "ELSE 2 END AS score FROM (SELECT hw, lower(gloss) AS lg FROM sense)" +
                ") s JOIN hw h ON h.id = s.hw WHERE s.score < 2 GROUP BY h.id ORDER BY sc, h.freq DESC LIMIT " + limit,
            arrayOf(w, "to $w", "$w;%", "to $w;%", "%; $w", "%; to $w", "%; $w;%", "%; to $w;%"),
        ) { it.string(0)!! to it.string(1)!! }
    }

    /**
     * Every reading in [lang] ("yue" Jyutping, "cmn" pinyin) with its commonest headword's
     * frequency, commonest first: what glide typing on the Chinese keyboards can spell.
     */
    fun readingsByFrequency(lang: String, limit: Int): List<Pair<String, Long>> = sql.query(
        "SELECT r.r, MAX(h.freq) AS f FROM reading r JOIN hw h ON h.id = r.hw WHERE r.lang = ? " +
            "GROUP BY r.r ORDER BY f DESC LIMIT " + limit, arrayOf(lang),
    ) { it.string(0)!! to it.long(1) }

    /** The highest frequency of any headword. */
    val maxFrequency: Long by lazy {
        sql.query("SELECT MAX(freq) FROM hw", emptyArray()) { it.long(0) }.firstOrNull() ?: 0L
    }

    override fun lookup(form: String): List<Headword> {
        val heads = sql.query(
            "SELECT hw.id, hw.trad, hw.simp, hw.freq FROM form JOIN hw ON hw.id = form.hw WHERE form.form = ?",
            arrayOf(form)
        ) { Triple(it.long(0), it.string(1)!! to it.string(2)!!, it.long(3)) }
        if (heads.isEmpty()) return emptyList()
        val ids = heads.map { it.first }
        val marks = ids.joinToString(",") { "?" }
        val idArgs = ids.map { it.toString() }.toTypedArray()

        val readings = HashMap<Long, MutableMap<Lang, MutableList<String>>>()
        sql.query(
            "SELECT hw, lang, r FROM reading WHERE hw IN ($marks) ORDER BY hw, lang, rank", idArgs
        ) { Triple(it.long(0), Lang.of(it.string(1)!!), it.string(2)!!) }.forEach { (id, lang, r) ->
            readings.getOrPut(id) { mutableMapOf() }.getOrPut(lang) { mutableListOf() }.add(r)
        }

        val senses = HashMap<Long, MutableList<Sense>>()
        sql.query(
            "SELECT hw, lang, r, gloss, pos, reg, label, written, vern, colloc, src, alt " +
                "FROM sense WHERE hw IN ($marks) ORDER BY hw, ord", idArgs
        ) {
            it.long(0) to Sense(
                lang = Lang.of(it.string(1)!!),
                reading = it.string(2),
                gloss = it.string(3)!!,
                partOfSpeech = it.string(4),
                register = it.string(5),
                label = it.string(6),
                written = it.string(7),
                vernacular = it.string(8),
                collocation = it.string(9),
                source = it.string(10) ?: "",
                alt = it.string(11),
            )
        }.forEach { (id, s) -> senses.getOrPut(id) { mutableListOf() }.add(s) }

        val info = HashMap<Long, WordInfo>()
        sql.query("SELECT hw, hsk, band, reg, cl, pos FROM info WHERE hw IN ($marks)", idArgs) {
            it.long(0) to WordInfo(
                hsk = it.string(1)?.toIntOrNull(),
                band = it.string(2)?.toIntOrNull(),
                register = it.string(3),
                measureWords = it.string(4),
                partOfSpeech = it.string(5),
            )
        }.forEach { (id, i) -> info[id] = i }

        return heads.map { (id, forms, freq) ->
            Headword(id, forms.first, forms.second, freq, readings[id].orEmpty(), senses[id].orEmpty(),
                info[id] ?: WordInfo.None)
        }
    }

    /**
     * Words that continue [prefix] (what to write next after 學: 校, 生, 習 ...), most frequent
     * first, in Simplified when [simplified] else Traditional. Returns the rest of each word.
     */
    fun continuations(prefix: String, simplified: Boolean, limit: Int = 12): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val words = sql.query(
            "SELECT hw.trad, hw.simp FROM form JOIN hw ON hw.id = form.hw " +
                "WHERE form.form > ? AND form.form < ? ORDER BY hw.freq DESC LIMIT 60",
            arrayOf(prefix, prefix + "\uFFFF"),
        ) { it.string(0)!! to it.string(1)!! }
        val out = LinkedHashSet<String>()
        for ((trad, simp) in words) {
            val word = when {
                simplified && simp.startsWith(prefix) -> simp
                trad.startsWith(prefix) -> trad
                simp.startsWith(prefix) -> simp
                else -> continue
            }
            val rest = word.substring(prefix.length)
            if (rest.isNotEmpty() && rest.codePointCount(0, rest.length) <= 3) out += rest
            if (out.size >= limit) break
        }
        return out.toList()
    }

    /**
     * The commonest single character read only as [syllable] in [lang] (in every word it is
     * written in): said aloud, it can only come out as that sound. Null when there is none.
     */
    fun soundAlike(syllable: String, lang: Lang, simplified: Boolean): String? = sql.query(
        "SELECT hw.trad, hw.simp FROM reading r JOIN hw ON hw.id = r.hw " +
            "WHERE r.lang = ? AND r.r = ? AND r.rank = 0 AND length(hw.trad) = 1 " +
            "AND NOT EXISTS (SELECT 1 FROM form f JOIN reading o ON o.hw = f.hw " +
            "WHERE f.form = hw.trad AND o.lang = r.lang AND o.r <> r.r) " +
            "ORDER BY hw.freq DESC LIMIT 1",
        arrayOf(lang.code, syllable),
    ) { if (simplified) it.string(1) else it.string(0) }.firstOrNull()

    /**
     * [reading] (numbered syllables) spelt in characters that can only be read that way, so a
     * voice says exactly that reading (行 read hang4, not haang4); null when a syllable has
     * no such character.
     */
    fun sayAs(reading: String, lang: Lang, simplified: Boolean): String? {
        val syllables = reading.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (syllables.isEmpty()) return null
        val out = StringBuilder()
        for (s in syllables) out.append(soundAlike(s.lowercase(), lang, simplified) ?: return null)
        return out.toString()
    }

    /**
     * A character's Japanese or Korean readings from Unihan, most common first: [lang] "ja"
     * (kana: on'yomi in katakana, kun'yomi in hiragana) or "ko" (Hangul). Empty when there
     * are none (or the data predates them).
     */
    fun charReadings(ch: String, lang: String): List<String> = runCatching {
        sql.query("SELECT r FROM charread WHERE ch = ? AND lang = ? ORDER BY rank", arrayOf(ch, lang)) { it.string(0) }
            .filterNotNull()
    }.getOrDefault(emptyList())

    /** Every single character the dictionary has (Traditional and Simplified forms). */
    fun characters(): Set<String> = sql.query(
        "SELECT DISTINCT form FROM form WHERE length(form) = 1", emptyArray(),
    ) { it.string(0) }.filterNotNull().toHashSet()

    fun meta(key: String): String? =
        sql.query("SELECT v FROM meta WHERE k = ?", arrayOf(key)) { it.string(0) }.firstOrNull()
}
