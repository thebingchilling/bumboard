package app.lychee.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * Voice typing in the bar above the keys, like Gboard's. With "offline" on (the default) and
 * Android 12+, speech is recognised on the phone by the system's on-device recogniser; otherwise
 * it goes to Google's recognition service when the phone has it (Speech Services by Google,
 * then the Google app), or to the system default. Lychee itself never uses the network. The
 * panel shows what is heard as it is heard and hands the final text to [onText]; nothing is
 * typed until the recogniser is done, so LatinIME's own composing state is never disturbed.
 */
class VoicePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** Final recognised text, ready to type. */
    var onText: (String) -> Unit = {}
    /** The panel closed (done, cancelled or failed). */
    var onClose: () -> Unit = {}

    private val palette = M3Colors.of(context)
    private var recognizer: SpeechRecognizer? = null
    private val mic = ImageView(context)
    private val micHalo = View(context)
    private val status = TextView(context)
    private val heard = TextView(context)
    /** Offline language missing: Download, or online this time. */
    private val choices = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
    }

    val isActive: Boolean get() = visibility == VISIBLE

    init {
        // a bar in place of the strip, like Gboard's: Back, what is heard, and the mic
        setBackgroundColor(palette.surface)
        isClickable = true
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(app.lychee.layout.LycheePanels.backButton(context) { cancel() },
            LinearLayout.LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
        val words = FrameLayout(context)
        status.apply {
            setTextColor(palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER_VERTICAL
        }
        heard.apply {
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.START
        }
        words.addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        words.addView(heard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        words.addView(choices, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END))
        row.addView(words, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(8) })
        val micBox = FrameLayout(context)
        micHalo.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.primaryContainer)
        }
        mic.apply {
            setImageResource(R.drawable.ic_sym_mic)
            imageTintList = ColorStateList.valueOf(palette.onPrimary)
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.primary)
            }
            contentDescription = context.getString(R.string.lychee_voice_stop)
            // tap the mic to stop (or to start again after a stop)
            setOnClickListener {
                val r = recognizer
                if (r != null) r.stopListening() else start(language)
            }
        }
        micBox.addView(micHalo, LayoutParams(dp(42), dp(42), Gravity.CENTER))
        micBox.addView(mic, LayoutParams(dp(40), dp(40), Gravity.CENTER))
        row.addView(micBox, LinearLayout.LayoutParams(dp(56), LayoutParams.MATCH_PARENT))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = GONE
    }

    /**
     * Starts listening in [language] (a BCP 47 tag, or null for the recogniser's default).
     * Returns false when voice typing cannot start here (no recogniser, or the microphone
     * permission still has to be granted, in which case the permission screen is opened).
     */
    fun start(language: String?): Boolean = start(language, online = false)

    private fun start(language: String?, online: Boolean): Boolean {
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
                return false
            }
            cancel(notify = false)
            this.language = language
            // on this phone when it can (Android 12+): nothing leaves the phone, and the speech
            // model runs in the system's own process, not Lychee's
            onDevice = !online && offlinePreferred() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            val r = if (onDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                googleService()?.let { SpeechRecognizer.createSpeechRecognizer(context, it) }
                    ?: SpeechRecognizer.createSpeechRecognizer(context)
            }
            recognizer = r
            r.setRecognitionListener(listener)
            heard.text = ""
            choices.visibility = GONE
            status.visibility = VISIBLE
            status.setText(if (onDevice) R.string.lychee_voice_starting_offline else R.string.lychee_voice_starting)
            visibility = VISIBLE
            r.startListening(request())
            true
        } catch (t: Throwable) {
            // never take the keyboard down with it
            Log.e(TAG, "voice typing failed to start", t)
            recognizer = null
            visibility = VISIBLE
            status.visibility = VISIBLE
            status.setText(R.string.lychee_voice_error)
            false
        }
    }

    private var language: String? = null
    /** Whether the recogniser in use runs on this phone. */
    private var onDevice = false

    private fun request() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offlinePreferred())
        .apply { language?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) } }

    @Suppress("DEPRECATION")
    private fun offlinePreferred() =
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_OFFLINE, true)

    /**
     * The on-device recogniser lacks this language. Nothing is downloaded unless asked: the
     * bar offers Download (the system's own download, with its progress here on Android 14+)
     * or online voice typing this once.
     */
    private fun offerDownload() {
        recognizer?.run { setRecognitionListener(null); cancel(); destroy() }
        recognizer = null
        heard.text = ""
        status.visibility = VISIBLE
        status.text = context.getString(R.string.lychee_voice_offline_missing, languageName())
        choices.removeAllViews()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            choices.addView(choice(R.string.lychee_voice_download) { download() })
        }
        choices.addView(choice(R.string.lychee_voice_online_once) { start(language, online = true) })
        choices.visibility = VISIBLE
    }

    private fun languageName(): String =
        language?.let { java.util.Locale.forLanguageTag(it).getDisplayName() } ?: ""

    private fun download() {
        choices.visibility = GONE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val r = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull() ?: return
        status.text = context.getString(R.string.lychee_voice_downloading, languageName(), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            r.triggerModelDownload(request(), context.mainExecutor, object : android.speech.ModelDownloadListener {
                override fun onProgress(completedPercent: Int) {
                    status.text = context.getString(R.string.lychee_voice_downloading, languageName(), completedPercent)
                }
                override fun onSuccess() {
                    status.text = context.getString(R.string.lychee_voice_downloaded, languageName())
                    r.destroy()
                }
                override fun onScheduled() {
                    status.text = context.getString(R.string.lychee_voice_download_scheduled, languageName())
                    r.destroy()
                }
                override fun onError(error: Int) {
                    status.setText(R.string.lychee_voice_download_failed)
                    r.destroy()
                }
            })
        } else {
            // Android 13: the system downloads it and shows its own notification
            runCatching { r.triggerModelDownload(request()) }
            status.text = context.getString(R.string.lychee_voice_download_scheduled, languageName())
            r.destroy()
        }
    }

    private fun choice(label: Int, onClick: () -> Unit) = TextView(context).apply {
        setText(label)
        setTextColor(palette.onPrimaryContainer)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(palette.primaryContainer)
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)).apply { marginStart = dp(6) }
        setOnClickListener { onClick() }
    }


    fun cancel() = cancel(notify = true)

    private fun cancel(notify: Boolean) {
        recognizer?.run {
            setRecognitionListener(null)
            cancel()
            destroy()
        }
        recognizer = null
        if (visibility != GONE) {
            visibility = GONE
            if (notify) onClose()
        }
    }

    private fun finish(text: String?) {
        cancel(notify = false)
        if (!text.isNullOrBlank()) onText(text)
        onClose()
    }

    /** Google's recogniser, preferred over whatever the phone has set as default. */
    private fun googleService(): ComponentName? {
        val pm = context.packageManager
        val installed = pm.queryIntentServices(Intent(RECOGNITION_SERVICE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
        return GOOGLE_SERVICES.firstOrNull { it in installed }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) =
            status.setText(if (onDevice) R.string.lychee_voice_listening_offline else R.string.lychee_voice_listening)
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            val scale = 1f + (rmsdB.coerceIn(0f, 10f) / 10f) * 0.45f
            micHalo.scaleX = scale
            micHalo.scaleY = scale
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() = status.setText(R.string.lychee_voice_working)
        override fun onError(error: Int) {
            Log.w(TAG, "recogniser error $error")
            if (onDevice && (error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE)) {
                offerDownload()
                return
            }
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                finish(null)
            } else {
                status.visibility = VISIBLE
                status.setText(R.string.lychee_voice_error)
                recognizer?.destroy()
                recognizer = null
            }
        }
        override fun onResults(results: Bundle?) = finish(best(results))
        override fun onPartialResults(partialResults: Bundle?) {
            best(partialResults)?.let {
                heard.text = it
                status.visibility = if (it.isEmpty()) VISIBLE else INVISIBLE
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun best(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    override fun onDetachedFromWindow() {
        cancel(notify = false)
        super.onDetachedFromWindow()
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    init {
        status.typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
    }

    companion object {
        /** Voice typing on this phone (offline) when the phone can. */
        const val PREF_OFFLINE = "lychee_voice_offline"
        private const val TAG = "LycheeVoice"
        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / _UNAVAILABLE (Android 12)
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
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
