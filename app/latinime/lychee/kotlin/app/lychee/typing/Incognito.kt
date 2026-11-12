package app.lychee.typing

import android.view.inputmethod.EditorInfo
import com.android.inputmethod.latin.utils.InputTypeUtils

/**
 * Incognito: Lychee learns and keeps nothing from what is typed. On by itself, and only then,
 * in password fields and in apps that ask keyboards not to learn (Chrome's incognito tabs).
 * There is no switch for it: it follows the field.
 *
 * While it is on: no new words or word pairs, no autocorrect memory, no key-touch tuning, no
 * handwriting samples and no recent emoji. The clipboard isn't touched by it (copies are made in
 * other apps; sensitive ones, such as passwords from a password manager, are never kept).
 */
object Incognito {
    @Volatile
    private var auto = false

    /** Whether nothing should be learned or kept just now. */
    @JvmStatic
    val active: Boolean get() = auto

    /** Called as each field starts. */
    @JvmStatic
    fun update(info: EditorInfo?) {
        auto = info != null && (InputTypeUtils.isPasswordInputType(info.inputType) ||
            InputTypeUtils.isVisiblePasswordInputType(info.inputType) ||
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0)
    }
}
