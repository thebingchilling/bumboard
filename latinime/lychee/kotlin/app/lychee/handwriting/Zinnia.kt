package app.lychee.handwriting

import android.content.Context
import android.preference.PreferenceManager
import app.lychee.chinese.ChineseInput
import app.lychee.packs.Packs
import java.io.File

/**
 * Zinnia (libbumzinnia.so): recognises one handwritten character from its strokes. A model is
 * opened (memory-mapped) only while the handwriting pane is open; [close] frees it.
 */
class Zinnia private constructor(private var handle: Long) {

    /** Candidates for [strokes] (x, y pairs; negative x ends a stroke) drawn in [width] x [height]. */
    fun classify(width: Int, height: Int, strokes: FloatArray, count: Int = 10): List<String> {
        if (handle == 0L) return emptyList()
        return nativeClassify(handle, width, height, strokes, count).filterNotNull()
    }

    fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0L
    }

    companion object {
        private var loaded = false

        private fun load(): Boolean {
            if (!loaded) loaded = runCatching { System.loadLibrary("lycheezinnia") }.isSuccess
            return loaded
        }

        /** Opens [model]; null when it can't be read. */
        fun open(model: File): Zinnia? {
            if (!load() || !model.exists()) return null
            val handle = nativeOpen(model.path)
            return if (handle != 0L) Zinnia(handle) else null
        }

        /**
         * The handwriting models for the current keyboard ([engine]: "yue", "cmn", "ja" or null):
         * the Chinese pack's Traditional and Simplified models (the script typed first), or
         * the Japanese one; otherwise whatever is installed. Older single-script packs count too.
         */
        fun modelsFor(context: Context, engine: String?): List<File> {
            @Suppress("DEPRECATION")
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val simplified = app.lychee.chinese.ChineseScript.candidatesSimplified(prefs)
            fun models(id: String): List<File> {
                if (!Packs.isInstalled(context, id)) return emptyList()
                val files = Packs.dir(context, id).walkTopDown().filter { it.isFile && it.name.endsWith(".model") }.toList()
                // hans.model / hant.model: the script being typed first
                return files.sortedBy { if (it.name.startsWith(if (simplified) "hans" else "hant")) 0 else 1 }
            }
            // the keyboard's own language only: Cantonese writes Traditional, Mandarin the script
            // being typed, Japanese Japanese
            val traditional = engine == "yue" || (engine != "ja" && !simplified)
            val zh = models("hw_zh")
            val chinese = zh.filter { it.name.startsWith(if (traditional) "hant" else "hans") }.ifEmpty {
                models(if (traditional) "hw_zh_hant" else "hw_zh_hans")
            }.ifEmpty { zh }
            val japanese = models("hw_ja")
            return if (engine == "ja") japanese.ifEmpty { chinese } else chinese.ifEmpty { japanese }
        }

        /** Opens [models] together; null when none can be read. */
        fun openAll(models: List<File>): ZinniaSet? =
            models.mapNotNull { open(it) }.takeIf { it.isNotEmpty() }?.let { ZinniaSet(it) }

        @JvmStatic private external fun nativeOpen(path: String): Long
        @JvmStatic private external fun nativeClose(handle: Long)
        @JvmStatic private external fun nativeClassify(handle: Long, width: Int, height: Int, strokes: FloatArray, nbest: Int): Array<String?>
    }
}

/** Several models asked together (Traditional and Simplified): their guesses interleaved, best first. */
class ZinniaSet(private val models: List<Zinnia>) {
    fun classify(width: Int, height: Int, strokes: FloatArray, count: Int = 10): List<String> {
        val lists = models.map { it.classify(width, height, strokes, count) }
        val out = LinkedHashSet<String>()
        for (i in 0 until count) for (list in lists) list.getOrNull(i)?.let { out += it }
        return out.take(count)
    }

    fun close() = models.forEach { it.close() }
}
