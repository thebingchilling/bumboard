package app.lychee.packs

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.android.inputmethod.latin.R

/**
 * Installs a pack the moment its file is opened: tap Open on the browser's "download
 * complete" notice (or share the file to Lychee) and a small dialog shows it installing,
 * then installed. No file picking, and Lychee still never touches the network itself.
 */
class PackImportActivity : ComponentActivity() {

    private var state by mutableStateOf<State>(State.Working)

    private sealed class State {
        object Working : State()
        data class Done(val name: String) : State()
        data class Failed(val message: String) : State()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = fileOf(intent)
        if (uri == null) {
            finish()
            return
        }
        setContent {
            LycheeTheme {
                AlertDialog(
                    onDismissRequest = { if (state !is State.Working) finish() },
                    title = { Text(stringResource(R.string.lychee_import_title)) },
                    text = {
                        when (val s = state) {
                            State.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                                Spacer(Modifier.width(16.dp))
                                Text(stringResource(R.string.lychee_packs_importing))
                            }
                            is State.Done -> Text(stringResource(R.string.lychee_import_done, s.name))
                            is State.Failed -> Text(s.message)
                        }
                    },
                    confirmButton = {
                        if (state !is State.Working) TextButton(onClick = { finish() }) { Text(stringResource(R.string.lychee_close)) }
                    },
                )
            }
        }
        if (savedInstanceState == null) {
            val app = applicationContext
            Thread {
                val result = PackImporter.import(app, uri, null)
                runOnUiThread {
                    state = when (result) {
                        is PackImporter.Result.Ok -> State.Done(result.pack.name)
                        is PackImporter.Result.Error -> State.Failed(result.message)
                    }
                }
            }.start()
        }
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
}
