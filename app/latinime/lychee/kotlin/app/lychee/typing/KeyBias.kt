package app.lychee.typing

import android.content.Context
import app.lychee.DiagLog
import com.android.inputmethod.keyboard.Key
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.BinaryDictionary
import com.android.inputmethod.latin.BinaryDictionaryGetter
import com.android.inputmethod.latin.Dictionary
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Keys that know what is likely next, like the mainstream keyboards': a press on the edge
 * between two letter keys types the letter more likely to follow what is being typed
 * ([LetterModel], made from the keyboard's own words: LatinIME's dictionary for English and the
 * other alphabets, Rime's romanisation for Chinese, Japanese and Korean). A press well inside a
 * key always types that key. The model is made once per keyboard language, in the background,
 * and kept in a file.
 */
object KeyBias {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var model: LetterModel? = null
    @Volatile
    private var language: String? = null
    /** The letters of the word being typed (the last few). */
    private val context = StringBuilder()

    /** Gets the model for a keyboard: [engine] ("yue", "cmn", "ja", "ko") or [locale]'s words. */
    @JvmStatic
    fun prepare(appContext: Context, engine: String?, locale: Locale?) {
        val lang = engine ?: locale?.toString() ?: return
        if (lang == language) return
        language = lang
        model = null
        val app = appContext.applicationContext
        executor.execute {
            if (language != lang) return@execute
            val built = runCatching { load(app, lang) ?: build(app, engine, locale)?.also { save(app, lang, it) } }
                .onFailure { DiagLog.log("key model for $lang failed", it) }
                .getOrNull()
            if (language == lang) {
                model = built
                // nothing to make it from yet (a pack still unpacking): tried again next time
                if (built == null) language = null
            }
        }
    }

    /** What was typed: letters join the word, anything else ends it. */
    @JvmStatic
    @Synchronized
    fun onKey(code: Int, isDelete: Boolean) {
        when {
            isDelete -> if (context.isNotEmpty()) context.setLength(context.length - 1)
            code in 'a'.code..'z'.code || code in 'A'.code..'Z'.code -> {
                context.append(Character.toLowerCase(code.toChar()))
                if (context.length > 8) context.delete(0, context.length - 8)
            }
            code > 0 -> context.setLength(0)
        }
    }

    /** A new word starts (a suggestion picked, the cursor moved, a new field). */
    @JvmStatic
    @Synchronized
    fun reset() = context.setLength(0)

    /**
     * The key a press at ([x], [y]) types, given that it hit [hit]: [hit] itself, or a letter
     * key whose edge the press nearly reached when that letter is far likelier next.
     */
    @JvmStatic
    fun choose(keyboard: Keyboard, hit: Key?, x: Int, y: Int): Key? {
        val m = model ?: return hit
        if (hit == null || !isLetter(hit.code) || !keyboard.mId.isAlphabetKeyboard) return hit
        val width = keyboard.mMostCommonKeyWidth.toFloat().takeIf { it > 0 } ?: return hit
        val keys = ArrayList<Key>()
        val candidates = ArrayList<Pair<Char, Float>>()
        keys += hit
        candidates += Character.toLowerCase(hit.code.toChar()) to 0f
        for (key in keyboard.getNearestKeys(x, y)) {
            if (key === hit || !isLetter(key.code)) continue
            val distance = kotlin.math.sqrt(key.squaredDistanceToEdge(x, y).toFloat()) / width
            if (distance > LetterModel.REACH) continue
            keys += key
            candidates += Character.toLowerCase(key.code.toChar()) to distance
        }
        if (keys.size == 1) return hit
        val typed = synchronized(this) { context.toString() }
        return keys[LetterModel.choose(m, typed, candidates)]
    }

    private fun isLetter(code: Int) = code in 'a'.code..'z'.code || code in 'A'.code..'Z'.code

    // ---------------------------------------------------------------- making the model

    private fun build(context: Context, engine: String?, locale: Locale?): LetterModel? {
        val builder = LetterModel.Builder()
        var words = 0
        if (engine != null) {
            // Rime's dictionaries: each entry's romanisation, its syllables run together (as typed)
            val shared = File(context.filesDir, "rime/shared")
            val files = when (engine) {
                "yue" -> listOf("jyut6ping3.chars.dict.yaml", "jyut6ping3.words.dict.yaml")
                "cmn" -> listOf("lychee_cmn.dict.yaml")
                "ja" -> listOf("lychee_ja.dict.yaml")
                "ko" -> listOf("lychee_ko.dict.yaml")
                else -> emptyList()
            }
            for (name in files) {
                val f = File(shared, name)
                if (!f.exists()) continue
                var body = false
                f.forEachLine { line ->
                    if (!body) {
                        if (line.startsWith("...")) body = true
                        return@forEachLine
                    }
                    val parts = line.split('\t')
                    if (parts.size < 2) return@forEachLine
                    val code = parts[1].filter { it in 'a'..'z' }
                    if (code.isEmpty()) return@forEachLine
                    val weight = parts.getOrNull(2)?.trim()?.toDoubleOrNull() ?: 1.0
                    builder.add(code, LetterModel.weightOf(weight.coerceAtLeast(1.0)))
                    words++
                }
            }
        } else if (locale != null) {
            for (file in BinaryDictionaryGetter.getDictionaryFiles(locale, context, false)) {
                val dict = BinaryDictionary(file.mFilename, file.mOffset, file.mLength, false, locale,
                    Dictionary.TYPE_MAIN, false)
                try {
                    dict.forEachWord { word, probability ->
                        // LatinIME's probabilities are log-scaled (0-255)
                        builder.add(word, Math.pow(10.0, probability / 40.0))
                        words++
                    }
                } finally {
                    dict.close()
                }
            }
        }
        DiagLog.log("key model for ${engine ?: locale}: $words words")
        return if (words > 0) builder.build() else null
    }

    private fun file(context: Context, lang: String) = File(context.cacheDir, "keymodel-$VERSION-$lang.bin")

    private fun load(context: Context, lang: String): LetterModel? {
        val f = file(context, lang)
        if (!f.exists()) return null
        return runCatching {
            DataInputStream(f.inputStream().buffered()).use { input ->
                val n = input.readInt()
                LetterModel.fromArray(FloatArray(n) { input.readFloat() })
            }
        }.getOrNull()
    }

    private fun save(context: Context, lang: String, model: LetterModel) {
        runCatching {
            val counts = model.toArray()
            DataOutputStream(file(context, lang).outputStream().buffered()).use { out ->
                out.writeInt(counts.size)
                counts.forEach { out.writeFloat(it) }
            }
        }
    }

    /** Bumped when the way models are made changes, so old files aren't used. */
    private const val VERSION = 1
}
