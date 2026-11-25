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
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    /** Stands in for the activity's result registry (the file picker), which a snapshot hasn't. */
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {}
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun page(name: String, title: String, scroll: Int = 0, setUp: () -> Unit) {
        StoreLook.tiles = false; StoreLook.listingB = false; StoreLook.startTab = 0; StoreLook.startCategory = -1
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
                                navigationIcon = { Icon(painterResource(R.drawable.ic_sym_arrow_back), null, Modifier.padding(start = 12.dp())) },
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

    private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

    @Test fun storeA() = page("1-store-A-plain", "Add-ons") {}
    @Test fun storeB() = page("2-store-B-tiles", "Add-ons") { StoreLook.tiles = true }
    @Test fun categoryA() = page("3-category-dictionaries-A", "Add-ons") { StoreLook.startCategory = 0 }
    @Test fun categoryB() = page("4-category-dictionaries-B", "Add-ons") { StoreLook.startCategory = 0; StoreLook.tiles = true }
    @Test fun listingA() = page("5-listing-A", "Add-ons") { ProductSheet.open = Store.byId("pack_ja") }
    @Test fun listingB() = page("6-listing-B", "Add-ons") { StoreLook.listingB = true; ProductSheet.open = Store.byId("pack_ja") }
    @Test fun listingAScrolled() = page("7-listing-A-further", "Add-ons", scroll = 1400) { ProductSheet.open = Store.byId("pack_ja") }
    @Test fun installed() = page("8-installed", "Add-ons") { StoreLook.startTab = 1 }
}
