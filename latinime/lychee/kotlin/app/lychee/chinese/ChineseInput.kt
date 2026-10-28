package app.lychee.chinese

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodSubtype
import app.lychee.core.Annotation
import app.lychee.core.InputMode
import app.lychee.rime.Candidate
import app.lychee.rime.Composition
import app.lychee.rime.Rime
import app.lychee.rime.RimeService
import com.android.inputmethod.event.Event
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.suggestions.SuggestionStripView

/**
 * Rime typing for LatinIME. When the current subtype carries `LycheeEngine=yue`, `cmn`, `ja` or
 * `ko`, LatinIME hands its key events here first; letters (romanisation) go to Rime and the
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
    private var preedit: PreeditPopup? = null

    /** True while Rime holds a composition, so LatinIME must leave the composing text alone. */
    var isComposing = false
        private set

    val isActive: Boolean
        get() = engine != null

    /** The Rime engine of the current keyboard ("yue", "cmn", "ja", "ko"), or null. */
    val currentEngine: String?
        get() = engine

    fun attachViews(bar: CandidateBarView?, strip: View?, inputView: View) {
        this.bar = bar
        this.strip = strip
        popup?.dismiss()
        popup = DictionaryPopup(inputView.context)
        keyboardFrame = inputView.findViewById(R.id.main_keyboard_frame)
        preedit?.dismiss()
        preedit = PreeditPopup(inputView.context)
        this.inputView = inputView
        panel = inputView.findViewById<CandidatePanelView>(R.id.lychee_candidate_panel)?.also { p ->
            p.onPick = { index -> pick(index); updateMoreButton() }
            p.onLongPick = { index, anchor -> showDictionary(index, anchor) }
        }
        moreButton = inputView.findViewById<android.widget.ImageView>(R.id.lychee_candidate_more)?.also { b ->
            b.imageTintList = android.content.res.ColorStateList.valueOf(inputView.context.getColor(R.color.lychee_m3_on_key_variant))
            b.background = inputView.context.getDrawable(R.drawable.lychee_m3_suggestion)
            b.setOnClickListener { togglePanel() }
        }
        bar?.onPick = { index -> if (showingGuesses) onGuess(index) else pick(index) }
        bar?.onLongPick = { index, anchor -> showDictionary(index, anchor) }
        // the notch: a swipe down on the candidates opens them all, with more room
        bar?.onExpand = { if (panel?.visibility != View.VISIBLE) togglePanel() }
        // hold to peek: the card goes when the finger lifts
        bar?.onRelease = { if (prefs.getString(PREF_DICTIONARY_HOLD, "pin") == "peek") popup?.dismiss() }
        show(null)
    }

    /** Handwriting guesses or next characters, shown in the bar while nothing is composed. */
    private var guesses: List<String> = emptyList()
    private var onGuess: (Int) -> Unit = {}
    private val showingGuesses: Boolean
        get() = shown == null && guesses.isNotEmpty()

    fun showGuesses(list: List<String>, onPick: (Int) -> Unit) {
        guesses = list
        onGuess = onPick
        if (list.isNotEmpty()) Readings.open(ime) { if (showingGuesses) show(null) }
        if (!isComposing) show(null)
    }

    /** Handwriting reads Cantonese, or Mandarin when typing Simplified. */
    private fun guessMode() =
        if (prefs.getString(PREF_SCRIPT, "traditional") == "simplified") InputMode.Mandarin else InputMode.Cantonese

    /** What the bar shows: the keyboard's own candidates, or handwriting guesses. */
    private fun barMode() = if (showingGuesses) guessMode() else inputMode()

    /** Long-press on a candidate: its full dictionary entry in a popup from the candidate. */
    private fun showDictionary(index: Int, anchor: View) {
        val candidate = (if (showingGuesses) guesses.getOrNull(index)?.let { Candidate(it, "") }
            else shown?.candidates?.getOrNull(index)) ?: return
        val guessing = showingGuesses
        if (!isChinese && !guessing) {
            showWordCard(index, candidate)
            return
        }
        val annotator = Readings.annotator ?: return
        val resolved = annotator.resolve(candidate.text, candidate.comment, barMode()) ?: return
        if (resolved.headwords.isEmpty() && resolved.reading == null) return
        val frame = keyboardFrame?.takeIf { it.height > 0 } ?: return
        val peek = prefs.getString(PREF_DICTIONARY_HOLD, "pin") == "peek"
        val voice = if (guessing) (if (guessMode() == InputMode.Cantonese) "yue" else "cmn") else engine
        val locales = app.lychee.speech.Speech.localesFor(voice, prefs.getString(PREF_SCRIPT, "traditional") == "simplified")
        popup?.show(frame, candidate.text, resolved, Readings.displayPrefs(prefs), peek,
            onSpeak = { text, lang ->
                // YUE and PU: the word in Cantonese or in Mandarin, whichever keyboard this is
                val voices = when (lang) {
                    app.lychee.core.Lang.Cantonese -> app.lychee.speech.Speech.localesFor("yue", false)
                    app.lychee.core.Lang.Mandarin -> app.lychee.speech.Speech.localesFor("cmn",
                        prefs.getString(PREF_SCRIPT, "traditional") == "simplified")
                    else -> locales
                }
                app.lychee.speech.Speech.speak(ime, text, voices)
            }) {
            if (guessing) onGuess(index) else pick(index)
        }
    }

    /** Japanese and Korean: the word's entry from the installed dictionary pack. */
    private fun showWordCard(index: Int, candidate: Candidate) {
        val entries = app.lychee.dict.PackData.words(ime, engine, candidate.text)
        if (entries.isEmpty()) return
        val frame = keyboardFrame?.takeIf { it.height > 0 } ?: return
        val peek = prefs.getString(PREF_DICTIONARY_HOLD, "pin") == "peek"
        val lang = if (engine == "ja") "ja" else "ko"
        val forms = listOf(candidate.text) + entries.map { it.lemma }.take(1)
        val examples = app.lychee.dict.PackData.examples(ime, lang, forms)
        val locales = app.lychee.speech.Speech.localesFor(engine, false)
        popup?.showWord(frame, candidate.text, candidate.comment.trim().takeIf { it.isNotEmpty() }, entries,
            examples, peek, onSpeak = { text, _ -> app.lychee.speech.Speech.speak(ime, text, locales) }, lang = lang) {
            pick(index)
        }
    }

    /**
     * A Japanese or Korean candidate's reading and meaning: the kana reading (furigana) of words
     * with kanji, else the romanisation Rime typed; Hanja beside Korean words.
     */
    private fun wordAnnotation(candidate: Candidate, showMeaning: Boolean): Annotation? {
        val romanisation = candidate.comment.trim().takeIf { it.isNotEmpty() }
        val entry = app.lychee.dict.PackData.words(ime, engine, candidate.text, limit = 1).firstOrNull()
        val hasKanji = candidate.text.codePoints().anyMatch { app.lychee.core.Annotator.isHan(it) }
        val reading = when {
            engine == "ja" && hasKanji && entry?.reading != null -> entry.reading
            else -> romanisation
        }
        val meaning = entry?.senses?.firstOrNull()?.takeIf { showMeaning }?.let { app.lychee.core.Annotator.shorten(it) }
        if (reading == null && meaning == null) return null
        return Annotation(listOfNotNull(reading), meaning, null, otherScript = entry?.hanja?.takeIf { engine == "ko" })
    }


    /** Readings for handwriting guesses: Cantonese readings, or Mandarin when typing Simplified. */
    private fun guessAnnotation(): ((Candidate) -> Annotation?)? {
        if (!Readings.showReadings(prefs)) return null
        val annotator = Readings.annotator ?: return null
        val mode = guessMode()
        val display = Readings.displayPrefs(prefs)
        return { candidate -> annotator.annotate(candidate.text, "", mode, display) }
    }

    /** The background annotation for the current settings, or null when readings are off. */
    private fun annotation(): ((Candidate) -> Annotation?)? {
        if (!Readings.showReadings(prefs)) return null
        // Japanese and Korean: each candidate's romanisation (Rime's spelling hint) as its reading
        if (!isChinese) {
            val meaning = prefs.getBoolean(Readings.PREF_MEANING, true)
            return { candidate -> wordAnnotation(candidate, meaning) }
        }
        val annotator = Readings.annotator ?: return null
        val mode = inputMode()
        val display = Readings.displayPrefs(prefs)
        return { candidate -> annotator.annotate(candidate.text, candidate.comment, mode, display) }
    }

    /** The speech recogniser language for the current Chinese keyboard, or null when not Chinese. */
    fun voiceLanguage(): String? = when (engine) {
        null -> null
        "ja" -> "ja-JP"
        "ko" -> "ko-KR"
        "yue" -> "yue-Hant-HK"
        // Mandarin is mainland Mandarin, whichever script is typed
        else -> "cmn-Hans-CN"
    }

    fun onSubtypeChanged(subtype: InputMethodSubtype?) {
        val newEngine = subtype?.getExtraValueOf(EXTRA_ENGINE)
        if (newEngine == engine) return
        reset()
        engine = newEngine
        if (newEngine != null) {
            // imported Rime packs (the Japanese dictionary) join Rime's data first
            RimeService.start(ime) { RimeService.refreshPacks(ime) { selectSchema() } }
            if (newEngine == "yue" || newEngine == "cmn") Readings.open(ime) { if (isComposing) show(shown) }
            if (newEngine == "ja" && !app.lychee.packs.Packs.isInstalled(ime, "rime_ja")) {
                android.widget.Toast.makeText(ime, R.string.lychee_ja_needs_pack, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        show(null)
    }

    /** Cantonese or Mandarin (not Japanese or Korean). */
    private val isChinese: Boolean
        get() = engine == "yue" || engine == "cmn"

    /** Whether 中 is on: English words typed come back as Chinese candidates. */
    private val englishMode: Boolean
        get() = engine.let { it == "yue" || it == "cmn" } && prefs.getBoolean(PREF_ENGLISH_MODE + engine, false)

    private fun inputMode() = when {
        !isChinese -> InputMode.Other
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
        if (engine == "ja" || engine == "ko") {
            // without its schema (the Japanese pack missing or unreadable) the letters type as
            // they are rather than vanishing into Rime
            schemaMissing = !Rime.selectSchema(session, "lychee_$engine")
            return
        }
        schemaMissing = false
        // fuzzy spellings (zh/z, n/l, ng ...) have schemas of their own
        val fuzzy = if (prefs.getBoolean(PREF_FUZZY + engine, false)) "_fuzzy" else ""
        val schema = when {
            englishMode -> if (engine == "yue") "lychee_en_yue" else "lychee_en_cmn"
            engine == "yue" -> (if (prefs.getString(PREF_CANTONESE_INPUT, "jyutping") == "yale") "lychee_yue_yale" else "lychee_yue") + fuzzy
            else -> "lychee_cmn$fuzzy"
        }
        Rime.selectSchema(session, schema)
        val simplified = prefs.getString(PREF_SCRIPT, "traditional") == "simplified"
        Rime.setOption(session, "simplification", simplified)
        Rime.setOption(session, "traditionalization", !simplified)
    }

    /** The keyboard's Rime schema couldn't be selected: keys go to LatinIME. */
    private var schemaMissing = false

    /** Returns true when the event was used for Chinese input. */
    fun onEvent(event: Event): Boolean {
        if (!isActive || schemaMissing) return false
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
                    // Enter sends the typed letters as they are
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
            // Korean has no capitals: they spell the same syllables
            code in 'A'.code..'Z'.code && engine == "ko" -> key(code + 32)
            code in 'A'.code..'Z'.code -> {
                // A capital letter ends the composition and is typed as is.
                if (isComposing) commitFirst()
                false
            }
            code in '0'.code..'9'.code || code == '\''.code && isComposing -> isComposing && key(code)
            // Chinese punctuation: ends a composition with its first candidate, then the
            // full-width mark, as Chinese input methods do
            // Korean punctuation is the ASCII marks: the word typed so far goes first
            engine == "ko" && (code in PUNCTUATION || code == '"'.code || code == '\''.code && !isComposing) -> {
                if (isComposing) commitFirst()
                false
            }
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
    private fun fullWidth(code: Int): String = when {
        engine == "ja" && code in JA_PUNCTUATION -> JA_PUNCTUATION.getValue(code)
        else -> chineseFullWidth(code)
    }

    private fun chineseFullWidth(code: Int): String = when (code) {
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
            app.lychee.layout.LycheePanels.hideOthers(view, panel)
            app.lychee.layout.LycheePanels.cover(panel, view)
            panel.bringToFront()
            panel.show(candidates, candidateScale(), annotation(), Readings.barStyle(prefs, inputMode().lang))
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
            // like Gboard: the letters show above the candidates, not in the text field
            ic?.setComposingText("", 1)
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
        val guessing = composition == null && guesses.isNotEmpty()
        val candidates = if (guessing) guesses.map { Candidate(it, "") } else composition?.candidates.orEmpty()
        val showing = (isActive || guessing) && candidates.isNotEmpty()
        // the candidates get the whole bar: 中 and 繁/简 wait until nothing is typed
        updateScriptKey(stripView, hidden = showing)
        updateBarHeight(stripView, showing)
        val typed = if (isActive) composition?.preedit.orEmpty() else ""
        // over the candidates, or over the strip while nothing is found for the letters yet
        val anchor: View? = if (showing) bar else stripView
        if (typed.isNotEmpty() && anchor != null) {
            // after the bar has its new height and place
            anchor.post { if (shown === composition) preedit?.show(anchor, typed) }
        } else {
            preedit?.dismiss()
        }
        val bar = bar ?: return
        if ((!isActive && !guessing) || candidates.isEmpty()) {
            bar.setCandidates(emptyList(), null)
            bar.visibility = View.GONE
            moreButton?.visibility = View.GONE
            panel?.visibility = View.GONE
            updateMoreButton()
            stripView?.setLycheeCandidatesShowing(false)
            return
        }
        bar.setScale(candidateScale())
        bar.style = Readings.barStyle(prefs, barMode().lang).copy(fontLang = engine)
        bar.setCandidates(candidates, if (guessing) guessAnnotation() else annotation())
        bar.visibility = View.VISIBLE
        moreButton?.visibility = if (guessing) View.GONE else View.VISIBLE
        // the open dropdown follows the typing
        if (panel?.visibility == View.VISIBLE) panel?.show(candidates, candidateScale(), annotation(), bar.style)
        updateMoreButton()
        stripView?.setLycheeCandidatesShowing(true)
    }

    /** The candidate size for this keyboard's language, else the one for all languages. */
    private fun candidateScale(): Float {
        val all = prefs.getFloat(PREF_CANDIDATE_SIZE, 1f)
        return prefs.getFloat(PREF_CANDIDATE_SIZE + "_" + engine, all).coerceIn(0.7f, 1.6f)
    }

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
    private fun updateScriptKey(stripView: SuggestionStripView?, hidden: Boolean = false) {
        stripView ?: return
        if (!isActive || !isChinese || hidden) {
            stripView.setLycheeScriptKey(null, null)
            stripView.setLycheeZhongKey(null, null)
            updateEdges(stripView)
            return
        }
        if ((engine == "yue" || engine == "cmn") && app.lychee.Features.isEnabled(app.lychee.Feature.ENGLISH_TO_CHINESE)) {
            stripView.setLycheeZhongKey(englishMode) { toggleEnglishMode() }
        } else {
            stripView.setLycheeZhongKey(null, null)
        }
        val simplified = prefs.getString(PREF_SCRIPT, "traditional") == "simplified"
        stripView.setLycheeScriptKey(simplified) {
            prefs.edit().putString(PREF_SCRIPT, if (simplified) "traditional" else "simplified").apply()
            if (RimeService.ready && session != 0L) selectSchema()
            if (isComposing) sync() else show(shown)
        }
        updateEdges(stripView)
    }

    /** Keeps the candidates and their ⌄ clear of the pinned keys: 中 on the left, 繁/简 on the right. */
    private fun updateEdges(stripView: SuggestionStripView) {
        val edge = stripView.lycheeEdgeWidth
        val more = ime.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)
        (moreButton?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.marginEnd != edge) {
                lp.marginEnd = edge
                moreButton?.layoutParams = lp
            }
        }
        val start = stripView.lycheeStartWidth
        (bar?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.marginEnd != edge + more || lp.marginStart != start) {
                lp.marginEnd = edge + more
                lp.marginStart = start
                bar?.layoutParams = lp
            }
        }
    }

    /** What each candidate shows under its characters, so the bar can scale it to fit. */
    private fun annotationLines(): CandidateBarView.Lines {
        if (!Readings.showReadings(prefs)) return CandidateBarView.Lines(0, false)
        if (!isChinese && !(shown == null && guesses.isNotEmpty())) {
            return CandidateBarView.Lines(1, prefs.getBoolean(Readings.PREF_MEANING, true) && app.lychee.dict.PackData.hasWords(ime, engine))
        }
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
        /** Japanese punctuation (where it differs from Chinese). */
        private val JA_PUNCTUATION = mapOf(
            ','.code to "、", '.'.code to "。", '/'.code to "・", '~'.code to "〜",
            '\\'.code to "＼", '^'.code to "＾", '_'.code to "＿",
        )
        private val FULL_WIDTH: Set<Int> =
            (PUNCTUATION.values.flatMap { it.codePoints().toArray().toList() } + "“”‘’・〜".map { it.code }).toSet()
        const val EXTRA_ENGINE = "LycheeEngine"
        const val PREF_SCRIPT = "lychee_chinese_script"
        /** Candidate text size, 0.7 .. 1.6; per language with "_yue", "_cmn", "_ja", "_ko". */
        const val PREF_CANDIDATE_SIZE = "lychee_candidate_size"
        /** Long-press on a candidate: "peek" (while held) or "pin" (until closed). */
        const val PREF_DICTIONARY_HOLD = "lychee_dictionary_hold"
        const val PREF_CANTONESE_INPUT = "lychee_cantonese_input"
        /** Per keyboard ("…yue", "…cmn"): fuzzy spellings (zh/z, n/l, ng ...). */
        const val PREF_FUZZY = "lychee_fuzzy_"
        /** Per keyboard ("…yue", "…cmn"): 中 is on. */
        const val PREF_ENGLISH_MODE = "lychee_english_mode_"
        private const val KEY_BACKSPACE = 0xff08
        /** Candidates fetched per keystroke; the bar scrolls through these. */
        private const val CANDIDATE_LIMIT = 40
    }
}
