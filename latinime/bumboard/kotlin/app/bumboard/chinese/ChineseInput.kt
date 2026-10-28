package app.bumboard.chinese

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodSubtype
import app.bumboard.core.Annotation
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

    /** The Rime engine of the current keyboard ("yue", "cmn", "ja"), or null. */
    val currentEngine: String?
        get() = engine

    fun attachViews(bar: CandidateBarView?, strip: View?, inputView: View) {
        this.bar = bar
        this.strip = strip
        popup?.dismiss()
        popup = DictionaryPopup(inputView.context)
        keyboardFrame = inputView.findViewById(R.id.main_keyboard_frame)
        this.inputView = inputView
        panel = inputView.findViewById<CandidatePanelView>(R.id.bum_candidate_panel)?.also { p ->
            p.onPick = { index -> pick(index); updateMoreButton() }
        }
        moreButton = inputView.findViewById<android.widget.ImageView>(R.id.bum_candidate_more)?.also { b ->
            b.imageTintList = android.content.res.ColorStateList.valueOf(ime.getColor(R.color.bum_m3_on_key_variant))
            b.background = ime.getDrawable(R.drawable.bum_m3_suggestion)
            b.setOnClickListener { togglePanel() }
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
        val mode = inputMode()
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
        val mode = inputMode()
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

    /** Whether 中 is on: English words typed come back as Chinese candidates. */
    private val englishMode: Boolean
        get() = engine.let { it == "yue" || it == "cmn" } && prefs.getBoolean(PREF_ENGLISH_MODE + engine, false)

    private fun inputMode() = when {
        engine == "yue" && englishMode -> InputMode.EnglishToCantonese
        engine == "yue" -> InputMode.Cantonese
        englishMode -> InputMode.EnglishToMandarin
        else -> InputMode.Mandarin
    }

    /** 中: switches between pinyin/Jyutping and English-to-Chinese on this keyboard. */
    private fun toggleEnglishMode() {
        val engine = engine ?: return
        if (isComposing) commitFirst()
        prefs.edit().putBoolean(PREF_ENGLISH_MODE + engine, !englishMode).apply()
        if (RimeService.ready && session != 0L) selectSchema()
        show(shown)
    }

    /** Re-reads schema and Traditional/Simplified settings, e.g. after the settings changed. */
    fun onSettingsChanged() {
        if (isActive && RimeService.ready) selectSchema()
        show(null)
    }

    private fun selectSchema() {
        val engine = engine ?: return
        if (session == 0L) session = Rime.createSession()
        val schema = when {
            englishMode -> if (engine == "yue") "bum_en_yue" else "bum_en_cmn"
            engine == "yue" -> if (prefs.getString(PREF_CANTONESE_INPUT, "jyutping") == "yale") "bum_yue_yale" else "bum_yue"
            else -> "bum_cmn"
        }
        Rime.selectSchema(session, schema)
        val simplified = prefs.getString(PREF_SCRIPT, "traditional") == "simplified"
        Rime.setOption(session, "simplification", simplified)
        Rime.setOption(session, "traditionalization", !simplified)
    }

    /** Returns true when the event was used for Chinese input. */
    fun onEvent(event: Event): Boolean {
        if (!isActive) return false
        val code = event.mCodePoint
        if (!RimeService.ready || session == 0L) {
            // Rime is still loading: letters go to LatinIME, but punctuation is Chinese already
            val mark = when {
                code in PUNCTUATION || code == '"'.code || code == '\''.code -> fullWidth(code)
                else -> return false
            }
            ime.currentInputConnection?.commitText(mark, 1)
            return true
        }
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
            // English to Chinese: space takes the first Chinese word (spaces are not part of a lookup)
            code == Constants.CODE_SPACE && englishMode -> {
                val composing = isComposing
                if (composing) pick(0)
                composing
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

    /** The ⌄ / ⌃ at the end of the row: every candidate over the keys, like Gboard. */
    private fun togglePanel() {
        val panel = panel ?: return
        val view = inputView ?: return
        if (panel.visibility == View.VISIBLE) {
            panel.visibility = View.GONE
        } else {
            val candidates = shown?.candidates ?: return
            app.bumboard.layout.BumPanels.hideOthers(view, panel)
            app.bumboard.layout.BumPanels.cover(panel, view)
            panel.bringToFront()
            panel.show(candidates, candidateScale())
        }
        updateMoreButton()
    }

    private fun updateMoreButton() {
        val open = panel?.visibility == View.VISIBLE
        moreButton?.setImageResource(if (open) R.drawable.ic_sym_keyboard_arrow_up else R.drawable.ic_sym_keyboard_arrow_down)
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
        val candidates = composition?.candidates.orEmpty()
        updateBarHeight(stripView, isActive && candidates.isNotEmpty())
        val bar = bar ?: return
        if (!isActive || candidates.isEmpty()) {
            bar.setCandidates(emptyList(), null)
            bar.visibility = View.GONE
            moreButton?.visibility = View.GONE
            panel?.visibility = View.GONE
            updateMoreButton()
            stripView?.setBumCandidatesShowing(false)
            return
        }
        bar.setScale(candidateScale())
        bar.setCandidates(candidates, annotation())
        bar.visibility = View.VISIBLE
        moreButton?.visibility = View.VISIBLE
        // the open dropdown follows the typing
        if (panel?.visibility == View.VISIBLE) panel?.show(candidates, candidateScale())
        updateMoreButton()
        stripView?.setBumCandidatesShowing(true)
    }

    private fun candidateScale() = prefs.getFloat(PREF_CANDIDATE_SIZE, 1f).coerceIn(0.7f, 1.6f)

    /**
     * The top bar keeps the usual strip height (the same as on the English keyboards) and only
     * grows while candidates show, to fit a candidate with its readings and one line of meaning
     * (measured, at the candidate size setting). The keys at its edges stay at the bottom.
     */
    private fun updateBarHeight(stripView: SuggestionStripView?, candidates: Boolean) {
        val res = ime.resources
        val standard = res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        val bar = bar
        val height = if (!candidates || bar == null) standard
        else {
            bar.setScale(candidateScale())
            maxOf(standard, bar.heightFor(annotationLines()))
        }
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
            stripView.setBumZhongKey(null, null)
            updateEdges(stripView)
            return
        }
        if ((engine == "yue" || engine == "cmn") && app.bumboard.Features.isEnabled(app.bumboard.Feature.ENGLISH_TO_CHINESE)) {
            stripView.setBumZhongKey(englishMode) { toggleEnglishMode() }
        } else {
            stripView.setBumZhongKey(null, null)
        }
        updateEdges(stripView)
        val simplified = prefs.getString(PREF_SCRIPT, "traditional") == "simplified"
        stripView.setBumScriptKey(simplified) {
            prefs.edit().putString(PREF_SCRIPT, if (simplified) "traditional" else "simplified").apply()
            if (RimeService.ready && session != 0L) selectSchema()
            if (isComposing) sync() else show(shown)
        }
    }

    /** Keeps the candidates and their ⌄ clear of the locked keys on the right (中, 繁/简). */
    private fun updateEdges(stripView: SuggestionStripView) {
        val edge = stripView.bumEdgeWidth
        val more = ime.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)
        (moreButton?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.marginEnd != edge) {
                lp.marginEnd = edge
                moreButton?.layoutParams = lp
            }
        }
        (bar?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.marginEnd != edge + more) {
                lp.marginEnd = edge + more
                bar?.layoutParams = lp
            }
        }
    }

    /** What each candidate shows under its characters, so the bar can scale it to fit. */
    private fun annotationLines(): CandidateBarView.Lines {
        if (!Readings.showReadings(prefs)) return CandidateBarView.Lines(0, false)
        val display = Readings.displayPrefs(prefs)
        var lines = 1
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
        /** Long-press on a candidate: "peek" (while held) or "pin" (until closed). */
        const val PREF_DICTIONARY_HOLD = "bum_dictionary_hold"
        const val PREF_CANTONESE_INPUT = "bum_cantonese_input"
        /** Per keyboard ("…yue", "…cmn"): 中 is on. */
        const val PREF_ENGLISH_MODE = "bum_english_mode_"
        private const val KEY_BACKSPACE = 0xff08
        /** Candidates fetched per keystroke; the bar scrolls through these. */
        private const val CANDIDATE_LIMIT = 40
    }
}
