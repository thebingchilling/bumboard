package app.lychee.handwriting

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.lychee.lycheeHost
import app.lychee.fonts.LycheeFonts
import app.lychee.layout.LycheePanels
import app.lychee.ui.M3Colors
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants
import java.util.concurrent.Executors

/**
 * Handwriting over the keys, like Gboard's: write a character anywhere on the pad; the guesses
 * show in the bar, and the best one is typed as the next character is begun (or space, Enter or
 * a key is pressed), so characters can be written one after another; tap another guess to type
 * that instead. The guesses show in the candidate bar, with
 * their readings: tap one to replace what was typed. After each character the bar suggests
 * what usually follows it (校, 生, 習 after 學), from the readings dictionary's words.
 * The ink fades away by itself a couple of seconds after the last stroke (its guesses stay in
 * the bar to pick from). ⌫ never touches the ink: it clears the guesses while they show,
 * otherwise deletes.
 *
 * A mode, like text editing: the Handwriting shortcut (Chinese and Japanese keyboards only)
 * opens it over the keys, with its name in the bar above, leaving the keyboard's own bottom row
 * and candidate bar as they are; Back and ⌫ are keys in the pad's corners (the phone's Back
 * closes it too). The keys of that bottom row reach the pad first ([onKey]): what is written
 * goes in before them, and ?123 or emoji close the pad.
 *
 * Everything moves: the ink fades away moments after the last stroke (and at once, softly, when
 * a guess is picked), while the next character can already be written over it.
 *
 * Recognition is PaddleOCR's PP-OCRv5 text recogniser ([OcrRecognizer], opened only while the
 * pad is open): the ink is drawn as a picture and read, so stroke order and joined-up strokes
 * don't matter. Chinese and Japanese use its Chinese model, Korean its Korean model; on Japanese
 * the stroke matcher adds the kana, which the model mistakes for look-alike kanji.
 */
class HandwritingPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val palette = M3Colors.of(context)
    private val main = Handler(Looper.getMainLooper())
    private val pad = Pad()
    /** "Write here", faint in the middle of the empty pad. */
    private val hint = TextView(context).apply {
        setText(R.string.lychee_hw_write_here)
        setTextColor(palette.onSurfaceVariant)
        alpha = 0.7f
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        visibility = GONE
    }
    private val message = TextView(context).apply {
        setTextColor(palette.onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        setPadding(dp(24), 0, dp(24), 0)
    }
    private var ocr: OcrRecognizer? = null
    /** What the last automatic commit typed, so a tapped candidate can replace it. */
    private var lastTyped: String? = null
    private var guesses: List<String> = emptyList()
    /**
     * The best guess for the character last written, not yet typed: it is typed, as Gboard
     * does, when the next character is begun, or space, Enter or another key is pressed, or
     * the pad closes. Tapping another guess types that instead; ⌫ while guesses show drops it.
     */
    private var unpicked: String? = null

    // before init, which places them
    private val backKey: View by lazy {
        key(R.drawable.ic_sym_arrow_back, R.string.lychee_back) { this@HandwritingPanelView.visibility = GONE }
    }
    /** ⌫ floating over the pad's corner: a round button, not a key, so the pad stays open. */
    private val deleteKey: View by lazy {
        FrameLayout(context).apply {
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_sym_backspace)
                imageTintList = ColorStateList.valueOf(palette.onSurface)
                scaleType = ImageView.ScaleType.CENTER
                background = LycheePanels.raisedRound(context, palette.surfaceContainerHigh)
                contentDescription = context.getString(R.string.lychee_emoji_delete)
            }, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
        }.also { repeatOnHold(it, ::backspace) }
    }

    init {
        orientation = VERTICAL
        isClickable = true
        setBackgroundColor(palette.surface)
        // a hairline between the candidates above and the pad, like the clipboard's
        addView(View(context).apply {
            setBackgroundColor(palette.outlineVariant)
            alpha = 0.7f
        }, LayoutParams(LayoutParams.MATCH_PARENT, 1))
        // the guesses show in the candidate bar above (lycheeShowGuesses), so the pad gets the room
        // the pad has no background of its own: write straight on the keyboard
        // the pad over the keyboard's upper rows; its bottom row (?123, the comma, space, the
        // period, Enter) and the candidate bar above stay the keyboard's own, but for Back laid
        // over ?123 (there is nothing else to switch to while writing). ⌫ floats in the pad's corner
        addView(FrameLayout(context).apply {
            addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(pad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(deleteKey, FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(8), dp(8))
            })
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        visibility = GONE
    }

    /**
     * Over the keys but their bottom row (the keyboard's own stays usable), with Back over its
     * ?123 key.
     */
    private fun fitToKeys() {
        val row = LycheePanels.keyRow(this)
        val lp = layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.height <= 0) return
        val bottomRow = row.height + row.bottomPadding
        lp.height -= bottomRow
        lp.bottomMargin += bottomRow
        layoutParams = lp
        placeBack()
    }

    /** Back, laid exactly over the keyboard's ?123 key (beside the pad, in the panel's parent). */
    private fun placeBack() {
        val parent = parent as? FrameLayout ?: return
        val keys = rootView.findViewById<com.android.inputmethod.keyboard.MainKeyboardView>(R.id.keyboard_view) ?: return
        val key = keys.keyboard?.getKey(Constants.CODE_SWITCH_ALPHA_SYMBOL) ?: return
        val at = IntArray(2).also { keys.getLocationInWindow(it) }
        val origin = IntArray(2).also { parent.getLocationInWindow(it) }
        if (backKey.parent !== parent) {
            (backKey.parent as? android.view.ViewGroup)?.removeView(backKey)
            parent.addView(backKey)
        }
        val half = LycheePanels.keyRow(this).gap / 2
        backKey.layoutParams = FrameLayout.LayoutParams(key.width + half * 2, key.height + half * 2, Gravity.TOP or Gravity.START).apply {
            leftMargin = at[0] - origin[0] + keys.paddingLeft + key.x - half
            topMargin = at[1] - origin[1] + keys.paddingTop + key.y - half
        }
        backKey.setPadding(half, half, half, half)
        backKey.visibility = VISIBLE
        backKey.bringToFront()
    }

    /**
     * A key of the keyboard pressed while writing: what is on the pad goes in first; keys that
     * change the keyboard (the symbols, emoji) close the pad. Returns true when the pad used it.
     */
    fun onKey(code: Int): Boolean {
        if (visibility != VISIBLE) return false
        when (code) {
            Constants.CODE_DELETE -> {
                // ⌫ while guesses show drops them, as the pad's own ⌫ does
                if (guesses.isNotEmpty()) {
                    backspace()
                    return true
                }
                lastTyped = null
                return false
            }
            Constants.CODE_SWITCH_ALPHA_SYMBOL, Constants.CODE_EMOJI, Constants.CODE_SHORTCUT -> {
                commitPending()
                visibility = GONE
                return false
            }
        }
        commitPending()
        lastTyped = null
        return false
    }


    /** Held, [frame]'s key repeats [action] like the keyboard's ⌫ (after a pause, then quickly). */
    @SuppressLint("ClickableViewAccessibility")
    private fun repeatOnHold(frame: View, action: () -> Unit) {
        val target = (frame as? FrameLayout)?.getChildAt(0) ?: frame
        val again = object : Runnable {
            override fun run() {
                action()
                main.postDelayed(this, 50)
            }
        }
        target.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    action()
                    main.postDelayed(again, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    main.removeCallbacks(again)
                }
            }
            true
        }
    }

    /** Types the character still on the pad, before a key of the keyboard does anything. */
    fun flush() {
        if (visibility == VISIBLE) commitPending()
    }

    private fun toolbar(): app.lychee.toolbar.ToolbarView? = rootView.findViewById(R.id.lychee_toolbar)

    fun show() {
        visibility = VISIBLE
        // the bar above shows "Handwriting" (Back is the key bottom left)
        toolbar()?.showTitle(R.string.lychee_sc_handwriting, withBack = false) { visibility = GONE }
        fitToKeys()
        pad.clear()
        updateHint()
        lastTyped = null
        showGuesses(emptyList())
        release()
        app.lychee.chinese.Readings.open(context)
        val engine = lycheeHost()?.lycheeEngine()
        val lang = if (engine == "ko") "ko" else "zh"
        // the model opens off the main thread (it is read from the APK); what is written
        // meanwhile is read as soon as it is open
        worker.execute {
            val opened = OcrRecognizer.open(context, lang)
            main.post {
                if (visibility == VISIBLE && ocr == null) {
                    ocr = opened
                    message.visibility = if (opened == null) VISIBLE else GONE
                    updateHint()
                    if (pad.hasInk) recognise()
                } else if (opened != null) {
                    worker.execute { opened.close() }
                }
            }
        }
        // the characters' stroke shapes (a second opinion for Chinese and Japanese, and the
        // kana's shapes); and the Chinese dictionary's characters, for the filter
        if (engine != "ko") loadMatcher(context) { if (visibility == VISIBLE && pad.hasInk) recognise() }
        if (engine != "ko" && engine != "ja") worker.execute { chineseCharacters() }
        message.visibility = GONE
        message.text = context.getString(R.string.lychee_hw_unavailable)
        updateHint()
    }

    override fun setVisibility(visibility: Int) {
        val closing = visibility != VISIBLE && this.visibility == VISIBLE
        if (closing) {
            commitPending(now = true)
            main.removeCallbacksAndMessages(null)
            release()
            guesses = emptyList()
            toolbar()?.let {
                if (it.mode == app.lychee.toolbar.ToolbarView.Mode.TITLE) it.mode = app.lychee.toolbar.ToolbarView.Mode.NORMAL
            }
        }
        super.setVisibility(visibility)
        if (visibility != VISIBLE) backKey.visibility = GONE
        // after the pad has gone, so the bar's 英 comes back (it is hidden while writing)
        if (closing) lycheeHost()?.lycheeShowGuesses(emptyList()) {}
    }

    /** Closes the model on the recognition thread, after any recognition still running. */
    private fun release() {
        val o = ocr ?: return
        ocr = null
        worker.execute { o.close() }
    }

    // ------------------------------------------------------------------ recognition

    /** Whether the recogniser is open. */
    private val ready: Boolean
        get() = ocr != null

    /** Bumped whenever the ink changes; guesses belong to the ink they were made for. */
    private var inkVersion = 0
    private var guessedVersion = -1
    /** Type the guess as soon as the guesses for the whole ink arrive (see [commitPending]). */
    private var commitWhenGuessed = false

    private fun recognise() {
        val ink = pad.strokeList()
        if (ink.isEmpty()) return
        val version = ++inkVersion
        unpicked = null
        val o = ocr
        val m = matcher
        val engine = lycheeHost()?.lycheeEngine()
        // the character before the cursor: what usually follows it ranks a little higher
        val before = runCatching {
            lycheeHost()?.lycheeInputConnection()?.getTextBeforeCursor(2, 0)?.toString()
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { t -> String(Character.toChars(t.codePointBefore(t.length))) }
        worker.execute {
            val started = System.currentTimeMillis()
            val result = runCatching { rank(o, m, ink, engine, before).take(10) }
                .onFailure { app.lychee.DiagLog.log("handwriting failed", it) }
                .getOrDefault(emptyList())
            val took = System.currentTimeMillis() - started
            if (took > 400) app.lychee.DiagLog.log("handwriting took $took ms")
            main.post {
                // a newer stroke has its own recognition coming: these guesses are stale
                if (version != inkVersion || !pad.hasInk) return@post
                guessedVersion = version
                lastInk = null
                showGuesses(result)
                unpicked = guesses.firstOrNull()
                if (commitWhenGuessed) commitPending()
            }
        }
    }

    /**
     * PP-OCR's guesses (its confidence, 0..1), only characters of the keyboard's language
     * (Chinese characters; Japanese adds the kana; Korean, Hangul), with common characters a
     * little ahead (the readings dictionary's frequency). On Japanese the stroke matcher's
     * kana come in too.
     */
    private fun rank(o: OcrRecognizer?, m: StrokeMatcher?, ink: List<FloatArray>, engine: String?, before: String? = null): List<String> {
        // each keyboard only its own language's characters: Korean, Hangul; Japanese, kana and
        // kanji; Chinese, the characters its dictionary has (no kana, Hangul, or characters
        // only Japanese or Korean use)
        val chinese = if (engine == "ko" || engine == "ja") null else chineseCharacters()
        val allowed: (String) -> Boolean = when (engine) {
            "ko" -> { ch -> ch.length == 1 && ch[0] in '\uAC00'..'\uD7A3' }
            "ja" -> { ch -> isKana(ch) || isHan(ch) }
            else -> { ch -> isHan(ch) && (chinese == null || ch in chinese) }
        }
        val score = LinkedHashMap<String, Float>()
        o?.recognise(ink, 20, allowed)?.forEach { (ch, p) -> score[ch] = p }
        if (engine != "ko" && m != null) {
            // a second opinion from the strokes' shapes and directions (the stroke-order data),
            // which a roughly drawn character fools differently from the picture: agreement
            // lifts a character, and one the picture missed can still come up
            m.match(ink, 30).filter { (ch, _) -> isHan(ch) && allowed(ch) }.forEach { (ch, distance) ->
                val s = (1f - distance / HAN_DISTANCE).coerceAtLeast(0f) * HAN_WEIGHT
                if (s > 0f) score[ch] = (score[ch] ?: 0f) + s
            }
        }
        if (engine == "ja" && m != null) {
            // PP-OCR takes kana for look-alike kanji (く for 人, け for 十): the stroke matcher
            // knows their shapes
            m.match(ink, 40).filter { (ch, _) -> isKana(ch) }.take(5).forEach { (ch, distance) ->
                val s = (1f - distance / KANA_DISTANCE).coerceAtLeast(0f) * KANA_WEIGHT
                if (s > (score[ch] ?: 0f)) score[ch] = s
            }
        }
        if (engine != "ko" && before != null && isHan(before)) {
            // characters that usually follow the one before the cursor (學 → 校, 生, 習)
            @Suppress("DEPRECATION")
            val simplified = app.lychee.chinese.ChineseScript.candidatesSimplified(
                android.preference.PreferenceManager.getDefaultSharedPreferences(context))
            val next = runCatching { app.lychee.chinese.Readings.db?.continuations(before, simplified, 40) }
                .getOrNull().orEmpty()
            next.forEachIndexed { i, word ->
                val ch = String(Character.toChars(word.codePointAt(0)))
                score[ch]?.let { score[ch] = it + CONTEXT * (1f - i / 40f) }
            }
        }
        // characters this person has picked before: a little higher each time (up to a limit)
        for (ch in score.keys.toList()) {
            val n = HandwritingPicks.count(context, ch)
            if (n > 0) score[ch] = score.getValue(ch) + PERSONAL * kotlin.math.min(1f, kotlin.math.ln(1f + n) / 3f)
        }
        if (engine != "ko") {
            val db = app.lychee.chinese.Readings.db
            val logTop = db?.let { kotlin.math.ln(it.maxFrequency + 1.0) }?.takeIf { it > 0 }
            if (db != null && logTop != null) {
                for (ch in score.keys.toList()) {
                    if (!isHan(ch)) continue
                    val f = runCatching { db.frequency(ch) }.getOrDefault(0L)
                    score[ch] = score.getValue(ch) + COMMON * (kotlin.math.ln(f + 1.0) / logTop).toFloat()
                }
            }
        }
        return score.entries.sortedByDescending { it.value }.map { it.key }
    }

    private fun isKana(ch: String) = ch.length == 1 && ch[0] in '\u3040'..'\u30FF'

    private fun isHan(ch: String) = ch.codePointCount(0, ch.length) == 1 &&
        Character.UnicodeScript.of(ch.codePointAt(0)) == Character.UnicodeScript.HAN

    /** The strokes of the last character the pad cleared by itself, kept for [pick]'s sample. */
    private var lastInk: List<FloatArray>? = null

    /**
     * A few seconds after the last stroke the ink fades out and the pad is ready for the next
     * character; the guesses stay in the bar until another character is written or one is picked.
     */
    private val autoClear: Runnable = object : Runnable {
        override fun run() {
            if (!pad.hasInk) return
            // its guesses first (the recogniser is still reading it)
            if (guessedVersion != inkVersion) {
                main.postDelayed(this, 250)
                return
            }
            lastInk = pad.strokeList()
            // the ink fades away; the next character can be written over it at once
            pad.fadeAway()
            // the guesses on show belong to this ink still: keep them
            guessedVersion = ++inkVersion
            updateHint()
        }
    }

    private fun scheduleAutoClear() {
        main.removeCallbacks(autoClear)
        main.postDelayed(autoClear, AUTO_CLEAR_MS)
    }

    private fun cancelAutoClear() {
        main.removeCallbacks(autoClear)
    }

    /**
     * Moving on (space, Enter, a key, the pad closing): the character written is typed as its
     * best guess (unless a guess was tapped or ⌫ dropped them), and the pad clears.
     */
    private fun commitPending(@Suppress("UNUSED_PARAMETER") now: Boolean = false) {
        main.removeCallbacks(autoCommit)
        commitWhenGuessed = false
        cancelAutoClear()
        val typed = unpicked != null
        autoType()
        lastInk = null
        if (!pad.hasInk) {
            // what follows (a space, a key) comes after it: its guesses are done with
            if (typed) showGuesses(emptyList())
            return
        }
        pad.fadeAway()
        inkVersion++
        showGuesses(emptyList())
        updateHint()
    }

    private val autoCommit = Runnable { commitPending() }

    /** Types the best guess for the character last written, if it hasn't been typed or picked. */
    private fun autoType() {
        val text = unpicked ?: return
        unpicked = null
        lycheeHost()?.lycheeType(text)
        lastTyped = text
        // the pad's ink (or the ink it cleared by itself) is a sample for the guess typed
        val ink = if (pad.hasInk) pad.strokeList() else lastInk
        if (ink != null) HandwritingSamples.save(context, ink, text, guesses, lycheeHost()?.lycheeEngine())
    }

    /** A tapped guess: replaces what was just typed, or types it when still writing. */
    private fun pick(text: String) {
        unpicked = null
        if (pad.hasInk) {
            main.removeCallbacks(autoCommit)
            cancelAutoClear()
            HandwritingSamples.save(context, pad.strokeList(), text, guesses, lycheeHost()?.lycheeEngine())
            pad.fadeAway()
            inkVersion++
            updateHint()
        } else if (lastInk != null) {
            // the character the pad already cleared by itself
            HandwritingSamples.save(context, lastInk!!, text, guesses, lycheeHost()?.lycheeEngine())
            lastInk = null
        } else {
            val previous = lastTyped
            if (previous != null) {
                repeat(previous.codePointCount(0, previous.length)) { lycheeHost()?.lycheeSendCode(Constants.CODE_DELETE) }
            }
        }
        lycheeHost()?.lycheeType(text)
        lastTyped = text
        HandwritingPicks.picked(context, text)
        showNext(text)
    }

    private fun space() {
        commitPending()
        lastTyped = null
        lycheeHost()?.lycheeSendCode(Constants.CODE_SPACE)
    }

    /**
     * ⌫ never touches the ink (it fades by itself): while guesses show it clears them, and
     * otherwise deletes from the text.
     */
    private fun backspace() {
        if (guesses.isNotEmpty()) {
            commitWhenGuessed = false
            unpicked = null
            lastInk = null
            showGuesses(emptyList())
            // what is still on the pad is read again with its next stroke
            if (pad.hasInk) guessedVersion = ++inkVersion
            return
        }
        lastTyped = null
        lycheeHost()?.lycheeShowGuesses(emptyList()) {}
        lycheeHost()?.lycheeSendCode(Constants.CODE_DELETE)
    }

    /** "Write here" while the pad is empty and ready. */
    private fun updateHint() {
        val show = ready && !pad.hasInk
        if (show == (hint.visibility == VISIBLE)) return
        hint.animate().cancel()
        if (show && LycheePanels.animate(context)) {
            hint.alpha = 0f
            hint.visibility = VISIBLE
            hint.animate().alpha(0.7f).setStartDelay(INK_FADE_MS).setDuration(app.lychee.layout.LycheeMotion.IN)
                .setInterpolator(app.lychee.layout.LycheeMotion.enterEasing).start()
        } else {
            hint.alpha = 0.7f
            hint.visibility = if (show) VISIBLE else GONE
        }
    }

    /** The recogniser's guesses for the ink on the pad, in the candidate bar. */
    private fun showGuesses(found: List<String>) {
        // writing Traditional: every written form of each character (為 then 爲)
        @Suppress("DEPRECATION")
        val traditional = !app.lychee.chinese.ChineseScript.candidatesSimplified(
            android.preference.PreferenceManager.getDefaultSharedPreferences(context))
        val list = if (traditional) app.lychee.chinese.Variants.expand(context, found) else found
        guesses = list
        lycheeHost()?.lycheeShowGuesses(list) { i -> list.getOrNull(i)?.let { pick(it) } }
    }

    /** What usually follows [typed]: tapping one types it and suggests what follows that. */
    private fun showNext(typed: String) {
        val last = typed.codePointBefore(typed.length).let { String(Character.toChars(it)) }
        @Suppress("DEPRECATION")
        val simplified = app.lychee.chinese.ChineseScript.candidatesSimplified(
            android.preference.PreferenceManager.getDefaultSharedPreferences(context))
        val next = runCatching { app.lychee.chinese.Readings.db?.continuations(last, simplified) }.getOrNull().orEmpty()
        lycheeHost()?.lycheeShowGuesses(next) { i ->
            next.getOrNull(i)?.let { text ->
                lycheeHost()?.lycheeType(text)
                lastTyped = null
                showNext(text)
            }
        }
    }

    // ------------------------------------------------------------------ views

    private fun key(
        icon: Int?, label: Int, isSpace: Boolean = false, pill: Boolean = false, enter: Boolean = false,
        onClick: () -> Unit,
    ): View {
        val view: View = if (icon != null) ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(if (enter) palette.onPrimary else palette.onSecondaryContainer)
            scaleType = ImageView.ScaleType.CENTER
        } else TextView(context).apply {
            setText(label)
            setTextColor(if (isSpace) palette.onSurface else palette.onSecondaryContainer)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = app.lychee.fonts.LycheeFonts.ui(context, if (pill) 600 else 500)
            gravity = Gravity.CENTER
        }
        view.contentDescription = context.getString(label)
        view.background = when {
            // Enter and ?123 like the keyboard's: the accent key and a functional key
            enter -> LycheePanels.accentKey(context)
            isSpace -> LycheePanels.letterKey(context)
            else -> LycheePanels.functionalKey(context)
        }
        view.setOnClickListener { onClick() }
        return FrameLayout(context).apply {
            setPadding(dp(3), dp(3), dp(3), dp(3))
            addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    /** The writing pad: draws the ink and keeps the strokes for recognition. */
    private inner class Pad : View(context) {
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurface
            style = Paint.Style.STROKE
            // a thin pen, like Gboard's
            strokeWidth = resources.displayMetrics.density * 3.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val paths = ArrayList<Path>()
        private val points = ArrayList<FloatArray>()
        private var current: ArrayList<Float>? = null
        /** The last character's ink, fading away under the new one. */
        private val fading = ArrayList<Path>()
        private val fadingInk = Paint(ink)
        private var fader: android.animation.ValueAnimator? = null
        private var lastX = 0f
        private var lastY = 0f

        val hasInk get() = points.isNotEmpty() || current != null

        fun clear() {
            fader?.cancel()
            fading.clear()
            paths.clear()
            points.clear()
            current = null
            invalidate()
            updateHint()
        }

        /** Clears the pad softly: the ink fades (and shrinks a touch) while new strokes may start. */
        fun fadeAway() {
            if (paths.isEmpty()) {
                clear()
                return
            }
            if (!LycheePanels.animate(context)) {
                clear()
                return
            }
            fader?.cancel()
            fading.clear()
            fading.addAll(paths)
            paths.clear()
            points.clear()
            current = null
            fader = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
                duration = INK_FADE_MS
                interpolator = app.lychee.layout.LycheeMotion.exitEasing
                addUpdateListener {
                    fadingInk.alpha = ((it.animatedValue as Float) * 255).toInt()
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        fading.clear()
                        invalidate()
                    }
                })
                start()
            }
            invalidate()
            updateHint()
        }

        fun undo() {
            if (paths.isNotEmpty()) paths.removeAt(paths.lastIndex)
            if (points.isNotEmpty()) points.removeAt(points.lastIndex)
            invalidate()
        }

        /** Each stroke as x, y pairs. */
        fun strokeList(): List<FloatArray> = points.toList()

        /** All strokes as x, y pairs with (-1, -1) after each. */
        fun strokes(): FloatArray {
            val total = points.sumOf { it.size + 2 }
            val out = FloatArray(total)
            var at = 0
            for (s in points) {
                s.copyInto(out, at)
                at += s.size
                out[at++] = -1f
                out[at++] = -1f
            }
            return out
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    hint.animate().cancel()
                    hint.visibility = GONE
                    main.removeCallbacks(autoCommit)
                    // writing again: the ink stays until a pause, and a new character is not a
                    // replacement for the one picked before
                    cancelAutoClear()
                    // a new character begun: the last one's best guess is typed first
                    if (!hasInk) autoType()
                    // still writing: no typing yet
                    commitWhenGuessed = false
                    current = arrayListOf(event.x, event.y)
                    paths += Path().apply { moveTo(event.x, event.y) }
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val stroke = current ?: return true
                    for (h in 0 until event.historySize) add(stroke, event.getHistoricalX(h), event.getHistoricalY(h))
                    add(stroke, event.x, event.y)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    current?.let { points += it.toFloatArray() }
                    current = null
                    // the guesses show; one is typed only when it is tapped
                    recognise()
                    scheduleAutoClear()
                }
            }
            invalidate()
            return true
        }

        private fun add(stroke: ArrayList<Float>, x: Float, y: Float) {
            stroke += x
            stroke += y
            // smooth curves through the midpoints, not a chain of straight lines
            paths.lastOrNull()?.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + y) / 2f)
            lastX = x
            lastY = y
        }

        override fun onDraw(canvas: Canvas) {
            if (fading.isNotEmpty()) {
                // shrinking a touch towards the middle as it fades
                val f = fadingInk.alpha / 255f
                val s = 0.96f + 0.04f * f
                canvas.save()
                canvas.scale(s, s, width / 2f, height / 2f)
                for (p in fading) canvas.drawPath(p, fadingInk)
                canvas.restore()
            }
            for (p in paths) canvas.drawPath(p, ink)
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        /** The pause after the last stroke before the ink fades away, and the fade. */
        const val AUTO_CLEAR_MS = 900L
        const val INK_FADE_MS = 220L
        /** How much a character that usually follows the one before gains, at most. */
        const val CONTEXT = 0.12f
        /** How much a character picked often before gains, at most. */
        const val PERSONAL = 0.08f
        /** How much a common character gains (see rank): a tie-breaker between look-alikes. */
        const val COMMON = 0.06f
        /** The stroke matcher's say on Chinese characters: past this distance none; its weight. */
        const val HAN_DISTANCE = 0.45f
        const val HAN_WEIGHT = 0.35f
        /** A stroke matcher distance past this gives a kana no score at all. */
        const val KANA_DISTANCE = 0.4f
        /** A perfect kana match's score (PP-OCR's confident guesses score near 1). */
        const val KANA_WEIGHT = 0.9f
        val worker = Executors.newSingleThreadExecutor()

        /** The Chinese dictionary's single characters, read once (null until it is open). */
        @Volatile
        private var chineseSet: Set<String>? = null

        fun chineseCharacters(): Set<String>? {
            chineseSet?.let { return it }
            val db = app.lychee.chinese.Readings.db ?: return null
            return runCatching { db.characters() }.getOrNull()?.takeIf { it.isNotEmpty() }?.also { chineseSet = it }
        }

        /** The stroke matcher, built once from the stroke-order data (a second or so). */
        @Volatile
        var matcher: app.lychee.handwriting.StrokeMatcher? = null
        @Volatile
        private var matcherLoading = false

        /** Builds the matcher, on a thread of its own so recognition never waits behind it. */
        private val loader = Executors.newSingleThreadExecutor()

        fun loadMatcher(context: Context, then: () -> Unit = {}) {
            if (matcher != null || matcherLoading) return
            matcherLoading = true
            val appContext = context.applicationContext
            loader.execute {
                val started = System.currentTimeMillis()
                val refs = app.lychee.dict.PackData.allMedians(appContext).map { (ch, json) ->
                    ch to app.lychee.handwriting.StrokeMatcher.parseMedians(json)
                }
                if (refs.isNotEmpty()) matcher = app.lychee.handwriting.StrokeMatcher(refs)
                matcherLoading = false
                app.lychee.DiagLog.log("handwriting shapes: ${refs.size} in ${System.currentTimeMillis() - started} ms")
                if (refs.isNotEmpty()) Handler(Looper.getMainLooper()).post(then)
            }
        }
    }
}
