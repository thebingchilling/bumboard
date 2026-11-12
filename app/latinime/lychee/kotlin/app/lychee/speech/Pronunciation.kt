package app.lychee.speech

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import java.util.Locale

/**
 * Which way a language is said, for readings, cards and the speak buttons (Settings >
 * Pronunciation):
 *  - Mandarin: the Mainland's (CC-CEDICT's readings, a zh-CN voice) or Taiwan's (its "Taiwan
 *    pr." readings where they differ, such as 垃圾 lèsè, and a zh-TW voice);
 *  - English: both UK and US pronunciations on the cards (the default), or just one, and that
 *    voice for words without their own.
 */
object Pronunciation {
    const val PREF_MANDARIN = "lychee_pron_mandarin"
    const val PREF_ENGLISH = "lychee_pron_english"

    const val MAINLAND = "mainland"
    const val TAIWAN = "taiwan"
    const val BOTH = "both"
    const val US = "us"
    const val UK = "uk"

    @Suppress("DEPRECATION")
    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun mandarinTaiwan(prefs: SharedPreferences): Boolean = prefs.getString(PREF_MANDARIN, MAINLAND) == TAIWAN

    fun mandarinTaiwan(context: Context): Boolean = mandarinTaiwan(prefs(context))

    /** [BOTH], [US] or [UK]. */
    fun english(context: Context): String = prefs(context).getString(PREF_ENGLISH, BOTH) ?: BOTH

    /** The voice for English words with no pronunciation of their own; null for the phone's. */
    fun englishLocale(context: Context): Locale? = when (english(context)) {
        US -> Locale.US
        UK -> Locale.UK
        else -> null
    }

    /**
     * Whether an English pronunciation part ("/ˈtʌməˌtoʊ/ (US)") shows: an untagged one always,
     * a tagged one when it is the region chosen or both are.
     */
    fun showsEnglish(context: Context, part: String): Boolean = when (english(context)) {
        US -> !part.endsWith("(UK)")
        UK -> !part.endsWith("(US)")
        else -> true
    }
}
