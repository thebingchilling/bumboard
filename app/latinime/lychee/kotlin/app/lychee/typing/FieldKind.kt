package app.lychee.typing

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Fields that take only one kind of text: numbers, phone numbers and dates (the number pad),
 * and passwords, email addresses and web addresses (plain Latin letters). In them Lychee is a
 * plain English keyboard: no Chinese, Japanese or Korean engine, no language switching, no
 * handwriting and none of the Chinese keys (繁/简, 英).
 */
object FieldKind {
    /** Whether the field being typed in is one of those. */
    @JvmStatic
    @Volatile
    var restricted: Boolean = false
        private set

    @JvmStatic
    fun update(info: EditorInfo?) {
        restricted = info != null && isRestricted(info.inputType)
    }

    @JvmStatic
    fun isRestricted(inputType: Int): Boolean {
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
            InputType.TYPE_CLASS_TEXT -> when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI -> true
                else -> false
            }
            else -> false
        }
    }
}
