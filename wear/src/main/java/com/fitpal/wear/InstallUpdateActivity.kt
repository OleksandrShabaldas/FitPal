package com.fitpal.wear

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider

/**
 * Invisible activity that hands the staged APK to the watch's package installer. Launched from the
 * "update ready" notification (a notification can't start an install on its own).
 *
 * If the watch has no installer UI available, this says so rather than failing silently — the
 * ADB sideload path in the README still works in that case.
 */
class InstallUpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apk = WatchUpdateInstaller.stagingFile(this)
        if (!apk.exists() || apk.length() <= 0L) {
            Toast.makeText(this, "No update file found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure {
            Toast.makeText(this, "This watch won't open the installer — sideload with ADB", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
