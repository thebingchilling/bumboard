package app.lychee.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import com.android.inputmethod.latin.R

/**
 * A see-through screen that only asks for the microphone, since an input method cannot ask
 * for a permission itself. Opened by the voice key the first time; closes straight after.
 */
class MicPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.lychee.DiagLog.init(this)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        // asked once: a screen brought back (rotated, or restored) waits for its answer
        if (savedInstanceState == null) {
            app.lychee.DiagLog.log("asking for the microphone")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        app.lychee.DiagLog.log("microphone ${if (granted) "allowed" else "not allowed"}")
        if (granted) {
            runCatching { Toast.makeText(applicationContext, R.string.lychee_voice_permission_granted, Toast.LENGTH_SHORT).show() }
        }
        finish()
        // no closing animation: straight back to the app and its keyboard
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val REQUEST = 1
    }
}
