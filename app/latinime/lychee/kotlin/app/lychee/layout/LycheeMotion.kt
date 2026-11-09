package app.lychee.layout

import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.keyboard.KeyboardId

/**
 * The keyboard's motion, like Gboard's: every panel over the keys (shortcuts, text editing,
 * number pad, handwriting, voice, resizing, candidates, word cards, emoji) fades in rising a
 * little and fades out as it goes; the keys cross-fade when they change (letters and symbols,
 * one language and another). Nothing moves when the phone's animations are off.
 */
object LycheeMotion {
    /** How long a panel takes to come in, and to go. */
    private const val IN = 220L
    private const val OUT = 140L
    private const val KEYS = 160L

    /** Animates [group]'s children as they show and hide (the input view's panels). */
    @JvmStatic
    fun install(group: ViewGroup) {
        val rise = group.resources.displayMetrics.density * 16
        val transition = object : LayoutTransition() {
            // the number pad is part of the keyboard, not a tool over it: it changes in the way
            // the keys change (letters to symbols), and simply goes when it closes
            override fun showChild(parent: ViewGroup, child: View, oldVisibility: Int) {
                if (child is app.lychee.toolbar.NumpadPanelView) {
                    keysIn(child)
                    return
                }
                super.showChild(parent, child, oldVisibility)
            }

            override fun hideChild(parent: ViewGroup, child: View, newVisibility: Int) {
                if (child is app.lychee.toolbar.NumpadPanelView) return
                super.hideChild(parent, child, newVisibility)
            }
        }.apply {
            // only the panels themselves: the others keep still (no sliding around them)
            disableTransitionType(LayoutTransition.CHANGE_APPEARING)
            disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
            disableTransitionType(LayoutTransition.CHANGING)
            setAnimator(LayoutTransition.APPEARING, AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(null, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(null, View.TRANSLATION_Y, rise, 0f),
                )
            })
            setDuration(LayoutTransition.APPEARING, IN)
            setInterpolator(LayoutTransition.APPEARING, DecelerateInterpolator(2f))
            setStartDelay(LayoutTransition.APPEARING, 0)
            setAnimator(LayoutTransition.DISAPPEARING, AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(null, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(null, View.TRANSLATION_Y, 0f, rise / 2),
                )
            })
            setDuration(LayoutTransition.DISAPPEARING, OUT)
            setInterpolator(LayoutTransition.DISAPPEARING, AccelerateInterpolator())
            setStartDelay(LayoutTransition.DISAPPEARING, 0)
            // a gone panel ends as it was, ready to come in again
            addTransitionListener(object : LayoutTransition.TransitionListener {
                override fun startTransition(t: LayoutTransition, c: ViewGroup, v: View, type: Int) {}
                override fun endTransition(t: LayoutTransition, c: ViewGroup, v: View, type: Int) {
                    v.alpha = 1f
                    v.translationY = 0f
                }
            })
        }
        group.layoutTransition = transition
    }

    /** The number pad coming in: it fades in like changed keys do (no scaling). */
    @JvmStatic
    fun keysIn(view: View) {
        if (!LycheePanels.animate(view.context)) return
        view.animate().cancel()
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(KEYS).setInterpolator(DecelerateInterpolator(1.5f)).start()
    }

    /**
     * The keys changed from [old] to [new]: when it's another set of keys (letters and
     * symbols, another language), the keys that changed fade in, and those that stayed the
     * same (the same label in the same place: the bottom row, Enter, backspace ...) don't flash.
     */
    @JvmStatic
    fun keyboardChanged(view: com.android.inputmethod.keyboard.KeyboardView, old: Keyboard?, new: Keyboard) {
        if (old == null || !view.isShown || !LycheePanels.animate(view.context)) return
        if (kind(old.mId) == kind(new.mId) && old.mId.mSubtype == new.mId.mSubtype) return
        val stayed = HashSet<String>()
        for (k in old.sortedKeys) stayed += signature(k)
        val changed = new.sortedKeys.filterTo(HashSet()) { signature(it) !in stayed }
        view.lycheeFadeIn(changed, KEYS)
    }

    /** A key as it shows: where it is, its label or icon, what it types. */
    private fun signature(k: com.android.inputmethod.keyboard.Key): String =
        "${k.x},${k.y},${k.width},${k.height}|${k.label}|${k.iconId}|${k.code}|${k.hintLabel}"

    /**
     * Shows or hides [view] (the bar's toolbar and suggestions taking turns): one coming in
     * fades in; one going goes at once, so the two never overlap.
     */
    @JvmStatic
    fun fade(view: View, visible: Boolean, hidden: Int) {
        val target = if (visible) View.VISIBLE else hidden
        if (view.visibility == target) return
        view.visibility = target
        if (!visible || !view.isAttachedToWindow || !LycheePanels.animate(view.context)) {
            view.animate().cancel()
            view.alpha = 1f
            return
        }
        view.animate().cancel()
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(FADE).setInterpolator(DecelerateInterpolator()).start()
    }

    private const val FADE = 150L

    /** Letters (shifted or not), symbols (either page), or anything else on its own. */
    private fun kind(id: KeyboardId): Int = when {
        id.isAlphabetKeyboard -> 0
        id.mElementId == KeyboardId.ELEMENT_SYMBOLS || id.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> 1
        else -> 2 + id.mElementId
    }
}
