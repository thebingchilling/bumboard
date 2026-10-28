package app.bumboard.handwriting

import android.content.Context
import android.preference.PreferenceManager
import app.bumboard.chinese.ChineseInput
import app.bumboard.packs.Packs
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
            if (!loaded) loaded = runCatching { System.loadLibrary("bumzinnia") }.isSuccess
            return loaded
        }

        /** Opens [model]; null when it can't be read. */
        fun open(model: File): Zinnia? {
            if (!load() || !model.exists()) return null
            val handle = nativeOpen(model.path)
            return if (handle != 0L) Zinnia(handle) else null
        }

        /**
         * The handwriting pack for the current keyboard ([engine]: "yue", "cmn", "ja" or null):
         * Traditional or Simplified Chinese (following 繁/简), Japanese; otherwise any installed.
         */
        fun modelFor(context: Context, engine: String?): File? {
            @Suppress("DEPRECATION")
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val simplified = prefs.getString(ChineseInput.PREF_SCRIPT, "traditional") == "simplified"
            val wanted = when (engine) {
                "ja" -> listOf("hw_ja", "hw_zh_hant", "hw_zh_hans")
                else -> if (simplified) listOf("hw_zh_hans", "hw_zh_hant", "hw_ja") else listOf("hw_zh_hant", "hw_zh_hans", "hw_ja")
            }
            for (id in wanted) {
                if (!Packs.isInstalled(context, id)) continue
                val dir = Packs.dir(context, id)
                dir.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".model") }?.let { return it }
            }
            return null
        }

        @JvmStatic private external fun nativeOpen(path: String): Long
        @JvmStatic private external fun nativeClose(handle: Long)
        @JvmStatic private external fun nativeClassify(handle: Long, width: Int, height: Int, strokes: FloatArray, nbest: Int): Array<String?>
    }
}
