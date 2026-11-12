package app.lychee.lookup

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import app.lychee.chinese.ChineseScript
import app.lychee.chinese.DictionaryPopup
import app.lychee.chinese.Readings
import app.lychee.core.InputMode
import app.lychee.core.Lang
import app.lychee.core.Resolved
import app.lychee.dict.PackData
import app.lychee.speech.Speech
import com.android.inputmethod.latin.R
import java.util.concurrent.Executors

/**
 * "Look up in Lychee" in Android's menu for selected text (Copy, Paste, Share ...): the word
 * card for what was selected, floating over the app, from the dictionaries on the phone (no
 * network). Chinese, Japanese and Korean get their usual card; an English word gets its
 * English card with the word in Chinese at the top, a tap replacing the selection with it
 * (when the text can be changed). The card's right-hand button copies the word. A tap outside
 * closes it.
 */
class LookupActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var host: FrameLayout
    private var popup: DictionaryPopup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()?.take(MAX_CHARS)
        if (text.isNullOrEmpty()) {
            finish()
            return
        }
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        val root = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { finish() }
        }
        host = FrameLayout(this)
        root.addView(host, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = dp(24) })
        setContentView(root)
        root.post { show(text, readOnly, root.height) }
    }

    private fun show(text: String, readOnly: Boolean, height: Int) {
        val room = (height * 0.7f).toInt()
        val card = DictionaryPopup(this, standalone = host, standaloneRoom = room, onDismiss = { finish() },
            actionIcon = R.drawable.ic_sym_content_copy, actionLabel = R.string.lychee_lookup_copy)
        popup = card
        val copyWord = { copy(text) }
        val script = app.lychee.fonts.LycheeFonts.scriptOf(text)
        val hasHan = text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }
        when {
            script == "ko" || script == "ja" -> word(card, text, script!!, copyWord)
            hasHan -> chinese(card, text, copyWord)
            text.any { it.isLetter() } -> english(card, text, readOnly, copyWord)
            else -> finish()
        }
    }

    /** A Japanese or Korean word: its entry from the dictionary packs. */
    private fun word(card: DictionaryPopup, text: String, lang: String, copyWord: () -> Unit) = background({
        val entries = PackData.words(this, lang, text)
        entries to PackData.examples(this, lang, listOf(text) + entries.map { it.lemma }.take(1))
    }) { (entries, examples) ->
        val locales = Speech.localesFor(lang, false)
        card.showWord(host, text, null, entries, examples, false,
            onSpeak = { t, _ -> Speech.speak(this, t, locales) }, lang = lang,
            onSpeakAs = { t, _ -> Speech.speak(this, t, locales) }, onType = copyWord)
    }

    /** Chinese: Cantonese or Mandarin readings as the 繁/简 key leans (Mandarin for Simplified). */
    @Suppress("DEPRECATION")
    private fun chinese(card: DictionaryPopup, text: String, copyWord: () -> Unit) {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val simplified = ChineseScript.candidatesSimplified(prefs)
        val mode = if (simplified) InputMode.Mandarin else InputMode.Cantonese
        Readings.open(this) {
            background({
                Readings.annotator?.resolve(text, "", mode)
                    ?: Resolved(mode.lang ?: Lang.Cantonese, null, null, null, emptyList(), true)
            }) { resolved ->
                card.show(host, text, resolved, Readings.displayPrefs(prefs), false,
                    onSpeak = { t, lang ->
                        val voices = when (lang) {
                            Lang.Cantonese -> Speech.localesFor("yue", false)
                            Lang.Mandarin -> Speech.localesFor("cmn", simplified)
                            else -> Speech.localesFor(if (simplified) "cmn" else "yue", simplified)
                        }
                        Speech.speak(this, t, voices)
                    }, onType = copyWord)
            }
        }
    }

    /** English: the English card, with the word in Chinese at the top. */
    @Suppress("DEPRECATION")
    private fun english(card: DictionaryPopup, text: String, readOnly: Boolean, copyWord: () -> Unit) {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val simplified = ChineseScript.candidatesSimplified(prefs)
        Readings.open(this) {
            background({
                val word = text.lowercase()
                val chinese = runCatching { Readings.db?.english(word).orEmpty() }.getOrDefault(emptyList())
                    .map { (trad, simp) -> if (simplified) simp else trad }.distinct().take(6)
                PackData.englishWords(this, word) to chinese
            }) { (entries, chinese) ->
                val locales = listOf(java.util.Locale.UK, java.util.Locale.US)
                card.showWord(host, text, null, entries, emptyList(), false,
                    onSpeak = { t, _ -> Speech.speak(this, t, locales) }, lang = "en",
                    onSpeakAs = { t, locale -> Speech.speak(this, t, listOfNotNull(locale) + locales) },
                    translations = chinese,
                    onTranslation = { t -> if (readOnly) copy(t) else replace(t) },
                    onType = copyWord)
            }
        }
    }

    /** Puts [text] in the selection's place and closes. */
    private fun replace(text: String) {
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        finish()
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(text, text))
        Toast.makeText(this, R.string.lychee_lookup_copied, Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Runs [work] off the main thread, then [then] with its result on it (unless closed). */
    private fun <T> background(work: () -> T, then: (T) -> Unit) {
        worker.execute {
            val result = runCatching(work).getOrNull() ?: return@execute
            main.post { if (!isFinishing && !isDestroyed) then(result) }
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        /** A word or a phrase, not a page. */
        const val MAX_CHARS = 40
        val worker = Executors.newSingleThreadExecutor()
    }
}
