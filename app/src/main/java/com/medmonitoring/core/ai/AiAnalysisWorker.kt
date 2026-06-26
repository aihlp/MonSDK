package com.medmonitoring.core.ai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.medmonitoring.app.MainActivity
import com.medmonitoring.app.R
import com.medmonitoring.core.storage.db.DatabaseMigrations
import com.medmonitoring.core.storage.db.MedDatabase
import java.util.UUID
import java.util.concurrent.TimeUnit

class AiAnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (isLocalModelMode()) {
            setForeground(createForegroundInfo(applicationContext.getString(R.string.ai_analysis_running)))
        }
        val result = AiBackgroundAnalysisRunner(applicationContext).run()
        if (result is AiBackgroundAnalysisResult.Ready) {
            updateForeground(applicationContext.getString(R.string.ai_analysis_ready))
            if (result.notifyWhenReady) {
                showMotivation(result.response.notification?.body?.ifBlank { null } ?: applicationContext.getString(R.string.ai_analysis_ready))
            }
        }
        return Result.success()
    }

    private suspend fun isLocalModelMode(): Boolean {
        val db = Room.databaseBuilder(applicationContext, MedDatabase::class.java, DATABASE_NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            .enableMultiInstanceInvalidation()
            .build()
        return try {
            db.aiSettingsDao().get()?.mode == AiSettingsContract.MODE_LOCAL_MODEL
        } finally {
            db.close()
        }
    }

    private suspend fun updateForeground(text: String) {
        runCatching { setForeground(createForegroundInfo(text)) }
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val channelId = "ai_analysis"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, applicationContext.getString(R.string.ai_notification_channel_analysis), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.ai_notification_title_analysis))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAiChatIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ANALYSIS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ANALYSIS_NOTIFICATION_ID, notification)
        }
    }

    private fun showMotivation(text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val channelId = "ai_motivation"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, applicationContext.getString(R.string.ai_notification_channel_motivation), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.ai_notification_title_health_insights))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAiChatIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(UUID.randomUUID().hashCode(), notification)
    }

    private fun openAiChatIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.AI_CHAT_DEEP_LINK), applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_DESTINATION, MainActivity.DESTINATION_AI_CHAT)
        return PendingIntent.getActivity(
            applicationContext,
            OPEN_AI_CHAT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val UNIQUE_WORK = "ai_daily_analysis"
        const val MANUAL_WORK = "ai_manual_analysis"
        private const val ANALYSIS_NOTIFICATION_ID = 3101
        private const val OPEN_AI_CHAT_REQUEST_CODE = 3102
        private const val DATABASE_NAME = "med_database"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AiAnalysisWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AiAnalysisWorker>().build()
            )
        }
    }
}
