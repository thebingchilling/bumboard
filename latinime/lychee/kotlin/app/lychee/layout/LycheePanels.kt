package app.lychee.layout

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.android.inputmethod.keyboard.KeyboardTheme
import com.android.inputmethod.latin.R

/** Helpers for the panels drawn over the keys (voice, shortcuts, text editing, resizing). */
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

    /** Hides every panel over the keys except [except]. */
    @JvmStatic
    fun hideOthers(inputView: View, except: View?) {
        for (id in PANELS) {
            val panel = inputView.findViewById<View>(id) ?: continue
            if (panel !== except && panel.visibility == View.VISIBLE) {
                if (panel is app.lychee.voice.VoicePanelView) panel.cancel() else panel.visibility = View.GONE
            }
        }
    }

    @JvmStatic
    fun anyShown(inputView: View): Boolean =
        PANELS.any { inputView.findViewById<View>(it)?.visibility == View.VISIBLE }

    /** Whether a panel other than [except] is open over the keys. */
    fun anyOtherShown(inputView: View, except: View): Boolean =
        PANELS.any { id -> inputView.findViewById<View>(id)?.let { it !== except && it.visibility == View.VISIBLE } == true }

    private val PANELS = intArrayOf(
        R.id.lychee_voice_panel, R.id.lychee_gallery_panel, R.id.lychee_text_edit_panel,
        R.id.lychee_resize_panel, R.id.lychee_numpad_panel, R.id.lychee_candidate_panel,
        R.id.lychee_float_resize, R.id.lychee_handwriting_panel,
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

    /** The background of a functional key in the selected key style (flat or raised). */
    fun functionalKey(context: Context): Drawable? = app.lychee.ui.LycheeColorStyle.themed(context).getDrawable(
        if (raised(context)) R.drawable.lychee_m3_key_functional_raised else R.drawable.lychee_m3_key_functional_flat
    )

    /** The background of a letter key in the selected key style. */
    fun letterKey(context: Context): Drawable? = app.lychee.ui.LycheeColorStyle.themed(context).getDrawable(
        if (raised(context)) R.drawable.lychee_m3_key_raised else R.drawable.lychee_m3_key_flat
    )

    private fun raised(context: Context) =
        KeyboardTheme.getKeyboardTheme(context).mThemeId == KeyboardTheme.THEME_ID_LYCHEE_RAISED
}
