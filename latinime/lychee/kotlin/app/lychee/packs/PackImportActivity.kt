package app.lychee.packs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lychee.settings.LycheeTheme
import app.lychee.settings.Page
import app.lychee.settings.SettingsActivity
import com.android.inputmethod.latin.R

/**
 * Installs packs the moment their files are opened: tap Open on the browser's "download
 * complete" notice (or share the file to Lychee) and a small dialog shows it installing, then
 * installed. Lychee still never touches the network itself.
 *
 * It also takes the packs page's lychee://updates links: they carry the newest versions (see
 * [Updates]); with install=1 ("Back to Lychee" after downloading updates) the file picker opens
 * in Downloads, so every downloaded update installs in one go.
 */
class PackImportActivity : ComponentActivity() {

    private var state by mutableStateOf<State>(State.Working(null))

    private sealed class State {
        data class Working(val progress: String?) : State()
        data class Done(val lines: List<String>) : State()
    }

    private val pickMany = registerForActivityResult(PickInDownloads()) { uris ->
        if (uris.isNullOrEmpty()) {
            openDownloadsPage()
            finish()
        } else {
            install(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent?.data?.takeIf { intent?.action == Intent.ACTION_VIEW && it.scheme == "lychee" }
        if (link != null) {
            Updates.record(applicationContext, link)
            if (link.getQueryParameter("install") != "1") {
                openDownloadsPage()
                finish()
                return
            }
            if (savedInstanceState == null) {
                pickMany.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }
        } else {
            val uri = fileOf(intent)
            if (uri == null) {
                finish()
                return
            }
            if (savedInstanceState == null) install(listOf(uri))
        }
        setContent {
            LycheeTheme {
                AlertDialog(
                    onDismissRequest = { if (state is State.Done) finish() },
                    title = { Text(stringResource(R.string.lychee_import_title)) },
                    text = {
                        when (val s = state) {
                            is State.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                                Spacer(Modifier.width(16.dp))
                                Text(s.progress ?: stringResource(R.string.lychee_packs_importing))
                            }
                            is State.Done -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                s.lines.forEach { Text(it) }
                            }
                        }
                    },
                    confirmButton = {
                        if (state is State.Done) TextButton(onClick = { finish() }) { Text(stringResource(R.string.lychee_close)) }
                    },
                )
            }
        }
    }

    /** Imports [uris] one after another on a background thread. */
    private fun install(uris: List<Uri>) {
        val app = applicationContext
        Thread {
            val lines = uris.mapIndexed { i, uri ->
                if (uris.size > 1) {
                    val progress = getString(R.string.lychee_updates_installing, i + 1, uris.size)
                    runOnUiThread { state = State.Working(progress) }
                }
                when (val result = PackImporter.import(app, uri, null)) {
                    is PackImporter.Result.Ok -> getString(R.string.lychee_import_done, result.pack.name)
                    is PackImporter.Result.Error -> result.message
                }
            }
            runOnUiThread { state = State.Done(lines) }
        }.start()
    }

    private fun openDownloadsPage() {
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_PAGE, Page.Packs.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun fileOf(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        else -> null
    }

    /** The system file picker, several files at once, opening in Downloads. */
    private class PickInDownloads : ActivityResultContracts.OpenMultipleDocuments() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI,
                        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download"),
                    )
                }
            }
    }
}
