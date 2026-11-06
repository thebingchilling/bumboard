package app.lychee.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchModelTest {
    @Test
    fun learnsWhereAKeysPressesLand() {
        val t = TouchModel()
        repeat(100) { t.learn('a', 0.1f, 0.2f) }
        val a = t.offset('a')
        assertEquals(0.1f, a[0], 0.02f)
        assertEquals(0.2f, a[1], 0.02f)
        // a key not pressed yet starts from where every key's presses land
        val b = t.offset('B')
        assertTrue(b[1] > 0.05f && b[1] < a[1])
    }

    @Test
    fun startsAtTheCentresAndIgnoresStrayPresses() {
        val t = TouchModel()
        assertEquals(0f, t.offset('e')[0], 0f)
        t.learn('e', 2f, 0f)
        assertEquals(0, t.presses())
    }

    @Test
    fun neverMovesAnAimFar() {
        val t = TouchModel()
        repeat(500) { t.learn('q', 0.7f, -0.7f) }
        val q = t.offset('q')
        assertTrue(q[0] <= 0.35f && q[1] >= -0.35f)
    }

    @Test
    fun keepsItsCounts() {
        val t = TouchModel()
        repeat(20) { t.learn('k', -0.1f, 0.15f) }
        val again = TouchModel.fromArray(t.toArray())
        assertNotNull(again)
        assertEquals(t.offset('k')[1], again!!.offset('k')[1], 1e-6f)
    }
}
