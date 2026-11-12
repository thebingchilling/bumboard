package app.lychee.handwriting

import android.content.Context
import android.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * When turned on (Settings > Advanced > Save handwriting samples), each character written is
 * kept on the phone with what was typed for it and the recogniser's guesses, so misreadings can
 * be checked on real writing: Settings > About > Diagnostics shares them. Nothing leaves the
 * phone unless shared. The newest [KEEP] are kept.
 */
object HandwritingSamples {
    const val PREF = "lychee_hw_samples"
    private const val FILE = "handwriting-samples.jsonl"
    private const val KEEP = 500

    @Suppress("DEPRECATION")
    fun enabled(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF, false)

    @Synchronized
    fun save(context: Context, strokes: List<FloatArray>, typed: String?, guesses: List<String>, engine: String?) {
        if (!enabled(context) || strokes.isEmpty() || app.lychee.typing.Incognito.active) return
        runCatching {
            val ink = JSONArray()
            strokes.forEach { s -> ink.put(JSONArray().also { a -> s.forEach { a.put(Math.round(it).toInt()) } }) }
            val line = JSONObject()
                .put("lang", engine ?: "")
                .put("typed", typed ?: "")
                .put("guesses", JSONArray(guesses.take(10)))
                .put("ink", ink)
                .toString()
            val f = file(context)
            val lines = if (f.exists()) f.readLines().takeLast(KEEP - 1) else emptyList()
            f.writeText((lines + line).joinToString("\n", postfix = "\n"))
        }
    }

    fun count(context: Context): Int = runCatching { file(context).readLines().size }.getOrDefault(0)

    /** The saved samples, newest last, at most [limit] of them (sharing has a size limit). */
    fun read(context: Context, limit: Int = 150): String =
        runCatching { file(context).readLines().takeLast(limit).joinToString("\n") }.getOrDefault("")

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)
}
