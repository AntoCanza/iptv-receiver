package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.model.CatalogItem
import java.util.Locale

internal enum class CatalogSort { PROVIDER, TITLE, NEWEST, RATING, RELEVANCE }

/** Missing values sort last. Default browsing preserves the provider's own order. */
internal fun sortCatalog(items: List<CatalogItem>, sort: CatalogSort, query: String = ""): List<CatalogItem> = when (sort) {
    CatalogSort.RELEVANCE -> items.sortedBy { searchRank(it.title, query) }
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

internal fun searchRank(title: String, query: String): Int {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return 0
    val name = title.trim().lowercase(Locale.ROOT)
    return when {
        name == needle -> 0
        name.startsWith(needle) -> 1
        name.split(Regex("[\\s:–—-]+" )).any { it == needle } -> 2
        name.contains(needle) -> 3
        else -> 4
    }
}
