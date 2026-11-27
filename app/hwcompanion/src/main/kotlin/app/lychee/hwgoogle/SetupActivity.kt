package app.lychee.hwgoogle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Lychee handwriting's one screen, in Material 3 like Lychee's Settings: what the app is, each
 * language's model (ready, downloading or not on the phone; whether Lychee's keyboards use it;
 * Download or Remove), what the app does and doesn't do with the internet, a switch to hide its
 * icon, and Close, clear of the navigation bar (the screen draws edge to edge). Lychee opens it
 * from Settings > Add-ons too, so a hidden icon is never a lost app.
 */
class SetupActivity : ComponentActivity() {

    /** What each language's row shows. */
    enum class State { CHECKING, DOWNLOADING, READY, NOT_HERE, FAILED }

    class Row(val tag: String, val state: State, val used: Boolean)

    private val rows = mutableStateListOf<Row>()
    private var hidden by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hidden = Icon.hidden(this)
        rows.addAll(Models.TAGS.map { Row(it, State.CHECKING, false) })
        setContent {
            CompanionTheme {
                SetupScreen(rows, hidden,
                    onDownload = { tag -> Models.fetch(tag) { runOnUiThread { refresh() } }; refresh() },
                    onRemove = { tag -> Models.remove(tag) { runOnUiThread { refresh() } } },
                    onHide = { hide -> hidden = hide; Icon.setHidden(this, hide) },
                    onClose = { finishAndRemoveTask() })
            }
        }
        Icon.apply(this)
        refresh(checkForNewer = true)
    }

    /**
     * Each language's line: ready (and used or not by your keyboards), downloading, or not on the
     * phone. [checkForNewer]: the models your keyboards use are fetched again too (ML Kit gets
     * Google's newest), as opening the app always did.
     */
    private fun refresh(checkForNewer: Boolean = false) {
        val used = Models.used(this)
        if (checkForNewer) used.forEach { tag -> if (!Models.isFetching(tag)) Models.fetch(tag) { runOnUiThread { refresh() } } }
        Models.ready { ready ->
            runOnUiThread {
                val next = Models.TAGS.map { tag ->
                    val inUse = tag in used
                    Row(tag, when {
                        Models.isFetching(tag) -> State.DOWNLOADING
                        tag in ready -> State.READY
                        inUse -> State.FAILED
                        else -> State.NOT_HERE
                    }, inUse)
                }
                rows.clear()
                rows.addAll(next)
            }
        }
    }
}

/** The screen itself (also drawn by the screenshots). */
@Composable
fun SetupScreen(
    rows: List<SetupActivity.Row>, hidden: Boolean,
    onDownload: (String) -> Unit, onRemove: (String) -> Unit, onHide: (Boolean) -> Unit, onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = colors.surface) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                // drawn edge to edge: kept clear of the status bar and the navigation bar
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // the app: its icon, its name, one line on what it is (not in a card)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                // the launcher icon's two layers (an adaptive icon can't be painted as one)
                androidx.compose.foundation.layout.Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))) {
                    Image(painterResource(R.drawable.lychee_icon_background), null, Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    Image(painterResource(R.drawable.lychee_icon_foreground), null, Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
                    Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }

            Heading(stringResource(R.string.models))
            Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
                rows.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = colors.outlineVariant)
                    LanguageRow(row, onDownload, onRemove)
                }
            }
            Text(stringResource(R.string.models_note, Models.SIZE_MB), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp))

            Heading(stringResource(R.string.privacy))
            Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
                Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface, modifier = Modifier.padding(16.dp))
            }

            Heading(stringResource(R.string.app_icon))
            Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.hide_icon)) },
                    supportingContent = { Text(stringResource(R.string.hide_icon_note)) },
                    trailingContent = { Switch(checked = hidden, onCheckedChange = onHide) },
                    colors = ListItemDefaults.colors(containerColor = colors.surfaceContainerLow),
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onClose) { Text(stringResource(R.string.close)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Heading(text: String) = Text(
    text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
)

@Composable
private fun LanguageRow(row: SetupActivity.Row, onDownload: (String) -> Unit, onRemove: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(when (row.tag) {
        "zh-Hani-CN" -> R.string.lang_zh_cn
        "zh-Hani-HK" -> R.string.lang_zh_hk
        "zh-Hani-TW" -> R.string.lang_zh_tw
        "ja" -> R.string.lang_ja
        else -> R.string.lang_ko
    })
    val status = stringResource(when (row.state) {
        SetupActivity.State.CHECKING -> R.string.checking
        SetupActivity.State.DOWNLOADING -> R.string.downloading
        SetupActivity.State.READY -> if (row.used) R.string.ready_used else R.string.ready_unused
        SetupActivity.State.NOT_HERE -> R.string.not_downloaded
        SetupActivity.State.FAILED -> R.string.failed
    })
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = {
            Text(status, color = if (row.state == SetupActivity.State.READY && row.used) colors.tertiary else colors.onSurfaceVariant)
        },
        trailingContent = {
            when (row.state) {
                SetupActivity.State.CHECKING, SetupActivity.State.DOWNLOADING ->
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                SetupActivity.State.READY -> OutlinedButton(onClick = { onRemove(row.tag) }) { Text(stringResource(R.string.remove)) }
                else -> Button(onClick = { onDownload(row.tag) }) { Text(stringResource(R.string.download)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = colors.surfaceContainerLow),
    )
}
