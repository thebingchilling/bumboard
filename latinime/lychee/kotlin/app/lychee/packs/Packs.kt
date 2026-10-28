package app.lychee.packs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.preference.PreferenceManager
import androidx.browser.customtabs.CustomTabsIntent
import org.json.JSONObject
import java.io.File

/** What a pack holds; decides how it is checked on import and where it is used. */
enum class PackKind(val id: String) {
    FONT_ZH("font_zh"),
    FONT_EMOJI("font_emoji"),
    HANDWRITING("handwriting"),
    RIME("rime"),
    VOICE("voice");

    val isFont get() = this == FONT_ZH || this == FONT_EMOJI

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** One entry of the pack catalogue (assets/lychee/packs.json). */
data class Pack(
    val id: String,
    val kind: PackKind,
    val name: String,
    val summary: String,
    val version: String,
    /** The pack's source (its release page), for credit and updates; empty for "your own file". */
    val url: String,
    /** A direct link to the file (on Lychee's packs release): the browser saves it at once. */
    val download: String,
    /** The file to download, as shown to the user. */
    val file: String,
    /** Pins the exact file when set. */
    val sha256: String?,
    /** Chooses the font inside a downloaded zip, and [prefer] picks among several matches. */
    val pick: Regex?,
    val prefer: Regex?,
    val size: String,
    val license: String,
    val credit: String,
    /** Any file of this kind is accepted after it passes its checks. */
    val custom: Boolean,
    /** Built by Lychee: a zip with pack.json naming this id. */
    val built: Boolean,
    /** Speech language for voice packs ("en", "cmn", ...). */
    val lang: String?,
)

/**
 * Lychee's optional downloads. Lychee itself never touches the network (it has no INTERNET
 * permission): [openPage] shows a pack's download page in the browser, which saves the file,
 * and [PackImporter] copies it in from wherever the user picks it.
 */
object Packs {
    private var cached: List<Pack>? = null
    var releasePage: String = ""
        private set

    fun catalog(context: Context): List<Pack> {
        cached?.let { return it }
        val json = context.assets.open("lychee/packs.json").bufferedReader().use { JSONObject(it.readText()) }
        releasePage = json.optString("release")
        val list = json.getJSONArray("packs")
        val packs = (0 until list.length()).mapNotNull { i ->
            val o = list.getJSONObject(i)
            val kind = PackKind.of(o.getString("kind")) ?: return@mapNotNull null
            Pack(
                id = o.getString("id"),
                kind = kind,
                name = o.getString("name"),
                summary = o.optString("summary"),
                version = o.optString("version"),
                url = o.optString("url"),
                download = o.optString("download"),
                file = o.optString("file"),
                sha256 = o.optString("sha256").takeIf { it.isNotEmpty() }?.lowercase(),
                pick = o.optString("pick").takeIf { it.isNotEmpty() }?.let { Regex(it) },
                prefer = o.optString("prefer").takeIf { it.isNotEmpty() }?.let { Regex(it) },
                size = o.optString("size"),
                license = o.optString("license"),
                credit = o.optString("credit"),
                custom = o.optBoolean("custom"),
                built = o.optBoolean("built"),
                lang = o.optString("lang").takeIf { it.isNotEmpty() },
            )
        }
        cached = packs
        return packs
    }

    fun byId(context: Context, id: String) = catalog(context).firstOrNull { it.id == id }

    private const val PREF_PENDING = "lychee_pack_pending"

    /**
     * Starts downloading [pack]: its file opens in the browser, which saves it. Lychee
     * remembers the pack, so the file opened afterwards (or chosen) is known to be it.
     */
    fun download(context: Context, pack: Pack) {
        @Suppress("DEPRECATION")
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PREF_PENDING, pack.id).apply()
        openPage(context, pack.download.ifEmpty { pack.url })
    }

    /** The pack last downloaded and not yet installed, if any. */
    fun pending(context: Context): Pack? {
        @Suppress("DEPRECATION")
        val id = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_PENDING, null) ?: return null
        return byId(context, id)?.takeIf { !isInstalled(context, it.id) }
    }

    fun clearPending(context: Context) {
        @Suppress("DEPRECATION")
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().remove(PREF_PENDING).apply()
    }

    fun ofKind(context: Context, kind: PackKind) = catalog(context).filter { it.kind == kind }

    /** Opens [url] in a Custom Tab (or any browser); the browser does the downloading. */
    fun openPage(context: Context, url: String) {
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().apply {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.launchUrl(context, uri)
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ignored: ActivityNotFoundException) {
            }
        } catch (e: SecurityException) {
        }
    }

    // ------------------------------------------------------------------ installed packs

    fun root(context: Context) = File(context.filesDir, "packs")

    fun dir(context: Context, id: String) = File(root(context), id)

    fun isInstalled(context: Context, id: String) = File(dir(context, id), INFO).exists()

    /** The installed version, or null when the pack is not installed. */
    fun installedVersion(context: Context, id: String): String? =
        File(dir(context, id), INFO).takeIf { it.exists() }
            ?.let { runCatching { JSONObject(it.readText()).optString("version") }.getOrNull() }

    /** The pack's main file (a font's .ttf/.otf), when it has one. */
    fun mainFile(context: Context, id: String): File? =
        File(dir(context, id), INFO).takeIf { it.exists() }
            ?.let { runCatching { JSONObject(it.readText()).optString("main") }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { File(dir(context, id), it) }
            ?.takeIf { it.exists() }

    fun sizeOnDisk(context: Context, id: String): Long =
        dir(context, id).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun totalSize(context: Context): Long =
        root(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun remove(context: Context, id: String) {
        dir(context, id).deleteRecursively()
        changed(context)
    }

    /** Records an install; [main] is relative to the pack's folder. */
    internal fun writeInfo(context: Context, id: String, version: String, main: String?) {
        File(dir(context, id), INFO).writeText(
            JSONObject().put("id", id).put("version", version).put("main", main ?: "").toString(),
        )
        changed(context)
    }

    /** Bumps a preference so the running keyboard notices that packs changed. */
    private fun changed(context: Context) {
        @Suppress("DEPRECATION")
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putLong(PREF_CHANGED, System.currentTimeMillis()).apply()
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toFloat())
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toFloat())
        bytes >= 1L shl 10 -> "%d KB".format(bytes / (1L shl 10))
        else -> "$bytes B"
    }

    const val PREF_CHANGED = "lychee_packs_changed"
    private const val INFO = "installed.json"
}
