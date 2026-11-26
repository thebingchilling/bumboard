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

    private fun page(name: String, title: String, scroll: Int = 0, setUp: () -> Unit) = draw(paparazzi, name, title, scroll, setUp)

    companion object {
    /** Stands in for the activity's result registry (the file picker), which a snapshot hasn't. */
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {}
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun draw(paparazzi: Paparazzi, name: String, title: String, scroll: Int = 0, setUp: () -> Unit) {
        StoreLook.startTab = 0
        ProductSheet.open = null
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
                            AddonsPage()
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
    @Test fun listingPack() = page("6-listing-japanese-pack", "Japanese Pack") { ProductSheet.open = Store.byId("pack_ja") }
    @Test fun listingAddon() = page("7-listing-chinese-in-depth", "Chinese In Depth") { ProductSheet.open = Store.byId("card_zh") }
    @Test fun listingHandwriting() = page("8-listing-google-handwriting", "Google Handwriting") { ProductSheet.open = Store.byId("handwriting") }
    @Test fun updates() = page("9-updates", "Add-ons") { StoreLook.startTab = 2 }
}

/** Add-ons in dark mode. */
class AddonsScreensDark {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = S23.copy(nightMode = com.android.resources.NightMode.NIGHT),
        theme = "android:Theme.Material.NoActionBar")

    @Test fun store() = AddonsScreens.draw(paparazzi, "d1-store-dark", "Add-ons") {}
    @Test fun listing() = AddonsScreens.draw(paparazzi, "d2-listing-dark", "Japanese Pack") { ProductSheet.open = Store.byId("pack_ja") }
}
