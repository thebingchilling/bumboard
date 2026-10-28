package app.lychee.layout

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import app.lychee.toolbar.Shortcut
import app.lychee.toolbar.ShortcutStore

/**
 * How the keyboard is laid out: normal or one-handed (narrower, against one side), its height,
 * the number row and which key sits beside the comma. Stored in LatinIME's default preferences; KeyboardSwitcher sizes the keys
 * from this and [LayoutApplier] arranges the views.
 */
class LycheeLayout private constructor(private val prefs: SharedPreferences) {

    enum class Mode { NORMAL, ONE_HANDED }

    val mode: Mode
        get() = when (prefs.getString(PREF_MODE, null)) {
            "one_handed" -> Mode.ONE_HANDED
            else -> Mode.NORMAL
        }

    /** In one-handed mode: true when the keys are against the right edge. */
    val oneHandedRight: Boolean get() = prefs.getBoolean(PREF_ONE_HANDED_RIGHT, true)

    /** Keyboard height as a fraction of the standard height. */
    val heightScale: Float get() = prefs.getFloat(PREF_HEIGHT_SCALE, 1f).coerceIn(MIN_HEIGHT, MAX_HEIGHT)

    val numberRow: Boolean get() = prefs.getBoolean(PREF_NUMBER_ROW, true)

    /**
     * The emoji key: "combined" on the comma key like Gboard's (tap for a comma, long-press for
     * emoji), or a key beside the comma: "emoji", "language" or "none".
     */
    val commaKey: String get() = prefs.getString(PREF_COMMA_KEY, null) ?: "combined"

    /** True: the comma key shows the emoji face and has emoji in its long-press menu. */
    val emojiOnComma: Boolean get() = commaKey == "combined"

    /** True: an emoji key beside the comma (when there is one); false: a next-language key. */
    val emojiKey: Boolean get() = commaKey != "language"

    /** Whether there is a key beside the comma at all. */
    val sideKey: Boolean get() = commaKey == "emoji" || commaKey == "language"

    /**
     * The shortcuts in the comma's popup, after Settings (which is always first). By default
     * one-handed mode and whichever of emoji and next language has no key beside the comma;
     * shortcuts already on the bar above the keys are left out.
     */
    fun commaMenu(): List<Shortcut> = chosenCommaMenu().filter { it !in ShortcutStore(prefs).toolbar() }

    /** The comma popup as chosen in Settings, before leaving out what is on the bar. */
    fun chosenCommaMenu(): List<Shortcut> {
        val saved = prefs.getString(PREF_COMMA_MENU, null)
        if (saved != null) return saved.split(',').mapNotNull { Shortcut.byId(it) }.filter { it != Shortcut.SETTINGS }
        return buildList {
            add(Shortcut.ONE_HANDED)
            if (commaKey != "emoji") add(Shortcut.EMOJI)
            if (commaKey != "language") add(Shortcut.LANGUAGE)
        }
    }

    fun setCommaMenu(shortcuts: List<Shortcut>) =
        prefs.edit().putString(PREF_COMMA_MENU, shortcuts.joinToString(",") { it.id }).apply()

    /**
     * The comma's popup as LatinIME more-keys: Settings, then each chosen shortcut that belongs
     * on the keyboard ([engine]: null for English, which has no handwriting).
     */
    fun commaMoreKeys(@Suppress("UNUSED_PARAMETER") languageKey: Boolean, engine: String?): String =
        (listOf("!text/keyspec_settings") + commaMenu().filter { app.lychee.toolbar.Shortcuts.available(it, engine) }
            .map { "!icon/lychee_sc_${it.id}|!code/key_lychee_sc_${it.id}" })
            .joinToString(",")

    fun keyboardWidth(fullWidth: Int): Int = when (mode) {
        Mode.NORMAL -> fullWidth
        Mode.ONE_HANDED -> (fullWidth * ONE_HANDED_WIDTH).toInt()
    }

    fun keyboardHeight(standardHeight: Int): Int {
        var h = standardHeight * heightScale
        if (numberRow) h *= NUMBER_ROW_GROWTH
        return h.toInt()
    }

    fun edit(): SharedPreferences.Editor = prefs.edit()

    fun setMode(mode: Mode) = prefs.edit().putString(PREF_MODE, when (mode) {
        Mode.NORMAL -> "normal"
        Mode.ONE_HANDED -> "one_handed"
    }).apply()

    companion object {
        const val PREF_MODE = "lychee_layout_mode"
        const val PREF_ONE_HANDED_RIGHT = "lychee_one_handed_right"
        const val PREF_HEIGHT_SCALE = "lychee_height_scale"
        const val PREF_NUMBER_ROW = "lychee_number_row"
        const val PREF_COMMA_KEY = "lychee_comma_key"
        const val PREF_COMMA_MENU = "lychee_comma_menu"

        const val MIN_HEIGHT = 0.7f
        const val MAX_HEIGHT = 1.5f
        const val ONE_HANDED_WIDTH = 0.82f
        /** A fifth row on top of four: the letters keep their height. */
        const val NUMBER_ROW_GROWTH = 1.25f

        @JvmStatic
        @Suppress("DEPRECATION")
        fun get(context: Context) = LycheeLayout(PreferenceManager.getDefaultSharedPreferences(context))
    }
}
