package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.ProviderException
import io.github.antocanza.iptvreceiver.model.requireAllowedStreamScheme
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

internal fun artworkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(20, TimeUnit.SECONDS)
    .followSslRedirects(true)
    .addNetworkInterceptor { chain ->
        try { requireAllowedStreamScheme(chain.request().url.scheme) }
        catch (_: ProviderException) { throw IOException("Artwork transport policy blocked this request.") }
        chain.proceed(chain.request())
    }.build()
