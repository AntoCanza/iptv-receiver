package io.github.antocanza.iptvreceiver.data

import java.util.concurrent.atomic.AtomicBoolean

/** Credential-bearing addresses remain in memory and must never appear in diagnostics. */
class LiveManifestSession(internal val originUrl: String) {
    private val firstPlaylistSeen = AtomicBoolean(false)
    @Volatile private var currentUrl: String? = null

    internal fun parsed(url: String, isMediaPlaylist: Boolean) {
        // A master playlist can have several independent variants; never collapse those URLs.
        if (firstPlaylistSeen.compareAndSet(false, true) && isMediaPlaylist) currentUrl = url
    }

    internal fun resolve(requestedUrl: String): String = currentUrl ?: requestedUrl
    internal fun canRefresh(failedUrl: String): Boolean = currentUrl != null && failedUrl != originUrl
    internal fun refreshed(resolvedUrl: String) { if (currentUrl != null) currentUrl = resolvedUrl }
    override fun toString(): String = "LiveManifestSession([redacted])"
}
