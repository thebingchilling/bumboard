package app.lychee.core

/** Which Rime schema produced a candidate. */
enum class InputMode(val lang: Lang?, val englishLookup: Boolean) {
    Cantonese(Lang.Cantonese, false),
    Mandarin(Lang.Mandarin, false),
    EnglishToCantonese(Lang.Cantonese, true),
    EnglishToMandarin(Lang.Mandarin, true),
    Other(null, false);
}

enum class CantoneseRomanization { Jyutping, Yale, Both }

/** How tones are written: marks (nǐ, néih; not for Jyutping), numbers (ni3) or superscript (ni³). */
enum class ToneStyle { Marks, Numbers, Superscript }

/** What to show under each candidate. Defaults match the settings screen. */
data class DisplayPrefs(
    val cantonese: CantoneseRomanization = CantoneseRomanization.Jyutping,
    val yaleToneMarks: Boolean = true,
    val pinyinToneMarks: Boolean = true,
    /** Jyutping's tones (numbers or superscript; marks mean numbers) */
    val jyutpingTones: ToneStyle = ToneStyle.Numbers,
    /** Yale's and pinyin's tones; when set, these win over yaleToneMarks / pinyinToneMarks */
    val yaleTones: ToneStyle? = null,
    val pinyinTones: ToneStyle? = null,
    /** pinyin under Cantonese candidates, Jyutping/Yale under Mandarin ones */
    val showOtherLanguage: Boolean = false,
    val showMeaning: Boolean = true,
    /** the Chinese keyboards type Simplified */
    val simplified: Boolean = false,
    /** Zhuyin (bopomofo) with pinyin, under the candidates and on the cards */
    val zhuyin: Boolean = false,
    /** Mandarin as said in Taiwan (CC-CEDICT's "Taiwan pr." readings) rather than the Mainland */
    val mandarinTaiwan: Boolean = false,
)

/** Everything resolved for one candidate, before formatting. */
data class Resolved(
    val lang: Lang,
    /** reading in [lang], numbered syllables; null when unknown */
    val reading: String?,
    /** reading in the other language, when known */
    val otherReading: String?,
    val meaning: String?,
    /** dictionary entries for the word card, best match first */
    val headwords: List<Headword>,
    /** true when the text was not in the dictionary and was read piece by piece */
    val composed: Boolean,
)

data class Annotation(
    val lines: List<String>,
    val meaning: String?,
    val resolved: Resolved?,
    /** each line's tones, syllable by syllable (0: neutral or unknown) */
    val tones: List<List<Int>> = emptyList(),
    /** level, register ... of the word (not of a word read piece by piece) */
    val info: WordInfo? = null,
    /** the word in the other script (学校 for 學校), when it differs */
    val otherScript: String? = null,
) {
    companion object {
        val Empty = Annotation(emptyList(), null, null)
    }
}

class Annotator(private val dictionary: Dictionary, cacheSize: Int = 1024) {

    private val cache = object : LinkedHashMap<String, Resolved?>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Resolved?>) =
            size > cacheSize
    }

    private val lookupCache = object : LinkedHashMap<String, List<Headword>>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Headword>>) =
            size > cacheSize * 4
    }

    @Synchronized
    fun clearCache() {
        cache.clear()
        lookupCache.clear()
    }

    fun annotate(text: String, comment: String, mode: InputMode, prefs: DisplayPrefs): Annotation {
        val resolved = regional(resolve(text, comment, mode) ?: return Annotation.Empty, prefs)
        val lines = toneLines(resolved, prefs)
        val word = resolved.headwords.firstOrNull()?.takeIf { !resolved.composed }
        return Annotation(
            lines.map { it.first },
            if (prefs.showMeaning) resolved.meaning else null,
            resolved,
            tones = lines.map { it.second },
            info = word?.info,
            otherScript = otherScript(text, resolved),
        )
    }

    @Synchronized
    fun resolve(text: String, comment: String, mode: InputMode): Resolved? {
        val key = "${mode.ordinal}\u0000$text\u0000$comment"
        if (cache.containsKey(key)) return cache[key]
        val r = resolveUncached(text, comment, mode)
        cache[key] = r
        return r
    }

    /**
     * [sentence] in [lang]'s romanisation (as chosen in [prefs]), a word at a time: the longest
     * run of characters the dictionary has as a word, read the dictionary's way (the reading
     * of the word, so a character read two ways reads as it does in that word); punctuation
     * as it is written in English, anything else as it is.
     */
    fun romanise(sentence: String, lang: Lang, prefs: DisplayPrefs): String {
        val cps = sentence.codePoints().toArray()
        val words = ArrayList<String>()
        var i = 0
        while (i < cps.size) {
            val cp = cps[i]
            if (!isHan(cp)) {
                val p = SentencePunct[cp] ?: String(Character.toChars(cp))
                when {
                    p.isBlank() -> {}
                    // punctuation joins the word before it
                    words.isNotEmpty() && p in CLOSING -> words[words.size - 1] += p
                    // letters and digits run on in one word
                    words.isNotEmpty() && Character.isLetterOrDigit(cp) && i > 0 && !isHan(cps[i - 1]) &&
                        Character.isLetterOrDigit(cps[i - 1]) -> words[words.size - 1] += p
                    else -> words += p
                }
                i++
                continue
            }
            var matched = false
            for (len in minOf(MAX_WORD, cps.size - i) downTo 1) {
                if ((0 until len).any { !isHan(cps[i + it]) }) continue
                val form = String(cps, i, len)
                val reading = lookup(form).firstNotNullOfOrNull { hw ->
                    hw.readings[lang]?.firstOrNull { it.trim().split(Regex("\\s+")).size == len }
                } ?: continue
                words += format(reading, lang, prefs)
                i += len
                matched = true
                break
            }
            if (!matched) {
                words += String(Character.toChars(cp))
                i++
            }
        }
        return words.joinToString(" ")
    }

    private fun lookup(form: String): List<Headword> =
        lookupCache.getOrPut(form) { dictionary.lookup(form).sortedByDescending { it.frequency } }

    private fun resolveUncached(text: String, comment: String, mode: InputMode): Resolved? {
        val lang = mode.lang ?: return null
        val chars = text.codePoints().toArray()
        if (chars.isEmpty() || chars.none(::isHan)) return null
        val hint = if (mode.englishLookup) null else parseHint(comment, lang, chars.size)
        val hws = lookup(text)
        if (hws.isNotEmpty()) return resolveWord(hws, hint, lang)
        return resolveComposed(chars, hint, lang)
    }

    private fun resolveWord(hws: List<Headword>, hint: List<String>?, lang: Lang): Resolved {
        val hintText = hint?.joinToString(" ")
        var best: Headword? = null
        var reading: String? = null
        if (hintText != null) {
            loop@ for (hw in hws) {
                for (r in hw.readings[lang].orEmpty()) {
                    if (matches(r, hintText, lang)) {
                        best = hw; reading = r; break@loop
                    }
                }
            }
        }
        if (best == null) {
            best = hws.firstOrNull { !it.readings[lang].isNullOrEmpty() } ?: hws.first()
            reading = when {
                hintText != null && lang == Lang.Cantonese && hint.all { it.last().isDigit() } -> hintText
                else -> best.readings[lang]?.firstOrNull() ?: hintText
            }
        }
        val other = otherReading(best, lang, reading)
        val ordered = listOf(best) + hws.filter { it !== best }
        return Resolved(lang, reading, other, meaning(ordered, lang, reading), ordered, composed = false)
    }

    private fun resolveComposed(chars: IntArray, hint: List<String>?, lang: Lang): Resolved {
        val readings = mutableListOf<String?>()
        val others = mutableListOf<String?>()
        val headwords = mutableListOf<Headword>()
        var i = 0
        while (i < chars.size) {
            var len = minOf(MAX_SEGMENT, chars.size - i)
            var found: List<Headword> = emptyList()
            while (len > 0) {
                found = lookup(String(chars, i, len))
                if (found.isNotEmpty()) break
                len--
            }
            if (found.isEmpty()) {
                readings += hint?.getOrNull(i)
                others += null
                i++
                continue
            }
            val sub = hint?.subList(i, i + len)
            val r = resolveWord(found, sub, lang)
            readings += r.reading ?: sub?.joinToString(" ")
            others += r.otherReading
            headwords += r.headwords.first()
            i += len
        }
        // every candidate gets a reading: a piece the dictionary doesn't know shows as "?"
        val reading = readings.takeIf { it.any { r -> r != null } }?.joinToString(" ") { it ?: "?" }
        val other = others.takeIf { it.any { r -> r != null } }?.joinToString(" ") { it ?: "?" }
        // a phrase read piece by piece: the pieces' meanings, so every candidate has one
        val meaning = headwords.mapNotNull { meaning(listOf(it), lang, null)?.let { m -> shorten(m, 16) } }
            .takeIf { it.isNotEmpty() }?.joinToString(" + ")
        return Resolved(lang, reading, other, meaning, headwords, composed = true)
    }

    private fun matches(reading: String, hint: String, lang: Lang): Boolean = when (lang) {
        Lang.Cantonese -> {
            val hasTones = hint.any { it.isDigit() }
            if (hasTones) reading == hint else Romanization.toneless(reading) == hint
        }
        else -> Romanization.pinyinKey(reading) == Romanization.pinyinKey(hint)
    }

    private fun otherReading(hw: Headword, lang: Lang, reading: String?): String? {
        val otherLang = if (lang == Lang.Cantonese) Lang.Mandarin else Lang.Cantonese
        if (reading != null) {
            val viaSense = when (lang) {
                Lang.Cantonese -> hw.senses.firstOrNull { it.lang == Lang.Mandarin && it.alt == reading }?.reading
                else -> hw.senses.firstOrNull {
                    it.lang == Lang.Mandarin && it.reading != null && it.alt != null &&
                        Romanization.pinyinKey(it.reading) == Romanization.pinyinKey(reading)
                }?.alt
            }
            if (viaSense != null) return viaSense
        }
        return hw.readings[otherLang]?.firstOrNull()
    }

    private fun meaning(hws: List<Headword>, lang: Lang, reading: String?): String? {
        var bestScore = Int.MAX_VALUE
        var best: Sense? = null
        hws.forEachIndexed { hwIndex, hw ->
            for (s in hw.senses) {
                if (!useful(s)) continue
                val sameReading = reading != null && s.reading != null && when (lang) {
                    Lang.Cantonese -> s.reading == reading
                    else -> Romanization.pinyinKey(s.reading) == Romanization.pinyinKey(reading)
                }
                val score = hwIndex * 10 + when {
                    s.lang == lang && sameReading -> 0
                    lang == Lang.Cantonese && s.lang == Lang.Mandarin && s.alt == reading -> 1
                    s.lang == lang && s.reading == null -> 2
                    s.lang == lang -> 3
                    s.lang == Lang.Any -> 5
                    else -> 4
                }
                if (score < bestScore) {
                    bestScore = score
                    best = s
                }
            }
        }
        return best?.let { shorten(it.gloss) }
    }

    private fun useful(s: Sense): Boolean =
        s.partOfSpeech != "cl" && !unhelpful.containsMatchIn(s.gloss)

    private fun parseHint(comment: String, lang: Lang, length: Int): List<String>? {
        val tokens = comment.trim().removePrefix("~").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size != length) return null
        val pattern = if (lang == Lang.Cantonese) cantoneseToken else mandarinToken
        return tokens.takeIf { t -> t.all { pattern.matches(it) } }?.map { it.lowercase() }
    }

    companion object {
        const val MAX_SEGMENT = 6

        private val taiwanNote = Regex("Taiwan pr\\. \\[([^\\]]+)]", RegexOption.IGNORE_CASE)

        /**
         * The Taiwan reading of a word CC-CEDICT reads [reading] on the Mainland ("Taiwan pr.
         * [le4 se4]" on 垃圾), or null when it has none.
         */
        fun taiwanReading(headwords: List<Headword>, reading: String?): String? {
            val r = reading?.trim()?.lowercase() ?: return null
            val syllables = r.split(Regex("\\s+")).size
            for (hw in headwords) {
                if (hw.readings[Lang.Mandarin]?.any { it.trim().lowercase() == r } != true) continue
                for (sense in hw.senses) {
                    if (sense.lang != Lang.Mandarin && sense.lang != Lang.Any) continue
                    val found = taiwanNote.find(sense.gloss)?.groupValues?.get(1)?.trim()?.lowercase() ?: continue
                    if (found.split(Regex("\\s+")).size == syllables) return found
                }
            }
            return null
        }

        /** [resolved] with its Mandarin said the way [prefs] asks (Taiwan or the Mainland). */
        fun regional(resolved: Resolved, prefs: DisplayPrefs): Resolved {
            if (!prefs.mandarinTaiwan || resolved.composed) return resolved
            return when (resolved.lang) {
                Lang.Mandarin -> taiwanReading(resolved.headwords, resolved.reading)
                    ?.let { resolved.copy(reading = it) } ?: resolved
                Lang.Cantonese -> taiwanReading(resolved.headwords, resolved.otherReading)
                    ?.let { resolved.copy(otherReading = it) } ?: resolved
                else -> resolved
            }
        }
        private val cantoneseToken = Regex("^[a-z]+[1-6]?$")
        private val mandarinToken = Regex("^[a-zü]+[1-5]?$")
        private val unhelpful = Regex(
            "^(variant of|old variant|archaic variant|see |see also|also written|also pr\\.|" +
                "Taiwan pr\\.|erhua variant|used in |abbr\\. for|surname )",
            RegexOption.IGNORE_CASE
        )

        private const val MAX_WORD = 6
        private val CLOSING = setOf(".", ",", "!", "?", ":", ";", "…", ")", "\"")
        private val SentencePunct: Map<Int, String> = mapOf(
            '。'.code to ".", '，'.code to ",", '、'.code to ",", '！'.code to "!", '？'.code to "?",
            '：'.code to ":", '；'.code to ";", '「'.code to "\"", '」'.code to "\"", '『'.code to "\"",
            '』'.code to "\"", '（'.code to "(", '）'.code to ")", '…'.code to "…", '　'.code to " ",
            '“'.code to "\"", '”'.code to "\"", '·'.code to " ",
        )

        fun isHan(cp: Int): Boolean =
            cp in 0x3400..0x4DBF || cp in 0x4E00..0x9FFF || cp in 0xF900..0xFAFF ||
                cp in 0x20000..0x323AF || cp == 0x3007

        /** A one-line meaning: first sense, first alternative, no leading usage note. */
        fun shorten(gloss: String, max: Int = 48): String {
            var g = gloss.trim()
            val lead = Regex("^\\(([^)]*)\\)\\s*").find(g)
            if (lead != null && lead.range.last + 1 < g.length) g = g.substring(lead.range.last + 1)
            g = g.split(';').first().trim()
            if (g.length > max) g = g.take(max - 1).trimEnd() + "…"
            return g
        }

        fun lines(r: Resolved, prefs: DisplayPrefs): List<String> = toneLines(r, prefs).map { it.first }

        /** Tone numbers of a numbered reading ("nei5 hou2" -> [5, 2]; Mandarin's 5 is neutral, 0). */
        fun tones(reading: String, lang: Lang): List<Int> =
            reading.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { s ->
                val d = s.last().digitToIntOrNull() ?: 0
                if (lang == Lang.Mandarin && d == 5) 0 else d
            }

        /** [text] in the other script, from the word's Traditional and Simplified forms. */
        fun otherScript(text: String, r: Resolved): String? {
            if (r.headwords.isEmpty()) return null
            val other = if (r.composed) {
                // only when the pieces cover the whole text
                val trad = r.headwords.joinToString("") { it.traditional }
                val simp = r.headwords.joinToString("") { it.simplified }
                when (text) { trad -> simp; simp -> trad; else -> null }
            } else {
                val hw = r.headwords.first()
                when (text) { hw.traditional -> hw.simplified; hw.simplified -> hw.traditional; else -> null }
            }
            return other?.takeIf { it != text }
        }

        /** Each reading line with its tones. */
        fun toneLines(r: Resolved, prefs: DisplayPrefs): List<Pair<String, List<Int>>> {
            val out = mutableListOf<Pair<String, List<Int>>>()
            var lineLang = Lang.Cantonese
            var source = ""
            fun emit(line: String) {
                out += line to tones(source, lineLang)
            }
            fun cantonese(reading: String) {
                lineLang = Lang.Cantonese
                source = reading
                when (prefs.cantonese) {
                    CantoneseRomanization.Jyutping ->
                        emit(Romanization.format(reading, jyutpingStyle(prefs)))
                    CantoneseRomanization.Yale -> emit(yale(reading, prefs))
                    CantoneseRomanization.Both -> {
                        emit(yale(reading, prefs))
                        emit(Romanization.format(reading, jyutpingStyle(prefs)))
                    }
                }
            }
            fun mandarin(reading: String) {
                lineLang = Lang.Mandarin
                source = reading
                emit(Romanization.format(
                    reading,
                    pinyinStyle(prefs)
                ))
                if (prefs.zhuyin) emit(Zhuyin.format(reading))
            }
            when (r.lang) {
                Lang.Cantonese -> {
                    r.reading?.let(::cantonese)
                    if (prefs.showOtherLanguage) r.otherReading?.let(::mandarin)
                }
                else -> {
                    r.reading?.let(::mandarin)
                    if (prefs.showOtherLanguage) r.otherReading?.let { o ->
                        val before = out.size
                        cantonese(o)
                        // keep a single Cantonese line under Mandarin candidates
                        while (out.size > before + 1) out.removeAt(out.size - 1)
                    }
                }
            }
            return out
        }

        fun yale(reading: String, prefs: DisplayPrefs) = Romanization.format(reading, yaleStyle(prefs))

        fun jyutpingStyle(prefs: DisplayPrefs) =
            if (prefs.jyutpingTones == ToneStyle.Superscript) Romanization.Style.JyutpingSuperscript
            else Romanization.Style.JyutpingNumbers

        fun yaleStyle(prefs: DisplayPrefs) = when (prefs.yaleTones ?: if (prefs.yaleToneMarks) ToneStyle.Marks else ToneStyle.Numbers) {
            ToneStyle.Marks -> Romanization.Style.YaleMarks
            ToneStyle.Numbers -> Romanization.Style.YaleNumbers
            ToneStyle.Superscript -> Romanization.Style.YaleSuperscript
        }

        fun pinyinStyle(prefs: DisplayPrefs) = when (prefs.pinyinTones ?: if (prefs.pinyinToneMarks) ToneStyle.Marks else ToneStyle.Numbers) {
            ToneStyle.Marks -> Romanization.Style.PinyinMarks
            ToneStyle.Numbers -> Romanization.Style.PinyinNumbers
            ToneStyle.Superscript -> Romanization.Style.PinyinSuperscript
        }

        /** [reading] (numbered syllables) in [lang]'s romanisation as chosen in [prefs]. */
        fun format(reading: String, lang: Lang, prefs: DisplayPrefs): String = when (lang) {
            Lang.Cantonese -> if (prefs.cantonese == CantoneseRomanization.Yale) yale(reading, prefs)
                else Romanization.format(reading, jyutpingStyle(prefs))
            else -> Romanization.format(reading, pinyinStyle(prefs)) +
                if (prefs.zhuyin) "  " + Zhuyin.format(reading) else ""
        }
    }
}
