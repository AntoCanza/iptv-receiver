package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentMetadata
import io.github.antocanza.iptvreceiver.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPresentationTest {
    private val first = CatalogItem("1", "Zebra", ContentType.MOVIE,
        metadata = ContentMetadata(year = 2024, rating = 8.2, genres = listOf("Drama"), cast = "Jane Sample", director = "Alex Example"),
        addedAtSeconds = 100)
    private val second = CatalogItem("2", "alpha", ContentType.MOVIE,
        metadata = ContentMetadata(year = 2025, rating = 6.5, genres = listOf("Adventure")), addedAtSeconds = 200)
    private val missing = CatalogItem("3", "Unrated", ContentType.MOVIE)
    private val items = listOf(missing, first, second)

    @Test fun `sorting preserves provider order by default and places missing metadata last`() {
        assertEquals(items, sortCatalog(items, CatalogSort.PROVIDER))
        assertEquals(listOf(second, missing, first), sortCatalog(items, CatalogSort.TITLE))
        assertEquals(listOf(second, first, missing), sortCatalog(items, CatalogSort.NEWEST))
        assertEquals(listOf(first, second, missing), sortCatalog(items, CatalogSort.RATING))
        assertEquals(items, listOf(missing, first, second))
    }

    @Test fun `equal ratings keep the provider order instead of shuffling results`() {
        val equal = second.copy(metadata = second.metadata.copy(rating = first.metadata.rating))
        assertEquals(listOf(equal, first, missing), sortCatalog(listOf(missing, equal, first), CatalogSort.RATING))
    }

    @Test fun `search matches available title year and genre without extra requests`() {
        for (query in listOf("zeb", "2024", "DRAMA")) assertTrue(first.matchesCatalog(query, null))
        assertTrue(first.matchesCatalog("", null))
        assertFalse(missing.matchesCatalog("2024", null))
        assertFalse(first.matchesCatalog("unrelated", null))
    }

    @Test fun `search puts an exact title before prefixes and partial matches with stable ties`() {
        val titles = listOf("My Friends", "Friends: Reunion", "Friends", "Best Friends", "FRIENDS")
            .mapIndexed { index, title -> CatalogItem(index.toString(), title, ContentType.SERIES) }
        assertEquals(listOf("Friends", "FRIENDS", "Friends: Reunion", "My Friends", "Best Friends"),
            sortCatalog(titles, CatalogSort.RELEVANCE, " friends ").map { it.title })
        assertEquals(titles, sortCatalog(titles, CatalogSort.PROVIDER, "Friends"))
        assertEquals(titles.sortedBy { it.title.lowercase(java.util.Locale.ROOT) },
            sortCatalog(titles, CatalogSort.TITLE, "Friends"))
    }

    @Test fun `genre filters combine with search and reject titles with unknown genres`() {
        assertTrue(first.matchesCatalog("Zebra", "drama"))
        assertFalse(first.matchesCatalog("Zebra", "Adventure"))
        assertFalse(second.matchesCatalog("Zebra", "Adventure"))
        assertFalse(missing.matchesCatalog("", "Drama"))
    }

    @Test fun `searching Dexter finds the series rather than unrelated titles whose cast has that name`() {
        val series = CatalogItem("4", "Dexter: New Blood", ContentType.SERIES)
        val unrelated = CatalogItem("5", "A different series", ContentType.SERIES,
            metadata = ContentMetadata(cast = "Dexter Sample", director = "Dexter Example"))
        assertEquals(listOf(series), listOf(unrelated, series).filter { it.matchesCatalog("dexter", null) })
        assertFalse(first.matchesCatalog("Jane", null))
        assertFalse(first.matchesCatalog("Example", null))
    }
}
