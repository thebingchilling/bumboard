package app.lychee.typing

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Fields that take only one kind of text: numbers, phone numbers and dates (the number pad),
 * and passwords, email addresses and web addresses (plain Latin letters).
 *
 * - Number, phone and date fields: the number pad; no language switching.
 * - Password, email and web address fields: they open as a plain English keyboard (no Chinese,
 *   Japanese or Korean engine, no handwriting, none of the Chinese keys), but the language key
 *   and shortcut still work: switching language there turns the keyboards back on for that field.
 */
object FieldKind {
    /** Whether the field opened as plain English and hasn't been switched to another language. */
    @JvmStatic
    @Volatile
    var restricted: Boolean = false
        private set

    /** Whether the field is a number, phone or date field: no language switching at all. */
    @JvmStatic
    @Volatile
    var languageLocked: Boolean = false
        private set

    private var lastField: String? = null
    private var unlocked = false

    @JvmStatic
    fun update(info: EditorInfo?) {
        val field = info?.let { "${it.packageName}/${it.fieldId}/${it.inputType}" }
        // a switch of language holds until another field starts (apps may restart the same one)
        if (field != lastField) unlocked = false
        lastField = field
        languageLocked = info != null && isNumeric(info.inputType)
        restricted = info != null && isRestricted(info.inputType) && !unlocked
    }

    /** The language was switched in a password, email or web address field: keyboards come back. */
    @JvmStatic
    fun unlock() {
        if (languageLocked) return
        unlocked = true
        restricted = false
    }

    @JvmStatic
    fun isNumeric(inputType: Int): Boolean = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
        else -> false
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
