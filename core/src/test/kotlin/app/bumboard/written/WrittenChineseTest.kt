package app.bumboard.written

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class WrittenChineseTest {

    /** The real table (data/swc/swc.tsv, Traditional). */
    private val converter = WrittenChinese.parse(
        File("../data/swc/swc.tsv").readLines(Charsets.UTF_8).asSequence(),
    )

    @Test
    fun colloquialToWritten() {
        assertEquals("他們在這裡正在吃飯", converter.convert("佢哋喺度食緊飯"))
        assertEquals("我們明天去喝茶吧", converter.convert("我哋聽日去飲茶啦"))
        assertEquals("我不知道他為什麼不來", converter.convert("我唔知佢點解唔嚟"))
        assertEquals("他說他很累", converter.convert("佢話佢好攰"))
        assertEquals("這個是什麼？", converter.convert("呢個係乜嘢嚟㗎？"))
    }

    @Test
    fun standardWordsStay() {
        assertEquals("他和我的關係很好", converter.convert("佢同我嘅關係好好"))
        assertEquals("現在幾點？", converter.convert("而家幾點？"))
        assertEquals("我很喜歡這間餐廳的點心", converter.convert("我好鍾意呢間餐廳嘅點心"))
        assertEquals("同學們都同意", converter.convert("同學們都同意"))
        assertEquals("企業", converter.convert("企業"))
    }

    @Test
    fun plainTextIsUnchanged() {
        assertEquals("Hello, world.", converter.convert("Hello, world."))
        assertEquals("", converter.convert(""))
    }

    @Test
    fun longestMatchWins() {
        val c = WrittenChinese(mapOf("a" to "1", "ab" to "2", "abc" to "3"))
        assertEquals("3", c.convert("abc"))
        assertEquals("21", c.convert("aba"))
    }
}
