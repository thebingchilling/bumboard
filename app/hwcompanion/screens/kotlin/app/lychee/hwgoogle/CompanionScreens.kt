package app.lychee.hwgoogle

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import org.junit.Rule
import org.junit.Test

private val S23 = DeviceConfig.PIXEL_6.copy(screenWidth = 1080, screenHeight = 2340, xdpi = 425, ydpi = 425,
    density = com.android.resources.Density.create(420))

private val rows = listOf(
    SetupActivity.Row("zh-Hani-CN", SetupActivity.State.READY, true),
    SetupActivity.Row("zh-Hani-HK", SetupActivity.State.READY, true),
    SetupActivity.Row("zh-Hani-TW", SetupActivity.State.DOWNLOADING, false),
    SetupActivity.Row("ja", SetupActivity.State.NOT_HERE, false),
    SetupActivity.Row("ko", SetupActivity.State.NOT_HERE, false),
)

/** Lychee handwriting's screen, light and dark. */
class CompanionScreens {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = S23, theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun setup() = paparazzi.snapshot("c1-companion") { CompanionTheme(dark = false) { SetupScreen(rows, false, {}, {}, {}, {}) } }
}

class CompanionScreensDark {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = S23.copy(nightMode = NightMode.NIGHT), theme = "android:Theme.Material.NoActionBar")

    @Test fun setup() = paparazzi.snapshot("c2-companion-dark") { CompanionTheme(dark = true) { SetupScreen(rows, false, {}, {}, {}, {}) } }
}
