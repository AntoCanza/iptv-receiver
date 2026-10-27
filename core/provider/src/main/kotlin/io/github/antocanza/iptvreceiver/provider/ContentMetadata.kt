package io.github.antocanza.iptvreceiver.provider

import io.github.antocanza.iptvreceiver.model.ContentMetadata
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

internal fun Map<String, JsonElement>.metadataText(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim()
        ?.takeUnless { it.isBlank() || it.lowercase(Locale.ROOT) in EMPTY_VALUES }
}

internal fun contentMetadata(
    values: Map<String, JsonElement>,
    connection: ProviderConnection,
    fallback: ContentMetadata = ContentMetadata(),
): ContentMetadata {
    val release = values.metadataText("release_date", "releasedate", "releaseDate")
    val year = values.metadataText("year")?.toIntOrNull()?.takeIf { it in 1800..2199 }
        ?: release?.takeIf { it.matches(RELEASE_DATE) }
            ?.take(4)?.toIntOrNull()?.takeIf { it in 1800..2199 }
    val rating = values.metadataText("rating")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 10 }
        ?: values.metadataText("rating_5based")?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0 && it <= 5 }?.times(2)
    val genres = when (val genre = values["genre"]) {
        is JsonArray -> genre.mapNotNull { (it as? JsonPrimitive)?.takeUnless { value -> value is JsonNull }?.content }
        else -> listOfNotNull(values.metadataText("genre"))
    }.map(String::trim).filter { it.lowercase(Locale.ROOT) !in EMPTY_VALUES }
        .flatMap { it.split(GENRE_SEPARATOR) }.map(String::trim)
        .filter { it.isNotEmpty() && it.lowercase(Locale.ROOT) !in EMPTY_VALUES && it != "0" }
        .distinctBy { it.lowercase(Locale.ROOT) }
    val backdrops = artworkUrls(values, connection, listOf("backdrop_path", "backdrop"))
    return ContentMetadata(
        year = year ?: fallback.year,
        rating = rating ?: fallback.rating,
        genres = genres.ifEmpty { fallback.genres },
        cast = values.metadataText("cast", "actors") ?: fallback.cast,
        director = values.metadataText("director") ?: fallback.director,
        country = values.metadataText("country") ?: fallback.country,
        durationSeconds = durationSeconds(values) ?: fallback.durationSeconds,
        trailerUrl = trailerUrl(values.metadataText("youtube_trailer", "trailer_url")) ?: fallback.trailerUrl,
        backdropUrls = backdrops.ifEmpty { fallback.backdropUrls },
    )
}

internal fun durationSeconds(values: Map<String, JsonElement>): Long? {
    values.metadataText("duration_secs")?.toLongOrNull()?.takeIf { it in 1..MAX_DURATION_SECONDS }?.let { return it }
    val duration = values.metadataText("duration")
    if (duration != null && duration.matches(DURATION)) {
        val parts = duration.split(':').map(String::toLong)
        val seconds = parts[0] * 3600 + parts[1] * 60 + parts[2]
        if (seconds in 1..MAX_DURATION_SECONDS) return seconds
    }
    return values.metadataText("episode_run_time", "runtime")?.toLongOrNull()
        ?.takeIf { it in 1..MAX_DURATION_SECONDS / 60 }?.times(60)
}

private fun trailerUrl(value: String?): String? {
    if (value == null) return null
    if (value.matches(YOUTUBE_ID)) return "https://www.youtube.com/watch?v=$value"
    val url = value.toHttpUrlOrNull() ?: return null
    // Trailers open only after a tap, in the user's external browser/video app.
    return url.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()
}

// Reject corrupt/overflowing durations before they reach formatting or sorting.
private const val MAX_DURATION_SECONDS = 365L * 24 * 60 * 60
private val EMPTY_VALUES = setOf("null", "n/a", "none", "-")
private val RELEASE_DATE = Regex("[0-9]{4}(?:-[0-9]{2}-[0-9]{2})?(?:[ T].*)?")
private val GENRE_SEPARATOR = Regex("[,/|]")
private val DURATION = Regex("[0-9]{1,4}:[0-5][0-9]:[0-5][0-9]")
private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")
