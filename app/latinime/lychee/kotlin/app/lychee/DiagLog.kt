package app.lychee

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short log of what the keyboard's engines did (packs unpacked, Rime started, schemas
 * chosen, failures), kept in memory only (never written to storage, which cost battery on every
 * line) so Settings > About > Diagnostics can show and share it without adb while the keyboard
 * is running. The newest [KEEP] lines are kept; nothing leaves the device unless the user shares
 * it. Crashes are kept on their own (CrashLog).
 */
object DiagLog {
    /** The file older versions wrote: removed on first use. */
    private const val OLD_FILE = "diagnostics.txt"
    private const val KEEP = 300
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT)

    @JvmStatic
    fun init(context: Context) {
        runCatching { File(context.applicationContext.filesDir, OLD_FILE).delete() }
    }

    @JvmStatic
    @Synchronized
    fun log(message: String) {
        Log.i("Lychee", message)
        lines.addLast("${time.format(Date())}  $message")
        while (lines.size > KEEP) lines.removeFirst()
    }

    @JvmStatic
    fun log(message: String, error: Throwable) = log("$message: ${error.javaClass.simpleName}: ${error.message}")

    @Synchronized
    @Suppress("UNUSED_PARAMETER")
    fun read(context: Context): String = lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n")

    @Synchronized
    @Suppress("UNUSED_PARAMETER")
    fun clear(context: Context) {
        lines.clear()
    }
}
