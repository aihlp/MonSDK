package com.medmonitoring.core.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.medmonitoring.app.MainActivity
import com.medmonitoring.app.R
import com.medmonitoring.app.di.AndroidStringProvider
import com.medmonitoring.core.ai.AiChatRepository
import com.medmonitoring.core.program.ProgramModuleDefinition
import com.medmonitoring.core.storage.db.DatabaseMigrations
import com.medmonitoring.core.storage.db.MedDatabase
import com.medmonitoring.core.storage.entity.ReminderEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var programModule: ProgramModuleDefinition

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("label") ?: context.getString(R.string.reminder)
        val type = intent.getStringExtra("type").orEmpty()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "medmonitor_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.reminders),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(MainActivity.AI_CHAT_DEEP_LINK), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_DESTINATION, MainActivity.DESTINATION_AI_CHAT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.reminder_notification_text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        notificationManager.notify(UUID.randomUUID().hashCode(), builder.build())

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val db = Room.databaseBuilder(context.applicationContext, MedDatabase::class.java, "med_database")
                .addMigrations(*DatabaseMigrations.ALL)
                .enableMultiInstanceInvalidation()
                .build()
            try {
                AiChatRepository(
                    db,
                    AndroidStringProvider(context.applicationContext),
                    programModule.program
                ).postReminderNotification(title, type)
            } finally {
                db.close()
                pendingResult.finish()
            }
        }
    }
}

@AndroidEntryPoint
class RebootReceiver : BroadcastReceiver() {
    @Inject lateinit var db: MedDatabase

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        CoroutineScope(Dispatchers.IO).launch {
            db.reminderDao().getReminders().filter { it.enabled }.forEach {
                scheduleAlarm(context, alarmManager, it)
            }
        }
    }
}

fun scheduleAlarm(context: Context, alarmManager: AlarmManager, reminder: ReminderEntity) {
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("label", reminder.label)
        putExtra("type", reminder.type)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        reminder.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, reminder.hour)
        set(Calendar.MINUTE, reminder.minute)
        set(Calendar.SECOND, 0)
        if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
    }
    alarmManager.setRepeating(
        AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        AlarmManager.INTERVAL_DAY,
        pendingIntent
    )
}

fun cancelAlarm(context: Context, alarmManager: AlarmManager, reminder: ReminderEntity) {
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        reminder.id.hashCode(),
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}
