package app.lychee.packs

import java.io.File
import java.io.RandomAccessFile

/**
 * Which ways an emoji font stores its pictures, read from its table directory (the first font
 * of a collection): Lychee draws CBDT (bitmaps, like Noto Color Emoji) and COLR (coloured
 * layers; version 1, with gradients, only from Android 13). Apple's sbix and SVG fonts are not
 * supported (yet).
 */
data class EmojiFontFormat(
    val cbdt: Boolean,
    /** the COLR table's version (0 or 1), or null when there is none */
    val colr: Int?,
    val sbix: Boolean,
    val svg: Boolean,
) {
    /** Whether it has pictures Lychee can draw on an Android version [sdk]. */
    fun supportedOn(sdk: Int): Boolean = cbdt || colr == 0 || (colr != null && sdk >= 33)

    companion object {
        /** Null when [file] isn't a font Lychee can read. */
        fun of(file: File): EmojiFontFormat? = runCatching {
            RandomAccessFile(file, "r").use { f ->
                var base = 0L
                val tag = f.readInt()
                if (tag == TTCF) {
                    // a collection: its first font
                    f.seek(12)
                    base = f.readInt().toLong() and 0xFFFFFFFFL
                    f.seek(base)
                    f.readInt()
                }
                val tables = f.readUnsignedShort()
                var cbdt = false; var sbix = false; var svg = false
                var colrAt = -1L
                for (i in 0 until tables) {
                    f.seek(base + 12 + i * 16L)
                    val bytes = ByteArray(4).also { f.readFully(it) }
                    f.readInt() // checksum
                    val offset = f.readInt().toLong() and 0xFFFFFFFFL
                    when (String(bytes, Charsets.US_ASCII)) {
                        "CBDT" -> cbdt = true
                        "sbix" -> sbix = true
                        "SVG " -> svg = true
                        "COLR" -> colrAt = offset
                    }
                }
                val colr = if (colrAt >= 0) { f.seek(colrAt); f.readUnsignedShort() } else null
                EmojiFontFormat(cbdt, colr, sbix, svg)
            }
        }.getOrNull()

        private const val TTCF = 0x74746366 // "ttcf"
    }
}
