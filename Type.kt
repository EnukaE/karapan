package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.ChecklistDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

object AlarmScheduler {

    fun isReminderActiveForDate(
        startDate: Long?,
        isReminderEnabled: Boolean,
        repeatInterval: String?,
        targetTimeInMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isReminderEnabled || startDate == null) return false

        val startCal = Calendar.getInstance().apply { timeInMillis = startDate }
        val targetCal = Calendar.getInstance().apply { timeInMillis = targetTimeInMillis }

        val startCompare = Calendar.getInstance().apply {
            timeInMillis = startDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCompare = Calendar.getInstance().apply {
            timeInMillis = targetTimeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (targetCompare.before(startCompare)) return false

        return when (repeatInterval?.lowercase()?.trim()) {
            "daily" -> true
            "weekly" -> startCal.get(Calendar.DAY_OF_WEEK) == targetCal.get(Calendar.DAY_OF_WEEK)
            "monthly" -> startCal.get(Calendar.DAY_OF_MONTH) == targetCal.get(Calendar.DAY_OF_MONTH) ||
                         (startCal.get(Calendar.DAY_OF_MONTH) > targetCal.getActualMaximum(Calendar.DAY_OF_MONTH) &&
                          targetCal.get(Calendar.DAY_OF_MONTH) == targetCal.getActualMaximum(Calendar.DAY_OF_MONTH))
            else -> { // "none" or null or empty
                startCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                startCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
            }
        }
    }

    fun getNextTriggerTime(
        startDate: Long?,
        isReminderEnabled: Boolean,
        isAllDay: Boolean,
        reminderTime: String?, // "HH:mm"
        repeatInterval: String?
    ): Long? {
        if (!isReminderEnabled || startDate == null) return null

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = startDate

        val (hour, minute) = if (isAllDay) {
            Pair(9, 0) // default all day alerts trigger at 9 AM
        } else {
            val parts = reminderTime?.split(":")
            val h = parts?.getOrNull(0)?.toIntOrNull() ?: 9
            val m = parts?.getOrNull(1)?.toIntOrNull() ?: 0
            Pair(h, m)
        }

        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val interval = repeatInterval?.lowercase()?.trim() ?: "none"
        if (interval == "none" || interval.isEmpty()) {
            return if (cal.timeInMillis > now) cal.timeInMillis else null
        }

        while (cal.timeInMillis <= now) {
            when (interval) {
                "daily" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                "weekly" -> cal.add(Calendar.DAY_OF_YEAR, 7)
                "monthly" -> cal.add(Calendar.MONTH, 1)
                else -> cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return cal.timeInMillis
    }

    fun scheduleAlarm(context: Context, id: String, title: String, message: String, triggerTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Don't set alarms in the past
        if (triggerTime <= System.currentTimeMillis()) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_TRIGGER_ALERT"
            putExtra("alert_id", id)
            putExtra("alert_title", title)
            putExtra("alert_message", message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            // Handle edgecases for systems restricting exact alarms gracefully
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelAlarm(context: Context, id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_TRIGGER_ALERT"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun rescheduleAllAlarms(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = ChecklistDatabase.getDatabase(context)
                val dao = db.checklistDao()
                val checklists = dao.getAllChecklists().first()
                val now = System.currentTimeMillis()

                for (checklist in checklists) {
                    // Skip templates
                    if (checklist.isTemplate) continue

                    val items = dao.getItemsForChecklistDirect(checklist.id)
                    val isAllCompleted = items.isNotEmpty() && items.all { it.isCompleted }

                    if (!isAllCompleted) {
                        if (checklist.isReminderEnabled) {
                            val nextTrigger = getNextTriggerTime(
                                startDate = checklist.dueDate,
                                isReminderEnabled = checklist.isReminderEnabled,
                                isAllDay = checklist.isAllDay,
                                reminderTime = checklist.reminderTime,
                                repeatInterval = checklist.repeatInterval
                            )
                            if (nextTrigger != null && nextTrigger > now) {
                                scheduleAlarm(
                                    context = context,
                                    id = "list_past_due_${checklist.id}",
                                    title = "⏰ Checklist Reminder",
                                    message = "Checklist '${checklist.name}' reminder is active!",
                                    triggerTime = nextTrigger
                                )
                            } else {
                                cancelAlarm(context, "list_past_due_${checklist.id}")
                            }
                        } else {
                            // Check entire checklist due dates fallback
                            checklist.dueDate?.let { due ->
                                if (due > now) {
                                    scheduleAlarm(
                                        context = context,
                                        id = "list_past_due_${checklist.id}",
                                        title = "⚠️ Checklist Overdue",
                                        message = "'${checklist.name}' checklist was due!",
                                        triggerTime = due
                                    )
                                } else {
                                    cancelAlarm(context, "list_past_due_${checklist.id}")
                                }
                            } ?: cancelAlarm(context, "list_past_due_${checklist.id}")
                        }
                    } else {
                        cancelAlarm(context, "list_past_due_${checklist.id}")
                    }

                    // Check individual item reminders
                    for (item in items) {
                        if (item.isCompleted) {
                            cancelAlarm(context, "item_past_due_${item.id}")
                            continue
                        }

                        if (item.isReminderEnabled) {
                            val nextTrigger = getNextTriggerTime(
                                startDate = item.dueDate,
                                isReminderEnabled = item.isReminderEnabled,
                                isAllDay = item.isAllDay,
                                reminderTime = item.reminderTime,
                                repeatInterval = item.repeatInterval
                            )
                            if (nextTrigger != null && nextTrigger > now) {
                                scheduleAlarm(
                                    context = context,
                                    id = "item_past_due_${item.id}",
                                    title = "⏰ Checkpoint Reminder",
                                    message = "Checkpoint '${item.text}' is scheduled now!",
                                    triggerTime = nextTrigger
                                )
                            } else {
                                cancelAlarm(context, "item_past_due_${item.id}")
                            }
                        } else {
                            item.dueDate?.let { itemDue ->
                                if (itemDue > now) {
                                    scheduleAlarm(
                                        context = context,
                                        id = "item_past_due_${item.id}",
                                        title = "⚠️ Checkpoint Overdue",
                                        message = "Item '${item.text}' is PAST DUE!",
                                        triggerTime = itemDue
                                    )
                                } else {
                                    cancelAlarm(context, "item_past_due_${item.id}")
                                }
                            } ?: cancelAlarm(context, "item_past_due_${item.id}")
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently handle exceptions in coroutine
            }
        }
    }
}
