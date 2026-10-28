package app.bumboard

import android.view.inputmethod.InputConnection
import app.bumboard.toolbar.Shortcut

/** What BumBoard's own views need from LatinIME (which implements this). */
interface BumHost {
    /** Runs a toolbar or gallery shortcut. */
    fun bumRunShortcut(shortcut: Shortcut)

    /** Rebuilds the keys and re-arranges the views after a layout setting changed. */
    fun bumReloadLayout()

    /** Sends a LatinIME key code (Constants.CODE_*) or a character, as if its key was tapped. */
    fun bumSendCode(code: Int)

    /** Types [text] as LatinIME would type a key's text. */
    fun bumType(text: String)

    fun bumInputConnection(): InputConnection?

    /** The current keyboard's engine ("yue", "cmn", "ja", "ko"), or null for English. */
    fun bumEngine(): String?

    /** Opens one page of BumBoard's settings (a [app.bumboard.settings.Page] name). */
    fun bumOpenSettings(page: String)
}

/** The [BumHost] (LatinIME) behind a keyboard view's themed context, if any. */
fun android.view.View.bumHost(): BumHost? {
    var c: android.content.Context? = context
    while (c != null) {
        if (c is BumHost) return c
        c = (c as? android.content.ContextWrapper)?.baseContext
    }
    return null
}
