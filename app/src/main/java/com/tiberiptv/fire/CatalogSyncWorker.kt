package com.tiberiptv.fire

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CatalogSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val label = RemoteLabels.BACKGROUND_SYNC
            if (!RemoteActionGuard.tryAcquire(label)) {
                return@withContext Result.retry()
            }
            try {
                TiberNetwork.initialize(applicationContext)
                val credentials = CredentialStore(applicationContext).load()
                if (!credentials.isComplete()) {
                    AppStateStore(applicationContext).markBackgroundSyncSuccess(refreshedModes = 0)
                    return@withContext Result.success()
                }
                val api = XtreamApi(credentials)
                val stateStore = AppStateStore(applicationContext)
                stateStore.markBackgroundSyncStarted()
                val refreshedModes = syncModes(api, stateStore)
                if (refreshedModes == 0 && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    stateStore.markBackgroundSyncFailure("Aucun contenu reçu, nouvel essai prévu")
                    Result.retry()
                } else {
                    stateStore.markBackgroundSyncSuccess(refreshedModes)
                    Result.success()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                AppStateStore(applicationContext).markBackgroundSyncFailure(
                    exception.message ?: exception.javaClass.simpleName
                )
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            } finally {
                RemoteActionGuard.release(label)
            }
        }

    private suspend fun syncModes(api: XtreamApi, stateStore: AppStateStore): Int {
        var refreshedModes = 0
        for (mode in BACKGROUND_SYNC_MODES) {
            val rows = CatalogSyncEngine.fetchRows(api, mode)
            if (rows.isEmpty()) {
                continue
            }
            stateStore.saveRows(mode.name, rows)
            CatalogSyncEngine.hydrateDetails(api, stateStore, mode, rows)
            PosterImages.warmCatalog(applicationContext, rows)
            refreshedModes += 1
        }
        return refreshedModes
    }

    private companion object {
        val BACKGROUND_SYNC_MODES: List<Mode> = listOf(Mode.MOVIES, Mode.SERIES, Mode.LIVE)
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
