package io.github.antocanza.iptvreceiver.model

/** Deliberately not a data class: generated toString must never expose credentials. */
class ProviderConnection(
    val name: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val allowHttpStreams: Boolean = false,
) {
    override fun toString(): String = "ProviderConnection([redacted])"
}

enum class ContentType(val apiType: String, val streamPath: String) {
    LIVE("live", "live"),
    MOVIE("vod", "movie"),
    SERIES("series", "series"),
}

data class Account(val status: String, val expiresAtSeconds: Long?, val liveFormats: List<String>)

data class Category(val id: String, val name: String, val parentId: String?)

data class CatalogItem(
    val id: String,
    val title: String,
    val type: ContentType,
    val imageUrl: String? = null,
    val extension: String? = null,
)

data class MovieDetails(val item: CatalogItem, val description: String)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val extension: String,
)

data class SeriesDetails(val title: String, val description: String, val episodes: List<Episode>)

/** Contains a credential-bearing URL; never log, serialize, or use as a navigation argument. */
class PlaybackSource(val url: String, val title: String, val isLive: Boolean, val allowHttpStreams: Boolean = false) {
    override fun toString(): String = "PlaybackSource([redacted])"
}

/** Keeps UI independent of the provider's HTTP and JSON implementation. */
interface CatalogRepository {
    suspend fun authenticate(connection: ProviderConnection): Account
    suspend fun categories(connection: ProviderConnection, type: ContentType): List<Category>
    suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String): List<CatalogItem>
    suspend fun movie(connection: ProviderConnection, item: CatalogItem): MovieDetails
    suspend fun series(connection: ProviderConnection, item: CatalogItem): SeriesDetails
    fun stream(connection: ProviderConnection, item: CatalogItem, liveFormat: String = "m3u8"): PlaybackSource
    fun episodeStream(connection: ProviderConnection, episode: Episode): PlaybackSource
}

enum class FailureReason {
    INVALID_SERVER, HTTPS_REQUIRED, CREDENTIALS_REQUIRED, AUTHENTICATION_FAILED,
    INACTIVE_ACCOUNT, EXPIRED_ACCOUNT, SELECT_EPISODE, INVALID_STREAM,
    UNREACHABLE, REDIRECTED, ACCESS_DENIED, UNAVAILABLE, EMPTY_RESPONSE,
    UNSUPPORTED_RESPONSE, TOO_LARGE, RESPONSE_UNREADABLE, SAVE_FAILED,
    REMOVE_FAILED, INSECURE_STREAM, UNKNOWN,
}

/** Stable error codes are localized by the UI and contain no raw provider values. */
class ProviderException(val reason: FailureReason) : Exception(reason.name)

/** Applied before every manifest/segment request, not just the initial stream URL. */
fun requireAllowedStreamScheme(scheme: String?, allowHttp: Boolean) {
    when (scheme?.lowercase()) {
        "https" -> Unit
        "http" -> if (!allowHttp) throw ProviderException(FailureReason.INSECURE_STREAM)
        else -> throw ProviderException(FailureReason.INVALID_STREAM)
    }
}
