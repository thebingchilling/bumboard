package app.lychee.chinese

import android.content.Context
import java.io.File

/**
 * The other written forms of a Traditional character (為 and 爲, 裏 and 裡, 衛 and 衞), from
 * the table Rime's variants filter uses (rime/shared/opencc/lychee_variants.txt, made by
 * build_data.py): typing gets them from Rime itself, handwriting from here.
 */
object Variants {
    @Volatile
    private var table: Map<String, List<String>>? = null

    private fun load(context: Context): Map<String, List<String>> {
        table?.let { return it }
        val f = File(context.applicationContext.filesDir, "rime/shared/opencc/lychee_variants.txt")
        val map = HashMap<String, List<String>>()
        runCatching {
            f.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) map[line.substring(0, tab)] = line.substring(tab + 1).split(' ').filter { it.isNotEmpty() }
            }
        }
        // empty until Rime's data is installed: read again next time
        if (map.isNotEmpty()) table = map
        return map
    }

    /** [list] with each character's other forms right after it (no repeats). */
    fun expand(context: Context, list: List<String>): List<String> {
        val t = load(context)
        if (t.isEmpty()) return list
        val out = LinkedHashSet<String>()
        for (s in list) {
            out += s
            t[s]?.let { out += it }
        }
        return out.toList()
    }
}
