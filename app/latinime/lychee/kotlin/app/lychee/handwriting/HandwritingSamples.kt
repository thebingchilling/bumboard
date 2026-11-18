package app.lychee.handwriting

import android.content.Context
import android.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * When turned on (Settings > Advanced > Save handwriting samples), each character written is
 * kept on the phone with what was typed for it and the recogniser's guesses, so misreadings can
 * be checked on real writing, and the recogniser trained on real handwriting
 * (packs/handwriting/real): Settings > About > Diagnostics shares them or saves them to a file.
 * Nothing leaves the phone unless shared. The newest [KEEP] are kept.
 */
object HandwritingSamples {
    const val PREF = "lychee_hw_samples"
    private const val FILE = "handwriting-samples.jsonl"
    private const val KEEP = 20000
    /** Past this size the file is cut back to the newest [KEEP]. */
    private const val MAX_BYTES = 40L shl 20

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
            // added to the end (rewriting thousands of lines for each character was slow), and
            // only now and then cut back
            f.appendText(line + "\n")
            if (f.length() > MAX_BYTES) f.writeText(f.readLines().takeLast(KEEP).joinToString("\n", postfix = "\n"))
        }
    }

    fun count(context: Context): Int = runCatching { file(context).readLines().size }.getOrDefault(0)

    /** The saved samples, newest last, at most [limit] of them (sharing has a size limit). */
    fun read(context: Context, limit: Int = 150): String =
        runCatching { file(context).readLines().takeLast(limit).joinToString("\n") }.getOrDefault("")

    /** Every saved sample, one JSON object per line, to [out] (Diagnostics > Save to a file). */
    fun writeAll(context: Context, out: java.io.OutputStream) {
        val f = file(context)
        if (f.exists()) f.inputStream().use { it.copyTo(out) }
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)
}
