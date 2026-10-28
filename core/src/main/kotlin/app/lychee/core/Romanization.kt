package app.lychee.core

import java.text.Normalizer

/** Conversions between the romanisations Lychee shows. Syllables use numbered tones. */
object Romanization {

    private val jyutpingSyllable = Regex("^([a-z]+?)([1-6])$")
    private val pinyinSyllable = Regex("^([a-zü]+?)([1-5])$")

    /** "jyut6" -> "yut6"; "zoeng1" -> "jeung1". Returns the input if it is not Jyutping. */
    fun jyutpingToYaleNumbered(syllable: String): String {
        val m = jyutpingSyllable.matchEntire(syllable.lowercase()) ?: return syllable
        return yaleBase(m.groupValues[1]) + m.groupValues[2]
    }

    /** "jyut6" -> "yuht"; "maai4" -> "màaih"; "si1" -> "sī"; "m4" -> "m̀h". */
    fun jyutpingToYale(syllable: String): String {
        val m = jyutpingSyllable.matchEntire(syllable.lowercase()) ?: return syllable
        val base = yaleBase(m.groupValues[1])
        val tone = m.groupValues[2].toInt()
        val low = tone >= 4
        val mark = when (tone) {
            1 -> '̄' // macron
            2, 5 -> '́' // acute
            4 -> '̀' // grave
            else -> null
        }
        // syllabic nasals: m, ng
        if (base == "m" || base == "ng") {
            val marked = if (mark != null) base[0] + mark.toString() + base.substring(1) else base
            return nfc(marked + if (low) "h" else "")
        }
        val parts = splitYale(base) ?: return nfc(base)
        val (initial, vowels, coda) = parts
        val markIndex = vowels.indexOfFirst { it in "aeiou" }.let { if (it < 0) 0 else it }
        val markedVowels = buildString {
            vowels.forEachIndexed { i, c ->
                append(c)
                if (i == markIndex && mark != null) append(mark)
            }
        }
        return nfc(initial + markedVowels + (if (low) "h" else "") + coda)
    }

    private fun yaleBase(jyutping: String): String {
        var s = jyutping
        s = when {
            s.startsWith("jyu") -> "yu" + s.substring(3)
            s.startsWith("j") -> "y" + s.substring(1)
            s.startsWith("z") -> "j" + s.substring(1)
            s.startsWith("c") -> "ch" + s.substring(1)
            else -> s
        }
        s = s.replace("eoi", "eui").replace("eo", "eu").replace("oe", "eu")
        if (s.endsWith("aa")) s = s.dropLast(1)
        return s
    }

    private val initials = listOf("ng", "gw", "kw", "ch", "b", "p", "m", "f", "d", "t", "n", "l",
        "g", "k", "h", "w", "j", "s", "y")

    /** Splits a Yale syllable (without tone) into initial, vowel cluster and coda. */
    private fun splitYale(s: String): Triple<String, String, String>? {
        var initial = initials.firstOrNull { s.startsWith(it) && s.length > it.length } ?: ""
        // "yu" after an initial is a vowel ("syu"); a bare "y" before a vowel is the initial
        var rest = s.substring(initial.length)
        if (initial == "y" && rest.firstOrNull() !in setOf('a', 'e', 'i', 'o', 'u')) {
            initial = ""
            rest = s
        }
        val vowelEnd = rest.indexOfFirst { it !in "aeiouy" }.let { if (it < 0) rest.length else it }
        val vowels = rest.substring(0, vowelEnd)
        val coda = rest.substring(vowelEnd)
        if (vowels.isEmpty() || coda !in setOf("", "m", "n", "ng", "p", "t", "k")) return null
        return Triple(initial, vowels, coda)
    }

    /** "lü4" / "lv4" / "lu:4" -> "lǜ"; "ma5" -> "ma". */
    fun pinyinToMarked(syllable: String): String {
        val norm = syllable.lowercase().replace("u:", "ü").replace("v", "ü")
        val m = pinyinSyllable.matchEntire(norm) ?: return syllable
        val body = m.groupValues[1]
        val tone = m.groupValues[2].toInt()
        if (tone == 5) return body
        val idx = when {
            'a' in body -> body.indexOf('a')
            'e' in body -> body.indexOf('e')
            "ou" in body -> body.indexOf('o')
            else -> body.indexOfLast { it in "aeiouü" }
        }
        if (idx < 0) {
            // syllabic n, m, ng
            val first = body.indexOfFirst { it in "mn" }
            if (first < 0) return body
            return nfc(body.substring(0, first + 1) + toneMark(tone) + body.substring(first + 1))
        }
        return nfc(body.substring(0, idx + 1) + toneMark(tone) + body.substring(idx + 1))
    }

    private fun toneMark(tone: Int) = when (tone) {
        1 -> "̄"
        2 -> "́"
        3 -> "̌"
        4 -> "̀"
        else -> ""
    }

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    /** Drops tone digits: "nei5 hou2" -> "nei hou". */
    fun toneless(reading: String): String = reading.replace(Regex("[1-6]"), "")

    /** Mandarin syllables compared without tones and with ü spelt as v. */
    fun pinyinKey(reading: String): String =
        toneless(reading.lowercase().replace("u:", "v").replace("ü", "v")).trim()

    fun format(reading: String, style: Style): String {
        val syllables = reading.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val parts = when (style) {
            Style.JyutpingNumbers -> syllables
            Style.YaleMarks -> syllables.map(::jyutpingToYale)
            Style.YaleNumbers -> syllables.map(::jyutpingToYaleNumbered)
            Style.PinyinMarks -> syllables.map(::pinyinToMarked)
            Style.PinyinNumbers -> syllables.map { it.replace("u:", "ü").replace("v", "ü") }
        }
        return parts.joinToString(" ")
    }

    enum class Style { JyutpingNumbers, YaleMarks, YaleNumbers, PinyinMarks, PinyinNumbers }
}
