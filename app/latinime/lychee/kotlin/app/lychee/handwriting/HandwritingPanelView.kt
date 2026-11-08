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
 * Handwriting over the keys, like Gboard's: write a character anywhere on the pad; a moment
 * after the last stroke the best guess is typed and the pad clears for the next one, so
 * characters can be written one after another. The guesses show in the candidate bar, with
 * their readings: tap one to replace what was typed. After each character the bar suggests
 * what usually follows it (校, 生, 習 after 學), from the readings dictionary's words.
 * ⌫ takes back the last stroke while writing, otherwise deletes.
 *
 * A mode, like text editing: the Handwriting shortcut (Chinese and Japanese keyboards only)
 * opens it over the keys, with Back and its name in the bar above (Back, or the phone's,
 * closes it). Its bottom row: ?123 (the symbols, closing the pad), space, ⌫ and Enter.
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
    private val bottom = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
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

    init {
        orientation = VERTICAL
        isClickable = true
        setBackgroundColor(palette.surface)
        // the guesses show in the candidate bar above (lycheeShowGuesses), so the pad gets the room
        // the pad has no background of its own: write straight on the keyboard
        addView(FrameLayout(context).apply {
            addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(pad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(dp(8), dp(6), dp(8), dp(4)) })
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)).apply { setMargins(dp(4), 0, dp(4), dp(4)) })
        buildBottomRow()
        visibility = GONE
    }

    private fun buildBottomRow() {
        bottom.removeAllViews()
        // the symbols: the pad closes and the keyboard shows its symbols page
        bottom.addView(key(null, R.string.lychee_hw_symbols, pill = true) {
            commitPending()
            this@HandwritingPanelView.visibility = GONE
            lycheeHost()?.lycheeSendCode(Constants.CODE_SWITCH_ALPHA_SYMBOL)
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.3f))
        bottom.addView(key(null, R.string.lychee_hw_space, isSpace = true) { space() }, LayoutParams(0, LayoutParams.MATCH_PARENT, 4f))
        bottom.addView(key(R.drawable.ic_sym_backspace, R.string.lychee_emoji_delete) { backspace() },
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1.3f))
        bottom.addView(key(R.drawable.ic_sym_keyboard_return, R.string.lychee_hw_enter, enter = true) {
            commitPending(); lycheeHost()?.lycheeSendCode(Constants.CODE_ENTER)
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.3f))
    }

    /** Types the character still on the pad, before a key of the keyboard does anything. */
    fun flush() {
        if (visibility == VISIBLE) commitPending()
    }

    private fun toolbar(): app.lychee.toolbar.ToolbarView? = rootView.findViewById(R.id.lychee_toolbar)

    fun show() {
        visibility = VISIBLE
        // the bar above shows Back and "Handwriting", like text editing
        toolbar()?.showTitle(R.string.lychee_sc_handwriting) { visibility = GONE }
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
        // the kana's shapes, for Japanese
        if (engine == "ja") loadMatcher(context) { if (visibility == VISIBLE && pad.hasInk) recognise() }
        message.visibility = GONE
        message.text = context.getString(R.string.lychee_hw_unavailable)
        updateHint()
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE && this.visibility == VISIBLE) {
            commitPending(now = true)
            main.removeCallbacksAndMessages(null)
            release()
            guesses = emptyList()
            lycheeHost()?.lycheeShowGuesses(emptyList()) {}
            toolbar()?.let {
                if (it.mode == app.lychee.toolbar.ToolbarView.Mode.TITLE) it.mode = app.lychee.toolbar.ToolbarView.Mode.NORMAL
            }
        }
        super.setVisibility(visibility)
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
        val o = ocr
        val m = matcher
        val engine = lycheeHost()?.lycheeEngine()
        worker.execute {
            val started = System.currentTimeMillis()
            val result = runCatching { rank(o, m, ink, engine).take(10) }
                .onFailure { app.lychee.DiagLog.log("handwriting failed", it) }
                .getOrDefault(emptyList())
            val took = System.currentTimeMillis() - started
            if (took > 400) app.lychee.DiagLog.log("handwriting took $took ms")
            main.post {
                // a newer stroke has its own recognition coming: these guesses are stale
                if (version != inkVersion || !pad.hasInk) return@post
                guessedVersion = version
                showGuesses(result)
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
    private fun rank(o: OcrRecognizer?, m: StrokeMatcher?, ink: List<FloatArray>, engine: String?): List<String> {
        val allowed: (String) -> Boolean = when (engine) {
            "ko" -> { ch -> ch.length == 1 && ch[0] in '\uAC00'..'\uD7A3' }
            "ja" -> { ch -> isKana(ch) || isHan(ch) }
            else -> { ch -> isHan(ch) }
        }
        val score = LinkedHashMap<String, Float>()
        o?.recognise(ink, 20, allowed)?.forEach { (ch, p) -> score[ch] = p }
        if (engine == "ja" && m != null) {
            // PP-OCR takes kana for look-alike kanji (く for 人, け for 十): the stroke matcher
            // knows their shapes
            m.match(ink, 40).filter { (ch, _) -> isKana(ch) }.take(5).forEach { (ch, distance) ->
                val s = (1f - distance / KANA_DISTANCE).coerceAtLeast(0f) * KANA_WEIGHT
                if (s > (score[ch] ?: 0f)) score[ch] = s
            }
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

    /**
     * Types the best guess for the ink on the pad and clears it for the next character; when
     * the guesses for all of the ink are still being worked out, as soon as they arrive (never
     * a guess made from fewer strokes). [now]: type what there is (the pad is closing).
     */
    private fun commitPending(now: Boolean = false) {
        main.removeCallbacks(autoCommit)
        if (!pad.hasInk) return
        if (!now && guessedVersion != inkVersion) {
            commitWhenGuessed = true
            return
        }
        commitWhenGuessed = false
        val best = guesses.firstOrNull()
        HandwritingSamples.save(context, pad.strokeList(), best, guesses, lycheeHost()?.lycheeEngine())
        pad.clear()
        if (best != null) {
            lycheeHost()?.lycheeType(best)
            lastTyped = best
            showNext(best)
        }
    }

    private val autoCommit = Runnable { commitPending() }

    /** A tapped guess: replaces what was just typed, or types it when still writing. */
    private fun pick(text: String) {
        if (pad.hasInk) {
            main.removeCallbacks(autoCommit)
            HandwritingSamples.save(context, pad.strokeList(), text, guesses, lycheeHost()?.lycheeEngine())
            pad.clear()
        } else {
            val previous = lastTyped
            if (previous != null) {
                repeat(previous.codePointCount(0, previous.length)) { lycheeHost()?.lycheeSendCode(Constants.CODE_DELETE) }
            }
        }
        lycheeHost()?.lycheeType(text)
        lastTyped = text
        showNext(text)
    }

    private fun space() {
        commitPending()
        lastTyped = null
        lycheeHost()?.lycheeSendCode(Constants.CODE_SPACE)
    }

    private fun backspace() {
        if (pad.hasInk) {
            pad.undo()
            commitWhenGuessed = false
            if (pad.hasInk) recognise() else {
                inkVersion++
                showGuesses(emptyList())
            }
            return
        }
        lastTyped = null
        showGuesses(emptyList())
        lycheeHost()?.lycheeSendCode(Constants.CODE_DELETE)
    }

    /** "Write here" while the pad is empty and ready. */
    private fun updateHint() {
        hint.visibility = if (ready && !pad.hasInk) VISIBLE else GONE
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

        val hasInk get() = points.isNotEmpty() || current != null

        fun clear() {
            paths.clear()
            points.clear()
            current = null
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
                    hint.visibility = GONE
                    main.removeCallbacks(autoCommit)
                    // still writing: no typing yet
                    commitWhenGuessed = false
                    current = arrayListOf(event.x, event.y)
                    paths += Path().apply { moveTo(event.x, event.y) }
                }
                MotionEvent.ACTION_MOVE -> {
                    val stroke = current ?: return true
                    for (h in 0 until event.historySize) add(stroke, event.getHistoricalX(h), event.getHistoricalY(h))
                    add(stroke, event.x, event.y)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    current?.let { points += it.toFloatArray() }
                    current = null
                    recognise()
                    main.postDelayed(autoCommit, AUTO_COMMIT_MS)
                }
            }
            invalidate()
            return true
        }

        private fun add(stroke: ArrayList<Float>, x: Float, y: Float) {
            stroke += x
            stroke += y
            paths.lastOrNull()?.lineTo(x, y)
        }

        override fun onDraw(canvas: Canvas) {
            for (p in paths) canvas.drawPath(p, ink)
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private companion object {
        /** A pause this long after a stroke types the character. */
        const val AUTO_COMMIT_MS = 1300L
        /** How much a common character gains (see rank): a tie-breaker between look-alikes. */
        const val COMMON = 0.06f
        /** A stroke matcher distance past this gives a kana no score at all. */
        const val KANA_DISTANCE = 0.4f
        /** A perfect kana match's score (PP-OCR's confident guesses score near 1). */
        const val KANA_WEIGHT = 0.9f
        val worker = Executors.newSingleThreadExecutor()

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
