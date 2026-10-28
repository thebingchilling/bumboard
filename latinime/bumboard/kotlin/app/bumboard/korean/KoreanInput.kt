package app.bumboard.korean

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.InputMethodSubtype
import com.android.inputmethod.event.Event
import com.android.inputmethod.latin.common.Constants

/**
 * Korean typing for the 2-set keyboard (subtype extra value `BumEngine=ko`): jamo keys build
 * syllables in the text field's composing text; backspace takes off one jamo at a time. Any
 * other key finishes the syllable and then goes on to LatinIME as usual.
 */
class KoreanInput(private val ime: InputMethodService) {
    private val composer = HangulComposer()
    private var active = false

    val isActive get() = active
    val isComposing get() = composer.isComposing

    fun onSubtypeChanged(subtype: InputMethodSubtype?) {
        reset()
        active = subtype?.getExtraValueOf("BumEngine") == "ko"
    }

    /** Returns true when the event was used. */
    fun onEvent(event: Event): Boolean {
        if (!active) return false
        val code = event.mCodePoint
        val ic = ime.currentInputConnection ?: return false
        if (Hangul.isJamo(code)) {
            val done = composer.type(code.toChar())
            ic.beginBatchEdit()
            if (done.isNotEmpty()) ic.commitText(done, 1)
            ic.setComposingText(composer.current.text, 1)
            ic.endBatchEdit()
            return true
        }
        if (event.mKeyCode == Constants.CODE_DELETE && composer.isComposing) {
            composer.backspace()
            if (composer.isComposing) {
                ic.setComposingText(composer.current.text, 1)
            } else {
                ic.setComposingText("", 1)
                ic.finishComposingText()
            }
            return true
        }
        // anything else ends the syllable first
        finish()
        return false
    }

    /** Commits the syllable being typed, e.g. before other text or when the field changes. */
    fun finish() {
        if (!composer.isComposing) return
        val ic = ime.currentInputConnection
        val text = composer.finish()
        ic?.setComposingText(text, 1)
        ic?.finishComposingText()
    }

    fun reset() {
        if (composer.isComposing) ime.currentInputConnection?.finishComposingText()
        composer.reset()
    }
}
