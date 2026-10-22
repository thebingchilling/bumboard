package app.bumboard.emoji

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.bumboard.ui.M3Colors
import com.android.inputmethod.event.Event
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants

/**
 * Emoji search above the letter keys. While it is open, LatinIME hands key events here first:
 * letters build the query (in English, or Chinese through the keywords) and matching emoji
 * appear in a row; tapping one types it into the app.
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

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        setBackgroundColor(palette.surface)
        setPadding(dp(4), dp(4), dp(8), dp(4))
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_sym_arrow_back)
            imageTintList = ColorStateList.valueOf(palette.onSurface)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = context.getString(R.string.bum_back)
            setOnClickListener { close() }
        }, LayoutParams(dp(44), LayoutParams.MATCH_PARENT))
        queryView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.START
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(palette.surfaceContainerHigh)
                cornerRadius = dp(20).toFloat()
            }
        }
        addView(queryView, LayoutParams(dp(132), LayoutParams.MATCH_PARENT))
        scroller.isHorizontalScrollBarEnabled = false
        scroller.addView(results)
        addView(scroller, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(6) })
    }

    fun start() {
        query.clear()
        isActive = true
        visibility = VISIBLE
        EmojiData.load(context) { refresh() }
    }

    fun stop() {
        isActive = false
        visibility = GONE
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
                if (query.isNotEmpty()) query.setLength(query.length - 1)
            }
            event.mKeyCode == Constants.CODE_EMOJI -> {
                // back to the panel; LatinIME then switches to it
                stop()
                return false
            }
            code == Constants.CODE_ENTER -> shown.firstOrNull()?.let { type(it) }
            code == Constants.CODE_SPACE -> if (query.isNotEmpty() && query.last() != ' ') query.append(' ')
            code > 0 && Character.isLetterOrDigit(code) -> query.appendCodePoint(Character.toLowerCase(code))
            code > 0 -> query.appendCodePoint(code)
            else -> return false // shift, symbols and other keyboard keys behave as usual
        }
        refresh()
        return true
    }

    private fun refresh() {
        queryView.text = query.ifEmpty { context.getString(R.string.bum_emoji_search) }
        queryView.setTextColor(if (query.isEmpty()) palette.onSurfaceVariant else palette.onSurface)
        val found = if (query.isBlank()) store.recents() else EmojiData.search(query.toString()).map { store.shown(it) }
        shown = found
        results.removeAllViews()
        found.forEach { emoji ->
            results.addView(TextView(context).apply {
                text = emoji
                typeface = EmojiData.typeface
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setOnClickListener { type(emoji) }
            }, LayoutParams(dp(46), LayoutParams.MATCH_PARENT))
        }
        if (found.isEmpty() && query.isNotBlank()) {
            results.addView(TextView(context).apply {
                text = context.getString(R.string.bum_emoji_none)
                setTextColor(palette.onSurfaceVariant)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, 0, 0)
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        scroller.scrollTo(0, 0)
    }

    private fun type(emoji: String) {
        onType(emoji)
        store.addRecent(emoji)
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
