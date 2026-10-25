package io.github.antocanza.iptvreceiver.data

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.antocanza.iptvreceiver.ReceiverApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CacheCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val inForeground = withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        if (inForeground) return@withContext Result.retry()
        val app = applicationContext as ReceiverApplication
        val now = System.currentTimeMillis()
        val cutoff = now - TimeUnit.DAYS.toMillis(1)
        pruneExpiredTempFiles(app.cacheDir.resolve("tmp"), cutoff)
        pruneExpiredTempFiles(app.cacheDir.resolve("temporary"), cutoff)
        val preferences = app.getSharedPreferences("cache_maintenance", Context.MODE_PRIVATE)
        if (now - preferences.getLong("artwork_cleared_at", now) >= TimeUnit.DAYS.toMillis(7)) {
            app.clearArtworkDiskCaches()
            preferences.edit { putLong("artwork_cleared_at", now) }
        }
        Result.success()
    }
}

internal fun scheduleCacheCleanup(context: Context) {
    val preferences = context.getSharedPreferences("cache_maintenance", Context.MODE_PRIVATE)
    if (!preferences.contains("artwork_cleared_at")) preferences.edit { putLong("artwork_cleared_at", System.currentTimeMillis()) }
    val work = PeriodicWorkRequestBuilder<CacheCleanupWorker>(1, TimeUnit.DAYS, 6, TimeUnit.HOURS)
        .setInitialDelay(1, TimeUnit.DAYS)
        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("app-cache-cleanup", ExistingPeriodicWorkPolicy.KEEP, work)
}
