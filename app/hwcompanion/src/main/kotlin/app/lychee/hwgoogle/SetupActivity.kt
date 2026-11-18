package app.lychee.hwgoogle

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * The companion's one screen: it downloads ML Kit's handwriting models (Chinese, Japanese,
 * Korean; it checks for newer ones each time it is opened), then hides the app's launcher icon,
 * as there is nothing more to do here: Lychee uses it from then on. Lychee opens this screen
 * again from Settings > Add-ons.
 */
class SetupActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var done: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 24).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Google's handwriting recognition (ML Kit) for Lychee. ML Kit's licence needs it " +
                "to run in this small app of its own, which downloads Google's handwriting models " +
                "once; Lychee itself never goes online. What you write is read on this phone."
            textSize = 15f
            setPadding(0, pad / 2, 0, pad)
        })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = Models.TAGS.size
        }
        root.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        status = TextView(this).apply { textSize = 14f; setPadding(0, pad / 2, 0, pad) }
        root.addView(status)
        done = Button(this).apply {
            text = "Back to Lychee"
            isEnabled = false
            setOnClickListener { finish() }
        }
        root.addView(done)
        setContentView(root)
        download()
    }

    private fun download() {
        status.text = "Downloading the handwriting models (about 20 MB each)…"
        var finished = 0
        var ok = 0
        Models.TAGS.forEach { tag ->
            Models.fetch(tag) { ready ->
                runOnUiThread {
                    finished++
                    if (ready) ok++
                    progress.progress = finished
                    if (finished == Models.TAGS.size) showDone(ok)
                }
            }
        }
    }

    private fun showDone(ok: Int) {
        if (ok == 0) {
            status.text = "The models couldn't be downloaded. Check the connection, then open this again from Lychee (Settings > Add-ons)."
            done.text = "Close"
        } else {
            status.text = "Ready: Lychee's handwriting pad now uses Google's handwriting recognition. " +
                "This app keeps out of your way (its icon is hidden); Settings > Add-ons in Lychee " +
                "can open or remove it."
            // nothing more to do here: off the launcher
            packageManager.setComponentEnabledSetting(
                ComponentName(this, "$packageName.Launcher"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
            )
        }
        done.isEnabled = true
    }
}
