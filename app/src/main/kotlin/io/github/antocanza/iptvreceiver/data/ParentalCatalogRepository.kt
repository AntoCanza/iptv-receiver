package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.Category
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.Episode
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.model.ProviderException
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections
import java.util.WeakHashMap

/** Enforces the same restrictions for browsing, search, details, history and stream resolution. */
class ParentalCatalogRepository(
    private val delegate: CatalogRepository,
    private val controls: ParentalControls,
) : CatalogRepository by delegate {
    val policy = controls.policy
    private val categories = ConcurrentHashMap<String, List<Category>>()
    private val items = ConcurrentHashMap<String, CatalogItem>()
    private val episodeParents = ConcurrentHashMap<String, CatalogItem>()
    private val completeCatalogs = ConcurrentHashMap.newKeySet<String>()
    private val identities = Collections.synchronizedMap(WeakHashMap<ProviderConnection, String>())
    private val blockedCache = ConcurrentHashMap<String, Pair<Long, Set<String>>>()
    private fun identity(connection: ProviderConnection): String = identities.getOrPut(connection) { accountKey(connection) }
    private fun typeKey(connection: ProviderConnection, type: ContentType) = "${identity(connection)}:${type.name}"
    private fun itemKey(connection: ProviderConnection, item: CatalogItem) = "${typeKey(connection, item.type)}:${item.id}"

    override suspend fun authenticate(connection: ProviderConnection) = delegate.authenticate(connection).also {
        categories.clear()
        items.clear()
        episodeParents.clear()
        completeCatalogs.clear()
        blockedCache.clear()
    }

    private suspend fun rawCategories(connection: ProviderConnection, type: ContentType): List<Category> {
        denyIfUnavailable()
        return delegate.categories(connection, type).also {
            val key = typeKey(connection, type)
            categories[key] = it
            blockedCache.remove(key)
        }
    }

    override suspend fun categories(connection: ProviderConnection, type: ContentType): List<Category> {
        val all = rawCategories(connection, type)
        val blocked = controls.policy.value.blockedIds(connection, type, all)
        return all.filterNot { it.id in blocked }
    }

    override suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String?): List<CatalogItem> {
        val allCategories = rawCategories(connection, type)
        if (categoryId in controls.policy.value.blockedIds(connection, type, allCategories)) return emptyList()
        val loaded = delegate.items(connection, type, categoryId).map { item ->
            if (item.categoryId == null && categoryId != null) item.copy(categoryId = categoryId) else item
        }.onEach { items[itemKey(connection, it)] = it }
        if (categoryId == null) completeCatalogs.add(typeKey(connection, type))
        return loaded.filter { allowed(connection, it) }
    }

    /** Older history has no category/adult metadata: resolve its identifiers against the provider first. */
    suspend fun authorize(connection: ProviderConnection, item: CatalogItem): CatalogItem {
        denyIfUnavailable()
        val key = itemKey(connection, item)
        if (items[key] == null && typeKey(connection, item.type) !in completeCatalogs) {
            rawCategories(connection, item.type)
            delegate.items(connection, item.type, null).forEach { items[itemKey(connection, it)] = it }
            completeCatalogs.add(typeKey(connection, item.type))
        }
        val canonical = items[key] ?: throw ProviderException(FailureReason.PARENTAL_BLOCKED)
        if (!allowed(connection, canonical)) throw ProviderException(FailureReason.PARENTAL_BLOCKED)
        return canonical
    }

    suspend fun visibleHistory(connection: ProviderConnection, entries: List<WatchHistoryEntry>): List<WatchHistoryEntry> {
        denyIfUnavailable()
        // One request per represented type, even if many historical IDs are missing or removed.
        entries.map { it.target.item }.groupBy(CatalogItem::type).forEach { (type, group) ->
            if (typeKey(connection, type) !in completeCatalogs && group.any { items[itemKey(connection, it)] == null }) {
                rawCategories(connection, type)
                delegate.items(connection, type, null).forEach { items[itemKey(connection, it)] = it }
                completeCatalogs.add(typeKey(connection, type))
            }
        }
        return entries.filter { entry -> items[itemKey(connection, entry.target.item)]?.let { allowed(connection, it) } == true }
    }

    override suspend fun movie(connection: ProviderConnection, item: CatalogItem) =
        delegate.movie(connection, authorize(connection, item)).also { details ->
            items[itemKey(connection, details.item)] = details.item
            requireAllowed(connection, details.item)
        }

    override suspend fun series(connection: ProviderConnection, item: CatalogItem) =
        delegate.series(connection, authorize(connection, item)).also { details ->
            details.episodes.forEach { episode -> episodeParents["${identity(connection)}:${episode.id}"] = item }
        }

    override fun stream(connection: ProviderConnection, item: CatalogItem, liveFormat: String) =
        delegate.stream(connection, requireAllowed(connection, item), liveFormat)

    override fun episodeStream(connection: ProviderConnection, episode: Episode) =
        run {
            val parent = episodeParents["${identity(connection)}:${episode.id}"]
                ?: throw ProviderException(FailureReason.PARENTAL_BLOCKED)
            requireAllowed(connection, parent)
            delegate.episodeStream(connection, episode)
        }

    suspend fun authorizeEpisode(connection: ProviderConnection, target: WatchTarget) {
        val item = authorize(connection, target.item)
        val episode = target.episode ?: return
        val details = series(connection, item)
        if (details.episodes.none { it.id == episode.id }) throw ProviderException(FailureReason.PARENTAL_BLOCKED)
    }

    private fun requireAllowed(connection: ProviderConnection, item: CatalogItem): CatalogItem {
        val canonical = items[itemKey(connection, item)] ?: throw ProviderException(FailureReason.PARENTAL_BLOCKED)
        if (!allowed(connection, canonical)) throw ProviderException(FailureReason.PARENTAL_BLOCKED)
        return canonical
    }

    private fun allowed(connection: ProviderConnection, item: CatalogItem): Boolean {
        val policy = controls.policy.value
        val key = typeKey(connection, item.type)
        val allCategories = categories[key] ?: return false
        val cached = blockedCache[key]
        val blocked = if (cached?.first == policy.revision) cached.second else
            policy.blockedIds(connection, item.type, allCategories).also { blockedCache[key] = policy.revision to it }
        return !policy.blocks(item, blocked)
    }

    private fun denyIfUnavailable() {
        if (controls.policy.value.storageFailed) throw ProviderException(FailureReason.PARENTAL_BLOCKED)
    }
}
