package app.lychee.hwgoogle

import android.app.Activity
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
 * Lychee handwriting's one screen, plain and short: what the app is, each language's model (ready,
 * downloading or not on the phone; whether Lychee's keyboards use it; Download or Remove), what
 * the app does and doesn't do with the internet, and a switch to hide its icon. Lychee's colours (the
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

    /** Each language's status line and its button. */
    private val rows = HashMap<String, Pair<TextView, TextView>>()

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

        // the languages and their models: only those your keyboards use are kept
        root.addView(heading(getString(R.string.models)))
        root.addView(card {
            Models.TAGS.forEachIndexed { i, tag ->
                if (i > 0) addView(divider())
                addView(LinearLayout(this@SetupActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(10), dp(8), dp(10))
                    val status = text(getString(R.string.checking), 13f, onVariant)
                    addView(LinearLayout(this@SetupActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(languageName(tag), 15f, onSurface))
                        addView(status)
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    val button = pill("").apply { visibility = View.INVISIBLE }
                    addView(button)
                    rows[tag] = status to button
                })
            }
        })
        root.addView(text(getString(R.string.models_note, Models.SIZE_MB), 13f, onVariant).apply { setPadding(dp(4), dp(8), dp(4), 0) })

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
                    isChecked = Icon.hidden(this@SetupActivity)
                    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                    thumbTintList = ColorStateList(states, intArrayOf(primary, onVariant))
                    trackTintList = ColorStateList(states, intArrayOf(primary and 0x00FFFFFF or 0x66000000, onVariant and 0x00FFFFFF or 0x33000000))
                    setOnCheckedChangeListener { _, hide -> Icon.setHidden(this@SetupActivity, hide) }
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
        Icon.apply(this)
        refresh(checkForNewer = true)
    }

    private fun languageName(tag: String): String = getString(when (tag) {
        "zh-Hani-CN" -> R.string.lang_zh_cn
        "zh-Hani-HK" -> R.string.lang_zh_hk
        "zh-Hani-TW" -> R.string.lang_zh_tw
        "ja" -> R.string.lang_ja
        else -> R.string.lang_ko
    })

    /**
     * Each language's line: ready (and used or not by your keyboards), downloading, or not on the
     * phone, with Remove or Download beside it. [checkForNewer]: the models your keyboards use are
     * fetched again too (ML Kit gets Google's newest), as opening the app always did.
     */
    private fun refresh(checkForNewer: Boolean = false) {
        val used = Models.used(this)
        if (checkForNewer) used.forEach { tag -> if (!Models.isFetching(tag)) Models.fetch(tag) { runOnUiThread { refresh() } } }
        Models.ready { ready ->
            runOnUiThread {
                Models.TAGS.forEach { tag ->
                    val (status, button) = rows[tag] ?: return@forEach
                    val inUse = tag in used
                    when {
                        Models.isFetching(tag) -> {
                            status.text = getString(R.string.downloading)
                            status.setTextColor(onVariant)
                            button.visibility = View.INVISIBLE
                        }
                        tag in ready -> {
                            status.text = getString(if (inUse) R.string.ready_used else R.string.ready_unused)
                            status.setTextColor(if (inUse) ok else onVariant)
                            button.text = getString(R.string.remove)
                            button.visibility = View.VISIBLE
                            button.setOnClickListener {
                                button.visibility = View.INVISIBLE
                                Models.remove(tag) { runOnUiThread { refresh() } }
                            }
                        }
                        else -> {
                            status.text = getString(if (inUse) R.string.failed else R.string.not_downloaded)
                            status.setTextColor(onVariant)
                            button.text = getString(R.string.download)
                            button.visibility = View.VISIBLE
                            button.setOnClickListener {
                                Models.fetch(tag) { runOnUiThread { refresh() } }
                                refresh()
                            }
                        }
                    }
                }
            }
        }
    }

    /** A small rounded text button in the accent colour. */
    private fun pill(label: String) = TextView(this).apply {
        text = label
        setTextColor(primary)
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        minHeight = dp(40)
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(0x1F, Color.red(primary), Color.green(primary), Color.blue(primary))),
            null, GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.WHITE) })
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
}
