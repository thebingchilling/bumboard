package app.lychee.engine

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputMethodSubtype
import app.lychee.chinese.CandidateBarView
import app.lychee.chinese.ChineseInput
import com.android.inputmethod.event.Event

/**
 * The input engine LatinIME hands keys to before its own handling: Rime ([ChineseInput]) for
 * Cantonese, Mandarin, Japanese and Korean, all typed in romanisation. The current subtype
 * decides whether it is active; otherwise it passes everything through.
 */
class Engines(ime: InputMethodService, prefs: SharedPreferences) {
    val chinese = ChineseInput(ime, prefs)

    /** True while an engine holds composing text, so LatinIME must leave it alone. */
    val isComposing: Boolean
        get() = chinese.isComposing

    fun attachViews(bar: CandidateBarView?, strip: View?, inputView: View) =
        chinese.attachViews(bar, strip, inputView)

    fun onSubtypeChanged(subtype: InputMethodSubtype?) {
        chinese.onSubtypeChanged(subtype)
    }

    fun onSettingsChanged() = chinese.onSettingsChanged()

    /** Returns true when an engine used the event. */
    fun onEvent(event: Event): Boolean = chinese.onEvent(event)

    fun beforeExternalText() = chinese.beforeExternalText()

    fun reset() = chinese.reset()

    /** The current keyboard's engine: "yue", "cmn", "ja", "ko", or null for LatinIME's own. */
    val currentEngine: String?
        get() = chinese.currentEngine

    /** The speech recogniser language for the current keyboard, or null to use its locale. */
    fun voiceLanguage(): String? = chinese.voiceLanguage()
}
