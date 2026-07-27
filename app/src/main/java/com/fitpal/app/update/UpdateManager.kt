package com.fitpal.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.fitpal.app.BuildConfig
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.shared.WearContract
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Where the update flow currently is, for the Settings card + the prompt dialog. */
enum class UpdatePhase {
    IDLE,
    CHECKING,
    AVAILABLE,
    DOWNLOADING_PHONE,
    READY_PHONE,
    DOWNLOADING_WATCH,
    SENDING_WATCH,
    WATCH_SENT,
    UP_TO_DATE,
    FAILED
}

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val available: AvailableUpdate? = null,
    /** 0..1 during a download / transfer. */
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null,
    /** The watch's installed version, once it has reported one. */
    val watchVersion: String? = null,
    val watchConnected: Boolean = false
) {
    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** True when a newer release also ships a watch APK and the watch is behind it. */
    val watchNeedsUpdate: Boolean
        get() {
            val target = available?.version ?: return false
            val installed = watchVersion ?: return false
            return available.watchApkUrl != null &&
                UpdateChecker.compareVersions(target, installed) > 0
        }
}

/**
 * Drives "is there a newer FitPal on GitHub?" for this sideloaded build, for **both** the phone and
 * the paired watch.
 *
 * How far this can go is capped by Android itself: an app can download an APK and *open the system
 * installer*, but it can never install silently — the user always confirms in the OS dialog (only
 * device-owner/rooted setups can bypass that). So "auto update" here means auto-check plus
 * auto-download, landing the user on the confirm screen with nothing left to hunt for.
 *
 * The watch can't reach GitHub reliably on its own, so the phone downloads the watch APK and
 * streams it over the already-paired Data Layer channel; the watch then prompts to install it.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checker: UpdateChecker,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadDir = File(context.cacheDir, "updates").apply { mkdirs() }

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** Set by [WearDataLayerService] when the watch reports its version. */
    fun onWatchVersionReported(version: String) {
        _state.update { it.copy(watchVersion = version.trim(), watchConnected = true) }
    }

    /**
     * Check GitHub. [userInitiated] shows "you're up to date" / errors; the silent daily check
     * stays quiet unless it actually finds something.
     */
    fun check(userInitiated: Boolean = false) {
        if (_state.value.phase == UpdatePhase.CHECKING) return
        scope.launch {
            _state.update { it.copy(phase = UpdatePhase.CHECKING, message = null) }
            askWatchForVersion()
            val found = checker.check(BuildConfig.VERSION_NAME)
            settings.setLastUpdateCheck(System.currentTimeMillis())
            _state.update {
                when {
                    found != null -> it.copy(phase = UpdatePhase.AVAILABLE, available = found)
                    userInitiated -> it.copy(phase = UpdatePhase.UP_TO_DATE, available = null)
                    else -> it.copy(phase = UpdatePhase.IDLE, available = null)
                }
            }
            // "Auto update" = don't make the user tap Download; go straight to the system installer.
            if (found?.phoneApkUrl != null && settings.autoInstallUpdates.value) downloadAndInstallPhone()
        }
    }

    /** Run the once-a-day background check, if the user left auto-check on. */
    fun checkOnStartIfDue() {
        if (!settings.autoCheckUpdates.value) return
        val last = settings.lastUpdateCheck.value
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(userInitiated = false)
    }

    /** Download the phone APK, then hand it to the system installer for the user to confirm. */
    fun downloadAndInstallPhone() {
        val update = _state.value.available ?: return
        val url = update.phoneApkUrl ?: return
        scope.launch {
            val file = File(downloadDir, "FitPal-app-${update.version}.apk")
            val ok = download(url, file, update.phoneApkBytes, UpdatePhase.DOWNLOADING_PHONE)
            if (!ok) return@launch
            _state.update { it.copy(phase = UpdatePhase.READY_PHONE, progress = 1f) }
            launchInstaller(file)
        }
    }

    /** Re-open the installer for an APK already downloaded (e.g. the user dismissed the dialog). */
    fun installDownloadedPhoneApk() {
        val version = _state.value.available?.version ?: return
        val file = File(downloadDir, "FitPal-app-$version.apk")
        if (file.exists()) launchInstaller(file)
    }

    /** Download the watch APK on the phone, then stream it to the watch over the Data Layer. */
    fun updateWatch() {
        val update = _state.value.available ?: return
        val url = update.watchApkUrl ?: return
        scope.launch {
            val file = File(downloadDir, "FitPal-watch-${update.version}.apk")
            val ok = download(url, file, update.watchApkBytes, UpdatePhase.DOWNLOADING_WATCH)
            if (!ok) return@launch
            sendApkToWatch(file, update.version)
        }
    }

    fun dismiss() {
        _state.update { it.copy(phase = UpdatePhase.IDLE, message = null) }
    }

    /** Don't prompt again for this specific version. */
    fun skipVersion() {
        _state.value.available?.version?.let { settings.setSkippedUpdateVersion(it) }
        dismiss()
    }

    // ---------------- internals ----------------

    private suspend fun askWatchForVersion() {
        runCatching {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            _state.update { it.copy(watchConnected = nodes.isNotEmpty()) }
            val client = Wearable.getMessageClient(context)
            nodes.forEach { node ->
                runCatching {
                    client.sendMessage(node.id, WearContract.PATH_REQUEST_WATCH_VERSION, ByteArray(0)).await()
                }
            }
        }
    }

    /** Shared download with progress + a .part temp file, mirroring ModelManager's approach. */
    private fun download(url: String, target: File, approxBytes: Long, phase: UpdatePhase): Boolean {
        _state.update {
            it.copy(phase = phase, progress = 0f, downloadedBytes = 0L, totalBytes = approxBytes, message = null)
        }
        // Already downloaded and complete? Skip straight to using it.
        if (target.exists() && approxBytes > 0 && target.length() == approxBytes) return true

        val tmp = File(target.absolutePath + ".part")
        return try {
            if (tmp.exists()) tmp.delete()
            var current = url
            var redirects = 0
            while (true) {
                val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "FitPal-Android")
                    setRequestProperty("Accept", "*/*")
                }
                try {
                    conn.connect()
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val location = conn.getHeaderField("Location") ?: throw IOException("Redirect with no location")
                        if (++redirects > 6) throw IOException("Too many redirects")
                        current = if (location.startsWith("http")) location else URL(URL(current), location).toString()
                        continue
                    }
                    if (code !in 200..299) throw IOException("Server returned HTTP $code")
                    val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else approxBytes
                    conn.inputStream.use { input ->
                        BufferedOutputStream(FileOutputStream(tmp)).use { output ->
                            val buffer = ByteArray(1 shl 16)
                            var downloaded = 0L
                            var lastReported = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (downloaded - lastReported >= 500_000) {
                                    lastReported = downloaded
                                    val d = downloaded
                                    _state.update {
                                        it.copy(
                                            progress = if (total > 0) (d.toFloat() / total).coerceIn(0f, 1f) else 0f,
                                            downloadedBytes = d,
                                            totalBytes = total
                                        )
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                    break
                } finally {
                    conn.disconnect()
                }
            }
            if (tmp.length() <= 0L) throw IOException("Downloaded file is empty")
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not save the download")
            true
        } catch (e: Exception) {
            tmp.delete()
            _state.update { it.copy(phase = UpdatePhase.FAILED, message = e.message ?: "Download failed") }
            false
        }
    }

    /**
     * Open the system package installer. Android verifies the new APK is signed with the same key
     * as the installed app before allowing the update, which is what makes this safe: a tampered
     * download simply won't install over FitPal.
     */
    private fun launchInstaller(apk: File) {
        runCatching {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { e ->
            _state.update {
                it.copy(
                    phase = UpdatePhase.FAILED,
                    message = "Couldn't open the installer: ${e.message}. Allow FitPal to install " +
                        "unknown apps in Android settings, then try again."
                )
            }
        }
    }

    /** Stream the watch APK over a Data Layer channel to every connected watch. */
    private suspend fun sendApkToWatch(apk: File, version: String) {
        _state.update { it.copy(phase = UpdatePhase.SENDING_WATCH, progress = 0f, totalBytes = apk.length()) }
        val result = runCatching {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) throw IOException("No watch connected")
            val messageClient = Wearable.getMessageClient(context)
            val channelClient = Wearable.getChannelClient(context)
            for (node in nodes) {
                // Tell the watch what's coming so it can label its own prompt.
                runCatching {
                    messageClient.sendMessage(
                        node.id, WearContract.PATH_APK_INCOMING, version.toByteArray()
                    ).await()
                }
                val channel = channelClient.openChannel(node.id, WearContract.PATH_APK_CHANNEL).await()
                try {
                    val out = channelClient.getOutputStream(channel).await()
                    withContext(Dispatchers.IO) {
                        out.use { output ->
                            apk.inputStream().use { input ->
                                val buffer = ByteArray(1 shl 16)
                                var sent = 0L
                                val total = apk.length()
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    sent += read
                                    val s = sent
                                    _state.update {
                                        it.copy(
                                            progress = if (total > 0) (s.toFloat() / total).coerceIn(0f, 1f) else 0f,
                                            downloadedBytes = s,
                                            totalBytes = total
                                        )
                                    }
                                }
                                output.flush()
                            }
                        }
                    }
                } finally {
                    runCatching { channelClient.close(channel).await() }
                }
            }
        }
        _state.update {
            if (result.isSuccess) {
                it.copy(phase = UpdatePhase.WATCH_SENT, progress = 1f)
            } else {
                it.copy(
                    phase = UpdatePhase.FAILED,
                    message = "Couldn't send to the watch: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                )
            }
        }
    }

    companion object {
        /** How often the silent background check runs. */
        private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
