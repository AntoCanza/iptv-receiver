package io.github.antocanza.iptvreceiver.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.antocanza.iptvreceiver.MainActivity
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.ReceiverApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.FilterOutputStream

class MediaDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val generation = inputData.getString("generation") ?: return@withContext Result.failure()
        val app = applicationContext as ReceiverApplication
        val downloads = app.downloads
        val store = downloads.store
        try {
            downloads.transferLock.withLock {
                downloads.locked(id) {
                    val entry = store.get(id) ?: return@locked Result.success()
                    if (entry.generation != generation || !entry.active) return@locked Result.success()
                    if (!entry.allowed(app.parentalControls.policy.value) || downloads.account.value != entry.account) {
                        store.update(id, generation) { it.copy(status = DownloadStatus.PAUSED, failure = DownloadFailure.BLOCKED) }
                        return@locked Result.success()
                    }
                    val address = downloads.request(id)
                    if (address == null) {
                        store.update(id, generation) { it.copy(status = DownloadStatus.FAILED, failure = DownloadFailure.SIGN_IN) }
                        return@locked Result.failure()
                    }
                    setForeground(foreground())
                    store.update(id, generation) { it.copy(status = DownloadStatus.DOWNLOADING, failure = DownloadFailure.NONE) }
                    val jobContext = currentCoroutineContext()
                    val output = downloads.files.output(entry.location)
                    ProgressiveDownload().transfer(address, object : DownloadOutput {
                        override fun length(): Long = try { output.length() } catch (_: Exception) { throw DownloadStorageException() }
                        override fun open(offset: Long) = try {
                            object : FilterOutputStream(output.open(offset)) {
                                override fun write(value: Int) { try { out.write(value) } catch (_: IOException) { throw DownloadStorageException() } }
                                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                                    try { out.write(buffer, offset, length) } catch (_: IOException) { throw DownloadStorageException() }
                                }
                                override fun flush() { try { out.flush() } catch (_: IOException) { throw DownloadStorageException() } }
                            }
                        } catch (_: Exception) { throw DownloadStorageException() }
                    }, entry.validator, entry.total, checkActive = {
                        jobContext.ensureActive()
                        val current = store.get(id)
                        if (current?.generation != generation || !current.active) throw CancellationException("Download stopped")
                        if (downloads.account.value != entry.account || !current.allowed(app.parentalControls.policy.value)) throw DownloadBlockedException()
                    }, progress = { bytes, total, validator ->
                        store.update(id, generation) { it.copy(bytes = bytes, total = total, validator = validator) }
                    })
                    jobContext.ensureActive()
                    val latest = store.get(id)
                    if (latest?.generation == generation && latest.active && latest.allowed(app.parentalControls.policy.value) && downloads.account.value == entry.account) {
                        val completed = store.update(id, generation) { it.copy(status = DownloadStatus.READY, failure = DownloadFailure.NONE) }
                        if (completed != null) downloads.forgetRequest(id)
                    }
                    Result.success()
                }
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                store.update(id, generation) { if (it.active) it.copy(status = DownloadStatus.QUEUED) else it }
            }
            throw error
        } catch (_: DownloadBlockedException) {
            store.update(id, generation) { it.copy(status = DownloadStatus.PAUSED, failure = DownloadFailure.BLOCKED) }
            Result.success()
        } catch (error: Exception) {
            val failure = when (error) {
                is UnsupportedDownloadException -> DownloadFailure.UNSUPPORTED
                is DownloadAccessException -> DownloadFailure.SIGN_IN
                is DownloadStorageException, is SecurityException -> DownloadFailure.STORAGE
                else -> DownloadFailure.NETWORK
            }
            val retry = failure == DownloadFailure.NETWORK && runAttemptCount < 3
            try { store.update(id, generation) { if (it.active) it.copy(status = if (retry) DownloadStatus.QUEUED else DownloadStatus.FAILED, failure = failure) else it } }
            catch (_: Exception) { return@withContext Result.failure() }
            if (retry) Result.retry() else Result.failure()
        }
    }

    private fun foreground(): ForegroundInfo {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("downloads", context.getString(R.string.downloads), NotificationManager.IMPORTANCE_LOW))
        val intent = Intent(context, MainActivity::class.java).putExtra("open_downloads", true)
        val pending = PendingIntent.getActivity(context, 71, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, "downloads")
            .setSmallIcon(R.drawable.ic_download).setContentTitle(context.getString(R.string.downloads))
            .setContentText(context.getString(R.string.download_notification)).setContentIntent(pending)
            .setOngoing(true).setOnlyAlertOnce(true).setProgress(0, 0, true).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(7101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(7101, notification)
    }
}

private class DownloadStorageException : IOException("Download storage unavailable")
private class DownloadBlockedException : IOException("Download blocked")
