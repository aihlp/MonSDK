package com.medmonitoring.core.ingestion

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.medmonitoring.core.domain.repository.EventRepository
import com.medmonitoring.core.domain.model.SourceType
import com.medmonitoring.core.normalization.NormalizerService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

private val Context.syncDataStore by preferencesDataStore("health_sync")

class PeriodicSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        val source = dependencies.healthConnectDataSource()
        if (!dependencies.collectionSettings().state.value.automaticCollectionEnabled) {
            return Result.success()
        }
        if (!source.isAvailable() || !source.hasPermissions()) return Result.success()

        return try {
            val key = stringPreferencesKey("changes_token_${source.sourceType.name}")
            val storedToken = applicationContext.syncDataStore.data.first()[key]
            val token = if (storedToken == null) {
                source.createChangesToken().also { initial ->
                    source.readInitialSnapshot(Instant.now().minus(Duration.ofDays(30)))
                        .forEach { dependencies.repository().insertRawEvent(it) }
                    dependencies.normalizer().processPendingEvents()
                    applicationContext.syncDataStore.edit { it[key] = initial }
                }
            } else {
                storedToken
            }
            val changes = source.readChanges(token)
            if (changes.tokenExpired) {
                dependencies.repository().deleteRecordsBySourceType(source.sourceType)
                source.readInitialSnapshot(Instant.now().minus(Duration.ofDays(30)))
                    .forEach { dependencies.repository().insertRawEvent(it) }
            }
            changes.upsertions.forEach { dependencies.repository().insertRawEvent(it) }
            changes.deletedSourceIds.forEach { dependencies.normalizer().deleteSourceRecord(it) }
            dependencies.normalizer().processPendingEvents()
            applicationContext.syncDataStore.edit { it[key] = changes.nextToken }
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK = "health-connect-periodic-sync"

        fun schedule(context: Context, intervalMinutes: Long = 60) {
            val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
                intervalMinutes.coerceAtLeast(15),
                TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }

        suspend fun resetChangesToken(context: Context) {
            context.syncDataStore.edit { it.remove(stringPreferencesKey("changes_token_${SourceType.HEALTH_CONNECT.name}")) }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun healthConnectDataSource(): HealthConnectDataSource
    fun repository(): EventRepository
    fun normalizer(): NormalizerService
    fun collectionSettings(): com.medmonitoring.core.settings.CollectionSettings
}
