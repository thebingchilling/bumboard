package app.lychee

import android.view.inputmethod.InputConnection
import app.lychee.toolbar.Shortcut

/** What Lychee's own views need from LatinIME (which implements this). */
interface LycheeHost {
    /** Runs a toolbar or gallery shortcut. */
    fun lycheeRunShortcut(shortcut: Shortcut)

    /** Rebuilds the keys and re-arranges the views after a layout setting changed. */
    fun lycheeReloadLayout()

    /** Sends a LatinIME key code (Constants.CODE_*) or a character, as if its key was tapped. */
    fun lycheeSendCode(code: Int)

    /** Types [text] as LatinIME would type a key's text. */
    fun lycheeType(text: String)

    fun lycheeInputConnection(): InputConnection?

    /** The current keyboard's engine ("yue", "cmn", "ja", "ko"), or null for English. */
    fun lycheeEngine(): String?

    /** Opens one page of Lychee's settings (a [app.lychee.settings.Page] name). */
    fun lycheeOpenSettings(page: String)
}

/** The [LycheeHost] (LatinIME) behind a keyboard view's themed context, if any. */
fun android.view.View.lycheeHost(): LycheeHost? {
    var c: android.content.Context? = context
    while (c != null) {
        if (c is LycheeHost) return c
        c = (c as? android.content.ContextWrapper)?.baseContext
    }
    return null
}
