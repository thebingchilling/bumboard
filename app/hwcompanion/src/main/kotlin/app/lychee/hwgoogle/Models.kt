package app.lychee.hwgoogle

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier

/**
 * The handwriting models Lychee's keyboards can use: Chinese (Simplified as written in mainland
 * China, Traditional as written in Hong Kong and Taiwan), Japanese and Korean. ML Kit downloads
 * them from Google; they are then read on the phone.
 *
 * Only the languages of the person's keyboards are kept on the phone: Lychee says which those
 * are each time it binds the service ([setUsed]: a Cantonese keyboard uses Hong Kong's and the
 * mainland's, Mandarin the mainland's and Taiwan's, as Chinese reads both scripts), and only
 * those are fetched by themselves. The others can still be downloaded, and any can be removed,
 * on the app's screen.
 */
object Models {
    val TAGS = listOf("zh-Hani-CN", "zh-Hani-HK", "zh-Hani-TW", "ja", "ko")

    /** About how big each model is (ML Kit doesn't say), for the screen. */
    const val SIZE_MB = 20

    fun model(tag: String): DigitalInkRecognitionModel? =
        runCatching { DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag) }.getOrNull()
            ?.let { DigitalInkRecognitionModel.builder(it).build() }

    val manager: RemoteModelManager get() = RemoteModelManager.getInstance()

    private fun prefs(context: Context) = context.getSharedPreferences("lychee_handwriting", Context.MODE_PRIVATE)

    /** The models Lychee's keyboards use, as Lychee last said (none until it has). */
    fun used(context: Context): Set<String> = prefs(context).getStringSet(PREF_USED, emptySet()).orEmpty()

    /** Lychee's keyboards use [tags]: those come by themselves (the rest are left as they are). */
    fun setUsed(context: Context, tags: Collection<String>) {
        val known = tags.filter { it in TAGS }.toSet()
        prefs(context).edit().putStringSet(PREF_USED, known).apply()
        fetchMissing(context)
    }

    /** The tags being downloaded now (each is fetched once at a time). */
    private val fetching = java.util.Collections.synchronizedSet(HashSet<String>())

    fun isFetching(tag: String) = tag in fetching

    /**
     * Downloads [tag]'s model, or checks for a newer one when it is there already (ML Kit fetches
     * Google's newest model for this version of it); [done] gets whether it is ready.
     */
    fun fetch(tag: String, done: (Boolean) -> Unit = {}) {
        val m = model(tag) ?: return done(false)
        fetching += tag
        // never a crash: ML Kit not starting is a failed download, tried again next time
        runCatching {
            manager.download(m, DownloadConditions.Builder().build())
                .addOnSuccessListener { fetching -= tag; done(true) }
                .addOnFailureListener { fetching -= tag; done(false) }
        }.onFailure { fetching -= tag; done(false) }
    }

    /** Removes [tag]'s model from the phone; [done] gets whether it went. */
    fun remove(tag: String, done: (Boolean) -> Unit = {}) {
        val m = model(tag) ?: return done(false)
        runCatching {
            manager.deleteDownloadedModel(m)
                .addOnSuccessListener { done(true) }
                .addOnFailureListener { done(false) }
        }.onFailure { done(false) }
    }

    /** Fetches each model the keyboards use that isn't here yet (in the background). */
    fun fetchMissing(context: Context) {
        used(context).forEach { tag ->
            val m = model(tag) ?: return@forEach
            if (tag in fetching) return@forEach
            runCatching {
                manager.isModelDownloaded(m).addOnSuccessListener { have -> if (!have && tag !in fetching) fetch(tag) }
            }
        }
    }

    /** [done] gets the tags whose models are downloaded. */
    fun ready(done: (List<String>) -> Unit) {
        val results = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val all = TAGS.mapNotNull { t -> model(t)?.let { t to it } }
        if (all.isEmpty()) return done(emptyList())
        all.forEach { (tag, m) ->
            runCatching {
                manager.isModelDownloaded(m).addOnCompleteListener { task ->
                    results[tag] = task.isSuccessful && task.result == true
                    if (results.size == all.size) done(TAGS.filter { results[it] == true })
                }
            }.onFailure {
                results[tag] = false
                if (results.size == all.size) done(TAGS.filter { results[it] == true })
            }
        }
    }

    private const val PREF_USED = "used_models"
}
