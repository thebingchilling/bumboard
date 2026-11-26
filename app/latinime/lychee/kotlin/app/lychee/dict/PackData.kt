package app.lychee.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.lychee.packs.Packs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A Japanese, Korean or English dictionary entry (from the rime_ja, dict_ko or dict_en pack). */
data class WordEntry(
    val form: String,
    /** the dictionary form (먹다 for 먹어요) */
    val lemma: String,
    /** kana (Japanese), Revised Romanization (Korean) or IPA (English) */
    val reading: String?,
    val hanja: String?,
    val partOfSpeech: String?,
    /** one meaning per line, most common first */
    val senses: List<String>,
)

/**
 * A character's strokes (SVG paths in a 1024 box, y up) and breakdown, from the strokes pack.
 * Japanese and Korean characters have only their strokes' centre lines (no [strokes]).
 */
data class CharStrokes(
    val char: String,
    val strokes: List<String>,
    /** each stroke's centre line, as points, in drawing order */
    val medians: List<List<Pair<Float, Float>>>,
    val decomposition: String?,
    val radical: String?,
    val definition: String?,
    val hint: String?,
)

/**
 * The SQLite databases inside the installed data packs, read in place (SQLite pages them in
 * as needed, so a large pack costs disk, not memory). Re-opened when a pack is updated.
 */
object PackData {
    private const val TAG = "LycheePackData"
    private val open = HashMap<String, Pair<String, SQLiteDatabase>>()

    @Synchronized
    private fun db(context: Context, packId: String, name: String): SQLiteDatabase? {
        val file = File(Packs.dir(context, packId), "data/$name")
        val key = file.path
        if (!file.exists()) {
            open.remove(key)?.second?.close()
            return null
        }
        val stamp = "${Packs.installedVersion(context, packId)}:${file.lastModified()}"
        open[key]?.let { (s, db) -> if (s == stamp) return db else db.close() }
        return try {
            SQLiteDatabase.openDatabase(
                file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            ).also { open[key] = stamp to it }
        } catch (t: Throwable) {
            Log.e(TAG, "can't open $key", t)
            null
        }
    }

    /** Whether the meanings for a keyboard's engine ("ja", "ko") are installed. */
    fun hasWords(context: Context, engine: String?): Boolean = wordDb(context, engine) != null

    private fun wordDb(context: Context, engine: String?) = when (engine) {
        "ja" -> db(context, "rime_ja", "ja.db")
        "ko" -> db(context, "dict_ko", "ko.db")
        "en" -> db(context, "dict_en", "en.db")
        else -> null
    }

    /**
     * English entries for [word] as typed: the word itself (lower case), else its stem by the
     * usual endings (cats, running, tried, bigger ...); irregular forms are in the data.
     */
    fun englishWords(context: Context, word: String, limit: Int = 4): List<WordEntry> {
        // curly apostrophes as the dictionary writes them: don’t → don't
        val w = word.lowercase().trim().replace('\u2019', '\'')
        if (w.isEmpty()) return emptyList()
        words(context, "en", w, limit).takeIf { it.isNotEmpty() }?.let { return it }
        val stems = buildList {
            fun strip(suffix: String, vararg ends: String) {
                if (w.length > suffix.length + 1 && w.endsWith(suffix)) {
                    val base = w.dropLast(suffix.length)
                    ends.forEach { add(base + it) }
                    // a doubled last letter: running -> run, bigger -> big
                    if (base.length > 2 && base.last() == base[base.length - 2]) add(base.dropLast(1))
                }
            }
            strip("ies", "y"); strip("ied", "y"); strip("es", ""); strip("s", "")
            strip("ing", "", "e"); strip("ed", "", "e"); strip("er", "", "e"); strip("est", "", "e")
            strip("ly", "", "le"); strip("'s", "")
        }
        for (stem in stems.distinct()) {
            words(context, "en", stem, limit).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return emptyList()
    }

    /**
     * The commonest English word pronounced only as [ipa] ("/lɛd/"), other than [word]: said
     * aloud, it says that pronunciation (lead the metal as "led", not as in "lead the way").
     * Null when no word sounds just like it.
     */
    fun homophone(context: Context, ipa: String, word: String): String? {
        val db = wordDb(context, "en") ?: return null
        val core = Regex("/[^/]+/").find(ipa)?.value ?: return null
        val like = "%$core%"
        return runCatching {
            db.rawQuery(
                "SELECT form FROM word w WHERE reading LIKE ? AND form <> ? AND form = lower(lemma) " +
                    "AND NOT EXISTS (SELECT 1 FROM word o WHERE o.form = w.form AND IFNULL(o.reading, '') NOT LIKE ?) " +
                    "ORDER BY rank LIMIT 1",
                arrayOf(like, word.lowercase(), like),
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    /**
     * The Japanese or Korean dictionary's words for glide typing, commonest first: Japanese
     * readings (kana), Korean words (Hangul), each with its rank (0 the commonest).
     */
    fun glideWords(context: Context, engine: String, limit: Int): List<Pair<String, Int>> {
        val db = wordDb(context, engine) ?: return emptyList()
        val column = if (engine == "ja") "reading" else "form"
        return runCatching {
            db.rawQuery(
                "SELECT $column, MIN(rank) AS k FROM word WHERE $column IS NOT NULL AND $column != '' " +
                    "GROUP BY $column ORDER BY k LIMIT $limit", null,
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getInt(1)) } }
        }.getOrDefault(emptyList())
    }

    /**
     * A kanji's KANJIDIC2 entry (in the Japanese pack): readings, meanings, grade, strokes, and
     * (version 3 packs) its old four-level JLPT level, newspaper frequency rank and parts.
     */
    data class KanjiInfo(
        val onyomi: String, val kunyomi: String, val meanings: String, val grade: Int?, val strokes: Int?,
        val jlpt: Int? = null, val freq: Int? = null, val parts: String? = null,
    )

    fun kanji(context: Context, ch: String): KanjiInfo? {
        val db = wordDb(context, "ja") ?: return null
        fun Int.orNull(c: android.database.Cursor) = if (c.isNull(this)) null else c.getInt(this)
        return runCatching {
            db.rawQuery("SELECT onyomi, kunyomi, meanings, grade, strokes, jlpt, freq, parts FROM kanji WHERE ch = ?", arrayOf(ch)).use { c ->
                if (!c.moveToFirst()) null else KanjiInfo(
                    c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2).orEmpty(),
                    3.orNull(c), 4.orNull(c), 5.orNull(c), 6.orNull(c), c.getString(7),
                )
            }
        }.recoverCatching {
            // an older pack: no JLPT, frequency or parts columns
            db.rawQuery("SELECT onyomi, kunyomi, meanings, grade, strokes FROM kanji WHERE ch = ?", arrayOf(ch)).use { c ->
                if (!c.moveToFirst()) null else KanjiInfo(
                    c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2).orEmpty(),
                    3.orNull(c), 4.orNull(c),
                )
            }
        }.getOrNull() // an older pack has no kanji table
    }

    /**
     * A Japanese word's learner extras (Japanese pack version 3): its JLPT level (5 = N5 ...
     * 1 = N1), its pitch accents (the mora after which the pitch drops, 0 for none; several when
     * it varies, possibly marked by part of speech: "(副)0,(名)3") and its conjugation class
     * (JapaneseConjugation's). Null fields when the pack has none.
     */
    data class JapaneseExtras(val jlpt: Int?, val accent: String?, val vclass: String?)

    fun japaneseExtras(context: Context, form: String, reading: String?): JapaneseExtras? {
        val db = wordDb(context, "ja") ?: return null
        fun first(sql: String, vararg args: String): String? = runCatching {
            db.rawQuery(sql, args).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
        }.getOrNull()
        val r = reading ?: form
        val jlpt = first("SELECT level FROM jlpt WHERE form = ? AND reading = ? ORDER BY level DESC LIMIT 1", form, r)
            ?: first("SELECT level FROM jlpt WHERE form = ? ORDER BY level DESC LIMIT 1", form)
        val accent = first("SELECT accent FROM accent WHERE form = ? AND reading = ? LIMIT 1", form, r)
            ?: first("SELECT accent FROM accent WHERE form = ? LIMIT 1", form)
        val vclass = first("SELECT vclass FROM word WHERE form = ? AND vclass IS NOT NULL ORDER BY (lemma = form) DESC, rank LIMIT 1", form)
        if (jlpt == null && accent == null && vclass == null) return null
        return JapaneseExtras(jlpt?.toIntOrNull(), accent, vclass)
    }

    /**
     * A Korean word's learner extras (Korean pack, the National Institute of Korean Language's
     * learner dictionary): its level (1 beginner, 2 intermediate, 3 advanced), its standard
     * pronunciation (학교 -> 학꾜) and its key conjugated forms with their pronunciations.
     */
    data class KoreanExtras(val level: Int?, val pron: String?, val forms: List<Pair<String, String>>, val pos: String?)

    fun koreanExtras(context: Context, lemma: String): KoreanExtras? {
        val db = wordDb(context, "ko") ?: return null
        return runCatching {
            // a graded homonym first
            db.rawQuery("SELECT level, pron, forms, pos FROM nikl WHERE lemma = ? ORDER BY (level IS NULL), level LIMIT 1", arrayOf(lemma)).use { c ->
                if (!c.moveToFirst()) null else KoreanExtras(
                    if (c.isNull(0)) null else c.getInt(0),
                    c.getString(1),
                    c.getString(2).orEmpty().split('\n').mapNotNull { line ->
                        line.split('|').takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0] to it[1] }
                    },
                    c.getString(3),
                )
            }
        }.getOrNull() // an older pack has no nikl table
    }

    /** Entries for [text] typed on the [engine] keyboard, the word itself before forms of others. */
    fun words(context: Context, engine: String?, text: String, limit: Int = 4): List<WordEntry> {
        val db = wordDb(context, engine) ?: return emptyList()
        return runCatching {
            db.rawQuery(
                "SELECT form, lemma, reading, hanja, pos, gloss FROM word WHERE form = ? " +
                    "ORDER BY (lemma = form) DESC, rank LIMIT $limit",
                arrayOf(text),
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        WordEntry(
                            c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                            c.getString(5).split('\n').filter { it.isNotBlank() },
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Words and phrases that begin with [text] on the [engine] keyboard ("ja", "ko", "en"), the
     * commonest first: what the word builds (食べ → 食べ物, 학교 → 학교생활).
     */
    fun phrases(context: Context, engine: String?, text: String, limit: Int = 8): List<WordEntry> {
        forScreensPhrases?.let { return it(text) }
        val db = wordDb(context, engine) ?: return emptyList()
        return runCatching {
            // a range over the index rather than LIKE (which would read every row)
            db.rawQuery(
                "SELECT form, lemma, reading, hanja, pos, gloss FROM word WHERE form > ? AND form < ? AND lemma = form " +
                    "ORDER BY rank LIMIT ${limit * 2}",
                arrayOf(text, text + "\uFFFF"),
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        WordEntry(
                            c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                            c.getString(5).split('\n').filter { it.isNotBlank() },
                        )
                    )
                }
            }
        }.getOrDefault(emptyList()).distinctBy { it.lemma }.take(limit)
    }

    /** Screenshots only: stand-ins for the packs, which are built in CI. */
    @JvmStatic var forScreensStrokes: ((String) -> CharStrokes?)? = null
    @JvmStatic var forScreensPhrases: ((String) -> List<WordEntry>)? = null
    @JvmStatic var forScreensContaining: ((String) -> List<String>)? = null

    /**
     * The parts [char] is built from (學 → ⺍ 冖 子), from its breakdown in the strokes pack (the
     * structure marks and unknown parts left out).
     */
    fun components(context: Context, char: String): List<String> {
        val decomposition = strokes(context, char)?.decomposition ?: return emptyList()
        return decomposition.codePoints().toArray()
            .filter { it !in 0x2FF0..0x2FFF && it != '？'.code && it != '?'.code && String(Character.toChars(it)) != char }
            .map { String(Character.toChars(it)) }.distinct()
    }

    /** Characters with [char] among their parts (子 → 字 學 好 ...), up to [limit]. */
    fun containing(context: Context, char: String, limit: Int = 40): List<String> {
        forScreensContaining?.let { return it(char).take(limit) }
        val db = db(context, "strokes_zh", "strokes.db") ?: return emptyList()
        return runCatching {
            db.rawQuery("SELECT ch FROM char WHERE decomposition LIKE ? AND ch != ? LIMIT ${limit * 4}", arrayOf("%$char%", char)).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        }.getOrDefault(emptyList())
    }

    /** Korean words written with the Hanja [hanja] (學校 → 학교), their meanings first by rank. */
    fun wordsByHanja(context: Context, hanja: String, limit: Int = 4): List<WordEntry> {
        val db = wordDb(context, "ko") ?: return emptyList()
        return runCatching {
            db.rawQuery(
                "SELECT form, lemma, reading, hanja, pos, gloss FROM word WHERE hanja = ? " +
                    "ORDER BY (lemma = form) DESC, rank LIMIT $limit",
                arrayOf(hanja),
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        WordEntry(
                            c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                            c.getString(5).split('\n').filter { it.isNotBlank() },
                        )
                    )
                }
            }
        }.getOrDefault(emptyList()).distinctBy { it.lemma }
    }

    fun hasStrokes(context: Context) = db(context, "strokes_zh", "strokes.db") != null

    /** Every character's stroke shapes (medians, as stored), for the handwriting stroke matcher. */
    /** The characters Japanese writes (KanjiVG's kana and kanji), or null without the stroke pack. */
    fun japaneseCharacters(context: Context): Set<String>? {
        val db = db(context, "strokes_zh", "strokes.db") ?: return null
        return runCatching {
            db.rawQuery("SELECT ch FROM char_ja", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun allMedians(context: Context): List<Pair<String, String>> {
        val db = db(context, "strokes_zh", "strokes.db") ?: return emptyList()
        val chinese = runCatching {
            db.rawQuery("SELECT ch, medians FROM char", null).use { c ->
                val out = ArrayList<Pair<String, String>>(c.count)
                while (c.moveToNext()) out += c.getString(0) to c.getString(1)
                out
            }
        }.getOrDefault(emptyList())
        // the kana too (from the Japanese stroke order), so Japanese handwriting finds them
        val kana = runCatching {
            db.rawQuery("SELECT ch, medians FROM char_ja WHERE ch >= '\u3040' AND ch <= '\u30ff'", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            }
        }.getOrDefault(emptyList())
        return chinese + kana
    }

    /**
     * [char]'s stroke order: Japanese ([lang] "ja": KanjiVG's, for kana and kanji) or Korean
     * (Hangul) when there is one, otherwise Chinese (Make Me a Hanzi's).
     */
    fun strokes(context: Context, char: String, lang: String? = null): CharStrokes? {
        forScreensStrokes?.let { return it(char) }
        val db = db(context, "strokes_zh", "strokes.db") ?: return null
        val cp = char.codePointAt(0)
        val local = when {
            cp in 0xAC00..0xD7A3 || cp in 0x3131..0x318E -> "char_ko"
            cp in 0x3040..0x30FF || lang == "ja" -> "char_ja"
            else -> null
        }
        if (local != null) {
            runCatching {
                db.rawQuery("SELECT medians FROM $local WHERE ch = ?", arrayOf(char)).use { c ->
                    if (c.moveToFirst()) return CharStrokes(char, emptyList(), parseMedians(c.getString(0)), null, null, null, null)
                }
            }
        }
        return runCatching {
            db.rawQuery(
                "SELECT strokes, medians, decomposition, radical, definition, etymology FROM char WHERE ch = ?",
                arrayOf(char),
            ).use { c ->
                if (!c.moveToFirst()) return null
                val strokes = JSONArray(c.getString(0)).let { a -> List(a.length()) { a.getString(it) } }
                val medians = JSONArray(c.getString(1)).let { a ->
                    List(a.length()) { i ->
                        val pts = a.getJSONArray(i)
                        List(pts.length()) { j ->
                            val p = pts.getJSONArray(j)
                            p.getDouble(0).toFloat() to p.getDouble(1).toFloat()
                        }
                    }
                }
                val hint = c.getString(5)?.let { runCatching { JSONObject(it).optString("hint") }.getOrNull() }
                CharStrokes(char, strokes, medians, c.getString(2), c.getString(3), c.getString(4),
                    hint?.takeIf { it.isNotBlank() })
            }
        }.getOrNull()
    }

    private fun parseMedians(json: String): List<List<Pair<Float, Float>>> = JSONArray(json).let { a ->
        List(a.length()) { i ->
            val pts = a.getJSONArray(i)
            List(pts.length()) { j ->
                val p = pts.getJSONArray(j)
                p.getDouble(0).toFloat() to p.getDouble(1).toFloat()
            }
        }
    }

    /** Up to [limit] (sentence, English) pairs in [lang] ("yue", "cmn", "ja", "ko") using any of [forms]. */
    fun examples(context: Context, lang: String, forms: Collection<String>, limit: Int = 3): List<Pair<String, String>> {
        val db = db(context, "examples", "examples.db") ?: return emptyList()
        val distinct = forms.filter { it.isNotBlank() }.distinct().take(4)
        if (distinct.isEmpty()) return emptyList()
        val where = distinct.joinToString(" OR ") { "text LIKE ?" }
        val args = arrayOf(lang) + distinct.map { "%$it%" }
        return runCatching {
            db.rawQuery("SELECT text, en FROM ex WHERE lang = ? AND ($where) LIMIT $limit", args).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            }
        }.getOrDefault(emptyList())
    }
}
