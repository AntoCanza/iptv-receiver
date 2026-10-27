package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

data class PlaybackProgress(
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean = false,
    val watched: Boolean = completed,
    val lastPlayedAtMillis: Long? = null,
) {
    val canResume: Boolean get() = !completed && positionMs >= 5_000

    companion object {
        fun checkpoint(positionMs: Long, durationMs: Long?, ended: Boolean = false): PlaybackProgress {
            val duration = durationMs?.takeIf { it in 1..MAX_POSITION }
            val position = positionMs.coerceIn(0, duration ?: MAX_POSITION)
            val completed = ended || (duration != null && position > 0 && position >= duration - duration / 20)
            return PlaybackProgress(position, duration, completed)
        }
        internal const val MAX_POSITION = 365L * 24 * 3600 * 1000
    }
}

interface PlaybackProgressStore {
    fun load(connection: ProviderConnection): Map<List<String>, PlaybackProgress>
    fun read(connection: ProviderConnection, target: WatchTarget): PlaybackProgress? = load(connection)[target.key]
    fun forItem(connection: ProviderConnection, item: CatalogItem): Map<List<String>, PlaybackProgress> =
        load(connection).filterKeys { it[0] == item.type.name && it[1] == item.id }
    fun update(connection: ProviderConnection, target: WatchTarget, observedAtNanos: Long = System.nanoTime(), change: (PlaybackProgress?) -> PlaybackProgress)
    fun save(connection: ProviderConnection, target: WatchTarget, progress: PlaybackProgress, observedAtNanos: Long = System.nanoTime()) =
        update(connection, target, observedAtNanos) { previous ->
            progress.copy(watched = progress.watched || progress.completed || previous?.watched == true,
                lastPlayedAtMillis = progress.lastPlayedAtMillis ?: previous?.lastPlayedAtMillis)
        }
    fun restart(connection: ProviderConnection, target: WatchTarget, observedAtNanos: Long = System.nanoTime()) =
        update(connection, target, observedAtNanos) { previous ->
            PlaybackProgress(0, previous?.durationMs, watched = previous?.watched == true, lastPlayedAtMillis = previous?.lastPlayedAtMillis)
        }
    fun setWatched(connection: ProviderConnection, target: WatchTarget, watched: Boolean, observedAtNanos: Long = System.nanoTime()) =
        update(connection, target, observedAtNanos) { previous ->
            PlaybackProgress(if (watched) previous?.durationMs ?: 0 else 0, previous?.durationMs,
                completed = watched, watched = watched, lastPlayedAtMillis = previous?.lastPlayedAtMillis)
        }
    fun clear(connection: ProviderConnection)
}

/** Legacy atomic format, retained for migration and platform-independent storage tests. */
class FilePlaybackProgressStore(private val directory: File) : PlaybackProgressStore {
    private val versions = mutableMapOf<List<String>, Long>()
    private val clearedAt = mutableMapOf<String, Long>()
    @Synchronized override fun load(connection: ProviderConnection): Map<List<String>, PlaybackProgress> = read(file(connection))

    @Synchronized override fun update(connection: ProviderConnection, target: WatchTarget, observedAtNanos: Long, change: (PlaybackProgress?) -> PlaybackProgress) {
        if (target.item.type == ContentType.LIVE) return // Live TV and catch-up are not movie/episode progress.
        require((target.item.type == ContentType.SERIES) == (target.episode != null))
        val file = file(connection)
        val account = accountKey(connection)
        if (clearedAt[account]?.let { it >= observedAtNanos } == true) return
        val versionKey = listOf(account) + target.key
        if (versions[versionKey]?.let { it > observedAtNanos } == true) return
        val entries = read(file).toMutableMap()
        val progress = change(entries[target.key])
        val safe = PlaybackProgress.checkpoint(progress.positionMs, progress.durationMs, progress.completed)
            .let { it.copy(watched = progress.watched || it.completed, lastPlayedAtMillis = progress.lastPlayedAtMillis) }
        if (entries[target.key] == safe) { versions[versionKey] = observedAtNanos; return }
        entries[target.key] = safe
        if (entries.size > 100_000) throw IOException("Too many playback positions")
        writePrivateData(file) { output ->
            output.writeInt(2); output.writeInt(entries.size)
            entries.forEach { (key, value) ->
                key.forEach(output::writeUTF)
                output.writeLong(value.positionMs); output.writeLong(value.durationMs ?: -1); output.writeBoolean(value.completed)
                output.writeBoolean(value.watched); output.writeLong(value.lastPlayedAtMillis ?: -1)
            }
        }
        versions[versionKey] = observedAtNanos
    }

    @Synchronized override fun clear(connection: ProviderConnection) {
        val file = file(connection)
        if (file.exists() && !file.delete()) throw IOException("Cannot clear playback progress")
        clearedAt[accountKey(connection)] = System.nanoTime()
        versions.keys.removeAll { it.first() == accountKey(connection) }
    }

    private fun file(connection: ProviderConnection) = directory.resolve("${accountKey(connection)}.bin")
    private fun read(file: File): Map<List<String>, PlaybackProgress> {
        if (!file.exists()) return emptyMap()
        return DataInputStream(file.inputStream().buffered()).use { input ->
            val version = input.readInt()
            if (version !in 1..2) throw IOException("Unsupported progress version")
            val count = input.readInt()
            if (count !in 0..100_000) throw IOException("Invalid progress size")
            val result = mutableMapOf<List<String>, PlaybackProgress>()
            repeat(count) {
                val type = input.readUTF(); val id = input.readUTF(); val episode = input.readUTF()
                if (type !in setOf("MOVIE", "SERIES") || id.isBlank() || (type == "SERIES") != episode.isNotBlank()) throw IOException("Invalid progress target")
                val position = input.readLong(); val duration = input.readLong(); val completed = input.readBoolean()
                val watched = if (version >= 2) input.readBoolean() else completed
                val played = if (version >= 2) input.readLong() else -1
                if (position !in 0..PlaybackProgress.MAX_POSITION || (duration != -1L && duration !in 1..PlaybackProgress.MAX_POSITION)) throw IOException("Invalid playback position")
                if (played < -1) throw IOException("Invalid playback timestamp")
                result[listOf(type, id, episode)] = PlaybackProgress.checkpoint(position, duration.takeIf { it > 0 }, completed)
                    .let { it.copy(watched = watched || it.completed, lastPlayedAtMillis = played.takeIf { value -> value >= 0 }) }
            }
            if (input.read() != -1) throw IOException("Invalid progress data")
            result
        }
    }
}

internal fun writePrivateData(file: File, write: (DataOutputStream) -> Unit) {
    val directory = checkNotNull(file.parentFile)
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot save app data")
    val temporary = File.createTempFile("state-", ".tmp", directory)
    try {
        FileOutputStream(temporary).use { stream ->
            val output = DataOutputStream(stream.buffered()); write(output); output.flush(); stream.fd.sync()
        }
        try { Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), file.toPath(), REPLACE_EXISTING) }
    } finally { temporary.delete() }
}
