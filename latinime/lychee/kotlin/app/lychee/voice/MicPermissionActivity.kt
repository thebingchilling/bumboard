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
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.lychee_voice_permission_granted, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private companion object {
        const val REQUEST = 1
    }
}
