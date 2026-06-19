package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.example.data.ChecklistDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, TodayWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.today_widget)

            val db = ChecklistDatabase.getDatabase(context)
            val items = db.checklistDao().getAllItems().first()
            val now = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayStr = sdf.format(Date(now))

            val todayItems = items.filter { item ->
                !item.isCompleted && (item.isAddedToToday || (item.dueDate != null && sdf.format(Date(item.dueDate)) == todayStr))
            }

            if (todayItems.isEmpty()) {
                views.setTextViewText(R.id.widget_title, "No tasks for today! 🎉")
            } else {
                val text = todayItems.joinToString("\n") { "• ${it.text}" }
                views.setTextViewText(R.id.widget_title, "Today's Tasks:\n$text")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun triggerRefresh(context: Context) {
            val intent = Intent(context, TodayWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            context.sendBroadcast(intent)
        }
    }
}
