package com.tiberiptv.fire

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object CatalogSyncScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    fun enqueueNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<CatalogSyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun preemptRunningBackgroundSync(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        RemoteActionGuard.release(RemoteLabels.BACKGROUND_SYNC)
        schedule(context)
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private const val PERIODIC_WORK_NAME = "catalog_background_sync"
    private const val ONE_TIME_WORK_NAME = "catalog_background_sync_now"
    private const val WORK_TAG = "catalog_sync"
    private const val REPEAT_INTERVAL_HOURS = 6L
    private const val BACKOFF_DELAY_MINUTES = 30L
}
