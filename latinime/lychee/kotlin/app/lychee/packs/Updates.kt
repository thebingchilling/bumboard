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
    /** The packs page (GitHub Pages of the public lychee repository; data/packs/site). */
    const val PAGE = "https://lychee-keyboard.github.io/lychee/"
    private const val PREF_LATEST = "lychee_pack_latest"
    private const val PREF_CHECKED = "lychee_pack_checked"
    /** A quiet reminder to check shows after this long without a check. */
    private const val REMIND_AFTER_MS = 14L * 24 * 60 * 60 * 1000

    @Suppress("DEPRECATION")
    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    /** Installed packs that came from the catalogue (your own fonts have no updates). */
    fun installed(context: Context): List<Pack> =
        Packs.catalog(context).filter { !it.custom && Packs.isInstalled(context, it.id) }

    /**
     * The page's address for the installed packs; with [all], the page starts downloading every
     * update at once. [accent] (an ARGB colour) lets the page match the app's colours.
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
        val latest = JSONObject()
        uri.getQueryParameter("latest").orEmpty().split(',').forEach { item ->
            val id = item.substringBefore(':', "")
            val version = item.substringAfter(':', "")
            if (id.isNotEmpty() && version.isNotEmpty() && Packs.byId(context, id) != null) latest.put(id, version)
        }
        prefs(context).edit()
            .putString(PREF_LATEST, latest.toString())
            .putLong(PREF_CHECKED, System.currentTimeMillis())
            .apply()
        return true
    }

    private fun latest(context: Context): JSONObject =
        runCatching { JSONObject(prefs(context).getString(PREF_LATEST, null) ?: "{}") }.getOrDefault(JSONObject())

    /** The newer version of an installed [pack], or null when it is up to date (or unknown). */
    fun newer(context: Context, pack: Pack): String? {
        if (pack.custom) return null
        val installed = Packs.installedVersion(context, pack.id) ?: return null
        val latest = latest(context).optString(pack.id).takeIf { it.isNotEmpty() } ?: return null
        return latest.takeIf { it != installed }
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
