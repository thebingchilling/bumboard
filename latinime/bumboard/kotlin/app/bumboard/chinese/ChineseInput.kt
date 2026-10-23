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
import com.android.inputmethod.latin.suggestions.SuggestionStripView

/**
 * Chinese typing for LatinIME. When the current subtype carries `BumEngine=yue` or
 * `BumEngine=cmn`, LatinIME hands its key events here first; letters go to Rime and the
 * candidates replace the suggestion strip. Everything Rime does not want falls back to LatinIME.
 */
class ChineseInput(private val ime: InputMethodService, private val prefs: SharedPreferences) {
    private var session = 0L
    private var engine: String? = null
    private var bar: CandidateBarView? = null
    private var panel: CandidatePanelView? = null
    private var moreButton: android.widget.ImageView? = null
    private var inputView: View? = null
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
        this.inputView = inputView
        panel = inputView.findViewById<CandidatePanelView>(R.id.bum_candidate_panel)?.also { p ->
            p.onPick = { index -> pick(index) }
        }
        moreButton = inputView.findViewById<android.widget.ImageView>(R.id.bum_candidate_more)?.also { b ->
            b.imageTintList = android.content.res.ColorStateList.valueOf(ime.getColor(R.color.bum_m3_on_key_variant))
            b.background = ime.getDrawable(R.drawable.bum_m3_suggestion)
            b.setOnClickListener { openPanel() }
        }
        bar?.onPick = { index -> pick(index) }
        bar?.onLongPick = { index, anchor -> showDictionary(index, anchor) }
        // hold to peek: the card goes when the finger lifts
        bar?.onRelease = { if (prefs.getString(PREF_DICTIONARY_HOLD, "pin") == "peek") popup?.dismiss() }
        show(null)
    }

    /** Long-press on a candidate: its full dictionary entry in a popup from the candidate. */
    private fun showDictionary(index: Int, anchor: View) {
        val candidate = shown?.candidates?.getOrNull(index) ?: return
        val annotator = Readings.annotator ?: return
        val mode = if (engine == "yue") InputMode.Cantonese else InputMode.Mandarin
        val resolved = annotator.resolve(candidate.text, candidate.comment, mode) ?: return
        if (resolved.headwords.isEmpty() && resolved.reading == null) return
        val frame = keyboardFrame?.takeIf { it.height > 0 } ?: return
        val peek = prefs.getString(PREF_DICTIONARY_HOLD, "pin") == "peek"
        popup?.show(frame, candidate.text, resolved, Readings.displayPrefs(prefs), peek) {
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

    /** The speech recogniser language for the current Chinese keyboard, or null when not Chinese. */
    fun voiceLanguage(): String? = when (engine) {
        null -> null
        "yue" -> "yue-Hant-HK"
        else -> if (prefs.getString(PREF_SCRIPT, "traditional") == "simplified") "cmn-Hans-CN" else "cmn-Hant-TW"
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
            code in '0'.code..'9'.code || code == '\''.code && isComposing -> isComposing && key(code)
            // Chinese punctuation: ends a composition with its first candidate, then the
            // full-width mark, as Chinese input methods do
            code in PUNCTUATION || code == '"'.code || code == '\''.code -> {
                if (isComposing) commitFirst()
                ime.currentInputConnection?.commitText(fullWidth(code), 1)
                true
            }
            // keys already labelled with a full-width mark (the Chinese comma and period)
            code in FULL_WIDTH -> {
                if (isComposing) commitFirst()
                ime.currentInputConnection?.commitText(String(Character.toChars(code)), 1)
                true
            }
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

    /** Whether the next " and ' open a quotation (they alternate, like Chinese IMEs). */
    private var doubleOpen = true
    private var singleOpen = true

    /** The full-width Chinese form of an ASCII punctuation mark. */
    private fun fullWidth(code: Int): String = when (code) {
        '"'.code -> (if (doubleOpen) "“" else "”").also { doubleOpen = !doubleOpen }
        '\''.code -> (if (singleOpen) "‘" else "’").also { singleOpen = !singleOpen }
        else -> PUNCTUATION.getValue(code)
    }

    /** Every candidate, with readings and meanings, over the keys. */
    private fun openPanel() {
        val panel = panel ?: return
        val view = inputView ?: return
        val candidates = shown?.candidates ?: return
        app.bumboard.layout.BumPanels.hideOthers(view, panel)
        app.bumboard.layout.BumPanels.cover(panel, view)
        panel.show(candidates, annotation(), candidateScale())
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

    /**
     * Candidates show in the middle of the strip while Rime is composing, between the locked
     * Traditional/Simplified and mic keys, at the strip's own height; otherwise the strip shows
     * its shortcuts or suggestions as usual.
     */
    private fun show(composition: Composition?) {
        shown = composition
        popup?.dismiss()
        val stripView = strip as? SuggestionStripView
        updateScriptKey(stripView)
        updateBarHeight(stripView)
        val bar = bar ?: return
        val candidates = composition?.candidates.orEmpty()
        if (!isActive || candidates.isEmpty()) {
            bar.setCandidates(emptyList(), null)
            bar.visibility = View.GONE
            moreButton?.visibility = View.GONE
            panel?.visibility = View.GONE
            stripView?.setBumCandidatesShowing(false)
            return
        }
        val full = meaningsInRow()
        val standard = ime.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        val scale = if (full) candidateScale() else minOf(candidateScale(), CandidateBarView.compactFit(ime, standard))
        bar.setStyle(scale, full)
        bar.setCandidates(candidates, annotation())
        bar.visibility = View.VISIBLE
        moreButton?.visibility = View.VISIBLE
        stripView?.setBumCandidatesShowing(true)
    }

    private fun candidateScale() = prefs.getFloat(PREF_CANDIDATE_SIZE, 1f).coerceIn(0.7f, 1.6f)

    private fun meaningsInRow() = prefs.getBoolean(PREF_MEANINGS_IN_ROW, false)

    /**
     * The candidate row is the usual strip height, like English, so the keyboard keeps its size
     * between languages. With "meanings in the candidate row" on, the top bar on the Chinese
     * keyboards is as tall as a candidate with every reading and its meaning, all the time, so
     * nothing is cut off and it never jumps while typing.
     */
    private fun updateBarHeight(stripView: SuggestionStripView?) {
        val res = ime.resources
        val standard = res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        val height = if (!isActive || !meaningsInRow()) standard
        else maxOf(standard, CandidateBarView.heightFor(ime, annotationLines(), candidateScale()))
        for (view in listOfNotNull(stripView, bar)) {
            val lp = view.layoutParams ?: continue
            if (lp.height != height) {
                lp.height = height
                view.layoutParams = lp
            }
        }
    }

    /** 繁 or 简 on the strip's left edge on the Chinese keyboards; a tap switches. */
    private fun updateScriptKey(stripView: SuggestionStripView?) {
        stripView ?: return
        if (!isActive) {
            stripView.setBumScriptKey(null, null)
            return
        }
        val simplified = prefs.getString(PREF_SCRIPT, "traditional") == "simplified"
        stripView.setBumScriptKey(simplified) {
            prefs.edit().putString(PREF_SCRIPT, if (simplified) "traditional" else "simplified").apply()
            if (RimeService.ready && session != 0L) selectSchema()
            if (isComposing) sync() else show(shown)
        }
    }

    /** What each candidate shows under its characters, so the bar can scale it to fit. */
    private fun annotationLines(): CandidateBarView.Lines {
        if (!Readings.showReadings(prefs)) return CandidateBarView.Lines(0, false)
        val display = Readings.displayPrefs(prefs)
        var lines = 1
        if (display.cantonese == CantoneseRomanization.Both && engine == "yue") lines++
        if (display.showOtherLanguage) lines++
        return CandidateBarView.Lines(lines, display.showMeaning)
    }

    companion object {
        /** ASCII punctuation and its Chinese full-width form. */
        private val PUNCTUATION = mapOf(
            ','.code to "，", '.'.code to "。", '?'.code to "？", '!'.code to "！",
            ':'.code to "：", ';'.code to "；", '('.code to "（", ')'.code to "）",
            '['.code to "「", ']'.code to "」", '{'.code to "『", '}'.code to "』",
            '<'.code to "《", '>'.code to "》", '\\'.code to "、", '~'.code to "～",
            '^'.code to "……", '_'.code to "——",
        )
        private val FULL_WIDTH: Set<Int> =
            (PUNCTUATION.values.flatMap { it.codePoints().toArray().toList() } + "“”‘’".map { it.code }).toSet()
        const val EXTRA_ENGINE = "BumEngine"
        const val PREF_SCRIPT = "bum_chinese_script"
        /** Candidate text size, 0.7 .. 1.6. */
        const val PREF_CANDIDATE_SIZE = "bum_candidate_size"
        /** Every reading and the meaning in the candidate row (a taller bar); off: compact. */
        const val PREF_MEANINGS_IN_ROW = "bum_meanings_in_row"
        /** Long-press on a candidate: "peek" (while held) or "pin" (until closed). */
        const val PREF_DICTIONARY_HOLD = "bum_dictionary_hold"
        const val PREF_CANTONESE_INPUT = "bum_cantonese_input"
        private const val KEY_BACKSPACE = 0xff08
        /** Candidates fetched per keystroke; the bar scrolls through these. */
        private const val CANDIDATE_LIMIT = 40
    }
}
