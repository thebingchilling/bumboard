package app.lychee.hwgoogle

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Lychee handwriting's one screen, plain and short: what the app is, each language's model and
 * whether it is ready (downloading any that aren't, and checking for newer ones), what the app
 * does and doesn't do with the internet, and a switch to hide its icon. Lychee's colours (the
 * logo's red), no Material library, for size. Lychee opens it from Settings > Add-ons too, so a
 * hidden icon is never a lost app.
 */
class SetupActivity : Activity() {
    private val dark get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // the Lychee logo's colours
    private val primary get() = if (dark) 0xFFFF6B7F.toInt() else 0xFFC8283F.toInt()
    private val surface get() = if (dark) 0xFF1C1214.toInt() else 0xFFFFF8F7.toInt()
    private val cardColor get() = if (dark) 0xFF2C1F22.toInt() else 0xFFFFFFFF.toInt()
    private val onSurface get() = if (dark) 0xFFF8E6E8.toInt() else 0xFF2B1619.toInt()
    private val onVariant get() = if (dark) 0xFFD2B9BD.toInt() else 0xFF6B4E53.toInt()
    private val ok get() = if (dark) 0xFF7BD796.toInt() else 0xFF2A8546.toInt()

    private val rows = HashMap<String, TextView>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(surface))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor = surface
            window.navigationBarColor = surface
            if (!dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(24))
        }

        // the app: its icon, its name, one line on what it is
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@SetupActivity).apply { setImageResource(R.mipmap.ic_launcher) },
                LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(LinearLayout(this@SetupActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
                addView(text(getString(R.string.app_name), 22f, onSurface, bold = true))
                addView(text(getString(R.string.tagline), 14f, onVariant))
            })
        })

        // the languages and their models
        root.addView(heading(getString(R.string.models)))
        root.addView(card {
            Models.TAGS.forEachIndexed { i, tag ->
                if (i > 0) addView(divider())
                addView(LinearLayout(this@SetupActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    addView(text(languageName(tag), 15f, onSurface), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(text(getString(R.string.checking), 13f, onVariant).also { rows[tag] = it })
                })
            }
        })
        root.addView(text(getString(R.string.models_note), 13f, onVariant).apply { setPadding(dp(4), dp(8), dp(4), 0) })

        // privacy, plainly
        root.addView(heading(getString(R.string.privacy)))
        root.addView(card {
            addView(text(getString(R.string.privacy_body), 14f, onSurface).apply {
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setLineSpacing(0f, 1.2f)
            })
        })

        // the icon: shown or hidden
        root.addView(heading(getString(R.string.app_icon)))
        root.addView(card {
            addView(LinearLayout(this@SetupActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(12), dp(10))
                addView(LinearLayout(this@SetupActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(getString(R.string.hide_icon), 15f, onSurface))
                    addView(text(getString(R.string.hide_icon_note), 13f, onVariant).apply { setPadding(0, dp(2), dp(8), 0) })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Switch(this@SetupActivity).apply {
                    isChecked = iconHidden()
                    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                    thumbTintList = ColorStateList(states, intArrayOf(primary, onVariant))
                    trackTintList = ColorStateList(states, intArrayOf(primary and 0x00FFFFFF or 0x66000000, onVariant and 0x00FFFFFF or 0x33000000))
                    setOnCheckedChangeListener { _, hide -> setIconHidden(hide) }
                })
            })
        })

        // done
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.END
            setPadding(0, dp(20), 0, 0)
            addView(TextView(this@SetupActivity).apply {
                text = getString(R.string.close)
                setTextColor(primary)
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                minHeight = dp(44)
                background = RippleDrawable(ColorStateList.valueOf(Color.argb(0x1F, Color.red(primary), Color.green(primary), Color.blue(primary))),
                    null, GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(Color.WHITE) })
                setOnClickListener { finishAndRemoveTask() }
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(ScrollView(this).apply { addView(root) })
        applyIconSetting()
        download()
    }

    private fun languageName(tag: String): String = getString(when (tag) {
        "zh-Hani-CN" -> R.string.lang_zh_cn
        "zh-Hani-HK" -> R.string.lang_zh_hk
        "zh-Hani-TW" -> R.string.lang_zh_tw
        "ja" -> R.string.lang_ja
        else -> R.string.lang_ko
    })

    /** Fetches each model not here yet (and a newer one where Google has it); each row says how it went. */
    private fun download() {
        Models.TAGS.forEach { tag ->
            rows[tag]?.text = getString(R.string.downloading)
            Models.fetch(tag) { ready ->
                runOnUiThread {
                    rows[tag]?.apply {
                        text = getString(if (ready) R.string.ready else R.string.failed)
                        setTextColor(if (ready) ok else onVariant)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the icon

    private val prefs get() = getSharedPreferences("lychee_handwriting", MODE_PRIVATE)

    private fun iconHidden() = prefs.getBoolean(PREF_HIDE_ICON, false)

    private fun setIconHidden(hide: Boolean) {
        prefs.edit().putBoolean(PREF_HIDE_ICON, hide).apply()
        applyIconSetting()
    }

    /** The launcher icon as chosen: shown (the default) or hidden. */
    private fun applyIconSetting() {
        runCatching {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, "$packageName.Launcher"),
                if (iconHidden()) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    // ------------------------------------------------------------------ views

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun heading(s: String) = text(s, 14f, primary, bold = true).apply { setPadding(dp(4), dp(24), 0, dp(8)) }

    private fun card(fill: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(cardColor) }
        fill()
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(onVariant and 0x00FFFFFF or 0x22000000)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { marginStart = dp(16) }
    }

    private companion object {
        const val PREF_HIDE_ICON = "hide_icon"
    }
}
