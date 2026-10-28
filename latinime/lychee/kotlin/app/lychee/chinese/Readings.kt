package app.lychee.chinese

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.lychee.core.Annotator
import app.lychee.core.CantoneseRomanization
import app.lychee.core.DisplayPrefs
import app.lychee.core.ReadingsDb
import app.lychee.core.Sql
import java.io.File
import java.util.concurrent.Executors

/**
 * readings.db (built by lychee/data/build_data.py) and the [Annotator] over it.
 * SQLite cannot open an APK asset, so the database is copied to files/ when it changes.
 */
object Readings {
    private const val TAG = "LycheeReadings"
    private const val ASSET = "lychee/readings.db"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var annotator: Annotator? = null
        private set

    /** The database itself, for lookups beyond annotation (next characters ...). */
    @Volatile
    var db: ReadingsDb? = null
        private set
    private var opening = false

    /** Opens the database in the background; [then] runs on the main thread once it is ready. */
    fun open(context: Context, then: () -> Unit = {}) {
        if (annotator != null || opening) return
        opening = true
        val app = context.applicationContext
        executor.execute {
            try {
                val file = install(app)
                val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                val readings = ReadingsDb(AndroidSql(db))
                this.db = readings
                annotator = Annotator(readings)
            } catch (t: Throwable) {
                Log.e(TAG, "readings.db unavailable", t)
            }
            main.post {
                opening = false
                if (annotator != null) then()
            }
        }
    }

    private fun install(context: Context): File {
        val dest = File(context.noBackupFilesDir, "readings.db")
        val stamp = File(context.noBackupFilesDir, "readings.stamp")
        // Re-copied after every install or update of the app.
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (!dest.exists() || !stamp.exists() || stamp.readText() != version) {
            val tmp = File(dest.path + ".tmp")
            context.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(dest)
            stamp.writeText(version)
        }
        return dest
    }

    /** What to show under candidates, from the Chinese settings page. */
    fun displayPrefs(prefs: SharedPreferences) = DisplayPrefs(
        // one romanisation everywhere: the one Cantonese is typed in (Jyutping or Yale)
        cantonese = if (prefs.getString(ChineseInput.PREF_CANTONESE_INPUT, "jyutping") == "yale") {
            CantoneseRomanization.Yale
        } else {
            CantoneseRomanization.Jyutping
        },
        showOtherLanguage = prefs.getBoolean(PREF_OTHER_LANGUAGE, false),
        showMeaning = prefs.getBoolean(PREF_MEANING, true),
        simplified = prefs.getString(ChineseInput.PREF_SCRIPT, "traditional") == "simplified",
    )

    /** Readings under candidates are off when both the romanisation and the meaning are off. */
    fun showReadings(prefs: SharedPreferences) = prefs.getBoolean(PREF_SHOW_READINGS, true)

    /** Around each candidate: tone colours, the other script, level and register badges. */
    fun barStyle(prefs: SharedPreferences, lang: app.lychee.core.Lang?) = CandidateBarView.Style(
        toneColours = prefs.getBoolean(PREF_TONE_COLOURS, true),
        otherScript = prefs.getBoolean(PREF_OTHER_SCRIPT, true),
        badges = prefs.getBoolean(PREF_BADGES, true),
        lang = lang,
    )

    const val PREF_TONE_COLOURS = "lychee_tone_colours"
    const val PREF_OTHER_SCRIPT = "lychee_other_script"
    const val PREF_BADGES = "lychee_badges"
    const val PREF_SHOW_READINGS = "lychee_show_readings"
    const val PREF_OTHER_LANGUAGE = "lychee_other_language"
    const val PREF_MEANING = "lychee_show_meaning"
}

/** [Sql] over Android's SQLite, for [ReadingsDb]. */
class AndroidSql(private val db: SQLiteDatabase) : Sql {
    override fun <T> query(sql: String, args: Array<String>, row: (Sql.Row) -> T): List<T> {
        db.rawQuery(sql, args).use { c ->
            val r = object : Sql.Row {
                override fun string(i: Int): String? = if (c.isNull(i)) null else c.getString(i)
                override fun long(i: Int): Long = c.getLong(i)
            }
            val out = ArrayList<T>(c.count)
            while (c.moveToNext()) out += row(r)
            return out
        }
    }
}
