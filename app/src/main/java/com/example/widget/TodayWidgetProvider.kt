package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.ChecklistDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_REFRESH -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, TodayWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            }
            ACTION_TOGGLE_ITEM -> {
                val itemId = intent.getIntExtra(EXTRA_ITEM_ID, -1)
                val currentStatus = intent.getBooleanExtra(EXTRA_CURRENT_STATUS, false)
                if (itemId != -1) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val database = ChecklistDatabase.getDatabase(context)
                            val dao = database.checklistDao()
                            // Toggle item completion in DB
                            dao.updateItemCompletion(itemId, !currentStatus)
                            
                            // Immediately broadcast widget update
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            val componentName = ComponentName(context, TodayWidgetProvider::class.java)
                            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                            for (appWidgetId in appWidgetIds) {
                                updateWidget(context, appWidgetManager, appWidgetId)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.example.widget.ACTION_REFRESH"
        const val ACTION_TOGGLE_ITEM = "com.example.widget.ACTION_TOGGLE_ITEM"
        const val EXTRA_ITEM_ID = "com.example.widget.EXTRA_ITEM_ID"
        const val EXTRA_CURRENT_STATUS = "com.example.widget.EXTRA_CURRENT_STATUS"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = ChecklistDatabase.getDatabase(context)
                    val dao = database.checklistDao()

                    // Fetch items from database
                    val allItems = dao.getAllItems().first()
                    val checklists = dao.getAllChecklists().first()

                    val todayChecklist = checklists.find { it.name == "Today's Focus Tasks" }

                    // Check if parent checklists have a due date for today
                    val cal = java.util.Calendar.getInstance()
                    val todayYear = cal.get(java.util.Calendar.YEAR)
                    val todayDay = cal.get(java.util.Calendar.DAY_OF_YEAR)

                    fun isTimestampToday(timestamp: Long?): Boolean {
                        if (timestamp == null) return false
                        val itemCal = java.util.Calendar.getInstance()
                        itemCal.timeInMillis = timestamp
                        return todayYear == itemCal.get(java.util.Calendar.YEAR) && todayDay == itemCal.get(java.util.Calendar.DAY_OF_YEAR)
                    }

                    val checklistsScheduledForToday = checklists.filter { isTimestampToday(it.dueDate) }.map { it.id }.toSet()

                    // Match today items
                    val todayItems = if (todayChecklist == null) emptyList() else allItems.filter { it.checklistId == todayChecklist.id }
                    val syncedTodayItems = allItems.filter { it.checklistId != todayChecklist?.id && it.isAddedToToday }
                    val externalTodayItems = allItems.filter { 
                        it.checklistId != todayChecklist?.id && 
                        !it.isAddedToToday && 
                        ((it.dueDate != null && isTimestampToday(it.dueDate)) || checklistsScheduledForToday.contains(it.checklistId))
                    }

                    // Total active/incomplete items for today
                    val combinedTodayList = (todayItems + syncedTodayItems + externalTodayItems).filter { !it.isCompleted }
                    val pendingCount = combinedTodayList.size

                    val views = RemoteViews(context.packageName, R.layout.today_widget)

                    // Update header badge
                    views.setTextViewText(R.id.widget_count, "$pendingCount pending")

                    if (pendingCount == 0) {
                        views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_items_container, View.GONE)
                    } else {
                        views.setViewVisibility(R.id.widget_empty_text, View.GONE)
                        views.setViewVisibility(R.id.widget_items_container, View.VISIBLE)

                        // Populate rows (max 5)
                        val rowIds = arrayOf(
                            R.id.widget_item_row_1,
                            R.id.widget_item_row_2,
                            R.id.widget_item_row_3,
                            R.id.widget_item_row_4,
                            R.id.widget_item_row_5
                        )
                        val checkIds = arrayOf(
                            R.id.widget_item_check_1,
                            R.id.widget_item_check_2,
                            R.id.widget_item_check_3,
                            R.id.widget_item_check_4,
                            R.id.widget_item_check_5
                        )
                        val textIds = arrayOf(
                            R.id.widget_item_text_1,
                            R.id.widget_item_text_2,
                            R.id.widget_item_text_3,
                            R.id.widget_item_text_4,
                            R.id.widget_item_text_5
                        )

                        for (i in 0 until 5) {
                            if (i < combinedTodayList.size) {
                                val item = combinedTodayList[i]
                                views.setViewVisibility(rowIds[i], View.VISIBLE)
                                
                                var cleanText = item.text
                                if (cleanText.startsWith("[CL_SHORTCUT:")) {
                                    val bracketIdx = cleanText.indexOf("]")
                                    if (bracketIdx != -1) {
                                        cleanText = "📋 " + cleanText.substring(bracketIdx + 1).trim()
                                    }
                                } else if (cleanText.startsWith("[CALL_SHORTCUT:")) {
                                    val bracketIdx = cleanText.indexOf("]")
                                    if (bracketIdx != -1) {
                                        cleanText = "📞 " + cleanText.substring(bracketIdx + 1).trim()
                                    }
                                } else if (cleanText.startsWith("[WA_SHORTCUT:")) {
                                    val bracketIdx = cleanText.indexOf("]")
                                    if (bracketIdx != -1) {
                                        cleanText = "💬 " + cleanText.substring(bracketIdx + 1).trim()
                                    }
                                }
                                
                                views.setTextViewText(textIds[i], cleanText)
                                views.setTextViewText(checkIds[i], if (item.isCompleted) "☑ " else "○ ")

                                // Click action to toggle item completion directly from widget
                                val toggleIntent = Intent(context, TodayWidgetProvider::class.java).apply {
                                    action = ACTION_TOGGLE_ITEM
                                    putExtra(EXTRA_ITEM_ID, item.id)
                                    putExtra(EXTRA_CURRENT_STATUS, item.isCompleted)
                                }
                                val togglePendingIntent = PendingIntent.getBroadcast(
                                    context,
                                    item.id,
                                    toggleIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                views.setOnClickPendingIntent(rowIds[i], togglePendingIntent)
                            } else {
                                views.setViewVisibility(rowIds[i], View.GONE)
                            }
                        }
                    }

                    // Intent to open Main App
                    val openAppIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val openAppPendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_empty_text, openAppPendingIntent)

                    // Intent to trigger manual widget refresh
                    val refreshIntent = Intent(context, TodayWidgetProvider::class.java).apply {
                        action = ACTION_WIDGET_REFRESH
                    }
                    val refreshPendingIntent = PendingIntent.getBroadcast(
                        context,
                        101,
                        refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

                    // Intent to open Main App and focus quick add task field
                    val quickAddIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("focus_add_task", true)
                    }
                    val quickAddPendingIntent = PendingIntent.getActivity(
                        context,
                        102,
                        quickAddIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_add, quickAddPendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun triggerRefresh(context: Context) {
            val refreshIntent = Intent(context, TodayWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_REFRESH
            }
            context.sendBroadcast(refreshIntent)
        }
    }
}
