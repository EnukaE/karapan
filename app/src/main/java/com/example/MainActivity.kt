package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.ChecklistDatabase
import com.example.data.ChecklistRepository
import com.example.ui.ChecklistScreen
import com.example.ui.ChecklistViewModel
import com.example.ui.ChecklistViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val database by lazy { ChecklistDatabase.getDatabase(applicationContext) }
    private val repository by lazy { ChecklistRepository(database.checklistDao()) }
    private val factory by lazy { ChecklistViewModelFactory(repository, applicationContext) }
    private val viewModel: ChecklistViewModel by viewModels { factory }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.ACTION_DISMISS_ALERT") {
                val alertId = intent.getStringExtra("alert_id")
                if (alertId != null) {
                    viewModel.dismissAlert(alertId, context)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create High-Priority Notification Channel for Heads-up alerts
        createNotificationChannel()

        // Reschedule background alarm schedules from Database
        AlarmScheduler.rescheduleAllAlarms(applicationContext)

        // Automatic Widget Refresh trigger on data updates (Items and checklists/projects schedules)
        lifecycleScope.launch {
            launch {
                viewModel.allItems.collect {
                    com.example.widget.TodayWidgetProvider.triggerRefresh(applicationContext)
                }
            }
            launch {
                viewModel.checklists.collect {
                    com.example.widget.TodayWidgetProvider.triggerRefresh(applicationContext)
                }
            }
        }

        // Register dismiss BroadcastReceiver safely with RECEIVER_NOT_EXPORTED for security
        val filter = IntentFilter("com.example.ACTION_DISMISS_ALERT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dismissReceiver, filter)
        }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChecklistScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.startLocationUpdates()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(dismissReceiver)
        } catch (e: Exception) {
            // Ignore if already unregistered
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "reminders_channel"
            val channelName = "Checklist Reminders"
            val channelDescription = "Provides screen-pop alerts for checklist and checkpoint schedules"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
