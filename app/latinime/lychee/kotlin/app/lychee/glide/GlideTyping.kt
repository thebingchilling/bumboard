package app.lychee.glide

import android.content.Context
import android.util.Log
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.BinaryDictionary
import com.android.inputmethod.latin.BinaryDictionaryGetter
import com.android.inputmethod.latin.Dictionary
import com.android.inputmethod.latin.common.InputPointers
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Glide typing for LatinIME's batch input. LatinIME's own gesture decoder was never open
 * source, so the path is decoded here ([GlideDecoder]) against a compact list of the main
 * dictionary's words, read once per language in the background (about 2 MB) and then reused.
 */
object GlideTyping {
    private const val TAG = "LycheeGlide"
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var decoder: GlideDecoder? = null
    @Volatile
    private var locale: Locale? = null

    /** What the loaded list is for: a locale (English ...) or a Rime engine ("yue" ...). */
    @Volatile
    private var engine: String? = null

    /** Reads the word list for [newLocale] if it is not already loaded (or loading). */
    @JvmStatic
    fun prepare(context: Context, newLocale: Locale?) {
        if (newLocale == null || (newLocale == locale && engine == null)) return
        locale = newLocale
        engine = null
        decoder = null
        val app = context.applicationContext
        executor.execute {
            if (locale != newLocale || engine != null) return@execute
            val built = runCatching { build(app, newLocale) }
                .onFailure { Log.w(TAG, "glide word list unavailable", it) }.getOrNull()
            if (locale == newLocale && engine == null) decoder = built
        }
    }

    /**
     * The glide list for a Rime keyboard ([newEngine] "yue", "cmn", "ja", "ko"): its words as
     * they are typed in letters (Jyutping, pinyin, romaji, romanised Korean), commonest first.
     * The letters a glide spells go to Rime like typed keys, and Rime shows the characters.
     */
    @JvmStatic
    fun prepareEngine(context: Context, newEngine: String) {
        if (newEngine == engine) return
        engine = newEngine
        locale = null
        decoder = null
        val appContext = context.applicationContext
        val build = {
            if (engine == newEngine) {
                val built = runCatching { buildRoman(appContext, newEngine) }
                    .onFailure { Log.w(TAG, "glide word list for $newEngine unavailable", it) }.getOrNull()
                if (engine == newEngine) decoder = built
            }
        }
        if (newEngine == "yue" || newEngine == "cmn") {
            // the readings dictionary opens first (it may already be open)
            app.lychee.chinese.Readings.open(appContext) { executor.execute { build() } }
        } else {
            executor.execute { build() }
        }
    }

    private fun buildRoman(context: Context, engine: String): GlideDecoder? {
        val builder = GlideWords.Builder()
        when (engine) {
            "yue", "cmn" -> {
                val list = app.lychee.chinese.Readings.db?.readingsByFrequency(engine, MAX_ROMAN).orEmpty()
                val top = list.firstOrNull()?.second?.coerceAtLeast(1L) ?: 1L
                for ((reading, freq) in list) {
                    // "nei5 hou2" → neihou; pinyin's ü as Rime takes it (v)
                    val letters = reading.lowercase().replace("u:", "v").replace('ü', 'v').filter { it in 'a'..'z' }
                    if (letters.isEmpty()) continue
                    builder.add(letters, scale(kotlin.math.ln(freq + 1.0) / kotlin.math.ln(top + 1.0)))
                }
            }
            "ja", "ko" -> {
                val list = app.lychee.dict.PackData.glideWords(context, engine, MAX_ROMAN)
                val n = list.size.coerceAtLeast(1)
                list.forEachIndexed { i, (word, _) ->
                    val roman = if (engine == "ja") app.lychee.core.SentenceRomanizer.kana(word)
                        else app.lychee.core.SentenceRomanizer.korean(word)
                    val letters = roman.lowercase().filter { it in 'a'..'z' }
                    if (letters.isNotEmpty()) builder.add(letters, scale(1.0 - i.toDouble() / n))
                }
            }
        }
        val words = builder.build()
        Log.i(TAG, "glide words for $engine: ${words.size}")
        return if (words.size > 0) GlideDecoder(words) else null
    }

    /** 0..1 to the list's 0..255 frequency (never 0, so rare words still come up). */
    private fun scale(x: Double) = (20 + 235 * x.coerceIn(0.0, 1.0)).toInt()

    private const val MAX_ROMAN = 60_000

    private fun build(context: Context, locale: Locale): GlideDecoder? {
        val builder = GlideWords.Builder()
        val files = BinaryDictionaryGetter.getDictionaryFiles(locale, context, false)
        for (file in files) {
            val dict = BinaryDictionary(file.mFilename, file.mOffset, file.mLength, false, locale,
                Dictionary.TYPE_MAIN, false)
            try {
                dict.forEachWord { word, probability -> builder.add(word, probability) }
            } finally {
                dict.close()
            }
        }
        val words = builder.build()
        Log.i(TAG, "glide words for $locale: ${words.size}")
        return if (words.size > 0) GlideDecoder(words) else null
    }

    @JvmStatic
    fun isReady() = decoder != null

    /** The best words for the glide in [pointers] over [keyboard], best first. */
    @JvmStatic
    fun decode(pointers: InputPointers, keyboard: Keyboard): List<GlideMatch> {
        val decoder = decoder ?: return emptyList()
        val keys = HashMap<Char, KeyCenter>()
        for (key in keyboard.sortedKeys) {
            val code = key.code
            if (code <= 0) continue
            val c = Character.toLowerCase(code.toChar())
            if (c in 'a'..'z' && c !in keys) {
                keys[c] = KeyCenter(key.x + key.width / 2f, key.y + key.height / 2f)
            }
        }
        return decoder.decode(pointers.xCoordinates, pointers.yCoordinates, pointers.pointerSize, keys,
            keyboard.mMostCommonKeyWidth.toFloat())
    }
}
