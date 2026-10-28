package app.bumboard.written

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import app.bumboard.fonts.BumFonts
import app.bumboard.ui.M3Colors
import com.android.inputmethod.latin.R

/**
 * 書面語: turns the selected text, or else the sentence before the cursor, from colloquial
 * Cantonese into Standard Written Chinese. A card above the keys shows the result; Replace puts
 * it in the text field, and Undo (on the same card) puts the original back.
 */
object WrittenChinesePopup {
    private var converter: WrittenChinese? = null
    private var window: PopupWindow? = null

    /** The phrase table (assets/bumboard/swc.tsv, built from data/swc), loaded once. */
    @Synchronized
    fun converter(context: Context): WrittenChinese? {
        converter?.let { return it }
        val loaded = runCatching {
            context.assets.open("bumboard/swc.tsv").bufferedReader().useLines { WrittenChinese.parse(it.toList().asSequence()) }
        }.getOrNull()
        converter = loaded
        return loaded
    }

    fun dismiss() {
        window?.dismiss()
        window = null
    }

    /** Converts from [ic] and shows the card above [keyboard] (the keyboard frame). */
    fun show(context: Context, ic: InputConnection, keyboard: View) {
        dismiss()
        val converter = converter(context) ?: return
        ic.finishComposingText()
        val selected = ic.getSelectedText(0)?.toString()?.takeIf { it.isNotEmpty() }
        val source = selected ?: lastSentence(ic.getTextBeforeCursor(MAX_CHARS, 0)?.toString().orEmpty())
        val c = M3Colors.of(context)
        val converted = converter.convert(source)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 10))
            background = GradientDrawable().apply {
                setColor(c.surfaceContainerHigh)
                cornerRadius = dp(context, 24).toFloat()
            }
            elevation = dp(context, 8).toFloat()
        }
        card.addView(text(context, context.getString(R.string.bum_written_title), 14f, c.primary).apply {
            typeface = app.bumboard.fonts.BumFonts.ui(context, 500)
        })
        val body = text(context, when {
            source.isBlank() -> context.getString(R.string.bum_written_nothing)
            converted == source -> context.getString(R.string.bum_written_same)
            else -> converted
        }, if (converted != source && source.isNotBlank()) 20f else 15f, c.onSurface).apply {
            BumFonts.chinese(context)?.let { typeface = it }
            setPadding(0, dp(context, 6), 0, dp(context, 6))
        }
        card.addView(body)
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        card.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val screen = context.resources.displayMetrics.widthPixels
        val width = minOf(dp(context, 340), screen - dp(context, 16))
        val popup = PopupWindow(card, width, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(context, 8).toFloat()
            setOnDismissListener { if (window === this) window = null }
        }
        actions.addView(button(context, context.getString(R.string.bum_close), c, filled = false) { popup.dismiss() })
        if (source.isNotBlank() && converted != source) {
            lateinit var replace: TextView
            var replaced = false
            replace = button(context, context.getString(R.string.bum_written_replace), c, filled = true) {
                if (!replaced) {
                    swap(ic, from = source, to = converted, wasSelected = selected != null)
                    replaced = true
                    replace.setText(R.string.bum_written_undo)
                } else {
                    // Undo: the original back in place of what was put in
                    swap(ic, from = converted, to = source, wasSelected = false)
                    popup.dismiss()
                }
            }
            actions.addView(replace)
        }
        window = popup
        val location = IntArray(2).also { keyboard.getLocationInWindow(it) }
        card.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        popup.showAtLocation(keyboard, Gravity.TOP or Gravity.START,
            (screen - width) / 2, (location[1] - card.measuredHeight - dp(context, 8)).coerceAtLeast(0))
    }

    /** Puts [to] where [from] is: the selection, or the text just before the cursor. */
    private fun swap(ic: InputConnection, from: String, to: String, wasSelected: Boolean) {
        ic.beginBatchEdit()
        if (wasSelected) {
            ic.commitText(to, 1)
        } else {
            val before = ic.getTextBeforeCursor(from.length, 0)?.toString()
            if (before == from) {
                ic.deleteSurroundingText(from.length, 0)
            }
            ic.commitText(to, 1)
        }
        ic.endBatchEdit()
    }

    /** The text after the last sentence end (。！？.!? or a new line), without leading spaces. */
    private fun lastSentence(text: String): String {
        var end = text.length
        // a sentence end right before the cursor belongs to the sentence
        while (end > 0 && text[end - 1] in ENDS) end--
        val start = text.lastIndexOfAny(ENDS, end - 1).let { if (it < 0) 0 else it + 1 }
        val sentence = text.substring(start)
        val trimmed = sentence.trimStart()
        return trimmed
    }

    private fun text(context: Context, value: String, sp: Float, color: Int) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
    }

    private fun button(context: Context, label: String, c: M3Colors, filled: Boolean, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = app.bumboard.fonts.BumFonts.ui(context, 500)
            setTextColor(if (filled) c.onPrimary else c.primary)
            gravity = Gravity.CENTER
            setPadding(dp(context, 18), dp(context, 8), dp(context, 18), dp(context, 8))
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 20).toFloat()
                if (filled) setColor(c.primary) else setColor(Color.TRANSPARENT)
            }
            setOnClickListener { onClick() }
        }

    private fun dp(context: Context, v: Int) = (context.resources.displayMetrics.density * v).toInt()

    private const val MAX_CHARS = 300
    private val ENDS = charArrayOf('。', '！', '？', '.', '!', '?', '\n')
}
