package app.bumboard.layout

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.android.inputmethod.keyboard.KeyboardTheme
import com.android.inputmethod.latin.R

/** Helpers for the panels drawn over the keys (voice, shortcuts, text editing, resizing). */
object BumPanels {

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
                if (panel is app.bumboard.voice.VoicePanelView) panel.cancel() else panel.visibility = View.GONE
            }
        }
    }

    @JvmStatic
    fun anyShown(inputView: View): Boolean =
        PANELS.any { inputView.findViewById<View>(it)?.visibility == View.VISIBLE }

    private val PANELS = intArrayOf(
        R.id.bum_voice_panel, R.id.bum_gallery_panel, R.id.bum_text_edit_panel,
        R.id.bum_resize_panel, R.id.bum_numpad_panel, R.id.bum_candidate_panel,
        R.id.bum_float_resize, R.id.bum_handwriting_panel,
    )

    /**
     * The Back button every panel has, in its bottom-left corner where possible: a filled
     * circle in the accent colour, so it never looks like one of the keys.
     */
    @JvmStatic
    fun backButton(context: Context, onClick: () -> Unit): View {
        val palette = app.bumboard.ui.M3Colors.of(context)
        val size = (context.resources.displayMetrics.density * 40).toInt()
        val button = android.widget.ImageView(context).apply {
            setImageResource(R.drawable.ic_sym_arrow_back)
            imageTintList = android.content.res.ColorStateList.valueOf(palette.onPrimaryContainer)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(palette.primaryContainer)
            }
            contentDescription = context.getString(R.string.bum_back)
            setOnClickListener { onClick() }
        }
        // centred in whatever cell it is given, at a fixed size
        return FrameLayout(context).apply {
            addView(button, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        }
    }

    /** The background of a functional key in the selected key style (flat or raised). */
    fun functionalKey(context: Context): Drawable? = app.bumboard.ui.BumColorStyle.themed(context).getDrawable(
        if (raised(context)) R.drawable.bum_m3_key_functional_raised else R.drawable.bum_m3_key_functional_flat
    )

    /** The background of a letter key in the selected key style. */
    fun letterKey(context: Context): Drawable? = app.bumboard.ui.BumColorStyle.themed(context).getDrawable(
        if (raised(context)) R.drawable.bum_m3_key_raised else R.drawable.bum_m3_key_flat
    )

    private fun raised(context: Context) =
        KeyboardTheme.getKeyboardTheme(context).mThemeId == KeyboardTheme.THEME_ID_BUM_RAISED
}
