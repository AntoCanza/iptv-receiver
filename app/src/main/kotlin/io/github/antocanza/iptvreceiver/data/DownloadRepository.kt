package io.github.antocanza.iptvreceiver.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.antocanza.iptvreceiver.ReceiverApplication
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.Category
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DownloadRepository(private val context: Context, private val viewing: ViewingStore) {
    private val app get() = context.applicationContext as ReceiverApplication
    val store = DownloadStore(context.filesDir.resolve("downloads/index.bin"))
    val files = DownloadFiles(context)
    private val secrets = DownloadRequests(context.filesDir.resolve("download-requests"))
    private val settings = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val work get() = WorkManager.getInstance(context)
    private val locks = ConcurrentHashMap<String, Mutex>()
    // One transfer at a time avoids opening several provider connections for the download queue.
    internal val transferLock = Mutex()
    private val mutableAccount = MutableStateFlow(settings.getString("account", null))
    val account = mutableAccount.asStateFlow()
    private val mutableFolder = MutableStateFlow(settings.getString("folder", null))
    val folder = mutableFolder.asStateFlow()
    private val mutableWifiOnly = MutableStateFlow(settings.getBoolean("wifi", true))
    val wifiOnly = mutableWifiOnly.asStateFlow()
    private val mutablePersistenceFailed = MutableStateFlow(false)
    val persistenceFailed = mutablePersistenceFailed.asStateFlow()

    @SuppressLint("UseKtx")
    suspend fun rememberAccount(connection: ProviderConnection) = withContext(Dispatchers.IO) {
        val key = accountKey(connection)
        if (account.value != null && account.value != key) signOut()
        check(settings.edit().putString("account", key).commit())
        mutableAccount.value = key
    }

    @SuppressLint("UseKtx")
    suspend fun signOut() = withContext(Dispatchers.IO) {
        val key = account.value
        check(settings.edit().remove("account").commit())
        mutableAccount.value = null
        work.cancelAllWorkByTag("media-download").result.get()
        val entries = try { store.load().filter { it.account == key } } catch (_: Exception) { emptyList() }
        entries.forEach { entry ->
            if (entry.active) {
                store.update(entry.id) { it.copy(status = DownloadStatus.PAUSED, generation = UUID.randomUUID().toString()) }
                work.cancelUniqueWork(workName(entry.id))
            }
            locked(entry.id) { secrets.remove(entry.id) }
        }
        secrets.clear()
    }

    @SuppressLint("UseKtx")
    suspend fun setFolder(uri: Uri?) = withContext(Dispatchers.IO) {
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            files.requireLocalTree(uri)
        }
        check(settings.edit().putString("folder", uri?.toString()).commit())
        mutableFolder.value = uri?.toString()
        // Existing downloads keep their own grants and locations; only new files use this folder.
    }

    @SuppressLint("UseKtx")
    suspend fun setWifiOnly(value: Boolean) = withContext(Dispatchers.IO) {
        check(settings.edit().putBoolean("wifi", value).commit())
        mutableWifiOnly.value = value
        store.load().filter { it.active && it.account == account.value }.forEach { entry ->
            pause(entry.id)
            if (store.get(entry.id)?.status == DownloadStatus.PAUSED) resume(entry.id)
        }
    }

    suspend fun enqueue(connection: ProviderConnection, target: WatchTarget, repository: CatalogRepository) = withContext(Dispatchers.IO) {
        val extension = downloadExtension(target) ?: throw UnsupportedDownloadException()
        if (repository is ParentalCatalogRepository) repository.authorizeEpisode(connection, target)
        val canonical = if (repository is ParentalCatalogRepository) target.copy(item = repository.authorize(connection, target.item)) else target
        val source = resolve(connection, canonical, repository)
        val key = accountKey(connection)
        check(account.value == key)
        // Complete any legacy migration before credential-free offline progress is possible.
        viewing.progress.read(connection, target)
        val categories = categoryAncestors(repository.categories(connection, canonical.item.type), canonical.item.categoryId)
        val id = MessageDigest.getInstance("SHA-256").digest((key + "\u0000" + target.key.joinToString("\u0000")).toByteArray())
            .joinToString("") { "%02x".format(it) }
        locked(id) {
            check(account.value == key)
            val existing = store.get(id)
            if (existing != null && existing.status !in setOf(DownloadStatus.FAILED, DownloadStatus.MISSING, DownloadStatus.PAUSED)) return@locked
            val location = existing?.location ?: files.create(id, extension, folder.value)
            val entry = DownloadEntry(id, key, canonical, categories, location, bytes = existing?.bytes ?: 0,
                total = existing?.total, validator = existing?.validator)
            if (!entry.allowed(app.parentalControls.policy.value)) throw IllegalStateException("Download blocked")
            try {
                secrets.save(id, source.url)
                store.put(entry)
            } catch (error: Exception) {
                if (existing == null) files.delete(location)
                throw error
            }
            schedule(entry)
        }
    }

    suspend fun pause(id: String) = withContext(Dispatchers.IO) {
        val entry = owned(id)
        store.update(id) { if (it.active) it.copy(status = DownloadStatus.PAUSED, generation = UUID.randomUUID().toString()) else it }
        work.cancelUniqueWork(workName(entry.id)).result.get()
    }

    suspend fun resume(id: String, connection: ProviderConnection? = null, repository: CatalogRepository? = null) = withContext(Dispatchers.IO) {
        locked(id) {
            var entry = owned(id)
            check(entry.status != DownloadStatus.READY && entry.status != DownloadStatus.DELETING)
            check(entry.allowed(app.parentalControls.policy.value))
            if (connection != null && repository != null) {
                check(accountKey(connection) == entry.account)
                if (repository is ParentalCatalogRepository) repository.authorizeEpisode(connection, entry.target)
                val canonical = if (repository is ParentalCatalogRepository) entry.target.copy(item = repository.authorize(connection, entry.target.item)) else entry.target
                entry = entry.copy(target = canonical, categories = categoryAncestors(repository.categories(connection, canonical.item.type), canonical.item.categoryId))
                check(entry.allowed(app.parentalControls.policy.value))
                secrets.save(id, resolve(connection, canonical, repository).url)
            }
            if (!secrets.exists(id)) {
                store.update(id) { it.copy(status = DownloadStatus.FAILED, failure = DownloadFailure.SIGN_IN) }
                return@locked
            }
            if (entry.status == DownloadStatus.MISSING) {
                // The location is immutable in an entry: remove missing records and download again from details.
                throw IOException("Download file unavailable")
            }
            entry = entry.copy(status = DownloadStatus.QUEUED, failure = DownloadFailure.NONE, generation = UUID.randomUUID().toString())
            store.put(entry)
            schedule(entry)
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val entry = owned(id)
        store.update(id) { it.copy(status = DownloadStatus.DELETING, generation = UUID.randomUUID().toString()) }
        work.cancelUniqueWork(workName(id)).result.get()
        locked(id) {
            try {
                files.delete(entry.location)
                secrets.remove(id)
                store.remove(id)
            } catch (error: Exception) {
                store.update(id) { it.copy(status = DownloadStatus.FAILED, failure = DownloadFailure.STORAGE) }
                throw error
            }
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val key = account.value
        store.load().filter { it.account == key && it.status in setOf(DownloadStatus.READY, DownloadStatus.MISSING, DownloadStatus.DELETING) }.forEach { entry ->
            if (entry.status == DownloadStatus.DELETING) remove(entry.id)
            else store.update(entry.id) { it.copy(status = if (files.exists(it.location, it.bytes)) DownloadStatus.READY else DownloadStatus.MISSING) }
        }
        store.load().filter { it.account == key && it.active }.forEach { entry ->
            val jobs = work.getWorkInfosByTag("media-generation:${entry.generation}").get()
            if (jobs.isEmpty() || jobs.all { it.state.isFinished }) store.update(entry.id, entry.generation) {
                if (it.active) it.copy(status = DownloadStatus.PAUSED) else it
            }
        }
    }

    suspend fun playback(id: String): Pair<DownloadEntry, PlaybackProgress?> = withContext(Dispatchers.IO) {
        val entry = owned(id)
        check(entry.status == DownloadStatus.READY && entry.allowed(app.parentalControls.policy.value))
        if (!files.exists(entry.location, entry.bytes)) {
            store.update(id) { it.copy(status = DownloadStatus.MISSING) }
            throw IOException("Download file unavailable")
        }
        entry to viewing.offlineProgress(entry.account, entry.target)
    }

    suspend fun recordWatch(entry: DownloadEntry, observed: Long) = withContext(Dispatchers.IO) {
        try {
            if (account.value == entry.account) viewing.recordOfflineWatch(entry.account, entry.target, observed)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { mutablePersistenceFailed.value = true }
    }

    suspend fun saveProgress(entry: DownloadEntry, progress: PlaybackProgress, observed: Long) = withContext(Dispatchers.IO) {
        try {
            if (account.value == entry.account) viewing.saveOfflineProgress(entry.account, entry.target, progress, observed)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { mutablePersistenceFailed.value = true }
    }

    internal fun request(id: String): String? = secrets.load(id)
    internal fun forgetRequest(id: String) = secrets.remove(id)
    internal suspend fun <T> locked(id: String, action: suspend () -> T): T = locks.getOrPut(id) { Mutex() }.withLock { action() }

    private fun owned(id: String): DownloadEntry = checkNotNull(store.get(id)).also { check(it.account == account.value) }
    private fun resolve(connection: ProviderConnection, target: WatchTarget, repository: CatalogRepository): PlaybackSource =
        target.episode?.let { repository.episodeStream(connection, it) } ?: repository.stream(connection, target.item)

    private fun schedule(entry: DownloadEntry) {
        val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setInputData(workDataOf("id" to entry.id, "generation" to entry.generation))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .addTag("media-download").addTag("media-generation:${entry.generation}").build()
        work.enqueueUniqueWork(workName(entry.id), ExistingWorkPolicy.REPLACE, request).result.get()
    }

    private fun workName(id: String) = "media-download:$id"
}

internal fun categoryAncestors(categories: List<Category>, categoryId: String?): List<Category> {
    val indexed = categories.associateBy { it.id }
    val result = linkedMapOf<String, Category>()
    var current = categoryId
    while (current != null && current !in result && result.size < 1000) {
        val category = indexed[current] ?: break
        result[current] = category
        current = category.parentId
    }
    return result.values.toList()
}

/** Pending requests need credentials across background/process restarts; only Keystore-encrypted bytes persist. */
private class DownloadRequests(private val directory: File) {
    fun exists(id: String): Boolean = file(id).isFile
    fun save(id: String, url: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val clear = url.toByteArray(Charsets.UTF_8)
        try {
            val encrypted = cipher.doFinal(clear)
            writePrivateData(file(id)) { it.writeInt(cipher.iv.size); it.write(cipher.iv); it.writeInt(encrypted.size); it.write(encrypted) }
        } finally { clear.fill(0) }
    }
    fun load(id: String): String? {
        if (!file(id).exists()) return null
        return DataInputStream(file(id).inputStream()).use { input ->
            check(input.readInt() == 12)
            val iv = ByteArray(12).also(input::readFully)
            val size = input.readInt(); check(size in 16..65536)
            val encrypted = ByteArray(size).also(input::readFully)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            val clear = cipher.doFinal(encrypted)
            try { String(clear, Charsets.UTF_8) } finally { clear.fill(0) }
        }
    }
    fun remove(id: String) { val file = file(id); if (file.exists() && !file.delete()) throw IOException("Cannot clear download request") }
    fun clear() { directory.listFiles { file -> file.name.matches(Regex("[a-f0-9]{64}\\.bin")) }?.forEach { remove(it.nameWithoutExtension) } }
    private fun file(id: String): File { require(id.matches(Regex("[a-f0-9]{64}"))); return directory.resolve("$id.bin") }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("iptv-downloads-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("iptv-downloads-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
