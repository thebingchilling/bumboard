package app.lychee.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import com.android.inputmethod.latin.R
import java.util.Locale

/**
 * Tap to hear: the phone's own text-to-speech. Lychee has no network access; the engine works
 * offline once its voice data for the language is installed (Settings > Text-to-speech).
 */
object Speech {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, List<Locale>>? = null

    /** Speaks [text] in the first of [locales] the engine has a voice for. */
    fun speak(context: Context, text: String, locales: List<Locale>) {
        val engine = tts
        if (engine != null && ready) {
            say(context, engine, text, locales)
            return
        }
        pending = text to locales
        if (engine != null) return
        val app = context.applicationContext
        tts = TextToSpeech(app) { status ->
            ready = status == TextToSpeech.SUCCESS
            val p = pending
            pending = null
            val t = tts
            if (!ready || t == null) {
                Toast.makeText(app, R.string.lychee_speech_unavailable, Toast.LENGTH_SHORT).show()
                tts = null
            } else if (p != null) {
                say(app, t, p.first, p.second)
            }
        }
    }

    private fun say(context: Context, engine: TextToSpeech, text: String, locales: List<Locale>) {
        val locale = locales.firstOrNull {
            engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
        }
        if (locale == null) {
            Toast.makeText(context, R.string.lychee_speech_no_voice, Toast.LENGTH_LONG).show()
            return
        }
        engine.language = locale
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lychee")
    }

    /** Voices to try for a keyboard's engine ("yue", "cmn", "ja", "ko"), best first. */
    fun localesFor(engine: String?, simplified: Boolean): List<Locale> = when (engine) {
        "yue" -> listOf(Locale.forLanguageTag("yue-HK"), Locale.forLanguageTag("zh-HK"))
        "ja" -> listOf(Locale.JAPAN)
        "ko" -> listOf(Locale.KOREA)
        else -> if (simplified) listOf(Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE)
        else listOf(Locale.TRADITIONAL_CHINESE, Locale.SIMPLIFIED_CHINESE)
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
