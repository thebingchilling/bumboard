package app.lychee.packs

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * The packs that come with Lychee (handwriting, the Japanese and Korean dictionaries, stroke
 * order, example sentences): the APK carries their zips (assets/lychee/bundled, put there by
 * the build from the published packs) and they are unpacked into the packs folder the first
 * time, and again when an update brings newer ones. Only fonts and emoji are downloads.
 */
object BundledPacks {
    private const val TAG = "LycheeBundled"
    private const val DIR = "lychee/bundled"
    private val lock = Any()
    @Volatile
    private var done = false

    /** Pack id -> version of the packs in this APK. */
    fun versions(context: Context): Map<String, String> = runCatching {
        val json = context.assets.open("$DIR/bundled.json").bufferedReader().use { JSONObject(it.readText()) }
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    /** Whether [id] comes with Lychee (so it is never a download). */
    fun isBundled(context: Context, id: String) = id in versions(context)

    /** Unpacks every bundled pack that is missing or older; blocking (call off the main thread). */
    @JvmStatic
    fun installAll(context: Context) {
        if (done) return
        synchronized(lock) {
            if (done) return
            val app = context.applicationContext
            for ((id, version) in versions(app)) {
                if (Packs.installedVersion(app, id) == version) continue
                runCatching { install(app, id, version) }
                    .onFailure { Log.w(TAG, "couldn't unpack $id", it) }
            }
            done = true
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
