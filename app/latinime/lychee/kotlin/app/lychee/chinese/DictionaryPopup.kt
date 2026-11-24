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
 * One fixed size, never growing (REDESIGN.md): a header that always shows the word, the reading
 * of every syllable, the 粵/普 switch and speaker, and ✕; then the tabs (Summary, Readings,
 * Definitions, Parts, Examples; empty ones left out), swiped sideways or tapped, each page
 * scrolling on its own. A part (a character, a piece of a phrase) opens as its own card, with
 * Back.
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
        val reshow = { show(keyboard, text, resolved, prefs, peek, onSpeak, local, showChinese, onSpeakLocal, onType) }
        val r = Annotator.regional(resolved, prefs)
        // one 粵/普 choice for the whole card: the header's switch sets what every speaker says
        val language = LangChoice()
        fun makeContent(c: M3Colors, body: LinearLayout, page: Page?) = Content(ctx, c, prefs, body, onSpeak).apply {
            this.local = local
            this.showChinese = local == null || showChinese
            this.onSpeakLocal = onSpeakLocal
            flat = true
            pageTitle = page?.let { ctx.getString(it.label) }
            choice = language
            begin(r)
        }
        presentTabs(keyboard, peek, onType, reshow,
            header = { c, body -> makeContent(c, body, null).header(text, r) },
            page = { which, c, body ->
                makeContent(c, body, which).page(which, text, r) { part ->
                    // a part opened as its own card: Back comes back here
                    val pr = Readings.annotator?.resolve(part, "", if (r.lang == Lang.Mandarin) app.lychee.core.InputMode.Mandarin
                        else app.lychee.core.InputMode.Cantonese) ?: return@page
                    navigate { show(keyboard, part, pr, prefs, peek = false, onSpeak = onSpeak) {} }
                }
            })
    }

    /**
     * One Kanji or Hanja of a Japanese or Korean word: its readings in that language (kana or
     * Hangul with their romanisation), what its speaker says, and its meaning.
     */
    class CharReading(
        val ch: String, val rows: List<Pair<String?, List<Pair<String, String>>>>, val say: String, val meaning: String?,
        /** A kanji's form: its old (traditional) form where Japan simplified it, or Japan-made. */
        val note: String? = null,
        /** A kanji's details: school grade, frequency, parts. */
        val details: String? = null,
    )

    /**
     * A piece of a Japanese or Korean phrase the dictionary doesn't have as a whole: a word it
     * has (or a single Kanji or Hanja), its reading and its meaning, so the phrase can be read.
     */
    class PhrasePart(val form: String, val reading: String?, val meaning: String, val full: String? = null)

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
        /** Small badges under the word: its learner level ("JLPT N5", "Beginner"). */
        badges: List<String> = emptyList(),
        /** How it is said, beyond its spelling: a Japanese word's pitch accent, a Korean word's sound changes. */
        pronunciation: CharSequence? = null,
        /** A verb's or adjective's conjugation table. */
        conjugation: List<app.lychee.core.Conjugated>? = null,
        /** A phrase the dictionary lacks as a whole: the words and characters it is made of. */
        parts: List<PhrasePart> = emptyList(),
        onType: () -> Unit,
    ) {
        val reshow = {
            showWord(keyboard, text, romanisation, entries, examples, peek, onSpeak, lang, onSpeakAs, translations,
                onTranslation, chinese, chinesePrefs, chineseForm, characters, charactersTitle, oldForm, badges,
                pronunciation, conjugation, parts, onType)
        }
        val prefs = chinesePrefs ?: DisplayPrefs()
        fun makeContent(c: M3Colors, body: LinearLayout, page: Page?) = Content(ctx, c, prefs, body, onSpeak).apply {
            fontLang = lang
            flat = true
            pageTitle = page?.let { ctx.getString(it.label) }
        }
        // each pronunciation has its own speak button; only a word with none keeps the heading's
        val anyReading = entries.any { !it.reading.isNullOrBlank() }
        val pronunciations = entries.mapNotNull { e -> e.reading?.takeIf { it.isNotBlank() } }.distinct()
        val heteronym = lang == "en" && pronunciations.size > 1
        val hanja = entries.firstOrNull()?.hanja?.takeIf { lang == "ko" && it.isNotBlank() }

        /** One entry's readings (each with its speaker; English UK and US apart). */
        fun readingsOf(content: Content, body: LinearLayout, e: app.lychee.dict.WordEntry, c: M3Colors) {
            val reading = e.reading?.takeIf { it != e.lemma && it.isNotBlank() } ?: return
            val label = ctx.getString(if (lang == "en") R.string.lychee_card_pronunciation else R.string.lychee_card_reading)
            val list = if (lang == "en") reading.split(" · ").let { all ->
                all.filter { app.lychee.speech.Pronunciation.showsEnglish(ctx, it) }.ifEmpty { all }
            } else listOf(reading)
            for (part in list) {
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
                            heteronym -> app.lychee.dict.PackData.homophone(ctx, part, e.lemma) ?: e.lemma
                            else -> e.lemma
                        }
                        speak(say, locale)
                    }
                }
                body.addView(content.readingRow(content.labelled(label, part), button))
            }
        }

        /** An entry's word, when it isn't the word looked up (a form of it, or the next entry). */
        fun entryWord(content: Content, body: LinearLayout, e: app.lychee.dict.WordEntry, i: Int) {
            if (i > 0 || e.lemma != text) {
                if (i > 0) body.addView(content.divider())
                body.addView(content.entryChip(e.lemma))
            }
        }

        presentTabs(keyboard, peek, onType, reshow,
            header = { c, body ->
                val content = makeContent(c, body, null)
                content.heading(text, if (anyReading && onSpeakAs != null && lang == "en") null else text)
                // the reading under the word, as one quiet line: the kana and romaji, or the romanisation
                romanisation?.takeIf { it.isNotEmpty() }?.let { r ->
                    body.addView(content.textView(r, 16f, c.primary).apply {
                        typeface = app.lychee.fonts.LycheeFonts.appFont(context, 500)
                    })
                }
                // the other way of writing it: a Korean word's Hanja, a Japanese word's old form
                val alternative = when {
                    hanja != null && hanja != text -> ctx.getString(R.string.lychee_card_hanja) to hanja
                    lang == "ja" && oldForm != null && oldForm != text -> ctx.getString(R.string.lychee_card_old_form) to oldForm
                    else -> null
                }
                alternative?.let { (label, form) ->
                    body.addView(content.labelled(label, form).apply {
                        textLocale = if (lang == "ko") java.util.Locale.KOREAN else java.util.Locale.TRADITIONAL_CHINESE
                    })
                }
                if (badges.isNotEmpty()) content.badges(badges)
            },
            page = { which, c, body ->
                val content = makeContent(c, body, which)
                when (which) {
                    Page.SUMMARY -> {
                        if (translations.isNotEmpty()) content.translations(translations, onTranslation)
                        when {
                            entries.isNotEmpty() -> entries.take(3).forEachIndexed { i, e ->
                                entryWord(content, body, e, i)
                                e.partOfSpeech?.takeIf { it.isNotBlank() }?.let { body.addView(content.partOfSpeech(it)) }
                                e.senses.take(Content.SUMMARY_SENSES).forEachIndexed { n, s -> content.plainSense(n + 1, s) }
                            }
                            parts.isNotEmpty() -> {
                                // a phrase the dictionary lacks as a whole: what its pieces mean, each in full
                                body.addView(content.note(ctx.getString(R.string.lychee_card_phrase_note)))
                                parts.forEach { p -> body.addView(content.pieceLine(p.form, p.full ?: p.meaning)) }
                            }
                            else -> body.addView(content.note(ctx.getString(R.string.lychee_card_no_definition)))
                        }
                    }
                    Page.READINGS -> {
                        entries.forEachIndexed { i, e -> readingsOf(content, body, e, c) }
                        pronunciation?.let { body.addView(content.textView(it, 15f, c.onSurface).apply { setPadding(0, dp(6), 0, dp(4)) }) }
                        chinese?.let { content.chineseBox(chineseForm ?: text, it) }
                    }
                    Page.DEFINITIONS -> {
                        entries.forEachIndexed { i, e ->
                            entryWord(content, body, e, i)
                            e.hanja?.takeIf { it != hanja && it.isNotBlank() }?.let { body.addView(content.textView(it, 15f, c.onSurfaceVariant)) }
                            e.partOfSpeech?.takeIf { it.isNotBlank() }?.let { body.addView(content.partOfSpeech(it)) }
                            e.senses.take(Content.MAX_SENSES).forEachIndexed { n, s -> content.plainSense(n + 1, s) }
                        }
                        conjugation?.takeIf { it.size > 1 }?.let { content.conjugation(it) }
                    }
                    Page.PARTS -> {
                        if (lang == "ja" || lang == "ko") content.strokes(text)
                        if (characters.isNotEmpty()) content.characterReadings(charactersTitle ?: "", characters)
                        if (entries.isEmpty() && parts.isNotEmpty()) {
                            content.phraseParts(ctx.getString(R.string.lychee_card_parts), parts, parts.size) { p ->
                                openWordPart(keyboard, p.form, lang, onSpeak, onSpeakAs)
                            }
                        }
                    }
                    Page.EXAMPLES -> content.examples(examples) { sentence ->
                        when (lang) {
                            "ja" -> app.lychee.core.SentenceRomanizer.japanese(sentence) { form ->
                                app.lychee.dict.PackData.words(ctx, "ja", form, 1).firstOrNull()?.let { it.reading ?: it.form }
                            }
                            "ko" -> app.lychee.core.SentenceRomanizer.korean(sentence)
                            else -> null
                        }
                    }
                }
            })
    }

    /** A Japanese or Korean phrase's piece opened as its own card (Back returns to the phrase). */
    private fun openWordPart(keyboard: View, form: String, lang: String?, onSpeak: ((String, Lang?) -> Unit)?,
                             onSpeakAs: ((String, java.util.Locale?) -> Unit)?) {
        val entries = app.lychee.dict.PackData.words(ctx, lang, form)
        val examples = if (lang != null) app.lychee.dict.PackData.examples(ctx, lang, listOf(form)) else emptyList()
        navigate {
            showWord(keyboard, form, entries.firstOrNull()?.reading?.takeIf { it != form }, entries, examples,
                peek = false, onSpeak = onSpeak, lang = lang, onSpeakAs = onSpeakAs) {}
        }
    }

    /** The card's tabs, in order; a tab with nothing in it is left out. */
    enum class Page(val label: Int) {
        SUMMARY(R.string.lychee_card_tab_summary),
        READINGS(R.string.lychee_card_tab_readings),
        DEFINITIONS(R.string.lychee_card_tab_definitions),
        PARTS(R.string.lychee_card_parts),
        EXAMPLES(R.string.lychee_card_examples),
    }

    /** The card's one 粵/普 choice: the header's switch, every speaker and reading line follow it. */
    class LangChoice {
        var lang: Lang? = null
        val listeners = ArrayList<() -> Unit>()
        fun changed() = listeners.forEach { it() }
    }

    /** The cards a part was opened from, newest last: Back shows the one before. */
    private val history = ArrayList<() -> Unit>()
    /** How to show the card now on screen again (for [history]). */
    private var current: (() -> Unit)? = null
    /** The next card shown is a part opened from this one (not a new look-up). */
    private var navigating = false

    /** Opens [next] (a part's card) on top of this one; Back returns. */
    private fun navigate(next: () -> Unit) {
        current?.let { history += it }
        navigating = true
        next()
        navigating = false
    }

    private fun back() {
        val previous = history.removeLastOrNull() ?: return
        navigating = true
        current = null
        previous()
        navigating = false
    }

    /**
     * The card: one fixed size, never growing. A header (the word, its reading, the 粵/普 switch
     * and speaker, Back when a part was opened, ✕), then the tabs, then the pages, swiped
     * sideways or chosen by tab, each scrolling on its own. [reshow] shows this card again
     * (Back from a part opened on it).
     */
    private fun presentTabs(
        keyboard: View, peek: Boolean, onType: () -> Unit, reshow: () -> Unit,
        header: (M3Colors, LinearLayout) -> Unit,
        page: (Page, M3Colors, LinearLayout) -> Unit,
    ) {
        if (!navigating) history.clear()
        current = reshow
        host?.removeAllViews()
        host = null
        val panel = standalone ?: keyboard.rootView.findViewById<FrameLayout>(R.id.lychee_dictionary_panel) ?: return
        panel.animate().cancel()
        panel.removeAllViews()
        val parent = panel.parent as? View ?: return
        val keyboardTop = if (standalone != null) 0 else IntArray(2).also { keyboard.getLocationInWindow(it) }[1] -
            IntArray(2).also { parent.getLocationInWindow(it) }[1]
        @Suppress("DEPRECATION")
        val statusBar = parent.rootWindowInsets?.stableInsetTop ?: dp(24)
        val space = if (standalone != null) standaloneRoom else keyboardTop - statusBar - dp(12)
        if (space < dp(160)) return
        // one fixed height: about 60% of the room above the keys, within limits that suit a phone
        val height = if (standalone != null) space else minOf(space, maxOf(dp(260), (space * 0.6f).toInt()), dp(MAX_CARD_DP))
        val c = M3Colors.of(ctx)

        // the pages, each built once; empty ones are left out
        val pages = Page.values().mapNotNull { p ->
            val body = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(16))
            }
            runCatching { page(p, c, body) }.onFailure { app.lychee.DiagLog.log("dictionary card: ${p.name}", it) }
            if (body.childCount == 0) null else p to body
        }

        // the header: Back (when a part was opened), the word and its reading, ✕
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(8), dp(10), dp(8), dp(6))
        }
        if (history.isNotEmpty() && !peek) head.addView(cornerButton(R.drawable.ic_sym_arrow_back, ctx.getString(R.string.lychee_card_back), c, filled = false) { back() },
            LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(4) })
        val title = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (history.isNotEmpty()) 4 else 8), 0, dp(4), 0)
        }
        header(c, title)
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!peek) {
            // Look up in Lychee keeps its own action (copy) beside ✕; inside the keyboard the
            // candidate types the word
            if (standalone != null) head.addView(cornerButton(actionIcon, ctx.getString(actionLabel), c, filled = true) {
                dismiss()
                onType()
            }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(6) })
            head.addView(cornerButton(R.drawable.ic_sym_close, ctx.getString(R.string.lychee_close), c, filled = false) { dismiss() },
                LinearLayout.LayoutParams(dp(40), dp(40)))
        }

        // the tabs: scroll sideways if they don't fit; the current one in the accent colour, underlined
        val tabRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        val tabs = android.widget.HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabRow)
        }
        val pager = androidx.recyclerview.widget.RecyclerView(ctx)
        val layout = androidx.recyclerview.widget.LinearLayoutManager(ctx, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        pager.layoutManager = layout
        androidx.recyclerview.widget.PagerSnapHelper().attachToRecyclerView(pager)
        pager.overScrollMode = View.OVER_SCROLL_NEVER
        pager.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun getItemCount() = pages.size
            override fun getItemViewType(position: Int) = position
            override fun onCreateViewHolder(group: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val body = pages[viewType].second
                (body.parent as? ViewGroup)?.removeView(body)
                val scroll = ScrollView(ctx).apply {
                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    isVerticalScrollBarEnabled = true
                    addView(body)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(scroll) {}
            }
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {}
        }
        // every page kept, never recycled (each is its own view)
        pager.setItemViewCacheSize(Page.values().size)
        val tabViews = pages.mapIndexed { i, (p, _) ->
            TextView(ctx).apply {
                text = ctx.getString(p.label)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = app.lychee.fonts.LycheeFonts.appFont(context, 600)
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                background = TypedValue().let { v ->
                    ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
                    ctx.getDrawable(v.resourceId)
                }
                setOnClickListener { pager.smoothScrollToPosition(i) }
            }.also { tabRow.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))) }
        }
        fun select(i: Int) {
            tabViews.forEachIndexed { j, t ->
                val on = i == j
                t.setTextColor(if (on) c.primary else c.onSurfaceVariant)
                t.foreground = if (on) android.graphics.drawable.LayerDrawable(arrayOf(GradientDrawable().apply {
                    setColor(c.primary)
                    cornerRadius = dp(2).toFloat()
                })).apply { setLayerGravity(0, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL); setLayerHeight(0, dp(3)); setLayerInset(0, dp(10), 0, dp(10), 0) } else null
            }
            tabViews.getOrNull(i)?.let { t -> tabs.post { tabs.smoothScrollTo((t.left - dp(24)).coerceAtLeast(0), 0) } }
        }
        select(0)
        pager.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(view: androidx.recyclerview.widget.RecyclerView, state: Int) {
                if (state != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) return
                val i = layout.findFirstCompletelyVisibleItemPosition().takeIf { it >= 0 } ?: layout.findFirstVisibleItemPosition()
                if (i >= 0) select(i)
            }
        })

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHigh)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), androidx.core.graphics.ColorUtils.setAlphaComponent(c.outline, 0x60))
            }
            elevation = dp(18).toFloat()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                outlineSpotShadowColor = android.graphics.Color.BLACK
                outlineAmbientShadowColor = android.graphics.Color.BLACK
            }
            clipToOutline = true
            addView(head)
            if (pages.size > 1) addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
            addView(View(ctx).apply { setBackgroundColor(c.outlineVariant) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, dp(1) / 2)))
            addView(pager, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        // just above the keyboard, over the keys only (one-handed mode keeps to the narrowed keys)
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
        val keysWidth = if (standalone != null) standalone.width.takeIf { it > 0 } ?: ctx.resources.displayMetrics.widthPixels
            else keys?.width?.takeIf { it > 0 } ?: parent.width
        val cardWidth = minOf(keysWidth - 2 * dp(16), dp(MAX_CARD_WIDTH_DP))
        panel.addView(card, FrameLayout.LayoutParams(cardWidth, height, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
            setMargins(0, dp(20), 0, dp(10))
        })
        panel.clipChildren = false
        panel.clipToPadding = false
        panel.visibility = View.VISIBLE
        panel.bringToFront()
        // grows out of the keyboard (a part's card cross-fades in place)
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
        /** Small tonal badges in a row (a word's learner level). */
        fun badges(list: List<String>) {
            body.addView(FlowRow(ctx, dp(6)).apply {
                list.forEach { b ->
                    addView(textView(b, 12f, c.onSecondaryContainer).apply {
                        typeface = app.lychee.fonts.LycheeFonts.appFont(context, 500)
                        setPadding(dp(10), dp(3), dp(10), dp(3))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(8).toFloat()
                            setColor(c.secondaryContainer)
                        }
                    })
                }
                setPadding(0, dp(2), 0, dp(4))
            })
        }

        /** A verb's or adjective's forms: a section of two columns, what each is and the word in it. */
        fun conjugation(rows: List<app.lychee.core.Conjugated>) {
            section(ctx.getString(R.string.lychee_card_conjugation))
            rows.forEach { r ->
                body.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(3), 0, dp(3))
                    addView(textView(r.label, 13f, c.onSurfaceVariant),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(textView(r.text, 16f, c.onSurface).apply {
                        app.lychee.fonts.LycheeFonts.forText(ctx, r.text, fontLang)?.let { typeface = it }
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
                })
            }
            endSection()
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
        /** The card's one 粵/普 choice, shared by the header and every page. */
        var choice: LangChoice = LangChoice()
        private var speakLang: Lang?
            get() = choice.lang
            set(v) { choice.lang = v }

        /** On tabs: sections are a small label, not a box (the page is the box). */
        var flat = false
        /** The tab this content fills: a section of the same name needs no label. */
        var pageTitle: String? = null

        /** The language choice starts from Settings (the keyboard's language by default). */
        fun begin(r: Resolved) {
            if (speakLang == null) speakLang = when (settings.getString(PREF_SPEAK_LANG, "auto")) {
                "yue" -> Lang.Cantonese
                "cmn" -> Lang.Mandarin
                else -> if (r.lang == Lang.Mandarin) Lang.Mandarin else Lang.Cantonese
            }
            // the word's main reading in each language, which the summary's meanings follow
            r.reading?.let { mainReading[r.lang] = norm(it) }
            r.otherReading?.let { mainReading[if (r.lang == Lang.Mandarin) Lang.Cantonese else Lang.Mandarin] = norm(it) }
        }

        /**
         * The card's header: the word large, its other script, the 粵/普 switch and speaker, and
         * under them the reading of the whole word (every syllable, wrapping) in the chosen language.
         */
        fun header(text: String, r: Resolved) {
            val main = r.headwords.firstOrNull()
            if (main != null && !r.composed && main.traditional != main.simplified) {
                val first = if (prefs.simplified) main.simplified else main.traditional
                val second = if (prefs.simplified) main.traditional else main.simplified
                heading(first, text, otherForm(first, second))
            } else {
                heading(text, text, (Annotator.otherScript(text, r) ?: charByChar(text))?.let { otherForm(text, it) })
            }
            if (!showChinese) return
            val line = textView("", 16f, c.primary).apply {
                typeface = app.lychee.fonts.LycheeFonts.appFont(context, 500)
                setPadding(0, dp(2), 0, 0)
            }
            fun refresh() {
                val lang = speakLang ?: r.lang
                val reading = if (lang == r.lang) r.reading else r.otherReading
                line.text = reading?.takeIf { !it.contains('?') }?.let { Annotator.format(it, lang, prefs) }
                    ?: runCatching { Readings.annotator?.romanise(text, lang, prefs) }.getOrNull().orEmpty()
                line.visibility = if (line.text.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            refresh()
            choice.listeners += { refresh() }
            body.addView(line)
        }

        /**
         * One tab of a Chinese card. [openPart]: a character or piece opened as its own card.
         */
        fun page(p: Page, text: String, r: Resolved, openPart: (String) -> Unit) {
            val main = r.headwords.firstOrNull()
            spokenWord = text
            when (p) {
                Page.SUMMARY -> summary(r)
                Page.READINGS -> {
                    // a Kanji or Hanja: its Japanese or Korean readings first
                    local?.forEach { lr ->
                        val speak = onSpeakLocal
                        if (lr.items.isEmpty() || speak == null) {
                            body.addView(readingRow(labelled(lr.label, lr.shown), speak?.let { say -> smallSpeaker { say(lr.say) } }))
                        } else {
                            chipLines(lr.label, lr.items.map { (native, roman, said) -> ReadingChip(roman, native) { speak(said) } }, perLine = 2)
                                .forEach { body.addView(it) }
                        }
                    }
                    if (showChinese) readingRows(r, main, text)
                }
                Page.DEFINITIONS -> {
                    if (main != null && !r.composed) facts(main)
                    r.headwords.forEachIndexed { i, hw ->
                        if (r.composed || i > 0) {
                            if (i > 0) body.addView(divider())
                            body.addView(entryChip(if (prefs.simplified) hw.simplified else hw.traditional))
                        }
                        senses(hw, r)
                    }
                }
                Page.PARTS -> {
                    strokes(text)
                    val cps = text.codePoints().toArray()
                    if (r.composed && r.headwords.isNotEmpty()) {
                        r.headwords.forEach { hw -> part(hw, r.lang) { openPart(if (prefs.simplified) hw.simplified else hw.traditional) } }
                    } else if (cps.size > 1) {
                        // each character of the word, to open on its own
                        val mode = if (r.lang == Lang.Mandarin) app.lychee.core.InputMode.Mandarin else app.lychee.core.InputMode.Cantonese
                        cps.map { String(Character.toChars(it)) }.filter { Annotator.isHan(it.codePointAt(0)) }.distinct().forEach { ch ->
                            Readings.annotator?.resolve(ch, "", mode)?.headwords?.firstOrNull()?.let { hw -> part(hw, r.lang) { openPart(ch) } }
                        }
                    }
                }
                Page.EXAMPLES -> {
                    if (!showChinese) return
                    val lang = if (r.lang == Lang.Cantonese) "yue" else "cmn"
                    val forms = listOfNotNull(text, main?.traditional, main?.simplified)
                    val romanLang = if (r.lang == Lang.Mandarin) Lang.Mandarin else Lang.Cantonese
                    examples(app.lychee.dict.PackData.examples(ctx, lang, forms), sayIn = romanLang) { sentence ->
                        Readings.annotator?.romanise(sentence, romanLang, prefs)
                    }
                }
            }
        }

        /** An entry's word in a small tonal chip, setting its meanings apart from the next entry's. */
        fun entryChip(form: String): View = textView(form, 18f, c.onSecondaryContainer).apply {
            app.lychee.fonts.LycheeFonts.forText(ctx, form, fontLang)?.let { typeface = it }
            setPadding(dp(10), dp(3), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(c.secondaryContainer)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8); bottomMargin = dp(4) }
        }

        /** A part of speech, small and italic, over the meanings. */
        fun partOfSpeech(pos: String): View = textView(pos, 12f, c.onSurfaceVariant).apply {
            setTypeface(typeface, Typeface.ITALIC)
            setPadding(0, dp(4), 0, dp(2))
        }

        /** A piece of a phrase and its whole meaning, the piece set apart on the left. */
        fun pieceLine(form: String, meaning: String): View = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
            addView(textView(form, 16f, c.onSurface).apply {
                app.lychee.fonts.LycheeFonts.forText(ctx, form, fontLang)?.let { typeface = it }
                minWidth = dp(44)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(12) })
            addView(textView(meaning, 15f, c.onSurface).apply { setLineSpacing(0f, 1.1f) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        /**
         * The summary's meanings: the first few of the keyboard's language (else any), one or two
         * lines each, and the measure word, enough to tell whether this is the word wanted.
         */
        private fun summary(r: Resolved) {
            if (r.composed) {
                // a phrase read piece by piece: each piece and its whole meaning
                body.addView(note(ctx.getString(R.string.lychee_card_phrase_note)))
                r.headwords.forEach { hw ->
                    val form = if (prefs.simplified) hw.simplified else hw.traditional
                    val gloss = hw.senses.firstOrNull { it.partOfSpeech != "cl" && it.gloss.isNotBlank() }?.gloss ?: return@forEach
                    body.addView(pieceLine(form, gloss))
                }
                return
            }
            val wanted = if (r.lang == Lang.Mandarin) Lang.Mandarin else Lang.Cantonese
            // the most useful meanings of each entry (at most three entries), each in full; with
            // more than one entry, each starts with its word in a chip, so words and meanings
            // never run together
            val entries = r.headwords.take(3)
            entries.forEachIndexed { index, hw ->
                // the meanings of its most common reading first (rarer readings are on Definitions)
                val all = hw.senses.filter { it.partOfSpeech != "cl" && !it.gloss.startsWith("variant of") && !it.gloss.startsWith("see ") }
                    .let { list ->
                        list.filter { s -> s.reading.isNullOrBlank() || s.lang == Lang.Any || mainReading[s.lang]?.let { m -> norm(s.reading!!) == m } != false }
                            .ifEmpty { list }
                    }
                val list = (all.filter { it.lang == wanted } + all.filter { it.lang != wanted }).distinctBy { it.gloss.lowercase() }
                if (list.isEmpty()) return@forEachIndexed
                if (entries.size > 1) body.addView(entryChip(if (prefs.simplified) hw.simplified else hw.traditional))
                list.take(if (index == 0) SUMMARY_SENSES else 2).forEachIndexed { i, s -> plainSense(i + 1, s.gloss) }
            }
            val hw = r.headwords.firstOrNull() ?: return
            if (!showChinese) return
            val measure = hw.info.measureWordList()
            if (measure.isNotEmpty()) {
                val script = { t: String, s: String -> if (prefs.simplified) s else t }
                body.addView(labelled(ctx.getString(R.string.lychee_card_measure_word).trimEnd(':'),
                    measure.take(2).joinToString("  ·  ") { (t, s, _) -> script(t, s) }).apply { setPadding(0, dp(8), 0, 0) })
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
        private fun readingRows(r: Resolved, word: Headword?, spoken: String?) {
            // a phrase read piece by piece: the whole phrase's readings, never its first piece's
            val hw = if (r.composed) null else word
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
                cr.details?.let { d ->
                    col.addView(textView(d, 12f, c.onSurfaceVariant).apply { setPadding(0, 0, 0, dp(2)) })
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
            // outlined, not filled: 粵 or 普, the card's language; a tap switches it everywhere
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = app.lychee.fonts.LycheeFonts.chinese(ctx) ?: Typeface.DEFAULT
            setTextColor(c.primary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), c.primary)
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(4) }
            fun update() {
                text = if (speakLang == Lang.Mandarin) "普" else "粵"
                contentDescription = ctx.getString(R.string.lychee_card_speak_language) + ": " +
                    ctx.getString(if (speakLang == Lang.Mandarin) R.string.lychee_card_mandarin else R.string.lychee_card_cantonese)
            }
            update()
            choice.listeners += { update() }
            setOnClickListener {
                speakLang = if (speakLang == Lang.Mandarin) Lang.Cantonese else Lang.Mandarin
                choice.changed()
            }
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

        /**
         * A Japanese or Korean phrase's pieces under [title]: each word or character large on the
         * left, its reading in the accent colour and its meaning under it (at most [limit]).
         */
        fun phraseParts(title: String, parts: List<PhrasePart>, limit: Int, onOpen: ((PhrasePart) -> Unit)? = null) {
            section(title)
            parts.take(limit).forEach { p ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                }
                row.addView(textView(p.form, 24f, c.onSurface).apply {
                    typeface = app.lychee.fonts.LycheeFonts.forText(ctx, p.form, fontLang) ?: Typeface.DEFAULT
                    minWidth = dp(48)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = dp(14) })
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                p.reading?.let { col.addView(textView(it, 15f, c.primary).apply {
                    typeface = app.lychee.fonts.LycheeFonts.appFont(context, 600)
                }) }
                col.addView(textView(p.full ?: p.meaning, 14f, c.onSurfaceVariant).apply { setPadding(0, dp(1), 0, 0) })
                row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                onSpeak?.let { say -> row.addView(smallSpeaker { say(p.form, null) }) }
                onOpen?.let { open -> opens(row) { open(p) } }
                body.addView(row)
            }
            endSection()
        }

        /** [row] opens something of its own: it ripples when tapped and ends in a chevron. */
        private fun opens(row: LinearLayout, onClick: () -> Unit) {
            row.addView(android.widget.ImageView(ctx).apply {
                setImageResource(R.drawable.ic_sym_chevron_right)
                imageTintList = android.content.res.ColorStateList.valueOf(c.onSurfaceVariant)
                contentDescription = ctx.getString(R.string.lychee_card_open_part)
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(4); gravity = Gravity.CENTER_VERTICAL })
            row.background = TypedValue().let { v ->
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
                ctx.getDrawable(v.resourceId)
            }
            row.setOnClickListener { onClick() }
        }

        /** One character or word of a composed candidate: both languages' readings, and its meaning. */
        private fun part(hw: Headword, lang: Lang, onOpen: (() -> Unit)? = null) {
            val langs = if (lang == Lang.Mandarin) listOf(Lang.Mandarin, Lang.Cantonese) else listOf(Lang.Cantonese, Lang.Mandarin)
            val readings = langs.mapNotNull { l -> hw.readings[l]?.firstOrNull()?.let { Annotator.format(it, l, prefs) } }
                .joinToString("  ·  ")
            // the meaning in full, never cut
            val gloss = hw.senses.firstOrNull { it.partOfSpeech != "cl" }?.gloss ?: ""
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
            onOpen?.let { opens(row, it) }
            body.addView(row)
        }

        private fun section(title: String) {
            if (!flat) {
                body = sectionCard(title)
                return
            }
            // on a tab: a small label in the accent colour (none when it repeats the tab's name)
            body = root
            if (title.isBlank() || title == pageTitle) return
            body.addView(textView(title, 13f, c.primary).apply {
                typeface = app.lychee.fonts.LycheeFonts.appFont(context, 600)
                setPadding(0, dp(14), 0, dp(4))
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

        fun note(t: String) = textView(t, 13f, c.onSurfaceVariant).apply {
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
