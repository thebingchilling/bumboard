package app.bumboard.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * Voice typing over the keys. Speech goes to Google's recognition service when the phone has
 * it (Speech Services by Google, then the Google app), otherwise to the system default. The
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

    val isActive: Boolean get() = visibility == VISIBLE

    init {
        setBackgroundColor(palette.surface)
        isClickable = true // keep touches off the keys underneath
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        heard.apply {
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            maxLines = 3
            setPadding(dp(24), 0, dp(24), dp(16))
        }
        status.apply {
            setTextColor(palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
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
            contentDescription = context.getString(R.string.bum_voice_stop)
            setOnClickListener { recognizer?.stopListening() }
        }
        micBox.addView(micHalo, LayoutParams(dp(72), dp(72), Gravity.CENTER))
        micBox.addView(mic, LayoutParams(dp(64), dp(64), Gravity.CENTER))
        column.addView(heard, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(micBox, LinearLayout.LayoutParams(dp(96), dp(96)))
        column.addView(status)
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(app.bumboard.layout.BumPanels.backButton(context) { cancel() },
            LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.START))
        visibility = GONE
    }

    /**
     * Starts listening in [language] (a BCP 47 tag, or null for the recogniser's default).
     * Returns false when voice typing cannot start here (no recogniser, or the microphone
     * permission still has to be granted, in which case the permission screen is opened).
     */
    fun start(language: String?): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            context.startActivity(Intent(context, MicPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "no speech recogniser on this phone")
            return false
        }
        cancel(notify = false)
        val r = googleService()?.let { SpeechRecognizer.createSpeechRecognizer(context, it) }
            ?: SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (language != null) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        heard.text = ""
        status.setText(R.string.bum_voice_starting)
        visibility = VISIBLE
        r.startListening(intent)
        return true
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
        override fun onReadyForSpeech(params: Bundle?) = status.setText(R.string.bum_voice_listening)
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            val scale = 1f + (rmsdB.coerceIn(0f, 10f) / 10f) * 0.35f
            micHalo.scaleX = scale
            micHalo.scaleY = scale
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() = status.setText(R.string.bum_voice_working)
        override fun onError(error: Int) {
            Log.w(TAG, "recogniser error $error")
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                finish(null)
            } else {
                status.setText(R.string.bum_voice_error)
                recognizer?.destroy()
                recognizer = null
                postDelayed({ if (recognizer == null) cancel() }, 1500)
            }
        }
        override fun onResults(results: Bundle?) = finish(best(results))
        override fun onPartialResults(partialResults: Bundle?) {
            best(partialResults)?.let { heard.text = it }
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
        status.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private companion object {
        const val TAG = "BumVoice"
        const val RECOGNITION_SERVICE = "android.speech.RecognitionService"
        val GOOGLE_SERVICES = listOf(
            ComponentName("com.google.android.tts",
                "com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"),
            ComponentName("com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"),
            ComponentName("com.google.android.as",
                "com.google.android.apps.miphone.aiai.app.AiAiSpeechRecognitionService"),
        )
    }
}
