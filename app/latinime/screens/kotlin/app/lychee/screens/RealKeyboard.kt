package app.lychee.screens

import android.content.Context
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
 * Lychee's real input view, as the keyboard service makes it (KeyboardSwitcher): the same theme
 * context (the keyboard theme, Lychee's colour overlay), the same layout (input_view: the top bar,
 * the candidate bar, the real MainKeyboardView), and a real keyboard built by KeyboardLayoutSet
 * from Lychee's own key layouts for [engine]'s subtype. Nothing hand-drawn.
 */
object RealKeyboard {
    /** The subtypes as method.xml declares them. */
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
     * symbols ...), laid out at [width] px. Returns the input view and its keyboard view.
     */
    fun inputView(context: Context, engine: String?, width: Int, element: Int = KeyboardId.ELEMENT_ALPHABET): Pair<View, MainKeyboardView> {
        // no native touch model on a computer (drawing never needs it)
        com.android.inputmethod.keyboard.ProximityInfo.sLycheeWithoutNative = true
        // what the keyboard service sets up before any keyboard is shown
        com.android.inputmethod.accessibility.AccessibilityUtils.init(context)
        val editor = EditorInfo().apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            packageName = "com.example.chat"
        }
        val themed = themeContext(context)
        val input = LayoutInflater.from(themed).inflate(R.layout.input_view, null)
        val keys = input.findViewById<MainKeyboardView>(R.id.keyboard_view)
        val res = themed.resources
        val layout = app.lychee.layout.LycheeLayout.get(themed)
        val builder = KeyboardLayoutSet.Builder(themed, editor)
        builder.setKeyboardGeometry(layout.keyboardWidth(width), layout.keyboardHeight(ResourceUtils.getDefaultKeyboardHeight(res)))
        builder.setLycheeOptions(layout.numberRow, layout.emojiKey, layout.sideKey)
        builder.setSubtype(RichInputMethodSubtype(subtype(engine)))
        builder.setVoiceInputKeyEnabled(true)
        builder.setLanguageSwitchKeyEnabled(true)
        builder.setSplitLayoutEnabledByUser(false)
        val keyboard = builder.build().getKeyboard(element)
        keys.setKeyboard(keyboard)
        // the keys that came with this keyboard fade in on the phone: drawn as they end up
        keys.lycheeFadeIn(emptySet(), 0)
        return input to keys
    }
}
