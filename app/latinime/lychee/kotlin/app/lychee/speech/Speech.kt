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
        tts = newEngine(app) { status ->
            ready = status == TextToSpeech.SUCCESS
            val t = tts
            if (!ready || t == null) {
                tts = null
                return@newEngine
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
        tts = newEngine(app) { status ->
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
        // the voice's own pitch and speed (Settings > Pronunciation > Voices), else the engine's
        tune(context, engine, voice)
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

    /** Google's speech engine: preferred when installed, for its voices in every language. */
    const val GOOGLE_TTS = "com.google.android.tts"

    /** The engine Lychee speaks with: Google's when it is installed, else the phone's default. */
    fun engineName(context: Context): String? =
        runCatching { context.packageManager.getPackageInfo(GOOGLE_TTS, 0); GOOGLE_TTS }.getOrNull()

    /** A new engine (Google's when installed). */
    fun newEngine(context: Context, listener: TextToSpeech.OnInitListener): TextToSpeech =
        engineName(context)?.let { TextToSpeech(context, listener, it) } ?: TextToSpeech(context, listener)

    // each voice's own settings, by its name: a name of the user's, pitch and speed (x100), and
    // its measured pitch (Hz)
    const val PREF_VOICE_NAME = "lychee_voice_name_"
    const val PREF_VOICE_PITCH = "lychee_voice_pitch_"
    const val PREF_VOICE_RATE = "lychee_voice_rate_"
    const val PREF_VOICE_HZ = "lychee_voice_hz_"

    @Suppress("DEPRECATION")
    private fun prefs(context: Context) = android.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /** Sets [engine]'s pitch and speed to [voice]'s own (1.0 for none). */
    fun tune(context: Context, engine: TextToSpeech, voice: android.speech.tts.Voice?) {
        val p = prefs(context)
        val pitch = voice?.let { p.getInt(PREF_VOICE_PITCH + it.name, 100) } ?: 100
        val rate = voice?.let { p.getInt(PREF_VOICE_RATE + it.name, 100) } ?: 100
        runCatching {
            engine.setPitch(pitch / 100f)
            engine.setSpeechRate(rate / 100f)
        }
    }

    /** The short code a voice is known by in its engine ("cmn-cn-x-ccc-local" -> "ccc"). */
    fun voiceCode(v: android.speech.tts.Voice): String =
        Regex("-x-([a-z0-9]+)").find(v.name.lowercase())?.groupValues?.get(1) ?: v.name

    /**
     * A voice's name for people: the user's own name for it, else its place in the list, how high
     * or low it sounds (measured, see [measure]) and its code. No made-up names, no guessed
     * gender.
     */
    fun voiceLabel(context: Context, v: android.speech.tts.Voice, index: Int): String {
        val p = prefs(context)
        p.getString(PREF_VOICE_NAME + v.name, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val hz = p.getFloat(PREF_VOICE_HZ + v.name, 0f)
        val sound = when {
            hz <= 0f -> null
            hz < 145f -> "lower voice"
            hz < 190f -> "middle voice"
            else -> "higher voice"
        }
        return listOfNotNull("Voice ${index + 1}", sound, voiceCode(v)).joinToString(" · ")
    }

    /**
     * Measures how high [voice] sounds: says [text] into a file, finds the pitch of each voiced
     * stretch (autocorrelation) and keeps the median, in Hz, for [voiceLabel]. [done] gets it on
     * the main thread, or null when it couldn't be measured.
     */
    fun measure(context: Context, engine: TextToSpeech, voice: android.speech.tts.Voice, text: String, done: (Float?) -> Unit) {
        val app = context.applicationContext
        val file = java.io.File(app.cacheDir, "voice-sample.wav")
        val id = "lychee-measure-" + voice.name
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId != id) return
                val hz = runCatching { medianPitch(file.readBytes()) }.getOrNull()
                file.delete()
                if (hz != null) prefs(app).edit().putFloat(PREF_VOICE_HZ + voice.name, hz).apply()
                main.post { done(hz) }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { done(null) } }
        })
        runCatching {
            engine.setVoice(voice)
            engine.setPitch(1f)
            engine.setSpeechRate(1f)
            engine.synthesizeToFile(text, android.os.Bundle(), file, id)
        }.onFailure { done(null) }
    }

    /** The median pitch (Hz) of a 16-bit PCM WAV file's voiced stretches, or null. */
    private fun medianPitch(wav: ByteArray): Float? {
        if (wav.size < 64) return null
        fun le32(i: Int) = (wav[i].toInt() and 0xff) or ((wav[i + 1].toInt() and 0xff) shl 8) or
            ((wav[i + 2].toInt() and 0xff) shl 16) or ((wav[i + 3].toInt() and 0xff) shl 24)
        val rate = le32(24).takeIf { it in 8000..96000 } ?: return null
        // the samples: after the "data" chunk's header
        var at = 12
        var start = -1
        var size = 0
        while (at + 8 <= wav.size) {
            val idTag = String(wav, at, 4, Charsets.US_ASCII)
            val len = le32(at + 4)
            if (idTag == "data") { start = at + 8; size = minOf(len, wav.size - start); break }
            at += 8 + len
        }
        if (start < 0) return null
        val n = size / 2
        val x = FloatArray(n) { i -> ((wav[start + 2 * i].toInt() and 0xff) or (wav[start + 2 * i + 1].toInt() shl 8)).toShort() / 32768f }
        val frame = rate / 25   // 40 ms
        val minLag = rate / 400
        val maxLag = rate / 70
        val found = ArrayList<Float>()
        var f = 0
        while (f + frame + maxLag < n) {
            var energy = 0f
            for (i in f until f + frame) energy += x[i] * x[i]
            if (energy / frame > 1e-4f) {
                var best = 0f
                var bestLag = 0
                for (lag in minLag..maxLag) {
                    var c = 0f
                    for (i in f until f + frame) c += x[i] * x[i + lag]
                    if (c > best) { best = c; bestLag = lag }
                }
                if (bestLag > 0 && best / energy > 0.5f) found += rate.toFloat() / bestLag
            }
            f += frame
        }
        if (found.size < 3) return null
        found.sort()
        return found[found.size / 2]
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
