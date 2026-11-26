package app.lychee.screens

import app.lychee.core.ReadingsDb
import app.lychee.core.Sql
import app.lychee.dict.PackData
import java.io.File
import java.sql.Connection

/**
 * The real data in the screenshots: the readings dictionary and every pack (stroke order,
 * examples, the English, Japanese and Korean dictionaries), as tools/screens-data.sh built and
 * unpacked them into build/screens-data, read through the desktop's SQLite (layoutlib has no
 * Android SQLite) by the app's own look-ups. Nothing made up.
 */
object RealData {
    private val dir = File("build/screens-data")

    /** Whether the real data is there (screens.sh makes it when it isn't). */
    val available: Boolean get() = File(dir, "readings.db").exists()

    /** Points the app's look-ups at the real data. */
    fun install() {
        check(available) { "No real data: run tools/screens-data.sh" }
        app.lychee.chinese.Readings.useForScreens(ReadingsDb(JdbcSql(open(File(dir, "readings.db")))))
        PackData.forScreensStrokes = null
        PackData.forScreensPhrases = null
        PackData.forScreensContaining = null
        PackData.forScreensDb = { id, name -> File(dir, "$id/data/$name").takeIf { it.exists() }?.let { JdbcPackDb(open(it)) } }
    }

    private val connections = HashMap<String, Connection>()

    private fun open(file: File): Connection = connections.getOrPut(file.path) {
        Class.forName("org.sqlite.JDBC")
        // read-only: the files are the real packs, never changed
        org.sqlite.SQLiteConfig().apply { setReadOnly(true) }.createConnection("jdbc:sqlite:" + file.absolutePath)
    }

    /** The core look-ups' [Sql] over a JDBC connection. */
    private class JdbcSql(private val c: Connection) : Sql {
        override fun <T> query(sql: String, args: Array<String>, row: (Sql.Row) -> T): List<T> =
            c.prepareStatement(sql).use { st ->
                args.forEachIndexed { i, a -> st.setString(i + 1, a) }
                st.executeQuery().use { rs ->
                    val r = object : Sql.Row {
                        override fun string(i: Int): String? = rs.getString(i + 1)
                        override fun long(i: Int): Long = rs.getLong(i + 1)
                    }
                    buildList { while (rs.next()) add(row(r)) }
                }
            }
    }

    /** A pack's database for [PackData]: each query's rows in a MatrixCursor, as Android would hand them. */
    private class JdbcPackDb(private val c: Connection) : PackData.PackDb {
        override fun rawQuery(sql: String, args: Array<out String>?): android.database.Cursor =
            c.prepareStatement(sql).use { st ->
                args?.forEachIndexed { i, a -> st.setString(i + 1, a) }
                st.executeQuery().use { rs ->
                    val meta = rs.metaData
                    val cols = Array(meta.columnCount) { meta.getColumnLabel(it + 1) }
                    val cursor = android.database.MatrixCursor(cols)
                    while (rs.next()) cursor.addRow(Array<Any?>(cols.size) { rs.getObject(it + 1) })
                    cursor
                }
            }
    }
}
