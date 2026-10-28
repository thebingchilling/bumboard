package app.bumboard.rime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.bumboard.packs.PackKind
import app.bumboard.packs.Packs
import java.io.File
import java.util.concurrent.Executors

/**
 * Starts Rime once per process. The schemas and precompiled dictionaries ship as APK assets
 * under rime/ and are copied to files/rime/shared whenever the APK's copy changes.
 */
object RimeService {
    private const val TAG = "BumRime"
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
     * precompiled build/) into Rime's shared data, then runs [then] on the main thread.
     */
    fun refreshPacks(context: Context, then: () -> Unit = {}) {
        val app = context.applicationContext
        executor.execute {
            try {
                installPacks(app, File(File(app.filesDir, "rime"), "shared"))
            } catch (t: Throwable) {
                Log.e(TAG, "Rime packs failed to install", t)
            }
            main.post(then)
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
