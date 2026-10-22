package app.bumboard.chinese

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodSubtype
import app.bumboard.core.Annotation
import app.bumboard.core.CantoneseRomanization
import app.bumboard.core.InputMode
import app.bumboard.rime.Candidate
import app.bumboard.rime.Composition
import app.bumboard.rime.Rime
import app.bumboard.rime.RimeService
import com.android.inputmethod.event.Event
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants

/**
 * Chinese typing for LatinIME. When the current subtype carries `BumEngine=yue` or
 * `BumEngine=cmn`, LatinIME hands its key events here first; letters go to Rime and the
 * candidates replace the suggestion strip. Everything Rime does not want falls back to LatinIME.
 */
class ChineseInput(private val ime: InputMethodService, private val prefs: SharedPreferences) {
    private var session = 0L
    private var engine: String? = null
    private var bar: CandidateBarView? = null
    private var strip: View? = null
    private var popup: DictionaryPopup? = null
    private var keyboardFrame: View? = null
    private var shown: Composition? = null

    /** True while Rime holds a composition, so LatinIME must leave the composing text alone. */
    var isComposing = false
        private set

    val isActive: Boolean
        get() = engine != null

    fun attachViews(bar: CandidateBarView?, strip: View?, inputView: View) {
        this.bar = bar
        this.strip = strip
        popup?.dismiss()
        popup = DictionaryPopup(inputView.context)
        keyboardFrame = inputView.findViewById(R.id.main_keyboard_frame)
        bar?.onPick = { index -> pick(index) }
        bar?.onLongPick = { index, anchor -> showDictionary(index, anchor) }
        show(null)
    }

    /** Long-press on a candidate: its full dictionary entry in a popup from the candidate. */
    private fun showDictionary(index: Int, anchor: View) {
        val candidate = shown?.candidates?.getOrNull(index) ?: return
        val annotator = Readings.annotator ?: return
        val mode = if (engine == "yue") InputMode.Cantonese else InputMode.Mandarin
        val resolved = annotator.resolve(candidate.text, candidate.comment, mode) ?: return
        if (resolved.headwords.isEmpty() && resolved.reading == null) return
        val height = keyboardFrame?.height?.takeIf { it > 0 } ?: return
        popup?.show(anchor, candidate.text, resolved, Readings.displayPrefs(prefs), height - anchor.height) {
            pick(index)
        }
    }

    /** The background annotation for the current settings, or null when readings are off. */
    private fun annotation(): ((Candidate) -> Annotation?)? {
        if (!Readings.showReadings(prefs)) return null
        val annotator = Readings.annotator ?: return null
        val mode = if (engine == "yue") InputMode.Cantonese else InputMode.Mandarin
        val display = Readings.displayPrefs(prefs)
        return { candidate -> annotator.annotate(candidate.text, candidate.comment, mode, display) }
    }

    fun onSubtypeChanged(subtype: InputMethodSubtype?) {
        val newEngine = subtype?.getExtraValueOf(EXTRA_ENGINE)
        if (newEngine == engine) return
        reset()
        engine = newEngine
        if (newEngine != null) {
            RimeService.start(ime) { selectSchema() }
            Readings.open(ime) { if (isComposing) show(shown) }
        }
        show(null)
    }

    /** Re-reads schema and Traditional/Simplified settings, e.g. after the settings changed. */
    fun onSettingsChanged() {
        if (isActive && RimeService.ready) selectSchema()
        show(null)
    }

    private fun selectSchema() {
        val engine = engine ?: return
        if (session == 0L) session = Rime.createSession()
        val schema = when (engine) {
            "yue" -> if (prefs.getString(PREF_CANTONESE_INPUT, "jyutping") == "yale") "bum_yue_yale" else "bum_yue"
            else -> "bum_cmn"
        }
        Rime.selectSchema(session, schema)
        val simplified = prefs.getString(PREF_SCRIPT, "traditional") == "simplified"
        Rime.setOption(session, "simplification", simplified)
        Rime.setOption(session, "traditionalization", !simplified)
    }

    /** Returns true when the event was used for Chinese input. */
    fun onEvent(event: Event): Boolean {
        if (!isActive || !RimeService.ready || session == 0L) return false
        val code = event.mCodePoint
        val handled = when {
            event.mKeyCode == Constants.CODE_DELETE -> isComposing && key(KEY_BACKSPACE)
            code == Constants.CODE_ENTER -> {
                if (isComposing) {
                    // Enter sends the typed letters as they are.
                    val raw = Rime.input(session)
                    Rime.clearComposition(session)
                    ime.currentInputConnection?.commitText(raw, 1)
                    sync()
                    true
                } else {
                    false
                }
            }
            code == Constants.CODE_SPACE -> isComposing && key(code)
            code in 'a'.code..'z'.code -> key(code)
            code in 'A'.code..'Z'.code -> {
                // A capital letter ends the composition and is typed as is.
                if (isComposing) commitFirst()
                false
            }
            code in '0'.code..'9'.code || code == '\''.code -> isComposing && key(code)
            code > 0 -> key(code)
            else -> false
        }
        return handled
    }

    /** Called before LatinIME types text directly (e.g. a key with a label of several characters). */
    fun beforeExternalText() {
        if (isComposing) commitFirst()
    }

    fun pick(index: Int) {
        if (session == 0L) return
        Rime.selectCandidate(session, index)
        sync()
    }

    /** Drops any composition, e.g. when the text field changes. */
    fun reset() {
        popup?.dismiss()
        if (session != 0L) Rime.clearComposition(session)
        if (isComposing) ime.currentInputConnection?.finishComposingText()
        isComposing = false
        show(null)
    }

    private fun commitFirst() {
        Rime.commitComposition(session)
        sync()
    }

    private fun key(keycode: Int): Boolean {
        val handled = Rime.processKey(session, keycode)
        if (handled) sync()
        return handled
    }

    private fun sync() {
        val ic = ime.currentInputConnection
        Rime.commit(session)?.let { ic?.commitText(it, 1) }
        val composition = Rime.composition(session, CANDIDATE_LIMIT)
        if (composition != null) {
            ic?.setComposingText(composition.preedit, 1)
            isComposing = true
        } else if (isComposing) {
            ic?.setComposingText("", 1)
            ic?.finishComposingText()
            isComposing = false
        }
        show(composition)
    }

    /** In Chinese the bar replaces the strip all the time, at a steady height. */
    private fun show(composition: Composition?) {
        shown = composition
        popup?.dismiss()
        val bar = bar ?: return
        if (!isActive) {
            bar.visibility = View.GONE
            strip?.visibility = View.VISIBLE
            return
        }
        bar.setBarHeight(barHeight())
        bar.setCandidates(composition?.candidates.orEmpty(), annotation())
        strip?.visibility = View.GONE
        bar.visibility = View.VISIBLE
    }

    private fun barHeight(): Int {
        val res = ime.resources
        val base = res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        if (!Readings.showReadings(prefs)) return base
        val display = Readings.displayPrefs(prefs)
        var lines = 1
        if (display.cantonese == CantoneseRomanization.Both && engine == "yue") lines++
        if (display.showOtherLanguage) lines++
        fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, res.displayMetrics)
        val extra = lines * sp(14f) + (if (display.showMeaning) sp(13f) else 0f)
        return base + extra.toInt()
    }

    companion object {
        const val EXTRA_ENGINE = "BumEngine"
        const val PREF_SCRIPT = "bum_chinese_script"
        const val PREF_CANTONESE_INPUT = "bum_cantonese_input"
        private const val KEY_BACKSPACE = 0xff08
        /** Candidates fetched per keystroke; the bar scrolls through these. */
        private const val CANDIDATE_LIMIT = 40
    }
}
