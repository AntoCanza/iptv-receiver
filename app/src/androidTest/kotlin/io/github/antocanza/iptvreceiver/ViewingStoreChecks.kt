package io.github.antocanza.iptvreceiver

import android.content.Context
import io.github.antocanza.iptvreceiver.data.*
import io.github.antocanza.iptvreceiver.model.*
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Uses a dedicated database and fictional account only; never touches a saved provider connection. */
internal class ViewingStoreChecks(private val context: Context, private val passed: (String) -> Unit) {
    fun run() {
        val suffix = System.nanoTime().toString()
        val name = "viewing-checks-$suffix.db"
        val legacy = context.cacheDir.resolve("viewing-checks-$suffix")
        val connection = ProviderConnection("Synthetic account", "https://fixture.example.test", "fixture-user", "fixture-password")
        val other = ProviderConnection("Other", connection.serverUrl, "other-user", "other-password")
        val movie = WatchTarget(CatalogItem("movie", "Synthetic movie", ContentType.MOVIE))
        val finished = movie.copy(item = movie.item.copy(id = "finished"))
        var time = TimeUnit.DAYS.toMillis(20_000)
        val oldHistory = FileWatchHistoryStore(legacy.resolve("watch-history")) { time }
        oldHistory.record(connection, movie); oldHistory.record(connection, finished)
        val old = legacy.resolve("playback-progress/${accountKey(connection)}.bin")
        old.parentFile!!.mkdirs()
        DataOutputStream(old.outputStream()).use { output ->
            output.writeInt(1); output.writeInt(2)
            listOf(movie to PlaybackProgress(60_000, 100_000), finished to PlaybackProgress(100_000, 100_000, true)).forEach { (target, point) ->
                target.key.forEach(output::writeUTF); output.writeLong(point.positionMs); output.writeLong(point.durationMs!!); output.writeBoolean(point.completed)
            }
        }
        val original = old.readBytes()
        fun create() = ViewingStore(context, name, legacy) { time }
        try {
            create().use { store ->
                check(store.progress.read(connection, movie)?.positionMs == 60_000L)
                check(store.progress.read(connection, movie)?.lastPlayedAtMillis == time)
                check(store.progress.read(connection, finished)?.watched == true)
                check(store.history.page(connection, 50).entries.size == 2)
                check(old.readBytes().contentEquals(original))
                passed("legacy version-one bookmarks and history migrate together without changing source progress")

                store.progress.restart(connection, finished, 10)
                store.progress.save(connection, finished, PlaybackProgress(10_000, 100_000), 20)
                check(store.progress.read(connection, finished)?.watched == true)
                check(store.progress.read(connection, finished)?.canResume == true)
                store.progress.setWatched(connection, finished, false, 40)
                store.progress.save(connection, finished, PlaybackProgress(100_000, 100_000, true), 30)
                check(store.progress.read(connection, finished)?.watched == false)
                passed("restart preserves watched state and stale checkpoints cannot undo manual edits")

                store.progress.save(other, movie, PlaybackProgress(20_000, null))
                val renamed = ProviderConnection("Renamed", "https://FIXTURE.example.test:443/", connection.username, "changed-password")
                check(store.progress.read(renamed, movie)?.positionMs == 60_000L)
                check(store.progress.read(other, movie)?.positionMs == 20_000L)
                val show = CatalogItem("movie", "Series", ContentType.SERIES)
                val episode = WatchTarget(show, Episode("movie", "Episode", 1, 1, "mp4"))
                store.progress.save(connection, episode, PlaybackProgress(30_000, null))
                check(store.progress.forItem(connection, show).size == 1)
                check(store.progress.read(connection, movie)?.positionMs == 60_000L)
                passed("indexed lookups isolate account, content type, show and episode")

                repeat(55) { index ->
                    time++
                    store.history.record(connection, movie.copy(item = movie.item.copy(id = "item-$index")))
                }
                check(store.history.page(connection, 50).entries.size == 50)
                check(store.history.page(connection, 50).hasMore)
                check(!store.history.page(connection, 100).hasMore)
                val rows = store.progress.load(connection).size
                repeat(100) { store.progress.save(connection, movie, PlaybackProgress(5_000L + it, 100_000), 100L + it) }
                check(store.progress.load(connection).size == rows)
                store.progress.save(connection, movie.copy(item = movie.item.copy(type = ContentType.LIVE)), PlaybackProgress(50_000, null))
                check(store.progress.load(connection).size == rows)
                passed("history pages are bounded and repeated playback ticks update existing rows only")

                val live = movie.copy(item = movie.item.copy(type = ContentType.LIVE))
                store.history.record(connection, live)
                time += FileWatchHistoryStore.RETENTION_MILLIS
                check(store.history.load(connection).none { it.target.item.type == ContentType.LIVE })
                check(store.history.load(connection).isNotEmpty())
                time += FileWatchHistoryStore.VOD_RETENTION_MILLIS
                store.history.pruneExpired()
                check(store.history.load(connection).isEmpty())
                check(store.progress.read(connection, movie)?.canResume == true)
                passed("180-day VOD and ten-day live retention do not expire durable progress")
            }
            // A completed migration must never read an obsolete legacy file again.
            old.writeBytes(byteArrayOf(0, 0, 3, -25))
            create().use { store ->
                check(store.progress.read(connection, movie)?.canResume == true)
                val staleObservation = System.nanoTime()
                store.progress.clear(connection); store.history.clear(connection)
                store.progress.save(connection, movie, PlaybackProgress(15_000, null), staleObservation)
                check(store.progress.load(connection).isEmpty())
                check(store.progress.read(other, movie)?.positionMs == 20_000L)
            }
            create().use { store -> check(store.progress.load(connection).isEmpty()) }
            passed("database reopen retains state and clearing cannot resurrect imported files or affect another account")

            val bad = ProviderConnection("Future", connection.serverUrl, "future-user", "secret")
            val badFile = legacy.resolve("playback-progress/${accountKey(bad)}.bin")
            badFile.writeBytes(byteArrayOf(0, 0, 3, -25))
            oldHistory.record(bad, movie)
            create().use { store ->
                try { store.progress.load(bad); error("Future schema accepted") } catch (_: IOException) { }
                check(badFile.readBytes().contentEquals(byteArrayOf(0, 0, 3, -25)))
                badFile.delete()
                FilePlaybackProgressStore(legacy.resolve("playback-progress")).save(bad, movie, PlaybackProgress(15_000, null))
                check(store.progress.read(bad, movie)?.positionMs == 15_000L)
                check(store.history.load(bad).size == 1)
            }
            passed("failed migrations preserve unknown data and retry transactionally without partial imports")

            val bytes = context.getDatabasePath(name).readBytes().toString(Charsets.ISO_8859_1)
            listOf(connection.serverUrl, connection.username, connection.password, other.password).forEach { check(!bytes.contains(it)) }
            passed("viewing database excludes provider addresses and account credentials")
        } finally {
            context.deleteDatabase(name)
            legacy.deleteRecursively()
        }
    }
}
