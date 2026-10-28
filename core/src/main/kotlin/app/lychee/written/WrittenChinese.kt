package app.lychee.written

/**
 * Turns colloquial written Cantonese into Standard Written Chinese (書面語): 佢哋喺度食緊飯 →
 * 他們在這裡正在吃飯. A phrase table applied longest match first, so words that contain a
 * colloquial character but are standard (關係, 一點, 同學) stay as they are because the table
 * maps them to themselves. No model: a few thousand entries, instant, almost no memory.
 *
 * Table lines: `phrase<TAB>replacement`; `#` starts a comment.
 */
class WrittenChinese(private val table: Map<String, String>) {
    private val longest = table.keys.maxOfOrNull { it.length } ?: 0

    fun convert(text: CharSequence): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            var matched = false
            var len = minOf(longest, text.length - i)
            while (len > 0) {
                val replacement = table[text.substring(i, i + len)]
                if (replacement != null) {
                    out.append(replacement)
                    i += len
                    matched = true
                    break
                }
                len--
            }
            if (!matched) {
                out.append(text[i])
                i++
            }
        }
        return out.toString()
    }

    companion object {
        fun parse(lines: Sequence<String>): WrittenChinese {
            val table = HashMap<String, String>()
            for (raw in lines) {
                // a replacement may be empty (嚟㗎 -> ""), so keep the tab
                val line = raw.substringBefore('#').trimEnd('\r', '\n')
                if (line.isBlank()) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val from = line.substring(0, tab).trim()
                val to = line.substring(tab + 1).trim()
                if (from.isNotEmpty()) table.putIfAbsent(from, to)
            }
            return WrittenChinese(table)
        }
    }
}
