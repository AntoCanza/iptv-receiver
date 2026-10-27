package io.github.antocanza.iptvreceiver.data

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.HttpDataSource
import io.github.antocanza.iptvreceiver.model.ProviderException
import io.github.antocanza.iptvreceiver.model.requireAllowedStreamScheme
import java.io.IOException

/** No URI is included in this exception because stream paths may contain account secrets. */
class InvalidStreamSchemeException : IOException("Stream URL must use HTTP or HTTPS.")

@androidx.annotation.OptIn(UnstableApi::class)
class StreamDataSourceFactory(
    private val delegate: DataSource.Factory,
    private val liveManifestSession: LiveManifestSession? = null,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = GuardedStreamDataSource(delegate.createDataSource(), liveManifestSession)
}

@androidx.annotation.OptIn(UnstableApi::class)
private class GuardedStreamDataSource(
    private val delegate: DataSource,
    private val liveManifestSession: LiveManifestSession?,
) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) = delegate.addTransferListener(transferListener)
    override fun open(dataSpec: DataSpec): Long {
        val request = liveManifestSession?.resolve(dataSpec.uri.toString())?.let { dataSpec.withUri(it.toUri()) } ?: dataSpec
        requireHttp(request.uri)
        try { return delegate.open(request) }
        catch (error: HttpDataSource.InvalidResponseCodeException) {
            val session = liveManifestSession
            if (error.responseCode != 509 || session == null || !session.canRefresh(request.uri.toString())) throw error
            // Refresh an expired redirected playlist before already-buffered media runs out.
            try { delegate.close() } catch (_: IOException) { /* Open a fresh manifest connection. */ }
            val refreshed = dataSpec.withUri(session.originUrl.toUri())
            requireHttp(refreshed.uri)
            val length = delegate.open(refreshed)
            delegate.uri?.let { session.refreshed(it.toString()) }
            return length
        }
    }
    private fun requireHttp(uri: Uri) {
        try { requireAllowedStreamScheme(uri.scheme) }
        catch (_: ProviderException) { throw InvalidStreamSchemeException() }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)
    override fun getUri(): Uri? = delegate.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders
    override fun close() = delegate.close()
}
