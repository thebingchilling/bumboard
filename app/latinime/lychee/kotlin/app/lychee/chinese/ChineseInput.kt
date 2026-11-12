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

    init {
        // Rime restarting (packs brought new schemas): the session goes with it; a new one is
        // made and the schema chosen again when it is back
        RimeService.onRestart = {
            if (isComposing) ime.currentInputConnection?.finishComposingText()
            isComposing = false
            session = 0L
            selectedEngine = null
            selecting = false
            selectToken++
            show(null)
        }
    }
    private var engine: String? = null
    private var bar: CandidateBarView? = null
    private var panel: CandidatePanelView? = null
    private var moreButton: android.widget.ImageView? = null
    private var inputView: View? = null
    private var strip: View? = null
    private var popup: DictionaryPopup? = null
    private var keyboardFrame: View? = null
    private var shown: Composition? = null
    private var preedit: android.widget.TextView? = null

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
        // the phone's speech engine takes seconds to start: started now, it is ready when a
        // card's speak button is first tapped
        app.lychee.speech.Speech.warmUp(ime)
        keyboardFrame = inputView.findViewById(R.id.main_keyboard_frame)
        preedit = inputView.findViewById<android.widget.TextView>(R.id.lychee_preedit)?.also { p ->
            p.setTextColor(app.lychee.ui.LycheeColorStyle.color(inputView.context, R.attr.lycheeColorOnKeyVariant))
            p.typeface = app.lychee.fonts.LycheeFonts.ui(inputView.context, 500)
            p.includeFontPadding = false
        }
        this.inputView = inputView
        panel = inputView.findViewById<CandidatePanelView>(R.id.lychee_candidate_panel)?.also { p ->
            p.onPick = { index -> pick(index); updateMoreButton() }
            p.onLongPick = { index, anchor -> showDictionary(index, anchor) }
        }
        moreButton = inputView.findViewById<android.widget.ImageView>(R.id.lychee_candidate_more)?.also { b ->
            b.imageTintList = android.content.res.ColorStateList.valueOf(app.lychee.ui.LycheeColorStyle.color(inputView.context, R.attr.lycheeColorOnKeyVariant))
            // a divider keeps it apart from the candidates; it presses as a pill beside it
            val ctx = inputView.context
            b.background = android.graphics.drawable.LayerDrawable(arrayOf(
                ctx.getDrawable(R.drawable.lychee_m3_strip_divider)!!, app.lychee.ui.LycheeColorStyle.themed(ctx).getDrawable(R.drawable.lychee_m3_suggestion)!!,
            )).apply {
                setLayerGravity(0, android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL)
                setLayerInsetStart(1, (ctx.resources.displayMetrics.density * 3).toInt())
            }
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

    /** The English suggestions that don't fit on the bar, and what tapping one does. */
    private var englishMore: List<String> = emptyList()
    private var onEnglishMore: (Int) -> Unit = {}

    /** The English keyboards' ⌄: the suggestions that don't fit, in the dropdown. */
    fun setEnglishMore(words: List<String>, onPick: (Int) -> Unit) {
        englishMore = words
        onEnglishMore = onPick
        if (isActive) return
        if (words.isEmpty() && englishPanel) panel?.visibility = View.GONE
        moreButton?.visibility = if (words.isEmpty()) View.GONE else View.VISIBLE
        if (englishPanel && words.isNotEmpty()) showEnglishPanel()
        updateMoreButton()
    }

    /** Whether the dropdown shows English suggestions (not candidates). */
    private val englishPanel: Boolean
        get() = !isActive && panel?.visibility == View.VISIBLE

    private fun showEnglishPanel() {
        val panel = panel ?: return
        val view = inputView ?: return
        app.lychee.layout.LycheePanels.hideOthers(view, panel)
        app.lychee.layout.LycheePanels.cover(panel, view)
        panel.bringToFront()
        val words = englishMore
        panel.show(words.map { Candidate(it, "") }, candidateScale(), null, CandidateBarView.Style(fontLang = "en"),
            onPicked = { i -> onEnglishMore(i); updateMoreButton() },
            onLongPicked = { i, _ -> words.getOrNull(i)?.let { w -> showEnglishWord(w) { onEnglishMore(i) } } })
    }

    /** Handwriting guesses or next characters, shown in the bar while nothing is composed. */
    private var guesses: List<String> = emptyList()
    private var onGuess: (Int) -> Unit = {}
    private val showingGuesses: Boolean
        get() = shown == null && guesses.isNotEmpty()

    fun showGuesses(list: List<String>, onPick: (Int) -> Unit) {
        guesses = list
        onGuess = onPick
        predicting = false
        if (list.isNotEmpty()) Readings.open(ime) { if (showingGuesses) show(null) }
        if (!isComposing) show(null)
    }

    /**
     * Handwriting guesses read like the keyboard's own candidates: Cantonese on the Cantonese
     * keyboard, Mandarin on the Mandarin one; elsewhere Mandarin when typing Simplified.
     */
    private fun guessMode() = when (engine) {
        "yue" -> InputMode.Cantonese
        "cmn" -> InputMode.Mandarin
        else -> if (ChineseScript.candidatesSimplified(prefs)) InputMode.Mandarin else InputMode.Cantonese
    }

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
        // every candidate has a card, even one the dictionary lacks (its strokes, its readings)
        val resolved = annotator.resolve(candidate.text, candidate.comment, barMode())
            ?: app.lychee.core.Resolved(barMode().lang ?: app.lychee.core.Lang.Cantonese, null, null, null, emptyList(), true)
        val frame = keyboardFrame?.takeIf { it.height > 0 } ?: return
        val peek = prefs.getString(PREF_DICTIONARY_HOLD, "pin") == "peek"
        val voice = if (guessing) (if (guessMode() == InputMode.Cantonese) "yue" else "cmn") else engine
        val locales = app.lychee.speech.Speech.localesFor(voice, ChineseScript.candidatesSimplified(prefs))
        // a Kanji or Hanja written on the Japanese or Korean keyboard: its readings in that
        // language (the Chinese ones only when Settings says so)
        val local = if (guessing) localReadings(candidate.text) else null
        val localVoices = app.lychee.speech.Speech.localesFor(engine, false)
        popup?.show(frame, candidate.text, resolved, Readings.displayPrefs(prefs), peek,
            local = local,
            showChinese = prefs.getBoolean(Readings.PREF_CHINESE_ON_KANJI, true),
            onSpeakLocal = { text -> app.lychee.speech.Speech.speak(ime, text, localVoices) },
            onSpeak = { text, lang ->
                // YUE and PU: the word in Cantonese or in Mandarin, whichever keyboard this is
                val voices = when (lang) {
                    app.lychee.core.Lang.Cantonese -> app.lychee.speech.Speech.localesFor("yue", false)
                    app.lychee.core.Lang.Mandarin -> app.lychee.speech.Speech.localesFor("cmn",
                        ChineseScript.candidatesSimplified(prefs))
                    else -> locales
                }
                app.lychee.speech.Speech.speak(ime, text, voices)
            }) {
            if (guessing) onGuess(index) else pick(index)
        }
    }

    /**
     * The English word card for [word] (a suggestion held on an English keyboard): its
     * definitions, examples and pronunciation; false when the dictionary doesn't have it.
     * Tapping the card types the word, as the suggestion would.
     */
    fun showEnglishWord(word: String, onType: () -> Unit): Boolean {
        // any word, even one the dictionary lacks (its card says so, and still speaks it)
        if (word.none { it.isLetter() }) return false
        val entries = app.lychee.dict.PackData.englishWords(ime, word)
        val frame = keyboardFrame?.takeIf { it.height > 0 } ?: return false
        val peek = prefs.getString(PREF_DICTIONARY_HOLD, "pin") == "peek"
        val locales = listOf(java.util.Locale.UK, java.util.Locale.US)
        popup?.showWord(frame, word, null, entries, emptyList(), peek,
            onSpeak = { text, _ -> app.lychee.speech.Speech.speak(ime, text, locales) }, lang = "en",
            // UK and US each in their own voice
            onSpeakAs = { text, locale -> app.lychee.speech.Speech.speak(ime, text, listOfNotNull(locale) + locales) },
            onType = onType)
        return true
    }

    /** Japanese and Korean: the word's entry from the installed dictionary pack. */
    private fun showWordCard(index: Int, candidate: Candidate) {
        // every candidate has a card: one the dictionary lacks shows its readings and strokes
        val entries = app.lychee.dict.PackData.words(ime, engine, candidate.text)
        val frame = keyboardFrame?.takeIf { it.height > 0 } ?: return
        val peek = prefs.getString(PREF_DICTIONARY_HOLD, "pin") == "peek"
        val lang = if (engine == "ja") "ja" else "ko"
        val forms = listOf(candidate.text) + entries.map { it.lemma }.take(1)
        val examples = app.lychee.dict.PackData.examples(ime, lang, forms)
        val locales = app.lychee.speech.Speech.localesFor(engine, false)
        // a word with Kanji or Hanja: its Chinese readings too (Cantonese and Mandarin), unless
        // turned off in Settings
        val display = Readings.displayPrefs(prefs)
        val hasHan = candidate.text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }
        val chinese = if (hasHan && prefs.getBoolean(Readings.PREF_CHINESE_ON_KANJI, true)) {
            Readings.annotator?.resolve(candidate.text, "", InputMode.Cantonese)
                ?.takeIf { r -> r.reading?.contains("?") == false }
                ?.let { r -> app.lychee.core.Annotator.regional(r, display) }
        } else null
        popup?.showWord(frame, candidate.text, wordReading(candidate, entries.firstOrNull()), entries,
            examples, peek, onSpeak = { text, l ->
                val voices = when (l) {
                    app.lychee.core.Lang.Cantonese -> app.lychee.speech.Speech.localesFor("yue", false)
                    app.lychee.core.Lang.Mandarin -> app.lychee.speech.Speech.localesFor("cmn", false)
                    else -> locales
                }
                app.lychee.speech.Speech.speak(ime, text, voices)
            }, lang = lang,
            onSpeakAs = { text, _ -> app.lychee.speech.Speech.speak(ime, text, locales) },
            chinese = chinese, chinesePrefs = display) {
            pick(index)
        }
    }

    /**
     * A Japanese or Korean candidate's reading and meaning: the kana reading (furigana) of words
     * with kanji, else the romanisation Rime typed; Hanja beside Korean words.
     */
    private fun wordAnnotation(candidate: Candidate, showMeaning: Boolean): Annotation? {
        val entry = app.lychee.dict.PackData.words(ime, engine, candidate.text, limit = 1).firstOrNull()
        val meaning = entry?.senses?.firstOrNull()?.takeIf { showMeaning }?.let { app.lychee.core.Annotator.shorten(it) }
        val reading = wordReading(candidate, entry)
        if (reading == null && meaning == null && entry?.hanja == null) return null
        // Korean: the word's Hanja beside its romanisation (the bar draws only the reading lines)
        val hanja = entry?.hanja?.takeIf { engine == "ko" && it.isNotBlank() }
        val line = listOfNotNull(reading, hanja).joinToString("  ").ifEmpty { null }
        return Annotation(listOfNotNull(line), meaning, null, otherScript = hanja)
    }

    /**
     * Every Japanese and Korean candidate's reading, made when the dictionary has none:
     * Japanese, the kana (for words with kanji or katakana) and the romaji; Korean, Revised
     * Romanization.
     */
    private fun wordReading(candidate: Candidate, entry: app.lychee.dict.WordEntry?): String? {
        val text = candidate.text
        val typed = candidate.comment.trim().takeIf { it.isNotEmpty() && it.all { c -> c.isLetter() || c == ' ' || c == '\'' } }
        return when (engine) {
            "ja" -> {
                val hasKanji = text.codePoints().anyMatch { app.lychee.core.Annotator.isHan(it) }
                val katakana = text.any { it in '\u30A1'..'\u30FA' }
                val kana = when {
                    hasKanji -> entry?.reading ?: typed?.let { app.lychee.core.SentenceRomanizer.hiraganaOf(it) }
                    else -> text
                }
                val romaji = typed ?: kana?.let { app.lychee.core.SentenceRomanizer.kana(it) }
                    ?: app.lychee.core.SentenceRomanizer.japanese(text) { form ->
                        app.lychee.dict.PackData.words(ime, "ja", form, 1).firstOrNull()?.let { it.reading ?: it.form }
                    }
                val shownKana = kana?.takeIf { hasKanji || katakana }?.let { k ->
                    // katakana words read in hiragana
                    k.map { if (it in '\u30A1'..'\u30F6') it - 0x60 else it }.joinToString("")
                }
                listOfNotNull(shownKana, romaji.takeIf { it.isNotBlank() && it != text }).joinToString("  ").ifEmpty { null }
            }
            "ko" -> typed ?: app.lychee.core.SentenceRomanizer.korean(text).takeIf { it != text }
            else -> typed
        }
    }


    /**
     * A character's readings in the Japanese or Korean keyboard's own language (on'yomi and
     * kun'yomi in kana with their romaji; Hangul with its romanisation); null on the other
     * keyboards. Kana and Hangul read as themselves.
     */
    private fun localReadings(text: String): List<DictionaryPopup.LocalReading>? {
        val lang = engine?.takeIf { it == "ja" || it == "ko" } ?: return null
        val db = Readings.db
        val out = ArrayList<DictionaryPopup.LocalReading>()
        val isHan = text.codePoints().anyMatch { app.lychee.core.Annotator.isHan(it) }
        if (lang == "ja") {
            if (isHan && db != null) {
                val all = text.codePoints().toArray().singleOrNull()?.let { db.charReadings(String(Character.toChars(it)), "ja") }.orEmpty()
                val on = all.filter { r -> r.any { it in '\u30A1'..'\u30FA' } }
                val kun = all - on.toSet()
                fun row(label: Int, list: List<String>) {
                    if (list.isEmpty()) return
                    val shown = list.joinToString("、") { r -> "$r ${app.lychee.core.SentenceRomanizer.kana(r.replace(".", "").replace("-", ""))}" }
                    out += DictionaryPopup.LocalReading(ime.getString(label), shown, list.first().replace(".", "").replace("-", ""))
                }
                row(R.string.lychee_card_onyomi, on)
                row(R.string.lychee_card_kunyomi, kun)
            } else if (!isHan) {
                out += DictionaryPopup.LocalReading(ime.getString(R.string.lychee_card_reading), app.lychee.core.SentenceRomanizer.kana(text), text)
            }
        } else {
            val hangul = if (isHan && db != null) {
                text.codePoints().toArray().singleOrNull()?.let { db.charReadings(String(Character.toChars(it)), "ko") }.orEmpty()
            } else listOf(text)
            if (hangul.isNotEmpty()) {
                val shown = hangul.joinToString("、") { h -> "$h ${app.lychee.core.SentenceRomanizer.korean(h)}" }
                out += DictionaryPopup.LocalReading(ime.getString(R.string.lychee_card_korean), shown, hangul.first())
            }
        }
        return out
    }

    /** Readings for handwriting guesses: Cantonese readings, or Mandarin when typing Simplified. */
    private fun guessAnnotation(): ((Candidate) -> Annotation?)? {
        if (!Readings.showReadings(prefs)) return null
        // the Japanese and Korean keyboards: the character's own language's readings
        if (engine == "ja" || engine == "ko") {
            // the same reading and meaning a typed candidate shows; the character's own
            // readings when the dictionary has no word for it
            val meaning = prefs.getBoolean(Readings.PREF_MEANING, true)
            return { candidate ->
                wordAnnotation(candidate, meaning)?.takeIf { it.lines.isNotEmpty() } ?: run {
                val local = localReadings(candidate.text).orEmpty()
                val line = local.joinToString("  ") { it.shown.substringBefore("、").let { first ->
                    // under a candidate: the kana (or Hangul) alone, the first of each kind
                    if (engine == "ja") first.substringBefore(' ') else first
                } }
                if (line.isBlank()) null else Annotation(listOf(line), null, null)
                }
            }
        }
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
        schemaMissing = false
        if (newEngine != null) {
            // the schema at once when Rime is running (letters typed straight away reach it);
            // imported Rime packs (the Japanese dictionary) are checked afterwards, and the
            // schema is chosen again only if they changed
            RimeService.start(ime) {
                if (selectedEngine != engine) selectSchema(async = true)
                replayPending()
                RimeService.refreshPacks(ime, engine) { changed ->
                    if (engine != newEngine) return@refreshPacks
                    // a changed pack matters only to the schemas that come from packs (Japanese,
                    // and the English lookups of Japanese and Korean):
                    // choosing the schema again drops what is being typed
                    val needed = (changed && (engine == "ja" || engine == "ko")) || (selectedEngine != engine && !selecting)
                    if (needed && !selectSchema(async = true)) {
                        // the pack never came: what was typed stays as it is, and keys type as is
                        schemaMissing = true
                        flushPending()
                        show(null)
                    }
                }
            }
            if (newEngine == "yue" || newEngine == "cmn") Readings.open(ime) { if (isComposing) show(shown) }
            warmUp(newEngine)
            if (newEngine == "ja" && !app.lychee.packs.Packs.isInstalled(ime, "rime_ja") &&
                !app.lychee.packs.BundledPacks.isBundled(ime, "rime_ja")) {
                android.widget.Toast.makeText(ime, R.string.lychee_ja_needs_pack, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        show(null)
    }

    /**
     * Gets the first key typed going at once: the candidates' font is loaded (WenKai is large)
     * and the keyboard's compiled dictionaries are read through once, so Rime's first look-ups
     * don't wait on storage. In the background, as soon as the keyboard is chosen.
     */
    private fun warmUp(engine: String) {
        val context = ime.applicationContext
        warmer.execute {
            runCatching {
                app.lychee.fonts.LycheeFonts.cjk(context, engine)
                app.lychee.fonts.LycheeFonts.appFont(context, 500)
            }
            val root = java.io.File(context.filesDir, "rime")
            val buffer = ByteArray(1 shl 16)
            for (dir in listOf(java.io.File(root, "user/build"), java.io.File(root, "shared/build"))) {
                dir.listFiles()?.filter { it.name.startsWith("lychee_$engine") && it.name.endsWith(".bin") }?.forEach { f ->
                    runCatching { f.inputStream().use { input -> while (input.read(buffer) > 0) Unit } }
                }
            }
        }
    }

    /** Cantonese or Mandarin (not Japanese or Korean). */
    private val isChinese: Boolean
        get() = engine == "yue" || engine == "cmn"

    /**
     * Whether 英 (영 on Korean) is on: English words typed come back as Chinese, Japanese or
     * Korean candidates.
     */
    private val englishMode: Boolean
        get() = englishAvailable && prefs.getBoolean(PREF_ENGLISH_MODE + engine, false)

    /**
     * Whether this keyboard has an English lookup: Chinese always; Japanese and Korean once
     * their dictionary packs carry one (lychee_en_ja, lychee_en_ko).
     */
    private val englishAvailable: Boolean
        get() = when (engine) {
            "yue", "cmn" -> app.lychee.Features.isEnabled(app.lychee.Feature.ENGLISH_TO_CHINESE)
            "ja", "ko" -> RimeService.hasSchema(ime, "lychee_en_$engine")
            else -> false
        }

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
        // only when something Rime reads changed: choosing the schema again drops what is being
        // typed, and costs time just as typing starts (this runs every time the keyboard opens)
        val signature = schemaSettings()
        if (isActive && RimeService.ready && !selecting && (signature != appliedSettings || selectedEngine != engine)) selectSchema(async = true)
        if (!isComposing) show(null)
    }

    /** The settings the schema choice depends on. */
    private fun schemaSettings(): String = listOf(
        engine, ChineseScript.candidatesSimplified(prefs), englishMode,
        prefs.getBoolean(PREF_FUZZY + engine, false), prefs.getString(PREF_CANTONESE_INPUT, "jyutping"),
    ).joinToString("|")

    /** [schemaSettings] when the schema was last chosen. */
    private var appliedSettings: String? = null

    /** The engine whose schema the session has (null before the first). */
    private var selectedEngine: String? = null

    /** A schema being chosen on Rime's thread (keys wait meanwhile, see onEvent). */
    private var selecting = false
    private var selectToken = 0

    /**
     * Chooses the keyboard's schema. [async]: on Rime's thread, in a new session, so the keys
     * never wait for the dictionaries to load (the first choice of a schema reads them, which
     * held up the first letters typed); letters typed meanwhile wait and go in after.
     * Returns false when the schema isn't there (a pack not yet unpacked, or missing).
     */
    private fun selectSchema(async: Boolean = false): Boolean {
        val engine = engine ?: return false
        if ((engine == "ja" || engine == "ko") && !RimeService.hasSchema(ime, "lychee_$engine")) {
            // not yet: the Japanese dictionary is still unpacking (letters wait, see onEvent),
            // or it isn't there at all (letters type as they are rather than vanishing)
            selectedEngine = null
            schemaMissing = !packComing(engine)
            app.lychee.DiagLog.log("lychee_$engine not there yet (pack ${if (schemaMissing) "missing" else "on its way"})")
            return false
        }
        // fuzzy spellings (zh/z, n/l, ng ...) have schemas of their own
        val fuzzy = if (prefs.getBoolean(PREF_FUZZY + engine, false)) "_fuzzy" else ""
        val schema = when {
            engine == "ja" || engine == "ko" -> if (englishMode) "lychee_en_$engine" else "lychee_$engine"
            englishMode -> if (engine == "yue") "lychee_en_yue" else "lychee_en_cmn"
            engine == "yue" -> (if (prefs.getString(PREF_CANTONESE_INPUT, "jyutping") == "yale") "lychee_yue_yale" else "lychee_yue") + fuzzy
            else -> "lychee_cmn$fuzzy"
        }
        val chinese = engine == "yue" || engine == "cmn"
        val simplified = ChineseScript.candidatesSimplified(prefs)
        appliedSettings = schemaSettings()
        fun apply(s: Long): Boolean {
            val ok = Rime.selectSchema(s, schema)
            if (chinese) {
                Rime.setOption(s, "simplification", simplified)
                Rime.setOption(s, "traditionalization", !simplified)
                // every written form of a Traditional character (為 and 爲) comes up too
                Rime.setOption(s, "lychee_variants", !simplified)
            }
            return ok
        }
        if (!async) {
            if (session == 0L) session = Rime.createSession()
            selectedEngine = engine
            val ok = apply(session)
            schemaMissing = !chinese && !ok
            return true
        }
        // the old session is left alone while the new one loads: nothing on the main thread
        // touches Rime meanwhile (session 0: keys wait in [pending])
        val old = session
        session = 0L
        selectedEngine = null
        selecting = true
        val token = ++selectToken
        val generation = RimeService.generation
        RimeService.onRimeThread {
            val started = System.currentTimeMillis()
            val s = Rime.createSession()
            val ok = apply(s)
            if (old != 0L) Rime.destroySession(old)
            val took = System.currentTimeMillis() - started
            main.post {
                if (token != selectToken || generation != RimeService.generation || this.engine != engine) {
                    RimeService.onRimeThread { Rime.destroySession(s) }
                    return@post
                }
                selecting = false
                session = s
                selectedEngine = engine
                schemaMissing = !chinese && !ok
                app.lychee.DiagLog.log("chose $schema in $took ms: ${if (ok) "ok" else "failed"}")
                replayPending()
                if (!isComposing) show(null)
            }
        }
        return true
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** The keyboard's Rime schema couldn't be selected: keys go to LatinIME. */
    private var schemaMissing = false

    /** Whether [engine]'s schema is on its way (its pack comes with Lychee, or is installed). */
    private fun packComing(engine: String): Boolean = engine == "ja" &&
        (app.lychee.packs.BundledPacks.isBundled(ime, "rime_ja") || app.lychee.packs.Packs.isInstalled(ime, "rime_ja"))

    /** The bar shows what usually follows the word just typed (see [predictAfter]). */
    private var predicting = false

    /**
     * After a word is typed on the Chinese keyboards, the bar suggests what usually follows it
     * (校, 生, 習 after 學), as handwriting does; a tap types one and suggests what follows
     * that. Any key typed clears them. Off with Settings > Typing > Next-word suggestions.
     */
    private fun predictAfter(typed: String) {
        if (!isChinese || typed.isEmpty()) return
        if (!prefs.getBoolean(com.android.inputmethod.latin.settings.Settings.PREF_BIGRAM_PREDICTIONS, true)) return
        val cp = typed.codePointBefore(typed.length)
        if (!app.lychee.core.Annotator.isHan(cp)) return
        val next = runCatching { Readings.db?.continuations(String(Character.toChars(cp)), ChineseScript.candidatesSimplified(prefs)) }
            .getOrNull().orEmpty()
        if (next.isEmpty()) return
        showGuesses(next) { i ->
            next.getOrNull(i)?.let { text ->
                ime.currentInputConnection?.commitText(text, 1)
                predictAfter(text)
            }
        }
        predicting = true
    }

    private fun stopPredicting() {
        if (!predicting) return
        predicting = false
        guesses = emptyList()
        onGuess = {}
        if (!isComposing) show(null)
    }

    /** Returns true when the event was used for Chinese input. */
    fun onEvent(event: Event): Boolean {
        if (!isActive || schemaMissing) return false
        stopPredicting()
        val code = event.mCodePoint
        if (!RimeService.ready || session == 0L || selectedEngine != engine) {
            // Rime is still loading: letters wait (shown as typed) and go to Rime once it is
            // ready, so nothing typed in the first moment is lost; punctuation is Chinese already
            val ic = ime.currentInputConnection
            when {
                code in 'a'.code..'z'.code || (engine == "ko" && code in 'A'.code..'Z'.code) ||
                    (pending.isNotEmpty() && (code in '0'.code..'9'.code || code == '\''.code)) -> {
                    pending.appendCodePoint(if (code in 'A'.code..'Z'.code) code + 32 else code)
                    ic?.setComposingText(pending, 1)
                    return true
                }
                event.mKeyCode == Constants.CODE_DELETE && pending.isNotEmpty() -> {
                    pending.setLength(pending.length - 1)
                    ic?.setComposingText(pending, 1)
                    return true
                }
            }
            // anything else first keeps the waiting letters as they are
            if (pending.isNotEmpty()) {
                ic?.finishComposingText()
                pending.setLength(0)
            }
            val mark = when {
                code in PUNCTUATION || code == '"'.code || code == '\''.code -> fullWidth(code)
                else -> return false
            }
            ic?.commitText(mark, 1)
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

    /** Letters typed while Rime was starting, given to it once it is ready. */
    private val pending = StringBuilder()

    /** Leaves the waiting letters in the text as they are. */
    private fun flushPending() {
        if (pending.isEmpty()) return
        ime.currentInputConnection?.finishComposingText()
        pending.setLength(0)
    }

    private fun replayPending() {
        if (pending.isEmpty() || session == 0L) return
        val letters = pending.toString()
        pending.setLength(0)
        ime.currentInputConnection?.setComposingText("", 1)
        letters.forEach { Rime.processKey(session, it.code) }
        sync()
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
        } else if (showingGuesses) {
            // handwriting guesses: all of them over the pad (which stays open under them)
            val list = guesses.map { Candidate(it, "") }
            app.lychee.layout.LycheePanels.cover(panel, view)
            panel.bringToFront()
            panel.show(list, candidateScale(), guessAnnotation(), bar?.style ?: Readings.barStyle(prefs, guessMode().lang),
                after = bar?.lastVisible ?: -1,
                onPicked = { i -> panel.visibility = View.GONE; onGuess(i); updateMoreButton() },
                onLongPicked = { i, anchor -> showDictionary(i, anchor) })
            app.lychee.layout.LycheePanels.reveal(panel)
        } else if (!isActive) {
            if (englishMore.isNotEmpty()) {
                showEnglishPanel()
                app.lychee.layout.LycheePanels.reveal(panel)
            }
        } else {
            val candidates = shown?.candidates ?: return
            app.lychee.layout.LycheePanels.hideOthers(view, panel)
            app.lychee.layout.LycheePanels.cover(panel, view)
            panel.bringToFront()
            panel.show(candidates, candidateScale(), annotation(), Readings.barStyle(prefs, inputMode().lang),
                after = bar?.lastVisible ?: -1)
            app.lychee.layout.LycheePanels.reveal(panel)
        }
        updateMoreButton()
    }

    private fun updateMoreButton() {
        val open = panel?.visibility == View.VISIBLE
        val b = moreButton ?: return
        // ⌄ while closed, ⌃ while open: the icon swaps with a quick fade (turning the whole
        // button turned its divider and ripple with it)
        b.rotation = 0f
        val icon = if (open) R.drawable.ic_sym_keyboard_arrow_up else R.drawable.ic_sym_keyboard_arrow_down
        if (b.tag == icon) return
        val first = b.tag == null
        b.tag = icon
        if (first || !b.isShown || !app.lychee.layout.LycheePanels.animate(b.context)) {
            b.setImageResource(icon)
            return
        }
        b.animate().cancel()
        b.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(90).withEndAction {
            b.setImageResource(icon)
            b.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()
        }.start()
    }

    /** Drops any composition, e.g. when the text field changes. */
    fun reset() {
        popup?.dismiss()
        pending.setLength(0)
        if (predicting) {
            predicting = false
            guesses = emptyList()
        }
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
        val started = android.os.SystemClock.uptimeMillis()
        val handled = Rime.processKey(session, keycode)
        if (handled) sync()
        val took = android.os.SystemClock.uptimeMillis() - started
        // a key that held the keyboard up is noted, to find what makes typing lag
        if (took > SLOW_KEY_MS) app.lychee.DiagLog.log("slow key on $engine: $took ms")
        return handled
    }

    private fun sync() {
        val ic = ime.currentInputConnection
        val committed = Rime.commit(session)
        committed?.let { ic?.commitText(it, 1) }
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
        // a word just typed: what usually follows it
        if (committed != null && composition == null) predictAfter(committed)
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
        // in the bar's own top line (a window over the app for it cost time on every key)
        val typed = if (isActive) composition?.preedit.orEmpty() else ""
        preedit?.let { p ->
            if (typed.isNotEmpty()) {
                if (p.text.toString() != typed) p.text = typed
                p.visibility = View.VISIBLE
            } else {
                p.visibility = View.GONE
            }
        }
        val bar = bar ?: return
        if ((!isActive && !guessing) || candidates.isEmpty()) {
            bar.setCandidates(emptyList(), null)
            bar.visibility = View.GONE
            val english = !isActive && !guessing && englishMore.isNotEmpty()
            moreButton?.visibility = if (english) View.VISIBLE else View.GONE
            if (!english || !englishPanel) panel?.visibility = View.GONE
            updateMoreButton()
            stripView?.setLycheeCandidatesShowing(false)
            return
        }
        bar.setScale(candidateScale())
        bar.style = Readings.barStyle(prefs, barMode().lang).copy(fontLang = engine)
        val annotate = if (guessing) guessAnnotation() else annotation()
        // every candidate keeps the same lines, filled or not, so they all line up
        bar.lines = if (annotate != null) annotationLines() else null
        bar.setCandidates(candidates, annotate)
        bar.visibility = View.VISIBLE
        // handwriting guesses are candidates like any others: the ⌄ shows them all
        moreButton?.visibility = View.VISIBLE
        // the open dropdown follows the typing
        if (panel?.visibility == View.VISIBLE) {
            bar.post { panel?.show(candidates, candidateScale(), annotation(), bar.style, after = bar.lastVisible) }
        }
        updateMoreButton()
        stripView?.setLycheeCandidatesShowing(true)
    }

    /** The candidate size for this keyboard's language, else the one for all languages. */
    private fun candidateScale(): Float = scaleFor(engine)

    /** The candidate size chosen for [engine]'s keyboard. */
    private fun scaleFor(engine: String?): Float {
        // one size for every language (Settings > Readings & candidates)
        return prefs.getFloat(PREF_CANDIDATE_SIZE, 1f).coerceIn(0.7f, 1.6f)
    }

    /**
     * The reading lines [engine]'s keyboard shows under its candidates, from its own settings
     * only: Zhuyin added to Mandarin makes the Mandarin bar taller, never the Korean one.
     */
    private fun linesFor(engine: String?): CandidateBarView.Lines {
        if (!Readings.showReadings(prefs)) return CandidateBarView.Lines(0, false)
        val meaning = prefs.getBoolean(Readings.PREF_MEANING, true)
        if (engine == "ja" || engine == "ko") return CandidateBarView.Lines(1, meaning)
        val display = Readings.displayPrefs(prefs)
        val mandarinLines = (if (display.pinyin || !display.zhuyin) 1 else 0) + (if (display.zhuyin) 1 else 0)
        val cantoneseLines = if (display.cantonese == app.lychee.core.CantoneseRomanization.Both) 2 else 1
        val lines = when (engine) {
            "cmn" -> mandarinLines + (if (display.showOtherLanguage) 1 else 0)
            else -> cantoneseLines + (if (display.showOtherLanguage) mandarinLines else 0)
        }
        return CandidateBarView.Lines(lines, display.showMeaning || meaning)
    }

    /**
     * On the Rime keyboards the top bar is always as tall as a candidate with its readings and
     * one line of meaning (measured, at the candidate size setting), with a line above for the
     * letters typed: it never changes height while typing (the whole keyboard moved under the
     * finger, and keys pressed just then went astray). Elsewhere it is the usual strip height.
     * The keys at its edges stay at the bottom.
     */
    private fun updateBarHeight(stripView: SuggestionStripView?, candidates: Boolean) {
        val res = ime.resources
        val standard = res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        val line = res.getDimensionPixelSize(R.dimen.lychee_preedit_height)
        val bar = bar
        val rime = isActive && !schemaMissing
        val height = if (bar == null || (!rime && !candidates)) standard
        else {
            // each keyboard's own height: as tall as its own reading lines need (one language's
            // settings never make another's bar taller); steady while typing on it
            bar.setScale(candidateScale())
            val tallest = bar.heightFor(linesFor(engine))
            maxOf(standard, tallest + if (rime) line else 0)
        }
        bar?.let { if (it.paddingTop != (if (rime) line else 0)) it.setPadding(0, if (rime) line else 0, 0, 0) }
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
        if (!isActive || hidden || schemaMissing) {
            stripView.setLycheeScriptKey(null, null)
            stripView.setLycheeZhongKey(null, null)
            updateEdges(stripView)
            return
        }
        // no 英 while the handwriting pad is open: it writes characters, not English
        val writing = stripView.rootView.findViewById<android.view.View>(R.id.lychee_handwriting_panel)
            ?.visibility == android.view.View.VISIBLE
        if (englishAvailable && !writing) {
            stripView.setLycheeZhongKey(englishMode, { toggleEnglishMode() }, if (engine == "ko") "영" else "英")
        } else {
            stripView.setLycheeZhongKey(null, null)
        }
        if (!isChinese) {
            stripView.setLycheeScriptKey(null, null)
            updateEdges(stripView)
            return
        }
        val simplified = ChineseScript.candidatesSimplified(prefs)
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
        val more = ime.resources.getDimensionPixelSize(R.dimen.lychee_candidate_more_width)
        (moreButton?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.marginEnd != edge) {
                lp.marginEnd = edge
                moreButton?.layoutParams = lp
            }
        }
        val start = stripView.lycheeStartWidth
        (preedit?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
            val want = start + (ime.resources.displayMetrics.density * 12).toInt()
            if (lp.marginStart != want) {
                lp.marginStart = want
                preedit?.layoutParams = lp
            }
        }
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
        if (!isChinese) {
            return CandidateBarView.Lines(1, prefs.getBoolean(Readings.PREF_MEANING, true) && app.lychee.dict.PackData.hasWords(ime, engine))
        }
        // the same lines as the keyboard's own bar (handwriting guesses included)
        val lines = linesFor(if (barMode().lang == app.lychee.core.Lang.Mandarin) "cmn" else "yue")
        return CandidateBarView.Lines(lines.readings, Readings.displayPrefs(prefs).showMeaning)
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
        /** Per keyboard ("…yue", "…cmn", "…ja", "…ko"): 英 (영) is on. */
        const val PREF_ENGLISH_MODE = "lychee_english_mode_"
        private const val KEY_BACKSPACE = 0xff08
        /** A key taking longer than this (Rime and the candidates) is noted in Diagnostics. */
        private const val SLOW_KEY_MS = 60L
        private val warmer: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor()
        /** Candidates fetched per keystroke; the bar scrolls through these. */
        private const val CANDIDATE_LIMIT = 40
    }
}
