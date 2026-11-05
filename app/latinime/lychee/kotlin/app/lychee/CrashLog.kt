package app.lychee

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash's stack trace in files/last_crash.txt so the settings app can show it
 * (with a copy button) without needing adb. Nothing leaves the device.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    @Volatile
    private var installed = false

    @JvmStatic
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        val text = buildString {
            appendLine("Lychee $version crashed")
            appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()))
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
        File(storage(context), FILE).writeText(text)
    }

    /** Device-protected storage, which the keyboard also uses before the phone is unlocked. */
    private fun storage(context: Context): File = context.createDeviceProtectedStorageContext().filesDir

    fun read(context: Context): String? =
        File(storage(context), FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(storage(context), FILE).delete()
    }
}
