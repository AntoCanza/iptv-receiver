package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.Category
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, READY, FAILED, MISSING, DELETING }
enum class DownloadFailure { NONE, NETWORK, STORAGE, UNSUPPORTED, SIGN_IN, BLOCKED }

/** No provider addresses, artwork, credentials or request URLs belong in this index. */
data class DownloadEntry(
    val id: String,
    val account: String,
    val target: WatchTarget,
    val categories: List<Category>,
    val location: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val bytes: Long = 0,
    val total: Long? = null,
    val validator: String? = null,
    val failure: DownloadFailure = DownloadFailure.NONE,
    val generation: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val title: String get() = target.episode?.let { "${target.item.title} · ${it.season}/${it.number} · ${it.title}" } ?: target.item.title
    val active: Boolean get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING
    fun allowed(policy: ParentalPolicy): Boolean = !policy.blocks(target.item, policy.blockedIds(account, target.item.type, categories))
}

internal fun downloadExtension(target: WatchTarget): String? {
    if (target.item.type == ContentType.LIVE || (target.item.type == ContentType.SERIES) != (target.episode != null)) return null
    return (target.episode?.extension ?: target.item.extension)?.lowercase()?.takeIf { it in setOf("mp4", "mkv", "ts", "m4v", "webm", "mov", "mp3", "aac", "m4a", "wav") }
}

/** Atomic, bounded and versioned, separately from viewing.db and disposable caches. */
class DownloadStore(private val file: File) {
    private val mutableEntries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val entries = mutableEntries.asStateFlow()
    private var loaded = false

    @Synchronized fun load(): List<DownloadEntry> {
        if (!loaded) {
            val read = if (!file.exists()) emptyList() else DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != 1) throw IOException("Unsupported download index")
                val count = input.readInt()
                if (count !in 0..500) throw IOException("Invalid download index")
                List(count) { readEntry(input) }.also { list ->
                    if (list.map { it.id }.distinct().size != list.size || input.read() != -1) throw IOException("Invalid download index")
                }
            }
            mutableEntries.value = read
            loaded = true
        }
        return mutableEntries.value
    }

    @Synchronized fun get(id: String): DownloadEntry? = load().firstOrNull { it.id == id }

    @Synchronized fun put(entry: DownloadEntry) {
        validate(entry)
        val next = load().filterNot { it.id == entry.id } + safe(entry)
        if (next.size > 500) throw IOException("Download limit reached")
        persist(next)
    }

    @Synchronized fun update(id: String, generation: String? = null, change: (DownloadEntry) -> DownloadEntry): DownloadEntry? {
        val previous = get(id) ?: return null
        if (generation != null && previous.generation != generation) return null
        val next = change(previous)
        require(next.id == previous.id && next.account == previous.account && next.location == previous.location && next.target.key == previous.target.key)
        put(next)
        return next
    }

    @Synchronized fun remove(id: String) { persist(load().filterNot { it.id == id }) }

    private fun persist(entries: List<DownloadEntry>) {
        writePrivateData(file) { output ->
            output.writeInt(1); output.writeInt(entries.size)
            entries.forEach { writeEntry(output, it) }
        }
        mutableEntries.value = entries
    }

    private fun safe(entry: DownloadEntry) = entry.copy(target = WatchTarget(
        entry.target.item.copy(imageUrl = null, imageFallbackUrls = emptyList(), metadata = io.github.antocanza.iptvreceiver.model.ContentMetadata()),
        entry.target.episode?.copy(description = "", imageUrls = emptyList()),
    ))

    private fun validate(entry: DownloadEntry) {
        require(entry.id.matches(Regex("[a-f0-9]{64}")) && entry.account.matches(Regex("[a-f0-9]{64}")))
        require(downloadExtension(entry.target) != null && entry.bytes >= 0 && (entry.total == null || entry.total >= 0))
        require(entry.total == null || entry.bytes <= entry.total)
        require(entry.status != DownloadStatus.READY || (entry.bytes > 0 && entry.total == entry.bytes))
        require(entry.categories.size <= 1000 && entry.location.length <= 8192)
        require(entry.location.startsWith("file:") || entry.location.startsWith("content:"))
    }

    private fun writeEntry(out: DataOutputStream, entry: DownloadEntry) {
        out.writeUTF(entry.id); out.writeUTF(entry.account)
        val item = entry.target.item
        out.writeUTF(item.type.name); out.writeUTF(item.id); out.writeUTF(item.title); out.writeUTF(item.extension.orEmpty())
        out.writeUTF(item.categoryId.orEmpty()); out.writeBoolean(item.isAdult)
        out.writeBoolean(entry.target.episode != null)
        entry.target.episode?.let { out.writeUTF(it.id); out.writeUTF(it.title); out.writeInt(it.season); out.writeInt(it.number); out.writeUTF(it.extension) }
        out.writeInt(entry.categories.size)
        entry.categories.forEach { out.writeUTF(it.id); out.writeUTF(it.name); out.writeUTF(it.parentId.orEmpty()); out.writeBoolean(it.isAdult) }
        out.writeUTF(entry.location); out.writeUTF(entry.status.name); out.writeLong(entry.bytes); out.writeLong(entry.total ?: -1)
        out.writeUTF(entry.validator.orEmpty()); out.writeUTF(entry.failure.name); out.writeUTF(entry.generation); out.writeLong(entry.createdAt)
    }

    private fun readEntry(input: DataInputStream): DownloadEntry {
        val id = input.readUTF(); val account = input.readUTF()
        val type = ContentType.valueOf(input.readUTF())
        val item = CatalogItem(input.readUTF(), input.readUTF(), type, extension = input.readUTF().ifEmpty { null },
            categoryId = input.readUTF().ifEmpty { null }, isAdult = input.readBoolean())
        val episode = if (input.readBoolean()) Episode(input.readUTF(), input.readUTF(), input.readInt(), input.readInt(), input.readUTF()) else null
        val count = input.readInt()
        if (count !in 0..1000) throw IOException("Invalid download classification")
        val categories = List(count) { Category(input.readUTF(), input.readUTF(), input.readUTF().ifEmpty { null }, input.readBoolean()) }
        return DownloadEntry(id, account, WatchTarget(item, episode), categories, input.readUTF(), DownloadStatus.valueOf(input.readUTF()),
            input.readLong(), input.readLong().takeIf { it >= 0 }, input.readUTF().ifEmpty { null }, DownloadFailure.valueOf(input.readUTF()),
            input.readUTF(), input.readLong()).also(::validate)
    }
}
