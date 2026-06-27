package com.medmonitoring.core.ai

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class AiModelDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        setForeground(createForegroundInfo("Preparing model download", 0, 0))
        setProgress(Data.Builder().putString(KEY_MODEL_ID, id).putLong(KEY_DOWNLOADED, 0L).putLong(KEY_TOTAL, 0L).build())
        val repository = EntryPointAccessors.fromApplication(applicationContext, DownloadEntryPoint::class.java).aiProfileRepository()
        var lastPublishedBytes = 0L
        var lastPublishedAt = 0L
        val result = repository.downloadModel(id) { downloaded, total ->
            val now = System.currentTimeMillis()
            if (downloaded != total && downloaded - lastPublishedBytes < PROGRESS_STEP_BYTES && now - lastPublishedAt < PROGRESS_INTERVAL_MS) {
                return@downloadModel
            }
            lastPublishedBytes = downloaded
            lastPublishedAt = now
            setProgress(Data.Builder().putString(KEY_MODEL_ID, id).putLong(KEY_DOWNLOADED, downloaded).putLong(KEY_TOTAL, total).build())
            setForeground(createForegroundInfo("Downloading AI model", downloaded, total))
        }
        return if (result.isSuccess) Result.success() else Result.failure()
    }

    private fun createForegroundInfo(title: String, downloaded: Long, total: Long): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AI model downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (total > 0L) {
            builder.setProgress(100, ((downloaded * 100) / total).toInt().coerceIn(0, 100), false)
                .setContentText("${((downloaded * 100) / total).coerceIn(0, 100)}%")
        } else {
            builder.setProgress(0, 0, true)
        }
        return ForegroundInfo(
            NOTIFICATION_ID,
            builder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadEntryPoint { fun aiProfileRepository(): AiProfileRepository }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val MODEL_TAG_PREFIX = "ai-model-download-model-"
        fun enqueue(context: Context, modelId: String) {
            val request = OneTimeWorkRequestBuilder<AiModelDownloadWorker>()
                .setInputData(Data.Builder().putString(KEY_MODEL_ID, modelId).build())
                .addTag(TAG)
                .addTag(MODEL_TAG_PREFIX + modelId)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // REPLACE lets a user explicitly retry immediately. The repository preserves
            // the .part file and resumes it with HTTP Range rather than starting over.
            WorkManager.getInstance(context).enqueueUniqueWork("ai-model-download-$modelId", ExistingWorkPolicy.REPLACE, request)
        }
        const val TAG = "ai-model-download"
        private const val CHANNEL_ID = "ai_model_download"
        private const val NOTIFICATION_ID = 4107
        private const val PROGRESS_STEP_BYTES = 1_048_576L
        private const val PROGRESS_INTERVAL_MS = 750L
    }
}
