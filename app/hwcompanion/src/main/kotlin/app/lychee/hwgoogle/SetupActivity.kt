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
        // Lychee's rose palette, as in Lychee's settings (not the wallpaper's colours)
        val primary = if (dark) 0xFFFFB2BC.toInt() else 0xFFA53A50.toInt()
        val surface = if (dark) 0xFF1A1112.toInt() else 0xFFFFF8F7.toInt()
        val onSurface = if (dark) 0xFFF0DEDF.toInt() else 0xFF22191A.toInt()
        val onVariant = if (dark) 0xFFD7C1C3.toInt() else 0xFF524345.toInt()
        val track = if (dark) 0xFF85233A.toInt() else 0xFFFFD9DD.toInt()

        // a full screen in the surface colour, its content in the middle
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(surface))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor = surface
            window.navigationBarColor = surface
            if (!dark) window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(onSurface)
            textSize = 24f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        })
        root.addView(TextView(this).apply {
            text = "Google's ML Kit handwriting recognition for Lychee's handwriting pad: Chinese, Japanese and " +
                "Korean. This app downloads Google's models (about 20 MB each); what you write is then read " +
                "on this phone, never sent anywhere."
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
        // the icon stays on the launcher (hiding it didn't work on every launcher): one that an
        // earlier version hid comes back
        runCatching {
            packageManager.setComponentEnabledSetting(
                android.content.ComponentName(this, "$packageName.Launcher"),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                android.content.pm.PackageManager.DONT_KILL_APP,
            )
        }
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
        status.text = "Ready: $ok of ${Models.TAGS.size} languages. Lychee's handwriting pad now uses " +
            "Google's ML Kit recognition, on this phone. Open this app any time to check for newer models."
    }
}
