package io.github.antocanza.iptvreceiver.model

/** Deliberately not a data class: generated toString must never expose credentials. */
class ProviderConnection(
    val name: String,
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    override fun toString(): String = "ProviderConnection([redacted])"
}

enum class ContentType(val apiType: String, val streamPath: String) {
    LIVE("live", "live"),
    MOVIE("vod", "movie"),
    SERIES("series", "series"),
}

data class Account(
    val status: String,
    val expiresAtSeconds: Long?,
    val liveFormats: List<String>,
    val activeConnections: Int? = null,
    val maxConnections: Int? = null,
    val isTrial: Boolean? = null,
    val serverInfo: ServerInfo = ServerInfo(),
)

data class ServerInfo(val timezone: String? = null, val timeAtSignInSeconds: Long? = null)

data class Category(val id: String, val name: String, val parentId: String?, val isAdult: Boolean = false)

/** Optional provider metadata; absent values must not become invented badges or filters. */
data class ContentMetadata(
    val year: Int? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val cast: String? = null,
    val director: String? = null,
    val country: String? = null,
    val durationSeconds: Long? = null,
    val trailerUrl: String? = null,
    val backdropUrls: List<String> = emptyList(),
)

data class CatalogItem(
    val id: String,
    val title: String,
    val type: ContentType,
    val imageUrl: String? = null,
    val extension: String? = null,
    val imageFallbackUrls: List<String> = emptyList(),
    val metadata: ContentMetadata = ContentMetadata(),
    val addedAtSeconds: Long? = null,
    val categoryId: String? = null,
    val isAdult: Boolean = false,
    val catchupDays: Int = 0,
)

val CatalogItem.artworkUrls: List<String> get() = (listOfNotNull(imageUrl) + imageFallbackUrls).distinct()

data class MovieDetails(val item: CatalogItem, val description: String)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val extension: String,
    val description: String = "",
    val imageUrls: List<String> = emptyList(),
    val durationSeconds: Long? = null,
)

data class Season(
    val number: Int,
    val name: String = "",
    val description: String = "",
)

data class SeriesDetails(
    val title: String,
    val description: String,
    val episodes: List<Episode>,
    val metadata: ContentMetadata = ContentMetadata(),
    val imageUrls: List<String> = emptyList(),
    val seasons: List<Season> = emptyList(),
)

/** Contains a credential-bearing URL; never log, serialize, or use as a navigation argument. */
class PlaybackSource @JvmOverloads constructor(val url: String, val title: String, val isLive: Boolean, val durationMs: Long? = null) {
    override fun toString(): String = "PlaybackSource([redacted])"
}

/** Keeps UI independent of the provider's HTTP and JSON implementation. */
interface CatalogRepository {
    suspend fun authenticate(connection: ProviderConnection): Account
    suspend fun categories(connection: ProviderConnection, type: ContentType): List<Category>
    /** A null category selects the complete catalog for this content type. */
    suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String?): List<CatalogItem>
    suspend fun movie(connection: ProviderConnection, item: CatalogItem): MovieDetails
    suspend fun series(connection: ProviderConnection, item: CatalogItem): SeriesDetails
    fun stream(connection: ProviderConnection, item: CatalogItem, liveFormat: String = "m3u8"): PlaybackSource
    fun episodeStream(connection: ProviderConnection, episode: Episode): PlaybackSource
    suspend fun guide(connection: ProviderConnection, item: CatalogItem): List<Programme> = emptyList()
    fun catchupStream(connection: ProviderConnection, item: CatalogItem, programme: Programme): PlaybackSource =
        throw ProviderException(FailureReason.UNAVAILABLE)
}

data class Programme(
    val id: String,
    val title: String,
    val description: String,
    val startsAtSeconds: Long,
    val endsAtSeconds: Long,
    val archiveAvailable: Boolean,
    /** Provider-local clock used by the Xtream archive endpoint, without a timezone conversion. */
    val providerStartLocal: String?,
) {
    fun canReplay(channel: CatalogItem, nowSeconds: Long): Boolean = channel.type == ContentType.LIVE &&
        channel.catchupDays > 0 && archiveAvailable && providerStartLocal != null && endsAtSeconds <= nowSeconds &&
        startsAtSeconds >= nowSeconds - channel.catchupDays * 86_400L && endsAtSeconds > startsAtSeconds &&
        endsAtSeconds - startsAtSeconds <= 24 * 3600
}

enum class FailureReason {
    INVALID_SERVER, CREDENTIALS_REQUIRED, AUTHENTICATION_FAILED, LOGIN_FAILED,
    INACTIVE_ACCOUNT, EXPIRED_ACCOUNT, SELECT_EPISODE, INVALID_STREAM,
    UNREACHABLE, REDIRECTED, ACCESS_DENIED, UNAVAILABLE, EMPTY_RESPONSE,
    UNSUPPORTED_RESPONSE, TOO_LARGE, RESPONSE_UNREADABLE, SAVE_FAILED,
    REMOVE_FAILED, PARENTAL_BLOCKED, UNKNOWN,
}

/** Stable error codes are localized by the UI and contain no raw provider values. */
class ProviderException(val reason: FailureReason) : Exception(reason.name)

/** Applied before every manifest/segment request, not just the initial stream URL. */
fun requireAllowedStreamScheme(scheme: String?) {
    when (scheme?.lowercase()) {
        "https", "http" -> Unit
        else -> throw ProviderException(FailureReason.INVALID_STREAM)
    }
}
