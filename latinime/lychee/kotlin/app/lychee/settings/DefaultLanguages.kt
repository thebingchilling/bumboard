package app.lychee.settings

import android.content.Context
import android.os.Build
import android.preference.PreferenceManager
import android.view.inputmethod.InputMethodManager
import com.android.inputmethod.latin.settings.Settings
import com.android.inputmethod.latin.utils.UncachedInputMethodManagerUtils
import java.util.Locale

/**
 * Lychee's languages out of the box: English (UK English where the phone's region uses it,
 * otherwise US) and 普通話 Mandarin; nothing else until turned on under Languages.
 */
object DefaultLanguages {
    private const val PREF_DONE = "lychee_default_languages_done"
    /** LatinIME's old predefined input styles (German QWERTY, French QWERTZ), no longer offered. */
    private const val OLD_PREDEFINED = "de:qwerty:AsciiCapable;fr:qwertz:AsciiCapable"
    private val UK_REGIONS = setOf("GB", "IE", "AU", "NZ", "ZA", "IN", "SG", "HK", "MY")

    /** Drops the old German/French input styles; call before LatinIME reads its subtypes. */
    @JvmStatic
    fun removeOldInputStyles(context: Context) {
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getString(Settings.PREF_CUSTOM_INPUT_STYLES, null) == OLD_PREDEFINED) {
            prefs.edit().putString(Settings.PREF_CUSTOM_INPUT_STYLES, "").apply()
        }
    }

    /** Once: turns on English (US or UK) and Mandarin only. Needs Android 14 or later. */
    @JvmStatic
    fun applyOnce(context: Context) {
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(PREF_DONE, false) || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return
        val imi = UncachedInputMethodManagerUtils.getInputMethodInfoOf(context.packageName, imm) ?: return
        val english = if (Locale.getDefault().country.uppercase() in UK_REGIONS) "en_GB" else "en_US"
        val hashes = (0 until imi.subtypeCount).map { imi.getSubtypeAt(it) }
            .filter { it.locale == english || it.getExtraValueOf("LycheeEngine") == "cmn" }
            .map { it.hashCode() }
        if (hashes.isEmpty()) return
        try {
            imm.setExplicitlyEnabledInputMethodSubtypes(imi.id, hashes.toIntArray())
            prefs.edit().putBoolean(PREF_DONE, true).apply()
        } catch (e: RuntimeException) {
            // not allowed yet (e.g. Lychee not turned on); tried again next time
        }
    }
}
