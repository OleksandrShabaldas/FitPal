package com.fitpal.app.update

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** A release on GitHub that's newer than what's installed. */
data class AvailableUpdate(
    /** Clean version, e.g. "1.1.0" (the leading "v" of the tag is stripped). */
    val version: String,
    val releaseNotes: String,
    /** Download URL + size of the phone APK, if the release has one. */
    val phoneApkUrl: String?,
    val phoneApkBytes: Long,
    /** Download URL + size of the watch APK, if the release has one. */
    val watchApkUrl: String?,
    val watchApkBytes: Long
)

/**
 * Checks GitHub Releases for a newer FitPal build.
 *
 * FitPal is sideloaded (not on the Play Store), so there's no store to deliver updates — this
 * reads the public releases API of the project's own repo. No token is needed for a public repo,
 * and nothing is sent: it's a plain GET.
 *
 * Assets are matched by name prefix (`FitPal-app…apk` / `FitPal-watch…apk`) so the version part of
 * the filename can change freely between releases.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    /**
     * The latest published release, or null if it isn't newer than [currentVersion] (or the check
     * fails — a failed update check is never worth surfacing as an error).
     */
    fun check(currentVersion: String): AvailableUpdate? = runCatching {
        val json = JSONObject(fetch(LATEST_RELEASE_URL))
        // Drafts/prereleases are filtered out by the /latest endpoint already.
        val tag = json.optString("tag_name").ifBlank { return null }
        val version = tag.removePrefix("v").trim()
        if (compareVersions(version, currentVersion.removePrefix("v")) <= 0) return null

        val assets = json.optJSONArray("assets")
        var phoneUrl: String? = null
        var phoneBytes = 0L
        var watchUrl: String? = null
        var watchBytes = 0L
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url")
                if (url.isBlank()) continue
                val size = asset.optLong("size")
                when {
                    name.startsWith("FitPal-app", ignoreCase = true) -> { phoneUrl = url; phoneBytes = size }
                    name.startsWith("FitPal-watch", ignoreCase = true) -> { watchUrl = url; watchBytes = size }
                }
            }
        }
        if (phoneUrl == null && watchUrl == null) return null

        AvailableUpdate(
            version = version,
            releaseNotes = json.optString("body").trim(),
            phoneApkUrl = phoneUrl,
            phoneApkBytes = phoneBytes,
            watchApkUrl = watchUrl,
            watchApkBytes = watchBytes
        )
    }.getOrNull()

    private fun fetch(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "FitPal-Android")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val REPO = "OleksandrShabaldas/FitPal"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO/releases/latest"

        /**
         * Compare dotted versions numerically ("1.10.0" > "1.9.0", which a string compare gets
         * wrong). Any non-numeric suffix is ignored. Returns <0, 0 or >0 like [Comparable].
         */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val pb = b.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = (pa.getOrNull(i) ?: 0).compareTo(pb.getOrNull(i) ?: 0)
                if (d != 0) return d
            }
            return 0
        }
    }
}
