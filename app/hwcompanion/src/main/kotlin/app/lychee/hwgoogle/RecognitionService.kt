package app.lychee.hwgoogle

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea

/**
 * What Lychee binds to read its handwriting pad (only apps with Lychee's signature may: the
 * manifest's permission). Messages, the same as Lychee's GoogleHandwriting:
 *
 *   RECOGNISE  data: "lang" (a Models.TAGS tag), optionally "also" (a second tag, read alongside
 *              and merged in: Chinese reads both scripts), "count", "s0".."sN" (each stroke's x, y pairs),
 *              "width", "height" (the pad), "before" (the text before the cursor)
 *              reply RESULT  data: "texts" (best first), or "notReady" while the model is
 *              still downloading (Lychee's own engine reads the pad meanwhile)
 *   STATUS     reply STATUS_RESULT  data: "ready" (the tags whose models are downloaded)
 *   USE        data: "tags" (the models Lychee's keyboards use): only those come by themselves
 *
 * Recognition runs on the phone, with ML Kit's downloaded models; nothing written leaves it.
 */
class RecognitionService : Service() {
    private val thread = HandlerThread("lychee-hw").apply { start() }
    private val recognizers = HashMap<String, DigitalInkRecognizer>()

    private val messenger = Messenger(object : Handler(thread.looper) {
        override fun handleMessage(msg: Message) {
            if (msg.what == USE) {
                runCatching { Models.setUsed(this@RecognitionService, msg.data.getStringArrayList("tags").orEmpty()) }
                return
            }
            val reply = msg.replyTo ?: return
            when (msg.what) {
                STATUS -> Models.ready { tags ->
                    send(reply, STATUS_RESULT, msg.arg1, Bundle().apply { putStringArrayList("ready", ArrayList(tags)) })
                }
                // a failure is an empty answer (Lychee's own engine then reads the pad), never a crash
                RECOGNISE -> runCatching { recognise(msg.data, reply, msg.arg1) }
                    .onFailure { send(reply, RESULT, msg.arg1, Bundle()) }
            }
        }
    })

    override fun onCreate() {
        super.onCreate()
        // the models Lychee's keyboards use come by themselves: nothing to open after installing
        runCatching { Models.fetchMissing(this) }
        // the launcher icon comes on with the first use (its entry is off in the manifest)
        Icon.apply(this)
    }

    private fun recognise(data: Bundle, reply: Messenger, id: Int) {
        val lang = data.getString("lang") ?: return send(reply, RESULT, id, Bundle())
        val model = Models.model(lang) ?: return send(reply, RESULT, id, Bundle())
        Models.manager.isModelDownloaded(model).addOnCompleteListener { task ->
            if (task.isSuccessful && task.result == true) {
                // the second model only once it is downloaded (it comes by itself meanwhile)
                val alsoModel = data.getString("also")?.takeIf { it != lang }?.let { Models.model(it) }
                if (alsoModel == null) {
                    runCatching { read(lang, false, data, reply, id) }.onFailure { send(reply, RESULT, id, Bundle()) }
                } else {
                    Models.manager.isModelDownloaded(alsoModel).addOnCompleteListener { t ->
                        val alsoReady = t.isSuccessful && t.result == true
                        runCatching { read(lang, alsoReady, data, reply, id) }.onFailure { send(reply, RESULT, id, Bundle()) }
                    }
                }
            } else {
                send(reply, RESULT, id, Bundle().apply { putBoolean("notReady", true) })
                // the language being written is wanted whatever was said before
                if (!Models.isFetching(lang)) Models.fetch(lang)
            }
        }
    }

    private fun read(lang: String, alsoReady: Boolean, data: Bundle, reply: Messenger, id: Int) {
        val recognizer = recognizers.getOrPut(lang) {
            val model = Models.model(lang) ?: return send(reply, RESULT, id, Bundle())
            DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
        }
        val ink = Ink.builder()
        for (i in 0 until data.getInt("count")) {
            val xy = data.getFloatArray("s$i") ?: continue
            val stroke = Ink.Stroke.builder()
            var k = 0
            while (k + 1 < xy.size) {
                stroke.addPoint(Ink.Point.create(xy[k], xy[k + 1]))
                k += 2
            }
            ink.addStroke(stroke.build())
        }
        val context = RecognitionContext.builder()
            .setPreContext(data.getString("before").orEmpty())
            .setWritingArea(WritingArea(data.getFloat("width", 1f), data.getFloat("height", 1f)))
            .build()
        val built = ink.build()
        // a second model (Chinese: the other script's), when it is downloaded, read at the same
        // time; the guesses alternate, the asked model's first, without repeats
        val alsoTag = data.getString("also")?.takeIf { alsoReady && it != lang }
        val alsoRecognizer = alsoTag?.let { tag ->
            recognizers[tag] ?: Models.model(tag)?.let { m ->
                DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build()).also { recognizers[tag] = it }
            }
        }
        val first = recognizer.recognize(built, context)
        val second = alsoRecognizer?.recognize(built, context)
        val all = listOfNotNull(first, second)
        com.google.android.gms.tasks.Tasks.whenAllComplete(all).addOnCompleteListener {
            val a = if (first.isSuccessful) first.result.candidates.map { it.text } else emptyList()
            val b = if (second != null && second.isSuccessful) second.result.candidates.map { it.text } else emptyList()
            if (!first.isSuccessful && b.isEmpty()) return@addOnCompleteListener send(reply, RESULT, id, Bundle())
            val merged = LinkedHashSet<String>()
            for (i in 0 until maxOf(a.size, b.size)) {
                a.getOrNull(i)?.let { merged += it }
                b.getOrNull(i)?.let { merged += it }
            }
            send(reply, RESULT, id, Bundle().apply { putStringArrayList("texts", ArrayList(merged)) })
        }
    }

    private fun send(to: Messenger, what: Int, id: Int, data: Bundle) {
        runCatching { to.send(Message.obtain(null, what, id, 0).apply { this.data = data }) }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        recognizers.values.forEach { runCatching { it.close() } }
        thread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val RECOGNISE = 1
        const val RESULT = 2
        const val STATUS = 3
        const val STATUS_RESULT = 4
        const val USE = 5
    }
}
