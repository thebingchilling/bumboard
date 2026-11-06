package app.lychee.typing

import android.content.Context
import app.lychee.DiagLog
import com.android.inputmethod.keyboard.Key
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.BinaryDictionary
import com.android.inputmethod.latin.BinaryDictionaryGetter
import com.android.inputmethod.latin.Dictionary
import com.android.inputmethod.latin.common.Constants
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The main keys' touch model, like the mainstream keyboards': a press types the key it most
 * likely meant ([KeyChooser]), from where it landed against where this person's presses for
 * each key land ([TouchModel], learnt as they type and kept in a file) and how likely each
 * letter, or the end of the word (the space bar), is next in the word being typed
 * ([LetterModel], made once per keyboard language in the background from its own words:
 * LatinIME's dictionary for English and the other alphabets, Rime's romanisation for Chinese,
 * Japanese and Korean, and kept in a file).
 */
object KeyBias {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var model: LetterModel? = null
    @Volatile
    private var language: String? = null
    /** Words typed word by word (not romanisation typed as one run): the space ends a word. */
    @Volatile
    private var wordByWord = false
    @Volatile
    private var touch: TouchModel? = null
    private var appContext: Context? = null
    @Volatile
    private var keyboard: Keyboard? = null

    /** The letters of the word being typed, and whether that is all of it. */
    private val word = StringBuilder()
    private var whole = true
    /** The word's presses not yet known to be right: letter, offset x, offset y. */
    private val pending = ArrayList<FloatArray>()
    private var learnt = 0

    /** Gets the models for a keyboard: [engine] ("yue", "cmn", "ja", "ko") or [locale]'s words. */
    @JvmStatic
    fun prepare(context: Context, engine: String?, locale: Locale?) {
        val app = context.applicationContext
        if (appContext == null) {
            appContext = app
            executor.execute { touch = loadTouch(app) ?: TouchModel() }
        }
        val lang = engine ?: locale?.toString() ?: return
        wordByWord = engine == null
        if (lang == language) return
        language = lang
        model = null
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

    /**
     * What was typed, at ([x], [y]) on the keys (negative when not pressed on them): letters
     * join the word, a delete takes back the last, anything else ends it. A word's presses
     * that stayed typed teach the touch model where this person presses each key.
     */
    @JvmStatic
    @Synchronized
    fun onKey(code: Int, isDelete: Boolean, x: Int, y: Int) {
        when {
            isDelete -> {
                if (word.isNotEmpty()) word.setLength(word.length - 1) else whole = true
                if (pending.isNotEmpty()) pending.removeAt(pending.size - 1)
            }
            isLetter(code) -> {
                word.append(Character.toLowerCase(code.toChar()))
                if (word.length > 24) {
                    word.delete(0, word.length - 24)
                    whole = false
                }
                remember(code, x, y)
            }
            code > 0 -> {
                // the word ended as typed: its presses were right
                if (code == Constants.CODE_SPACE || !Character.isLetterOrDigit(code)) confirm()
                pending.clear()
                word.setLength(0)
                whole = true
            }
        }
    }

    /** A new word starts (a suggestion picked, the cursor moved, a new field). */
    @JvmStatic
    @Synchronized
    fun reset() {
        // a picked suggestion may have fixed the presses: they teach nothing
        pending.clear()
        word.setLength(0)
        whole = true
    }

    private fun remember(code: Int, x: Int, y: Int) {
        val kb = keyboard ?: return
        if (x < 0 || y < 0 || !kb.mId.isAlphabetKeyboard) return
        val key = kb.getKey(code) ?: return
        val w = kb.mMostCommonKeyWidth.toFloat()
        val h = kb.mMostCommonKeyHeight.toFloat()
        if (w <= 0 || h <= 0) return
        val dx = (x - (key.x + key.width / 2f)) / w
        val dy = (y - (key.y + key.height / 2f)) / h
        pending += floatArrayOf(Character.toLowerCase(code).toFloat(), dx, dy)
        if (pending.size > 32) pending.removeAt(0)
    }

    private fun confirm() {
        val t = touch ?: return
        for (p in pending) t.learn(p[0].toInt().toChar(), p[1], p[2])
        learnt += pending.size
        if (learnt >= 40) {
            learnt = 0
            val app = appContext ?: return
            val counts = t.toArray()
            executor.execute { saveTouch(app, counts) }
        }
    }

    /**
     * The key a press at ([x], [y]) on [keyboard] means, given that it is over [hit]: [hit]
     * itself, or a key beside it (a letter or the space bar) that is likelier meant.
     */
    @JvmStatic
    fun choose(keyboard: Keyboard, hit: Key?, x: Int, y: Int): Key? {
        this.keyboard = keyboard
        if (hit == null || !keyboard.mId.isAlphabetKeyboard) return hit
        val w = keyboard.mMostCommonKeyWidth.toFloat()
        val h = keyboard.mMostCommonKeyHeight.toFloat()
        if (w <= 0 || h <= 0) return hit
        val typed: String
        val all: Boolean
        synchronized(this) {
            typed = word.toString()
            all = whole
        }
        // the space bar counts only in a word typed word by word, as its end
        val space = wordByWord && typed.isNotEmpty()
        if (!isLetter(hit.code) && !(space && hit.code == Constants.CODE_SPACE)) return hit
        val t = touch
        val keys = ArrayList<Key>()
        val candidates = ArrayList<KeyChooser.Candidate>()
        fun consider(key: Key) {
            if (keys.any { it === key }) return
            val c = when {
                isLetter(key.code) -> {
                    val letter = Character.toLowerCase(key.code.toChar())
                    val off = t?.offset(letter)
                    val aimX = key.x + key.width / 2f + (off?.get(0) ?: 0f) * w
                    val aimY = key.y + key.height / 2f + (off?.get(1) ?: 0f) * h
                    KeyChooser.Candidate(letter, (x - aimX) / w, (y - aimY) / h)
                }
                space && key.code == Constants.CODE_SPACE -> {
                    // a long key: aimed anywhere along it
                    val aimX = x.toFloat().coerceIn(key.x + w / 2, maxOf(key.x + w / 2, key.x + key.width - w / 2))
                    val aimY = key.y + key.height / 2f
                    KeyChooser.Candidate(' ', (x - aimX) / w, (y - aimY) / h)
                }
                else -> return
            }
            keys += key
            candidates += c
        }
        consider(hit)
        if (keys.isEmpty()) return hit
        for (key in keyboard.getNearestKeys(x, y)) consider(key)
        if (keys.size == 1) return hit
        return keys[KeyChooser.choose(model, typed, all, candidates, 0)]
    }

    private fun isLetter(code: Int) = code in 'a'.code..'z'.code || code in 'A'.code..'Z'.code

    // ---------------------------------------------------------------- making the model

    private fun build(context: Context, engine: String?, locale: Locale?): LetterModel? {
        // romanisation is typed as one run of syllables: no word beginnings
        val builder = LetterModel.Builder(prefixes = engine == null)
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
                    dict.forEachWord { w, probability ->
                        // LatinIME's probabilities are log-scaled (0-255)
                        builder.add(w, Math.pow(10.0, probability / 40.0))
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
        return runCatching { DataInputStream(f.inputStream().buffered()).use { LetterModel.read(it) } }.getOrNull()
    }

    private fun save(context: Context, lang: String, model: LetterModel) {
        runCatching { DataOutputStream(file(context, lang).outputStream().buffered()).use { model.write(it) } }
    }

    /** Where this person presses: kept with the app's files (it is theirs, not a cache). */
    private fun touchFile(context: Context) = File(context.filesDir, "touch-model.bin")

    private fun loadTouch(context: Context): TouchModel? = runCatching {
        DataInputStream(touchFile(context).inputStream().buffered()).use { input ->
            val n = input.readInt()
            TouchModel.fromArray(FloatArray(n) { input.readFloat() })
        }
    }.getOrNull()

    private fun saveTouch(context: Context, counts: FloatArray) {
        runCatching {
            val f = touchFile(context)
            val tmp = File(f.path + ".part")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(counts.size)
                counts.forEach { out.writeFloat(it) }
            }
            tmp.renameTo(f)
        }
    }

    /** Bumped when the way models are made changes, so old files aren't used. */
    private const val VERSION = 2
}
