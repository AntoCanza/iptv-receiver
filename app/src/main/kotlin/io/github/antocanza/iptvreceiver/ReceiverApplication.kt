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
import io.github.antocanza.iptvreceiver.data.FileWatchHistoryStore
import io.github.antocanza.iptvreceiver.data.ParentalControls
import io.github.antocanza.iptvreceiver.data.ParentalCatalogRepository
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.provider.XtreamCatalogRepository

class ReceiverApplication : Application() {
    override fun onCreate() { super.onCreate(); scheduleCacheCleanup(this) }
    val unfilteredRepository: CatalogRepository by lazy { XtreamCatalogRepository() }
    val parentalControls by lazy { ParentalControls(filesDir.resolve("parental-controls/settings.bin")) }
    val repository: CatalogRepository by lazy { ParentalCatalogRepository(unfilteredRepository, parentalControls) }
    val connectionStore by lazy { ConnectionStore(this) }
    val watchHistoryStore by lazy { FileWatchHistoryStore(filesDir.resolve("watch-history")) }
    val artworkLoader by lazy { createArtworkLoader() }

    fun clearArtworkDiskCaches() {
        artworkLoader.diskCache?.clear()
    }

    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    private fun createArtworkLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { artworkHttpClient() },
                cacheStrategy = { CacheControlCacheStrategy() }))
            add(SvgDecoder.Factory())
        }
        .memoryCache { MemoryCache.Builder().maxSizeBytes(12L * 1024 * 1024).build() }
        .diskCache {
            DiskCache.Builder().directory(cacheDir.resolve("artwork-compatible").toOkioPath())
                .maxSizeBytes(50L * 1024 * 1024).build()
        }.build()
}
