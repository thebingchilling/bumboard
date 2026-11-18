package app.lychee.hwgoogle

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier

/**
 * The handwriting models Lychee's keyboards use: Chinese (Simplified as written in mainland
 * China, Traditional as written in Hong Kong and Taiwan), Japanese and Korean. ML Kit downloads
 * them from Google; they are then read on the phone.
 */
object Models {
    val TAGS = listOf("zh-Hani-CN", "zh-Hani-HK", "zh-Hani-TW", "ja", "ko")

    fun model(tag: String): DigitalInkRecognitionModel? =
        runCatching { DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag) }.getOrNull()
            ?.let { DigitalInkRecognitionModel.builder(it).build() }

    val manager: RemoteModelManager get() = RemoteModelManager.getInstance()

    /** The tags being downloaded now (each is fetched once at a time). */
    private val fetching = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Downloads [tag]'s model, or checks for a newer one when it is there already (ML Kit fetches
     * Google's newest model for this version of it); [done] gets whether it is ready.
     */
    fun fetch(tag: String, done: (Boolean) -> Unit = {}) {
        val m = model(tag) ?: return done(false)
        fetching += tag
        manager.download(m, DownloadConditions.Builder().build())
            .addOnSuccessListener { fetching -= tag; done(true) }
            .addOnFailureListener { fetching -= tag; done(false) }
    }

    /** Fetches every model not downloaded yet (in the background; nothing to open or tap). */
    fun fetchMissing() {
        TAGS.forEach { tag ->
            val m = model(tag) ?: return@forEach
            if (tag in fetching) return@forEach
            manager.isModelDownloaded(m).addOnSuccessListener { have -> if (!have && tag !in fetching) fetch(tag) }
        }
    }

    /** [done] gets the tags whose models are downloaded. */
    fun ready(done: (List<String>) -> Unit) {
        val results = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val all = TAGS.mapNotNull { t -> model(t)?.let { t to it } }
        if (all.isEmpty()) return done(emptyList())
        all.forEach { (tag, m) ->
            manager.isModelDownloaded(m).addOnCompleteListener { task ->
                results[tag] = task.isSuccessful && task.result == true
                if (results.size == all.size) done(TAGS.filter { results[it] == true })
            }
        }
    }
}
