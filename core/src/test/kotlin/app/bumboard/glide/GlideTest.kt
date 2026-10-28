package app.bumboard.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideTest {

    /** QWERTY key centres, keys 100 wide and 100 tall. */
    private val keys: Map<Char, KeyCenter> = buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, KeyCenter(50f + i * 100, 50f)) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, KeyCenter(100f + i * 100, 150f)) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, KeyCenter(200f + i * 100, 250f)) }
    }

    private val words = GlideWords.Builder().apply {
        listOf(
            "hello" to 200, "hell" to 180, "help" to 210, "jello" to 120, "the" to 255, "tie" to 190,
            "toe" to 170, "world" to 200, "word" to 220, "would" to 230, "don't" to 220, "dint" to 60,
        ).forEach { (w, p) -> add(w, p) }
    }.build()

    /** A path through [letters]' keys, a point every 10 units, slightly off-centre. */
    private fun path(letters: String): Pair<IntArray, IntArray> {
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        val centres = letters.map { keys.getValue(it) }
        for (i in 0 until centres.size - 1) {
            val a = centres[i]
            val b = centres[i + 1]
            val steps = maxOf(1, (kotlin.math.hypot(b.x - a.x, b.y - a.y) / 10).toInt())
            for (s in 0 until steps) {
                val t = s / steps.toFloat()
                xs += (a.x + t * (b.x - a.x) + 7).toInt()
                ys += (a.y + t * (b.y - a.y) - 5).toInt()
            }
        }
        xs += centres.last().x.toInt()
        ys += centres.last().y.toInt()
        return xs.toIntArray() to ys.toIntArray()
    }

    private fun decode(letters: String): List<String> {
        val (xs, ys) = path(letters)
        return GlideDecoder(words).decode(xs, ys, xs.size, keys, 100f).map { it.word }
    }

    @Test
    fun findsTheWord() {
        assertEquals("hello", decode("helo").first())
        assertEquals("the", decode("the").first())
        assertEquals("world", decode("world").first())
        assertEquals("help", decode("help").first())
    }

    @Test
    fun apostrophesAreSkipped() {
        assertEquals("don't", decode("dont").first())
    }

    @Test
    fun wordsMustMatchTheEnds() {
        assertTrue("hell" !in decode("helo"))
    }

    @Test
    fun builderSkipsWordsOffTheKeys() {
        val w = GlideWords.Builder().apply {
            add("café", 200)
            add("cafe", 100)
            add("x2", 100)
        }.build()
        assertEquals(1, w.size)
        assertEquals("cafe", w.word(0))
    }
}
