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
        host?.let { h ->
            // it shrinks back into the keyboard as it fades, like every card
            val card = h.getChildAt(0)
            val finish = Runnable {
                if (host === h) return@Runnable  // a new card has taken the panel meanwhile
                h.removeAllViews()
                if (standalone == null) h.visibility = View.GONE
            }
            if (card != null && standalone == null) app.lychee.layout.LycheeMotion.cardOut(card, finish) else finish.run()
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

    /**
     * One Kanji or Hanja of a Japanese or Korean word: its readings in that language (kana or
     * Hangul with their romanisation), what its speaker says, and its meaning.
     */
    class CharReading(
        val ch: String, val rows: List<Pair<String?, List<Pair<String, String>>>>, val say: String, val meaning: String?,
        /** A kanji's form: its old (traditional) form where Japan simplified it, or Japan-made. */
        val note: String? = null,
    )

    /**
     * One kind of a character's Japanese or Korean readings: its label, as shown, and what is
     * said; and each reading on its own ([items]: the kana or Hangul, its romanisation, what its
     * speaker says), so every reading, the rarer ones too, has a speaker.
     */
    class LocalReading(
        val label: String, val shown: String, val say: String,
        val items: List<Triple<String, String, String>> = emptyList(),
    )

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
        /** The characters [chinese] was read from (a Korean word's Hanja), compared with Chinese's. */
        chineseForm: String? = null,
        /** Each Kanji or Hanja of the word with its Japanese or Korean readings. */
        characters: List<CharReading> = emptyList(),
        /** The heading over [characters]: Kanji or Hanja. */
        charactersTitle: String? = null,
        /** A Japanese word's old (traditional) form, where Japan simplified any of its kanji. */
        oldForm: String? = null,
        onType: () -> Unit,
    ) {
        present(keyboard, peek, onType) { c, body, compact ->
            val content = Content(ctx, c, chinesePrefs ?: DisplayPrefs(), body, onSpeak)
            content.compact = compact
            content.fontLang = lang
            // each pronunciation has its own speak button; only a word with none keeps the
            // heading's
            val anyReading = entries.any { !it.reading.isNullOrBlank() }
            // (English: each UK and US pronunciation has its own speaker instead; Japanese and
            // Korean always keep the word's, as their reading rows can be left out)
            content.heading(text, if (anyReading && onSpeakAs != null && lang == "en") null else text,
                strokesOf = text.takeIf { lang == "ja" || lang == "ko" })
            if (translations.isNotEmpty()) content.translations(translations, onTranslation)
            // the reading under the word, as one quiet line (no label): the kana and romaji, or
            // the romanisation and the Hanja
            val hanja = entries.firstOrNull()?.hanja?.takeIf { lang == "ko" && it.isNotBlank() }
            // (a Korean word's Hanja are in its Hanja section, as a Japanese word's Kanji are)
            val subtitle = romanisation.orEmpty()
            if (subtitle.isNotEmpty()) body.addView(content.textView(subtitle, 16f, c.primary).apply {
                typeface = app.lychee.fonts.LycheeFonts.appFont(context, 500)
                setPadding(0, 0, 0, dp(4))
            })
            // the other way of writing it: a Korean word's Hanja (the alternative to its
            // Hangul), a Japanese word's old form (traditional kanji)
            val alternative = when {
                hanja != null && hanja != text -> ctx.getString(R.string.lychee_card_hanja) to hanja
                lang == "ja" && oldForm != null && oldForm != text -> ctx.getString(R.string.lychee_card_old_form) to oldForm
                else -> null
            }
            alternative?.let { (label, form) ->
                body.addView(content.labelled(label, form).apply {
                    textLocale = if (lang == "ko") java.util.Locale.KOREAN else java.util.Locale.TRADITIONAL_CHINESE
                    setPadding(0, 0, 0, dp(4))
                })
            }
            // the stroke order straight after the reading
            if (lang == "ja" || lang == "ko") content.strokes(text)
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
                // a reading already in the line under the word is not shown again
                e.reading?.takeIf { it != e.lemma && romanisation?.contains(it) != true }?.let { reading ->
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
                e.hanja?.takeIf { it != hanja }?.let { body.addView(content.textView(it, 15f, c.onSurfaceVariant)) }
                // the part of speech small, over the meanings, rather than a labelled row
                e.partOfSpeech?.takeIf { it.isNotBlank() }?.let { pos ->
                    body.addView(content.textView(pos, 12f, c.onSurfaceVariant).apply {
                        setTypeface(typeface, Typeface.ITALIC)
                        setPadding(0, dp(4), 0, dp(2))
                    })
                }
                e.senses.take(if (compact) Content.SUMMARY_SENSES else Content.MAX_SENSES)
                    .forEachIndexed { n, s -> content.plainSense(n + 1, s) }
            }
            // a word the dictionary doesn't have still gets its card: the word and its voice
            if (entries.isEmpty()) body.addView(content.textView(ctx.getString(R.string.lychee_card_no_definition), 14f, c.onSurfaceVariant).apply {
                setPadding(0, dp(8), 0, dp(4))
            })
            if (compact) return@present
            // after the meanings: each Kanji or Hanja with its readings, then the word in Chinese
            if (characters.isNotEmpty()) content.characterReadings(charactersTitle ?: "", characters)
            chinese?.let { content.chineseBox(chineseForm ?: text, it) }
            content.examples(examples) { sentence ->
                when (lang) {
                    "ja" -> app.lychee.core.SentenceRomanizer.japanese(sentence) { form ->
                        app.lychee.dict.PackData.words(ctx, "ja", form, 1).firstOrNull()?.let { it.reading ?: it.form }
                    }
                    "ko" -> app.lychee.core.SentenceRomanizer.korean(sentence)
                    else -> null
                }
            }
        }
    }

    private fun present(keyboard: View, peek: Boolean, onType: () -> Unit, fill: (M3Colors, LinearLayout, Boolean) -> Unit) {
        host?.removeAllViews()
        host = null
        val panel = standalone ?: keyboard.rootView.findViewById<FrameLayout>(R.id.lychee_dictionary_panel) ?: return
        // a card still fading out from before goes at once
        panel.animate().cancel()
        panel.removeAllViews()
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
            // raised well clear of the app behind it: a deep, dark shadow and a clear outline
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHigh)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), androidx.core.graphics.ColorUtils.setAlphaComponent(c.outline, 0xA0))
            }
            elevation = dp(18).toFloat()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                outlineSpotShadowColor = android.graphics.Color.BLACK
                outlineAmbientShadowColor = android.graphics.Color.BLACK
            }
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
            val animate = app.lychee.layout.LycheePanels.animate(ctx)
            // hold the card at its size before the new content lays out, so nothing jumps
            if (animate) card.fixedHeight = from
            body.removeAllViews()
            fill(c, body, !open)
            scroll.scrollTo(0, 0)
            // the arrow turns over: up to grow, down to fold back
            expand.contentDescription = ctx.getString(if (open) R.string.lychee_card_less else R.string.lychee_card_more)
            // the full entry at least as tall as the summary was; the summary all the room it needs
            val limit = if (open) maxOf(room, from) else space
            card.maxHeight = limit
            if (!animate) {
                expand.rotation = if (open) 180f else 0f
                card.requestLayout()
                return
            }
            expand.animate().rotation(if (open) 180f else 0f).setDuration(app.lychee.layout.LycheeMotion.IN)
                .setInterpolator(app.lychee.layout.LycheeMotion.enterEasing).start()
            // the height the card will settle at
            card.fixedHeight = -1
            card.measure(View.MeasureSpec.makeMeasureSpec(card.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST))
            val to = card.measuredHeight
            card.fixedHeight = from
            card.requestLayout()
            // the card's bottom stays on the keyboard; its top travels up (or back down) while
            // the new content fades in
            body.alpha = 0.2f
            body.animate().alpha(1f).setDuration(app.lychee.layout.LycheeMotion.IN + 80)
                .setInterpolator(app.lychee.layout.LycheeMotion.enterEasing).start()
            android.animation.ValueAnimator.ofInt(from, to).apply {
                duration = app.lychee.layout.LycheeMotion.IN + 80
                interpolator = app.lychee.layout.LycheeMotion.enterEasing
                addUpdateListener {
                    card.fixedHeight = it.animatedValue as Int
                    card.requestLayout()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        card.fixedHeight = -1
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
            setMargins(0, dp(20), 0, dp(10))
        })
        panel.clipChildren = false
        panel.clipToPadding = false
        panel.visibility = View.VISIBLE
        panel.bringToFront()
        // grows up out of the keyboard like a popup, fading in (LycheeMotion, like every card)
        if (app.lychee.layout.LycheePanels.animate(ctx)) card.alpha = 0f
        card.post { app.lychee.layout.LycheeMotion.cardIn(card, card.width / 2f, card.height.toFloat()) }
        host = panel
    }

    /** Children left to right, wrapping onto new lines, [gap] apart both ways. */
    private class FlowRow(ctx: Context, val gap: Int) : ViewGroup(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec).takeIf { MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED }
                ?: Int.MAX_VALUE
            var x = 0
            var y = 0
            var line = 0
            var widest = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == GONE) continue
                child.measure(
                    if (width == Int.MAX_VALUE) MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    else MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                )
                if (x > 0 && x + child.measuredWidth > width) {
                    x = 0
                    y += line + gap
                    line = 0
                }
                x += child.measuredWidth + gap
                widest = maxOf(widest, x - gap)
                line = maxOf(line, child.measuredHeight)
            }
            setMeasuredDimension(if (width == Int.MAX_VALUE) widest else width, y + line)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val width = r - l
            var x = 0
            var y = 0
            var line = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == GONE) continue
                if (x > 0 && x + child.measuredWidth > width) {
                    x = 0
                    y += line + gap
                    line = 0
                }
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth + gap
                line = maxOf(line, child.measuredHeight)
            }
        }
    }

    /**
     * A column that never grows taller than [maxHeight]; while it grows or folds, exactly
     * [fixedHeight] tall (-1: as tall as its content).
     */
    private class MaxHeightLayout(ctx: Context, var maxHeight: Int) : LinearLayout(ctx) {
        var fixedHeight = -1
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val spec = if (fixedHeight >= 0) MeasureSpec.makeMeasureSpec(fixedHeight, MeasureSpec.EXACTLY)
                else MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, spec)
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
        val ctx: Context, val c: M3Colors, val prefs: DisplayPrefs, val root: LinearLayout,
        val onSpeak: ((String, Lang?) -> Unit)?,
    ) {
        /** Where content goes: the card itself, or the card of the section begun last. */
        var body: LinearLayout = root

        /** Back to the card itself, after a section (what follows is not part of it). */
        fun endSection() {
            body = root
        }

        /**
         * A section of its own: a distinct rounded card (Material 3's surface on the popup's
         * container), its title inside; what follows goes in it until the next section.
         */
        private fun sectionCard(title: String?, color: Int = c.surface): LinearLayout {
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(color)
                }
            }
            root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
            if (title != null) card.addView(textView(title, 13f, c.onSurfaceVariant).apply {
                typeface = app.lychee.fonts.LycheeFonts.appFont(context, 600)
                setPadding(0, 0, 0, dp(4))
            })
            return card
        }
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
                heading(first, null, otherForm(first, second), strokesOf = text)
            } else {
                // a phrase (read piece by piece) still shows its other script under it
                heading(text, null, (Annotator.otherScript(text, r) ?: charByChar(text))?.let { otherForm(text, it) }, strokesOf = text)
            }
            // both languages' readings, always
            spokenWord = text
            // a Kanji or Hanja: its Japanese or Korean readings first
            local?.forEach { lr ->
                val speak = onSpeakLocal
                if (lr.items.isEmpty() || speak == null) {
                    body.addView(readingRow(labelled(lr.label, lr.shown), speak?.let { say -> smallSpeaker { say(lr.say) } }))
                } else {
                    // each reading a chip that says itself
                    chipLines(lr.label, lr.items.map { (native, roman, said) -> ReadingChip(roman, native) { speak(said) } }, perLine = 2)
                        .forEach { body.addView(it) }
                }
            }
            if (showChinese) readingRows(r, main, text)
            // the stroke order straight after the readings
            strokes(text)
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
                        endSection()
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
            if (showChinese) examples(app.lychee.dict.PackData.examples(ctx, lang, forms2), sayIn = romanLang) { sentence ->
                Readings.annotator?.romanise(sentence, romanLang, prefs)
            }
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
            // the meanings of its most common reading only (rarer readings are in the full card)
            val all = hw.senses.filter { it.partOfSpeech != "cl" && !it.gloss.startsWith("variant of") && !it.gloss.startsWith("see ") }
                .let { list ->
                    list.filter { s -> s.reading.isNullOrBlank() || s.lang == Lang.Any || mainReading[s.lang]?.let { m -> norm(s.reading!!) == m } != false }
                        .ifEmpty { list }
                }
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
            // the word's most common reading (the dictionary lists it first), whichever was typed:
            // its rarer readings come further down, with their own meanings
            val yue = hw?.readings?.get(Lang.Cantonese)?.firstOrNull()
                ?: (if (r.lang == Lang.Cantonese) r.reading else r.otherReading)
            val cmn = hw?.readings?.get(Lang.Mandarin)?.firstOrNull()
                ?: (if (r.lang == Lang.Mandarin) r.reading else r.otherReading)
            // a phrase with no reading in one of the languages still gets one: read character by
            // character (already formatted, so not formatted again)
            val word = spoken ?: hw?.let { if (prefs.simplified) it.simplified else it.traditional }
            fun forced(lang: Lang): String? = word?.let { w ->
                runCatching { Readings.annotator?.romanise(w, lang, prefs) }.getOrNull()?.takeIf { it.isNotBlank() && !it.contains('?') }
            }
            class Row(val lang: Lang, val label: Int, val reading: String, val formatted: String)
            val rows = listOfNotNull(
                (yue?.let { Row(Lang.Cantonese, R.string.lychee_card_cantonese, it, Annotator.format(it, Lang.Cantonese, prefs)) }
                    ?: forced(Lang.Cantonese)?.let { Row(Lang.Cantonese, R.string.lychee_card_cantonese, it, it) }),
                (cmn?.let { Row(Lang.Mandarin, R.string.lychee_card_mandarin, it, Annotator.format(it, Lang.Mandarin, prefs)) }
                    ?: forced(Lang.Mandarin)?.let { Row(Lang.Mandarin, R.string.lychee_card_mandarin, it, it) }),
            ).let { if (r.lang == Lang.Mandarin) it.reversed() else it }
            // side by side, each with its speaker, to save room; a long phrase's readings one
            // under the other, full width, so they wrap and show whole
            val long = rows.any { it.formatted.length > LONG_READING }
            val pair = LinearLayout(ctx).apply {
                orientation = if (long) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            for ((i, row) in rows.withIndex()) {
                mainReading[row.lang] = norm(row.reading)
                // the main reading only; a reading with meanings of its own comes with them
                pair.addView(readingTile(ctx.getString(row.label), row.formatted,
                    readingSpeaker(hw, row.reading, row.lang, spoken)),
                    if (long) LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (i > 0) topMargin = dp(8)
                    } else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (i > 0) marginStart = dp(8)
                    })
            }
            if (pair.childCount > 0) body.addView(pair, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6); bottomMargin = dp(4) })
        }

        /**
         * One language's reading as a small tile: the language in small letters over the reading,
         * the speaker beside it, on a tinted rounded background.
         */
        private fun readingTile(language: String, reading: String, speaker: View?): View = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(6), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(c.surface)
            }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(textView(language, 12f, c.onSurfaceVariant).apply {
                    typeface = app.lychee.fonts.LycheeFonts.appFont(context, 500)
                })
                addView(textView(reading, 18f, c.primary).apply {
                    typeface = app.lychee.fonts.LycheeFonts.appFont(context, 500)
                    setPadding(0, dp(1), 0, 0)
                    // a long reading wraps onto more lines, never cut off
                    isSingleLine = false
                    maxLines = Int.MAX_VALUE
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (speaker != null) {
                (speaker.parent as? ViewGroup)?.removeView(speaker)
                addView(speaker)
            }
        }

        /** A reading as a chip: [bottom] (kana or Hangul) large, [top] (its romanisation) beside it, a small speaker after them. */
        class ReadingChip(val top: String, val bottom: String?, val onClick: (() -> Unit)?)

        private fun chipView(chip: ReadingChip): View = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(if (chip.onClick != null) 6 else 8), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(c.surfaceContainerHigh)
            }
            // the kana or Hangul large, its romanisation beside it in the accent colour (the
            // same for Japanese and Korean)
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                chip.bottom?.takeIf { it.isNotBlank() && it != chip.top }?.let { b ->
                    addView(textView(b, 18f, c.onSurface).apply {
                        app.lychee.fonts.LycheeFonts.forText(ctx, b, fontLang)?.let { typeface = it }
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { marginEnd = dp(6) })
                }
                addView(textView(chip.top, 13f, c.primary).apply {
                    typeface = app.lychee.fonts.LycheeFonts.appFont(context, 600)
                })
            })
            chip.onClick?.let { click ->
                addView(android.widget.ImageView(ctx).apply {
                    setImageResource(R.drawable.lychee_ic_volume)
                    imageTintList = android.content.res.ColorStateList.valueOf(c.primary)
                }, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(6) })
                contentDescription = ctx.getString(R.string.lychee_card_listen) + " " + chip.top
                isClickable = true
                foreground = TypedValue().let { v ->
                    ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
                    if (v.resourceId != 0) ctx.getDrawable(v.resourceId) else null
                }
                setOnClickListener { click() }
            }
        }

        /** A kind of reading ([label]: on, kun, Korean) and its readings as chips, wrapping onto new lines. */
        fun chipLines(label: String?, chips: List<ReadingChip>, @Suppress("UNUSED_PARAMETER") perLine: Int, labelWidth: Int = 64): List<View> =
            listOf(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(0, dp(3), 0, dp(3))
                if (label != null) addView(textView(label, 11f, c.onSurfaceVariant).apply {
                    typeface = app.lychee.fonts.LycheeFonts.appFont(context, 600)
                    // level with the middle of the first line of chips
                    setPadding(0, dp(10), 0, 0)
                }, LinearLayout.LayoutParams(dp(labelWidth), ViewGroup.LayoutParams.WRAP_CONTENT))
                // the chips wrap onto as many lines as they need
                addView(FlowRow(ctx, dp(6)).apply { chips.forEach { addView(chipView(it)) } },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })

        /**
         * Each Kanji or Hanja of a Japanese or Korean word as a tidy block: the character on a
         * square tile on the left; beside it its meaning, then a line per kind of reading (on'yomi,
         * kun'yomi; Korean), each reading a chip (romanisation over the kana or Hangul) that says
         * itself; a thin line between the characters.
         */
        fun characterReadings(title: String, list: List<CharReading>) {
            section(title)
            list.forEachIndexed { index, cr ->
                if (index > 0) body.addView(View(ctx).apply {
                    setBackgroundColor(c.outlineVariant)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    topMargin = dp(6); bottomMargin = dp(6)
                })
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    setPadding(0, dp(4), 0, dp(4))
                }
                row.addView(textView(cr.ch, 30f, c.onSurface).apply {
                    typeface = app.lychee.fonts.LycheeFonts.forText(ctx, cr.ch, fontLang) ?: Typeface.DEFAULT
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(c.surfaceContainer)
                    }
                }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) })
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                cr.meaning?.takeIf { it.isNotBlank() }?.let { m ->
                    col.addView(textView(m, 15f, c.onSurface).apply {
                        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(0, 0, 0, dp(2))
                    })
                }
                cr.note?.let { n ->
                    col.addView(textView(n, 13f, c.primary).apply { setPadding(0, 0, 0, dp(2)) })
                }
                for ((label, readings) in cr.rows) {
                    val chips = readings.map { (native, roman) ->
                        // まな(ぶ) is said まなぶ
                        val said = native.replace("(", "").replace(")", "")
                        ReadingChip(roman, native, onSpeak?.let { speak -> { speak(said, null) } })
                    }
                    // (one kind alone, as Korean's, needs no label)
                    chipLines(label.takeIf { cr.rows.size > 1 }, chips, perLine = 2, labelWidth = 64).forEach { col.addView(it) }
                }
                row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                body.addView(row)
            }
        }

        /**
         * The Chinese box on a Japanese or Korean word's card: the word as Chinese writes it,
         * Traditional and Simplified, with each character that differs from the Japanese Kanji or
         * Korean Hanja ([form]) in colour, then its Cantonese and Mandarin readings, each with a
         * speaker.
         */
        fun chineseBox(form: String, r: Resolved) {
            val hw = r.headwords.firstOrNull()
            // the most common readings
            val yue = hw?.readings?.get(Lang.Cantonese)?.firstOrNull() ?: (if (r.lang == Lang.Cantonese) r.reading else r.otherReading)
            val cmn = hw?.readings?.get(Lang.Mandarin)?.firstOrNull() ?: (if (r.lang == Lang.Mandarin) r.reading else r.otherReading)
            if (yue == null && cmn == null) return
            val trad = hw?.traditional?.takeIf { it.codePointCount(0, it.length) == form.codePointCount(0, form.length) } ?: form
            val simp = hw?.simplified?.takeIf { it.codePointCount(0, it.length) == trad.codePointCount(0, trad.length) } ?: trad
            val wordToSay = trad
            val own = form.codePoints().toArray()
            /** [word] with the characters unlike [form]'s in colour; whether any were. */
            fun marked(word: String): Pair<CharSequence, Boolean> {
                val sb = SpannableStringBuilder()
                var differs = false
                word.codePoints().toArray().forEachIndexed { i, cp ->
                    val ch = String(Character.toChars(cp))
                    val unlike = own.getOrNull(i)?.let { it != cp } ?: false
                    differs = differs || unlike
                    sb.append(ch, ForegroundColorSpan(if (unlike) c.primary else c.onSurface), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                return sb to differs
            }
            // a card of its own, tinted apart from the rest: this is Chinese, not the word's language
            endSection()
            // a light wash of the accent colour, not a dark grey block
            val box = sectionCard(null, androidx.core.graphics.ColorUtils.setAlphaComponent(c.primaryContainer, 0x66))
            val (tradText, tradDiffers) = marked(trad)
            val (simpText, simpDiffers) = marked(simp)
            val same = !tradDiffers && !simpDiffers
            // the heading: "Chinese", and that it is written alike when no character differs
            box.addView(textView(
                if (same) ctx.getString(R.string.lychee_card_chinese_same) else ctx.getString(R.string.lychee_card_chinese_readings),
                13f, c.primary,
            ).apply { typeface = app.lychee.fonts.LycheeFonts.appFont(context, 600) })
            // the forms: Traditional 學校  Simplified 学校 (one form when both are written
            // alike), wrapping onto a line each when narrow
            box.addView(FlowRow(ctx, dp(12)).apply {
                // marked in English, in full: Traditional and Simplified
                val forms = if (simp == trad) listOf(null to tradText) else listOf(TRADITIONAL to tradText, SIMPLIFIED to simpText)
                for ((mark, word) in forms) addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    if (mark != null) addView(textView(mark, 13f, c.onSurface).apply { setPadding(0, 0, dp(6), 0) })
                    addView(textView(word, 24f, c.onSurface).apply {
                        textLocale = java.util.Locale.TRADITIONAL_CHINESE.takeIf { mark != SIMPLIFIED } ?: java.util.Locale.SIMPLIFIED_CHINESE
                    })
                })
                setPadding(0, dp(2), 0, dp(2))
            })
            // the readings: 粵 hok6 haau6 🔈   普 xuéxiào 🔈, wrapping onto two lines when narrow
            val readings = listOfNotNull(
                yue?.let { Triple(Lang.Cantonese, ctx.getString(R.string.lychee_card_cantonese), it) },
                cmn?.let { Triple(Lang.Mandarin, ctx.getString(R.string.lychee_card_mandarin), it) },
            )
            for ((lang, mark, reading) in readings) {
                val line = TextView(ctx).apply {
                    val sb = SpannableStringBuilder()
                    sb.append(mark, ForegroundColorSpan(c.onSurface), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append("  ")
                    sb.append(Annotator.format(reading, lang, prefs), ForegroundColorSpan(c.primary), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    this.text = sb
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                }
                val speaker = onSpeak?.let { say -> smallSpeaker { say(wordToSay, lang) } }
                box.addView(readingRow(line, speaker))
            }
            spokenWord = spokenWord ?: form
        }

        /** Each language's main reading on this card, for the meanings read another way. */
        private val mainReading = HashMap<Lang, String>()

        /**
         * A small speaker saying [reading] in [lang]: the word itself when that is how it is
         * usually read, otherwise spelt in characters only ever read that way.
         */
        private fun readingSpeaker(hw: Headword?, reading: String, lang: Lang, spoken: String?): View? {
            val say = onSpeak ?: return null
            // every reading has a speaker: the word of this entry when no other is given
            val word = spoken ?: spokenWord
                ?: hw?.let { if (prefs.simplified) it.simplified else it.traditional } ?: return null
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
        /** The characters whose stroke order the heading shows on its right (not repeated below). */
        private var strokesInHeading: String? = null

        /**
         * The stroke order of a one- or two-character word as small animated tiles, for the
         * heading's right; null for a longer word (its strokes keep their own section).
         */
        private fun headingStrokes(text: String): View? {
            val chars = text.codePoints().toArray().filter {
                Annotator.isHan(it) || it in 0x3040..0x30FF || it in 0xAC00..0xD7A3
            }.map { String(Character.toChars(it)) }.distinct()
            if (chars.isEmpty() || chars.size > 2) return null
            val found = chars.mapNotNull { app.lychee.dict.PackData.strokes(ctx, it, fontLang) }
            if (found.size != chars.size) return null
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                found.forEach { ch ->
                    addView(FrameLayout(ctx).apply {
                        background = GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            setColor(c.surfaceContainer)
                        }
                        setPadding(dp(4), dp(4), dp(4), dp(4))
                        addView(app.lychee.dict.StrokeOrderView(ctx, ch, c.primary,
                            androidx.core.graphics.ColorUtils.setAlphaComponent(c.onSurface, 0x1C),
                            androidx.core.graphics.ColorUtils.setAlphaComponent(c.outline, 0x70),
                            current = c.primary),
                            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        contentDescription = ctx.getString(R.string.lychee_card_strokes)
                    }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginStart = dp(6) })
                }
            }
        }

        fun heading(shown: String, spoken: String?, other: String? = null, strokesOf: String? = null) {
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
            // a short word's stroke order on the right, written out beside it (not again below)
            // (the summary leaves the stroke order to the full card: it holds what typing needs)
            strokesOf?.takeIf { !compact }?.let { t -> headingStrokes(t)?.let { tiles ->
                head.addView(tiles)
                strokesInHeading = t
            } }
            if (spoken != null) onSpeak?.let { speak ->
                if (speakLang != null) head.addView(speakChip())
                head.addView(speaker { speak(spoken, speakLang) })
            }
            body.addView(head)
        }

        /** 粵 or 普: the language the speak buttons use; a tap switches it. */
        private fun speakChip() = TextView(ctx).apply {
            // the language in English (Cantonese or Mandarin), a tap switches
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = app.lychee.fonts.LycheeFonts.appFont(context, 600)
            setTextColor(c.onSecondaryContainer)
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(c.secondaryContainer)
            }
            contentDescription = ctx.getString(R.string.lychee_card_speak_language)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply { marginEnd = dp(8) }
            speakChips += this
            updateChip(this)
            setOnClickListener {
                speakLang = if (speakLang == Lang.Mandarin) Lang.Cantonese else Lang.Mandarin
                speakChips.forEach { updateChip(it) }
            }
        }

        private fun updateChip(chip: TextView) {
            chip.text = ctx.getString(if (speakLang == Lang.Mandarin) R.string.lychee_card_mandarin else R.string.lychee_card_cantonese)
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
            // shown by the heading already; the summary has none (only the full card)
            if (compact || strokesInHeading == text) return
            // Chinese characters, kana and Hangul (Japanese and Korean have their own order)
            val chars = text.codePoints().toArray().filter {
                Annotator.isHan(it) || it in 0x3040..0x30FF || it in 0xAC00..0xD7A3
            }.map { String(Character.toChars(it)) }.distinct().take(MAX_STROKE_CHARS)
            val found = chars.mapNotNull { app.lychee.dict.PackData.strokes(ctx, it, fontLang) }
            if (found.isEmpty()) return
            // right under the readings, small like the heading's: each character on a tinted
            // copybook tile of one fixed size (its grid never squeezed), written in the accent
            // colour over a faint copy; as many to a line as fit, the rest on the next line
            val flow = FlowRow(ctx, dp(6))
            found.forEach { ch ->
                flow.addView(FrameLayout(ctx).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(c.surfaceContainer)
                    }
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    addView(app.lychee.dict.StrokeOrderView(ctx, ch, c.primary,
                        androidx.core.graphics.ColorUtils.setAlphaComponent(c.onSurface, 0x1C),
                        androidx.core.graphics.ColorUtils.setAlphaComponent(c.outline, 0x70),
                        current = c.primary),
                        FrameLayout.LayoutParams(dp(48), dp(48)))
                    contentDescription = ctx.getString(R.string.lychee_card_strokes)
                })
            }
            body.addView(flow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(4); bottomMargin = dp(8) })
        }

        /** Example sentences, each with its romanisation ([romanise]) and English under it. */
        fun examples(list: List<Pair<String, String>>, sayIn: Lang? = null, romanise: ((String) -> String?)? = null) {
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
                    // in the sentence's own language (a Cantonese sentence is never read in Mandarin)
                    row.addView(speaker { speak(sentence, sayIn ?: speakLang) }.apply {
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
            // the meanings of the word as it is most commonly said, both languages' together
            // (the same meaning once); then each rarer reading, under its own heading, with the
            // meanings it carries
            val langs = if (r.lang == Lang.Mandarin) listOf(Lang.Mandarin, Lang.Cantonese, Lang.Any)
                else listOf(Lang.Cantonese, Lang.Mandarin, Lang.Any)
            val usable = hw.senses.filter { it.partOfSpeech != "cl" }
            fun readingOf(s: Sense): String? = s.reading?.takeIf { it.isNotBlank() }?.let { norm(it) }
                ?.takeIf { s.lang != Lang.Any && it != mainReading[s.lang] }
            val seen = HashSet<String>()
            val main = langs.flatMap { l -> usable.filter { it.lang == l && readingOf(it) == null } }
                .filter { seen.add(it.gloss.trim().lowercase()) }.take(MAX_SENSES)
            if (main.isNotEmpty()) {
                section(ctx.getString(R.string.lychee_card_meanings))
                main.forEachIndexed { i, s -> sense(i + 1, s) }
            }
            val rarer = langs.flatMap { l -> usable.filter { it.lang == l && readingOf(it) != null } }
                .groupBy { it.lang to readingOf(it)!! }
            if (rarer.isEmpty()) return
            section(ctx.getString(R.string.lychee_card_other_readings))
            for ((key, senses) in rarer) {
                val (lang, _) = key
                val reading = senses.first().reading!!
                val mark = ctx.getString(if (lang == Lang.Cantonese) R.string.lychee_card_cantonese else R.string.lychee_card_mandarin)
                body.addView(readingRow(textView(SpannableStringBuilder().apply {
                    append(mark, ForegroundColorSpan(c.onSurfaceVariant), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    append("  ")
                    append(Annotator.format(reading, lang, prefs))
                }, 15f, c.primary).apply { setPadding(0, dp(8), 0, 0) }, readingSpeaker(hw, reading, lang, spokenWord)))
                senses.distinctBy { it.gloss.trim().lowercase() }.take(3).forEachIndexed { i, s -> sense(i + 1, s) }
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
            // each part says itself, in the card's language
            onSpeak?.let { say -> row.addView(smallSpeaker { say(form, speakLang ?: lang) }) }
            body.addView(row)
        }

        private fun section(title: String) {
            body = sectionCard(title)
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
            const val TRADITIONAL = "Traditional"
            const val SIMPLIFIED = "Simplified"
            const val MAX_SENSES = 12
            /** Meanings in the summary. */
            const val SUMMARY_SENSES = 3
            /** A reading longer than this puts the two readings one under the other. */
            const val LONG_READING = 14
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
