package app.lychee.ui

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.preference.PreferenceManager
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Key sounds, chosen in Settings: the phone's own key click, or one of Lychee's (soft, click,
 * typewriter, bubble). Lychee's are synthesised once into small WAV files in the cache and
 * played with a SoundPool; space, Enter and delete sound a little different from letters.
 */
object KeySounds {
    const val PREF = "lychee_key_sound"
    const val SYSTEM = "system"
    /** Lychee's own soft click by default: the phone's is silent when its touch sounds are off. */
    const val DEFAULT = "soft"
    val STYLES = listOf(SYSTEM, "soft", "click", "typewriter", "bubble")

    private const val RATE = 44100
    private val worker = Executors.newSingleThreadExecutor()
    private var pool: SoundPool? = null
    private var style = DEFAULT
    /** sound ids by kind: 0 letter, 1 space, 2 enter, 3 delete */
    private val ids = IntArray(4)
    private var app: Context? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == PREF) load(p.getString(PREF, DEFAULT) ?: DEFAULT)
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        load(prefs.getString(PREF, DEFAULT) ?: DEFAULT)
    }

    private fun load(newStyle: String) {
        val context = app ?: return
        style = newStyle
        worker.execute {
            pool?.release()
            pool = null
            if (newStyle == SYSTEM) return@execute
            val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            ).build()
            val dir = File(context.cacheDir, "keysounds").apply { mkdirs() }
            for (kind in 0..3) {
                val f = File(dir, "$newStyle-$kind.wav")
                if (!f.exists()) writeWav(f, synth(newStyle, kind))
                ids[kind] = p.load(f.path, 1)
            }
            pool = p
        }
    }

    /**
     * Plays Lychee's sound for a key ([kind] 0 letter, 1 space, 2 enter, 3 delete) at [volume]
     * (0..1, or below 0 for the default). False when the phone's own sound should play.
     */
    @JvmStatic
    fun play(kind: Int, volume: Float): Boolean {
        if (style == SYSTEM) return false
        val p = pool ?: return true
        val v = if (volume < 0) 0.6f else volume.coerceIn(0f, 1f)
        p.play(ids[kind.coerceIn(0, 3)], v, v, 1, 0, 1f)
        return true
    }

    /** One short sound as 16-bit samples. */
    private fun synth(style: String, kind: Int): ShortArray {
        val pitch = when (kind) { 1 -> 0.8; 2 -> 0.7; 3 -> 1.15; else -> 1.0 }
        val ms = when (style) { "typewriter" -> 45; "bubble" -> 50; else -> 25 }
        val n = RATE * ms / 1000
        val random = java.util.Random(7L + kind)
        var lp = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val noise = random.nextDouble() * 2 - 1
            val s = when (style) {
                "soft" -> {
                    lp += (noise - lp) * 0.18
                    lp * 1.6 * exp(-t / 0.004)
                }
                "click" -> 0.5 * noise * exp(-t / 0.0012) + 0.5 * sin(2 * PI * 2600 * pitch * t) * exp(-t / 0.0025)
                "typewriter" -> {
                    lp += (noise - lp) * 0.35
                    0.7 * lp * exp(-t / 0.003) + 0.6 * sin(2 * PI * 140 * pitch * t) * exp(-t / 0.012) +
                        (if (t > 0.006) 0.25 * noise * exp(-(t - 0.006) / 0.002) else 0.0)
                }
                else -> { // bubble: a rising blip
                    val f = (380 + 9000 * t) * pitch
                    0.8 * sin(2 * PI * f * t) * exp(-t / 0.012) * (1 - exp(-t / 0.001))
                }
            }
            (s.coerceIn(-1.0, 1.0) * 0.8 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun writeWav(file: File, samples: ShortArray) {
        val tmp = File(file.path + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            fun le32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            val data = samples.size * 2
            out.writeBytes("RIFF"); le32(36 + data); out.writeBytes("WAVE")
            out.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(RATE); le32(RATE * 2); le16(2); le16(16)
            out.writeBytes("data"); le32(data)
            samples.forEach { le16(it.toInt()) }
        }
        tmp.renameTo(file)
    }
}
