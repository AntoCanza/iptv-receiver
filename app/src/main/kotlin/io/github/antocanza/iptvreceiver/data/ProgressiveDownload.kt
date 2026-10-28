package io.github.antocanza.iptvreceiver.data

import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI

interface DownloadOutput {
    fun length(): Long
    /** Truncate at offset before writing; an old tail must never survive a restarted request. */
    fun open(offset: Long): OutputStream
}

class UnsupportedDownloadException : IOException("Unsupported download")
class DownloadAccessException : IOException("Download access denied")

/** Platform HTTP transport. Errors deliberately contain no request addresses or provider text. */
internal class ProgressiveDownload {
    fun transfer(url: String, output: DownloadOutput, validator: String?, previousTotal: Long?,
        checkActive: () -> Unit, progress: (Long, Long?, String?) -> Unit) {
        val existing = output.length()
        val offset = if (validator != null && (previousTotal == null || existing < previousTotal)) existing else 0L
        val connection = connect(url, offset, validator, checkActive)
        try {
            val code = connection.responseCode
            if (code == 401 || code == 403) throw DownloadAccessException()
            if (code != 200 && code != 206) throw IOException("Download request failed")
            val type = connection.contentType.orEmpty().substringBefore(';').lowercase()
            if (type.startsWith("text/") || type.contains("json") || type.contains("xml") || type.contains("mpegurl") ||
                type.contains("dash") || connection.contentEncoding?.let { !it.equals("identity", true) } == true) throw UnsupportedDownloadException()
            val nextValidator = (connection.getHeaderField("ETag")?.takeUnless { it.startsWith("W/") }
                ?: connection.getHeaderField("Last-Modified"))?.takeIf { it.length <= 512 && !it.contains('\n') && !it.contains('\r') }
            val range = if (code == 206) parseDownloadRange(connection.getHeaderField("Content-Range")) else null
            val start = if (code == 206) offset else 0L
            val length = connection.getHeaderFieldLong("Content-Length", -1).takeIf { it >= 0 }
            if (code == 206 && (range == null || range.first != offset ||
                    (length != null && length != range.second - range.first + 1) ||
                    (previousTotal != null && previousTotal != range.third) ||
                    (validator != null && nextValidator != null && validator != nextValidator))) throw IOException("Invalid download range")
            val total = range?.third ?: length
            var bytes = start
            connection.inputStream.buffered().use { input ->
                // A playlist or error document is never a completed movie, even with a video extension.
                input.mark(512)
                val prefix = ByteArray(512)
                val prefixSize = input.read(prefix)
                input.reset()
                val text = if (prefixSize > 0) String(prefix, 0, prefixSize, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase() else ""
                if (start == 0L && (text.startsWith("#extm3u") || text.startsWith("<html") || text.startsWith("<!doctype") ||
                        text.startsWith("<?xml") || text.startsWith("<mpd") || text.startsWith("{") || text.startsWith("["))) throw UnsupportedDownloadException()
                output.open(start).use { destination ->
                    // Commit a new validator only after the old file has been truncated.
                    // Process death between these steps must never pair old bytes with a new identity.
                    progress(bytes, total, nextValidator)
                    val buffer = ByteArray(64 * 1024)
                    var lastUpdate = System.nanoTime()
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        destination.write(buffer, 0, count)
                        bytes += count
                        if (total != null && bytes > total) throw IOException("Invalid download length")
                        val now = System.nanoTime()
                        if (now - lastUpdate >= 500_000_000L) { progress(bytes, total, nextValidator); lastUpdate = now }
                    }
                    destination.flush()
                }
            }
            checkActive()
            if (bytes <= 0 || (total != null && bytes != total)) throw IOException("Incomplete download")
            progress(bytes, total ?: bytes, nextValidator)
        } finally { connection.disconnect() }
    }

    private fun connect(address: String, offset: Long, validator: String?, checkActive: () -> Unit): HttpURLConnection {
        var uri = URI(address)
        repeat(8) {
            checkActive()
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host == null || uri.userInfo != null) throw UnsupportedDownloadException()
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "IPTVReceiver/0.1")
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0 && validator != null) {
                connection.setRequestProperty("Range", "bytes=$offset-")
                connection.setRequestProperty("If-Range", validator)
            }
            var finalResponse = false
            try {
                val code = connection.responseCode
                if (code !in setOf(301, 302, 303, 307, 308)) { finalResponse = true; return connection }
                val location = connection.getHeaderField("Location") ?: throw IOException("Invalid download redirect")
                uri = uri.resolve(location)
            } finally {
                // Leave a final response open for transfer().
                if (!finalResponse) connection.disconnect()
            }
        }
        throw IOException("Too many download redirects")
    }
}

internal fun parseDownloadRange(value: String?): Triple<Long, Long, Long>? {
    val match = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(value.orEmpty()) ?: return null
    val (start, end, total) = match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
    return if (start <= end && end < total) Triple(start, end, total) else null
}
