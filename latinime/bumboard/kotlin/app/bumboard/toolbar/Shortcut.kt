package app.bumboard.toolbar

import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.inputmethod.latin.R

/** A shortcut for the toolbar above the keys and the shortcuts gallery. */
enum class Shortcut(val id: String, @DrawableRes val icon: Int, @StringRes val label: Int) {
    LANGUAGE("language", R.drawable.ic_sym_language, R.string.bum_sc_language),
    TEXT_EDIT("text_edit", R.drawable.ic_sym_text_select_start, R.string.bum_sc_text_edit),
    ONE_HANDED("one_handed", R.drawable.ic_sym_front_hand, R.string.bum_sc_one_handed),
    FLOATING("floating", R.drawable.ic_sym_picture_in_picture, R.string.bum_sc_floating),
    RESIZE("resize", R.drawable.ic_sym_height, R.string.bum_sc_resize),
    EMOJI("emoji", R.drawable.ic_sym_mood, R.string.bum_sc_emoji),
    UNDO("undo", R.drawable.ic_sym_undo, R.string.bum_sc_undo),
    REDO("redo", R.drawable.ic_sym_redo, R.string.bum_sc_redo),
    HANDWRITING("handwriting", R.drawable.ic_sym_edit, R.string.bum_sc_handwriting),
    WRITTEN_CHINESE("written_chinese", R.drawable.ic_sym_translate, R.string.bum_sc_written),
    SETTINGS("settings", R.drawable.ic_sym_settings, R.string.bum_sc_settings);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/**
 * Which shortcuts there are in what order (the gallery), and which of them are on the toolbar.
 * New shortcuts added in later versions join the end of a saved order.
 */
class ShortcutStore(private val prefs: SharedPreferences) {

    fun order(): List<Shortcut> {
        val saved = prefs.getString(PREF_ORDER, null)?.split(',')?.mapNotNull { Shortcut.byId(it) }.orEmpty()
        return saved + Shortcut.entries.filter { it !in saved }
    }

    fun pinned(): Set<Shortcut> =
        (prefs.getString(PREF_PINNED, null) ?: DEFAULT_PINNED).split(',').mapNotNull { Shortcut.byId(it) }.toSet()

    /** The toolbar: pinned shortcuts in gallery order. */
    fun toolbar(): List<Shortcut> = pinned().let { pinned -> order().filter { it in pinned } }

    fun setOrder(order: List<Shortcut>) =
        prefs.edit().putString(PREF_ORDER, order.joinToString(",") { it.id }).apply()

    fun setPinned(pinned: Set<Shortcut>) =
        prefs.edit().putString(PREF_PINNED, pinned.joinToString(",") { it.id }).apply()

    fun setPinned(shortcut: Shortcut, pinned: Boolean) {
        val set = pinned().toMutableSet()
        if (pinned) set += shortcut else set -= shortcut
        prefs.edit().putString(PREF_PINNED, set.joinToString(",") { it.id }).apply()
    }

    companion object {
        const val PREF_ORDER = "bum_shortcuts_order"
        const val PREF_PINNED = "bum_shortcuts_pinned"
        /** Like Gboard's bar: next language, text editing and undo. */
        const val DEFAULT_PINNED = "language,text_edit,undo"
    }
}
