package app.bumboard.layout

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import app.bumboard.toolbar.Shortcut
import app.bumboard.toolbar.ShortcutStore

/**
 * How the keyboard is laid out: normal, one-handed (narrower, against one side) or floating
 * (a smaller card that can be dragged anywhere), its height, the number row and which key sits
 * beside the comma. Stored in LatinIME's default preferences; KeyboardSwitcher sizes the keys
 * from this and [LayoutApplier] arranges the views.
 */
class BumLayout private constructor(private val prefs: SharedPreferences) {

    enum class Mode { NORMAL, ONE_HANDED, FLOATING }

    val mode: Mode
        get() = when (prefs.getString(PREF_MODE, null)) {
            "one_handed" -> Mode.ONE_HANDED
            "floating" -> Mode.FLOATING
            else -> Mode.NORMAL
        }

    /** In one-handed mode: true when the keys are against the right edge. */
    val oneHandedRight: Boolean get() = prefs.getBoolean(PREF_ONE_HANDED_RIGHT, true)

    /** Keyboard height as a fraction of the standard height. */
    val heightScale: Float get() = prefs.getFloat(PREF_HEIGHT_SCALE, 1f).coerceIn(MIN_HEIGHT, MAX_HEIGHT)

    /** Floating keyboard width as a fraction of the screen width. */
    val floatWidth: Float get() = prefs.getFloat(PREF_FLOAT_WIDTH, 0.8f).coerceIn(MIN_FLOAT_WIDTH, 1f)

    /** Floating keyboard position: left edge and distance above the bottom, in pixels (-1: centre). */
    val floatX: Int get() = prefs.getInt(PREF_FLOAT_X, -1)
    val floatY: Int get() = prefs.getInt(PREF_FLOAT_Y, -1)

    val numberRow: Boolean get() = prefs.getBoolean(PREF_NUMBER_ROW, false)

    /** The key beside the comma: "emoji", "language" or "none". */
    val commaKey: String get() = prefs.getString(PREF_COMMA_KEY, null) ?: "emoji"

    /** True: an emoji key beside the comma (when there is one); false: a next-language key. */
    val emojiKey: Boolean get() = commaKey != "language"

    /** Whether there is a key beside the comma at all. */
    val sideKey: Boolean get() = commaKey != "none"

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

    /** The comma's popup as LatinIME more-keys: Settings, then each chosen shortcut. */
    fun commaMoreKeys(@Suppress("UNUSED_PARAMETER") languageKey: Boolean): String =
        (listOf("!text/keyspec_settings") + commaMenu().map { "!icon/bum_sc_${it.id}|!code/key_bum_sc_${it.id}" })
            .joinToString(",")

    fun keyboardWidth(fullWidth: Int): Int = when (mode) {
        Mode.NORMAL -> fullWidth
        Mode.ONE_HANDED -> (fullWidth * ONE_HANDED_WIDTH).toInt()
        Mode.FLOATING -> (fullWidth * floatWidth).toInt()
    }

    fun keyboardHeight(standardHeight: Int): Int {
        var h = standardHeight * heightScale
        if (numberRow) h *= NUMBER_ROW_GROWTH
        if (mode == Mode.FLOATING) h *= FLOAT_HEIGHT
        return h.toInt()
    }

    fun edit(): SharedPreferences.Editor = prefs.edit()

    fun setMode(mode: Mode) = prefs.edit().putString(PREF_MODE, when (mode) {
        Mode.NORMAL -> "normal"
        Mode.ONE_HANDED -> "one_handed"
        Mode.FLOATING -> "floating"
    }).apply()

    companion object {
        const val PREF_MODE = "bum_layout_mode"
        const val PREF_ONE_HANDED_RIGHT = "bum_one_handed_right"
        const val PREF_HEIGHT_SCALE = "bum_height_scale"
        const val PREF_FLOAT_WIDTH = "bum_float_width"
        const val PREF_FLOAT_X = "bum_float_x"
        const val PREF_FLOAT_Y = "bum_float_y"
        const val PREF_NUMBER_ROW = "bum_number_row"
        const val PREF_COMMA_KEY = "bum_comma_key"
        const val PREF_COMMA_MENU = "bum_comma_menu"

        const val MIN_HEIGHT = 0.7f
        const val MAX_HEIGHT = 1.5f
        const val MIN_FLOAT_WIDTH = 0.5f
        const val ONE_HANDED_WIDTH = 0.82f
        /** A fifth row on top of four: the letters keep their height. */
        const val NUMBER_ROW_GROWTH = 1.25f
        const val FLOAT_HEIGHT = 0.85f

        @JvmStatic
        @Suppress("DEPRECATION")
        fun get(context: Context) = BumLayout(PreferenceManager.getDefaultSharedPreferences(context))
    }
}
