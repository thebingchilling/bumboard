package app.lychee.packs

import android.content.Context
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager
import org.json.JSONObject

/**
 * Pack updates, without network access: "Check for updates" opens Lychee's packs page in the
 * browser with the installed packs and their versions after the '#' (so they never leave the
 * phone). The page compares them with the newest versions, downloads what changed, and hands
 * the newest versions back through a lychee://updates link, which [record] keeps; the packs
 * then show an Update button until the newer version is installed. Only installed packs are
 * ever checked or updated.
 */
object Updates {
    /** The packs page (lychee-keyboard's GitHub Pages site; data/packs/site). */
    const val PAGE = "https://lychee-keyboard.github.io/"
    private const val PREF_LATEST = "lychee_pack_latest"
    private const val PREF_CHECKED = "lychee_pack_checked"
    private const val PREF_FILES = "lychee_pack_files"
    /** Where the packs' files are (lychee-keyboard/lychee's "packs" release). */
    private const val FILES = "https://github.com/lychee-keyboard/lychee/releases/download/packs/"
    /** A quiet reminder to check shows after this long without a check. */
    private const val REMIND_AFTER_MS = 14L * 24 * 60 * 60 * 1000

    @Suppress("DEPRECATION")
    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Every installed pack from the catalogue, those that come with Lychee included (your own
     * fonts have no updates): the packs page checks them all.
     */
    fun installed(context: Context): List<Pack> =
        Packs.catalog(context).filter { !it.custom && Packs.isInstalled(context, it.id) }

    /**
     * Opens the packs page with what is installed (versions after the '#'): it lists those, with
     * any updates, and every pack not installed with its download.
     */
    fun openAll(context: Context) = check(context, all = true)

    /**
     * The page's address for the installed packs; with [all], the page lists every add-on
     * (installed, not installed, with updates) and stays open. [accent] (an ARGB colour) lets the page match the app's colours.
     */
    fun pageUrl(context: Context, all: Boolean = false, accent: Int? = null): String {
        val have = installed(context).joinToString(",") { "${it.id}:${Packs.installedVersion(context, it.id).orEmpty()}" }
        val params = buildList {
            add("have=" + Uri.encode(have, ":,"))
            accent?.let { add("accent=" + "%06x".format(it and 0xFFFFFF)) }
            if (all) add("all=1")
            add("app=" + Uri.encode(context.packageName))
        }
        return PAGE + "#" + params.joinToString("&")
    }

    /** Opens the packs page; see [pageUrl]. */
    fun check(context: Context, all: Boolean = false) {
        Packs.openPage(context, pageUrl(context, all, accent(context)))
    }

    /** The phone's accent colour (Android 12+ wallpaper colours), for the page. */
    private fun accent(context: Context): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { context.getColor(android.R.color.system_accent1_600) }.getOrNull()
        } else null

    /**
     * Keeps the newest versions from a lychee://updates?latest=id:version,... link. Returns
     * whether the link was one.
     */
    fun record(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "lychee" || uri.host != "updates") return false
        // "Install downloaded updates" links carry no versions: keep the ones already known
        if (uri.getQueryParameter("latest") == null) return true
        val latest = pairs(context, uri.getQueryParameter("latest"))
        // the file each pack's newest version downloads as ("lychee-korean-dictionary-2026.11.18.2.zip")
        val files = pairs(context, uri.getQueryParameter("files"))
        prefs(context).edit()
            .putString(PREF_LATEST, latest.toString())
            .putString(PREF_FILES, files.toString())
            .putLong(PREF_CHECKED, System.currentTimeMillis())
            .apply()
        return true
    }

    /** id:value,... pairs of known packs (a value may hold anything but a comma). */
    private fun pairs(context: Context, list: String?): JSONObject {
        val out = JSONObject()
        list.orEmpty().split(',').forEach { item ->
            val id = item.substringBefore(':', "")
            val value = item.substringAfter(':', "")
            if (id.isNotEmpty() && value.isNotEmpty() && Packs.byId(context, id) != null) out.put(id, value)
        }
        return out
    }

    private fun stored(context: Context, key: String): JSONObject =
        runCatching { JSONObject(prefs(context).getString(key, null) ?: "{}") }.getOrDefault(JSONObject())

    private fun latest(context: Context): JSONObject = stored(context, PREF_LATEST)

    /** The newest version the packs page reported for [pack], if it was checked. */
    fun latestOf(context: Context, pack: Pack): String? = latest(context).optString(pack.id).takeIf { it.isNotEmpty() }

    /**
     * The file [pack] downloads as: its newest version's own name when the packs page gave it
     * ("lychee-korean-dictionary-2026.11.18.2.zip"), so it is told apart from earlier downloads.
     */
    fun downloadFile(context: Context, pack: Pack): String =
        stored(context, PREF_FILES).optString(pack.id).takeIf { it.isNotEmpty() && it.matches(SAFE_FILE) }
            // not checked yet: the packs page picks the newest, named the same way
            ?: if (pack.download.ifEmpty { pack.url }.startsWith(FILES)) {
                "lychee-" + pack.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') + "-<version>.zip"
            } else pack.file

    /**
     * Where [pack]'s file downloads from: always a file named with its version (see
     * [downloadFile]). Known from the last check, that file itself; otherwise the packs page,
     * which looks up the newest version and goes straight on to its file. Packs not published
     * on Lychee's release (fonts fetched from their makers) download from where they are.
     */
    fun downloadUrl(context: Context, pack: Pack): String {
        val file = stored(context, PREF_FILES).optString(pack.id).takeIf { it.isNotEmpty() && it.matches(SAFE_FILE) }
        if (file != null) return FILES + file
        val direct = pack.download.ifEmpty { pack.url }
        if (!direct.startsWith(FILES)) return direct
        return PAGE + "#get=" + Uri.encode(pack.id) + "&app=" + Uri.encode(context.packageName)
    }

    private val SAFE_FILE = Regex("lychee-[A-Za-z0-9.-]+\\.zip")

    /**
     * Compares two versions part by part ("2026.11.18.2" > "2026.11.18" > "2026.11.9"; numbers
     * as numbers): negative when [a] is older.
     */
    fun compare(a: String, b: String): Int {
        val x = a.split('.', '-')
        val y = b.split('.', '-')
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrNull(i) ?: return -1
            val q = y.getOrNull(i) ?: return 1
            val pn = p.toLongOrNull()
            val qn = q.toLongOrNull()
            val c = if (pn != null && qn != null) pn.compareTo(qn) else p.compareTo(q)
            if (c != 0) return c
        }
        return 0
    }

    /**
     * The newer version of an installed [pack], or null when it is up to date (or unknown). A
     * copy newer than the page's (one that came with an app update) has none.
     */
    fun newer(context: Context, pack: Pack): String? {
        if (pack.custom) return null
        val installed = Packs.installedVersion(context, pack.id) ?: return null
        val latest = latestOf(context, pack) ?: return null
        return latest.takeIf { compare(it, installed) > 0 }
    }

    fun hasUpdate(context: Context, pack: Pack) = newer(context, pack) != null

    /** Installed packs with a newer version. */
    fun available(context: Context): List<Pack> = installed(context).filter { hasUpdate(context, it) }

    /** Whether to remind the user to check: packs are installed and none was checked lately. */
    fun shouldRemind(context: Context): Boolean {
        if (installed(context).isEmpty()) return false
        val checked = prefs(context).getLong(PREF_CHECKED, 0L)
        if (checked == 0L) {
            // count from the first time this is asked, not from 1970
            prefs(context).edit().putLong(PREF_CHECKED, System.currentTimeMillis()).apply()
            return false
        }
        return System.currentTimeMillis() - checked > REMIND_AFTER_MS
    }
}
