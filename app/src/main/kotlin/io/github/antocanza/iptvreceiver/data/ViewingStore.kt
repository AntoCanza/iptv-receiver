package io.github.antocanza.iptvreceiver.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.Episode
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import java.io.File
import java.io.IOException

/** Indexed local state. Checkpoints touch one row; recent history is read in bounded pages. */
class ViewingStore(
    context: Context,
    name: String = "viewing.db",
    legacyDirectory: File = context.filesDir,
    private val now: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val legacyProgress = FilePlaybackProgressStore(legacyDirectory.resolve("playback-progress"))
    private val legacyHistory = FileWatchHistoryStore(legacyDirectory.resolve("watch-history"), now)
    private val helper = object : SQLiteOpenHelper(context.applicationContext, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE progress (
                account TEXT NOT NULL, type TEXT NOT NULL, item TEXT NOT NULL, episode TEXT NOT NULL,
                position INTEGER NOT NULL, duration INTEGER, completed INTEGER NOT NULL, watched INTEGER NOT NULL,
                played INTEGER, PRIMARY KEY (account, type, item, episode)) WITHOUT ROWID""")
            db.execSQL("""CREATE TABLE history (
                account TEXT NOT NULL, type TEXT NOT NULL, item TEXT NOT NULL, episode TEXT NOT NULL,
                title TEXT NOT NULL, extension TEXT NOT NULL, episode_title TEXT NOT NULL,
                season INTEGER NOT NULL, number INTEGER NOT NULL, episode_extension TEXT NOT NULL,
                played INTEGER NOT NULL, PRIMARY KEY (account, type, item, episode)) WITHOUT ROWID""")
            db.execSQL("CREATE INDEX history_recent ON history(account, played DESC, type, item, episode)")
            db.execSQL("CREATE TABLE imported (account TEXT PRIMARY KEY NOT NULL) WITHOUT ROWID")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw IOException("Unsupported viewing database version")
        }
        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw IOException("Unsupported viewing database version")
        }
    }
    private val imported = mutableSetOf<String>()
    private val versions = mutableMapOf<List<String>, Long>()
    private val clearedAt = mutableMapOf<String, Long>()

    /** Offline callers supply the opaque account key of a locally authorized download, never credentials. */
    @Synchronized internal fun offlineProgress(account: String, target: WatchTarget): PlaybackProgress? =
        offlineAccount(account) { db -> point(db, account, target.key) }

    @Synchronized internal fun saveOfflineProgress(account: String, target: WatchTarget, value: PlaybackProgress, observed: Long) {
        offlineAccount(account) { db ->
            val versionKey = listOf(account) + target.key
            if (clearedAt[account]?.let { it >= observed } == true || versions[versionKey]?.let { it > observed } == true) return@offlineAccount
            val previous = point(db, account, target.key)
            val safe = PlaybackProgress.checkpoint(value.positionMs, value.durationMs, value.completed).copy(
                watched = value.watched || value.completed || previous?.watched == true, lastPlayedAtMillis = now())
            transaction(db) { writePoint(db, account, target.key, safe) }
            versions[versionKey] = observed
        }
    }

    @Synchronized internal fun recordOfflineWatch(account: String, target: WatchTarget, observed: Long) {
        if (clearedAt[account]?.let { it >= observed } == true) return
        offlineAccount(account) { db -> transaction(db) { writeHistory(db, account, WatchHistoryEntry(target, now())) } }
    }

    private fun <T> offlineAccount(account: String, action: (SQLiteDatabase) -> T): T {
        require(account.matches(Regex("[a-f0-9]{64}")))
        val db = helper.writableDatabase
        // Online enqueue initializes migration first. Never bypass an unfinished legacy import.
        check(db.rawQuery("SELECT 1 FROM imported WHERE account = ?", arrayOf(account)).use { it.moveToFirst() })
        return action(db)
    }

    val progress: PlaybackProgressStore = object : PlaybackProgressStore {
        override fun load(connection: ProviderConnection): Map<List<String>, PlaybackProgress> = account(connection) { db, key ->
            points(db, "account = ?", arrayOf(key))
        }
        override fun read(connection: ProviderConnection, target: WatchTarget): PlaybackProgress? = account(connection) { db, key ->
            point(db, key, target.key)
        }
        override fun forItem(connection: ProviderConnection, item: CatalogItem): Map<List<String>, PlaybackProgress> = account(connection) { db, key ->
            points(db, "account = ? AND type = ? AND item = ?", arrayOf(key, item.type.name, item.id))
        }
        override fun update(connection: ProviderConnection, target: WatchTarget, observedAtNanos: Long, change: (PlaybackProgress?) -> PlaybackProgress) {
            if (target.item.type == ContentType.LIVE) return
            require((target.item.type == ContentType.SERIES) == (target.episode != null))
            account(connection) { db, key ->
                val versionKey = listOf(key) + target.key
                if (clearedAt[key]?.let { it >= observedAtNanos } == true) return@account
                if (versions[versionKey]?.let { it > observedAtNanos } == true) return@account
                transaction(db) {
                    val previous = point(db, key, target.key)
                    val changed = change(previous)
                    val safe = PlaybackProgress.checkpoint(changed.positionMs, changed.durationMs, changed.completed)
                        .let { it.copy(watched = changed.watched || it.completed, lastPlayedAtMillis = changed.lastPlayedAtMillis) }
                    if (safe != previous) writePoint(db, key, target.key, safe)
                }
                versions[versionKey] = observedAtNanos
            }
        }
        override fun clear(connection: ProviderConnection) {
            account(connection) { db, key ->
                db.delete("progress", "account = ?", arrayOf(key))
                clearedAt[key] = System.nanoTime()
                versions.keys.removeAll { it.first() == key }
                legacyProgress.clear(connection)
            }
        }
    }

    val history: WatchHistoryStore = object : WatchHistoryStore {
        override fun load(connection: ProviderConnection): List<WatchHistoryEntry> = page(connection, 100_000).entries
        override fun page(connection: ProviderConnection, limit: Int): HistoryPage = account(connection) { db, key ->
            readPage(db, key, limit)
        }
        override fun record(connection: ProviderConnection, target: WatchTarget): List<WatchHistoryEntry> = account(connection) { db, key ->
            require((target.item.type == ContentType.SERIES) == (target.episode != null))
            transaction(db) { writeHistory(db, key, WatchHistoryEntry(target, now())) }
            readPage(db, key, 50).entries
        }
        override fun pruneExpired() = synchronized(this@ViewingStore) {
            prune(helper.writableDatabase)
            legacyHistory.pruneExpired()
        }
        override fun clear(connection: ProviderConnection) {
            account(connection) { db, key ->
                db.delete("history", "account = ?", arrayOf(key))
                clearedAt[key] = System.nanoTime()
                legacyHistory.clear(connection)
            }
        }
    }

    @Synchronized private fun <T> account(connection: ProviderConnection, action: (SQLiteDatabase, String) -> T): T {
        val db = helper.writableDatabase
        val key = accountKey(connection)
        if (key !in imported) {
            val done = db.rawQuery("SELECT 1 FROM imported WHERE account = ?", arrayOf(key)).use { it.moveToFirst() }
            if (!done) transaction(db) {
                // Validate both legacy files before writing anything. A failed migration rolls back and can retry.
                val oldHistory = legacyHistory.load(connection)
                val oldProgress = legacyProgress.load(connection)
                val dates = oldHistory.associate { it.target.key to it.watchedAtMillis }
                oldHistory.forEach { writeHistory(db, key, it) }
                oldProgress.forEach { (target, value) ->
                    writePoint(db, key, target, value.copy(lastPlayedAtMillis = value.lastPlayedAtMillis ?: dates[target]))
                }
                db.execSQL("INSERT INTO imported(account) VALUES (?)", arrayOf(key))
            }
            imported += key
        }
        return action(db, key)
    }

    private fun readPage(db: SQLiteDatabase, account: String, limit: Int): HistoryPage {
        require(limit in 1..100_000)
        prune(db)
        val entries = db.query("history", null, "account = ?", arrayOf(account), null, null,
            "played DESC, type, item, episode", (limit + 1).toString()).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.historyEntry()) }
        }
        return HistoryPage(entries.take(limit), entries.size > limit)
    }

    private fun prune(db: SQLiteDatabase) {
        val timestamp = now()
        db.delete("history", "(type = ? AND played <= ?) OR (type != ? AND played <= ?)", arrayOf(
            ContentType.LIVE.name, (timestamp - FileWatchHistoryStore.RETENTION_MILLIS).toString(),
            ContentType.LIVE.name, (timestamp - FileWatchHistoryStore.VOD_RETENTION_MILLIS).toString()))
    }

    private fun points(db: SQLiteDatabase, selection: String, args: Array<String>): Map<List<String>, PlaybackProgress> =
        db.query("progress", null, selection, args, null, null, null).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(listOf(cursor.text("type"), cursor.text("item"), cursor.text("episode")), cursor.progress()) }
        }

    private fun point(db: SQLiteDatabase, account: String, target: List<String>): PlaybackProgress? =
        db.query("progress", null, "account = ? AND type = ? AND item = ? AND episode = ?",
            (listOf(account) + target).toTypedArray(), null, null, null).use { if (it.moveToFirst()) it.progress() else null }

    private fun writePoint(db: SQLiteDatabase, account: String, target: List<String>, value: PlaybackProgress) {
        db.execSQL("""INSERT OR REPLACE INTO progress
            (account, type, item, episode, position, duration, completed, watched, played) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            arrayOf<Any?>(account, target[0], target[1], target[2], value.positionMs, value.durationMs,
                if (value.completed) 1 else 0, if (value.watched) 1 else 0, value.lastPlayedAtMillis))
    }

    private fun writeHistory(db: SQLiteDatabase, account: String, value: WatchHistoryEntry) {
        val item = value.target.item
        val episode = value.target.episode
        // Only these whitelisted fields reach disk; never serialize provider models or artwork URLs.
        db.execSQL("""INSERT OR REPLACE INTO history
            (account, type, item, episode, title, extension, episode_title, season, number, episode_extension, played)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            arrayOf<Any?>(account, item.type.name, item.id, episode?.id.orEmpty(), item.title, item.extension.orEmpty(),
                episode?.title.orEmpty(), episode?.season ?: 0, episode?.number ?: 0, episode?.extension.orEmpty(), value.watchedAtMillis))
    }

    private fun Cursor.text(name: String): String = getString(getColumnIndexOrThrow(name))
    private fun Cursor.number(name: String): Long = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.optional(name: String): Long? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.progress() = PlaybackProgress(number("position"), optional("duration"), number("completed") != 0L,
        number("watched") != 0L, optional("played"))
    private fun Cursor.historyEntry(): WatchHistoryEntry {
        val item = CatalogItem(text("item"), text("title"), ContentType.valueOf(text("type")), extension = text("extension").ifEmpty { null })
        val episode = text("episode").takeIf { it.isNotEmpty() }?.let {
            Episode(it, text("episode_title"), number("season").toInt(), number("number").toInt(), text("episode_extension"))
        }
        return WatchHistoryEntry(WatchTarget(item, episode), number("played"))
    }

    private fun <T> transaction(db: SQLiteDatabase, action: () -> T): T {
        db.beginTransaction()
        try { return action().also { db.setTransactionSuccessful() } }
        finally { db.endTransaction() }
    }

    @Synchronized override fun close() { helper.close() }
}
