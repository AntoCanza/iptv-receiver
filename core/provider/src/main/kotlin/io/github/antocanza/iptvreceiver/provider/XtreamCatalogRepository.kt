package io.github.antocanza.iptvreceiver.provider

import io.github.antocanza.iptvreceiver.model.Account
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.Category
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.Episode
import io.github.antocanza.iptvreceiver.model.MovieDetails
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.model.ProviderException
import io.github.antocanza.iptvreceiver.model.SeriesDetails
import io.github.antocanza.iptvreceiver.model.artworkUrls
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class XtreamCatalogRepository internal constructor(private val transport: JsonTransport) : CatalogRepository {
    constructor() : this(HttpJsonTransport())

    override suspend fun authenticate(connection: ProviderConnection): Account {
        val result = request(connection).asObject()
        val user = result["user_info"].asObject()
        if (user.text("auth") != "1") throw ProviderException(FailureReason.AUTHENTICATION_FAILED)
        val status = user.text("status")
        if (!status.equals("Active", ignoreCase = true)) {
            throw ProviderException(FailureReason.INACTIVE_ACCOUNT)
        }
        val expiry = user.text("exp_date").toLongOrNull()?.takeIf { it > 0 }
        if (expiry != null && expiry <= System.currentTimeMillis() / 1000) {
            throw ProviderException(FailureReason.EXPIRED_ACCOUNT)
        }
        val formats = (user["allowed_output_formats"] as? JsonArray).orEmpty()
            .mapNotNull { it.scalar().takeIf { value -> value in setOf("m3u8", "ts") } }
        return Account(status, expiry, formats)
    }

    override suspend fun categories(connection: ProviderConnection, type: ContentType): List<Category> =
        request(connection, "get_${type.apiType}_categories").asArray().mapNotNull { element ->
            val item = element.asObject()
            val id = item.text("category_id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            Category(id, item.text("category_name"),
                item.text("parent_id").takeUnless { it.isBlank() || it == "0" || it == id })
        }.distinctBy(Category::id)

    override suspend fun items(
        connection: ProviderConnection,
        type: ContentType,
        categoryId: String?,
    ): List<CatalogItem> {
        val action = if (type == ContentType.SERIES) "get_series" else "get_${type.apiType}_streams"
        val url = requestUrl(connection, action, categoryId?.let { "category_id" to it })
        return transport.loadCatalog(url) { element ->
            val item = element.asObject()
            val id = item.text(if (type == ContentType.SERIES) "series_id" else "stream_id")
                .takeIf { it.matches(Regex("[0-9]+")) } ?: return@loadCatalog null
            val artwork = artworkUrls(item, connection)
            CatalogItem(
                id = id,
                title = item.text("name"),
                type = type,
                imageUrl = artwork.firstOrNull(),
                extension = item.text("container_extension").takeIf(String::isNotBlank),
                imageFallbackUrls = artwork.drop(1),
            )
        }
    }

    override suspend fun movie(connection: ProviderConnection, item: CatalogItem): MovieDetails {
        val result = request(connection, "get_vod_info", "vod_id" to item.id).asObject()
        val info = (result["info"] as? JsonObject).orEmpty()
        val movie = (result["movie_data"] as? JsonObject).orEmpty()
        val artwork = (item.artworkUrls + artworkUrls(info, connection) + artworkUrls(movie, connection)).distinct()
        return MovieDetails(
            item.copy(extension = movie.text("container_extension").ifBlank { item.extension.orEmpty() },
                imageUrl = artwork.firstOrNull(), imageFallbackUrls = artwork.drop(1)),
            info.text("plot").ifBlank { info.text("description") },
        )
    }

    override suspend fun series(connection: ProviderConnection, item: CatalogItem): SeriesDetails {
        val result = request(connection, "get_series_info", "series_id" to item.id).asObject()
        val info = (result["info"] as? JsonObject).orEmpty()
        val groups = result["episodes"]
        val entries = when (groups) {
            is JsonObject -> groups.entries.map { it.key to it.value }
            is JsonArray -> groups.mapIndexed { index, value -> index.toString() to value }
            else -> emptyList()
        }
        val episodes = entries.flatMap { (seasonKey, group) ->
            (group as? JsonArray).orEmpty().mapNotNull { element ->
                val episode = element.asObject()
                val id = episode.text("id").takeIf { it.matches(Regex("[0-9]+")) } ?: return@mapNotNull null
                Episode(
                    id,
                    episode.text("title"),
                    episode.text("season").toIntOrNull() ?: seasonKey.toIntOrNull() ?: 0,
                    episode.text("episode_num").toIntOrNull() ?: 0,
                    episode.text("container_extension"),
                )
            }
        }.distinctBy(Episode::id).sortedWith(compareBy(Episode::season, Episode::number))
        return SeriesDetails(info.text("name").ifBlank { item.title }, info.text("plot"), episodes)
    }

    override fun stream(connection: ProviderConnection, item: CatalogItem, liveFormat: String): PlaybackSource {
        if (item.type == ContentType.SERIES) throw ProviderException(FailureReason.SELECT_EPISODE)
        val extension = if (item.type == ContentType.LIVE) liveFormat else item.extension.orEmpty()
        return source(connection, item.type, item.id, extension, item.title)
    }

    override fun episodeStream(connection: ProviderConnection, episode: Episode): PlaybackSource =
        source(connection, ContentType.SERIES, episode.id, episode.extension, episode.title)

    private fun source(
        connection: ProviderConnection,
        type: ContentType,
        id: String,
        extension: String,
        title: String,
    ): PlaybackSource {
        if (!id.matches(Regex("[0-9]+")) || !extension.matches(Regex("[A-Za-z0-9]{1,8}"))) {
            throw ProviderException(FailureReason.INVALID_STREAM)
        }
        val url = baseUrl(connection).newBuilder()
            .addPathSegment(type.streamPath)
            .addPathSegment(connection.username)
            .addPathSegment(connection.password)
            .addPathSegment("$id.$extension")
            .build()
        return PlaybackSource(url.toString(), title, type == ContentType.LIVE, connection.allowHttpStreams)
    }

    private suspend fun request(
        connection: ProviderConnection,
        action: String? = null,
        parameter: Pair<String, String>? = null,
    ): JsonElement = transport.load(requestUrl(connection, action, parameter))

    private fun requestUrl(
        connection: ProviderConnection,
        action: String? = null,
        parameter: Pair<String, String>? = null,
    ): HttpUrl {
        val builder = baseUrl(connection).newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", connection.username)
            .addQueryParameter("password", connection.password)
        action?.let { builder.addQueryParameter("action", it) }
        parameter?.let { builder.addQueryParameter(it.first, it.second) }
        return builder.build()
    }
}

private fun baseUrl(connection: ProviderConnection): HttpUrl {
    val url = connection.serverUrl.trim().trimEnd('/').toHttpUrlOrNull()
        ?: throw ProviderException(FailureReason.INVALID_SERVER)
    if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) {
        throw ProviderException(FailureReason.HTTPS_REQUIRED)
    }
    if (connection.username.isBlank() || connection.password.isBlank()) {
        throw ProviderException(FailureReason.CREDENTIALS_REQUIRED)
    }
    return url
}

internal fun interface JsonTransport {
    suspend fun load(url: HttpUrl): JsonElement

    suspend fun loadCatalog(url: HttpUrl, mapItem: (JsonElement) -> CatalogItem?): List<CatalogItem> =
        load(url).asArray().mapNotNull(mapItem).distinctBy(CatalogItem::id)
}

internal class HttpJsonTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
    private val fullCatalogLimitBytes: Long = 128L * 1024 * 1024,
) : JsonTransport {
    override suspend fun load(url: HttpUrl): JsonElement = fetch(url) { readJson(it) }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun loadCatalog(url: HttpUrl, mapItem: (JsonElement) -> CatalogItem?): List<CatalogItem> = fetch(url) { response ->
        val limit = if (url.queryParameter("category_id") == null) fullCatalogLimitBytes else JSON_LIMIT_BYTES
        bounded(response, limit).use { source ->
            while (source.request(1) && source.buffer[0].toInt().toChar() in " \r\n\t") source.skip(1)
            if (source.request(1) && source.buffer[0].toInt().toChar() == '[') {
                try {
                    // Discard each raw record after mapping it; retain only compact catalog items.
                    Json.decodeFromStream(CatalogDeserializer(mapItem), source.inputStream())
                } catch (_: IllegalArgumentException) {
                    throw ProviderException(FailureReason.UNSUPPORTED_RESPONSE)
                }
            } else {
                // Some providers represent an empty catalog as {}.
                readJson(source).asArray().mapNotNull(mapItem).distinctBy(CatalogItem::id)
            }
        }
    }

    private suspend fun <T> fetch(url: HttpUrl, parse: (BufferedSource) -> T): T = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "IPTVReceiver/0.1").build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(
                    ProviderException(FailureReason.UNREACHABLE),
                )
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (it.code in 300..399) throw ProviderException(FailureReason.REDIRECTED)
                        if (it.code == 401 || it.code == 403) throw ProviderException(FailureReason.ACCESS_DENIED)
                        if (!it.isSuccessful) throw ProviderException(FailureReason.UNAVAILABLE)
                        // Never include a raw server response or request URL in an exception.
                        parse(it.body.source())
                    }
                }
                if (continuation.isActive) continuation.resumeWith(result.recoverCatching {
                    throw if (it is ProviderException) it else ProviderException(FailureReason.RESPONSE_UNREADABLE)
                })
            }
        })
    }

    private fun readJson(source: BufferedSource): JsonElement {
        val text = bounded(source, JSON_LIMIT_BYTES).use { it.readUtf8() }
        return try {
            Json.parseToJsonElement(text).also { element ->
                if (element !is JsonObject && element !is JsonArray) {
                    throw ProviderException(FailureReason.UNSUPPORTED_RESPONSE)
                }
            }
        } catch (_: IllegalArgumentException) {
            throw ProviderException(FailureReason.UNSUPPORTED_RESPONSE)
        }
    }

    private fun bounded(source: BufferedSource, limit: Long): BufferedSource = object : ForwardingSource(source) {
        private var bytesRead = 0L
        override fun read(sink: Buffer, byteCount: Long): Long {
            val count = super.read(sink, minOf(byteCount, limit - bytesRead + 1))
            if (count > 0) bytesRead += count
            if (bytesRead > limit) throw ProviderException(FailureReason.TOO_LARGE)
            return count
        }
    }.buffer()

    private companion object {
        const val JSON_LIMIT_BYTES = 20L * 1024 * 1024
    }
}

/** Streams the JSON array, validates its closing delimiter, and retains only mapped, unique items. */
private class CatalogDeserializer(private val mapItem: (JsonElement) -> CatalogItem?) : DeserializationStrategy<List<CatalogItem>> {
    override val descriptor = ListSerializer(JsonElement.serializer()).descriptor

    override fun deserialize(decoder: Decoder): List<CatalogItem> {
        val items = linkedMapOf<String, CatalogItem>()
        decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                val element = decodeSerializableElement(descriptor, index, JsonElement.serializer()).asObject()
                val item = mapItem(element) ?: continue
                items.putIfAbsent(item.id, item)
            }
        }
        return items.values.toList()
    }
}

private fun JsonElement?.asObject(): JsonObject = this as? JsonObject
    ?: throw ProviderException(FailureReason.UNSUPPORTED_RESPONSE)

private fun JsonElement.asArray(): JsonArray = when (this) {
    is JsonArray -> this
    is JsonObject -> if (isEmpty()) JsonArray(emptyList()) else throw ProviderException(FailureReason.UNSUPPORTED_RESPONSE)
    else -> throw ProviderException(FailureReason.UNSUPPORTED_RESPONSE)
}

private fun Map<String, JsonElement>.text(key: String): String = get(key).scalar()
private fun JsonElement?.scalar(): String = if (this is JsonPrimitive && this !is JsonNull) content else ""
