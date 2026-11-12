package app.lychee.layout

import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.keyboard.KeyboardId

/**
 * The keyboard's one way of moving. Everything is part of the keyboard, never a window opening
 * over it, so nothing slides or flies in: whatever comes (another set of keys, a panel over the
 * keys such as shortcuts, text editing, the number pad, handwriting, clipboard, voice, emoji,
 * candidates) cross-fades in place, settling from a hair smaller; whatever goes fades out the
 * same way, a little quicker. Cards (the word card, an emoji's card, the keyboard list) grow
 * out of what was touched with the same timing and easing. Nothing moves when the phone's
 * animations are off.
 */
object LycheeMotion {
    /** How long something takes to come in, and to go (Material 3's medium and short durations). */
    const val IN = 220L
    const val OUT = 150L

    /** Material 3's emphasized easing: quick to start and long to settle; and its way out. */
    @JvmField
    val enterEasing = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    @JvmField
    val exitEasing = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    /** The size something settles from (panels and keys), and fades to on its way out. */
    private const val FROM = 0.97f
    private const val TO = 0.985f
    /** The size a card grows from. */
    private const val CARD_FROM = 0.9f

    /** Animates [group]'s children as they show and hide (the input view's panels). */
    @JvmStatic
    fun install(group: ViewGroup) {
        val transition = LayoutTransition().apply {
            // only the panels themselves: the others keep still (no sliding around them)
            disableTransitionType(LayoutTransition.CHANGE_APPEARING)
            disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
            disableTransitionType(LayoutTransition.CHANGING)
            setAnimator(LayoutTransition.APPEARING, AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(null, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(null, View.SCALE_X, FROM, 1f),
                    ObjectAnimator.ofFloat(null, View.SCALE_Y, FROM, 1f),
                )
            })
            setDuration(LayoutTransition.APPEARING, IN)
            setInterpolator(LayoutTransition.APPEARING, enterEasing)
            setStartDelay(LayoutTransition.APPEARING, 0)
            setAnimator(LayoutTransition.DISAPPEARING, AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(null, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(null, View.SCALE_X, 1f, TO),
                    ObjectAnimator.ofFloat(null, View.SCALE_Y, 1f, TO),
                )
            })
            setDuration(LayoutTransition.DISAPPEARING, OUT)
            setInterpolator(LayoutTransition.DISAPPEARING, exitEasing)
            setStartDelay(LayoutTransition.DISAPPEARING, 0)
            // a gone panel ends as it was, ready to come in again
            addTransitionListener(object : LayoutTransition.TransitionListener {
                override fun startTransition(t: LayoutTransition, c: ViewGroup, v: View, type: Int) {}
                override fun endTransition(t: LayoutTransition, c: ViewGroup, v: View, type: Int) {
                    reset(v)
                }
            })
        }
        group.layoutTransition = transition
    }

    /** [view] comes in: a cross-fade in place, settling from a hair smaller. */
    @JvmStatic
    fun enter(view: View) {
        if (!LycheePanels.animate(view.context)) return
        view.animate().cancel()
        view.alpha = 0f
        view.scaleX = FROM
        view.scaleY = FROM
        view.translationY = 0f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(IN)
            .setInterpolator(enterEasing).start()
    }

    /** [view] goes: it fades out the same way, then [end] runs (hiding it). */
    @JvmStatic
    fun exit(view: View, end: Runnable) {
        if (!LycheePanels.animate(view.context) || !view.isShown) {
            end.run()
            return
        }
        view.animate().cancel()
        view.animate().alpha(0f).scaleX(TO).scaleY(TO).setStartDelay(0).setDuration(OUT)
            .setInterpolator(exitEasing).withEndAction {
                end.run()
                reset(view)
            }.start()
    }

    /** Keys coming in (another set of keys, the number pad): the same as any panel. */
    @JvmStatic
    fun keysIn(view: View) = enter(view)

    /** The keys changed from [old] to [new]: another set of keys comes in. */
    @JvmStatic
    fun keyboardChanged(view: View, old: Keyboard?, new: Keyboard) {
        if (old == null || !view.isShown || !LycheePanels.animate(view.context)) return
        if (kind(old.mId) == kind(new.mId) && old.mId.mSubtype == new.mId.mSubtype) return
        keysIn(view)
    }

    /**
     * A card coming in (the word card, an emoji's card, the keyboard list): it grows out of
     * ([pivotX], [pivotY]) in its own coordinates, or its middle, as it fades in.
     */
    @JvmStatic
    @JvmOverloads
    fun cardIn(card: View, pivotX: Float = Float.NaN, pivotY: Float = Float.NaN) {
        if (!LycheePanels.animate(card.context)) return
        card.animate().cancel()
        card.alpha = 0f
        card.scaleX = CARD_FROM
        card.scaleY = CARD_FROM
        card.translationY = 0f
        card.post {
            card.pivotX = if (pivotX.isNaN()) card.width / 2f else pivotX
            card.pivotY = if (pivotY.isNaN()) card.height / 2f else pivotY
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(IN)
                .setInterpolator(enterEasing).start()
        }
    }

    /** A card going: it shrinks back a little as it fades, then [end] runs. */
    @JvmStatic
    fun cardOut(card: View, end: Runnable) {
        if (!LycheePanels.animate(card.context)) {
            end.run()
            return
        }
        card.animate().cancel()
        card.animate().alpha(0f).scaleX(CARD_FROM).scaleY(CARD_FROM).setStartDelay(0).setDuration(OUT)
            .setInterpolator(exitEasing).withEndAction(end).start()
    }

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
        view.animate().alpha(1f).setDuration(OUT).setInterpolator(enterEasing).start()
    }

    private fun reset(v: View) {
        v.alpha = 1f
        v.scaleX = 1f
        v.scaleY = 1f
        v.translationY = 0f
    }

    /** Letters (shifted or not), symbols (either page), or anything else on its own. */
    private fun kind(id: KeyboardId): Int = when {
        id.isAlphabetKeyboard -> 0
        id.mElementId == KeyboardId.ELEMENT_SYMBOLS || id.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> 1
        else -> 2 + id.mElementId
    }
}
