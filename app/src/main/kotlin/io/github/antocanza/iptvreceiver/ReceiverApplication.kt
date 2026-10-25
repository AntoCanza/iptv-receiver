package io.github.antocanza.iptvreceiver

import android.app.Application
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.svg.SvgDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.network.cachecontrol.CacheControlCacheStrategy
import okio.Path.Companion.toOkioPath
import io.github.antocanza.iptvreceiver.data.artworkHttpClient
import io.github.antocanza.iptvreceiver.data.scheduleCacheCleanup
import io.github.antocanza.iptvreceiver.data.ConnectionStore
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.provider.XtreamCatalogRepository

class ReceiverApplication : Application() {
    override fun onCreate() { super.onCreate(); scheduleCacheCleanup(this) }
    val repository: CatalogRepository by lazy { XtreamCatalogRepository() }
    val connectionStore by lazy { ConnectionStore(this) }
    private val secureArtwork by lazy { artworkLoader(false) }
    private val compatibleArtwork by lazy { artworkLoader(true) }

    fun artworkLoaderFor(allowHttp: Boolean): ImageLoader = if (allowHttp) compatibleArtwork else secureArtwork

    fun clearArtworkDiskCaches() {
        secureArtwork.diskCache?.clear()
        compatibleArtwork.diskCache?.clear()
    }

    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    private fun artworkLoader(allowHttp: Boolean): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { artworkHttpClient(allowHttp) },
                cacheStrategy = { CacheControlCacheStrategy() }))
            add(SvgDecoder.Factory())
        }
        .memoryCache { MemoryCache.Builder().maxSizeBytes(12L * 1024 * 1024).build() }
        .diskCache {
            DiskCache.Builder().directory(cacheDir.resolve(if (allowHttp) "artwork-compatible" else "artwork-https").toOkioPath())
                .maxSizeBytes(50L * 1024 * 1024).build()
        }.build()
}
