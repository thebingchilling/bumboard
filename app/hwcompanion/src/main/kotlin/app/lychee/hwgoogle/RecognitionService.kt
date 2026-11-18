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
 *   RECOGNISE  data: "lang" (a Models.TAGS tag), "count", "s0".."sN" (each stroke's x, y pairs),
 *              "width", "height" (the pad), "before" (the text before the cursor)
 *              reply RESULT  data: "texts" (best first), or "notReady" while the model is
 *              still downloading (Lychee's own engine reads the pad meanwhile)
 *   STATUS     reply STATUS_RESULT  data: "ready" (the tags whose models are downloaded)
 *
 * Recognition runs on the phone, with ML Kit's downloaded models; nothing written leaves it.
 */
class RecognitionService : Service() {
    private val thread = HandlerThread("lychee-hw").apply { start() }
    private val recognizers = HashMap<String, DigitalInkRecognizer>()

    private val messenger = Messenger(object : Handler(thread.looper) {
        override fun handleMessage(msg: Message) {
            val reply = msg.replyTo ?: return
            when (msg.what) {
                STATUS -> Models.ready { tags ->
                    send(reply, STATUS_RESULT, msg.arg1, Bundle().apply { putStringArrayList("ready", ArrayList(tags)) })
                }
                RECOGNISE -> recognise(msg.data, reply, msg.arg1)
            }
        }
    })

    override fun onCreate() {
        super.onCreate()
        // the models come by themselves: nothing to open after installing
        Models.fetchMissing()
    }

    private fun recognise(data: Bundle, reply: Messenger, id: Int) {
        val lang = data.getString("lang") ?: return send(reply, RESULT, id, Bundle())
        val model = Models.model(lang) ?: return send(reply, RESULT, id, Bundle())
        Models.manager.isModelDownloaded(model).addOnCompleteListener { task ->
            if (task.isSuccessful && task.result == true) {
                read(lang, data, reply, id)
            } else {
                send(reply, RESULT, id, Bundle().apply { putBoolean("notReady", true) })
                Models.fetchMissing()
            }
        }
    }

    private fun read(lang: String, data: Bundle, reply: Messenger, id: Int) {
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
        recognizer.recognize(ink.build(), context)
            .addOnSuccessListener { result ->
                send(reply, RESULT, id, Bundle().apply {
                    putStringArrayList("texts", ArrayList(result.candidates.map { it.text }))
                })
            }
            .addOnFailureListener { send(reply, RESULT, id, Bundle()) }
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
    }
}
