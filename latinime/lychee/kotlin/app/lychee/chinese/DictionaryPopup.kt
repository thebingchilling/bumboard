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
        onSpeak: ((String, Lang?) -> Unit)? = null, onType: () -> Unit,
    ) {
        present(keyboard, peek, onType) { c, body -> Content(ctx, c, prefs, body, onSpeak).fill(text, resolved) }
    }

    /**
     * A Japanese or Korean word's card: its reading (kana, or romanisation and Hanja), part of
     * speech, meanings and examples, from the installed dictionary packs.
     */
    fun showWord(
        keyboard: View, text: String, romanisation: String?, entries: List<app.lychee.dict.WordEntry>,
        examples: List<Pair<String, String>>, peek: Boolean, onSpeak: ((String, Lang?) -> Unit)?, onType: () -> Unit,
    ) {
        present(keyboard, peek, onType) { c, body ->
            val content = Content(ctx, c, DisplayPrefs(), body, onSpeak)
            content.heading(text, text)
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
        val onSpeak: ((String, Lang?) -> Unit)?,
    ) {

        fun fill(text: String, r: Resolved) {
            val main = r.headwords.firstOrNull()
            // headword: Traditional · Simplified, and tap to hear it
            val forms = if (main != null && !r.composed && main.traditional != main.simplified)
                "${main.traditional} · ${main.simplified}" else text
            heading(forms, null)
            // one official reading per language, each with its own listen button
            readingRows(r, main, text)
            if (main != null && !r.composed) facts(main)
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
                    readingRows(Resolved(r.lang, null, null, null, listOf(hw), false), hw, null)
                }
                senses(hw, r)
            }
        }

        /**
         * The word's reading in Cantonese and in Mandarin: only the one that fits (the one typed,
         * else the most common), in the romanisation and tone style chosen in Settings, each with
         * a listen button (YUE, PU) when [spoken] is given.
         */
        private fun readingRows(r: Resolved, hw: Headword?, spoken: String?) {
            val yue = (if (r.lang == Lang.Cantonese) r.reading else r.otherReading)
                ?: hw?.readings?.get(Lang.Cantonese)?.firstOrNull()
            val cmn = (if (r.lang == Lang.Mandarin) r.reading else r.otherReading)
                ?: hw?.readings?.get(Lang.Mandarin)?.firstOrNull()
            val rows = listOfNotNull(
                yue?.let { Triple(Lang.Cantonese, R.string.lychee_card_cantonese, it) },
                cmn?.let { Triple(Lang.Mandarin, R.string.lychee_card_mandarin, it) },
            ).let { if (r.lang == Lang.Mandarin) it.reversed() else it }
            for ((lang, label, reading) in rows) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(labelled(ctx.getString(label), Annotator.format(reading, lang, prefs)),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (spoken != null) onSpeak?.let { speak ->
                    row.addView(listen(if (lang == Lang.Cantonese) R.string.lychee_card_yue else R.string.lychee_card_pu) {
                        speak(spoken, lang)
                    })
                }
                body.addView(row)
            }
        }

        /** A listen button: the speaker and the language's short name, raised like a key. */
        private fun listen(label: Int, onClick: () -> Unit) = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(12), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(c.primaryContainer)
            }
            elevation = dp(2).toFloat()
            contentDescription = ctx.getString(R.string.lychee_card_listen)
            addView(android.widget.ImageView(ctx).apply {
                setImageResource(R.drawable.lychee_ic_volume)
                imageTintList = android.content.res.ColorStateList.valueOf(c.onPrimaryContainer)
            }, LinearLayout.LayoutParams(dp(18), dp(18)))
            addView(textView(ctx.getString(label), 13f, c.onPrimaryContainer).apply {
                typeface = app.lychee.fonts.LycheeFonts.ui(ctx, 600)
                setPadding(dp(4), 0, 0, 0)
            })
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply {
                marginStart = dp(8); topMargin = dp(4)
            }
            setOnClickListener { onClick() }
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
            if (spoken != null) onSpeak?.let { speak -> head.addView(speaker { speak(spoken, null) }) }
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
                    row.addView(speaker { speak(sentence, null) }.apply {
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

        /** The measure words (levels and parts of speech stay off the card). */
        private fun facts(hw: Headword) {
            val info = hw.info
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
            // the meaning alone: no reading, part of speech or register beside it
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
                Annotator.format(it, lang, prefs)
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
