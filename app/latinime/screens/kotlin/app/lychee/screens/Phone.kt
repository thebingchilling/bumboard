package app.lychee.screens

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import app.lychee.chinese.CandidateBarView
import app.lychee.rime.Candidate
import app.lychee.settings.KeyboardPreviewView
import app.lychee.toolbar.ToolbarView
import com.android.inputmethod.latin.R

/** What the keyboard's top bar shows. */
sealed class TopBar {
    /** The shortcuts (the menu button, the shortcuts, the mic), as when nothing is typed. */
    object Shortcuts : TopBar()
    /** Candidates: [typed] letters on their own line above them (null: no such line, as while handwriting). */
    class Candidates(val sample: BarSample, val typed: Boolean = true) : TopBar()
    /** A panel's title (and its Back, and a control at its end such as the clipboard's switch). */
    class Title(val setUp: (ToolbarView) -> Unit) : TopBar()
}

/** The phone's parts, measured, for a screenshot to place things in. */
class PhoneFrame(val screen: FrameLayout, val keyboardTop: Int, val statusBar: Int, val keysTop: Int, val keysHeight: Int,
                 val keyRow: Int, val night: Boolean)

/**
 * A whole phone as a screenshot shows it, at the device's real sizes: the status bar, an app (a
 * chat, with its message field just above the keyboard), the keyboard's top bar ([bar]), the keys
 * and the gesture bar. [overKeys] lays a panel over the keys (all of them, or all but the bottom
 * row with [keepBottomRow], as handwriting does); [extra] adds anything else (the word card).
 */
fun drawPhone(paparazzi: Paparazzi, name: String, bar: TopBar = TopBar.Shortcuts, keepBottomRow: Boolean = false,
              overKeys: ((FrameLayout) -> View)? = null, extra: ((PhoneFrame) -> Unit)? = null) {
    val ctx = paparazzi.context
    val dm = ctx.resources.displayMetrics
    fun dp(v: Int) = (v * dm.density).toInt()
    val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val c = app.lychee.ui.M3Colors.of(ctx)
    val screen = FrameLayout(ctx).apply { setBackgroundColor(if (night) Color.parseColor("#1A1214") else Color.parseColor("#FFF8F8")) }
    val statusBar = dp(32)
    val gesture = dp(20)
    val keyboard = KeyboardPreviewView(ctx)
    keyboard.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
    val kbHeight = keyboard.measuredHeight
    val strip = ctx.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
    // the gesture bar's room under the keys, in the keyboard's colour, with its handle
    screen.addView(FrameLayout(ctx).apply {
        setBackgroundColor(c.surface)
        addView(View(ctx).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(c.onSurface) }
            alpha = 0.6f
        }, FrameLayout.LayoutParams(dp(108), dp(4), Gravity.CENTER))
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gesture, Gravity.BOTTOM))
    screen.addView(keyboard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kbHeight, Gravity.BOTTOM).apply { bottomMargin = gesture })
    val keysHeight = kbHeight - strip
    val keysBottom = dm.heightPixels - gesture
    // the top bar, over the preview's own (which shows the shortcuts)
    var barHeight = strip
    when (bar) {
        TopBar.Shortcuts -> Unit
        is TopBar.Candidates -> {
            val (view, h) = candidateBar(ctx, bar.sample, bar.typed)
            barHeight = maxOf(strip, h)
            screen.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barHeight, Gravity.BOTTOM).apply {
                bottomMargin = gesture + keysHeight
            })
        }
        is TopBar.Title -> {
            val toolbar = ToolbarView(ctx).apply { id = R.id.lychee_toolbar; setBackgroundColor(c.surface) }
            screen.addView(toolbar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, strip, Gravity.BOTTOM).apply {
                bottomMargin = gesture + keysHeight
            })
            bar.setUp(toolbar)
        }
    }
    val keyboardTop = keysBottom - keysHeight - barHeight
    // the app above the keyboard: a chat's title bar, and its message field just above the keys
    screen.addView(chatApp(ctx, night), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboardTop - statusBar, Gravity.TOP).apply {
        topMargin = statusBar
    })
    // a panel over the keys (the keyboard's bottom row left uncovered when asked)
    val keyRow = keysHeight / 4
    overKeys?.let { make ->
        val host = FrameLayout(ctx)
        val h = if (keepBottomRow) keysHeight - keyRow else keysHeight
        screen.addView(host, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h, Gravity.BOTTOM).apply {
            bottomMargin = gesture + (keysHeight - h)
        })
        val view = make(host)
        if (view.parent == null) host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.visibility = View.VISIBLE
    }
    // the status bar: the time on the left, signal and battery on the right
    screen.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24), 0, dp(24), 0)
        addView(TextView(ctx).apply { text = "9:41"; textSize = 14f; setTextColor(c.onSurface) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(ctx).apply { text = "▲ ▮ 82%"; textSize = 12f; setTextColor(c.onSurface) })
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, statusBar, Gravity.TOP))
    extra?.invoke(PhoneFrame(screen, keyboardTop, statusBar, keysBottom - keysHeight, keysHeight, keyRow, night))
    settle(screen)
    paparazzi.snapshot(screen, name)
}

/** Ends every fade and growth at once (a snapshot doesn't run animations). */
fun settle(v: View) {
    v.animate().cancel()
    // only what a fade-in starts from (alpha 0): faint text that is meant to be faint stays so
    if (v.alpha == 0f) v.alpha = 1f
    v.scaleX = 1f; v.scaleY = 1f; v.translationY = 0f; v.translationX = 0f
    if (v is ViewGroup) for (i in 0 until v.childCount) settle(v.getChildAt(i))
}

/** A plain chat app: its title bar, a couple of messages, and the message field at the bottom. */
private fun chatApp(ctx: android.content.Context, night: Boolean): View {
    val dm = ctx.resources.displayMetrics
    fun dp(v: Int) = (v * dm.density).toInt()
    val ink = if (night) Color.parseColor("#F2DEE1") else Color.parseColor("#22191B")
    val soft = if (night) Color.parseColor("#3A2E30") else Color.parseColor("#F1E4E6")
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(ctx).apply {
            text = "Mei"; textSize = 20f; setTextColor(ink); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        addView(View(ctx), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        fun bubble(t: String, mine: Boolean) = addView(TextView(ctx).apply {
            text = t; textSize = 15f; setTextColor(ink)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(if (mine) Color.parseColor(if (night) "#5A2A33" else "#F7D6DC") else soft) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            setMargins(dp(16), dp(4), dp(16), dp(4))
        })
        bubble("Are you coming to class tomorrow?", false)
        bubble("Yes! See you there", true)
        addView(TextView(ctx).apply {
            text = "Message"; textSize = 16f; setTextColor(ink); alpha = 0.6f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(soft) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(dp(12), dp(8), dp(12), dp(8)) })
    }
}

/**
 * The candidate bar as tall as the real one: the line for the letters typed (when [typed]), the
 * candidates with their readings and meanings, and 繁 at the right on the Chinese keyboards.
 */
fun candidateBar(ctx: android.content.Context, sample: BarSample, typed: Boolean): Pair<View, Int> {
    val dm = ctx.resources.displayMetrics
    fun dp(v: Int) = (v * dm.density).toInt()
    val c = app.lychee.ui.M3Colors.of(ctx)
    val strip = ctx.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
    val candidates = CandidateBarView(ctx).apply {
        style = CandidateBarView.Style(lang = null, fontLang = sample.fontLang)
        lines = sample.lines
    }
    val preedit = ctx.resources.getDimensionPixelSize(R.dimen.lychee_preedit_height)
    val height = maxOf(strip, candidates.heightFor(sample.lines) + preedit)
    val top = if (typed) preedit else 0
    val box = FrameLayout(ctx).apply { setBackgroundColor(c.surface) }
    if (typed) box.addView(TextView(ctx).apply {
        text = sample.typed
        setTextColor(c.primary)
        textSize = 12f
        includeFontPadding = false
        setPadding(dp(14), dp(2), 0, 0)
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, preedit, Gravity.TOP))
    val keyW = if (sample.script) dp(44) else 0
    box.addView(candidates, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height - top, Gravity.BOTTOM).apply { rightMargin = keyW })
    if (sample.script) box.addView(TextView(ctx).apply {
        text = "繁"; gravity = Gravity.CENTER; textSize = 16f; setTextColor(c.onSurfaceVariant)
    }, FrameLayout.LayoutParams(keyW, strip, Gravity.BOTTOM or Gravity.END))
    candidates.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels - keyW, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height - top, View.MeasureSpec.EXACTLY))
    candidates.layout(0, 0, dm.widthPixels - keyW, height - top)
    candidates.setForScreens(sample.candidates.map { Candidate(it, "") }, sample.annotate)
    return box to height
}
