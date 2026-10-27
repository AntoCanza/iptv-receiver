package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import io.github.antocanza.iptvreceiver.data.InvalidStreamSchemeException
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Retry short outages inside Media3's loader, keeping already-buffered live samples playable. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class LiveLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(10) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
        return retryDelayFor(loadErrorInfo.exception, loadErrorInfo.errorCount, super.getRetryDelayMsFor(loadErrorInfo))
    }

    internal fun retryDelayFor(error: IOException, errorCount: Int, defaultDelay: Long): Long {
        val causes = generateSequence(error as Throwable) { it.cause }.toList()
        if (causes.any { it is InvalidStreamSchemeException || it is SSLHandshakeException ||
            it is SSLPeerUnverifiedException || it is CertificateException }) return C.TIME_UNSET
        val response = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        if (response != null && response.responseCode !in setOf(404, 408, 410, 429) && response.responseCode !in 500..599) {
            return C.TIME_UNSET
        }
        if (errorCount >= 10) return C.TIME_UNSET
        return if (defaultDelay == C.TIME_UNSET) C.TIME_UNSET else minOf(defaultDelay, 1_500L)
    }
}
