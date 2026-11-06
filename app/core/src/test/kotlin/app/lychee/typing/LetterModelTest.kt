package app.lychee.typing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterModelTest {
    private val model = LetterModel.Builder().apply {
        listOf("the" to 100.0, "then" to 30.0, "they" to 30.0, "this" to 40.0, "that" to 50.0,
            "quick" to 10.0, "queen" to 5.0, "rag" to 2.0, "tag" to 2.0).forEach { (w, f) -> add(w, f) }
    }.build()

    private fun c(symbol: Char, dx: Float, dy: Float = 0f) = KeyChooser.Candidate(symbol, dx, dy)

    @Test
    fun likelyLettersScoreHigher() {
        assertTrue(model.logProb("t", 'h') > model.logProb("t", 'g'))
        assertTrue(model.logProb("q", 'u') > model.logProb("q", 'y'))
        // the word's beginning: words starting "qu" go on with i or e
        assertTrue(model.logProb("qu", 'i') > model.logProb("qu", 'a'))
    }

    @Test
    fun knowsWhereWordsEnd() {
        assertTrue(model.logProb("the", ' ') > model.logProb("th", ' '))
    }

    @Test
    fun aPressWellInsideAKeyNeverChanges() {
        // "tg" is unlikely, but the press is near the middle of g (h is a key width to its right)
        assertEquals(0, KeyChooser.choose(model, "t", true, listOf(c('g', 0.2f), c('h', -0.8f)), 0))
    }

    @Test
    fun anEdgePressGoesToTheLikelierLetter() {
        // pressed g near its edge with h: after "t", h it is
        assertEquals(1, KeyChooser.choose(model, "t", true, listOf(c('g', 0.45f), c('h', -0.55f)), 0))
        // and on y's edge with u after q, u
        assertEquals(1, KeyChooser.choose(model, "q", true, listOf(c('y', 0.45f), c('u', -0.55f)), 0))
    }

    @Test
    fun theSpaceBarEndsAWordPressedOnItsEdge() {
        // after "the", a press at the bottom of n, just above the space bar, ends the word
        assertEquals(1, KeyChooser.choose(model, "the", true, listOf(c('n', 0f, 0.48f), c(' ', 0f, -0.52f)), 0))
    }

    @Test
    fun farKeysAreIgnored() {
        assertEquals(0, KeyChooser.choose(model, "t", true, listOf(c('g', 0f), c('h', -1.0f)), 0))
    }

    @Test
    fun withoutAModelTheNearestAimWins() {
        // the press is over g, but nearer where this person's presses for h land
        assertEquals(1, KeyChooser.choose(null, "", true, listOf(c('g', 0.3f), c('h', -0.2f)), 0))
    }

    @Test
    fun keepsItself() {
        val bytes = ByteArrayOutputStream().also { out -> DataOutputStream(out).use { model.write(it) } }.toByteArray()
        val again = LetterModel.read(DataInputStream(ByteArrayInputStream(bytes)))
        assertNotNull(again)
        for ((word, next) in listOf("th" to 'e', "qu" to 'i', "the" to ' ', "zz" to 'q')) {
            assertEquals(model.logProb(word, next), again!!.logProb(word, next), 1e-9)
        }
    }
}
