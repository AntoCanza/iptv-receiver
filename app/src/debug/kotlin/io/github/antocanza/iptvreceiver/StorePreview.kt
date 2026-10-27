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
        return DemoCatalog(intent.getIntExtra("preview_fixture_port", 0).takeIf { it in 1..65535 }) to store
    }
}

private class DemoCatalog(private val fixturePort: Int? = null) : CatalogRepository {
    override suspend fun authenticate(connection: ProviderConnection) = Account("Active",
        System.currentTimeMillis() / 1000 + 30 * 24 * 60 * 60, listOf("m3u8", "ts"), activeConnections = 0, maxConnections = 2,
        serverInfo = ServerInfo("Europe/Rome", System.currentTimeMillis() / 1000))
    override suspend fun categories(connection: ProviderConnection, type: ContentType) = listOf(
        Category("featured", when (type) { ContentType.LIVE -> "Discover"; ContentType.MOVIE -> "Featured movies"; ContentType.SERIES -> "Featured series" }, null),
        Category("nature", "Nature & discovery", null), Category("space", "Space & science", null))
    override suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String?): List<CatalogItem> {
        val names = listOf("Ocean Lines", "Quiet Orbit", "Forest Light", "Golden Hour", "Beyond the Ridge", "Night Signals")
        return names.mapIndexed { index, title -> CatalogItem("demo-$index", title, type,
            "file:///android_asset/demo/poster-$index.svg", "mp4",
            metadata = if (type == ContentType.LIVE) ContentMetadata() else ContentMetadata(
                year = 2025 - index % 3, rating = 8.4 - index * 0.3,
                genres = listOf(if (index % 2 == 0) "Documentary" else "Adventure"),
                cast = "Alex Rivers, Morgan Vale", director = "Jamie Lake", country = "Italy",
                durationSeconds = 6420, backdropUrls = listOf("file:///android_asset/demo/poster-$index.svg")),
            addedAtSeconds = 1700000000L + index * 86400) }
    }
    override suspend fun movie(connection: ProviderConnection, item: CatalogItem) =
        MovieDetails(item, "Follow a small research crew as they explore an unfamiliar landscape, discover its hidden rhythms, and learn to see their surroundings in a new way. Their journey brings unexpected encounters and quiet moments of discovery. This original illustrative title belongs to the offline demo catalog. Your provider supplies the titles, artwork and descriptions in your own library.")
    override suspend fun series(connection: ProviderConnection, item: CatalogItem) = SeriesDetails(item.title,
        "An illustrative series from the offline demo catalog.", (1..8).map { Episode("demo-$it", "Chapter ${(it - 1) % 4 + 1}",
            (it - 1) / 4 + 1, (it - 1) % 4 + 1, "mp4", description = "The crew follows a new clue into an unexpected landscape.",
            imageUrls = listOf("file:///android_asset/demo/poster-${(it - 1) % 6}.svg"), durationSeconds = 2820) },
        metadata = item.metadata.copy(durationSeconds = 2820), imageUrls = item.artworkUrls,
        seasons = listOf(Season(1, "Season 1", "The journey begins."), Season(2, "Season 2", "New horizons.")))
    override fun stream(connection: ProviderConnection, item: CatalogItem, liveFormat: String): PlaybackSource = fixturePort?.let {
        PlaybackSource("http://127.0.0.1:$it/fixture.wav", item.title, item.type == ContentType.LIVE)
    } ?: throw ProviderException(FailureReason.UNAVAILABLE)
    override fun episodeStream(connection: ProviderConnection, episode: Episode): PlaybackSource = fixturePort?.let {
        PlaybackSource("http://127.0.0.1:$it/fixture.wav", episode.title, false)
    } ?: throw ProviderException(FailureReason.UNAVAILABLE)
}
