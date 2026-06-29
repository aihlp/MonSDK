package com.medmonitoring.core.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.medmonitoring.app.MainActivity
import com.medmonitoring.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AiAnalysisForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val programId = intent?.getStringExtra(EXTRA_PROGRAM_ID).orEmpty().ifBlank { null }
        val question = intent?.getStringExtra(EXTRA_QUESTION).orEmpty().trim()
        Log.i(TAG, "Foreground AI service started programId=$programId question=${question.isNotBlank()}")
        startForegroundCompat(getString(R.string.ai_analysis_running))
        scope.launch {
            val result = runCatching {
                val runner = AiBackgroundAnalysisRunner(applicationContext, programId)
                if (question.isBlank()) runner.run() else runner.runQuestion(question)
            }.getOrElse { error ->
                Log.e(TAG, "Foreground AI run failed", error)
                AiBackgroundAnalysisResult.Unavailable(getString(R.string.ai_status_analysis_stopped))
            }
            when (result) {
                is AiBackgroundAnalysisResult.Ready -> updateNotification(getString(R.string.ai_analysis_ready))
                is AiBackgroundAnalysisResult.BasicReady -> updateNotification(getString(R.string.ai_status_analysis_ready))
                is AiBackgroundAnalysisResult.Unavailable -> updateNotification(result.reason)
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(text: String) {
        val notification = createNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotification(text: String): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.ai_notification_channel_analysis), NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.ai_notification_title_analysis))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAiChatIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun openAiChatIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.AI_CHAT_DEEP_LINK), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_DESTINATION, MainActivity.DESTINATION_AI_CHAT)
        return PendingIntent.getActivity(
            this,
            OPEN_AI_CHAT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "AiAnalysisService"
        private const val CHANNEL_ID = "ai_analysis"
        private const val NOTIFICATION_ID = 3101
        private const val OPEN_AI_CHAT_REQUEST_CODE = 3102
        private const val EXTRA_PROGRAM_ID = "program_id"
        private const val EXTRA_QUESTION = "question"

        fun startAnalysis(context: Context, programId: String) {
            start(context, programId, null)
        }

        fun startQuestion(context: Context, programId: String, question: String) {
            start(context, programId, question)
        }

        private fun start(context: Context, programId: String, question: String?) {
            val intent = Intent(context, AiAnalysisForegroundService::class.java)
                .putExtra(EXTRA_PROGRAM_ID, programId)
                .putExtra(EXTRA_QUESTION, question.orEmpty())
            Log.i(TAG, "Requesting foreground AI service programId=$programId question=${!question.isNullOrBlank()}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
