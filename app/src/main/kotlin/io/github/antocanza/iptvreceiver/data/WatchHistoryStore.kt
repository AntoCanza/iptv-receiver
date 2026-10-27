package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.Episode
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Identifiers and display text only. Artwork and playback URLs may contain credentials. */
data class WatchTarget(val item: CatalogItem, val episode: Episode? = null) {
    val key: List<String> get() = listOf(item.type.name, item.id, episode?.id.orEmpty())
}

/** Progress is enriched from its own durable store; the existing history file remains version 1. */
data class WatchHistoryEntry(val target: WatchTarget, val watchedAtMillis: Long, val progress: PlaybackProgress? = null)
data class HistoryPage(val entries: List<WatchHistoryEntry>, val hasMore: Boolean)

interface WatchHistoryStore {
    fun load(connection: ProviderConnection): List<WatchHistoryEntry>
    fun record(connection: ProviderConnection, target: WatchTarget): List<WatchHistoryEntry>
    fun pruneExpired()
    fun page(connection: ProviderConnection, limit: Int): HistoryPage {
        val entries = load(connection)
        return HistoryPage(entries.take(limit), entries.size > limit)
    }
    fun clear(connection: ProviderConnection)
}

/** Durable app files, outside cacheDir. One atomic, versioned file per provider account. */
class FileWatchHistoryStore(private val directory: File, private val now: () -> Long = System::currentTimeMillis) : WatchHistoryStore {
    @Synchronized override fun load(connection: ProviderConnection): List<WatchHistoryEntry> {
        val file = accountFile(connection)
        val stored = read(file)
        val current = retained(stored, now())
        if (current != stored) write(file, current)
        return current
    }

    @Synchronized override fun record(connection: ProviderConnection, target: WatchTarget): List<WatchHistoryEntry> {
        require((target.item.type == ContentType.SERIES) == (target.episode != null))
        val file = accountFile(connection)
        val timestamp = now()
        // Never serialize provider artwork: even a poster URL can embed an account password.
        val safeTarget = target.copy(item = target.item.copy(imageUrl = null, imageFallbackUrls = emptyList()))
        val entries = retained(read(file), timestamp).filterNot { it.target.key == safeTarget.key }
        return (listOf(WatchHistoryEntry(safeTarget, timestamp)) + entries)
            .sortedByDescending { it.watchedAtMillis }.also { write(file, it) }
    }

    @Synchronized override fun pruneExpired() {
        val timestamp = now()
        directory.listFiles { file -> file.extension == "bin" }?.forEach { file ->
            val stored = read(file)
            val current = retained(stored, timestamp)
            if (current != stored) write(file, current)
        }
    }

    @Synchronized override fun clear(connection: ProviderConnection) {
        val file = accountFile(connection)
        if (file.exists() && !file.delete()) throw IOException("Cannot clear watch history")
    }

    private fun retained(entries: List<WatchHistoryEntry>, timestamp: Long) = entries
        .filter { it.watchedAtMillis > timestamp - retentionMillis(it.target.item.type) }
        .sortedByDescending { it.watchedAtMillis }

    private fun accountFile(connection: ProviderConnection): File {
        val uri = URI(connection.serverUrl.trim()).normalize()
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        val server = "${uri.scheme.lowercase(Locale.ROOT)}://${uri.host.lowercase(Locale.ROOT)}$port${uri.path.orEmpty().trimEnd('/')}"
        val identity = "$server\u0000${connection.username}"
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return directory.resolve(digest.joinToString("") { "%02x".format(it) } + ".bin")
    }

    private fun read(file: File): List<WatchHistoryEntry> {
        if (!file.exists()) return emptyList()
        return DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != SCHEMA_VERSION) throw IOException("Unsupported watch history version")
            val count = input.readInt()
            if (count !in 0..100_000) throw IOException("Invalid watch history size")
            List(count) {
                val timestamp = input.readLong()
                val typeName = input.readUTF()
                val type = ContentType.entries.firstOrNull { it.name == typeName } ?: throw IOException("Invalid content type")
                val item = CatalogItem(input.readUTF(), input.readUTF(), type,
                    extension = input.readUTF().ifEmpty { null })
                val episode = if (input.readBoolean()) Episode(input.readUTF(), input.readUTF(),
                    input.readInt(), input.readInt(), input.readUTF()) else null
                if ((type == ContentType.SERIES) != (episode != null)) throw IOException("Invalid history target")
                WatchHistoryEntry(WatchTarget(item, episode), timestamp)
            }
        }
    }

    private fun write(file: File, entries: List<WatchHistoryEntry>) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create watch history directory")
        val temporary = File.createTempFile("history-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { stream ->
                val output = DataOutputStream(stream.buffered())
                output.writeInt(SCHEMA_VERSION)
                output.writeInt(entries.size)
                entries.forEach { entry ->
                    output.writeLong(entry.watchedAtMillis)
                    val item = entry.target.item
                    output.writeUTF(item.type.name)
                    output.writeUTF(item.id)
                    output.writeUTF(item.title)
                    output.writeUTF(item.extension.orEmpty())
                    val episode = entry.target.episode
                    output.writeBoolean(episode != null)
                    if (episode != null) {
                        output.writeUTF(episode.id)
                        output.writeUTF(episode.title)
                        output.writeInt(episode.season)
                        output.writeInt(episode.number)
                        output.writeUTF(episode.extension)
                    }
                }
                output.flush()
                stream.fd.sync()
            }
            try { Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), file.toPath(), REPLACE_EXISTING) }
        } finally { temporary.delete() }
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        val RETENTION_MILLIS: Long = TimeUnit.DAYS.toMillis(10)
        val VOD_RETENTION_MILLIS: Long = TimeUnit.DAYS.toMillis(180)
        fun retentionMillis(type: ContentType): Long = if (type == ContentType.LIVE) RETENTION_MILLIS else VOD_RETENTION_MILLIS
    }
}
