package app.lychee.hwgoogle

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * A small Material 3 dialog (the phone's Material You colours, a 28 dp surface, a linear
 * progress bar, a text button), opened from Lychee (Settings > Add-ons > Models): it fetches
 * any of ML Kit's handwriting models not downloaded yet and checks for newer ones, then closes
 * itself. It never stays in recents (the manifest), and nothing needs it to be opened: the
 * models also come by themselves when Lychee first uses the companion.
 */
class SetupActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var close: TextView

    private val dark get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Material You's colour for [res] on Android 12+, else Lychee's teal palette's [fallback]. */
    private fun tone(res: Int, fallback: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getColor(res) else fallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val primary = if (dark) tone(android.R.color.system_accent1_200, 0xFF82D5C3.toInt())
            else tone(android.R.color.system_accent1_600, 0xFF006A5D.toInt())
        val surface = if (dark) tone(android.R.color.system_neutral1_800, 0xFF1B211F.toInt())
            else tone(android.R.color.system_neutral1_50, 0xFFF0F5F2.toInt())
        val onSurface = if (dark) tone(android.R.color.system_neutral1_100, 0xFFDEE4E1.toInt())
            else tone(android.R.color.system_neutral1_900, 0xFF171D1B.toInt())
        val onVariant = if (dark) tone(android.R.color.system_neutral2_200, 0xFFBEC9C5.toInt())
            else tone(android.R.color.system_neutral2_700, 0xFF3F4946.toInt())
        val track = if (dark) tone(android.R.color.system_accent1_800, 0xFF005045.toInt())
            else tone(android.R.color.system_accent1_100, 0xFFCCE8E0.toInt())

        // the dialog's surface: Material 3's extra-large corners
        window.setBackgroundDrawable(GradientDrawable().apply {
            cornerRadius = dp(28).toFloat()
            setColor(surface)
        })
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(16))
            minimumWidth = dp(300)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(onSurface)
            textSize = 24f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        })
        root.addView(TextView(this).apply {
            text = "Getting Google's handwriting models for Chinese, Japanese and Korean (about 20 MB each). " +
                "What you write is read on this phone."
            setTextColor(onVariant)
            textSize = 14f
            setLineSpacing(0f, 1.2f)
            setPadding(0, dp(16), 0, dp(20))
        })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = Models.TAGS.size
            progressTintList = ColorStateList.valueOf(primary)
            progressBackgroundTintList = ColorStateList.valueOf(track)
        }
        root.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)))
        status = TextView(this).apply {
            setTextColor(onVariant)
            textSize = 14f
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(status)
        // a Material 3 text button at the end
        close = TextView(this).apply {
            text = "Close"
            setTextColor(primary)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(40)
            background = RippleDrawable(ColorStateList.valueOf(Color.argb(0x1F, Color.red(primary), Color.green(primary), Color.blue(primary))),
                null, GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.WHITE) })
            setOnClickListener { finishAndRemoveTask() }
        }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.END
            setPadding(0, dp(20), 0, 0)
            addView(close)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        download()
    }

    private fun download() {
        status.text = "Downloading…"
        var finished = 0
        var ok = 0
        Models.TAGS.forEach { tag ->
            Models.fetch(tag) { ready ->
                runOnUiThread {
                    finished++
                    if (ready) ok++
                    progress.progress = finished
                    if (finished == Models.TAGS.size) done(ok)
                }
            }
        }
    }

    private fun done(ok: Int) {
        if (ok == 0) {
            status.text = "The models couldn't be downloaded. Check the connection and try again."
            return
        }
        status.text = "Ready. Lychee's handwriting pad now uses Google's handwriting recognition."
        // nothing more to do here: it goes by itself
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finishAndRemoveTask() }, 1500)
    }
}
