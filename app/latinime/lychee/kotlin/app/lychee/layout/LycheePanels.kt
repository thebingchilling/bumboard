package app.lychee.layout

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.android.inputmethod.keyboard.KeyboardTheme
import com.android.inputmethod.latin.R

/** Helpers for the panels drawn over the keys (voice, shortcuts, text editing, number pad, resizing). */
object LycheePanels {

    /** Places [panel] (a child of the input view) exactly over the keyboard view. */
    @JvmStatic
    fun cover(panel: View, inputView: View) {
        val keys = inputView.findViewById<View>(R.id.keyboard_view) ?: return
        val panelLoc = IntArray(2).also { inputView.getLocationInWindow(it) }
        val keysLoc = IntArray(2).also { keys.getLocationInWindow(it) }
        val lp = panel.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.BOTTOM or Gravity.START
        lp.width = keys.width
        lp.height = keys.height
        lp.leftMargin = keysLoc[0] - panelLoc[0]
        lp.bottomMargin = (panelLoc[1] + inputView.height) - (keysLoc[1] + keys.height)
        panel.layoutParams = lp
    }

    /** A panel opening over the keys: it cross-fades in place, like the keys changing (LycheeMotion). */
    @JvmStatic
    fun reveal(panel: View) {
        // once laid out, every button in it sinks a little when pressed and springs back
        panel.post { springButtons(panel, panel) }
        // the input view's own transition already brings it in
        if ((panel.parent as? android.view.ViewGroup)?.layoutTransition != null) return
        // the candidates' dropdown rolls down; the other panels cross-fade in
        if (panel.id == R.id.lychee_candidate_panel) LycheeMotion.dropDown(panel) else LycheeMotion.enter(panel)
    }

    /**
     * Gives every button under [v] the keys' press (LycheeMotion.springy): anything tappable
     * that is not most of the panel (the panel itself and the writing pad catch touches too).
     */
    private fun springButtons(v: View, panel: View) {
        val big = v.width > panel.width * 0.6f && v.height > panel.height * 0.4f
        if (v !== panel && !big && v.isClickable && v.stateListAnimator == null) LycheeMotion.springy(v)
        if (v is android.view.ViewGroup && v !is androidx.recyclerview.widget.RecyclerView) {
            for (i in 0 until v.childCount) springButtons(v.getChildAt(i), panel)
        }
    }

    /** The main keyboard's key rows, so every panel's keys and bars line up with them. */
    class KeyRow(
        /** A row's height, its gap included (the row pitch). */
        val height: Int,
        /** The gap between rows: each panel key is inset by half of it, like the keys. */
        val gap: Int,
        /** The keyboard's padding below its bottom row. */
        val bottomPadding: Int,
    )

    /** The key rows of the keyboard showing now (or a phone-sized guess before it has one). */
    @JvmStatic
    fun keyRow(view: View): KeyRow {
        val keys = view.rootView.findViewById<View>(R.id.keyboard_view) as? com.android.inputmethod.keyboard.KeyboardView
        val kb = keys?.keyboard
        val d = view.resources.displayMetrics.density
        if (kb == null || kb.mMostCommonKeyHeight <= 0) return KeyRow((56 * d).toInt(), (8 * d).toInt(), (4 * d).toInt())
        val rows = ((kb.mOccupiedHeight - kb.mTopPadding) / kb.mMostCommonKeyHeight).coerceAtLeast(1)
        val bottom = (kb.mOccupiedHeight - kb.mTopPadding - rows * kb.mMostCommonKeyHeight).coerceAtLeast(0)
        return KeyRow(kb.mMostCommonKeyHeight, kb.mVerticalGap, bottom)
    }

    /** The suggestion strip's height now (taller on the Chinese, Japanese and Korean keyboards). */
    @JvmStatic
    fun stripHeight(view: View): Int =
        view.rootView.findViewById<View>(R.id.suggestion_strip_view)?.height?.takeIf { it > 0 }
            ?: view.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)

    /** Whether the phone wants animations (Settings > Accessibility > Remove animations). */
    @JvmStatic
    fun animate(context: Context): Boolean = android.provider.Settings.Global.getFloat(
        context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    /**
     * Hides every panel over the keys except [except]. A number field's pad always stays (under
     * another panel, coming back as that one closes): LatinIME's own phone and number pads,
     * which have letters on their keys, must never show through. Only the field ending
     * ([hideAll]) puts it away.
     */
    @JvmStatic
    fun hideOthers(inputView: View, except: View?) {
        for (id in PANELS) {
            val panel = inputView.findViewById<View>(id) ?: continue
            if (panel is app.lychee.toolbar.NumpadPanelView && panel.forced) continue
            if (panel !== except && panel.visibility == View.VISIBLE) {
                panel.visibility = View.GONE
            }
        }
    }

    /** Hides every panel over the keys, a number field's pad too (the field has ended). */
    @JvmStatic
    fun hideAll(inputView: View) {
        for (id in PANELS) inputView.findViewById<View>(id)?.let { if (it.visibility == View.VISIBLE) it.visibility = View.GONE }
    }

    /** Whether a panel that Back closes is open (a number field's pad is not one: Back closes the keyboard). */
    @JvmStatic
    fun anyClosable(inputView: View): Boolean = PANELS.any { id ->
        inputView.findViewById<View>(id)?.let { p ->
            p.visibility == View.VISIBLE && !(p is app.lychee.toolbar.NumpadPanelView && p.forced)
        } == true
    }

    /** The panels over the keys in [inputView] (to keep, rather than find each time). */
    fun panels(inputView: View): List<View> = PANELS.toList().mapNotNull { inputView.findViewById<View>(it) }

    @JvmStatic
    fun anyShown(inputView: View): Boolean =
        PANELS.any { inputView.findViewById<View>(it)?.visibility == View.VISIBLE }

    /** Whether a panel other than [except] is open over the keys. */
    fun anyOtherShown(inputView: View, except: View): Boolean =
        PANELS.any { id -> inputView.findViewById<View>(id)?.let { it !== except && it.visibility == View.VISIBLE } == true }

    private val PANELS = intArrayOf(
        R.id.lychee_gallery_panel, R.id.lychee_text_edit_panel,
        R.id.lychee_numpad_panel, R.id.lychee_candidate_panel, R.id.lychee_handwriting_panel,
        R.id.lychee_resize_panel, R.id.lychee_clipboard_panel,
    )

    /**
     * The Back button every panel has, in its bottom-left corner where possible: a raised round
     * key with a soft shadow, like the mic and 繁/简.
     */
    @JvmStatic
    fun backButton(context: Context, onClick: () -> Unit): View {
        val palette = app.lychee.ui.M3Colors.of(context)
        val size = (context.resources.displayMetrics.density * 40).toInt()
        val button = android.widget.ImageView(context).apply {
            LycheeMotion.springy(this)
            setImageResource(R.drawable.ic_sym_arrow_back)
            imageTintList = android.content.res.ColorStateList.valueOf(palette.onSurface)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            background = raisedRound(context, palette.surfaceContainer)
            contentDescription = context.getString(R.string.lychee_back)
            setOnClickListener { onClick() }
        }
        // centred in whatever cell it is given, at a fixed size
        return FrameLayout(context).apply {
            addView(button, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        }
    }

    /** A round key face over a soft shadow, a little lower, like the raised keys. */
    @JvmStatic
    fun raisedRound(context: Context, color: Int): Drawable {
        val d = context.resources.displayMetrics.density
        val shadow = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(context.getColor(R.color.lychee_m3_shadow))
        }
        val face = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
        val lift = (2 * d).toInt()
        return android.graphics.drawable.LayerDrawable(arrayOf(shadow, face)).apply {
            setLayerInsetTop(0, lift)
            setLayerInsetBottom(1, lift)
        }
    }

    // Every panel's keys look like the keyboard's: the same fills, the same corners (Settings >
    // Theme), Enter in the accent colour, and ?123 / ABC as plain functional keys, not pills.

    /** The background of a functional key in the selected key style (flat or raised). */
    fun functionalKey(context: Context): Drawable? = app.lychee.ui.KeyStyle.round(context,
        app.lychee.ui.LycheeColorStyle.themed(context).getDrawable(
            if (raised(context)) R.drawable.lychee_m3_key_functional_raised else R.drawable.lychee_m3_key_functional_flat
        ))

    /** The background of a letter key in the selected key style. */
    fun letterKey(context: Context): Drawable? = app.lychee.ui.KeyStyle.round(context,
        app.lychee.ui.LycheeColorStyle.themed(context).getDrawable(
            if (raised(context)) R.drawable.lychee_m3_key_raised else R.drawable.lychee_m3_key_flat
        ))

    /** Enter, or a key that is switched on: the keyboard's accent-coloured key. */
    fun accentKey(context: Context): Drawable {
        val radius = context.resources.displayMetrics.density * 8
        fun shape(color: Int) = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = radius
            setColor(color)
        }
        val keys = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(app.lychee.ui.LycheeColorStyle.color(context, R.attr.lycheeColorAccentPressed)))
            addState(intArrayOf(), shape(app.lychee.ui.LycheeColorStyle.color(context, R.attr.lycheeColorAccent)))
        }
        return app.lychee.ui.KeyStyle.round(context, keys) ?: keys
    }

    private fun raised(context: Context) =
        KeyboardTheme.getKeyboardTheme(context).mThemeId == KeyboardTheme.THEME_ID_LYCHEE_RAISED
}
