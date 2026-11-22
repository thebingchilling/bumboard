package app.lychee.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import com.android.inputmethod.latin.R

/**
 * Voice typing, started and stopped by the mic key alone: a tap starts listening and the mic
 * glows (brighter as you speak, [onLevel]); a second tap stops, and what was said is typed. No bar
 * opens over the candidates. Speech goes to Google's recognition service when the phone has it
 * (Speech Services by Google, then the Google app), or to the system default; that service does
 * its own networking, Lychee itself never does. Nothing is typed until the recogniser is done, so
 * LatinIME's own composing state is never disturbed. When voice typing can't start or fails, a
 * short message says why.
 *
 * The recogniser's start and stop tones play at the media volume, loud for a key that is easily
 * touched by accident: while listening, the media volume is turned down (to a third of where it
 * was), and put back afterwards unless it was changed meanwhile.
 *
 * Kept as a view (it has no size and never shows) so the layout and LatinIME find it as before.
 */
class VoicePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** Final recognised text, ready to type. */
    var onText: (String) -> Unit = {}
    /** Listening started (true) or ended (false): the mic key glows while it listens. */
    var onListening: (Boolean) -> Unit = {}
    /** How loud the speech is now, 0..1, for the mic's glow. */
    var onLevel: (Float) -> Unit = {}

    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())

    val isActive: Boolean get() = recognizer != null

    init {
        visibility = GONE
    }

    /** The mic key: starts listening, or stops (and types what was said) when listening. */
    fun toggle(language: String?): Boolean {
        val r = recognizer
        if (r != null) {
            runCatching { r.stopListening() }.onFailure { cancel() }
            return true
        }
        return start(language)
    }

    /**
     * Starts listening in [language] (a BCP 47 tag, or null for the recogniser's default).
     * Returns false when voice typing cannot start here (no recogniser, or the microphone
     * permission still has to be granted, in which case the permission screen is opened).
     */
    fun start(language: String?): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            try {
                context.startActivity(Intent(context, MicPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            } catch (t: Throwable) {
                Log.w(TAG, "can't ask for the microphone", t)
            }
            return false
        }
        return try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "no speech recogniser on this phone")
                say(R.string.lychee_voice_no_recogniser)
                return false
            }
            cancel()
            val r = googleService()?.let { SpeechRecognizer.createSpeechRecognizer(context, it) }
                ?: SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(listener)
            quieten()
            onListening(true)
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                .apply { language?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) } })
            true
        } catch (t: Throwable) {
            // never take the keyboard down with it
            Log.e(TAG, "voice typing failed to start", t)
            app.lychee.DiagLog.log("voice typing failed to start", t)
            cancel()
            say(R.string.lychee_voice_error)
            false
        }
    }

    /** Stops listening without typing anything. */
    fun cancel() {
        // a recogniser already gone (its service restarted, the permission just granted) must
        // not take the keyboard down with it
        val r = recognizer ?: return
        recognizer = null
        runCatching { r.setRecognitionListener(null) }
        runCatching { r.cancel() }
        runCatching { r.destroy() }
        ended()
    }

    private fun finish(text: String?) {
        cancel()
        if (!text.isNullOrBlank()) onText(text)
    }

    private fun ended() {
        onLevel(0f)
        onListening(false)
        // after the recogniser's closing tone
        main.postDelayed(::restoreVolume, 700)
    }

    private fun say(message: Int) {
        runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    // ------------------------------------------------------------------ the tones' volume

    /** The media volume before listening, and what it was turned down to (-1: untouched). */
    private var volumeBefore = -1
    private var volumeQuiet = -1

    private fun quieten() {
        main.removeCallbacksAndMessages(null)
        if (volumeBefore >= 0) return
        runCatching {
            val audio = context.getSystemService(AudioManager::class.java) ?: return
            val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val quiet = if (now <= 1) now else maxOf(1, now / 3)
            if (quiet == now) return
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, quiet, 0)
            volumeBefore = now
            volumeQuiet = quiet
        }
    }

    private fun restoreVolume() {
        if (volumeBefore < 0 || recognizer != null) return
        runCatching {
            val audio = context.getSystemService(AudioManager::class.java) ?: return@runCatching
            // put back only if it wasn't changed meanwhile (the person's own choice stays)
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == volumeQuiet) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBefore, 0)
            }
        }
        volumeBefore = -1
        volumeQuiet = -1
    }

    // ------------------------------------------------------------------ recognition

    /** Google's recogniser, preferred over whatever the phone has set as default. */
    private fun googleService(): ComponentName? {
        val pm = context.packageManager
        val installed = pm.queryIntentServices(Intent(RECOGNITION_SERVICE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
        return GOOGLE_SERVICES.firstOrNull { it in installed }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) = onLevel(rmsdB.coerceIn(0f, 10f) / 10f)
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() = onLevel(0f)
        override fun onError(error: Int) {
            Log.w(TAG, "recogniser error $error")
            when (error) {
                // nothing heard: just stop
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> finish(null)
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    cancel()
                    say(R.string.lychee_voice_no_network)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    cancel()
                    say(R.string.lychee_voice_no_permission)
                }
                else -> {
                    app.lychee.DiagLog.log("voice typing error $error")
                    cancel()
                    say(R.string.lychee_voice_error)
                }
            }
        }
        override fun onResults(results: Bundle?) =
            finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onDetachedFromWindow() {
        cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val TAG = "LycheeVoice"
        private const val RECOGNITION_SERVICE = "android.speech.RecognitionService"
        private val GOOGLE_SERVICES = listOf(
            ComponentName("com.google.android.tts",
                "com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"),
            ComponentName("com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"),
            ComponentName("com.google.android.as",
                "com.google.android.apps.miphone.aiai.app.AiAiSpeechRecognitionService"),
        )
    }
}
