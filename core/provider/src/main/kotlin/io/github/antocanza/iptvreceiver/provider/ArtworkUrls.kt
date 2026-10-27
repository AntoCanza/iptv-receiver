package io.github.antocanza.iptvreceiver.provider

import io.github.antocanza.iptvreceiver.model.ProviderConnection
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun artworkUrls(
    values: Map<String, JsonElement>,
    connection: ProviderConnection,
    keys: List<String> = listOf("stream_icon", "cover", "movie_image", "cover_big", "poster"),
): List<String> {
    val base = connection.serverUrl.trim().trimEnd('/').plus("/").toHttpUrlOrNull() ?: return emptyList()
    return keys.flatMap { key ->
        when (val value = values[key]) {
            is JsonArray -> value.toList()
            else -> listOfNotNull(value)
        }
    }.flatMap { value ->
        val raw = (value as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim().orEmpty()
        if (raw.isBlank() || raw.lowercase() in setOf("null", "n/a", "none", "0", "-")) return@flatMap emptyList()
        val relative = raw.startsWith("/") || raw.startsWith("./") || raw.startsWith("../")
        val url = raw.toHttpUrlOrNull() ?: if (relative) base.resolve(raw) else null
        if (url == null || url.username.isNotEmpty() || url.password.isNotEmpty()) return@flatMap emptyList()
        if (url.isHttps) listOf(url.toString()) else {
            val upgraded = url.newBuilder().scheme("https").apply { if (url.port == 80) port(443) }.build().toString()
            listOf(upgraded, url.toString())
        }
    }.distinct()
}
