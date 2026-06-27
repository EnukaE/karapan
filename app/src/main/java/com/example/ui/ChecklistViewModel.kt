package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Category
import com.example.data.Checklist
import com.example.data.ChecklistItem
import com.example.data.ChecklistRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class ReminderAlert(
    val id: String,
    val title: String,
    val message: String,
    val isPastDue: Boolean,
    val itemText: String? = null,
    val checklistName: String,
    val checklistId: Int
)

class ChecklistViewModel(
    private val repository: ChecklistRepository,
    private val context: android.content.Context
) : ViewModel() {

    // --- State Expositions ---
    val categories: StateFlow<List<Category>> = repository.allCategories
        .map { list -> list.distinctBy { it.name.lowercase().trim() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val checklists: StateFlow<List<Checklist>> = repository.allChecklists
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allItems: StateFlow<List<ChecklistItem>> = repository.allItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val selectedChecklistId = MutableStateFlow<Int?>(null)
    
    // Filter by Category Selection
    val selectedFilterCategoryId = MutableStateFlow<Int?>(null)

    val simulatedLocation = MutableStateFlow("")
    val currentGpsLocation = MutableStateFlow<android.location.Location?>(null)
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private val fusedLocationClient by lazy {
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
    }

    private val _currentItems = MutableStateFlow<List<ChecklistItem>>(emptyList())
    val currentItems: StateFlow<List<ChecklistItem>> = _currentItems.asStateFlow()

    // Real-time Reminder System Alerts
    private val _reminderAlerts = MutableStateFlow<List<ReminderAlert>>(emptyList())
    val reminderAlerts: StateFlow<List<ReminderAlert>> = _reminderAlerts.asStateFlow()

    val dismissedAlertIds = MutableStateFlow<Set<String>>(emptySet())
    private val shownAlertIds = mutableSetOf<String>()

    fun dismissAlert(id: String, context: android.content.Context? = null) {
        dismissedAlertIds.update { it + id }
        updateReminderSystem()
        context?.let { ctx ->
            val notificationManager = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(id.hashCode())
        }
    }

    fun completeAlertItem(alert: ReminderAlert, context: android.content.Context? = null) {
        viewModelScope.launch {
            if (alert.id.startsWith("item_")) {
                val idStr = alert.id.substringAfterLast("_")
                val itemId = idStr.toIntOrNull()
                if (itemId != null) {
                    saveItemCompletedDate(itemId)
                    repository.updateItemCompletion(itemId, true)
                }
            } else if (alert.id.startsWith("list_")) {
                val idStr = alert.id.substringAfterLast("_")
                val listId = idStr.toIntOrNull()
                if (listId != null) {
                    val items = repository.getItemsForChecklistDirect(listId)
                    for (item in items) {
                        saveItemCompletedDate(item.id)
                        repository.updateItemCompletion(item.id, true)
                    }
                }
            }
            dismissAlert(alert.id, context)
        }
    }

    fun snoozeAlert(alert: ReminderAlert, minutes: Int, context: android.content.Context? = null) {
        viewModelScope.launch {
            dismissAlert(alert.id, context)
            // Schedule alert to show up again after delay
            viewModelScope.launch {
                kotlinx.coroutines.delay(minutes * 60 * 1000L)
                dismissedAlertIds.update { it - alert.id }
                updateReminderSystem()
            }
        }
    }

    fun postSystemNotifications(context: android.content.Context, currentAlerts: List<ReminderAlert>) {
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        for (alert in currentAlerts) {
            if (alert.id !in shownAlertIds && alert.id !in dismissedAlertIds.value) {
                shownAlertIds.add(alert.id)

                // Create intent for Dismiss action
                val dismissIntent = android.content.Intent("com.example.ACTION_DISMISS_ALERT").apply {
                    putExtra("alert_id", alert.id)
                    setPackage(context.packageName)
                }

                val dismissPendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    alert.id.hashCode(),
                    dismissIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                )

                // Intent to open Main Activity
                val openIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val openPendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val iconResId = android.R.drawable.ic_dialog_info

                val builder = androidx.core.app.NotificationCompat.Builder(context, "reminders_channel")
                    .setSmallIcon(iconResId)
                    .setColor(0xFF8152CB.toInt())
                    .setContentTitle(alert.title)
                    .setContentText(alert.message)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(openPendingIntent, true) // Makes it pop up on screen like a normal alarm notification!
                    .setContentIntent(openPendingIntent)
                    .setAutoCancel(true)
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Dismiss",
                        dismissPendingIntent
                    )

                try {
                    notificationManager.notify(alert.id.hashCode(), builder.build())
                } catch (e: SecurityException) {
                    // Fail gracefully if permission lacking
                }
            }
        }
    }

    // Ensure "Projects" category and system lists exist in the database
    private suspend fun ensureSystemEntitiesExist() {
        // Ensure "Projects" category exists
        val currentCats = repository.allCategories.first()
        val projectsCategory = currentCats.find { it.name.lowercase().trim() == "projects" }
        val projectsCatId = if (projectsCategory == null) {
            repository.insertCategory(Category(name = "Projects", color = "#3F51B5"))
        } else {
            projectsCategory.id.toLong()
        }

        // Ensure "Tasks & Todo" category exists
        val todoCategory = currentCats.find { it.name.lowercase().trim() == "tasks & todo" || it.name.lowercase().trim() == "todo" }
        val todoCatId = if (todoCategory == null) {
            repository.insertCategory(Category(name = "Tasks & Todo", color = "#4CAF50"))
        } else {
            todoCategory.id.toLong()
        }

        // Ensure "General Todo List" exists
        val currentLists = repository.allChecklists.first()
        var todoList = currentLists.find { it.name == "General Todo List" }
        if (todoList == null) {
            val todoId = repository.insertChecklist(
                Checklist(
                    name = "General Todo List",
                    icon = "✅",
                    categoryId = todoCatId.toInt(),
                    isTemplate = false
                )
            )
            // Prepopulate some sample general tasks if empty
            repository.insertItem(ChecklistItem(checklistId = todoId.toInt(), text = "Buy groceries & weekly ingredients", position = 0))
            repository.insertItem(ChecklistItem(checklistId = todoId.toInt(), text = "Schedule dentist appointment", position = 1))
        } else if (todoList.categoryId == null) {
            // Migrating existing "General Todo List" to belong to the todoCategory
            repository.updateChecklist(todoList.copy(categoryId = todoCatId.toInt()))
        }

        // Ensure "Today's Focus Tasks" exists
        var todayList = currentLists.find { it.name == "Today's Focus Tasks" }
        if (todayList == null) {
            val todayId = repository.insertChecklist(
                Checklist(
                    name = "Today's Focus Tasks",
                    icon = "📅",
                    categoryId = null,
                    isTemplate = false
                )
            )
            repository.insertItem(ChecklistItem(checklistId = todayId.toInt(), text = "Drink 8 glasses of water", position = 0))
            repository.insertItem(ChecklistItem(checklistId = todayId.toInt(), text = "Review class materials for 30 minutes", position = 1))
        }

        // Ensure "Bright Ideas Sandbox" exists
        var ideaList = currentLists.find { it.name == "Bright Ideas Sandbox" }
        if (ideaList == null) {
            val ideaId = repository.insertChecklist(
                Checklist(
                    name = "Bright Ideas Sandbox",
                    icon = "💡",
                    categoryId = null,
                    isTemplate = false
                )
            )
            repository.insertItem(
                ChecklistItem(
                    checklistId = ideaId.toInt(),
                    text = "Micro-SaaS to find unused domain names with AI",
                    position = 0,
                    isIdea = true,
                    excitementRating = 9,
                    frightRating = 3,
                    coolingOffStartedAt = System.currentTimeMillis() - 7200000
                )
            )
            repository.insertItem(
                ChecklistItem(
                    checklistId = ideaId.toInt(),
                    text = "Smart distraction blocker using brainwaves and customizable dynamic audio loops",
                    position = 1,
                    isIdea = true,
                    excitementRating = 8,
                    frightRating = 8,
                    coolingOffStartedAt = System.currentTimeMillis()
                )
            )
        }

        // Populate a sample Project if none exist in Projects category
        val updatedLists = repository.allChecklists.first()
        val projectsInCat = updatedLists.filter { it.categoryId == projectsCatId.toInt() }
        if (projectsInCat.isEmpty()) {
            val sampleProjId = repository.insertChecklist(
                Checklist(
                    name = "🚀 Web App Build",
                    icon = "💻",
                    categoryId = projectsCatId.toInt(),
                    isTemplate = false
                )
            )
            repository.insertItem(ChecklistItem(checklistId = sampleProjId.toInt(), text = "Set up git repository & branch policies", position = 0, isCompleted = true))
            repository.insertItem(ChecklistItem(checklistId = sampleProjId.toInt(), text = "Design database schema & primary keys", position = 1))
            repository.insertItem(ChecklistItem(checklistId = sampleProjId.toInt(), text = "Implement authentication endpoints", position = 2))
        }
    }

    init {
        // Ensure standard system entities are created
        viewModelScope.launch {
            ensureSystemEntitiesExist()
        }

        // Start real location updates
        startLocationUpdates()

        // Collect simulated location changes to trigger notifications
        viewModelScope.launch {
            simulatedLocation.collectLatest {
                updateReminderSystem()
            }
        }

        // Collect real GPS location changes to trigger notifications
        viewModelScope.launch {
            currentGpsLocation.collectLatest {
                updateReminderSystem()
            }
        }

        // Run database pre-population if empty, or cleanup duplicates if existing
        viewModelScope.launch {
            val list = repository.allCategories.first()
            if (list.isEmpty()) {
                prepopulateDatabase()
            } else {
                // Deduplicate categories by name representing the same logical label
                val grouped = list.groupBy { it.name.lowercase().trim() }
                grouped.forEach { (_, group) ->
                    if (group.size > 1) {
                        val mainCategory = group.first()
                        val duplicates = group.drop(1)
                        duplicates.forEach { dup ->
                            // Update checklists with duplicate category ID to mainCategory.id
                            repository.updateChecklistsCategoryId(dup.id, mainCategory.id)
                            // Delete the duplicate category
                            repository.deleteCategory(dup)
                        }
                    }
                }
            }
        }

        // Auto-select first active checklist if none selected
        viewModelScope.launch {
            checklists.collectLatest { list ->
                if (selectedChecklistId.value == null && list.isNotEmpty()) {
                    selectedChecklistId.value = list.first().id
                }
                updateReminderSystem()
            }
        }

        // Observe and stream items dynamically matching the currently selected checklist
        viewModelScope.launch {
            selectedChecklistId.collectLatest { id ->
                if (id != null) {
                    repository.getItemsForChecklist(id).collect { items ->
                        _currentItems.value = items.sortedWith(
                            compareBy<ChecklistItem> { it.isCompleted }
                                .thenBy { it.position }
                        )
                        updateReminderSystem()
                    }
                } else {
                    _currentItems.value = emptyList()
                }
            }
        }

        // Automate insertion of today's scheduled checklist shortcuts to Today's Focus Tasks
        viewModelScope.launch {
            while (isActive) {
                try {
                    val lists = repository.allChecklists.first()
                    val items = repository.allItems.first()
                    val todayList = lists.find { it.name == "Today's Focus Tasks" }
                    
                    if (todayList != null) {
                        val now = System.currentTimeMillis()
                        
                        for (checklist in lists) {
                            if (checklist.id == todayList.id || checklist.name == "Bright Ideas Sandbox") {
                                continue
                            }
                            
                            if (checklist.dueDate != null) {
                                if (isScheduledTimeReached(checklist, now)) {
                                    val shortcutTextPrefix = "[CL_SHORTCUT:${checklist.id}]"
                                    val hasShortcut = items.any { it.checklistId == todayList.id && it.text.startsWith(shortcutTextPrefix) }
                                    if (!hasShortcut) {
                                        val currentMaxPosition = items.filter { it.checklistId == todayList.id }.maxOfOrNull { it.position } ?: -1
                                        repository.insertItem(
                                            ChecklistItem(
                                                checklistId = todayList.id,
                                                text = "$shortcutTextPrefix ${checklist.name}",
                                                position = currentMaxPosition + 1
                                            )
                                        )
                                        updateReminderSystem()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors in periodic background shortcut injector
                }
                kotlinx.coroutines.delay(10000L) // check every 10 seconds
            }
        }
    }

    fun isChecklistDoneToday(checklistId: Int): Boolean {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val dateStr = sdf.format(java.util.Date(System.currentTimeMillis()))
        val key = "checklist_shortcut_done_${checklistId}_${dateStr}"
        return context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean(key, false)
    }

    fun toggleChecklistShortcutDone(item: ChecklistItem, checklistId: Int) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val dateStr = sdf.format(java.util.Date(System.currentTimeMillis()))
        val key = "checklist_shortcut_done_${checklistId}_${dateStr}"
        
        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, true)
            .apply()
            
        viewModelScope.launch {
            repository.deleteItem(item)
            updateReminderSystem()
        }
    }

    fun saveItemCompletedDate(itemId: Int, now: Long = System.currentTimeMillis()) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val dateStr = sdf.format(java.util.Date(now))
        val key = "item_completed_date_$itemId"
        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(key, dateStr)
            .apply()
    }

    fun getItemCompletedDate(itemId: Int): String? {
        val key = "item_completed_date_$itemId"
        return context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .getString(key, null)
    }

    fun clearItemCompletedDate(itemId: Int) {
        val key = "item_completed_date_$itemId"
        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    fun isScheduledTimeReached(checklist: Checklist, now: Long): Boolean {
        if (isChecklistDoneToday(checklist.id)) return false
        return isScheduledTimeReachedGeneral(
            dueDate = checklist.dueDate,
            isReminderEnabled = checklist.isReminderEnabled,
            isAllDay = checklist.isAllDay,
            reminderTime = checklist.reminderTime,
            repeatInterval = checklist.repeatInterval,
            now = now
        )
    }

    fun isScheduledTimeReachedForItem(item: ChecklistItem, now: Long): Boolean {
        val hasRepeat = !item.repeatInterval.isNullOrBlank() && item.repeatInterval!!.lowercase().trim() != "none"
        if (hasRepeat && item.isCompleted) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val todayStr = sdf.format(java.util.Date(now))
            val completedDate = getItemCompletedDate(item.id) ?: run {
                saveItemCompletedDate(item.id, now)
                todayStr
            }
            if (completedDate != todayStr) {
                // Completed on a different day! Reset it!
                viewModelScope.launch {
                    repository.updateItemCompletion(item.id, false)
                    clearItemCompletedDate(item.id)
                    updateReminderSystem()
                }
            }
        }

        return isScheduledTimeReachedGeneral(
            dueDate = item.dueDate,
            isReminderEnabled = item.isReminderEnabled,
            isAllDay = item.isAllDay,
            reminderTime = item.reminderTime,
            repeatInterval = item.repeatInterval,
            now = now
        )
    }

    fun isScheduledTimeReachedGeneral(
        dueDate: Long?,
        isReminderEnabled: Boolean,
        isAllDay: Boolean,
        reminderTime: String?,
        repeatInterval: String?,
        now: Long
    ): Boolean {
        val start = dueDate ?: return false
        
        val isDateActive = if (isReminderEnabled) {
            com.example.AlarmScheduler.isReminderActiveForDate(
                startDate = start,
                isReminderEnabled = true,
                repeatInterval = repeatInterval,
                targetTimeInMillis = now
            )
        } else {
            isTimestampTodayGeneral(start, now)
        }
        
        if (!isDateActive) return false
        
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        
        val (hour, minute) = if (isReminderEnabled) {
            if (isAllDay) {
                Pair(9, 0)
            } else {
                val parts = reminderTime?.split(":")
                val h = parts?.getOrNull(0)?.toIntOrNull() ?: 9
                val m = parts?.getOrNull(1)?.toIntOrNull() ?: 0
                Pair(h, m)
            }
        } else {
            val startCal = java.util.Calendar.getInstance().apply { timeInMillis = start }
            Pair(startCal.get(java.util.Calendar.HOUR_OF_DAY), startCal.get(java.util.Calendar.MINUTE))
        }
        
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        
        return now >= cal.timeInMillis
    }

    fun isTimestampToday(timestamp: Long?): Boolean {
        return isTimestampTodayGeneral(timestamp, System.currentTimeMillis())
    }

    fun isTimestampTodayGeneral(timestamp: Long?, now: Long): Boolean {
        if (timestamp == null) return false
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        val todayYear = cal.get(java.util.Calendar.YEAR)
        val todayDay = cal.get(java.util.Calendar.DAY_OF_YEAR)
        cal.timeInMillis = timestamp
        return todayYear == cal.get(java.util.Calendar.YEAR) && todayDay == cal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private suspend fun prepopulateDatabase() {
        // 1. Prepopulate default categories
        val catSchool = repository.insertCategory(Category(name = "School", color = "#55654C"))
        val catSocial = repository.insertCategory(Category(name = "Social", color = "#D9EABB"))
        val catTravel = repository.insertCategory(Category(name = "Travel", color = "#75786B"))
        val catPersonal = repository.insertCategory(Category(name = "Personal", color = "#131F0E"))

        // --- Class Prep Checklist ---
        val classListId = repository.insertChecklist(
            Checklist(
                name = "Before Class",
                icon = "🏫",
                categoryId = catSchool.toInt(),
                isTemplate = false,
                dueDate = System.currentTimeMillis() + 3600000 * 2 // Due in 2 hours
            )
        )
        repository.insertItem(ChecklistItem(checklistId = classListId.toInt(), text = "Confirm class schedule & local campus room", isCompleted = true, position = 0))
        repository.insertItem(ChecklistItem(checklistId = classListId.toInt(), text = "Pack textbook and spiral notebooks", isCompleted = false, position = 1))
        repository.insertItem(ChecklistItem(checklistId = classListId.toInt(), text = "Bring laptop with charger line", isCompleted = false, position = 2, dueDate = System.currentTimeMillis() - 60000)) // Item Past Due!
        repository.insertItem(ChecklistItem(checklistId = classListId.toInt(), text = "Store ID badge and card keys", isCompleted = false, position = 3))

        // --- Picnic Prep Checklist ---
        val picnicListId = repository.insertChecklist(
            Checklist(
                name = "Picnic Packing",
                icon = "🧺",
                categoryId = catSocial.toInt(),
                isTemplate = false,
                dueDate = System.currentTimeMillis() + 86400000 * 4 // Due in 4 days
            )
        )
        repository.insertItem(ChecklistItem(checklistId = picnicListId.toInt(), text = "Pack thermal blanket & cushions", position = 0))
        repository.insertItem(ChecklistItem(checklistId = picnicListId.toInt(), text = "Prepare delicious sandwiches & snacks", position = 1))
        repository.insertItem(ChecklistItem(checklistId = picnicListId.toInt(), text = "Fill cooling flasks with lemonade", position = 2))
        repository.insertItem(ChecklistItem(checklistId = picnicListId.toInt(), text = "Pack recycling bags for quick cleanup", position = 3))

        // --- Travel packing Checklist ---
        val travelingListId = repository.insertChecklist(
            Checklist(
                name = "Weekend Traveling Pack",
                icon = "✈️",
                categoryId = catTravel.toInt(),
                isTemplate = false
            )
        )
        repository.insertItem(ChecklistItem(checklistId = travelingListId.toInt(), text = "Confirm passport, flight tickets & reservations", position = 0))
        repository.insertItem(ChecklistItem(checklistId = travelingListId.toInt(), text = "Pack toiletries kit and dental paste", position = 1))
        repository.insertItem(ChecklistItem(checklistId = travelingListId.toInt(), text = "All gadget power accessories", position = 2))

        selectedChecklistId.value = classListId.toInt()
    }

    // --- Dynamic Reminder evaluating Engine ---
    fun updateReminderSystem() {
        viewModelScope.launch {
            val alerts = mutableListOf<ReminderAlert>()
            val now = System.currentTimeMillis()
            val allLists = repository.allChecklists.first()
            val userLoc = simulatedLocation.value.trim()
            val gpsLoc = currentGpsLocation.value

            for (checklist in allLists) {
                // Scheduled/Repeating reminders for checklist, task list, or project
                if (checklist.isReminderEnabled && checklist.dueDate != null) {
                    if (isScheduledTimeReached(checklist, now)) {
                        val items = repository.getItemsForChecklistDirect(checklist.id)
                        val isAllCompleted = items.isNotEmpty() && items.all { it.isCompleted }
                        if (!isAllCompleted) {
                            alerts.add(
                                ReminderAlert(
                                    id = "list_reminder_${checklist.id}",
                                    title = "⏰ Checklist Reminder",
                                    message = "Checklist '${checklist.name}' reminder is active today!",
                                    isPastDue = false,
                                    checklistName = checklist.name,
                                    checklistId = checklist.id
                                )
                            )
                        }
                    }
                } else {
                    // Fallback to standard checklist due dates
                    checklist.dueDate?.let { due ->
                        val items = repository.getItemsForChecklistDirect(checklist.id)
                        val isAllCompleted = items.isNotEmpty() && items.all { it.isCompleted }

                        if (!isAllCompleted) {
                            val diff = due - now
                            if (diff < 0) {
                                alerts.add(
                                    ReminderAlert(
                                        id = "list_past_due_${checklist.id}",
                                        title = "⚠️ Checklist Overdue",
                                        message = "'${checklist.name}' checklist was due!",
                                        isPastDue = true,
                                        checklistName = checklist.name,
                                        checklistId = checklist.id
                                    )
                                )
                            } else if (diff < 86400000) { // Due within 24h
                                val hours = (diff / 3600000).coerceAtLeast(0)
                                alerts.add(
                                    ReminderAlert(
                                        id = "list_imminent_${checklist.id}",
                                        title = "⏰ Checklist Closing Due",
                                        message = "'${checklist.name}' checklist is due in $hours hour(s).",
                                        isPastDue = false,
                                        checklistName = checklist.name,
                                        checklistId = checklist.id
                                    )
                                )
                            }
                        }
                    }
                }

                // Check entire checklist location reminder
                checklist.locationName?.let { cLoc ->
                    if (cLoc.trim().isNotEmpty()) {
                        val coords = parseCoordinates(cLoc)
                        var isNear = false
                        if (coords != null) {
                            val simCoords = parseCoordinates(userLoc)
                            val activeLat = simCoords?.first ?: gpsLoc?.latitude
                            val activeLng = simCoords?.second ?: gpsLoc?.longitude
                            if (activeLat != null && activeLng != null) {
                                val dist = calculateDistance(activeLat, activeLng, coords.first, coords.second)
                                if (dist <= 300f) { // Within 300 meters
                                    isNear = true
                                }
                            }
                        } else if (userLoc.isNotEmpty()) {
                            if (cLoc.contains(userLoc, ignoreCase = true) || userLoc.contains(cLoc, ignoreCase = true)) {
                                isNear = true
                            }
                        }

                        if (isNear) {
                            val items = repository.getItemsForChecklistDirect(checklist.id)
                            val isAllCompleted = items.isNotEmpty() && items.all { it.isCompleted }
                            if (!isAllCompleted) {
                                alerts.add(
                                    ReminderAlert(
                                        id = "list_loc_${checklist.id}",
                                        title = "📍 Location Reminder",
                                        message = "You arrived at '${checklist.locationName}'! Checklist is active: '${checklist.name}'",
                                        isPastDue = false,
                                        checklistName = checklist.name,
                                        checklistId = checklist.id
                                    )
                                )
                            }
                        }
                    }
                }

                // Check individual item due dates & location
                val items = repository.getItemsForChecklistDirect(checklist.id)
                for (item in items) {
                    if (item.isCompleted) continue
                    
                    // Scheduled/Repeating reminders for items
                    if (item.isReminderEnabled && item.dueDate != null) {
                        if (isScheduledTimeReachedForItem(item, now)) {
                            alerts.add(
                                ReminderAlert(
                                    id = "item_reminder_${item.id}",
                                    title = "⏰ Checkpoint Reminder",
                                    message = "Checkpoint '${item.text}' is active today!",
                                    isPastDue = false,
                                    itemText = item.text,
                                    checklistName = checklist.name,
                                    checklistId = checklist.id
                                )
                            )
                        }
                    } else {
                        // Fallback to standard item time checks
                        item.dueDate?.let { itemDue ->
                            val diff = itemDue - now
                            if (diff < 0) {
                                alerts.add(
                                    ReminderAlert(
                                        id = "item_past_due_${item.id}",
                                        title = "⚠️ Checkpoint Overdue",
                                        message = "Item '${item.text}' is PAST DUE!",
                                        isPastDue = true,
                                        itemText = item.text,
                                        checklistName = checklist.name,
                                        checklistId = checklist.id
                                    )
                                )
                            } else if (diff < 86400000) { // Due within 24h
                                val hours = (diff / 3600000).coerceAtLeast(0)
                                alerts.add(
                                    ReminderAlert(
                                        id = "item_imminent_${item.id}",
                                        title = "⏰ Checkpoint Imminent",
                                        message = "Item '${item.text}' is due in $hours hour(s).",
                                        isPastDue = false,
                                        itemText = item.text,
                                        checklistName = checklist.name,
                                        checklistId = checklist.id
                                    )
                                )
                            }
                        }
                    }

                    // Location checks
                    item.locationName?.let { iLoc ->
                        if (iLoc.trim().isNotEmpty()) {
                            val coords = parseCoordinates(iLoc)
                            var isNear = false
                            if (coords != null) {
                                val simCoords = parseCoordinates(userLoc)
                                val activeLat = simCoords?.first ?: gpsLoc?.latitude
                                val activeLng = simCoords?.second ?: gpsLoc?.longitude
                                if (activeLat != null && activeLng != null) {
                                    val dist = calculateDistance(activeLat, activeLng, coords.first, coords.second)
                                    if (dist <= 300f) { // Within 300 meters
                                        isNear = true
                                    }
                                }
                            } else if (userLoc.isNotEmpty()) {
                                if (iLoc.contains(userLoc, ignoreCase = true) || userLoc.contains(iLoc, ignoreCase = true)) {
                                    isNear = true
                                }
                            }

                            if (isNear) {
                                alerts.add(
                                    ReminderAlert(
                                        id = "item_loc_${item.id}",
                                        title = "📍 Location Reminder",
                                        message = "Arrived at '${item.locationName}': Don't forget to '${item.text}'!",
                                        isPastDue = false,
                                        itemText = item.text,
                                        checklistName = checklist.name,
                                        checklistId = checklist.id
                                    )
                                )
                            }
                        }
                    }
                }
            }
            val dismissed = dismissedAlertIds.value
            _reminderAlerts.value = alerts.filter { it.id !in dismissed }
        }
    }

    fun parseCoordinates(locationName: String?): Pair<Double, Double>? {
        if (locationName == null) return null
        val regex = Regex("""\((-?\d+\.\d+),\s*(-?\d+\.\d+)\)""")
        val match = regex.find(locationName)
        if (match != null) {
            val lat = match.groupValues[1].toDoubleOrNull()
            val lng = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null) {
                return Pair(lat, lng)
            }
        }
        val parts = locationName.split(",")
        if (parts.size == 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lng = parts[1].trim().toDoubleOrNull()
            if (lat != null && lng != null) {
                return Pair(lat, lng)
            }
        }
        return null
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun startLocationUpdates() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val hasFine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                return
            }
        }
        
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    currentGpsLocation.value = loc
                    updateReminderSystem()
                }
            }

            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 
                10000L
            )
                .setMinUpdateIntervalMillis(5000L)
                .build()

            val callback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    for (loc in result.locations) {
                        currentGpsLocation.value = loc
                        updateReminderSystem()
                    }
                }
            }
            locationCallback = callback
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, android.os.Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }

    // --- Category Management ---
    fun createCategory(name: String, color: String) {
        viewModelScope.launch {
            if (name.isNotEmpty()) {
                repository.insertCategory(Category(name = name.trim(), color = color))
            }
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            if (selectedFilterCategoryId.value == category.id) {
                selectedFilterCategoryId.value = null
            }
        }
    }

    // --- Checklist Management ---
    fun createChecklist(name: String, icon: String, categoryId: Int?, dueDate: Long?, isTemplate: Boolean, description: String? = null) {
        viewModelScope.launch {
            if (name.trim().isNotEmpty()) {
                val newId = repository.insertChecklist(
                    Checklist(
                        name = name.trim(),
                        icon = icon.ifBlank { "📝" },
                        categoryId = categoryId,
                        dueDate = dueDate,
                        isTemplate = isTemplate,
                        description = description
                    )
                )
                if (!isTemplate) {
                    selectedChecklistId.value = newId.toInt()
                }
            }
        }
    }

    fun createChecklistWithInitialTask(listName: String, taskText: String, categoryId: Int? = null) {
        viewModelScope.launch {
            if (listName.trim().isNotEmpty()) {
                val newId = repository.insertChecklist(
                    Checklist(
                        name = listName.trim(),
                        icon = "📋",
                        categoryId = categoryId,
                        dueDate = null,
                        isTemplate = false
                    )
                )
                repository.insertItem(
                    ChecklistItem(
                        checklistId = newId.toInt(),
                        text = taskText,
                        isCompleted = false,
                        position = 0,
                        isAddedToToday = true
                    )
                )
                selectedChecklistId.value = newId.toInt()
            }
        }
    }

    fun createChecklistWithParsedItems(name: String, icon: String, categoryId: Int?, dueDate: Long?, pastedNotes: String) {
        viewModelScope.launch {
            if (name.trim().isNotEmpty()) {
                val newId = repository.insertChecklist(
                    Checklist(
                        name = name.trim(),
                        icon = icon.ifBlank { "📝" },
                        categoryId = categoryId,
                        dueDate = dueDate,
                        isTemplate = false
                    )
                )
                
                selectedChecklistId.value = newId.toInt()
                
                if (pastedNotes.isNotBlank()) {
                    val lines = pastedNotes.split("\n")
                        .map { line ->
                            val trimmed = line.trim()
                            // Clean brackets like [ ], [  ], [x], [X], [-] at the beginning of each line
                            val cleaned = trimmed.replaceFirst(Regex("^\\[\\s*[xX-]?\\s*\\]\\s*"), "")
                            cleaned.trim()
                        }
                        .filter { it.isNotEmpty() }
                    
                    lines.forEachIndexed { index, text ->
                        repository.insertItem(
                            ChecklistItem(
                                checklistId = newId.toInt(),
                                text = text,
                                isCompleted = false,
                                position = index
                            )
                        )
                    }
                }
            }
        }
    }

    fun deleteChecklist(checklist: Checklist) {
        viewModelScope.launch {
            repository.deleteChecklist(checklist)
            if (selectedChecklistId.value == checklist.id) {
                selectedChecklistId.value = null
            }
        }
    }

    fun clearChecklistDoneToday(checklistId: Int) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val dateStr = sdf.format(java.util.Date(System.currentTimeMillis()))
        val key = "checklist_shortcut_done_${checklistId}_${dateStr}"
        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    fun updateChecklist(checklist: Checklist) {
        clearChecklistDoneToday(checklist.id)
        viewModelScope.launch {
            repository.updateChecklist(checklist)
            updateReminderSystem()
        }
    }

    fun createTaskListInProject(name: String, projectId: Int, todoCategoryId: Int?) {
        viewModelScope.launch {
            if (name.trim().isNotEmpty()) {
                repository.insertChecklist(
                    Checklist(
                        name = name.trim(),
                        icon = "📋",
                        categoryId = todoCategoryId,
                        projectId = projectId,
                        isVisibleInTaskListSec = false,
                        isTemplate = false
                    )
                )
            }
        }
    }

    fun toggleChecklistVisibleInTaskListSec(checklist: Checklist) {
        viewModelScope.launch {
            repository.updateChecklist(checklist.copy(isVisibleInTaskListSec = !checklist.isVisibleInTaskListSec))
        }
    }

    fun updateChecklistWithParsedItems(checklist: Checklist, pastedNotes: String) {
        clearChecklistDoneToday(checklist.id)
        viewModelScope.launch {
            repository.updateChecklist(checklist)
            updateReminderSystem()
            
            if (pastedNotes.isNotBlank()) {
                val lines = pastedNotes.split("\n")
                    .map { line ->
                        val trimmed = line.trim()
                        val cleaned = trimmed.replaceFirst(Regex("^\\[\\s*[xX-]?\\s*\\]\\s*"), "")
                        cleaned.trim()
                    }
                    .filter { it.isNotEmpty() }
                
                lines.forEachIndexed { index, text ->
                    repository.insertItem(
                        ChecklistItem(
                            checklistId = checklist.id,
                            text = text,
                            isCompleted = false,
                            position = index
                        )
                    )
                }
            }
        }
    }

    // Load template logic: Copied and created as active instance!
    fun loadTemplateAsChecklist(templateId: Int, activeName: String, categoryId: Int?, dueDate: Long?) {
        viewModelScope.launch {
            val template = repository.getChecklistByIdDirect(templateId) ?: return@launch
            val items = repository.getItemsForChecklistDirect(templateId)

            val newId = repository.insertChecklist(
                Checklist(
                    name = activeName.ifBlank { template.name.replace("Pre-", "Active ").replace("Blueprint", "").trim() },
                    icon = template.icon,
                    categoryId = categoryId ?: template.categoryId,
                    dueDate = dueDate,
                    isTemplate = false
                )
            )

            // Copy all blueprint items into active list with their ordering
            items.forEach { item ->
                repository.insertItem(
                    ChecklistItem(
                        checklistId = newId.toInt(),
                        text = item.text,
                        isCompleted = false,
                        position = item.position,
                        dueDate = null // Fresh checklist items start without item-specific due dates until configured
                    )
                )
            }

            selectedChecklistId.value = newId.toInt()
            updateReminderSystem()
        }
    }

    // Save active list as blueprints template
    fun saveActiveAsTemplate(activeId: Int, templateName: String) {
        viewModelScope.launch {
            val checklist = repository.getChecklistByIdDirect(activeId) ?: return@launch
            val items = repository.getItemsForChecklistDirect(activeId)

            val newTemplateId = repository.insertChecklist(
                Checklist(
                    name = templateName.ifBlank { "${checklist.name} Template Blueprint" },
                    icon = checklist.icon,
                    categoryId = checklist.categoryId,
                    isTemplate = true
                )
            )

            // Save items as blueprints reference
            items.forEach { item ->
                repository.insertItem(
                    ChecklistItem(
                        checklistId = newTemplateId.toInt(),
                        text = item.text,
                        isCompleted = false,
                        position = item.position,
                        dueDate = null
                    )
                )
            }
            updateReminderSystem()
        }
    }

    fun addItemToSpecificChecklist(checklistId: Int, text: String, dueDate: Long? = null, isAddedToToday: Boolean = false) {
        if (text.trim().isNotEmpty()) {
            viewModelScope.launch {
                val items = repository.getItemsForChecklistDirect(checklistId)
                val currentMaxPosition = items.maxOfOrNull { it.position } ?: -1
                repository.insertItem(
                    ChecklistItem(
                        checklistId = checklistId,
                        text = text.trim(),
                        position = currentMaxPosition + 1,
                        dueDate = dueDate,
                        isAddedToToday = isAddedToToday
                    )
                )
                updateReminderSystem()
            }
        }
    }

    fun addIdeaToSandbox(text: String, excitement: Int, fright: Int) {
        if (text.trim().isNotEmpty()) {
            viewModelScope.launch {
                val dbLists = repository.allChecklists.first()
                val ideaList = dbLists.find { it.name == "Bright Ideas Sandbox" }
                if (ideaList != null) {
                    val items = repository.getItemsForChecklistDirect(ideaList.id)
                    val currentMaxPosition = items.maxOfOrNull { it.position } ?: -1
                    repository.insertItem(
                        ChecklistItem(
                            checklistId = ideaList.id,
                            text = text.trim(),
                            position = currentMaxPosition + 1,
                            isIdea = true,
                            excitementRating = excitement,
                            frightRating = fright,
                            coolingOffStartedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun promoteIdeaToProject(item: ChecklistItem, newProjectName: String) {
        viewModelScope.launch {
            val categories = repository.allCategories.first()
            val projectsCat = categories.find { it.name == "Projects" }
            val projectsCatId = projectsCat?.id

            // Create project board with full idea text as description
            val projectId = repository.insertChecklist(
                Checklist(
                    name = newProjectName.trim(),
                    icon = "🚀",
                    categoryId = projectsCatId,
                    isTemplate = false,
                    description = item.text.trim()
                )
            )

            // Select the newly created project workspace
            selectedChecklistId.value = projectId.toInt()

            // Add launch milestone milestones/tasks automatically to spark real entrepreneurial momentum!
            repository.insertItem(
                ChecklistItem(
                    checklistId = projectId.toInt(),
                    text = "Launch & Validate: Define target audience & core value prop",
                    position = 0,
                    isAddedToToday = true
                )
            )
            repository.insertItem(
                ChecklistItem(
                    checklistId = projectId.toInt(),
                    text = "Create minimal prototype/landing page",
                    position = 1
                )
            )
            repository.insertItem(
                ChecklistItem(
                    checklistId = projectId.toInt(),
                    text = "Gather feedback from 5 real potential users",
                    position = 2
                )
            )

            // Delete the processed idea
            repository.deleteItem(item)
            updateReminderSystem()
        }
    }

    fun createProjectFromSpark(title: String, notes: String, excitement: Int, fright: Int) {
        viewModelScope.launch {
            val categories = repository.allCategories.first()
            val projectsCat = categories.find { it.name == "Projects" }
            val projectsCatId = projectsCat?.id

            // Create project checklist directly
            val projectId = repository.insertChecklist(
                Checklist(
                    name = title.trim(),
                    icon = "🚀",
                    categoryId = projectsCatId,
                    isTemplate = false,
                    description = notes.trim()
                )
            )

            // Select the newly created project checklist
            selectedChecklistId.value = projectId.toInt()
            updateReminderSystem()
        }
    }

    fun promoteIdeaToTask(item: ChecklistItem, targetChecklistId: Int) {
        viewModelScope.launch {
            val items = repository.getItemsForChecklistDirect(targetChecklistId)
            val currentMaxPosition = items.maxOfOrNull { it.position } ?: -1
            repository.insertItem(
                ChecklistItem(
                    checklistId = targetChecklistId,
                    text = "Implement idea: ${item.text}",
                    position = currentMaxPosition + 1,
                    isAddedToToday = true
                )
            )
            
            // Delete the processed idea
            repository.deleteItem(item)
            updateReminderSystem()
        }
    }

    // --- Items Management ---
    fun addItemToChecklist(text: String, dueDate: Long? = null) {
        val listId = selectedChecklistId.value ?: return
        if (text.trim().isNotEmpty()) {
            viewModelScope.launch {
                val currentMaxPosition = _currentItems.value.maxOfOrNull { it.position } ?: -1
                repository.insertItem(
                    ChecklistItem(
                        checklistId = listId,
                        text = text.trim(),
                        position = currentMaxPosition + 1,
                        dueDate = dueDate
                    )
                )
                updateReminderSystem()
            }
        }
    }

    fun updateItem(item: ChecklistItem) {
        viewModelScope.launch {
            repository.updateItem(item)
            updateReminderSystem()
        }
    }

    fun toggleItemAddedToToday(item: ChecklistItem) {
        viewModelScope.launch {
            repository.updateItem(item.copy(isAddedToToday = !item.isAddedToToday))
            updateReminderSystem()
        }
    }

    fun addItemToTodayAsShortcut(item: ChecklistItem) {
        viewModelScope.launch {
            repository.updateItem(item.copy(isAddedToToday = true))
            updateReminderSystem()
        }
    }

    fun addChecklistShortcutToToday(checklistId: Int) {
        viewModelScope.launch {
            val lists = checklists.value
            val todayId = lists.find { it.name == "Today's Focus Tasks" }?.id ?: return@launch
            
            val visited = mutableSetOf<Int>()
            val toAdd = mutableListOf<Int>()
            
            fun collectLinked(id: Int) {
                if (id in visited) return
                visited.add(id)
                toAdd.add(id)
                val current = checklists.value.find { it.id == id } ?: return
                val linkedIdsStr = current.linkedChecklistIds ?: return
                val linkedIds = linkedIdsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                for (linkedId in linkedIds) {
                    collectLinked(linkedId)
                }
            }
            
            collectLinked(checklistId)
            
            for (id in toAdd) {
                val checklist = checklists.value.find { it.id == id } ?: continue
                val shortcutText = "[CL_SHORTCUT:$id] ${checklist.name}"
                val alreadyExists = repository.getItemsForChecklistDirect(todayId).any { it.text.startsWith("[CL_SHORTCUT:$id]") }
                if (!alreadyExists) {
                    val currentMaxPosition = repository.getItemsForChecklistDirect(todayId).maxOfOrNull { it.position } ?: -1
                    repository.insertItem(
                        ChecklistItem(
                            checklistId = todayId,
                            text = shortcutText,
                            position = currentMaxPosition + 1
                        )
                    )
                }
            }
            updateReminderSystem()
        }
    }

    fun deleteItem(item: ChecklistItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
            updateReminderSystem()
        }
    }

    fun toggleItemCompletion(item: ChecklistItem) {
        viewModelScope.launch {
            val newCompleted = !item.isCompleted
            if (newCompleted) {
                saveItemCompletedDate(item.id)
            } else {
                clearItemCompletedDate(item.id)
            }
            repository.updateItemCompletion(item.id, newCompleted)
            updateReminderSystem()
        }
    }

    fun resetCurrentChecklist() {
        val listId = selectedChecklistId.value ?: return
        viewModelScope.launch {
            repository.resetChecklistCompletion(listId)
            updateReminderSystem()
        }
    }

    fun clearAllCompletedItems() {
        viewModelScope.launch {
            repository.deleteCompletedItems()
            updateReminderSystem()
        }
    }

    // --- Reordering Flow Engine ---
    fun moveItemUp(item: ChecklistItem) {
        val items = allItems.value.filter { it.checklistId == item.checklistId }
            .sortedWith(compareBy<ChecklistItem> { it.isCompleted }.thenBy { it.position })
        val index = items.indexOfFirst { it.id == item.id }
        if (index > 0) {
            val prevItem = items[index - 1]
            viewModelScope.launch {
                val posA = prevItem.position
                val posB = item.position
                val itemA = item.copy(position = if (posA == posB) posA - 1 else posA)
                val itemB = prevItem.copy(position = if (posA == posB) posB + 1 else posB)
                repository.updateItems(listOf(itemA, itemB))
            }
        }
    }

    fun moveItemDown(item: ChecklistItem) {
        val items = allItems.value.filter { it.checklistId == item.checklistId }
            .sortedWith(compareBy<ChecklistItem> { it.isCompleted }.thenBy { it.position })
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1 && index < items.lastIndex) {
            val nextItem = items[index + 1]
            viewModelScope.launch {
                val posA = nextItem.position
                val posB = item.position
                val itemA = item.copy(position = if (posA == posB) posA - 1 else posA)
                val itemB = nextItem.copy(position = if (posA == posB) posB + 1 else posB)
                repository.updateItems(listOf(itemA, itemB))
            }
        }
    }

    // --- Local Manual Backup & Restore System ---
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun exportBackupToUri(uri: android.net.Uri, contentResolver: android.content.ContentResolver, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cats = repository.allCategories.first()
                val lists = repository.allChecklists.first()
                val its = repository.allItems.first()
                val payload = AppBackupPayload(cats, lists, its)
                val jsonStr = moshi.adapter(AppBackupPayload::class.java).indent("  ").toJson(payload)
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonStr.toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    fun importBackupFromUri(uri: android.net.Uri, contentResolver: android.content.ContentResolver, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonStr = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: throw Exception("Failed to open input stream")
                
                val payload = moshi.adapter(AppBackupPayload::class.java).fromJson(jsonStr)
                    ?: throw Exception("Invalid backup data format")
                
                repository.restoreDatabase(payload.categories, payload.checklists, payload.items)
                
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class AppBackupPayload(
    val categories: List<Category>,
    val checklists: List<Checklist>,
    val items: List<ChecklistItem>
)

class ChecklistViewModelFactory(
    private val repository: ChecklistRepository,
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChecklistViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChecklistViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
