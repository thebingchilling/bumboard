package app.lychee.typing

import app.lychee.typing.AutoCorrectPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCorrectPolicyTest {
    @Test
    fun informalWordsStay() {
        assertEquals(Decision.KEEP, AutoCorrectPolicy.decide("bruh", "bro"))
        assertEquals(Decision.KEEP, AutoCorrectPolicy.decide("Nah", "bah"))
        assertEquals(Decision.KEEP, AutoCorrectPolicy.decide("lmao", "lamp"))
    }

    @Test
    fun jumblesAreCorrected() {
        assertEquals(Decision.CORRECT, AutoCorrectPolicy.decide("teh", "the"))
        assertEquals(Decision.CORRECT, AutoCorrectPolicy.decide("becuase", "because"))
        assertEquals(Decision.CORRECT, AutoCorrectPolicy.decide("hlelo", "hello"))
        assertEquals(Decision.CORRECT, AutoCorrectPolicy.decide("sdfg", "safe"))
    }

    @Test
    fun wordLikeUnknownWordsStay() {
        // a plausible word two slips from the dictionary's
        assertEquals(Decision.KEEP, AutoCorrectPolicy.decide("yeet", "yes"))
        assertEquals(Decision.KEEP, AutoCorrectPolicy.decide("vibin", "voting"))
    }

    @Test
    fun oneSlipIsLeftToTheThreshold() {
        assertEquals(Decision.DEFAULT, AutoCorrectPolicy.decide("helo", "hello"))
        assertEquals(Decision.DEFAULT, AutoCorrectPolicy.decide("definately", "definitely"))
    }

    @Test
    fun slipsOntoNeighbouringKeysAreCorrected() {
        // typos from a real message, which used to be left alone
        assertEquals(Decision.CORRECT, AutoCorrectPolicy.decide("doesnr", "doesn't"))
        assertEquals(Decision.CORRECT, AutoCorrectPolicy.decide("fratures", "features"))
        assertEquals(Decision.DEFAULT, AutoCorrectPolicy.decide("habr", "have"))
        assertEquals(Decision.DEFAULT, AutoCorrectPolicy.decide("autoecocrect", "autocorrect"))
    }

    @Test
    fun missingApostrophesAreCorrected() {
        assertEquals(Decision.CORRECT, AutoCorrectPolicy.decide("dont", "don't"))
        assertEquals(Decision.CORRECT, AutoCorrectPolicy.decide("theyre", "they're"))
    }

    @Test
    fun keyDistance() {
        assertTrue(AutoCorrectPolicy.neighbours('r', 't'))
        assertTrue(AutoCorrectPolicy.neighbours('e', 's'))
        assertTrue(AutoCorrectPolicy.neighbours('b', 'v'))
        assertFalse(AutoCorrectPolicy.neighbours('a', 'p'))
        assertEquals(0.5f, AutoCorrectPolicy.keyDistance("doesnr", "doesn't"), 0.001f)
        assertEquals(0f, AutoCorrectPolicy.keyDistance("dont", "don't"), 0.001f)
    }

    @Test
    fun shapes() {
        assertTrue(AutoCorrectPolicy.wordLike("strengths"))
        assertFalse(AutoCorrectPolicy.wordLike("xkcdq"))
        assertFalse(AutoCorrectPolicy.wordLike("helllo"))
        assertEquals(1, AutoCorrectPolicy.distance("teh", "the"))
        assertEquals(2, AutoCorrectPolicy.distance("bruh", "bro"))
    }
}
