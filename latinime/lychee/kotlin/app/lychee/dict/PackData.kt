package app.lychee.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.lychee.packs.Packs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A Japanese or Korean dictionary entry (from the rime_ja or dict_ko pack). */
data class WordEntry(
    val form: String,
    /** the dictionary form (먹다 for 먹어요) */
    val lemma: String,
    /** kana (Japanese) or Revised Romanization (Korean) */
    val reading: String?,
    val hanja: String?,
    val partOfSpeech: String?,
    /** one meaning per line, most common first */
    val senses: List<String>,
)

/** A character's strokes (SVG paths in a 1024 box, y up) and breakdown, from the strokes pack. */
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
        else -> null
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

    fun hasStrokes(context: Context) = db(context, "strokes_zh", "strokes.db") != null

    /** Every character's stroke shapes (medians, as stored), for the handwriting stroke matcher. */
    fun allMedians(context: Context): List<Pair<String, String>> {
        val db = db(context, "strokes_zh", "strokes.db") ?: return emptyList()
        return runCatching {
            db.rawQuery("SELECT ch, medians FROM char", null).use { c ->
                val out = ArrayList<Pair<String, String>>(c.count)
                while (c.moveToNext()) out += c.getString(0) to c.getString(1)
                out
            }
        }.getOrDefault(emptyList())
    }

    fun strokes(context: Context, char: String): CharStrokes? {
        val db = db(context, "strokes_zh", "strokes.db") ?: return null
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
