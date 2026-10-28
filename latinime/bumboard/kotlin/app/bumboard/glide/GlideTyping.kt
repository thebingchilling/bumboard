package app.bumboard.glide

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
    private const val TAG = "BumGlide"
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var decoder: GlideDecoder? = null
    @Volatile
    private var locale: Locale? = null

    /** Reads the word list for [newLocale] if it is not already loaded (or loading). */
    @JvmStatic
    fun prepare(context: Context, newLocale: Locale?) {
        if (newLocale == null || newLocale == locale) return
        locale = newLocale
        decoder = null
        val app = context.applicationContext
        executor.execute {
            if (locale != newLocale) return@execute
            val built = runCatching { build(app, newLocale) }
                .onFailure { Log.w(TAG, "glide word list unavailable", it) }.getOrNull()
            if (locale == newLocale) decoder = built
        }
    }

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
