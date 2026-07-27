package com.fitpal.app.ml

import android.content.Context
import com.fitpal.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** The on-device model the app needs (a single multimodal Gemma model). */
enum class ModelId { LLM }

/** Download/availability state of a model. */
sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/**
 * Downloads and tracks the on-device AI model (Gemma 3n E4B — the larger, more
 * accurate multimodal model: it can read photos and text). The file is gated on
 * Hugging Face, so the download sends the user's token and handles the redirect to
 * the CDN manually.
 *
 * This is the OFFLINE fallback engine. When a Gemini key is set the app prefers the online
 * model (see [RoutingIngredientEngine]); this on-device model is what runs with no internet,
 * no key, or once the daily free quota is spent. After this one download it works fully offline.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    data class Spec(
        val id: ModelId,
        val displayName: String,
        val description: String,
        val url: String,
        val fileName: String,
        val approxBytes: Long,
        val minBytes: Long,
        val requiresToken: Boolean
    )

    val specs: List<Spec> = listOf(
        Spec(
            id = ModelId.LLM,
            displayName = "Vision AI model",
            description = "Gemma 3n E4B — larger model, more accurate food & meal recognition",
            url = GEMMA_URL,
            fileName = "gemma_3n_e4b_int4.task",
            approxBytes = 4_410_000_000L,
            minBytes = 3_000_000_000L,
            requiresToken = true
        )
    )

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloading = mutableSetOf<ModelId>()

    private val _statuses = MutableStateFlow(
        specs.associate { spec ->
            spec.id to if (isReady(spec.id)) ModelStatus.Ready else ModelStatus.NotDownloaded
        }
    )
    val statuses: StateFlow<Map<ModelId, ModelStatus>> = _statuses.asStateFlow()

    private fun spec(id: ModelId): Spec = specs.first { it.id == id }

    fun fileFor(id: ModelId): File = File(modelsDir, spec(id).fileName)

    fun isReady(id: ModelId): Boolean {
        val file = fileFor(id)
        return file.exists() && file.length() >= spec(id).minBytes
    }

    /** The single multimodal model — used for both photos and text. */
    val isLlmReady: Boolean get() = isReady(ModelId.LLM)
    val allReady: Boolean get() = specs.all { isReady(it.id) }
    val hasToken: Boolean get() = !settingsRepository.hfToken.value.isNullOrBlank()

    fun startDownloadAll() {
        specs.forEach { if (!isReady(it.id)) startDownload(it.id) }
    }

    fun startDownload(id: ModelId) {
        synchronized(downloading) {
            if (id in downloading) return
            if (isReady(id)) {
                update(id, ModelStatus.Ready)
                return
            }
            downloading.add(id)
        }
        scope.launch {
            try {
                downloadBlocking(id)
            } finally {
                synchronized(downloading) { downloading.remove(id) }
            }
        }
    }

    private fun update(id: ModelId, status: ModelStatus) {
        _statuses.update { it + (id to status) }
    }

    private fun downloadBlocking(id: ModelId) {
        val s = spec(id)
        val token = settingsRepository.hfToken.value
        if (s.requiresToken && token.isNullOrBlank()) {
            update(id, ModelStatus.Failed("Add your Hugging Face token in Settings first"))
            return
        }

        val target = fileFor(id)
        val tmp = File(target.absolutePath + ".part")
        update(id, ModelStatus.Downloading(0f, 0L, s.approxBytes))

        try {
            if (tmp.exists()) tmp.delete()
            downloadWithRedirects(s.url, token, tmp, s.approxBytes) { downloaded, total ->
                update(
                    id,
                    ModelStatus.Downloading(
                        progress = (downloaded.toFloat() / total).coerceIn(0f, 1f),
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                )
            }
            if (tmp.length() < s.minBytes) {
                throw IOException("Downloaded file looks incomplete (${tmp.length()} bytes)")
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not save the model file")
            update(id, ModelStatus.Ready)
            // Reclaim the ~3 GB used by the previous (smaller E2B) model now that the
            // larger one is in place. Harmless if it was never downloaded.
            runCatching {
                File(modelsDir, "gemma_3n_e2b_int4.task").takeIf { it != target && it.exists() }?.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            update(id, ModelStatus.Failed(e.message ?: "Download failed"))
        }
    }

    /**
     * Download following redirects manually. The auth token is only sent to
     * huggingface.co; the redirect to the signed CDN URL is fetched without it
     * (sending auth there would be rejected).
     */
    private fun downloadWithRedirects(
        startUrl: String,
        token: String?,
        target: File,
        approxBytes: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        var current = startUrl
        var redirects = 0
        while (true) {
            val url = URL(current)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "FitPal-Android")
                setRequestProperty("Accept", "*/*")
                if (!token.isNullOrBlank() && url.host.contains("huggingface.co")) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            try {
                conn.connect()
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Redirect with no location")
                    if (++redirects > 6) throw IOException("Too many redirects")
                    current = if (location.startsWith("http")) location
                    else URL(url, location).toString()
                    continue
                }
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw IOException(
                        "Access denied — check your token and that you accepted the " +
                            "Gemma license on Hugging Face"
                    )
                }
                if (code !in 200..299) throw IOException("Server returned HTTP $code")

                val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else approxBytes
                conn.inputStream.use { input ->
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        val buffer = ByteArray(1 shl 16)
                        var downloaded = 0L
                        var lastReported = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastReported >= 4_000_000) {
                                lastReported = downloaded
                                onProgress(downloaded, total)
                            }
                        }
                        output.flush()
                    }
                }
                return
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        // Gated, multimodal Gemma 3n E4B in MediaPipe .task format (~4.41 GB).
        private const val GEMMA_URL =
            "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/" +
                "gemma-3n-E4B-it-int4.task"
    }
}
