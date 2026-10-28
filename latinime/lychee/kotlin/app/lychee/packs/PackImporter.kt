package app.lychee.packs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Copies a file the user picked into Lychee's own storage as a pack, after checking it:
 * a pinned hash when the catalogue has one, the pack.json of packs Lychee builds, and for
 * fonts that the phone can actually draw them. Runs on a background thread.
 */
object PackImporter {

    sealed class Result {
        data class Ok(val pack: Pack) : Result()
        data class Error(val message: String) : Result()
    }

    /** Files bigger than this are refused outright. */
    private const val MAX_BYTES = 700L shl 20

    /**
     * Imports [uri] as [expected] (the pack whose Import button was tapped), or, when null,
     * works out which pack it is from its hash or its pack.json.
     */
    fun import(context: Context, uri: Uri, expected: Pack?): Result {
        val staging = File(context.cacheDir, "pack-import").apply { deleteRecursively(); mkdirs() }
        try {
            val name = displayName(context, uri) ?: "file"
            val download = File(staging, "download")
            val sha = context.contentResolver.openInputStream(uri)?.use { copyHashing(it, download) }
                ?: return Result.Error("Couldn't open that file.")
            val zip = isZip(download)
            val info = if (zip) readPackJson(download) else null
            val catalog = Packs.catalog(context)
            val pack = expected
                ?: catalog.firstOrNull { it.sha256 == sha }
                ?: info?.optString("id")?.let { id -> catalog.firstOrNull { it.id == id } }
                // the pack whose Download was tapped last
                ?: Packs.pending(context)
                ?: return Result.Error("Lychee doesn't know this file. To use your own font, choose \"Your own font\" instead.")
            if (pack.sha256 != null && pack.sha256 != sha) {
                return Result.Error("This isn't the file for ${pack.name} (it doesn't match). Download it again from its page.")
            }
            if (info != null && info.optString("id") != pack.id) {
                return Result.Error("This pack is for ${info.optString("name", info.optString("id"))}, not ${pack.name}.")
            }
            if (pack.built && info == null) {
                return Result.Error("This isn't a Lychee pack. Download ${pack.file} from the packs page.")
            }
            val out = File(staging, "pack").apply { mkdirs() }
            val main: String? = when {
                pack.kind.isFont -> installFont(pack, download, name, zip, out) ?: return Result.Error(fontProblem)
                zip -> {
                    unzip(download, out)
                    null
                }
                else -> return Result.Error("${pack.name} comes as a .zip file; this isn't one.")
            }
            if (pack.kind.isFont) {
                val file = File(out, main!!)
                val problem = checkFont(file, pack.kind)
                if (problem != null) return Result.Error(problem)
            }
            val version = info?.optString("version")?.takeIf { it.isNotEmpty() }
                ?: pack.version.takeIf { it.isNotEmpty() && it != "latest" }
                ?: name
            // swap in the new copy
            val dest = Packs.dir(context, pack.id)
            dest.deleteRecursively()
            dest.parentFile?.mkdirs()
            if (!out.renameTo(dest)) {
                out.copyRecursively(dest, overwrite = true)
            }
            Packs.writeInfo(context, pack.id, version, main)
            return Result.Ok(pack)
        } catch (e: TooBig) {
            return Result.Error("That file is too big.")
        } catch (e: Exception) {
            return Result.Error("Couldn't import that file (${e.javaClass.simpleName}).")
        } finally {
            staging.deleteRecursively()
        }
    }

    private var fontProblem = ""

    /** Puts the font into [out] (from the zip when it is one); returns its file name. */
    private fun installFont(pack: Pack, download: File, name: String, zip: Boolean, out: File): String? {
        if (!zip) {
            val ext = name.substringAfterLast('.', "ttf").lowercase().takeIf { it in FONT_EXTENSIONS } ?: "ttf"
            val file = "font.$ext"
            download.copyTo(File(out, file), overwrite = true)
            return file
        }
        ZipFile(download).use { z ->
            val fonts = z.entries().toList().filter { e ->
                !e.isDirectory && e.name.substringAfterLast('.').lowercase() in FONT_EXTENSIONS &&
                    (pack.pick?.containsMatchIn(e.name) ?: true) &&
                    !e.name.contains("italic", ignoreCase = true) && !e.name.startsWith("__MACOSX")
            }
            val chosen = fonts.firstOrNull { pack.prefer?.containsMatchIn(it.name) == true }
                ?: fonts.firstOrNull { it.name.contains("regular", ignoreCase = true) }
                ?: fonts.minByOrNull { it.name.length }
            if (chosen == null) {
                fontProblem = "There's no matching font in that zip."
                return null
            }
            if (chosen.size > MAX_BYTES) throw TooBig()
            val ext = chosen.name.substringAfterLast('.').lowercase()
            val file = "font.$ext"
            z.getInputStream(chosen).use { input -> File(out, file).outputStream().use { input.copyTo(it) } }
            return file
        }
    }

    /** Null when [file] is a font this phone can draw for [kind]; otherwise what is wrong. */
    fun checkFont(file: File, kind: PackKind): String? {
        val typeface = loadFont(file) ?: return "That font couldn't be opened on this phone."
        val paint = Paint().apply { this.typeface = typeface }
        return when (kind) {
            PackKind.FONT_ZH ->
                if (SAMPLE_ZH.all { paint.hasGlyph(it.toString()) }) null else "That font has no Chinese characters."
            PackKind.FONT_EMOJI -> if (drawsInColour(typeface)) null
                else "This phone can't show that emoji font (it may need a newer Android version)."
            else -> null
        }
    }

    fun loadFont(file: File): Typeface? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Typeface.Builder(file).build()
        } else {
            Typeface.createFromFile(file)
        }
    }.getOrNull()?.takeIf { it != Typeface.DEFAULT }

    /** Draws an emoji and checks that coloured pixels come out (a bitmap or COLR font). */
    private fun drawsInColour(typeface: Typeface): Boolean {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size * 0.8f
            color = Color.BLACK
        }
        if (!paint.hasGlyph(SAMPLE_EMOJI)) return false
        Canvas(bitmap).drawText(SAMPLE_EMOJI, 2f, size * 0.8f, paint)
        var coloured = 0
        for (y in 0 until size step 2) for (x in 0 until size step 2) {
            val p = bitmap.getPixel(x, y)
            if (Color.alpha(p) > 128 && (Color.red(p) != Color.green(p) || Color.green(p) != Color.blue(p))) coloured++
        }
        bitmap.recycle()
        return coloured > 20
    }

    private fun readPackJson(file: File): JSONObject? = runCatching {
        ZipFile(file).use { z ->
            val entry = z.getEntry("pack.json") ?: return@use null
            z.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()) }
        }
    }.getOrNull()

    /** Extracts [file] into [out], refusing entries that would land outside it. */
    private fun unzip(file: File, out: File) {
        val root = out.canonicalPath + File.separator
        var total = 0L
        ZipFile(file).use { z ->
            for (e in z.entries()) {
                val target = File(out, e.name)
                if (!target.canonicalPath.startsWith(root)) continue
                if (e.isDirectory) {
                    target.mkdirs()
                    continue
                }
                total += e.size.coerceAtLeast(0)
                if (total > MAX_BYTES * 2) throw TooBig()
                target.parentFile?.mkdirs()
                z.getInputStream(e).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    private fun isZip(file: File): Boolean = file.inputStream().use { s ->
        val head = ByteArray(4)
        s.read(head) == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            head[2] == 3.toByte() && head[3] == 4.toByte()
    }

    private fun copyHashing(input: InputStream, to: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        to.outputStream().use { out ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw TooBig()
                digest.update(buffer, 0, n)
                out.write(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private class TooBig : Exception()

    private val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc", "otc")
    private const val SAMPLE_ZH = "永你好"
    private const val SAMPLE_EMOJI = "😀"
}
