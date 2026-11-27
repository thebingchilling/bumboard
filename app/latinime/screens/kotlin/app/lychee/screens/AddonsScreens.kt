package app.lychee.screens

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.lychee.settings.AddonsPage
import app.lychee.settings.AddonSheet
import app.lychee.settings.GroupCounter
import app.lychee.settings.LocalGroupCounter
import app.lychee.settings.LycheeTheme
import app.lychee.settings.ProductSheet
import app.lychee.settings.StoreLook
import app.lychee.store.Store
import com.android.inputmethod.latin.R
import org.junit.Rule
import org.junit.Test

/** Settings > Add-ons, framed as Settings frames it (its app bar), in each design option. */
class AddonsScreens {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = S23, theme = "android:Theme.Material.Light.NoActionBar")

    private fun page(name: String, title: String, scroll: Int = 0, setUp: () -> Unit) = draw(paparazzi, name, title, scroll, setUp = setUp)

    companion object {
    /** Stands in for the activity's result registry (the file picker), which a snapshot hasn't. */
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {}
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun draw(paparazzi: Paparazzi, name: String, title: String, scroll: Int = 0,
             content: @Composable () -> Unit = { AddonsPage() }, setUp: () -> Unit) {
        StoreLook.startTab = 0
        ProductSheet.open = null
        AddonSheet.open = null
        // the real word card in the listings' examples, from the sample dictionary
        if (RealData.available) RealData.install() else app.lychee.chinese.Readings.useForScreens(annotator)
        setUp()
        paparazzi.snapshot(name) {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner, LocalGroupCounter provides GroupCounter()) {
                LycheeTheme {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surface,
                        topBar = {
                            TopAppBar(
                                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                                navigationIcon = { Icon(painterResource(R.drawable.ic_sym_arrow_back), null, Modifier.padding(start = 12.dp)) },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                            )
                        },
                    ) { padding ->
                        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState(scroll))) {
                            content()
                        }
                    }
                }
            }
        }
    }

    }

    @Test fun store() = page("1-store", "Add-ons") {}
    @Test fun storeFurther() = page("2-store-further", "Add-ons", scroll = 1200) {}
    @Test fun storeFurthest() = page("3-store-languages", "Add-ons", scroll = 2400) {}
    @Test fun installed() = page("4-installed", "Add-ons") { StoreLook.startTab = 1 }
    @Test fun listingPack() = page("6-listing-japanese-pack", Store.PACK_JA.name) { ProductSheet.open = Store.byId("pack_ja") }
    @Test fun listingAddon() = page("7-listing-chinese-in-depth", Store.CARD_ZH.name) { ProductSheet.open = Store.byId("card_zh") }
    @Test fun listingHandwriting() = page("8-listing-google-handwriting", Store.HANDWRITING.name) { ProductSheet.open = Store.byId("handwriting") }
    @Test fun updates() = page("9-updates", "Add-ons") { StoreLook.startTab = 2 }
    @Test fun listingChineseFurther() = page("7b-listing-chinese-further", Store.CARD_ZH.name, scroll = 1100) { ProductSheet.open = Store.byId("card_zh") }
    /**
     * A listing's example, Light (its Dark is in AddonsScreensDark: Paparazzi draws every view in
     * the device's own light or dark, so the switch is shown on a phone of each).
     */
    @Test fun exampleLight() {
        if (RealData.available) RealData.install()
        paparazzi.snapshot(app.lychee.settings.KeyboardSample.build(paparazzi.context, Store.Sample.CHINESE, dark = false), "14-example-light")
    }

    /** Settings > Theme: its preview is the real keyboard. */
    @Test fun theme() = draw(paparazzi, "13-theme", "Theme", content = { app.lychee.settings.ThemePage() }) {}

    @Test fun listingFree() = page("12-listing-free-japanese-dictionary", "Japanese Typing") {
        AddonSheet.open = app.lychee.packs.Packs.catalog(paparazzi.context).firstOrNull { it.id == "rime_ja" }
    }
}

/** Add-ons in dark mode. */
class AddonsScreensDark {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = S23.copy(nightMode = com.android.resources.NightMode.NIGHT),
        theme = "android:Theme.Material.NoActionBar")

    @Test fun store() = AddonsScreens.draw(paparazzi, "d1-store-dark", "Add-ons") {}
    @Test fun listing() = AddonsScreens.draw(paparazzi, "d2-listing-dark", Store.PACK_JA.name) { ProductSheet.open = Store.byId("pack_ja") }
    @Test fun exampleDark() {
        if (RealData.available) RealData.install()
        paparazzi.snapshot(app.lychee.settings.KeyboardSample.build(paparazzi.context, Store.Sample.CHINESE, dark = true), "d3-example-dark")
    }
}

/** The rest of a listing's page, further down (Paparazzi shrinks very tall phones to 1000 px). */
class AddonsScreensFurther {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = S23, theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun listing2() = AddonsScreens.draw(paparazzi, "f1-listing-2", Store.CARD_ZH.name, scroll = 2000) { ProductSheet.open = Store.CARD_ZH }
    @Test fun listing3() = AddonsScreens.draw(paparazzi, "f2-listing-3", Store.CARD_ZH.name, scroll = 4000) { ProductSheet.open = Store.CARD_ZH }
    @Test fun scribble2() = AddonsScreens.draw(paparazzi, "f3-scribble-2", Store.HANDWRITING.name, scroll = 2000) { ProductSheet.open = Store.HANDWRITING }
    @Test fun free2() = AddonsScreens.draw(paparazzi, "f4-free-2", "Japanese Typing", scroll = 1500) {
        AddonSheet.open = app.lychee.packs.Packs.catalog(paparazzi.context).firstOrNull { it.id == "rime_ja" }
    }
}
