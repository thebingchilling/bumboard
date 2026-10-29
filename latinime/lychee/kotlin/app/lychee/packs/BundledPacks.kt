package app.lychee.packs

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * The packs that come with Lychee (handwriting, the Japanese and Korean dictionaries, stroke
 * order, example sentences): the APK carries their zips (assets/lychee/bundled, put there by
 * the build from the published packs) and those the enabled keyboards need are unpacked into
 * the packs folder, again when an update brings newer ones; the rest stay zipped (and are
 * removed when a keyboard is turned off). Only fonts and emoji are downloads.
 */
object BundledPacks {
    private const val TAG = "LycheeBundled"
    private const val DIR = "lychee/bundled"
    private val lock = Any()

    /** Pack id -> version of the packs in this APK. */
    fun versions(context: Context): Map<String, String> = runCatching {
        val json = context.assets.open("$DIR/bundled.json").bufferedReader().use { JSONObject(it.readText()) }
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    /** Whether [id] comes with Lychee (so it is never a download). */
    fun isBundled(context: Context, id: String) = id in versions(context)

    /** Which keyboards ("yue", "cmn", "ja", "ko") use each bundled pack. */
    private val USED_BY = mapOf(
        "hw_zh" to setOf("yue", "cmn"),
        "hw_ja" to setOf("ja"),
        "rime_ja" to setOf("ja"),
        "dict_ko" to setOf("ko"),
        "strokes_zh" to setOf("yue", "cmn", "ja"),
        "examples" to setOf("yue", "cmn", "ja", "ko"),
    )

    /** The engines of Lychee's enabled keyboards, or null when they can't be read. */
    fun enabledEngines(context: Context): Set<String>? = runCatching {
        val imm = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java) ?: return null
        val imi = imm.enabledInputMethodList.firstOrNull { it.packageName == context.packageName } ?: return null
        imm.getEnabledInputMethodSubtypeList(imi, true).mapNotNull { it.getExtraValueOf("LycheeEngine") }.toSet()
    }.getOrNull()

    /**
     * Unpacks the bundled packs the enabled keyboards (and [engine], the one in use) need, when
     * missing or older, and removes those no enabled keyboard needs any more, so a phone with
     * only Cantonese never holds the Japanese dictionary. Blocking: call off the main thread.
     */
    @JvmStatic
    @JvmOverloads
    fun installAll(context: Context, engine: String? = null) {
        synchronized(lock) {
            val app = context.applicationContext
            val enabled = enabledEngines(app)
            val engines = enabled.orEmpty() + listOfNotNull(engine)
            for ((id, version) in versions(app)) {
                val needed = USED_BY[id]?.any { it in engines } ?: true
                if (!needed) {
                    // only when the enabled keyboards are known for sure
                    if (enabled != null && Packs.isInstalled(app, id)) Packs.remove(app, id)
                    continue
                }
                if (Packs.installedVersion(app, id) == version) continue
                runCatching { install(app, id, version) }
                    .onFailure { Log.w(TAG, "couldn't unpack $id", it) }
            }
        }
    }

    private fun install(context: Context, id: String, version: String) {
        val staging = File(context.cacheDir, "bundled-$id").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(context.assets.open("$DIR/$id.zip").buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val out = File(staging, entry.name)
                    // never outside the folder
                    if (!out.canonicalPath.startsWith(staging.canonicalPath + File.separator)) continue
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val dest = Packs.dir(context, id)
            dest.deleteRecursively()
            dest.parentFile?.mkdirs()
            if (!staging.renameTo(dest)) staging.copyRecursively(dest, overwrite = true)
            Packs.writeInfo(context, id, version, null)
            Log.i(TAG, "unpacked $id $version")
        } finally {
            staging.deleteRecursively()
        }
    }
}
