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
    fun shapes() {
        assertTrue(AutoCorrectPolicy.wordLike("strengths"))
        assertFalse(AutoCorrectPolicy.wordLike("xkcdq"))
        assertFalse(AutoCorrectPolicy.wordLike("helllo"))
        assertEquals(1, AutoCorrectPolicy.distance("teh", "the"))
        assertEquals(2, AutoCorrectPolicy.distance("bruh", "bro"))
    }
}
