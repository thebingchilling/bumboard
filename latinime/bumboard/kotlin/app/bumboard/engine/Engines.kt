package app.bumboard.engine

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputMethodSubtype
import app.bumboard.chinese.CandidateBarView
import app.bumboard.chinese.ChineseInput
import app.bumboard.korean.KoreanInput
import com.android.inputmethod.event.Event

/**
 * The input engines LatinIME hands keys to before its own handling: Rime (Cantonese, Mandarin,
 * Japanese; [ChineseInput]) and Korean ([KoreanInput]). The current subtype decides which one
 * is active; the others pass everything through.
 */
class Engines(ime: InputMethodService, prefs: SharedPreferences) {
    val chinese = ChineseInput(ime, prefs)
    val korean = KoreanInput(ime)

    /** True while an engine holds composing text, so LatinIME must leave it alone. */
    val isComposing: Boolean
        get() = chinese.isComposing || korean.isComposing

    fun attachViews(bar: CandidateBarView?, strip: View?, inputView: View) =
        chinese.attachViews(bar, strip, inputView)

    fun onSubtypeChanged(subtype: InputMethodSubtype?) {
        chinese.onSubtypeChanged(subtype)
        korean.onSubtypeChanged(subtype)
    }

    fun onSettingsChanged() = chinese.onSettingsChanged()

    /** Returns true when an engine used the event. */
    fun onEvent(event: Event): Boolean = korean.onEvent(event) || chinese.onEvent(event)

    fun beforeExternalText() {
        korean.finish()
        chinese.beforeExternalText()
    }

    fun reset() {
        chinese.reset()
        korean.reset()
    }

    /** The current keyboard's engine: "yue", "cmn", "ja", "ko", or null for LatinIME's own. */
    val currentEngine: String?
        get() = chinese.currentEngine ?: if (korean.isActive) "ko" else null

    /** The speech recogniser language for the current keyboard, or null to use its locale. */
    fun voiceLanguage(): String? = chinese.voiceLanguage() ?: if (korean.isActive) "ko-KR" else null
}
