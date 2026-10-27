package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.model.CatalogItem
import java.util.Locale

internal enum class CatalogSort { PROVIDER, TITLE, NEWEST, RATING }

/** Missing values sort last. Default browsing preserves the provider's own order. */
internal fun sortCatalog(items: List<CatalogItem>, sort: CatalogSort): List<CatalogItem> = when (sort) {
    CatalogSort.PROVIDER -> items
    CatalogSort.TITLE -> items.sortedBy { it.title.lowercase(Locale.ROOT) }
    CatalogSort.NEWEST -> items.sortedByDescending { it.addedAtSeconds ?: Long.MIN_VALUE }
    CatalogSort.RATING -> items.sortedByDescending { it.metadata.rating ?: Double.NEGATIVE_INFINITY }
}

internal fun CatalogItem.matchesCatalog(query: String, genre: String?): Boolean {
    if (genre != null && metadata.genres.none { it.equals(genre, ignoreCase = true) }) return false
    return query.isBlank() || title.contains(query, ignoreCase = true) ||
        metadata.genres.any { it.contains(query, ignoreCase = true) } ||
        metadata.year?.toString()?.contains(query) == true
}
