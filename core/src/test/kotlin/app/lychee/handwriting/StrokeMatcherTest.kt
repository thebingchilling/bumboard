package app.lychee.handwriting

import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeMatcherTest {
    // Make Me a Hanzi-style medians (y up from 900)
    private val refs = listOf(
        "一" to "[[[100,450],[500,450],[900,450]]]",
        "丨" to "[[[500,850],[500,450],[500,50]]]",
        "二" to "[[[250,650],[750,650]],[[100,250],[900,250]]]",
        "十" to "[[[100,450],[900,450]],[[500,850],[500,50]]]",
        "丁" to "[[[100,800],[900,800]],[[500,800],[500,100],[420,150]]]",
    ).map { (c, j) -> c to StrokeMatcher.parseMedians(j) }
    private val matcher = StrokeMatcher(refs)

    private fun stroke(vararg xy: Float) = xy

    @Test
    fun parsesMedians() {
        val s = StrokeMatcher.parseMedians("[[[100,450],[900,450]],[[500,850],[500,50]]]")
        assertEquals(2, s.size)
        assertEquals(450f, s[0][1], 0f) // 900 - 450
        assertEquals(50f, s[1][1], 0f) // 900 - 850: the top
    }

    @Test
    fun wobblyHorizontalIsOne() {
        val best = matcher.match(listOf(stroke(10f, 52f, 60f, 49f, 120f, 55f, 180f, 50f))).first().first
        assertEquals("一", best)
    }

    @Test
    fun crossInEitherOrderIsTen() {
        val h = stroke(0f, 100f, 200f, 104f)
        val v = stroke(100f, 0f, 98f, 200f)
        assertEquals("十", matcher.match(listOf(h, v)).first().first)
        // written vertical first, and the horizontal right to left
        assertEquals("十", matcher.match(listOf(v, stroke(200f, 100f, 0f, 102f))).first().first)
    }

    @Test
    fun twoLinesAreTwo() {
        val top = stroke(50f, 60f, 150f, 62f)
        val bottom = stroke(10f, 150f, 190f, 148f)
        assertEquals("二", matcher.match(listOf(top, bottom)).first().first)
    }

    @Test
    fun twoLinesJoinedUpAreTwo() {
        // 二 written in one go: the top line, then on to the bottom line without lifting
        assertEquals("二", matcher.match(listOf(stroke(50f, 50f, 150f, 50f, 10f, 150f, 190f, 150f))).first().first)
    }
}
