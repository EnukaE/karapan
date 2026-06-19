package com.example.widget
import android.appwidget.*
import android.content.*
import android.widget.RemoteViews
import com.example.R
import com.example.data.ChecklistDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.today_widget)
            views.setTextViewText(R.id.widget_title, "Today's Agenda")
            appWidgetManager.updateAppWidget(id, views)
        }
    }
    companion object {
        fun triggerRefresh(context: Context) {
            val intent = Intent(context, TodayWidgetProvider::class.java).apply { action = AppWidgetManager.ACTION_APPWIDGET_UPDATE }
            context.sendBroadcast(intent)
        }
    }
}
