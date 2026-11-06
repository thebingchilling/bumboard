package app.lychee.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterModelTest {
    private val model = LetterModel.Builder().apply {
        listOf("the" to 100.0, "then" to 30.0, "they" to 30.0, "this" to 40.0, "that" to 50.0,
            "quick" to 10.0, "queen" to 5.0, "rag" to 2.0, "tag" to 2.0).forEach { (w, f) -> add(w, f) }
    }.build()

    @Test
    fun likelyLettersScoreHigher() {
        assertTrue(model.logProb("t", 'h') > model.logProb("t", 'g'))
        assertTrue(model.logProb("q", 'u') > model.logProb("q", 'y'))
    }

    @Test
    fun aPressWellInsideAKeyNeverChanges() {
        // "tg" is unlikely, but the press is in the middle of g
        assertEquals(0, LetterModel.choose(model, "t", listOf('g' to 0f, 'h' to 0.4f)))
    }

    @Test
    fun anEdgePressGoesToTheLikelierLetter() {
        // pressed g right at its edge with h: after "t", h it is
        assertEquals(1, LetterModel.choose(model, "t", listOf('g' to 0f, 'h' to 0.05f)))
        // and on y's edge with u after q, u
        assertEquals(1, LetterModel.choose(model, "q", listOf('y' to 0f, 'u' to 0.05f)))
    }

    @Test
    fun keyedOnFarNeighboursIsIgnored() {
        assertEquals(0, LetterModel.choose(model, "t", listOf('g' to 0f, 'h' to 0.8f)))
    }

    @Test
    fun keepsItsCounts() {
        val again = LetterModel.fromArray(model.toArray())!!
        assertEquals(model.logProb("th", 'e'), again.logProb("th", 'e'), 1e-9)
    }
}
