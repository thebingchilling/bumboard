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

    /**
     * Starts the engine ahead of time (it takes a few seconds to connect and load a voice), so
     * the first speak button tapped speaks at once.
     */
    fun warmUp(context: Context) {
        mandarinTaiwan = Pronunciation.mandarinTaiwan(context)
        if (tts != null) return
        val app = context.applicationContext
        tts = TextToSpeech(app) { status ->
            ready = status == TextToSpeech.SUCCESS
            val t = tts
            if (!ready || t == null) {
                tts = null
                return@TextToSpeech
            }
            val p = pending
            pending = null
            if (p != null) say(app, t, p.first, p.second)
        }
    }

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
        if (text.isBlank()) return
        // the exact region first (ja-JP, ko-KR, zh-TW ...), else any installed voice of the
        // language (some engines list only "ja" or "ko"), never one that needs the internet
        val voices = installedVoices(engine)
        // the voice chosen for this language in Settings > Pronunciation, when it is there
        val chosen = locales.firstOrNull()?.let { l ->
            @Suppress("DEPRECATION")
            android.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
                .getString(PREF_VOICE + l.toLanguageTag(), null)
        }?.let { name -> voices.firstOrNull { it.name == name } }
        val voice = chosen ?: locales.firstNotNullOfOrNull { l ->
            voices.filter { it.locale.language == l.language && (l.country.isEmpty() || it.locale.country == l.country) }
                .maxByOrNull { it.quality }
        } ?: locales.firstNotNullOfOrNull { l ->
            // any voice of the language, but never a Mandarin voice for Cantonese (it reads the
            // characters in Mandarin): Cantonese is yue, or Chinese of Hong Kong or Macau only
            if (cantonese(l)) null else voices.filter { it.locale.language == l.language }.maxByOrNull { it.quality }
        }
        val ok = if (voice != null) {
            runCatching { engine.setVoice(voice) == TextToSpeech.SUCCESS }.getOrDefault(false)
        } else {
            val locale = locales.firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
                ?: locales.filterNot(::cantonese).map { Locale(it.language) }.firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
            locale != null && engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
        }
        if (!ok) {
            Toast.makeText(context, R.string.lychee_speech_no_voice, Toast.LENGTH_LONG).show()
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lychee")
    }

    private fun cantonese(l: Locale) = l.language == "yue" || (l.language == "zh" && (l.country == "HK" || l.country == "MO"))

    /** Whether Mandarin is said Taiwan's way (Settings > Pronunciation); set by [Pronunciation]'s users. */
    @Volatile
    var mandarinTaiwan = false

    /**
     * Voices to try for a keyboard's engine ("yue", "cmn", "ja", "ko"), best first. Mandarin in
     * the region chosen in Settings > Pronunciation (the Mainland's or Taiwan's voice).
     */
    @Suppress("UNUSED_PARAMETER")
    fun localesFor(engine: String?, simplified: Boolean): List<Locale> = when (engine) {
        "yue" -> listOf(Locale.forLanguageTag("yue-HK"), Locale.forLanguageTag("zh-HK"))
        "ja" -> listOf(Locale.JAPAN, Locale.JAPANESE)
        "ko" -> listOf(Locale.KOREA, Locale.KOREAN)
        else -> if (mandarinTaiwan) listOf(Locale.TRADITIONAL_CHINESE, Locale.SIMPLIFIED_CHINESE)
        else listOf(Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE)
    }

    /** The voice chosen for a language, by its tag ("yue-HK", "zh-CN", "ja-JP" ...); none: the engine's. */
    const val PREF_VOICE = "lychee_voice_"

    /** The engine's voices that work offline and are installed. */
    fun installedVoices(engine: TextToSpeech): List<android.speech.tts.Voice> =
        runCatching { engine.voices }.getOrNull().orEmpty()
            .filter { !it.isNetworkConnectionRequired && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }

    /** The voices for a language tag: its own region's, else any of the language (Cantonese: yue, or zh-HK). */
    fun voicesFor(engine: TextToSpeech, tag: String): List<android.speech.tts.Voice> {
        val want = Locale.forLanguageTag(tag)
        val all = installedVoices(engine)
        fun matches(v: android.speech.tts.Voice) = when (want.language) {
            "yue" -> v.locale.language == "yue" || (v.locale.language == "zh" && v.locale.country == "HK")
            else -> v.locale.language == want.language && (want.country.isEmpty() || v.locale.country == want.country)
        }
        return all.filter(::matches).ifEmpty { all.filter { it.locale.language == want.language } }.sortedBy { it.name }
    }

    /** A voice's name for people: female or male when its name says, else its place in the list. */
    fun voiceLabel(v: android.speech.tts.Voice, index: Int): String {
        val n = v.name.lowercase()
        val gender = when {
            "female" in n || Regex("[-_#]f\\d|smtf|-f-").containsMatchIn(n) -> "Female"
            "male" in n || Regex("[-_#]m\\d|smtm|-m-").containsMatchIn(n) -> "Male"
            else -> null
        }
        val number = "Voice ${index + 1}"
        return if (gender != null) "$number · $gender" else number
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
