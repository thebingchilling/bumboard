package app.lychee.hwgoogle

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon. Its launcher entry is off in the manifest and switched on here unless the
 * person hid it: Android (10 and later) shows an "App info" stand-in icon on every launcher for
 * an app with permissions whose manifest launcher entry is on but turned off, and none for one
 * whose entry is off in the manifest, so hiding it this way really hides it.
 */
object Icon {
    private const val PREF_HIDE = "hide_icon"

    private fun prefs(context: Context) = context.getSharedPreferences("lychee_handwriting", Context.MODE_PRIVATE)

    fun hidden(context: Context) = prefs(context).getBoolean(PREF_HIDE, false)

    fun setHidden(context: Context, hide: Boolean) {
        prefs(context).edit().putBoolean(PREF_HIDE, hide).apply()
        apply(context)
    }

    /** Shows or hides the icon as chosen (shown unless hidden). */
    fun apply(context: Context) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}.Launcher"),
                if (hidden(context)) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
