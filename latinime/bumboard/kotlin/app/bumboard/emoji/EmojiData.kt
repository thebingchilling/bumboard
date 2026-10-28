package app.bumboard.emoji

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** One emoji as shown in the panel; [tones] holds its skin-tone variants, if any. */
class Emoji(val base: String, val name: String, val keywords: String, val tones: List<String>?) {
    /** Two people with a tone each: 25 variants, index (a - 1) * 5 + (b - 1). */
    val twoPeople: Boolean get() = tones?.size == 25
}

class EmojiGroup(val id: String, val items: List<Emoji>)

/**
 * emoji.json and the bundled Noto Color Emoji font (both built by bumboard/data/build_emoji.py),
 * loaded once in the background.
 */
object EmojiData {
    private const val TAG = "BumEmoji"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val waiting = mutableListOf<() -> Unit>()

    @Volatile
    var groups: List<EmojiGroup> = emptyList()
        private set

    @Volatile
    var typeface: Typeface = Typeface.DEFAULT
        private set

    @Volatile
    private var loaded = false
    private var loading = false

    /** Every emoji by its base form and by each skin-tone variant, for recents. */
    @Volatile
    var byForm: Map<String, Emoji> = emptyMap()
        private set

    fun load(context: Context, then: () -> Unit) {
        if (loaded) {
            then()
            return
        }
        waiting += then
        if (loading) return
        loading = true
        val app = context.applicationContext
        executor.execute {
            try {
                typeface = app.bumboard.fonts.BumFonts.bundledEmoji(app)
                val json = app.assets.open("bumboard/emoji.json").bufferedReader().use { it.readText() }
                groups = parse(JSONObject(json))
                byForm = buildMap {
                    groups.forEach { g -> g.items.forEach { e -> put(e.base, e); e.tones?.forEach { put(it, e) } } }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "emoji data unavailable", t)
            }
            main.post {
                loaded = true
                loading = false
                waiting.forEach { it() }
                waiting.clear()
            }
        }
    }

    private fun parse(root: JSONObject): List<EmojiGroup> {
        val groups = root.getJSONArray("groups")
        return (0 until groups.length()).map { g ->
            val group = groups.getJSONObject(g)
            val items = group.getJSONArray("items")
            EmojiGroup(group.getString("id"), (0 until items.length()).map { i ->
                val item = items.getJSONArray(i)
                val tones = if (item.length() > 3) item.getJSONArray(3).strings() else null
                Emoji(item.getString(0), item.getString(1), item.getString(2), tones)
            })
        }
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    /** Emoji whose name or keywords contain every word of [query], best matches first. */
    fun search(query: String, limit: Int = 80): List<Emoji> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val first = words.first()
        return groups.asSequence().flatMap { it.items.asSequence() }
            .filter { e -> words.all { e.keywords.contains(it) } }
            .sortedBy { e ->
                when {
                    e.name.startsWith(first) -> 0
                    e.keywords.split(' ').any { it.startsWith(first) } -> 1
                    else -> 2
                }
            }
            .take(limit)
            .toList()
    }
}

/** Recently used emoji and the skin tone last chosen for each emoji. */
class EmojiStore(private val prefs: SharedPreferences) {

    fun recents(): List<String> =
        prefs.getString(RECENTS, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    fun addRecent(emoji: String) {
        val list = listOf(emoji) + recents().filter { it != emoji }
        prefs.edit().putString(RECENTS, list.take(MAX_RECENTS).joinToString(SEPARATOR)).apply()
    }

    /** The variant to show for [emoji]: the tone chosen last time, or the plain emoji. */
    fun shown(emoji: Emoji): String =
        if (emoji.tones == null) emoji.base else prefs.getString(TONE + emoji.base, null) ?: emoji.base

    fun choose(emoji: Emoji, variant: String) {
        prefs.edit().putString(TONE + emoji.base, variant).apply()
    }

    private companion object {
        const val RECENTS = "bum_emoji_recents"
        const val TONE = "bum_emoji_tone_"
        const val SEPARATOR = "\u0001"
        const val MAX_RECENTS = 32
    }
}
