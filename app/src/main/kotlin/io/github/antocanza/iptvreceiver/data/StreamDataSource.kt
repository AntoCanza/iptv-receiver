package io.github.antocanza.iptvreceiver.data

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import io.github.antocanza.iptvreceiver.model.ProviderException
import io.github.antocanza.iptvreceiver.model.requireAllowedStreamScheme
import java.io.IOException

/** No URI is included in this exception because stream paths may contain account secrets. */
class InsecureStreamException : IOException("Stream security policy blocked unencrypted playback.")

@androidx.annotation.OptIn(UnstableApi::class)
class StreamDataSourceFactory(
    private val delegate: DataSource.Factory,
    private val allowHttp: Boolean,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = GuardedStreamDataSource(delegate.createDataSource(), allowHttp)
}

@androidx.annotation.OptIn(UnstableApi::class)
private class GuardedStreamDataSource(private val delegate: DataSource, private val allowHttp: Boolean) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) = delegate.addTransferListener(transferListener)
    override fun open(dataSpec: DataSpec): Long {
        try { requireAllowedStreamScheme(dataSpec.uri.scheme, allowHttp) }
        catch (_: ProviderException) { throw InsecureStreamException() }
        return try { delegate.open(dataSpec) } catch (error: HttpDataSource.InvalidResponseCodeException) {
            val insecureRedirect = error.responseCode in 300..399 && error.headerFields.entries.any { (key, values) ->
                key.equals("Location", ignoreCase = true) && values.any { it.toUri().scheme.equals("http", ignoreCase = true) }
            }
            if (insecureRedirect && !allowHttp) throw InsecureStreamException()
            throw error
        }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)
    override fun getUri(): Uri? = delegate.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders
    override fun close() = delegate.close()
}
