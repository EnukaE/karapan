package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.data.ChecklistDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Phone rebooted, reschedule all active alarms
                AlarmScheduler.rescheduleAllAlarms(context)
            }
            "com.example.ACTION_TRIGGER_ALERT" -> {
                val alertId = intent.getStringExtra("alert_id") ?: return
                val title = intent.getStringExtra("alert_title") ?: "Checklist Alarm"
                val message = intent.getStringExtra("alert_message") ?: "A checklist item is due!"

                showHeadsUpNotification(context, alertId, title, message)
            }
            "com.example.ACTION_DISMISS_ALERT_BG" -> {
                val alertId = intent.getStringExtra("alert_id") ?: return
                cancelNotification(context, alertId)
            }
            "com.example.ACTION_COMPLETE_ALERT_BG" -> {
                val alertId = intent.getStringExtra("alert_id") ?: return
                cancelNotification(context, alertId)

                // Update completion directly in database
                CoroutineScope(Dispatchers.IO).launch {
                    val db = ChecklistDatabase.getDatabase(context)
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val dateStr = sdf.format(java.util.Date(System.currentTimeMillis()))
                    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)

                    if (alertId.contains("item_")) {
                        val idStr = alertId.substringAfterLast("_")
                        val itemId = idStr.toIntOrNull()
                        if (itemId != null) {
                            prefs.edit().putString("item_completed_date_$itemId", dateStr).apply()
                            db.checklistDao().updateItemCompletion(itemId, true)
                        }
                    } else if (alertId.contains("list_")) {
                        val idStr = alertId.substringAfterLast("_")
                        val listId = idStr.toIntOrNull()
                        if (listId != null) {
                            val items = db.checklistDao().getItemsForChecklistDirect(listId)
                            val editor = prefs.edit()
                            for (item in items) {
                                editor.putString("item_completed_date_${item.id}", dateStr)
                                db.checklistDao().updateItemCompletion(item.id, true)
                            }
                            editor.apply()
                        }
                    }
                }
            }
        }
    }

    private fun showHeadsUpNotification(context: Context, alertId: String, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "reminders_channel"

        // Ensure channel is created with max priority & sound configured
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Checklist Reminders"
            val desc = "Provides screen-pop alerts for checklist and checkpoint schedules"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = desc
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC

                // Add default ringtone sound
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action Intent for Dismiss Button
        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = "com.example.ACTION_DISMISS_ALERT_BG"
            putExtra("alert_id", alertId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            alertId.hashCode() + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Action Intent for Complete Button
        val completeIntent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = "com.example.ACTION_COMPLETE_ALERT_BG"
            putExtra("alert_id", alertId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            alertId.hashCode() + 2,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Intent to open Main Activity when clicked
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("alert_id", alertId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            alertId.hashCode() + 3,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Built Notification with high priority to wake screen up or display Heads-up banner
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setColor(0xFF8152CB.toInt())
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
            .setFullScreenIntent(openPendingIntent, true) // Wakes up overlay and screen even if screen is locked or OFF!
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_input_delete,
                "Dismiss",
                dismissPendingIntent
            )
            .addAction(
                android.R.drawable.checkbox_on_background,
                "Complete",
                completePendingIntent
            )

        try {
            notificationManager.notify(alertId.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    private fun cancelNotification(context: Context, alertId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(alertId.hashCode())
    }
}
