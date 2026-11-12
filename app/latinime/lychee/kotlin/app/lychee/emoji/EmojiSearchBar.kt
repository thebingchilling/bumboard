package app.lychee.emoji

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import app.lychee.ui.M3Colors
import com.android.inputmethod.event.Event
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants

/**
 * Emoji search above the letter keys (and, with [start]'s unicode, the Unicode search: any
 * symbol by the words of its name or its code point, each shown with its name and code). It keeps the emoji panel's search pill (magnifier and
 * query), with the matching emoji in a row above it and Back in the corner. While it is open,
 * LatinIME hands key events here first: letters build the query (in English, or Chinese
 * through the keywords); tapping an emoji types it into the app.
 */
class EmojiSearchBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    /** Types an emoji into the app. */
    var onType: (String) -> Unit = {}

    /** Called when search is closed with the back button (back to the emoji panel). */
    var onClose: () -> Unit = {}

    var isActive = false
        private set

    private val palette = M3Colors.of(context)
    @Suppress("DEPRECATION")
    private val store = EmojiStore(PreferenceManager.getDefaultSharedPreferences(context))
    private val query = StringBuilder()
    private val queryView: TextView
    private val results = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val scroller = HorizontalScrollView(context)
    private var shown: List<String> = emptyList()
    /** Searching every Unicode symbol (by name or code point) rather than the emoji. */
    private var unicode = false
    private val info = EmojiInfo(context)

    init {
        orientation = VERTICAL
        visibility = GONE
        setBackgroundColor(palette.surface)
        // the matching emoji (recent ones before anything is typed)
        scroller.isHorizontalScrollBarEnabled = false
        scroller.addView(results)
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(dp(4), dp(2), dp(4), 0)
        })
        // Back, then the same pill as in the emoji panel
        val pillRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(8), dp(4))
        }
        pillRow.addView(app.lychee.layout.LycheePanels.backButton(context) { close() }, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
        queryView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.START
            gravity = Gravity.CENTER_VERTICAL
            compoundDrawablePadding = dp(12)
            setCompoundDrawablesRelativeWithIntrinsicBounds(context.getDrawable(R.drawable.ic_sym_search)?.mutate()?.apply {
                setTint(palette.onSurfaceVariant)
            }, null, null, null)
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                setColor(palette.surfaceContainerHigh)
                cornerRadius = dp(24).toFloat()
            }
        }
        pillRow.addView(queryView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(4) })
        addView(pillRow, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    @JvmOverloads
    fun start(unicode: Boolean = false) {
        this.unicode = unicode
        query.clear()
        picked = false
        isActive = true
        visibility = VISIBLE
        if (unicode) UnicodeNames.load(context) { if (isActive) refresh() } else EmojiData.load(context) { refresh() }
        refresh()
    }

    fun stop() {
        isActive = false
        visibility = GONE
        info.dismiss()
    }

    private fun close() {
        stop()
        onClose()
    }

    /** Returns true when the key was used for the search. */
    fun onEvent(event: Event): Boolean {
        if (!isActive) return false
        val code = event.mCodePoint
        when {
            event.mKeyCode == Constants.CODE_DELETE -> {
                // one letter of the query at a time (never the whole query at once); with nothing
                // left in the bar, Delete goes to the app and deletes what was typed (the symbol)
                if (query.isEmpty()) return false
                query.setLength(query.length - Character.charCount(query.codePointBefore(query.length)))
                picked = false
            }
            event.mKeyCode == Constants.CODE_EMOJI -> {
                // back to the panel; LatinIME then switches to it
                stop()
                return false
            }
            code == Constants.CODE_ENTER -> { shown.firstOrNull()?.let { type(it) }; return true }
            code == Constants.CODE_SPACE -> if (query.isNotEmpty() && query.last() != ' ') query.append(' ')
            unicode && code > 0 -> query.appendCodePoint(Character.toLowerCase(code))
            code > 0 && Character.isLetterOrDigit(code) -> query.appendCodePoint(Character.toLowerCase(code))
            code > 0 -> query.appendCodePoint(code)
            else -> return false // shift, symbols and other keyboard keys behave as usual
        }
        if (event.mKeyCode != Constants.CODE_DELETE) picked = false
        refresh()
        return true
    }

    private fun refresh() {
        queryView.text = query.ifEmpty { context.getString(if (unicode) R.string.lychee_unicode_search else R.string.lychee_emoji_search) }
        queryView.setTextColor(if (query.isEmpty()) palette.onSurfaceVariant else palette.onSurface)
        if (unicode) {
            refreshUnicode()
            return
        }
        // back from the Unicode search: its cells go
        if (results.childCount > 0 && results.getChildAt(0) is SymbolCell) results.removeAllViews()
        val found = if (query.isBlank()) store.recents() else EmojiData.search(query.toString(), RESULTS).map { store.shown(it) }
        shown = found
        // the result views are kept and reused: building them afresh on every key was the slow part
        while (results.childCount > 0 && results.getChildAt(results.childCount - 1) !is EmojiCell) {
            results.removeViewAt(results.childCount - 1)
        }
        found.forEachIndexed { i, emoji ->
            val cell = (results.getChildAt(i) as? EmojiCell) ?: EmojiCell().also {
                results.addView(it, LayoutParams(dp(50), LayoutParams.MATCH_PARENT))
            }
            cell.text = emoji
            cell.visibility = VISIBLE
            cell.setOnClickListener { type(emoji) }
        }
        for (i in found.size until results.childCount) results.getChildAt(i).visibility = GONE
        if (found.isEmpty() && query.isNotBlank()) {
            results.addView(TextView(context).apply {
                text = context.getString(R.string.lychee_emoji_none)
                setTextColor(palette.onSurfaceVariant)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, 0, 0)
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        scroller.scrollTo(0, 0)
    }

    /** A symbol was typed since the query last changed (see Delete in [onEvent]). */
    private var picked = false

    private fun type(emoji: String) {
        onType(emoji)
        picked = true
        if (!unicode) store.addRecent(emoji)
    }

    /**
     * The Unicode search's results: each symbol with its name and code point. The search runs
     * in the background a moment after the last key (typing never waits for it), and the result
     * cells are reused; the new results fade in.
     */
    private fun refreshUnicode() {
        val q = query.toString()
        val generation = ++searchGeneration
        removeCallbacks(pendingSearch)
        pendingSearch = Runnable {
            searcher.execute {
                if (generation != searchGeneration) return@execute
                val found = runCatching {
                    UnicodeNames.search(q, RESULTS, drawable = { symbolPaint.hasGlyph(it) },
                        used = { cp -> unicodeUse.getInt(cp.toString(16), 0) })
                }.getOrDefault(emptyList())
                post { if (generation == searchGeneration && isActive && unicode) showUnicode(q, found) }
            }
        }
        postDelayed(pendingSearch, if (q.isEmpty()) 0L else SEARCH_DELAY)
    }

    private var searchGeneration = 0
    private var pendingSearch = Runnable {}
    private val cells = ArrayList<SymbolCell>()
    private val emptyNote by lazy {
        TextView(context).apply {
            setTextColor(palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
    }

    private fun showUnicode(q: String, found: List<UnicodeNames.Symbol>) {
        shown = found.map { it.text }
        // the cells already made are reused; only as many as there are results show
        while (cells.size < found.size) cells += SymbolCell()
        if (results.childCount > 0 && results.getChildAt(0) !is SymbolCell && results.getChildAt(0) !== emptyNote) results.removeAllViews()
        results.removeView(emptyNote)
        for ((k, cell) in cells.withIndex()) {
            if (k < found.size) {
                cell.bind(found[k])
                if (cell.parent == null) results.addView(cell, LayoutParams(dp(CELL_WIDTH), LayoutParams.MATCH_PARENT).apply { marginEnd = dp(2) })
                cell.visibility = VISIBLE
            } else if (cell.parent != null) {
                cell.visibility = GONE
            }
        }
        if (found.isEmpty()) {
            emptyNote.text = context.getString(if (q.isBlank()) R.string.lychee_unicode_hint else R.string.lychee_emoji_none)
            results.addView(emptyNote, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        scroller.scrollTo(0, 0)
        if (app.lychee.layout.LycheePanels.animate(context)) {
            results.alpha = 0.4f
            results.animate().alpha(1f).setDuration(120).start()
        }
    }

    /** Searches off the main thread, one at a time. */
    private val searcher = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** How often each symbol was typed from the Unicode search (by code point, in hex). */
    private val unicodeUse by lazy { context.getSharedPreferences("lychee_unicode_use", Context.MODE_PRIVATE) }

    /** The symbols' font: the keys' font, Noto's symbols, then every font of the phone's. */
    private val symbolFace by lazy { app.lychee.fonts.LycheeFonts.ui(context) }
    private val symbolPaint by lazy { android.graphics.Paint().apply { typeface = symbolFace } }

    /**
     * One Unicode result, all the same size: the symbol, and beside it its name on one line
     * and its code point under that.
     */
    private inner class SymbolCell : LinearLayout(context) {
        private val symbol = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(palette.onSurface)
            typeface = symbolFace
            includeFontPadding = false
            isSingleLine = true
            gravity = Gravity.CENTER
        }
        private val name = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(palette.onSurface)
            typeface = app.lychee.fonts.LycheeFonts.ui(context)
            includeFontPadding = false
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        private val code = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(palette.onSurfaceVariant)
            typeface = app.lychee.fonts.LycheeFonts.ui(context)
            includeFontPadding = false
            isSingleLine = true
            setPadding(0, dp(2), 0, 0)
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(8), 0)
            background = app.lychee.ui.LycheeColorStyle.themed(context).getDrawable(R.drawable.lychee_m3_suggestion)
            addView(symbol, LayoutParams(dp(40), LayoutParams.WRAP_CONTENT))
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(name)
                addView(code)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        }

        fun bind(s: UnicodeNames.Symbol) {
            symbol.text = s.shown
            name.text = s.name.lowercase().ifEmpty { "—" }
            code.text = "U+%04X".format(s.code)
            setOnClickListener {
                // what this person uses comes first next time
                unicodeUse.edit().putInt(s.code.toString(16), unicodeUse.getInt(s.code.toString(16), 0) + 1).apply()
                type(s.text)
            }
            setOnLongClickListener { info.show(this, s.text, s.name.lowercase(), emoji = false); true }
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    /** One result. */
    private inner class EmojiCell : TextView(context) {
        init {
            typeface = app.lychee.fonts.LycheeFonts.emoji(context)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = app.lychee.ui.LycheeColorStyle.themed(context).getDrawable(R.drawable.lychee_m3_suggestion)
        }
    }

    private companion object {
        /** Results shown at most: a bar's worth and some to scroll. */
        const val RESULTS = 40
        /** A Unicode result's width (dp): every one the same. */
        const val CELL_WIDTH = 150
        /** How long after the last key the Unicode search runs (ms). */
        const val SEARCH_DELAY = 90L
    }
}
