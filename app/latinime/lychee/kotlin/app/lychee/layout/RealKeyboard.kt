package app.lychee.layout

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import com.android.inputmethod.keyboard.KeyboardId
import com.android.inputmethod.keyboard.KeyboardLayoutSet
import com.android.inputmethod.keyboard.KeyboardTheme
import com.android.inputmethod.keyboard.MainKeyboardView
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.RichInputMethodSubtype
import com.android.inputmethod.latin.utils.ResourceUtils

/**
 * Lychee's real input view outside the keyboard, for Settings' previews, the add-on examples
 * and the screenshots: the keyboard service's own layout (input_view: the top bar, the candidate
 * bar, the real MainKeyboardView and its panels), themed as KeyboardSwitcher themes it, with a
 * real keyboard built by KeyboardLayoutSet from Lychee's key layouts, arranged by LayoutApplier.
 * Nothing is drawn by hand, so it is exactly the keyboard.
 *
 * Settings not saved yet (a slider while it moves, light or dark chosen for an example) are
 * given as [overrides] of the keyboard's own preferences: every part of the keyboard reads its
 * settings through its context, so it sees them as if saved, and nothing is written.
 */
object RealKeyboard {

    /** The subtypes as method.xml declares them; [engine]: yue, cmn, ja, ko, or null for English. */
    private fun subtype(engine: String?): InputMethodSubtype {
        val (locale, extra) = when (engine) {
            "yue" -> "zh_HK" to "KeyboardLayoutSet=qwerty,AsciiCapable,EmojiCapable,LycheeEngine=yue"
            "cmn" -> "zh_CN" to "KeyboardLayoutSet=qwerty,AsciiCapable,EmojiCapable,LycheeEngine=cmn"
            "ja" -> "ja" to "KeyboardLayoutSet=qwerty,AsciiCapable,EmojiCapable,LycheeEngine=ja"
            "ko" -> "ko" to "KeyboardLayoutSet=qwerty,AsciiCapable,EmojiCapable,LycheeEngine=ko"
            else -> "en_US" to "TrySuppressingImeSwitcher,AsciiCapable,SupportTouchPositionCorrection,EmojiCapable"
        }
        @Suppress("DEPRECATION")
        return InputMethodSubtype.InputMethodSubtypeBuilder()
            .setSubtypeLocale(locale).setSubtypeMode("keyboard").setSubtypeExtraValue(extra)
            .setIsAsciiCapable(true).build()
    }

    /** [context] with [overrides] over the keyboard's preferences (none: the context itself). */
    fun withSettings(context: Context, overrides: Map<String, Any?>): Context =
        if (overrides.isEmpty()) context else Overridden(context, overrides)

    /** The keyboard theme's context, exactly as KeyboardSwitcher makes it. */
    fun themeContext(context: Context): Context {
        val theme = KeyboardTheme.getKeyboardTheme(context)
        val themed = ContextThemeWrapper(context, theme.mStyleId)
        app.lychee.ui.KeyStyle.override(context, null)?.let { themed.applyOverrideConfiguration(it) }
        themed.theme.applyStyle(app.lychee.ui.LycheeColorStyle.overlay(context), true)
        return themed
    }

    /**
     * The real input view with the real keyboard for [engine] ([element]: the letters, the
     * symbols ...), at [width] px, in a chat's text box. Returns the input view and its keys.
     */
    fun inputView(context: Context, engine: String?, width: Int, element: Int = KeyboardId.ELEMENT_ALPHABET): Pair<View, MainKeyboardView> {
        // set up as the keyboard service sets it up before any keyboard is shown
        com.android.inputmethod.accessibility.AccessibilityUtils.init(context)
        val editor = EditorInfo().apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            packageName = "com.example.chat"
        }
        val themed = themeContext(context)
        val input = LayoutInflater.from(themed).inflate(R.layout.input_view, null)
        val keys = input.findViewById<MainKeyboardView>(R.id.keyboard_view)
        val layout = LycheeLayout.get(themed)
        // a keyboard of these settings, not one kept from before them
        KeyboardLayoutSet.onKeyboardThemeChanged()
        val builder = KeyboardLayoutSet.Builder(themed, editor)
        builder.setKeyboardGeometry(layout.keyboardWidth(width), layout.keyboardHeight(ResourceUtils.getDefaultKeyboardHeight(themed.resources)))
        builder.setLycheeOptions(layout.numberRow, layout.emojiKey, layout.sideKey)
        builder.setSubtype(RichInputMethodSubtype(subtype(engine)))
        builder.setVoiceInputKeyEnabled(true)
        builder.setLanguageSwitchKeyEnabled(true)
        builder.setSplitLayoutEnabledByUser(false)
        keys.setKeyboard(builder.build().getKeyboard(element))
        // drawn as the keys end up (they fade in on the phone)
        keys.lycheeFadeIn(emptySet(), 0)
        @Suppress("DEPRECATION")
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(themed)
        keys.setLycheeBorders(prefs.getBoolean(app.lychee.ui.KeyStyle.PREF_BORDERS, true))
        input.findViewById<com.android.inputmethod.latin.suggestions.SuggestionStripView>(R.id.suggestion_strip_view)
            ?.setLycheeVoiceEnabled(prefs.getBoolean(com.android.inputmethod.latin.settings.Settings.PREF_VOICE_INPUT_KEY, true))
        // one-handed or full width, as LatinIME arranges it
        LayoutApplier.apply(input)
        return input to keys
    }

    /**
     * [candidates] in [input]'s real candidate bar, arranged as ChineseInput arranges it on the
     * phone (updateHeight, updateEdges): the bar's style and [lines], its height (the readings'
     * lines and the line for the letters typed), [typed] in that line (null: none, as while
     * writing, when the guesses sit at the bar's top, ⌄ and the title beside them and the pad
     * lifted under them), clear of Back on the left and 繁/简 ([script]) and ⌄ on the right;
     * the shortcuts make way. [fontLang]: yue, cmn, ja, ko. [annotate] gives each its readings and
     * meaning; Chinese ones without it get theirs from the dictionary, as the keyboard formats them.
     */
    fun showCandidates(input: View, candidates: List<String>, typed: String?, fontLang: String,
                       lines: app.lychee.chinese.CandidateBarView.Lines, script: Boolean,
                       annotate: (app.lychee.rime.Candidate) -> app.lychee.core.Annotation? = { null }) {
        val ctx = input.context
        val res = ctx.resources
        val dm = res.displayMetrics
        val bar = input.findViewById<app.lychee.chinese.CandidateBarView>(R.id.lychee_candidate_bar) ?: return
        val strip = input.findViewById<com.android.inputmethod.latin.suggestions.SuggestionStripView>(R.id.suggestion_strip_view)
        val more = input.findViewById<View>(R.id.lychee_candidate_more)
        val standard = res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        val line = res.getDimensionPixelSize(R.dimen.lychee_preedit_height)
        val chinese = fontLang == "yue" || fontLang == "cmn"
        bar.style = app.lychee.chinese.CandidateBarView.Style(lang = null, fontLang = fontLang)
        // English (handwriting guesses): Latin candidates allowed, as ChineseInput allows them there
        bar.allowLatin = fontLang == "en"
        bar.lines = lines
        val height = maxOf(standard, bar.heightFor(lines) + line)
        for (v in listOfNotNull(strip, bar)) v.layoutParams = v.layoutParams.apply { this.height = height }
        val writing = typed == null
        // the letters typed on their own line above; while writing none, and the guesses at the top
        if (!writing) bar.setPadding(0, line, 0, 0) else bar.setPadding(0, 0, 0, line)
        input.findViewById<android.widget.TextView>(R.id.lychee_preedit)?.let { p ->
            p.text = typed.orEmpty()
            p.visibility = if (!typed.isNullOrEmpty()) View.VISIBLE else View.GONE
        }
        (more?.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { lp ->
            lp.gravity = android.view.Gravity.END or (if (writing) android.view.Gravity.TOP else android.view.Gravity.BOTTOM)
            lp.height = if (writing) maxOf(standard, height - line) else standard
        }
        strip?.lycheeToolbar?.let { t ->
            if (writing) (t.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.bottomMargin = line; lp.height = maxOf(standard, height - line); t.layoutParams = lp
            }
        }
        if (writing) (input.findViewById<View>(R.id.lychee_handwriting_panel) as? app.lychee.handwriting.HandwritingPanelView)?.lift = line
        bar.visibility = View.VISIBLE
        more?.visibility = View.VISIBLE
        strip?.setLycheeCandidatesShowing(true)
        if (script && !writing) strip?.setLycheeScriptKey(false) {}
        @Suppress("DEPRECATION")
        val display = app.lychee.chinese.Readings.barPrefs(android.preference.PreferenceManager.getDefaultSharedPreferences(ctx))
        val mode = if (fontLang == "cmn") app.lychee.core.InputMode.Mandarin else app.lychee.core.InputMode.Cantonese
        val annotation: (app.lychee.rime.Candidate) -> app.lychee.core.Annotation? = { c ->
            annotate(c) ?: if (chinese) app.lychee.chinese.Readings.annotator?.annotate(c.text, "", mode, display) else null
        }
        val start = strip?.lycheeStartWidth ?: 0
        val edge = strip?.lycheeEdgeWidth ?: 0
        val moreWidth = res.getDimensionPixelSize(R.dimen.lychee_candidate_more_width)
        (bar.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp -> lp.marginStart = start; lp.marginEnd = edge + moreWidth; bar.layoutParams = lp }
        (more?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { it.marginEnd = edge }
        input.findViewById<View>(R.id.lychee_preedit)?.let { p ->
            (p.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.marginStart = start + (12 * dm.density).toInt()
        }
        // laid out now, so the candidates are placed at once (not on the next frame)
        val w = (input.width.takeIf { it > 0 } ?: dm.widthPixels) - start - edge - moreWidth
        bar.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        bar.layout(0, 0, w, height)
        bar.setForScreens(candidates.map { app.lychee.rime.Candidate(it, "") }, annotation)
    }

    /** A context whose default preferences read [overrides] first. */
    private class Overridden(base: Context, private val overrides: Map<String, Any?>) : ContextWrapper(base) {
        private val prefsName = packageName + "_preferences"
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            val real = super.getSharedPreferences(name, mode)
            return if (name == prefsName) Prefs(real, overrides) else real
        }
        // views made from it keep it (not the application's context)
        override fun getApplicationContext(): Context = this
    }

    /** Preferences that read [overrides] over [real]; writes go to [real]. */
    private class Prefs(private val real: SharedPreferences, private val overrides: Map<String, Any?>) : SharedPreferences by real {
        private inline fun <reified T> get(key: String?, fallback: () -> T): T =
            if (key != null && overrides.containsKey(key)) (overrides[key] as? T) ?: fallback() else fallback()
        override fun getString(key: String?, defValue: String?): String? =
            if (key != null && overrides.containsKey(key)) (overrides[key] as String?) ?: defValue else real.getString(key, defValue)
        override fun getInt(key: String?, defValue: Int): Int = get(key) { real.getInt(key, defValue) }
        override fun getLong(key: String?, defValue: Long): Long = get(key) { real.getLong(key, defValue) }
        override fun getFloat(key: String?, defValue: Float): Float = get(key) { real.getFloat(key, defValue) }
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = get(key) { real.getBoolean(key, defValue) }
        override fun contains(key: String?): Boolean = (key != null && overrides.containsKey(key)) || real.contains(key)
        override fun getAll(): MutableMap<String, *> = real.all.toMutableMap().apply { putAll(overrides) }
    }
}
