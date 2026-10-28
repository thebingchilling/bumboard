package app.lychee.core

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Runs against the real readings.db built by lychee/data/build_data.py. */
class ReadingsDbTest {

    private lateinit var conn: Connection
    private lateinit var annotator: Annotator

    @Before
    fun open() {
        val path = System.getProperty("lychee.readingsDb") ?: ""
        assumeTrue("readings.db not built", File(path).isFile)
        conn = DriverManager.getConnection("jdbc:sqlite:$path")
        val sql = object : Sql {
            override fun <T> query(sql: String, args: Array<String>, row: (Sql.Row) -> T): List<T> {
                conn.prepareStatement(sql).use { st ->
                    args.forEachIndexed { i, a -> st.setString(i + 1, a) }
                    st.executeQuery().use { rs ->
                        val r = object : Sql.Row {
                            override fun string(i: Int): String? = rs.getString(i + 1)
                            override fun long(i: Int): Long = rs.getLong(i + 1)
                        }
                        val out = ArrayList<T>()
                        while (rs.next()) out += row(r)
                        return out
                    }
                }
            }
        }
        annotator = Annotator(ReadingsDb(sql))
    }

    private fun annotate(text: String, comment: String, mode: InputMode, prefs: DisplayPrefs = DisplayPrefs()) =
        annotator.annotate(text, comment, mode, prefs)

    @Test
    fun cantoneseWords() {
        val a = annotate("雪櫃", "syut3 gwai6", InputMode.Cantonese)
        assertEquals(listOf("syut3 gwai6"), a.lines)
        assertEquals("fridge", a.meaning)
        assertEquals("yes", annotate("係", "hai6", InputMode.Cantonese).meaning)
    }

    @Test
    fun simplifiedFormsResolve() {
        val a = annotate("头发", "tau4 faat3", InputMode.Cantonese)
        assertEquals(listOf("tau4 faat3"), a.lines)
        assertEquals("hair", a.meaning)
    }

    @Test
    fun mandarinWithOtherLanguage() {
        val a = annotate("你好", "ni hao", InputMode.Mandarin, DisplayPrefs(showOtherLanguage = true))
        assertEquals(listOf("nǐ hǎo", "nei5 hou2"), a.lines)
        assertEquals("hello", a.meaning)
    }

    @Test
    fun polyphoneFollowsHint() {
        assertEquals(listOf("hòhng"), annotate("行", "hong4", InputMode.Cantonese,
            DisplayPrefs(cantonese = CantoneseRomanization.Yale)).lines.map { it })
        assertEquals(listOf("xíng"), annotate("行", "xing", InputMode.Mandarin).lines)
    }

    @Test
    fun composedSentence() {
        val a = annotate("我哋去飲茶", "ngo5 dei6 heoi3 jam2 caa4", InputMode.Cantonese)
        assertEquals(listOf("ngo5 dei6 heoi3 jam2 caa4"), a.lines)
    }
}
