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

        return heads.map { (id, forms, freq) ->
            Headword(id, forms.first, forms.second, freq, readings[id].orEmpty(), senses[id].orEmpty())
        }
    }

    fun meta(key: String): String? =
        sql.query("SELECT v FROM meta WHERE k = ?", arrayOf(key)) { it.string(0) }.firstOrNull()
}
