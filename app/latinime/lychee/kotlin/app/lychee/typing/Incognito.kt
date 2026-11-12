package app.lychee.typing

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.inputmethod.EditorInfo
import com.android.inputmethod.latin.utils.InputTypeUtils

/**
 * Incognito: Lychee learns and keeps nothing from what is typed. On by itself in password
 * fields and in apps that ask keyboards not to learn (Chrome's incognito tabs), or by hand
 * with the Incognito shortcut until it is turned off again.
 *
 * While it is on: no new words or word pairs, no autocorrect memory, no key-touch tuning, no
 * handwriting samples, no recent emoji and nothing kept from the clipboard.
 */
object Incognito {
    const val PREF = "lychee_incognito"

    @Volatile
    private var auto = false
    @Volatile
    private var manual = false
    private var prefs: SharedPreferences? = null
    /** Settings > Preferences > Incognito changes it too. */
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == PREF) manual = p.getBoolean(PREF, false)
    }

    /** Whether nothing should be learned or kept just now. */
    @JvmStatic
    val active: Boolean get() = auto || manual

    /** Whether incognito was turned on by hand (the shortcut). */
    @JvmStatic
    val manualOn: Boolean get() = manual

    @JvmStatic
    @Suppress("DEPRECATION")
    fun init(context: Context) {
        val p = prefs ?: PreferenceManager.getDefaultSharedPreferences(context.applicationContext).also {
            prefs = it
            it.registerOnSharedPreferenceChangeListener(listener)
        }
        manual = p.getBoolean(PREF, false)
    }

    /** Called as each field starts. */
    @JvmStatic
    fun update(info: EditorInfo?) {
        auto = info != null && (InputTypeUtils.isPasswordInputType(info.inputType) ||
            InputTypeUtils.isVisiblePasswordInputType(info.inputType) ||
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0)
    }

    /** The shortcut: on until turned off again. */
    @JvmStatic
    fun toggle(context: Context): Boolean {
        init(context)
        manual = !manual
        prefs?.edit()?.putBoolean(PREF, manual)?.apply()
        return manual
    }
}
