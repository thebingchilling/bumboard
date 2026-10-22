package app.bumboard.core

/** Which Rime schema produced a candidate. */
enum class InputMode(val lang: Lang?, val englishLookup: Boolean) {
    Cantonese(Lang.Cantonese, false),
    Mandarin(Lang.Mandarin, false),
    EnglishToCantonese(Lang.Cantonese, true),
    EnglishToMandarin(Lang.Mandarin, true),
    Other(null, false);
}

enum class CantoneseRomanization { Jyutping, Yale, Both }

/** What to show under each candidate. Defaults match the settings screen. */
data class DisplayPrefs(
    val cantonese: CantoneseRomanization = CantoneseRomanization.Jyutping,
    val yaleToneMarks: Boolean = true,
    val pinyinToneMarks: Boolean = true,
    /** pinyin under Cantonese candidates, Jyutping/Yale under Mandarin ones */
    val showOtherLanguage: Boolean = false,
    val showMeaning: Boolean = true,
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

data class Annotation(val lines: List<String>, val meaning: String?, val resolved: Resolved?) {
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
        val resolved = resolve(text, comment, mode) ?: return Annotation.Empty
        return Annotation(lines(resolved, prefs), if (prefs.showMeaning) resolved.meaning else null, resolved)
    }

    @Synchronized
    fun resolve(text: String, comment: String, mode: InputMode): Resolved? {
        val key = "${mode.ordinal}\u0000$text\u0000$comment"
        if (cache.containsKey(key)) return cache[key]
        val r = resolveUncached(text, comment, mode)
        cache[key] = r
        return r
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
        val reading = readings.takeIf { it.all { r -> r != null } }?.joinToString(" ")
        val other = others.takeIf { it.all { r -> r != null } }?.joinToString(" ")
        return Resolved(lang, reading, other, null, headwords, composed = true)
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
        private val cantoneseToken = Regex("^[a-z]+[1-6]?$")
        private val mandarinToken = Regex("^[a-zü]+[1-5]?$")
        private val unhelpful = Regex(
            "^(variant of|old variant|archaic variant|see |see also|also written|also pr\\.|" +
                "Taiwan pr\\.|erhua variant|used in |abbr\\. for|surname )",
            RegexOption.IGNORE_CASE
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

        fun lines(r: Resolved, prefs: DisplayPrefs): List<String> {
            val out = mutableListOf<String>()
            fun cantonese(reading: String) {
                when (prefs.cantonese) {
                    CantoneseRomanization.Jyutping ->
                        out += Romanization.format(reading, Romanization.Style.JyutpingNumbers)
                    CantoneseRomanization.Yale -> out += yale(reading, prefs)
                    CantoneseRomanization.Both -> {
                        out += yale(reading, prefs)
                        out += Romanization.format(reading, Romanization.Style.JyutpingNumbers)
                    }
                }
            }
            fun mandarin(reading: String) {
                out += Romanization.format(
                    reading,
                    if (prefs.pinyinToneMarks) Romanization.Style.PinyinMarks else Romanization.Style.PinyinNumbers
                )
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

        fun yale(reading: String, prefs: DisplayPrefs) = Romanization.format(
            reading,
            if (prefs.yaleToneMarks) Romanization.Style.YaleMarks else Romanization.Style.YaleNumbers
        )
    }
}
