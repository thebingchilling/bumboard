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

/** The real input view, for a screenshot to open its own panels in, as the keyboard service does. */
class RealInput(val input: View, val keys: com.android.inputmethod.keyboard.MainKeyboardView) {
    /**
     * The input view's own panel [id], placed over the keys by LycheePanels.cover and brought to
     * the front, exactly as LatinIME.showLycheePanel opens one (the test then calls its show).
     */
    fun <T : View> panel(id: Int): T {
        val p = input.findViewById<T>(id)
        app.lychee.layout.LycheePanels.cover(p, input)
        p.bringToFront()
        return p
    }
}

/**
 * A whole phone as a screenshot shows it, at the device's real sizes: the status bar, an app (a
 * chat, with its message field just above the keyboard), the keyboard's top bar ([bar]), the keys
 * and the gesture bar. [open] opens the input view's own panels (the number pad, handwriting, the
 * dropdown ...) the way the keyboard service does; [extra] adds anything else (the word card).
 */
fun drawPhone(paparazzi: Paparazzi, name: String, bar: TopBar = TopBar.Shortcuts,
              open: ((RealInput) -> Unit)? = null, extra: ((PhoneFrame) -> Unit)? = null) {
    val ctx = paparazzi.context
    val dm = ctx.resources.displayMetrics
    fun dp(v: Int) = (v * dm.density).toInt()
    val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val c = app.lychee.ui.M3Colors.of(ctx)
    val screen = FrameLayout(ctx).apply { setBackgroundColor(if (night) Color.parseColor("#1A1214") else Color.parseColor("#FFF8F8")) }
    val statusBar = dp(32)
    val gesture = dp(20)
    // the real data (dictionaries, packs) for every look-up the screens make
    if (RealData.available) RealData.install()
    // the real input view: the top bar, the candidate bar and the real keyboard for this language
    val engine = (bar as? TopBar.Candidates)?.sample?.fontLang?.takeIf { it != "en" }
    val (input, keysView) = app.lychee.layout.RealKeyboard.inputView(ctx, engine, dm.widthPixels)
    // the gesture bar's room under the keys, in the keyboard's colour, with its handle
    screen.addView(FrameLayout(ctx).apply {
        setBackgroundColor(c.surface)
        addView(View(ctx).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(c.onSurface) }
            alpha = 0.6f
        }, FrameLayout.LayoutParams(dp(108), dp(4), Gravity.CENTER))
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gesture, Gravity.BOTTOM))
    if (bar is TopBar.Title) input.findViewById<ToolbarView>(R.id.lychee_toolbar)?.let { bar.setUp(it) }
    screen.addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = gesture })
    fun layOut() {
        screen.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        screen.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }
    // laid out first, so a panel is placed over the keys where they really are
    layOut()
    open?.invoke(RealInput(input, keysView))
    // the candidates after the panel (the pad's own title gives way to its guesses, as on the phone)
    if (bar is TopBar.Candidates) showCandidates(input, bar.sample, bar.typed)
    layOut()
    val kbHeight = input.height
    val keysHeight = keysView.height.takeIf { it > 0 } ?: keysView.measuredHeight
    val keysBottom = dm.heightPixels - gesture
    val keyboardTop = keysBottom - kbHeight
    // the app above the keyboard: a chat's title bar, and its message field just above the keys
    screen.addView(chatApp(ctx, night), 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboardTop - statusBar, Gravity.TOP).apply {
        topMargin = statusBar
    })
    // a key row as the real keyboard has it (its rows share the keys' height)
    val keyRow = keysView.keyboard?.let { kb -> kb.mMostCommonKeyHeight } ?: (keysHeight / 4)
    // the status bar: the time on the left, signal and battery on the right
    screen.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24), 0, dp(24), 0)
        addView(TextView(ctx).apply { text = "9:41"; textSize = 14f; setTextColor(c.onSurface) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(ctx).apply { text = "▲ ▮ 82%"; textSize = 12f; setTextColor(c.onSurface) })
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, statusBar, Gravity.TOP))
    val before = screen.childCount
    extra?.invoke(PhoneFrame(screen, keyboardTop, statusBar, keysBottom - keysHeight, keysHeight, keyRow, night))
    // what a screenshot adds sits above the input view (which has an elevation of its own), as it does on the phone
    for (i in before until screen.childCount) screen.getChildAt(i).let { if (it.z < input.z + 1) it.translationZ = input.z + dp(8) }
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

/** The sample's candidates in the real candidate bar, arranged as the keyboard arranges it (RealKeyboard). */
fun showCandidates(input: View, sample: BarSample, typed: Boolean) =
    app.lychee.layout.RealKeyboard.showCandidates(input, sample.candidates, if (typed) sample.typed else null,
        sample.fontLang, sample.lines, sample.script,
        // Chinese from the real dictionary (RealData); Japanese and Korean the sample's (Rime's spelling hints aren't here)
        if (sample.fontLang == "yue" || sample.fontLang == "cmn") { _ -> null } else sample.annotate)
