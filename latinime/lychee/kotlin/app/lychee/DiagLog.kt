package app.lychee

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short log of what the keyboard's engines did (packs unpacked, Rime started, schemas
 * chosen, failures), kept on the phone in files/diagnostics.txt so Settings > About >
 * Diagnostics can show and share it without adb. The newest [KEEP] lines are kept; nothing
 * leaves the device unless the user shares it.
 */
object DiagLog {
    private const val FILE = "diagnostics.txt"
    private const val KEEP = 300
    private var file: File? = null
    private val lines = ArrayDeque<String>()
    private var loaded = false

    @JvmStatic
    fun init(context: Context) {
        if (file == null) file = File(context.applicationContext.filesDir, FILE)
    }

    @JvmStatic
    @Synchronized
    fun log(message: String) {
        Log.i("Lychee", message)
        val f = file ?: return
        if (!loaded) {
            runCatching { if (f.exists()) lines.addAll(f.readLines().takeLast(KEEP)) }
            loaded = true
        }
        lines.addLast("${SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())}  $message")
        while (lines.size > KEEP) lines.removeFirst()
        runCatching { f.writeText(lines.joinToString("\n", postfix = "\n")) }
    }

    @JvmStatic
    fun log(message: String, error: Throwable) = log("$message: ${error.javaClass.simpleName}: ${error.message}")

    fun read(context: Context): String =
        runCatching { File(context.applicationContext.filesDir, FILE).readText() }.getOrDefault("")

    @Synchronized
    fun clear(context: Context) {
        lines.clear()
        loaded = true
        File(context.applicationContext.filesDir, FILE).delete()
    }
}
