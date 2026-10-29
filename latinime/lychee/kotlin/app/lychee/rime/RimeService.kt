package app.lychee.rime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.lychee.packs.BundledPacks
import app.lychee.packs.PackKind
import app.lychee.packs.Packs
import java.io.File
import java.util.concurrent.Executors

/**
 * Starts Rime once per process. The schemas and precompiled dictionaries ship as APK assets
 * under rime/ and are copied to files/rime/shared whenever the APK's copy changes.
 */
object RimeService {
    private const val TAG = "LycheeRime"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val waiting = mutableListOf<() -> Unit>()

    @Volatile
    var ready = false
        private set
    private var starting = false

    /** Runs [then] on the main thread once Rime is ready (immediately if it already is). */
    fun start(context: Context, then: () -> Unit = {}) {
        if (ready) {
            then()
            return
        }
        waiting += then
        if (starting) return
        starting = true
        val app = context.applicationContext
        executor.execute {
            try {
                val root = File(app.filesDir, "rime")
                val shared = File(root, "shared")
                val user = File(root, "user").apply { mkdirs() }
                val changed = installAssets(app, shared)
                installPacks(app, shared)
                Rime.start(shared.path, user.path, fullCheck = changed)
                stagePackBuilds(app, user)
                ready = true
            } catch (t: Throwable) {
                Log.e(TAG, "Rime failed to start", t)
            }
            main.post {
                starting = false
                if (ready) {
                    waiting.forEach { it() }
                    waiting.clear()
                }
            }
        }
    }

    /**
     * Copies imported Rime packs (e.g. the Japanese dictionary: schema, dictionary and its
     * precompiled build/) into Rime's shared data, then runs [then] on the main thread with
     * whether anything changed.
     */
    fun refreshPacks(context: Context, engine: String? = null, then: (Boolean) -> Unit = {}) {
        val app = context.applicationContext
        executor.execute {
            var changed = false
            try {
                // the bundled packs this keyboard needs first (waits for an unpacking under way)
                BundledPacks.installAll(app, engine)
                val root = File(app.filesDir, "rime")
                changed = installPacks(app, File(root, "shared"))
                stagePackBuilds(app, File(root, "user"))
            } catch (t: Throwable) {
                Log.e(TAG, "Rime packs failed to install", t)
            }
            main.post { then(changed) }
        }
    }

    /**
     * Rime finds compiled dictionaries in the user folder's build/ (where its maintenance puts
     * the schemas in default.yaml's list); a pack's schema isn't in that list, so its compiled
     * files are copied there too, or the schema loads without a dictionary (letters typed,
     * nothing found).
     */
    private fun stagePackBuilds(context: Context, user: File) {
        val staging = File(user, "build").apply { mkdirs() }
        for (pack in Packs.ofKind(context, PackKind.RIME)) {
            if (!Packs.isInstalled(context, pack.id)) continue
            val build = File(File(Packs.dir(context, pack.id), "rime"), "build")
            build.listFiles()?.forEach { f ->
                val dest = File(staging, f.name)
                if (!dest.exists() || dest.length() != f.length() || dest.lastModified() < f.lastModified()) {
                    f.copyTo(dest, overwrite = true)
                }
            }
        }
    }

    /** Copies the installed Rime packs into [shared] when they changed. Returns whether they did. */
    private fun installPacks(context: Context, shared: File): Boolean {
        val packs = Packs.ofKind(context, PackKind.RIME)
            .filter { Packs.isInstalled(context, it.id) }
        val stamp = packs.joinToString(",") {
            val dir = Packs.dir(context, it.id)
            "${it.id}:${Packs.installedVersion(context, it.id)}:${dir.lastModified()}"
        }
        val stampFile = File(shared, "PACKS_STAMP")
        if (stampFile.exists() && stampFile.readText() == stamp) return false
        shared.mkdirs()
        for (pack in packs) {
            val rime = File(Packs.dir(context, pack.id), "rime")
            if (rime.isDirectory) rime.copyRecursively(shared, overwrite = true)
        }
        stampFile.writeText(stamp)
        Log.i(TAG, "Installed Rime packs: $stamp")
        return true
    }

    /** Copies assets/rime into [dest] when its stamp differs. Returns whether anything was copied. */
    private fun installAssets(context: Context, dest: File): Boolean {
        val assets = context.assets
        val stamp = assets.open("rime/STAMP").bufferedReader().use { it.readText().trim() }
        val stampFile = File(dest, "STAMP")
        if (stampFile.exists() && stampFile.readText().trim() == stamp) return false
        dest.deleteRecursively()
        fun copy(path: String, to: File) {
            val children = assets.list(path).orEmpty()
            if (children.isEmpty()) {
                to.parentFile?.mkdirs()
                assets.open(path).use { input -> to.outputStream().use { input.copyTo(it) } }
            } else {
                to.mkdirs()
                children.forEach { copy("$path/$it", File(to, it)) }
            }
        }
        copy("rime", dest)
        Log.i(TAG, "Installed Rime data $stamp")
        return true
    }
}
