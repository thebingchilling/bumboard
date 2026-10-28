package app.lychee.chinese

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import app.lychee.core.Annotator
import app.lychee.core.CantoneseRomanization
import app.lychee.core.DisplayPrefs
import app.lychee.core.Headword
import app.lychee.core.Lang
import app.lychee.core.Resolved
import app.lychee.core.Romanization
import app.lychee.core.Sense
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * The full dictionary entry for a candidate, opened by a long-press: part of the keyboard,
 * it opens out over the keys (in lychee_dictionary_panel) and closes back into them. Plain
 * Views in Material 3 colours.
 */
class DictionaryPopup(private val ctx: Context) {
    private var host: FrameLayout? = null

    fun dismiss() {
        host?.let {
            it.removeAllViews()
            it.visibility = View.GONE
        }
        host = null
    }

    /**
     * Shows [resolved] for [text] above [keyboard] (the keyboard frame), never over the keys.
     * [peek]: no buttons, for a card that only shows while the candidate is held.
     */
    fun show(
        keyboard: View, text: String, resolved: Resolved, prefs: DisplayPrefs, peek: Boolean,
        onSpeak: ((String) -> Unit)? = null, onType: () -> Unit,
    ) {
        present(keyboard, peek, onType) { c, body -> Content(ctx, c, prefs, body, onSpeak).fill(text, resolved) }
    }

    /**
     * A Japanese or Korean word's card: its reading (kana, or romanisation and Hanja), part of
     * speech, meanings and examples, from the installed dictionary packs.
     */
    fun showWord(
        keyboard: View, text: String, romanisation: String?, entries: List<app.lychee.dict.WordEntry>,
        examples: List<Pair<String, String>>, peek: Boolean, onSpeak: ((String) -> Unit)?, onType: () -> Unit,
    ) {
        present(keyboard, peek, onType) { c, body ->
            val content = Content(ctx, c, DisplayPrefs(), body, onSpeak)
            content.heading(text, null)
            romanisation?.let { body.addView(content.labelled(ctx.getString(R.string.lychee_card_romanisation), it)) }
            entries.forEachIndexed { i, e ->
                if (i > 0 || e.lemma != text) {
                    if (i > 0) body.addView(content.divider())
                    body.addView(content.textView(e.lemma, 22f, c.onSurface))
                }
                e.reading?.takeIf { it != e.lemma && it != romanisation }?.let {
                    body.addView(content.labelled(ctx.getString(R.string.lychee_card_reading), it))
                }
                e.hanja?.let { body.addView(content.labelled("Hanja", it)) }
                e.partOfSpeech?.let { body.addView(content.labelled(ctx.getString(R.string.lychee_card_pos), it)) }
                e.senses.take(Content.MAX_SENSES).forEachIndexed { n, s -> content.plainSense(n + 1, s) }
            }
            content.examples(examples)
        }
    }

    private fun present(keyboard: View, peek: Boolean, onType: () -> Unit, fill: (M3Colors, LinearLayout) -> Unit) {
        dismiss()
        val panel = keyboard.rootView.findViewById<FrameLayout>(R.id.lychee_dictionary_panel) ?: return
        val parent = panel.parent as? View ?: return
        val c = M3Colors.of(ctx)
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(4))
        }
        fill(c, body)

        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(12), dp(6), dp(12), dp(10))
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHigh)
                cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
            }
            addView(ScrollView(ctx).apply { addView(body) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(actions)
        }
        if (peek) {
            actions.visibility = View.GONE
        } else {
            actions.addView(button(ctx.getString(R.string.lychee_close), c, filled = false) { dismiss() })
            actions.addView(button(ctx.getString(R.string.lychee_type_it), c, filled = true) {
                dismiss()
                onType()
            })
        }
        // over the keys, opening up out of them
        app.lychee.layout.LycheePanels.cover(panel, parent)
        panel.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        panel.visibility = View.VISIBLE
        panel.bringToFront()
        panel.pivotY = panel.layoutParams.height.toFloat()
        panel.scaleY = 0.6f
        panel.alpha = 0f
        panel.animate().scaleY(1f).alpha(1f).setDuration(140).start()
        host = panel
    }

    /** A column that never grows taller than [maxHeight]. */
    private class MaxHeightLayout(ctx: Context, private val maxHeight: Int) : LinearLayout(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
        }
    }

    private fun button(label: String, c: M3Colors, filled: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
        setTextColor(if (filled) c.onPrimary else c.primary)
        setPadding(dp(24), dp(10), dp(24), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            if (filled) setColor(c.primary) else setStroke(dp(1), c.outline)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = (ctx.resources.displayMetrics.density * v).toInt()

    /** Fills the scrolling body with the entry. */
    private class Content(
        val ctx: Context, val c: M3Colors, val prefs: DisplayPrefs, val body: LinearLayout,
        val onSpeak: ((String) -> Unit)?,
    ) {

        fun fill(text: String, r: Resolved) {
            val main = r.headwords.firstOrNull()
            // headword: Traditional · Simplified, and tap to hear it
            val forms = if (main != null && !r.composed && main.traditional != main.simplified)
                "${main.traditional} · ${main.simplified}" else text
            heading(forms, text)
            readings(r, main).forEach { (label, value) -> body.addView(labelled(label, value)) }
            if (main != null && !r.composed) facts(main, r.lang)
            strokes(text)
            val lang = if (r.lang == Lang.Cantonese) "yue" else "cmn"
            val forms2 = listOfNotNull(text, main?.traditional, main?.simplified)
            examples(app.lychee.dict.PackData.examples(ctx, lang, forms2))

            if (r.composed) {
                section(ctx.getString(R.string.lychee_card_parts))
                r.headwords.forEach { hw -> part(hw, r.lang) }
                return
            }
            r.headwords.forEachIndexed { i, hw ->
                if (i > 0) {
                    body.addView(divider())
                    body.addView(textView("${hw.traditional} · ${hw.simplified}", 22f, c.onSurface).apply {
                        app.lychee.fonts.LycheeFonts.chinese(ctx)?.let { typeface = it }
                    })
                    readings(Resolved(r.lang, null, null, null, listOf(hw), false), hw)
                        .forEach { (label, value) -> body.addView(labelled(label, value)) }
                }
                senses(hw, r)
            }
        }

        /** All readings of the word in both languages, the matched one first. */
        private fun readings(r: Resolved, hw: Headword?): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            val yue = buildList {
                if (r.lang == Lang.Cantonese) r.reading?.let(::add)
                if (r.lang == Lang.Mandarin) r.otherReading?.let(::add)
                hw?.readings?.get(Lang.Cantonese)?.forEach { if (it !in this) add(it) }
            }.take(3)
            val cmn = buildList {
                if (r.lang == Lang.Mandarin) r.reading?.let(::add)
                if (r.lang == Lang.Cantonese) r.otherReading?.let(::add)
                hw?.readings?.get(Lang.Mandarin)?.forEach { if (it !in this) add(it) }
            }.take(3)
            if (yue.isNotEmpty()) {
                // only the romanisation chosen in Settings, never both
                out += if (prefs.cantonese == CantoneseRomanization.Yale) {
                    "Yale" to yue.joinToString("  ·  ") { Annotator.yale(it, prefs.copy(yaleToneMarks = true)) }
                } else {
                    "Jyutping" to yue.joinToString("  ·  ")
                }
            }
            if (cmn.isNotEmpty()) {
                out += "Pinyin" to cmn.joinToString("  ·  ") {
                    Romanization.format(it, Romanization.Style.PinyinMarks)
                }
            }
            return out
        }

        /** The word, large, with a button to hear [spoken]. */
        fun heading(shown: String, spoken: String?) {
            val head = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(textView(shown, 34f, c.onSurface).apply {
                app.lychee.fonts.LycheeFonts.chinese(ctx)?.let { typeface = it }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            onSpeak?.let { speak -> head.addView(speaker { speak(spoken ?: shown) }) }
            body.addView(head)
        }

        /** Stroke order and breakdown of each character (up to four), when the pack is installed. */
        private fun strokes(text: String) {
            val chars = text.codePoints().toArray().filter { Annotator.isHan(it) }
                .map { String(Character.toChars(it)) }.distinct().take(4)
            val found = chars.mapNotNull { app.lychee.dict.PackData.strokes(ctx, it) }
            if (found.isEmpty()) return
            section(ctx.getString(R.string.lychee_card_strokes))
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            found.forEach { ch ->
                row.addView(app.lychee.dict.StrokeOrderView(ctx, ch, c.onSurface,
                    androidx.core.graphics.ColorUtils.setAlphaComponent(c.onSurface, 0x30),
                    androidx.core.graphics.ColorUtils.setAlphaComponent(c.outline, 0x80)),
                    LinearLayout.LayoutParams(dp(76), dp(76)).apply { marginEnd = dp(8) })
            }
            body.addView(row)
            section(ctx.getString(R.string.lychee_card_breakdown))
            found.forEach { ch ->
                val parts = listOfNotNull(
                    ch.decomposition?.takeIf { it.isNotBlank() && it != "？" },
                    ch.radical?.let { "${ctx.getString(R.string.lychee_card_radical)} $it" },
                ).joinToString("  ·  ")
                val sb = SpannableStringBuilder()
                sb.append(ch.char, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (parts.isNotEmpty()) sb.append("   ").append(parts)
                body.addView(textView(sb, 16f, c.onSurface).apply { setPadding(0, dp(4), 0, 0) })
                (ch.hint ?: ch.definition)?.let { body.addView(note(it)) }
            }
        }

        fun examples(list: List<Pair<String, String>>) {
            if (list.isEmpty()) return
            section(ctx.getString(R.string.lychee_card_examples))
            list.forEach { (sentence, english) ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                col.addView(textView(sentence, 16f, c.onSurface).apply {
                    app.lychee.fonts.LycheeFonts.chinese(ctx)?.let { typeface = it }
                    setPadding(0, dp(6), 0, 0)
                })
                col.addView(textView(english, 13f, c.onSurfaceVariant))
                row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                onSpeak?.let { speak ->
                    row.addView(speaker { speak(sentence) }.apply {
                        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                    })
                }
                body.addView(row)
            }
        }

        fun plainSense(n: Int, gloss: String) {
            val sb = SpannableStringBuilder()
            sb.append("$n  ", ForegroundColorSpan(c.onSurfaceVariant), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(gloss)
            body.addView(textView(sb, 15f, c.onSurface).apply { setPadding(0, dp(4), 0, dp(2)) })
        }

        /** Level, register and part of speech on one line; then the measure words. */
        private fun facts(hw: Headword, lang: Lang) {
            val info = hw.info
            val level = listOfNotNull(
                info.hsk?.let { "HSK $it" },
                when (info.band) {
                    1 -> ctx.getString(R.string.lychee_badge_common)
                    2 -> ctx.getString(R.string.lychee_badge_everyday)
                    3 -> ctx.getString(R.string.lychee_badge_advanced)
                    else -> null
                }?.takeIf { lang == Lang.Cantonese || info.hsk == null },
                when (info.register) {
                    "col" -> ctx.getString(R.string.lychee_badge_colloquial)
                    "wri" -> ctx.getString(R.string.lychee_badge_written)
                    else -> null
                },
                info.partOfSpeech?.let { app.lychee.core.WordInfo.PARTS_OF_SPEECH[it] },
            )
            if (level.isNotEmpty()) body.addView(labelled(ctx.getString(R.string.lychee_card_level), level.joinToString(" · ")))
            val measure = info.measureWordList()
            if (measure.isNotEmpty()) {
                val script = { t: String, s: String -> if (prefs.simplified) s else t }
                body.addView(labelled(
                    ctx.getString(R.string.lychee_card_measure_word).trimEnd(':'),
                    measure.joinToString("  ·  ") { (t, s, py) ->
                        "${script(t, s)} ${Romanization.format(py, Romanization.Style.PinyinMarks)}"
                    },
                ))
            }
        }

        private fun speaker(onClick: () -> Unit) = android.widget.ImageView(ctx).apply {
            setImageResource(R.drawable.lychee_ic_volume)
            imageTintList = android.content.res.ColorStateList.valueOf(c.onPrimaryContainer)
            contentDescription = ctx.getString(R.string.lychee_card_listen)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c.primaryContainer)
            }
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { onClick() }
        }

        private fun senses(hw: Headword, r: Resolved) {
            val groups = listOf(
                Lang.Cantonese to R.string.lychee_card_cantonese,
                Lang.Mandarin to R.string.lychee_card_mandarin,
                Lang.Any to R.string.lychee_card_character,
            ).let { if (r.lang == Lang.Mandarin) listOf(it[1], it[0], it[2]) else it }
            for ((lang, title) in groups) {
                val list = hw.senses.filter { it.lang == lang && it.partOfSpeech != "cl" }
                if (list.isEmpty()) continue
                section(ctx.getString(title))
                list.take(MAX_SENSES).forEachIndexed { i, s -> sense(i + 1, s) }
            }
        }

        private fun sense(n: Int, s: Sense) {
            val muted = c.onSurfaceVariant
            val sb = SpannableStringBuilder()
            sb.append("$n  ", ForegroundColorSpan(muted), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(s.gloss)
            val tags = listOfNotNull(
                s.partOfSpeech?.let { POS[it] ?: it },
                s.register?.let { REGISTERS[it] ?: it },
                s.reading?.let {
                    if (s.lang == Lang.Mandarin) Romanization.format(it, Romanization.Style.PinyinMarks) else it
                },
            ).filter { it.isNotBlank() }
            if (tags.isNotEmpty()) {
                sb.append("   ")
                val start = sb.length
                sb.append(tags.joinToString(" · "))
                sb.setSpan(ForegroundColorSpan(muted), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            body.addView(textView(sb, 15f, c.onSurface).apply { setPadding(0, dp(4), 0, dp(2)) })
            s.written?.takeIf { it.isNotBlank() }?.let {
                body.addView(note("${ctx.getString(R.string.lychee_card_written)} $it"))
            }
            s.vernacular?.takeIf { it.isNotBlank() }?.let {
                body.addView(note("${ctx.getString(R.string.lychee_card_vernacular)} $it"))
            }
        }

        private fun part(hw: Headword, lang: Lang) {
            val reading = hw.readings[lang]?.firstOrNull()?.let {
                if (lang == Lang.Cantonese) it else Romanization.format(it, Romanization.Style.PinyinMarks)
            } ?: ""
            val gloss = hw.senses.firstOrNull { it.partOfSpeech != "cl" }?.gloss?.let { Annotator.shorten(it, 80) } ?: ""
            body.addView(textView("${hw.traditional}  $reading  —  $gloss", 15f, c.onSurface).apply {
                setPadding(0, dp(4), 0, dp(2))
            })
        }

        private fun section(title: String) {
            body.addView(textView(title, 14f, c.primary).apply {
                typeface = app.lychee.fonts.LycheeFonts.ui(context, 500)
                setPadding(0, dp(16), 0, dp(4))
            })
        }

        fun labelled(label: String, value: String): TextView {
            val sb = SpannableStringBuilder()
            sb.append(label, ForegroundColorSpan(c.onSurfaceVariant), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("   ")
            val start = sb.length
            sb.append(value)
            sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return textView(sb, 16f, c.onSurface).apply { setPadding(0, dp(2), 0, 0) }
        }

        private fun note(t: String) = textView(t, 13f, c.onSurfaceVariant).apply {
            setPadding(dp(20), 0, 0, dp(2))
        }

        fun divider() = View(ctx).apply {
            setBackgroundColor(c.outline)
            alpha = 0.4f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(14); bottomMargin = dp(10)
            }
        }

        fun textView(t: CharSequence, sp: Float, color: Int) = TextView(ctx).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
        }

        private fun dp(v: Int) = (ctx.resources.displayMetrics.density * v).toInt()

        companion object {
            const val MAX_SENSES = 12
            // TypeDuck codes
            val POS = mapOf(
                "n" to "noun", "v" to "verb", "adj" to "adjective", "adv" to "adverb",
                "morph" to "morpheme", "mw" to "measure word", "part" to "particle", "oth" to "other",
                "x" to "", "pron" to "pronoun", "prep" to "preposition", "conj" to "conjunction",
                "num" to "number", "intj" to "interjection", "aff" to "affix",
            )
            val REGISTERS = mapOf(
                "wri" to "written", "ver" to "vernacular", "for" to "formal", "lzh" to "classical",
                "yue" to "Cantonese", "cmn" to "Mandarin",
            )
        }
    }
}
