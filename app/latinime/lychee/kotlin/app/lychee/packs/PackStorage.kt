package app.lychee.packs

import android.content.Context
import android.preference.PreferenceManager

/**
 * Settings > Storage: what Lychee keeps on the phone, by language and kind, so a whole language
 * can be removed to save space and brought back later.
 *
 * Downloaded packs (fonts, emoji, voices) are deleted and can be downloaded again from Add-ons.
 * Packs that come with Lychee (dictionaries, stroke order, examples) are unpacked from the app
 * itself: removing one deletes the unpacked copy and keeps it out ([purged]) until it is
 * restored, which unpacks it again, no download needed.
 */
object PackStorage {
    private const val PREF_PURGED = "lychee_packs_purged"

    /** A group on the Storage page; [order] sorts the groups. */
    enum class Area(val order: Int) {
        CHINESE(0), JAPANESE(1), KOREAN(2), ENGLISH(3), SHARED(4), EMOJI(5), KEYBOARD(6), VOICE(7), OTHER(8)
    }

    /** What a pack is, in a word (shown beside its size). */
    enum class Kind { FONT, EMOJI, DICTIONARY, STROKES, EXAMPLES, HANDWRITING, VOICE, DATA }

    fun area(pack: Pack): Area = when {
        pack.kind == PackKind.FONT_ZH -> Area.CHINESE
        pack.kind == PackKind.FONT_JA || pack.id == "rime_ja" -> Area.JAPANESE
        pack.kind == PackKind.FONT_KO || pack.id == "dict_ko" -> Area.KOREAN
        pack.id == "dict_en" -> Area.ENGLISH
        pack.id == "strokes_zh" || pack.id == "examples" -> Area.SHARED
        pack.kind == PackKind.FONT_EMOJI -> Area.EMOJI
        pack.kind == PackKind.FONT_KEYS -> Area.KEYBOARD
        pack.kind == PackKind.VOICE -> Area.VOICE
        else -> Area.OTHER
    }

    fun kind(pack: Pack): Kind = when {
        pack.kind == PackKind.FONT_EMOJI -> Kind.EMOJI
        pack.kind.isFont -> Kind.FONT
        pack.kind == PackKind.RIME -> Kind.DICTIONARY
        pack.id.startsWith("dict_") -> Kind.DICTIONARY
        pack.id.startsWith("strokes") -> Kind.STROKES
        pack.id == "examples" -> Kind.EXAMPLES
        pack.kind == PackKind.HANDWRITING -> Kind.HANDWRITING
        pack.kind == PackKind.VOICE -> Kind.VOICE
        else -> Kind.DATA
    }

    /** One line of the Storage page. */
    class Entry(val pack: Pack, val bundled: Boolean, val installed: Boolean, val bytes: Long)

    /** Everything stored (and the packs that come with Lychee but were removed), by area. */
    fun entries(context: Context): Map<Area, List<Entry>> {
        val bundled = BundledPacks.versions(context).keys
        val purged = purged(context)
        return Packs.catalog(context).mapNotNull { pack ->
            val installed = Packs.isInstalled(context, pack.id)
            val isBundled = pack.id in bundled
            if (!installed && !(isBundled && pack.id in purged)) return@mapNotNull null
            Entry(pack, isBundled, installed, if (installed) Packs.sizeOnDisk(context, pack.id) else 0L)
        }.groupBy { area(it.pack) }.toSortedMap(compareBy { it.order })
    }

    @Suppress("DEPRECATION")
    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /** The packs that come with Lychee but were removed here. */
    fun purged(context: Context): Set<String> =
        prefs(context).getString(PREF_PURGED, null).orEmpty().split(',').filter { it.isNotEmpty() }.toSet()

    /** Removes [pack]: a download is deleted, a pack that comes with Lychee stays out until restored. */
    fun remove(context: Context, pack: Pack) {
        if (BundledPacks.isBundled(context, pack.id)) {
            prefs(context).edit().putString(PREF_PURGED, (purged(context) + pack.id).joinToString(",")).apply()
        }
        Packs.remove(context, pack.id)
    }

    /** Brings back a pack that comes with Lychee (unpacked from the app). Blocking: off the main thread. */
    fun restore(context: Context, pack: Pack) {
        prefs(context).edit().putString(PREF_PURGED, (purged(context) - pack.id).joinToString(",")).commit()
        BundledPacks.installAll(context)
    }
}
