package app.lychee.ui

import android.content.Context
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * When the keyboard goes dark on a schedule (Settings > Theme > Dark mode): between two set
 * times, or from sunset to sunrise. Lychee can't use the internet or ask for the phone's
 * location, so the sun is worked out for where the phone's time zone is (its place in the
 * public time zone database, assets/lychee/zones.tsv), close enough for a keyboard's colours.
 */
object DarkSchedule {
    /** Minutes after midnight, now. */
    fun nowMinutes(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    /** Whether [now] is in the dark stretch from [from] to [to] (minutes; it may pass midnight). */
    fun between(now: Int, from: Int, to: Int): Boolean =
        if (from == to) false else if (from < to) now in from until to else now >= from || now < to

    /** Today's sunrise and sunset here (minutes after midnight, local time); null in polar day or night. */
    fun sunTimes(context: Context, day: Calendar = Calendar.getInstance()): Pair<Int, Int>? {
        val (lat, lon) = place(context)
        val zoneOffset = day.timeZone.getOffset(day.timeInMillis) / 60000
        return sunTimes(lat, lon, day.get(Calendar.DAY_OF_YEAR), zoneOffset)
    }

    /** Whether it is between sunset and sunrise here now (dark all day in polar night). */
    fun sunDown(context: Context): Boolean {
        val (lat, lon) = place(context)
        val day = Calendar.getInstance()
        val zoneOffset = day.timeZone.getOffset(day.timeInMillis) / 60000
        val times = sunTimes(lat, lon, day.get(Calendar.DAY_OF_YEAR), zoneOffset)
            ?: return polarNight(lat, day.get(Calendar.DAY_OF_YEAR))
        return between(nowMinutes(), times.second, times.first)
    }

    /**
     * Sunrise and sunset (local minutes) by NOAA's equations, at [lat], [lon] on day [n] of the
     * year, [offset] minutes from UTC; null when the sun doesn't rise or set that day.
     */
    fun sunTimes(lat: Double, lon: Double, n: Int, offset: Int): Pair<Int, Int>? {
        val g = 2 * PI / 365 * (n - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
            0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val phi = Math.toRadians(lat)
        val arg = cos(Math.toRadians(90.833)) / (cos(phi) * cos(decl)) - tan(phi) * tan(decl)
        if (arg > 1 || arg < -1) return null
        val ha = Math.toDegrees(acos(arg))
        fun local(utc: Double) = (((utc + offset).toInt() % 1440) + 1440) % 1440
        return local(720 - 4 * (lon + ha) - eqTime) to local(720 - 4 * (lon - ha) - eqTime)
    }

    /** No sunrise today: night if it is winter on that side of the equator. */
    private fun polarNight(lat: Double, n: Int): Boolean {
        val northernSummer = n in 80..266
        return if (lat >= 0) !northernSummer else northernSummer
    }

    @Volatile
    private var zones: Map<String, Pair<Double, Double>>? = null

    /** Where the phone's time zone is: its city's place, or a guess from the hour it keeps. */
    fun place(context: Context): Pair<Double, Double> {
        val tz = TimeZone.getDefault()
        val map = zones ?: runCatching {
            context.assets.open("lychee/zones.tsv").bufferedReader().useLines { lines ->
                lines.filter { !it.startsWith("#") && it.isNotBlank() }.mapNotNull { line ->
                    val p = line.split('\t')
                    if (p.size < 3) null else p[0] to (p[1].toDouble() to p[2].toDouble())
                }.toMap()
            }
        }.getOrDefault(emptyMap()).also { zones = it }
        return map[tz.id] ?: (35.0 to tz.rawOffset / 3_600_000.0 * 15.0)
    }
}
