package app.lychee.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * What was copied, for the clipboard panel. Off until turned on (Settings > Preferences >
 * Clipboard); then the last [MAX_RECENT] copies are kept on the phone, in Lychee's own storage
 * (never sent anywhere). Unpinned ones go after an hour; pinned ones stay until deleted.
 * Nothing is kept that the app marks as sensitive (passwords from a password manager); incognito
 * doesn't change it, as copies are made in other apps.
 */
object ClipboardStore {
    const val PREF_ENABLED = "lychee_clipboard"
    private const val FILE = "lychee_clipboard"
    private const val KEY = "items"
    private const val MAX_RECENT = 20
    private const val MAX_PINNED = 50
    private const val MAX_LENGTH = 5000
    private const val EXPIRY_MS = 60 * 60 * 1000L
    /** ClipDescription.EXTRA_IS_SENSITIVE (Android 13); older apps use the same key. */
    private const val EXTRA_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    data class Item(val text: String, val time: Long, val pinned: Boolean)

    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    @Suppress("DEPRECATION")
    fun enabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_ENABLED, false)

    @Suppress("DEPRECATION")
    fun setEnabled(context: Context, on: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREF_ENABLED, on).apply()
        if (!on) clear(context, keepPinned = false)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Starts noticing copies (Android tells the keyboard while it is in use). */
    @JvmStatic
    fun watch(context: Context) {
        if (listener != null) return
        val app = context.applicationContext
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return
        val l = ClipboardManager.OnPrimaryClipChangedListener { capture(app) }
        listener = l
        runCatching { cm.addPrimaryClipChangedListener(l) }
    }

    /** Keeps what is on the clipboard now, unless it shouldn't be kept. */
    @JvmStatic
    fun capture(context: Context) {
        if (!enabled(context)) return
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return
        if (clip.description?.extras?.getBoolean(EXTRA_SENSITIVE, false) == true) return
        if (clip.itemCount == 0) return
        val text = runCatching { clip.getItemAt(0).coerceToText(context)?.toString() }.getOrNull()
            ?.trim()?.take(MAX_LENGTH) ?: return
        if (text.isEmpty()) return
        add(context, text)
    }

    @Synchronized
    fun add(context: Context, text: String) {
        val list = items(context).toMutableList()
        val old = list.firstOrNull { it.text == text }
        list.removeAll { it.text == text }
        list.add(0, Item(text, System.currentTimeMillis(), old?.pinned == true))
        save(context, list)
    }

    /** The copies, newest first: pinned ones, then the unpinned ones less than an hour old. */
    @Synchronized
    fun items(context: Context): List<Item> {
        val now = System.currentTimeMillis()
        val raw = runCatching { JSONArray(prefs(context).getString(KEY, "[]")) }.getOrDefault(JSONArray())
        val all = (0 until raw.length()).mapNotNull { i ->
            raw.optJSONObject(i)?.let { Item(it.optString("t"), it.optLong("at"), it.optBoolean("pin")) }
        }.filter { it.text.isNotEmpty() }
        val kept = all.filter { it.pinned || now - it.time < EXPIRY_MS }
        if (kept.size != all.size) save(context, kept)
        return kept
    }

    @Synchronized
    fun setPinned(context: Context, text: String, pinned: Boolean) =
        save(context, items(context).map { if (it.text == text) it.copy(pinned = pinned) else it })

    @Synchronized
    fun delete(context: Context, text: String) = save(context, items(context).filter { it.text != text })

    @Synchronized
    fun clear(context: Context, keepPinned: Boolean = true) =
        save(context, if (keepPinned) items(context).filter { it.pinned } else emptyList())

    private fun save(context: Context, list: List<Item>) {
        val pinned = list.filter { it.pinned }.take(MAX_PINNED)
        val recent = list.filter { !it.pinned }.take(MAX_RECENT)
        val ordered = list.filter { it in pinned || it in recent }
        val arr = JSONArray()
        ordered.forEach { arr.put(JSONObject().put("t", it.text).put("at", it.time).put("pin", it.pinned)) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }
}
