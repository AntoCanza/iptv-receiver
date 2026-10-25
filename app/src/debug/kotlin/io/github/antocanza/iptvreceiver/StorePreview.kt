package io.github.antocanza.iptvreceiver

import android.content.Intent
import io.github.antocanza.iptvreceiver.data.SavedConnectionStore
import io.github.antocanza.iptvreceiver.model.*

/** Synthetic, offline catalog for reproducible screenshots; excluded from release builds. */
internal object StorePreview {
    fun create(intent: Intent?): Pair<CatalogRepository, SavedConnectionStore>? {
        if (intent?.getBooleanExtra("store_preview", false) != true) return null
        val store = object : SavedConnectionStore {
            private var connection: ProviderConnection? = ProviderConnection("Demo catalog", "https://example.invalid", "demo", "demo")
            override fun load() = connection
            override fun save(connection: ProviderConnection) { this.connection = connection }
            override fun clear() { connection = null }
        }
        return DemoCatalog() to store
    }
}

private class DemoCatalog : CatalogRepository {
    override suspend fun authenticate(connection: ProviderConnection) = Account("Active", null, listOf("ts"))
    override suspend fun categories(connection: ProviderConnection, type: ContentType) = listOf(
        Category("featured", when (type) { ContentType.LIVE -> "Discover"; ContentType.MOVIE -> "Featured movies"; ContentType.SERIES -> "Featured series" }, null),
        Category("nature", "Nature & discovery", null), Category("space", "Space & science", null))
    override suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String?): List<CatalogItem> {
        val names = listOf("Ocean Lines", "Quiet Orbit", "Forest Light", "Golden Hour", "Beyond the Ridge", "Night Signals")
        return names.mapIndexed { index, title -> CatalogItem("demo-$index", title, type,
            "file:///android_asset/demo/poster-$index.svg", "mp4") }
    }
    override suspend fun movie(connection: ProviderConnection, item: CatalogItem) =
        MovieDetails(item, "An original illustrative title from the offline demo catalog. Your provider supplies the titles, artwork and descriptions in your own library.")
    override suspend fun series(connection: ProviderConnection, item: CatalogItem) = SeriesDetails(item.title,
        "An illustrative series from the offline demo catalog.", (1..4).map { Episode("demo-$it", "Chapter $it", 1, it, "mp4") })
    override fun stream(connection: ProviderConnection, item: CatalogItem, liveFormat: String): PlaybackSource =
        throw ProviderException(FailureReason.UNAVAILABLE)
    override fun episodeStream(connection: ProviderConnection, episode: Episode): PlaybackSource =
        throw ProviderException(FailureReason.UNAVAILABLE)
}
