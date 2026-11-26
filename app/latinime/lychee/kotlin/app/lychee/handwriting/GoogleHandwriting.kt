package app.lychee.handwriting

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Google's handwriting recognition (ML Kit), through Lychee's companion app: ML Kit downloads
 * its models, so it needs the internet, and its licence has it run in its own app; Lychee stays
 * offline and hands the companion the strokes on its pad (a bound service only apps signed like
 * Lychee may use), getting its guesses back. Recognition runs on the phone.
 *
 * The higher-quality choice; without the companion, Lychee's own recogniser reads the pad.
 */
class GoogleHandwriting private constructor(
    private val context: Context,
    private val lang: String,
    /** A second model read alongside (Chinese: the other script's), its guesses merged in. */
    private val also: String?,
    private val before: () -> String,
    /** Reads the pad while the companion is still downloading this language's model. */
    private val fallback: () -> Recognizer?,
) : Recognizer {

    private var backup: Recognizer? = null

    private val thread = HandlerThread("lychee-google-hw").apply { start() }
    private val replies = ArrayBlockingQueue<Pair<Int, Bundle>>(4)
    private val replyTo = Messenger(Handler(thread.looper) { msg ->
        replies.offer(msg.arg1 to msg.data)
        true
    })
    @Volatile private var service: Messenger? = null
    private val ids = AtomicInteger()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let { Messenger(it) }
            // which models Lychee's keyboards use: only those are kept on the phone
            val tags = usedModels(context)
            runCatching {
                service?.send(Message.obtain(null, USE).apply { data = Bundle().apply { putStringArrayList("tags", ArrayList(tags)) } })
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    init {
        runCatching {
            context.bindService(Intent().setComponent(SERVICE), connection, Context.BIND_AUTO_CREATE)
        }
    }

    /** The pad's size, for ML Kit's sense of how big the writing is. */
    var padWidth = 1f
    var padHeight = 1f

    override fun recognise(strokes: List<FloatArray>, limit: Int, allowed: (String) -> Boolean): List<Pair<String, Float>> {
        // the companion takes a moment to connect when the pad opens
        var s = service
        var waited = 0
        while (s == null && waited < 1500) {
            Thread.sleep(50)
            waited += 50
            s = service
        }
        s ?: return emptyList()
        val id = ids.incrementAndGet()
        val data = Bundle().apply {
            putString("lang", lang)
            also?.let { putString("also", it) }
            putInt("count", strokes.size)
            strokes.forEachIndexed { i, st -> putFloatArray("s$i", st) }
            putFloat("width", padWidth)
            putFloat("height", padHeight)
            putString("before", before())
        }
        replies.clear()
        runCatching { s.send(Message.obtain(null, RECOGNISE, id, 0).apply { this.data = data; replyTo = this@GoogleHandwriting.replyTo }) }
            .onFailure { return emptyList() }
        // its answer, or nothing after a while (the pad is never held up for long)
        while (true) {
            val (rid, result) = replies.poll(2, TimeUnit.SECONDS) ?: return emptyList()
            if (rid != id) continue
            if (result.getBoolean("notReady")) {
                // the model is still downloading (it comes by itself): Lychee's own engine meanwhile
                val b = backup ?: fallback()?.also { backup = it }
                return b?.recognise(strokes, limit, allowed).orEmpty()
            }
            val texts = result.getStringArrayList("texts").orEmpty()
            // ML Kit gives an order, not scores: confident at the top, less so further down
            return texts.filter(allowed).take(limit).mapIndexed { i, t -> t to (1f - i * 0.08f).coerceAtLeast(0.1f) }
        }
    }

    override fun close() {
        backup?.close()
        runCatching { context.unbindService(connection) }
        thread.quitSafely()
    }

    companion object {
        const val PACKAGE = "app.lychee.hwgoogle"
        val SERVICE = ComponentName(PACKAGE, "$PACKAGE.RecognitionService")
        private const val RECOGNISE = 1
        private const val STATUS = 3
        private const val USE = 5

        /**
         * The ML Kit models the enabled keyboards use: Cantonese reads Hong Kong's and the
         * mainland's, Mandarin the mainland's and Taiwan's (Chinese reads both scripts),
         * Japanese and Korean their own. English keyboards use none.
         */
        fun usedModels(context: Context): List<String> {
            val engines = runCatching {
                val imm = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                val me = imm.enabledInputMethodList.firstOrNull { it.packageName == context.packageName } ?: return emptyList()
                imm.getEnabledInputMethodSubtypeList(me, true)
                    .mapNotNull { it.getExtraValueOf(app.lychee.chinese.ChineseInput.EXTRA_ENGINE) }
            }.getOrDefault(emptyList())
            return engines.flatMap { e -> listOfNotNull(languageFor(e, true), languageFor(e, false), alsoFor(e, true), alsoFor(e, false)) }.distinct()
        }

        /**
         * Where the companion is downloaded (a release of Lychee's: the browser saves it, then
         * opening the file installs it). Its size, to show before downloading.
         */
        const val DOWNLOAD = "https://github.com/thebingchilling/bumboard/releases/download/handwriting-google/lychee-handwriting.apk"

        fun installed(context: Context): Boolean =
            runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

        /** The ML Kit model for the keyboard [engine] ("yue", "cmn", "ja", "ko"). */
        fun languageFor(engine: String?, simplified: Boolean): String = when (engine) {
            "ja" -> "ja"
            "ko" -> "ko"
            "yue" -> if (simplified) "zh-Hani-CN" else "zh-Hani-HK"
            else -> if (simplified) "zh-Hani-CN" else "zh-Hani-TW"
        }

        /**
         * Chinese reads both scripts' models, so every character comes up whichever script the
         * candidates are in: this is the other one (Traditional's for Simplified and back).
         */
        fun alsoFor(engine: String?, simplified: Boolean): String? = when (engine) {
            "ja", "ko" -> null
            "yue" -> if (simplified) "zh-Hani-HK" else "zh-Hani-CN"
            else -> if (simplified) "zh-Hani-TW" else "zh-Hani-CN"
        }

        /** Opens the companion when it is installed; null when it isn't. */
        fun open(context: Context, engine: String?, simplified: Boolean, before: () -> String,
                 fallback: () -> Recognizer? = { null }): GoogleHandwriting? =
            if (installed(context)) GoogleHandwriting(context.applicationContext, languageFor(engine, simplified),
                alsoFor(engine, simplified), before, fallback) else null

        /** The companion's setup screen (downloads the models; also checks for newer ones). */
        fun openSetup(context: Context) {
            runCatching {
                context.startActivity(Intent("$PACKAGE.SETUP").setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        /** The phone's page for the companion, where it is uninstalled. */
        fun openAppInfo(context: Context) {
            runCatching {
                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$PACKAGE"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        /** Downloads the companion in the browser. */
        fun download(context: Context) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
