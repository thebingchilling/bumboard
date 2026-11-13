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
 * The full dictionary entry for a candidate, opened by a long-press: a card always just above
 * the keyboard (in lychee_dictionary_panel), never over the keys or the candidates. Plain Views
 * in Material 3 colours.
 *
 * It opens condensed: a short card (the word, its readings and first meanings) that doesn't
 * scroll, its foot fading out when there is more; the ⌃ button between close and type grows it
 * to the full card (as tall as the entry, up to the room above), which scrolls, and ⌄ shrinks it
 * back. A short entry simply shows whole.
 */
class DictionaryPopup(
    private val ctx: Context,
    /** Outside the keyboard (Look up in Lychee): the card goes in here, at most [standaloneRoom] tall. */
    private val standalone: FrameLayout? = null,
    private val standaloneRoom: Int = 0,
    /** The card closed (✕, or a word typed). */
    private val onDismiss: () -> Unit = {},
    /** The right-hand button's icon and name, when it doesn't type the word. */
    private val actionIcon: Int = R.drawable.lychee_ic_input,
    private val actionLabel: Int = R.string.lychee_type_it,
) {
    companion object {
        /** What the card's speak buttons say Chinese in: "auto" (the keyboard's), "yue" or "cmn". */
        const val PREF_SPEAK_LANG = "lychee_card_speak_lang"
        /** The other script under the headword: "hyphens" (only what differs), "full" or "off". */
        const val PREF_OTHER_SCRIPT = "lychee_card_other_script"
        /** The card's largest height and width (dp): a popup, never the whole screen. */
        private const val MAX_CARD_DP = 420
        private const val MAX_CARD_WIDTH_DP = 440
    }

    private var host: FrameLayout? = null

    fun dismiss() {
        val shown = host != null
        host?.let {
            it.removeAllViews()
            if (standalone == null) it.visibility = View.GONE
        }
        host = null
        if (shown) onDismiss()
    }

    /**
     * Shows [resolved] for [text] above [keyboard] (the keyboard frame), never over the keys.
     * [peek]: no buttons, for a card that only shows while the candidate is held.
     */
    fun show(
        keyboard: View, text: String, resolved: Resolved, prefs: DisplayPrefs, peek: Boolean,
        onSpeak: ((String, Lang?) -> Unit)? = null,
        /** a Kanji or Hanja on the Japanese or Korean keyboard: its readings in that language */
        local: List<LocalReading>? = null,
        /** with [local]: whether the Chinese readings show too */
        showChinese: Boolean = true,
        /** with [local]: says a reading in that language */
        onSpeakLocal: ((String) -> Unit)? = null,
        onType: () -> Unit,
    ) {
        present(keyboard, peek, onType) { c, body, compact ->
            Content(ctx, c, prefs, body, onSpeak).apply {
                this.compact = compact
                this.local = local
                this.showChinese = local == null || showChinese
                this.onSpeakLocal = onSpeakLocal
            }.fill(text, Annotator.regional(resolved, prefs))
        }
    }

    /** One of a character's Japanese or Korean readings: its label, as shown, and what is said. */
    class LocalReading(val label: String, val shown: String, val say: String)

    /**
     * A Japanese or Korean word's card: its reading (kana, or romanisation and Hanja), part of
     * speech, meanings and examples, from the installed dictionary packs.
     */
    fun showWord(
        keyboard: View, text: String, romanisation: String?, entries: List<app.lychee.dict.WordEntry>,
        examples: List<Pair<String, String>>, peek: Boolean, onSpeak: ((String, Lang?) -> Unit)?,
        lang: String? = null,
        /** Says text in a given voice (English UK or US; null: the language's own). */
        onSpeakAs: ((String, java.util.Locale?) -> Unit)? = null,
        /** Look up in Lychee, on an English word: the word in Chinese, each one to tap. */
        translations: List<String> = emptyList(),
        onTranslation: (String) -> Unit = {},
        /** A word with Kanji or Hanja: its Chinese readings, shown under its own. */
        chinese: Resolved? = null,
        chinesePrefs: DisplayPrefs? = null,
        onType: () -> Unit,
    ) {
        present(keyboard, peek, onType) { c, body, compact ->
            val content = Content(ctx, c, chinesePrefs ?: DisplayPrefs(), body, onSpeak)
            content.compact = compact
            content.fontLang = lang
            // a single kanji: its KANJIDIC2 readings and meanings, briefly, under the heading
            val kanji = if (lang == "ja" && text.codePointCount(0, text.length) == 1 &&
                Character.UnicodeScript.of(text.codePointAt(0)) == Character.UnicodeScript.HAN
            ) app.lychee.dict.PackData.kanji(ctx, text) else null
            // each pronunciation has its own speak button; only a word with none keeps the
            // heading's
            val anyReading = entries.any { !it.reading.isNullOrBlank() }
            // (English: each UK and US pronunciation has its own speaker instead; Japanese and
            // Korean always keep the word's, as their reading rows can be left out)
            content.heading(text, if (anyReading && onSpeakAs != null && lang == "en") null else text)
            if (translations.isNotEmpty()) content.translations(translations, onTranslation)
            kanji?.let { k ->
                // kun readings with their okurigana in brackets: まな.ぶ → まな(ぶ)
                val kun = k.kunyomi.split('、').filter { it.isNotBlank() }
                    .joinToString("、") { r -> r.replace(Regex("\\.(.+)$"), "($1)") }
                if (k.onyomi.isNotBlank()) body.addView(content.labelled(ctx.getString(R.string.lychee_card_onyomi), k.onyomi))
                if (kun.isNotBlank()) body.addView(content.labelled(ctx.getString(R.string.lychee_card_kunyomi), kun))
                if (k.meanings.isNotBlank()) body.addView(content.textView(k.meanings, 15f, c.onSurface).apply {
                    if (compact) { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
                    setPadding(0, dp(4), 0, 0)
                })
                val facts = listOfNotNull(
                    k.grade?.let { g -> if (g in 1..6) ctx.getString(R.string.lychee_card_kanji_grade, g) else ctx.getString(R.string.lychee_card_kanji_joyo).takeIf { g == 8 } },
                    k.strokes?.let { n -> ctx.resources.getQuantityString(R.plurals.lychee_card_stroke_count, n, n) },
                ).joinToString(" · ")
                if (facts.isNotEmpty() && !compact) body.addView(content.textView(facts, 13f, c.onSurfaceVariant).apply {
                    setPadding(0, dp(2), 0, dp(4))
                })
            }
            romanisation?.let { body.addView(content.labelled(ctx.getString(R.string.lychee_card_romanisation), it)) }
            chinese?.let { content.chineseReadings(text, it) }
            // a word written alike but said differently by meaning (lead, wind, tear): each
            // pronunciation says itself
            val pronunciations = entries.mapNotNull { e -> e.reading?.takeIf { it.isNotBlank() } }.distinct()
            val heteronym = lang == "en" && pronunciations.size > 1
            // condensed: the first entry, its readings and first meanings only
            (if (compact) entries.take(1) else entries).forEachIndexed { i, e ->
                if (i > 0 || e.lemma != text) {
                    if (i > 0) body.addView(content.divider())
                    body.addView(content.textView(e.lemma, 22f, c.onSurface).apply {
                        app.lychee.fonts.LycheeFonts.forText(ctx, e.lemma, lang)?.let { typeface = it }
                    })
                }
                e.reading?.takeIf { it != e.lemma && it != romanisation }?.let { reading ->
                    val label = ctx.getString(if (lang == "en") R.string.lychee_card_pronunciation else R.string.lychee_card_reading)
                    // English UK and US apart, each in its own voice; Japanese says the kana
                    // reading itself; Korean the word
                    // (Settings > Pronunciation: both, or only the UK's or the US's)
                    val parts = if (lang == "en") reading.split(" · ").let { all ->
                        all.filter { app.lychee.speech.Pronunciation.showsEnglish(ctx, it) }.ifEmpty { all }
                    } else listOf(reading)
                    for (part in parts) {
                        val locale = when {
                            part.endsWith("(UK)") -> java.util.Locale.UK
                            part.endsWith("(US)") -> java.util.Locale.US
                            lang == "en" -> app.lychee.speech.Pronunciation.englishLocale(ctx)
                            else -> null
                        }
                        val button = onSpeakAs?.let { speak ->
                            content.speakButton {
                                val say = when {
                                    lang == "ja" -> reading
                                    // said as a word that sounds just like this pronunciation
                                    heteronym -> app.lychee.dict.PackData.homophone(ctx, part, e.lemma) ?: e.lemma
                                    else -> e.lemma
                                }
                                speak(say, locale)
                            }
                        }
                        body.addView(content.readingRow(content.labelled(label, part), button))
                    }
                }
                e.hanja?.let { body.addView(content.labelled("Hanja", it)) }
                e.partOfSpeech?.let { body.addView(content.labelled(ctx.getString(R.string.lychee_card_pos), it)) }
                e.senses.take(if (compact) Content.SUMMARY_SENSES else Content.MAX_SENSES)
                    .forEachIndexed { n, s -> content.plainSense(n + 1, s) }
            }
            // a word the dictionary doesn't have still gets its card: the word and its voice
            if (entries.isEmpty()) body.addView(content.textView(ctx.getString(R.string.lychee_card_no_definition), 14f, c.onSurfaceVariant).apply {
                setPadding(0, dp(8), 0, dp(4))
            })
            if (compact) return@present
            content.examples(examples) { sentence ->
                when (lang) {
                    "ja" -> app.lychee.core.SentenceRomanizer.japanese(sentence) { form ->
                        app.lychee.dict.PackData.words(ctx, "ja", form, 1).firstOrNull()?.let { it.reading ?: it.form }
                    }
                    "ko" -> app.lychee.core.SentenceRomanizer.korean(sentence)
                    else -> null
                }
            }
            if (lang == "ja" || lang == "ko") content.strokes(text)
        }
    }

    private fun present(keyboard: View, peek: Boolean, onType: () -> Unit, fill: (M3Colors, LinearLayout, Boolean) -> Unit) {
        host?.removeAllViews()
        host = null
        val panel = standalone ?: keyboard.rootView.findViewById<FrameLayout>(R.id.lychee_dictionary_panel) ?: return
        val parent = panel.parent as? View ?: return
        // the keyboard's top edge in the input view (which spans the screen), and the room
        // between it and the status bar
        val keyboardTop = if (standalone != null) 0 else IntArray(2).also { keyboard.getLocationInWindow(it) }[1] -
            IntArray(2).also { parent.getLocationInWindow(it) }[1]
        @Suppress("DEPRECATION")
        val statusBar = parent.rootWindowInsets?.stableInsetTop ?: dp(24)
        // a popup, not a sheet: at most about half the room above the keys (never the whole
        // screen), within limits that suit a phone
        val space = if (standalone != null) standaloneRoom else keyboardTop - statusBar - dp(12)
        if (space < dp(96)) return
        val room = if (standalone != null) space else minOf(space, maxOf(dp(220), (space * 0.6f).toInt()), dp(MAX_CARD_DP))
        // inside the keyboard it opens as a short summary (Look up in Lychee has the screen to itself)
        val startCompact = standalone == null
        val c = M3Colors.of(ctx)
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(2))
        }
        fill(c, body, startCompact)

        // its two buttons along the bottom, where the thumb is: close (X) on the left, type the
        // word on the right
        val actions = FrameLayout(ctx).apply { setPadding(dp(10), dp(4), dp(10), dp(10)) }
        val scroll = ScrollView(ctx).apply {
            addView(body)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        // the summary may take all the room above the keys, so it never scrolls
        val card = MaxHeightLayout(ctx, if (startCompact) space else room).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHigh)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), androidx.core.graphics.ColorUtils.setAlphaComponent(c.outlineVariant, 0x80))
            }
            elevation = dp(10).toFloat()
            clipToOutline = true
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(actions)
        }
        // the summary or the whole entry: ⌃ rebuilds the card as the full entry, growing up out
        // of the keyboard; ⌄ goes back to the summary
        var expanded = !startCompact
        val expand = cornerButton(R.drawable.ic_sym_keyboard_arrow_up, ctx.getString(R.string.lychee_card_more), c, filled = false) {}
        fun setExpanded(open: Boolean) {
            if (expanded == open) return
            expanded = open
            val from = card.height
            body.removeAllViews()
            fill(c, body, !open)
            scroll.scrollTo(0, 0)
            expand.setImageResource(if (open) R.drawable.ic_sym_keyboard_arrow_down else R.drawable.ic_sym_keyboard_arrow_up)
            expand.contentDescription = ctx.getString(if (open) R.string.lychee_card_less else R.string.lychee_card_more)
            // the full entry at least as tall as the summary was; the summary all the room it needs
            val limit = if (open) maxOf(room, from) else space
            if (!app.lychee.layout.LycheePanels.animate(ctx)) {
                card.maxHeight = limit
                card.requestLayout()
                return
            }
            // the card's bottom stays on the keyboard; its top travels
            body.alpha = 0f
            body.animate().alpha(1f).setDuration(app.lychee.layout.LycheeMotion.IN)
                .setInterpolator(app.lychee.layout.LycheeMotion.enterEasing).start()
            card.maxHeight = limit
            card.measure(View.MeasureSpec.makeMeasureSpec(card.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(card.maxHeight, View.MeasureSpec.AT_MOST))
            val to = card.measuredHeight
            android.animation.ValueAnimator.ofInt(from, to).apply {
                duration = app.lychee.layout.LycheeMotion.IN + 60
                interpolator = app.lychee.layout.LycheeMotion.enterEasing
                addUpdateListener {
                    card.maxHeight = it.animatedValue as Int
                    card.requestLayout()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        card.maxHeight = limit
                        card.requestLayout()
                    }
                })
                start()
            }
        }
        expand.setOnClickListener { setExpanded(!expanded) }
        if (peek) {
            actions.visibility = View.GONE
        } else {
            actions.addView(cornerButton(R.drawable.ic_sym_close, ctx.getString(R.string.lychee_close), c, filled = false) { dismiss() },
                FrameLayout.LayoutParams(dp(40), dp(40), Gravity.START or Gravity.BOTTOM))
            // inside the keyboard the candidate itself types the word: the right-hand button
            // grows the card instead; Look up in Lychee keeps its own action there
            if (standalone != null) {
                actions.addView(cornerButton(actionIcon, ctx.getString(actionLabel), c, filled = true) {
                dismiss()
                onType()
            }, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END or Gravity.BOTTOM))
            }
            if (startCompact) actions.addView(expand, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END or Gravity.BOTTOM))
            body.setPadding(body.paddingLeft, dp(12), body.paddingRight, dp(2))
        }
        // just above the keyboard, rising out of it
        // over the keys only: in one-handed mode the card keeps to the narrowed keyboard
        val keys = keyboard.rootView.findViewById<View>(R.id.keyboard_view)
        val keysLeft = keys?.let { k ->
            IntArray(2).also { k.getLocationInWindow(it) }[0] - IntArray(2).also { parent.getLocationInWindow(it) }[0]
        } ?: 0
        if (standalone == null) (panel.layoutParams as FrameLayout.LayoutParams).let { lp ->
            lp.gravity = Gravity.BOTTOM or Gravity.START
            lp.width = keys?.width?.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.leftMargin = keysLeft
            lp.bottomMargin = parent.height - keyboardTop
            panel.layoutParams = lp
        }
        // narrower than the keys, centred, floating a little above them
        val keysWidth = if (standalone != null) standalone.width.takeIf { it > 0 } ?: ctx.resources.displayMetrics.widthPixels
            else keys?.width?.takeIf { it > 0 } ?: parent.width
        val cardWidth = minOf(keysWidth - 2 * dp(16), dp(MAX_CARD_WIDTH_DP))
        panel.addView(card, FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
            setMargins(0, dp(12), 0, dp(10))
        })
        panel.visibility = View.VISIBLE
        panel.bringToFront()
        // grows up out of the keyboard like a popup, fading in (LycheeMotion, like every card)
        if (app.lychee.layout.LycheePanels.animate(ctx)) card.alpha = 0f
        card.post { app.lychee.layout.LycheeMotion.cardIn(card, card.width / 2f, card.height.toFloat()) }
        host = panel
    }

    /** A column that never grows taller than [maxHeight]. */
    private class MaxHeightLayout(ctx: Context, var maxHeight: Int) : LinearLayout(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
        }
    }

    /** A round icon button in a corner of the card. */
    private fun cornerButton(icon: Int, label: String, c: M3Colors, filled: Boolean, onClick: () -> Unit) =
        android.widget.ImageView(ctx).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(if (filled) c.onPrimary else c.onSurfaceVariant)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            contentDescription = label
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (filled) c.primary else c.surfaceContainer)
            }
            setOnClickListener { onClick() }
        }

    private fun button(label: String, c: M3Colors, filled: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = app.lychee.fonts.LycheeFonts.appFont(context, 500)
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
        /** The word's language ("ja", "ko"; null for Chinese), which picks the font. */
        var fontLang: String? = null
        /** The short summary: the word, its readings and first meanings, the essentials only. */
        var compact = false
        /** A Kanji or Hanja's Japanese or Korean readings, shown first. */
        var local: List<LocalReading>? = null
        /** Whether the Chinese readings (and Chinese examples) show. */
        var showChinese = true
        var onSpeakLocal: ((String) -> Unit)? = null

        @Suppress("DEPRECATION")
        private val settings = android.preference.PreferenceManager.getDefaultSharedPreferences(ctx)

        /**
         * The language the speak buttons use on a Chinese card: Cantonese or Mandarin, from
         * Settings (the keyboard's language by default), switched with the 粵/普 chip.
         */
        private var speakLang: Lang? = null
        private val speakChips = ArrayList<TextView>()

        fun fill(text: String, r: Resolved) {
            val main = r.headwords.firstOrNull()
            speakLang = when (settings.getString(PREF_SPEAK_LANG, "auto")) {
                "yue" -> Lang.Cantonese
                "cmn" -> Lang.Mandarin
                else -> if (r.lang == Lang.Mandarin) Lang.Mandarin else Lang.Cantonese
            }
            // headword: in the candidates' script, large; the other script smaller under it
            if (main != null && !r.composed && main.traditional != main.simplified) {
                val first = if (prefs.simplified) main.simplified else main.traditional
                val second = if (prefs.simplified) main.traditional else main.simplified
                heading(first, null, otherForm(first, second))
            } else {
                // a phrase (read piece by piece) still shows its other script under it
                heading(text, null, (Annotator.otherScript(text, r) ?: charByChar(text))?.let { otherForm(text, it) })
            }
            // both languages' readings, always
            spokenWord = text
            // a Kanji or Hanja: its Japanese or Korean readings first
            local?.forEach { lr ->
                body.addView(readingRow(labelled(lr.label, lr.shown), onSpeakLocal?.let { say -> smallSpeaker { say(lr.say) } }))
            }
            if (showChinese) readingRows(r, main, text)
            if (compact) {
                summary(r)
                return
            }
            if (main != null && !r.composed) facts(main)
            // the definitions first, then example sentences, then the stroke order
            if (r.composed) {
                // a candidate the dictionary has nothing for: its pieces, readings and meanings
                if (r.headwords.isNotEmpty()) {
                    section(ctx.getString(R.string.lychee_card_parts))
                    r.headwords.forEach { hw -> part(hw, r.lang) }
                }
            } else {
                r.headwords.forEachIndexed { i, hw ->
                    if (i > 0) {
                        body.addView(divider())
                        // each entry's word as large as the first's, its other script under it
                        val first = if (prefs.simplified) hw.simplified else hw.traditional
                        heading(first, null, otherForm(first, if (prefs.simplified) hw.traditional else hw.simplified))
                        if (showChinese) readingRows(Resolved(r.lang, null, null, null, listOf(hw), false), hw, null)
                    }
                    senses(hw, r)
                }
            }
            val lang = if (r.lang == Lang.Cantonese) "yue" else "cmn"
            val forms2 = listOfNotNull(text, main?.traditional, main?.simplified)
            val romanLang = if (r.lang == Lang.Mandarin) Lang.Mandarin else Lang.Cantonese
            if (showChinese) examples(app.lychee.dict.PackData.examples(ctx, lang, forms2)) { sentence ->
                Readings.annotator?.romanise(sentence, romanLang, prefs)
            }
            strokes(text)
        }

        /**
         * The summary's meanings: the first few of the keyboard's language (else any), one or two
         * lines each, and the measure word, enough to tell whether this is the word wanted.
         */
        private fun summary(r: Resolved) {
            if (r.composed) {
                // a phrase read piece by piece: each piece's meaning on one line
                r.headwords.take(SUMMARY_SENSES).forEach { hw ->
                    val form = if (prefs.simplified) hw.simplified else hw.traditional
                    val gloss = hw.senses.firstOrNull { it.partOfSpeech != "cl" }?.gloss?.let { Annotator.shorten(it, 40) }.orEmpty()
                    body.addView(textView("$form  $gloss", 14f, c.onSurface).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                }
                return
            }
            val hw = r.headwords.firstOrNull() ?: return
            val wanted = if (r.lang == Lang.Mandarin) Lang.Mandarin else Lang.Cantonese
            val all = hw.senses.filter { it.partOfSpeech != "cl" && !it.gloss.startsWith("variant of") && !it.gloss.startsWith("see ") }
            val list = (all.filter { it.lang == wanted } + all.filter { it.lang != wanted }).distinctBy { it.gloss.lowercase() }
            list.take(SUMMARY_SENSES).forEachIndexed { i, s -> plainSense(i + 1, s.gloss, lines = 2) }
            if (!showChinese) return
            val measure = hw.info.measureWordList()
            if (measure.isNotEmpty()) {
                val script = { t: String, s: String -> if (prefs.simplified) s else t }
                body.addView(labelled(ctx.getString(R.string.lychee_card_measure_word).trimEnd(':'),
                    measure.take(2).joinToString("  ·  ") { (t, s, _) -> script(t, s) }))
            }
        }

        /** [text] in the other script character by character, from the dictionary; null when the same. */
        private fun charByChar(text: String): String? {
            val db = Readings.db ?: return null
            val out = StringBuilder()
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val ch = String(Character.toChars(cp))
                val hw = if (Annotator.isHan(cp)) runCatching { db.lookup(ch) }.getOrNull()
                    ?.firstOrNull { it.traditional.length == ch.length && it.simplified.length == ch.length && (it.traditional == ch || it.simplified == ch) }
                else null
                out.append(when {
                    hw == null -> ch
                    hw.traditional == ch -> hw.simplified
                    else -> hw.traditional
                })
                i += Character.charCount(cp)
            }
            return out.toString().takeIf { it != text }
        }

        /**
         * The other script's form of the word, as chosen in Settings: in full, with hyphens for
         * the characters that are the same (學生 → 学-), or not at all; null when they match.
         */
        private fun otherForm(first: String, other: String): String? {
            if (first == other) return null
            return when (settings.getString(PREF_OTHER_SCRIPT, "hyphens")) {
                "off" -> null
                "full" -> other
                else -> {
                    val a = first.codePoints().toArray()
                    val b = other.codePoints().toArray()
                    if (a.size != b.size) other
                    else b.indices.joinToString("") { i -> if (a[i] == b[i]) "-" else String(Character.toChars(b[i])) }
                }
            }
        }

        /**
         * The word's reading in Cantonese and in Mandarin (the one typed, else the main one),
         * each with a 粵 or 普 button that says the word in that language; and, as the
         * dictionary has them, its other readings that carry other meanings (行 hang4 "row"
         * beside haang4 "walk"), each with the first of those meanings.
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
            // side by side, each with its speaker, to save room
            val pair = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            for ((lang, label, reading) in rows) {
                mainReading[lang] = norm(reading)
                // the main reading only; a reading with meanings of its own comes with them
                pair.addView(readingRow(labelled(ctx.getString(label), Annotator.format(reading, lang, prefs)),
                    readingSpeaker(hw, reading, lang, spoken)),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            }
            if (pair.childCount > 0) body.addView(pair)
        }

        /** A Japanese or Korean word's Chinese readings (Cantonese and Mandarin), under a heading. */
        fun chineseReadings(text: String, r: Resolved) {
            // a small, quiet group: one tinted line, 粵 and 普 with their readings, each a tap to hear
            val hw = r.headwords.firstOrNull()
            val yue = (if (r.lang == Lang.Cantonese) r.reading else r.otherReading) ?: hw?.readings?.get(Lang.Cantonese)?.firstOrNull()
            val cmn = (if (r.lang == Lang.Mandarin) r.reading else r.otherReading) ?: hw?.readings?.get(Lang.Mandarin)?.firstOrNull()
            if (yue == null && cmn == null) return
            val wordToSay = text
            val group = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(4), dp(6), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(c.surfaceContainer)
                }
            }
            for ((lang, mark, reading) in listOfNotNull(yue?.let { Triple(Lang.Cantonese, "粵", it) }, cmn?.let { Triple(Lang.Mandarin, "普", it) })) {
                group.addView(TextView(ctx).apply {
                    val sb = SpannableStringBuilder()
                    sb.append(mark, ForegroundColorSpan(c.onSurfaceVariant), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append(" ")
                    sb.append(Annotator.format(reading, lang, prefs), ForegroundColorSpan(c.primary), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    this.text = sb
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, dp(4), dp(12), dp(4))
                    onSpeak?.let { say -> setOnClickListener { say(wordToSay, lang) } }
                })
            }
            body.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6); bottomMargin = dp(2)
            })
            spokenWord = text
        }

        /** Each language's main reading on this card, for the meanings read another way. */
        private val mainReading = HashMap<Lang, String>()

        /**
         * A small speaker saying [reading] in [lang]: the word itself when that is how it is
         * usually read, otherwise spelt in characters only ever read that way.
         */
        private fun readingSpeaker(hw: Headword?, reading: String, lang: Lang, spoken: String?): View? {
            val say = onSpeak ?: return null
            val word = spoken ?: return null
            val usual = hw?.readings?.get(lang)?.firstOrNull()?.let { norm(it) == norm(reading) } ?: true
            return smallSpeaker { say(if (usual) word else sayAs(reading, lang) ?: word, lang) }
        }

        /** The word the card is for (what the speakers say). */
        var spokenWord: String? = null

        /** [reading] in characters only ever read that way, for the voice; null when there are none. */
        private fun sayAs(reading: String, lang: Lang): String? =
            runCatching { Readings.db?.sayAs(reading, lang, prefs.simplified) }.getOrNull()

        private fun norm(reading: String) = reading.trim().lowercase().replace(Regex("\\s+"), " ")

        /** A reading on its own line, with its speak button at the end. */
        fun readingRow(text: View, button: View?): View = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (button != null) addView(button)
            setPadding(0, dp(2), 0, dp(2))
        }

        /** A small speaker beside a reading: no label, it says the reading it sits next to. */
        fun smallSpeaker(onClick: () -> Unit): View = speaker(onClick).apply {
            setPadding(dp(7), dp(7), dp(7), dp(7))
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(8) }
        }

        /** The speak button, public for the word cards' readings. */
        fun speakButton(onClick: () -> Unit): View = smallSpeaker(onClick)

        /**
         * The word, large, with [other] (the other script's form) smaller under it, and the
         * card's speak button for [spoken]; on a Chinese card the 粵/普 chip beside it switches
         * which language every speak button uses.
         */
        fun heading(shown: String, spoken: String?, other: String? = null) {
            val head = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val words = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            words.addView(textView(shown, 38f, c.onSurface).apply {
                app.lychee.fonts.LycheeFonts.forText(ctx, shown, fontLang)?.let { typeface = it }
            })
            if (other != null) {
                // the other script's form: large too, a step smaller, the same colour
                words.addView(textView(other, 24f, c.onSurface).apply {
                    app.lychee.fonts.LycheeFonts.forText(ctx, shown, fontLang)?.let { typeface = it }
                    letterSpacing = 0.1f
                })
            }
            head.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (spoken != null) onSpeak?.let { speak ->
                if (speakLang != null) head.addView(speakChip())
                head.addView(speaker { speak(spoken, speakLang) })
            }
            body.addView(head)
        }

        /** 粵 or 普: the language the speak buttons use; a tap switches it. */
        private fun speakChip() = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            app.lychee.fonts.LycheeFonts.fixHeavy(this)
            setTextColor(c.onSecondaryContainer)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(c.secondaryContainer)
            }
            contentDescription = ctx.getString(R.string.lychee_card_speak_language)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(32)).apply { marginEnd = dp(8) }
            speakChips += this
            updateChip(this)
            setOnClickListener {
                speakLang = if (speakLang == Lang.Mandarin) Lang.Cantonese else Lang.Mandarin
                speakChips.forEach { updateChip(it) }
            }
        }

        private fun updateChip(chip: TextView) {
            chip.text = ChineseScript.ui(if (speakLang == Lang.Mandarin) "普" else "粵", ctx)
        }

        /**
         * The word in Chinese, under its heading: each translation a chip in the Chinese font, a tap
         * picks it ([onPick]).
         */
        fun translations(list: List<String>, onPick: (String) -> Unit) {
            section(ctx.getString(R.string.lychee_lookup_in_chinese))
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            list.forEach { t ->
                row.addView(textView(t, 22f, c.onSecondaryContainer).apply {
                    typeface = app.lychee.fonts.LycheeFonts.chinese(ctx) ?: Typeface.DEFAULT
                    gravity = Gravity.CENTER
                    setPadding(dp(14), dp(6), dp(14), dp(6))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(c.secondaryContainer)
                    }
                    setOnClickListener { onPick(t) }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = dp(8) })
            }
            body.addView(android.widget.HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            })
            body.addView(divider())
        }

        /** Stroke order and breakdown of each character (up to four), when the pack is installed. */
        fun strokes(text: String) {
            // Chinese characters, kana and Hangul (Japanese and Korean have their own order)
            val chars = text.codePoints().toArray().filter {
                Annotator.isHan(it) || it in 0x3040..0x30FF || it in 0xAC00..0xD7A3
            }.map { String(Character.toChars(it)) }.distinct().take(MAX_STROKE_CHARS)
            val found = chars.mapNotNull { app.lychee.dict.PackData.strokes(ctx, it, fontLang) }
            if (found.isEmpty()) return
            section(ctx.getString(R.string.lychee_card_strokes))
            // every character of a long word, four to a row
            // not another copy of the word: each character on a tinted copybook tile, written in
            // the accent colour over a faint copy, with its stroke count under it
            found.chunked(4).forEach { chunk ->
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                chunk.forEach { ch ->
                    val cell = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                    val tile = FrameLayout(ctx).apply {
                        background = GradientDrawable().apply {
                            cornerRadius = dp(14).toFloat()
                            setColor(c.surfaceContainer)
                        }
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        addView(app.lychee.dict.StrokeOrderView(ctx, ch, c.primary,
                            androidx.core.graphics.ColorUtils.setAlphaComponent(c.onSurface, 0x1C),
                            androidx.core.graphics.ColorUtils.setAlphaComponent(c.outline, 0x70),
                            current = c.primary),
                            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    }
                    cell.addView(tile, LinearLayout.LayoutParams(dp(72), dp(72)))
                    val count = ch.medians.size
                    cell.addView(textView(ctx.resources.getQuantityString(R.plurals.lychee_card_stroke_count, count, count), 11f, c.onSurfaceVariant).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, dp(3), 0, 0)
                    })
                    row.addView(cell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { marginEnd = dp(10); bottomMargin = dp(10) })
                }
                body.addView(row)
            }
        }

        /** Example sentences, each with its romanisation ([romanise]) and English under it. */
        fun examples(list: List<Pair<String, String>>, romanise: ((String) -> String?)? = null) {
            if (list.isEmpty()) return
            section(ctx.getString(R.string.lychee_card_examples))
            list.forEach { (sentence, english) ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                col.addView(textView(sentence, 16f, c.onSurface).apply {
                    app.lychee.fonts.LycheeFonts.forText(ctx, sentence, fontLang)?.let { typeface = it }
                    setPadding(0, dp(6), 0, 0)
                })
                romanise?.let { f -> runCatching { f(sentence) }.getOrNull() }?.takeIf { it.isNotBlank() && it != sentence }?.let { roman ->
                    col.addView(textView(roman, 14f, c.primary))
                }
                col.addView(textView(english, 13f, c.onSurfaceVariant))
                row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                onSpeak?.let { speak ->
                    row.addView(speaker { speak(sentence, speakLang) }.apply {
                        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                    })
                }
                body.addView(row)
            }
        }

        fun plainSense(n: Int, gloss: String, lines: Int = Int.MAX_VALUE) {
            senseBlock(n, gloss, emptyList(), lines)
        }

        /** One definition: its number in the accent colour, then the meaning and any notes under it. */
        private fun senseBlock(n: Int, gloss: String, notes: List<String>, lines: Int = Int.MAX_VALUE) {
            val block = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(if (compact) 2 else 4), 0, dp(if (compact) 2 else 4))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            // the number in the accent colour, the meaning beside it: no box around it
            block.addView(TextView(ctx).apply {
                text = "$n."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * TEXT_SCALE)
                typeface = app.lychee.fonts.LycheeFonts.appFont(ctx, 700)
                setTextColor(c.primary)
                includeFontPadding = false
            }, LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT))
            val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            column.addView(textView(gloss, 15f, c.onSurface).apply {
                setLineSpacing(0f, 1.1f)
                if (lines != Int.MAX_VALUE) {
                    maxLines = lines
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
            })
            notes.forEach { column.addView(textView(it, 13f, c.onSurfaceVariant).apply { setPadding(0, dp(4), 0, 0) }) }
            block.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(block)
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
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c.primaryContainer)
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener { onClick() }
        }

        private fun senses(hw: Headword, r: Resolved) {
            if (!showChinese) {
                // a Kanji or Hanja: its meanings together, without the Chinese languages' headings
                val list = hw.senses.filter { it.partOfSpeech != "cl" }.distinctBy { it.gloss.lowercase() }.take(MAX_SENSES)
                if (list.isEmpty()) return
                section(ctx.getString(R.string.lychee_card_meanings))
                list.forEachIndexed { i, s -> sense(i + 1, s) }
                return
            }
            val groups = listOf(
                Lang.Cantonese to R.string.lychee_card_cantonese,
                Lang.Mandarin to R.string.lychee_card_mandarin,
                Lang.Any to R.string.lychee_card_character,
            ).let { if (r.lang == Lang.Mandarin) listOf(it[1], it[0], it[2]) else it }
            for ((lang, title) in groups) {
                val list = hw.senses.filter { it.lang == lang && it.partOfSpeech != "cl" }.take(MAX_SENSES)
                if (list.isEmpty()) continue
                section(ctx.getString(title))
                // meanings read the main way first; those read another way under their reading
                val main = mainReading[lang]
                val groupsByReading = list.groupBy { s -> s.reading?.takeIf { it.isNotBlank() }?.let { norm(it) } ?: main }
                var n = 0
                val order = groupsByReading.keys.sortedBy { if (it == main || it == null) 0 else 1 }
                for (key in order) {
                    val senses = groupsByReading.getValue(key)
                    if (key != null && key != main && lang != Lang.Any) {
                        val reading = senses.first().reading!!
                        body.addView(readingRow(textView(Annotator.format(reading, lang, prefs), 15f, c.primary).apply {
                            setPadding(0, dp(10), 0, 0)
                        }, readingSpeaker(hw, reading, lang, spokenWord)))
                    }
                    senses.forEach { s -> sense(++n, s) }
                }
            }
        }

        private fun sense(n: Int, s: Sense) {
            // the meaning alone: no reading, part of speech or register beside it
            senseBlock(n, s.gloss, listOfNotNull(
                s.written?.takeIf { it.isNotBlank() }?.let { "${ctx.getString(R.string.lychee_card_written)} $it" },
                s.vernacular?.takeIf { it.isNotBlank() }?.let { "${ctx.getString(R.string.lychee_card_vernacular)} $it" },
            ))
        }

        /** One character or word of a composed candidate: both languages' readings, and its meaning. */
        private fun part(hw: Headword, lang: Lang) {
            val langs = if (lang == Lang.Mandarin) listOf(Lang.Mandarin, Lang.Cantonese) else listOf(Lang.Cantonese, Lang.Mandarin)
            val readings = langs.mapNotNull { l -> hw.readings[l]?.firstOrNull()?.let { Annotator.format(it, l, prefs) } }
                .joinToString("  ·  ")
            val gloss = hw.senses.firstOrNull { it.partOfSpeech != "cl" }?.gloss?.let { Annotator.shorten(it, 80) } ?: ""
            val form = if (prefs.simplified) hw.simplified else hw.traditional
            // the part large on the left; its pronunciation in the accent colour, and under it
            // the meaning on a line of its own (no dash between them)
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(textView(form, 28f, c.onSurface).apply {
                typeface = app.lychee.fonts.LycheeFonts.forText(ctx, form, fontLang) ?: Typeface.DEFAULT
                minWidth = dp(48)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = dp(14) })
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            if (readings.isNotEmpty()) col.addView(textView(readings, 15f, c.primary).apply {
                typeface = app.lychee.fonts.LycheeFonts.appFont(context, 600)
            })
            if (gloss.isNotEmpty()) col.addView(textView(gloss, 14f, c.onSurfaceVariant).apply {
                setPadding(0, dp(1), 0, 0)
            })
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(row)
        }

        private fun section(title: String) {
            body.addView(textView(title, 14f, c.primary).apply {
                typeface = app.lychee.fonts.LycheeFonts.appFont(context, 500)
                setPadding(0, dp(12), 0, dp(2))
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
            // everything on the card a step smaller than a page's text; the summary smaller still
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * TEXT_SCALE)
            setTextColor(color)
        }

        private fun dp(v: Int) = (ctx.resources.displayMetrics.density * v).toInt()

        companion object {
            const val MAX_SENSES = 12
            /** Meanings in the summary. */
            const val SUMMARY_SENSES = 3
            /** Every text on the card a step smaller than a page's (summary and full alike). */
            const val TEXT_SCALE = 0.86f
            /** Characters whose stroke order a card shows (a long word wraps onto more rows). */
            const val MAX_STROKE_CHARS = 16
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
