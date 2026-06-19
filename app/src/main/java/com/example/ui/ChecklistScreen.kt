package com.example.ui

// Cache invalidation comment to force compiler to reload actual on-disk ChecklistScreen.kt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Category
import com.example.data.Checklist
import com.example.data.ChecklistItem
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import android.content.Intent
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val checklists by viewModel.checklists.collectAsStateWithLifecycle()
    val selectedChecklistId by viewModel.selectedChecklistId.collectAsStateWithLifecycle()
    val selectedFilterCategoryId by viewModel.selectedFilterCategoryId.collectAsStateWithLifecycle()
    val currentItems by viewModel.currentItems.collectAsStateWithLifecycle()
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val reminderAlerts by viewModel.reminderAlerts.collectAsStateWithLifecycle()
    val simulatedLocation by viewModel.simulatedLocation.collectAsStateWithLifecycle()

    val isKeyboardVisible = WindowInsets.isImeVisible

    var showAddChecklistDialog by remember { mutableStateOf(false) }
    var initialCategoryForAddDialog by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var newItemText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showQuickIdeaDumpDialog by remember { mutableStateOf(false) }

    // Collapsible Rightside menu opening toggle
    var isRightMenuOpen by remember { mutableStateOf(false) }
    var showDrawerAddTask by remember { mutableStateOf(false) }
    var drawerTaskText by remember { mutableStateOf("") }

    // Checklist Settings Edit Dialog
    var showListSettingsDialog by remember { mutableStateOf(false) }
    var showEditDetailsDialog by remember { mutableStateOf(false) }

    // Simulated Location Selector Dialog
    var showSimulatedLocationDialog by remember { mutableStateOf(false) }

    // Checkpoint Item Settings Dialog
    var editingItem by remember { mutableStateOf<ChecklistItem?>(null) }

    // Confirmation dialog states
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ChecklistItem?>(null) }
    var itemToDeactivateAlarm by remember { mutableStateOf<ChecklistItem?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var checklistToDelete by remember { mutableStateOf<Checklist?>(null) }

    val activeChecklist = checklists.find { it.id == selectedChecklistId }
    val keyboardController = LocalSoftwareKeyboardController.current

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var isFirstRun by remember { mutableStateOf(sharedPrefs.getBoolean("is_first_run", true)) }

    // Backup & Restore ActResult Launchers
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(
                uri,
                context.contentResolver,
                onSuccess = {
                    android.widget.Toast.makeText(context, "Backup file saved successfully! 💾", android.widget.Toast.LENGTH_LONG).show()
                },
                onError = { ex ->
                    android.widget.Toast.makeText(context, "Export failed: ${ex.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackupFromUri(
                uri,
                context.contentResolver,
                onSuccess = {
                    android.widget.Toast.makeText(context, "Data restored successfully! 📂✅", android.widget.Toast.LENGTH_LONG).show()
                },
                onError = { ex ->
                    android.widget.Toast.makeText(context, "Restore failed: ${ex.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // Section selector: "today", "todo", "projects", "checklists"
    var selectedSection by remember { mutableStateOf("today") }

    val screenCoroutineScope = rememberCoroutineScope()
    val checklistLazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val todoLazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(selectedChecklistId) {
        if (currentItems.isNotEmpty()) {
            checklistLazyListState.scrollToItem(0)
        }
    }

    LaunchedEffect(selectedSection) {
        val todoChecklistTemp = checklists.find { it.name == "General Todo List" }
        val todoItemsTemp = if (todoChecklistTemp == null) emptyList() else allItems.filter { it.checklistId == todoChecklistTemp.id }
        if (selectedSection == "todo" && todoItemsTemp.isNotEmpty()) {
            todoLazyListState.scrollToItem(0)
        }
    }

    LaunchedEffect(reminderAlerts, isFirstRun) {
        if (reminderAlerts.isNotEmpty() && !isFirstRun) {
            viewModel.postSystemNotifications(context, reminderAlerts)
        }
    }

    // Keep widget in-sync in real-time with DB modifications
    LaunchedEffect(allItems, checklists) {
        com.example.widget.TodayWidgetProvider.triggerRefresh(context)
    }

    var isProjectsExpandedInDrawer by remember { mutableStateOf(false) }
    var isChecklistsExpandedInDrawer by remember { mutableStateOf(false) }
    var isTasksExpandedInDrawer by remember { mutableStateOf(false) }
    var isHyperfocusActive by remember { mutableStateOf(false) }

    // Double-Back click handling
    var lastBackTime by remember { mutableLongStateOf(0L) }
    androidx.activity.compose.BackHandler(enabled = true) {
        if (isRightMenuOpen) {
            isRightMenuOpen = false
        } else if (selectedSection != "today") {
            selectedSection = "today"
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackTime < 2000L) {
                (context as? androidx.activity.ComponentActivity)?.finish()
            } else {
                lastBackTime = now
                android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val projectsCategory = categories.find { it.name.lowercase().trim() == "projects" }
    val projectsCategoryId = projectsCategory?.id

    val todoCategory = categories.find { it.name.lowercase().trim() == "tasks & todo" || it.name.lowercase().trim() == "todo" }
    val todoCategoryId = todoCategory?.id

    val todoChecklist = checklists.find { it.name == "General Todo List" }
    val todayChecklist = checklists.find { it.name == "Today's Focus Tasks" }
    val ideaChecklist = checklists.find { it.name == "Bright Ideas Sandbox" }
    val todayItems = remember(allItems, todayChecklist) {
        if (todayChecklist == null) emptyList()
        else allItems.filter { it.checklistId == todayChecklist.id }
            .sortedWith(compareBy<ChecklistItem> { it.isCompleted }.thenBy { it.position })
    }

    var checklistOrderString by remember { mutableStateOf(sharedPrefs.getString("checklist_order", "") ?: "") }
    val orderMap = remember(checklistOrderString) {
        checklistOrderString.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .withIndex()
            .associate { it.value to it.index }
    }

    val todoChecklists = remember(checklists, todoCategoryId, orderMap) {
        checklists.filter { 
            (it.categoryId == todoCategoryId || (it.name == "General Todo List" && todoCategoryId == null)) && 
            it.id != todayChecklist?.id && 
            it.id != ideaChecklist?.id &&
            (it.projectId == null || it.isVisibleInTaskListSec)
        }
        .sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
    }

    val projectChecklists = remember(checklists, projectsCategoryId, orderMap) {
        checklists.filter { it.categoryId == projectsCategoryId && it.id != todayChecklist?.id && it.id != ideaChecklist?.id }
            .sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
    }

    val standardChecklists = remember(checklists, projectsCategoryId, todoCategoryId, todayChecklist, ideaChecklist, orderMap) {
        checklists.filter { 
            it.categoryId != projectsCategoryId && 
            (todoCategoryId == null || it.categoryId != todoCategoryId) &&
            it.id != todayChecklist?.id &&
            it.id != ideaChecklist?.id
        }
        .sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
    }

    fun moveChecklistRow(checklistId: Int, direction: Int, listToMove: List<com.example.data.Checklist>) {
        val currentIds = listToMove.map { it.id }.toMutableList()
        val index = currentIds.indexOf(checklistId)
        if (index == -1) return
        val newIndex = index + direction
        if (newIndex in 0 until currentIds.size) {
            val temp = currentIds[index]
            currentIds[index] = currentIds[newIndex]
            currentIds[newIndex] = temp
            
            val allIds = checklists.map { it.id }.toMutableList()
            val otherIds = allIds.filter { it !in listToMove.map { itDirect -> itDirect.id } }
            val combinedOrder = currentIds + otherIds
            
            val newPreferenceString = combinedOrder.joinToString(",")
            sharedPrefs.edit().putString("checklist_order", newPreferenceString).apply()
            checklistOrderString = newPreferenceString
        }
    }

    val filteredCategoriesForChips = remember(categories, projectsCategoryId) {
        categories.filter { it.id != projectsCategoryId }
    }

    // Helper to evaluate if task falls on today's calendar date
    fun isTimestampToday(timestamp: Long?): Boolean {
        if (timestamp == null) return false
        val cal = Calendar.getInstance()
        val todayYear = cal.get(Calendar.YEAR)
        val todayDay = cal.get(Calendar.DAY_OF_YEAR)
        cal.timeInMillis = timestamp
        return todayYear == cal.get(Calendar.YEAR) && todayDay == cal.get(Calendar.DAY_OF_YEAR)
    }

    // Group checklists by categories for the sidebar
    val filteredStandardChecklists = remember(standardChecklists, selectedFilterCategoryId) {
        if (selectedFilterCategoryId == null) {
            standardChecklists
        } else {
            standardChecklists.filter { it.categoryId == selectedFilterCategoryId }
        }
    }

    // Search matches of lists and items
    val matchedLists = remember(checklists, searchQuery, todoChecklist, todayChecklist, ideaChecklist) {
        if (searchQuery.isBlank()) emptyList()
        else checklists.filter { 
            it.name.contains(searchQuery, ignoreCase = true) && 
            it.id != todoChecklist?.id && 
            it.id != todayChecklist?.id &&
            it.id != ideaChecklist?.id
        }
    }
    val matchedItems = remember(allItems, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allItems.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (isSearchExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            isSearchExpanded = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Exit search")
                        }
                        
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search checkpoints or lists...", fontSize = 14.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, "Search icon", modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, "Clear query")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                                .testTag("global_search_bar")
                        )
                    }
                } else {
                    TopAppBar(
                        title = {
                            Row(
                                modifier = Modifier.padding(end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🎯",
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "කරපන් කරපන්!",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 18.sp
                                    )
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        actions = {
                            // Shortcut to Bright Ideas Sandbox
                            IconButton(
                                onClick = { selectedSection = "ideas" },
                                modifier = Modifier.testTag("quick_idea_dump_topbar_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Bright Ideas Sandbox Shortcut",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }





                            // Collapsible Menu toggle button
                            IconButton(
                                onClick = { isRightMenuOpen = true },
                                modifier = Modifier.testTag("open_collapsible_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open collateral lists drawer",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .imePadding()
                    .background(MaterialTheme.colorScheme.background)
            ) {

                // Dynamic Alerts Banners for active reminders (time / location)
                if (reminderAlerts.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(reminderAlerts) { alert ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (alert.title.contains("Location", ignoreCase = true)) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else if (alert.isPastDue) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    }
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.widthIn(min = 240.dp, max = 340.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (alert.title.contains("Location")) "📍" else "⏰",
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    Column(modifier = Modifier.weight(1.0f)) {
                                        Text(
                                            text = alert.title,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        )
                                        Text(
                                            text = alert.message,
                                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissAlert(alert.id, context) },
                                        modifier = Modifier.size(24.dp).testTag("dismiss_reminder_alert_" + alert.id)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss Reminder",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Search Results state versus normal lists
                if (searchQuery.isNotEmpty()) {
                    // Global Search Results View
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Search Results",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        if (matchedLists.isEmpty() && matchedItems.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No lists or check points found for \"$searchQuery\"",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (matchedLists.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Matching Checklists (${matchedLists.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }

                                    items(matchedLists) { list ->
                                        Card(
                                            onClick = {
                                                viewModel.selectedChecklistId.value = list.id
                                                searchQuery = "" // Reset search to show focused checklist
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(list.icon, fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(list.name, fontWeight = FontWeight.Bold)
                                                    val catName = categories.find { it.id == list.categoryId }?.name ?: "Personal"
                                                    Text(
                                                        text = "$catName Category",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected list",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }

                                if (matchedItems.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Matching Checkpoints (${matchedItems.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }

                                    items(matchedItems) { item ->
                                        val parentList = checklists.find { it.id == item.checklistId }
                                        Card(
                                            onClick = {
                                                if (parentList != null) {
                                                    viewModel.selectedChecklistId.value = parentList.id
                                                    searchQuery = "" // Focus
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = item.isCompleted,
                                                    onCheckedChange = { viewModel.toggleItemCompletion(item) }
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.text,
                                                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                                        color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "List: ${parentList?.name ?: "Unknown"}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Modern Multi-Section Container
                    when (selectedSection) {
                        "today" -> {
                            TodayPlannerView(
                                viewModel = viewModel,
                                allItems = allItems,
                                todayChecklist = todayChecklist,
                                isTimestampToday = ::isTimestampToday,
                                onConfigureReminder = { editingItem = it },
                                onDelete = { itemToDelete = it },
                                onDeactivateAlarm = { itemToDeactivateAlarm = it },
                                onStartHyperfocus = { isHyperfocusActive = true },
                                onNavigateToChecklist = { checklistId ->
                                    viewModel.selectedChecklistId.value = checklistId
                                    selectedSection = "checklists"
                                },
                                checklists = checklists,
                                categories = categories,
                                isKeyboardVisible = isKeyboardVisible,
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                        "todo" -> {
                            TodoListView(
                                viewModel = viewModel,
                                allItems = allItems,
                                todoChecklist = todoChecklist,
                                onConfigureReminder = { editingItem = it },
                                onDelete = { itemToDelete = it },
                                onDeactivateAlarm = { itemToDeactivateAlarm = it },
                                checklists = checklists,
                                categories = categories,
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                        "ideas" -> {
                            EntrepreneurIdeaSandboxView(
                                viewModel = viewModel,
                                allItems = allItems,
                                ideaChecklist = ideaChecklist,
                                checklists = checklists,
                                isTimestampToday = ::isTimestampToday,
                                onDelete = { itemToDelete = it },
                                onNavigateToProject = {
                                    selectedSection = "checklists"
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                        "projects" -> {
                            ProjectsDashboardView(
                                viewModel = viewModel,
                                projectChecklists = projectChecklists,
                                allItems = allItems,
                                projectsCategoryId = projectsCategoryId,
                                onCreateProjectClick = { showAddChecklistDialog = true },
                                onSelectProject = { projId ->
                                    viewModel.selectedChecklistId.value = projId
                                    selectedSection = "checklists"
                                },
                                onMoveChecklist = { checklistId, dir ->
                                    moveChecklistRow(checklistId, dir, projectChecklists)
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                        "tasks_dashboard" -> {
                            TasksDashboardView(
                                viewModel = viewModel,
                                todoChecklists = todoChecklists,
                                allItems = allItems,
                                todoCategoryId = todoCategoryId,
                                onCreateTaskClick = {
                                    initialCategoryForAddDialog = todoCategoryId
                                    showAddChecklistDialog = true
                                },
                                onSelectTaskList = { listId ->
                                    viewModel.selectedChecklistId.value = listId
                                    selectedSection = "checklists"
                                },
                                onMoveChecklist = { checklistId, dir ->
                                    moveChecklistRow(checklistId, dir, todoChecklists)
                                },
                                checklists = checklists,
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                        "checklists_dashboard" -> {
                            ChecklistsDashboardView(
                                viewModel = viewModel,
                                standardChecklists = filteredStandardChecklists,
                                allItems = allItems,
                                selectedFilterCategoryId = selectedFilterCategoryId,
                                onCreateChecklistClick = {
                                    initialCategoryForAddDialog = selectedFilterCategoryId
                                    showAddChecklistDialog = true
                                },
                                onSelectChecklist = { listId ->
                                    viewModel.selectedChecklistId.value = listId
                                    selectedSection = "checklists"
                                },
                                onMoveChecklist = { checklistId, dir ->
                                    moveChecklistRow(checklistId, dir, filteredStandardChecklists)
                                },
                                categories = categories,
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                        "completed_tasks" -> {
                            CompletedTasksView(
                                viewModel = viewModel,
                                allItems = allItems,
                                checklists = checklists,
                                onDelete = { itemToDelete = it },
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            )
                        }
                        else -> {
                            // Standard Checklists (default)
                            if (activeChecklist == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "📭", fontSize = 48.sp, modifier = Modifier.padding(bottom = 12.dp))
                                        Text(text = "No checklist active", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Button(
                                            onClick = { showAddChecklistDialog = true },
                                            shape = RoundedCornerShape(100.dp),
                                            modifier = Modifier.padding(top = 12.dp)
                                        ) {
                                            Text("Create Checklist")
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 20.dp)
                                ) {
                            val totalItems = currentItems.size
                            val checkedItems = currentItems.count { it.isCompleted }
                            val progressValue by animateFloatAsState(
                                targetValue = if (totalItems > 0) checkedItems.toFloat() / totalItems else 0f,
                                label = "checklist_progress"
                            )

                            var isEditingProjectDescription by remember(activeChecklist) { mutableStateOf(false) }
                            var editedProjectDescriptionText by remember(activeChecklist) { mutableStateOf(TextFieldValue(activeChecklist.description ?: "")) }

                            fun applyFormattingToProjectDesc(formatChar: String) {
                                val text = editedProjectDescriptionText.text
                                val selection = editedProjectDescriptionText.selection
                                val start = selection.start
                                val end = selection.end
                                if (start != end) {
                                    val selectedText = text.substring(start, end)
                                    val newText = text.substring(0, start) + formatChar + selectedText + formatChar + text.substring(end)
                                    val newSelectionStart = start
                                    val newSelectionEnd = end + formatChar.length * 2
                                    editedProjectDescriptionText = TextFieldValue(
                                        text = newText,
                                        selection = TextRange(newSelectionStart, newSelectionEnd)
                                    )
                                } else {
                                    val newText = text.substring(0, start) + formatChar + formatChar + text.substring(start)
                                    val newCursor = start + formatChar.length
                                    editedProjectDescriptionText = TextFieldValue(
                                        text = newText,
                                        selection = TextRange(newCursor)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (isKeyboardVisible) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = activeChecklist.icon, fontSize = 22.sp, modifier = Modifier.padding(end = 6.dp))
                                    Text(
                                        text = activeChecklist.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = activeChecklist.icon,
                                                fontSize = 32.sp,
                                                modifier = Modifier.padding(end = 12.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = activeChecklist.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                )

                                                val categoryName = categories.find { it.id == activeChecklist.categoryId }?.name ?: "Personal"
                                                Text(
                                                    text = "$categoryName Category",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                    ),
                                                    modifier = Modifier.padding(top = 1.dp)
                                                )

                                                // If checklist location reminder is set, display it!
                                                if (!activeChecklist.locationName.isNullOrBlank()) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Place,
                                                            contentDescription = "List location",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = "Alert at: ${activeChecklist.locationName}",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Very compact, space-saving row for actions below the title/category info!
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            // Small separated buttons for Editing, Notification, Refresh, Delete
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // 0. TODAY SHORTCUT (STAR/PIN LINK)
                                                val hasActiveTodayShortcut = remember(todayItems, activeChecklist.id) {
                                                    todayItems.any { it.text.startsWith("[CL_SHORTCUT:${activeChecklist.id}]") }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val shortcutItem = todayItems.find { it.text.startsWith("[CL_SHORTCUT:${activeChecklist.id}]") }
                                                        if (shortcutItem != null) {
                                                            viewModel.deleteItem(shortcutItem)
                                                            android.widget.Toast.makeText(context, "Shortcut removed from Today's Focus! 🗑️", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            viewModel.addChecklistShortcutToToday(activeChecklist.id)
                                                            android.widget.Toast.makeText(context, "Shortcut added to Today's Focus! 🌟", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp).testTag("list_shortcut_today_button")
                                                ) {
                                                    Icon(
                                                         imageVector = Icons.Default.Star,
                                                         contentDescription = "Pin Shortcut to Today",
                                                         tint = if (hasActiveTodayShortcut) androidx.compose.ui.graphics.Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                         modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // 1. EDITING checklist info
                                                IconButton(
                                                    onClick = { showEditDetailsDialog = true },
                                                    modifier = Modifier.size(28.dp).testTag("list_settings_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit checklist details",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // 2. NOTIFICATION reminders (Time & Location alarm)
                                                IconButton(
                                                    onClick = { showListSettingsDialog = true },
                                                    modifier = Modifier.size(28.dp).testTag("list_notifications_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Notifications,
                                                        contentDescription = "Checklist notifications",
                                                        tint = if (activeChecklist.dueDate != null || !activeChecklist.locationName.isNullOrBlank()) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // 3. REFRESH / Reset checklist progress
                                                IconButton(
                                                    onClick = { showResetConfirmDialog = true },
                                                    modifier = Modifier.size(28.dp).testTag("reset_checklist_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Reset checklist progress",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // 4. DELETE current active checklist
                                                IconButton(
                                                    onClick = { showDeleteConfirmDialog = true },
                                                    modifier = Modifier.size(28.dp).testTag("delete_category_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Active Checklist",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            LinearProgressIndicator(
                                                progress = { progressValue },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(100.dp)),
                                                strokeCap = StrokeCap.Round,
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.outlineVariant
                                            )

                                            Text(
                                                text = "$checkedItems/$totalItems done",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }

                                        // Dynamic encouraging slogan based on progress!
                                        val encouragementText = when {
                                            totalItems == 0 -> "Add some targets below to begin your prep! 📝"
                                            progressValue == 0f -> "All set to start. Let's get checklist-ready! 🚀"
                                            progressValue in 0.01f..0.29f -> "Step-by-step, we build clarity! Keep going 🌟"
                                            progressValue in 0.30f..0.55f -> "Making awesome progress. Keep it up! ⚡"
                                            progressValue in 0.56f..0.85f -> "Over halfway there. You're unstoppable! 💪"
                                            progressValue in 0.86f..0.99f -> "Almost there! Just a final double-check ✨"
                                            else -> "Outstanding! 100% prepared and safe travels! 🎉🚀"
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = encouragementText,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                            ),
                                            modifier = Modifier.padding(start = 2.dp)
                                        )
                                    }
                                }

                                if (currentItems.isNotEmpty()) {
                                    Button(
                                        onClick = { isHyperfocusActive = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                            .testTag("hyperfocus_list_button")
                                    ) {
                                        Text(
                                            text = "🚀 Break Task Paralysis (Hyperfocus Mode)",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val isActiveProject = remember(activeChecklist, projectsCategoryId) {
                                activeChecklist != null && activeChecklist.categoryId == projectsCategoryId
                            }
                            val projectTaskLists = remember(checklists, activeChecklist) {
                                if (activeChecklist == null) emptyList()
                                else checklists.filter { it.projectId == activeChecklist.id }
                            }

                            // Checklist checkpoints lists or Projects sub-checklists
                            if (isActiveProject) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .testTag("project_workspace_column")
                                ) {
                                    // Item 1: Concept Brief & Explanatory Notes (Always Visible!)
                                    item {
                                        if (isEditingProjectDescription) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                                                ),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(
                                                        text = "✍️ Edit Explanatory Notes",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )

                                                    Spacer(modifier = Modifier.height(10.dp))

                                                    // Formatting Buttons Toolbar
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = { applyFormattingToProjectDesc("*") },
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text("B", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
                                                        }
                                                        Button(
                                                            onClick = { applyFormattingToProjectDesc("_") },
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text("I", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
                                                        }
                                                        Button(
                                                            onClick = { applyFormattingToProjectDesc("~") },
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text("S", style = MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
                                                        }
                                                        Button(
                                                            onClick = {
                                                                val text = editedProjectDescriptionText.text
                                                                val selection = editedProjectDescriptionText.selection
                                                                val start = selection.start
                                                                val bulletText = "• "
                                                                val newText = text.substring(0, start) + bulletText + text.substring(start)
                                                                editedProjectDescriptionText = TextFieldValue(text = newText, selection = TextRange(start + bulletText.length))
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text("• List", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
                                                        }
                                                        Button(
                                                            onClick = {
                                                                val text = editedProjectDescriptionText.text
                                                                val selection = editedProjectDescriptionText.selection
                                                                val start = selection.start
                                                                val numberText = "1. "
                                                                val newText = text.substring(0, start) + numberText + text.substring(start)
                                                                editedProjectDescriptionText = TextFieldValue(text = newText, selection = TextRange(start + numberText.length))
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text("1. List", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    OutlinedTextField(
                                                        value = editedProjectDescriptionText,
                                                        onValueChange = { newValue ->
                                                            val textChange = newValue.text
                                                            val oldText = editedProjectDescriptionText.text
                                                            
                                                            val isNewlineInserted = textChange.length > oldText.length && 
                                                                newValue.selection.start > 0 && 
                                                                textChange[newValue.selection.start - 1] == '\n'
                                                            
                                                            if (isNewlineInserted) {
                                                                val insertPos = newValue.selection.start
                                                                val textBefore = textChange.substring(0, insertPos - 1)
                                                                val textAfter = textChange.substring(insertPos)
                                                                
                                                                val lines = textBefore.split('\n')
                                                                val lastLine = lines.lastOrNull() ?: ""
                                                                
                                                                val bulletRegex = Regex("""^(\s*)([•\-\*])\s*(.*)""")
                                                                val bulletMatch = bulletRegex.find(lastLine)
                                                                
                                                                val numberRegex = Regex("""^(\s*)(\d+)\.\s*(.*)""")
                                                                val numberMatch = numberRegex.find(lastLine)
                                                                
                                                                if (bulletMatch != null) {
                                                                    val indent = bulletMatch.groupValues[1]
                                                                    val prefix = bulletMatch.groupValues[2]
                                                                    val content = bulletMatch.groupValues[3].trim()
                                                                    
                                                                    if (content.isEmpty()) {
                                                                        val indexPrevLineStart = textBefore.length - lastLine.length
                                                                        val newText = textChange.substring(0, indexPrevLineStart) + textAfter
                                                                        val newCursorPos = minOf(newText.length, indexPrevLineStart)
                                                                        editedProjectDescriptionText = TextFieldValue(
                                                                            text = newText,
                                                                            selection = TextRange(newCursorPos)
                                                                        )
                                                                    } else {
                                                                        val nextBullet = "$indent$prefix "
                                                                        val newText = textChange.substring(0, insertPos) + nextBullet + textAfter
                                                                        val newCursorPos = insertPos + nextBullet.length
                                                                        editedProjectDescriptionText = TextFieldValue(
                                                                            text = newText,
                                                                            selection = TextRange(newCursorPos)
                                                                        )
                                                                    }
                                                                } else if (numberMatch != null) {
                                                                    val indent = numberMatch.groupValues[1]
                                                                    val numStr = numberMatch.groupValues[2]
                                                                    val currentNum = numStr.toIntOrNull() ?: 1
                                                                    val content = numberMatch.groupValues[3].trim()
                                                                    
                                                                    if (content.isEmpty()) {
                                                                        val indexPrevLineStart = textBefore.length - lastLine.length
                                                                        val newText = textChange.substring(0, indexPrevLineStart) + textAfter
                                                                        val newCursorPos = minOf(newText.length, indexPrevLineStart)
                                                                        editedProjectDescriptionText = TextFieldValue(
                                                                            text = newText,
                                                                            selection = TextRange(newCursorPos)
                                                                        )
                                                                    } else {
                                                                        val nextNumber = "$indent${currentNum + 1}. "
                                                                        val newText = textChange.substring(0, insertPos) + nextNumber + textAfter
                                                                        val newCursorPos = insertPos + nextNumber.length
                                                                        editedProjectDescriptionText = TextFieldValue(
                                                                            text = newText,
                                                                            selection = TextRange(newCursorPos)
                                                                        )
                                                                    }
                                                                } else {
                                                                    editedProjectDescriptionText = newValue
                                                                }
                                                            } else {
                                                                editedProjectDescriptionText = newValue
                                                            }
                                                        },
                                                        visualTransformation = WhatsAppFormattingTransformation(),
                                                        placeholder = { Text("Write explanatory notes, specs, goals, or targets...", fontSize = 13.sp) },
                                                        singleLine = false,
                                                        minLines = 4,
                                                        maxLines = 5,
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                        ),
                                                        modifier = Modifier.fillMaxWidth().testTag("edit_project_description_input")
                                                    )

                                                    Spacer(modifier = Modifier.height(10.dp))

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        TextButton(
                                                            onClick = {
                                                                isEditingProjectDescription = false
                                                            }
                                                        ) {
                                                            Text("Cancel")
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Button(
                                                            onClick = {
                                                                viewModel.updateChecklist(activeChecklist.copy(description = editedProjectDescriptionText.text))
                                                                isEditingProjectDescription = false
                                                            }
                                                        ) {
                                                            Text("Save Notes")
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Card(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                ),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("🧠", fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                                                            Text(
                                                                text = "Concept Brief & Explanatory Notes",
                                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                editedProjectDescriptionText = TextFieldValue(activeChecklist.description ?: "")
                                                                isEditingProjectDescription = true
                                                            },
                                                            modifier = Modifier.size(28.dp).testTag("edit_project_desc_button")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Edit,
                                                                contentDescription = "Edit notes",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(10.dp))

                                                    val descText = activeChecklist.description
                                                    if (!descText.isNullOrBlank()) {
                                                        Text(
                                                            text = parseWhatsAppText(descText),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "Add rich explanatory notes to this project workspace! Click the edit button above to start brain-dumping formatting notes, checklist specifications, or general directions. 🚀",
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Item 2: Separator Label
                                    item {
                                        Text(
                                            text = "Associated Task Lists",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }

                                    // Item 3: Sub task lists or empty placeholder
                                    if (projectTaskLists.isEmpty()) {
                                        item {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                                ),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center,
                                                    modifier = Modifier.padding(24.dp).fillMaxWidth()
                                                ) {
                                                    Text(text = "📋", fontSize = 32.sp, modifier = Modifier.padding(bottom = 6.dp))
                                                    Text(
                                                        text = "No task lists yet",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                                                    )
                                                    Text(
                                                        text = "This project has no task folders yet. Use the action bar below to add targets!",
                                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        items(projectTaskLists.size, key = { projectTaskLists[it].id }) { index ->
                                            val taskList = projectTaskLists[index]
                                            Card(
                                                onClick = {
                                                    viewModel.selectedChecklistId.value = taskList.id
                                                    selectedSection = "checklists"
                                                    isRightMenuOpen = false
                                                },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                ),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                shape = RoundedCornerShape(18.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(taskList.icon, fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                                                    
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = taskList.name,
                                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        val subItems = remember(allItems, taskList.id) {
                                                            allItems.filter { it.checklistId == taskList.id }
                                                        }
                                                        val totalCount = subItems.size
                                                        val doneCount = subItems.count { it.isCompleted }
                                                        val pendingCount = totalCount - doneCount
                                                        Text(
                                                            text = if (totalCount > 0) "$doneCount/$totalCount completed ($pendingCount pending)" else "0 tasks",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.toggleChecklistVisibleInTaskListSec(taskList)
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = if (taskList.isVisibleInTaskListSec) Icons.Default.Check else Icons.Default.Add,
                                                            contentDescription = "Show in Sidebar",
                                                            tint = if (taskList.isVisibleInTaskListSec) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                    
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.deleteChecklist(taskList)
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete Task List",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (currentItems.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(text = "📝", fontSize = 44.sp, modifier = Modifier.padding(bottom = 8.dp))
                                            Text(
                                                text = "No check points yet",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                                            )
                                            Text(
                                                text = "Add checklist targets using the input bar below!",
                                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                                modifier = Modifier.padding(horizontal = 32.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    // Dynamic Celebration Banner when 100% completed!
                                    val allCompleted = totalItems > 0 && checkedItems == totalItems
                                    if (allCompleted) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                                            colors = listOf(
                                                                MaterialTheme.colorScheme.primary,
                                                                MaterialTheme.colorScheme.secondary
                                                            )
                                                        )
                                                    )
                                                    .padding(14.dp)
                                                    .fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Text("🎉", fontSize = 28.sp)
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Pack preparation verified!",
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            style = MaterialTheme.typography.titleSmall
                                                        )
                                                        Text(
                                                            text = "All items secured. High five! Alarms will pop to safeguard your travel.",
                                                            color = Color.White.copy(alpha = 0.9f),
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    LazyColumn(
                                        state = checklistLazyListState,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .testTag("checklist_items_list")
                                    ) {
                                        items(currentItems.size) { index ->
                                            val item = currentItems[index]
                                            ChecklistItemRow(
                                                item = item,
                                                isFirst = index == 0,
                                                isLast = index == currentItems.lastIndex,
                                                onMoveUp = { viewModel.moveItemUp(item) },
                                                onMoveDown = { viewModel.moveItemDown(item) },
                                                onConfigureReminder = { editingItem = item },
                                                onCheckChange = { viewModel.toggleItemCompletion(item) },
                                                onDelete = { itemToDelete = item },
                                                onDeactivateAlarm = { itemToDeactivateAlarm = item },
                                                onToggleAddedToToday = if (activeChecklist?.id != todayChecklist?.id) {
                                                    { viewModel.toggleItemAddedToToday(item) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (!isActiveProject) {
                                    CommaSuggestBox(
                                        text = newItemText,
                                        onTextChange = { newItemText = it },
                                        checklists = checklists,
                                        categories = categories,
                                        allItems = allItems,
                                        onAddItemToSpecificChecklist = { listId, taskText ->
                                            viewModel.addItemToSpecificChecklist(listId, taskText, isAddedToToday = true)
                                        },
                                        onCreateNewListAndMove = { listName, taskText ->
                                            viewModel.createChecklistWithInitialTask(listName, taskText)
                                        },
                                        onAddShortcutToToday = { item ->
                                            viewModel.addItemToTodayAsShortcut(item)
                                        },
                                        onAddChecklistShortcutToToday = { checklistId ->
                                            viewModel.addChecklistShortcutToToday(checklistId)
                                        },
                                        onAddCustomShortcut = { customText ->
                                            activeChecklist?.id?.let { listId ->
                                                viewModel.addItemToSpecificChecklist(listId, customText)
                                            }
                                        },
                                        onDismiss = { newItemText = "" },
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .offset(y = (-80).dp)
                                    )
                                }

                                // Quick add checkpoint input
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(vertical = 12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .fillMaxWidth()
                                    ) {
                                        val placeholderText = if (isActiveProject) {
                                            "Add a Task List..."
                                        } else if (activeChecklist?.categoryId == todoCategoryId) {
                                            "Add a Task..."
                                        } else {
                                            "Add checkpoint..."
                                        }

                                        OutlinedTextField(
                                            value = newItemText,
                                            onValueChange = { newItemText = it },
                                            placeholder = {
                                                Text(
                                                    text = placeholderText,
                                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            maxLines = 1,
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    if (newItemText.isNotBlank()) {
                                                        if (isActiveProject) {
                                                            viewModel.createTaskListInProject(newItemText, activeChecklist!!.id, todoCategoryId)
                                                        } else {
                                                            viewModel.addItemToChecklist(newItemText)
                                                        }
                                                        newItemText = ""
                                                        keyboardController?.hide()
                                                    }
                                                }
                                            ),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = Color.Transparent,
                                                focusedBorderColor = Color.Transparent,
                                                disabledBorderColor = Color.Transparent,
                                                errorBorderColor = Color.Transparent
                                            ),
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("new_item_input")
                                        )

                                        Button(
                                            onClick = {
                                                if (newItemText.isNotBlank()) {
                                                    if (isActiveProject) {
                                                        viewModel.createTaskListInProject(newItemText, activeChecklist!!.id, todoCategoryId)
                                                    } else {
                                                        viewModel.addItemToChecklist(newItemText)
                                                    }
                                                    newItemText = ""
                                                    keyboardController?.hide()
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("add_item_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Add Item",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } // close else of normal checklist conditional
                    } // close else ->
                    } // close when (selectedSection)
                } // close else of search expanded
            }
        }

        // --- SECTION: Collapsible Right Side Lists Menu ---
        if (isRightMenuOpen) {
            // Background dimming scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { isRightMenuOpen = false }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(310.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = false) {} // block clicks passing down
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    .padding(vertical = 16.dp, horizontal = 14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Rightside Drawer Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Workspace",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(onClick = { isRightMenuOpen = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close collapsible menu"
                            )
                        }
                    }

                    // SECTION 1: TODAY
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            DynamicCalendarIcon(size = 14.dp)
                            Text(
                                text = "TODAY'S PLANNER",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                            )
                        }
                        
                        val isTodaySelected = selectedSection == "today"
                        Card(
                            onClick = {
                                selectedSection = "today"
                                isRightMenuOpen = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isTodaySelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isTodaySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DynamicCalendarIcon(size = 20.dp, modifier = Modifier.padding(end = 10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Today's Agenda",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isTodaySelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    val countToday = allItems.count { it.dueDate != null && isTimestampToday(it.dueDate) && !it.isCompleted }
                                    Text(
                                        text = "$countToday tasks scheduled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isTodaySelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 2: TASKS LISTS
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✅ TASKS LISTS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        initialCategoryForAddDialog = todoCategoryId
                                        showAddChecklistDialog = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add New Tasks List",
                                        tint = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        selectedSection = "tasks_dashboard"
                                        isRightMenuOpen = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp)
                                 ) {
                                    Text("See Full List", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                 }
                            }
                        }

                        if (todoChecklists.isEmpty()) {
                            Text(
                                text = "No active task lists",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            val visibleTasks = if (isTasksExpandedInDrawer) todoChecklists else todoChecklists.take(5)
                            visibleTasks.forEach { todoList ->
                                val isSelected = todoList.id == selectedChecklistId && selectedSection == "checklists"
                                Card(
                                    onClick = {
                                        viewModel.selectedChecklistId.value = todoList.id
                                        selectedSection = "checklists"
                                        isRightMenuOpen = false
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        }
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(todoList.icon, fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                                        val hasTodoTodayShortcut = remember(todayItems, todoList.id) {
                                            todayItems.any { it.text.startsWith("[CL_SHORTCUT:${todoList.id}]") }
                                        }
                                        IconButton(
                                            onClick = {
                                                val shortcutItem = todayItems.find { it.text.startsWith("[CL_SHORTCUT:${todoList.id}]") }
                                                if (shortcutItem != null) {
                                                    viewModel.deleteItem(shortcutItem)
                                                    android.widget.Toast.makeText(context, "Shortcut removed from Today! 🗑️", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    viewModel.addChecklistShortcutToToday(todoList.id)
                                                    android.widget.Toast.makeText(context, "Shortcut added to Today's planner! 🌟", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Pin / Shortcut to Today",
                                                tint = if (hasTodoTodayShortcut) androidx.compose.ui.graphics.Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = todoList.name,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (todoList.projectId != null) {
                                                val associatedProject = checklists.find { it.id == todoList.projectId }
                                                if (associatedProject != null) {
                                                    Text(
                                                        text = "📁 ${associatedProject.name}",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Normal),
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        
                                        val filterItems = allItems.filter { it.checklistId == todoList.id }
                                        val pendingTodoCount = filterItems.count { !it.isCompleted }
                                        Text(
                                            text = "$pendingTodoCount pending",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        
                                        IconButton(
                                            onClick = { moveChecklistRow(todoList.id, -1, todoChecklists) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move Up",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { moveChecklistRow(todoList.id, 1, todoChecklists) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move Down",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 2.5: IDEA SANDBOX
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "💡 ENTREPRENEUR IDEAS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        
                        val isIdeasSelected = selectedSection == "ideas"
                        Card(
                            onClick = {
                                selectedSection = "ideas"
                                isRightMenuOpen = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isIdeasSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isIdeasSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💡", fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Bright Ideas Sandbox",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isIdeasSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    val ideaItems = allItems.filter { it.checklistId == ideaChecklist?.id }
                                    val countIdeas = ideaItems.size
                                    Text(
                                        text = "$countIdeas bright concepts captured",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isIdeasSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 3: PROJECTS
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📁 PROJECTS PLANNER",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        initialCategoryForAddDialog = projectsCategoryId
                                        showAddChecklistDialog = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add New Project",
                                        tint = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        selectedSection = "projects"
                                        isRightMenuOpen = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("See Full List", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))

                        if (projectChecklists.isEmpty()) {
                            Text(
                                    text = "No active projects",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            val visibleProjects = if (isProjectsExpandedInDrawer) projectChecklists else projectChecklists.take(5)
                            visibleProjects.forEach { project ->
                                val isSelected = project.id == selectedChecklistId && selectedSection == "checklists"
                                Card(
                                    onClick = {
                                        viewModel.selectedChecklistId.value = project.id
                                        selectedSection = "checklists"
                                        isRightMenuOpen = false
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        }
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(project.icon, fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                                        val hasProjectTodayShortcut = remember(todayItems, project.id) {
                                            todayItems.any { it.text.startsWith("[CL_SHORTCUT:${project.id}]") }
                                        }
                                        IconButton(
                                            onClick = {
                                                val shortcutItem = todayItems.find { it.text.startsWith("[CL_SHORTCUT:${project.id}]") }
                                                if (shortcutItem != null) {
                                                    viewModel.deleteItem(shortcutItem)
                                                    android.widget.Toast.makeText(context, "Shortcut removed from Today! 🗑️", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    viewModel.addChecklistShortcutToToday(project.id)
                                                    android.widget.Toast.makeText(context, "Shortcut added to Today's planner! 🌟", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Pin / Shortcut to Today",
                                                tint = if (hasProjectTodayShortcut) androidx.compose.ui.graphics.Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        val projItems = allItems.filter { it.checklistId == project.id }
                                        val projDone = projItems.count { it.isCompleted }
                                        val projTotal = projItems.size
                                        Text(
                                            text = "$projDone/$projTotal",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )

                                        IconButton(
                                            onClick = {
                                                moveChecklistRow(project.id, -1, projectChecklists)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move Up",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                moveChecklistRow(project.id, 1, projectChecklists)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move Down",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }


                        }
                    }

                    // SECTION 4: CHECKLISTS
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📋 CHECKLISTS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    initialCategoryForAddDialog = selectedFilterCategoryId
                                    showAddChecklistDialog = true
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add New Checklist",
                                    tint = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            TextButton(
                                onClick = {
                                    selectedSection = "checklists_dashboard"
                                    isRightMenuOpen = false
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("See Full List", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            item {
                                val isAllSelected = selectedFilterCategoryId == null
                                FilterChip(
                                    selected = isAllSelected,
                                    onClick = { viewModel.selectedFilterCategoryId.value = null },
                                    label = { Text("All", fontSize = 11.sp) },
                                    shape = RoundedCornerShape(100.dp)
                                )
                            }

                            items(filteredCategoriesForChips) { category ->
                                val isSelected = selectedFilterCategoryId == category.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectedFilterCategoryId.value = category.id },
                                    label = { Text(category.name, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(100.dp)
                                )
                            }

                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { showAddCategoryDialog = true },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add new Category",
                                            modifier = Modifier.size(12.dp)
                                        )
                                    },
                                    label = { Text("Add Label", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    shape = RoundedCornerShape(100.dp),
                                    modifier = Modifier.testTag("categories_add_label_chip")
                                )
                            }
                        }

                        if (filteredStandardChecklists.isEmpty()) {
                            Text(
                                text = "No checklists found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        } else {
                            val visibleChecklists = if (isChecklistsExpandedInDrawer) filteredStandardChecklists else filteredStandardChecklists.take(5)
                            visibleChecklists.forEach { checklist ->
                                val isSelected = checklist.id == selectedChecklistId && selectedSection == "checklists"
                                Card(
                                    onClick = {
                                        viewModel.selectedChecklistId.value = checklist.id
                                        selectedSection = "checklists"
                                        isRightMenuOpen = false
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(checklist.icon, fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                                        val hasChecklistTodayShortcut = remember(todayItems, checklist.id) {
                                            todayItems.any { it.text.startsWith("[CL_SHORTCUT:${checklist.id}]") }
                                        }
                                        IconButton(
                                            onClick = {
                                                val shortcutItem = todayItems.find { it.text.startsWith("[CL_SHORTCUT:${checklist.id}]") }
                                                if (shortcutItem != null) {
                                                    viewModel.deleteItem(shortcutItem)
                                                    android.widget.Toast.makeText(context, "Shortcut removed from Today! 🗑️", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    viewModel.addChecklistShortcutToToday(checklist.id)
                                                    android.widget.Toast.makeText(context, "Shortcut added to Today's planner! 🌟", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Pin / Shortcut to Today",
                                                tint = if (hasChecklistTodayShortcut) androidx.compose.ui.graphics.Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = checklist.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        IconButton(
                                            onClick = {
                                                moveChecklistRow(checklist.id, -1, filteredStandardChecklists)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Move Up",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                moveChecklistRow(checklist.id, 1, filteredStandardChecklists)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Move Down",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 4.5: COMPLETED TASKS (above settings)
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "🏆 COMPLETED TASKS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        
                        val isCompletedSelected = selectedSection == "completed_tasks"
                        Card(
                            onClick = {
                                selectedSection = "completed_tasks"
                                isRightMenuOpen = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCompletedSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isCompletedSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("drawer_completed_tasks_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🏆", fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Completed Task Archive",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isCompletedSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    val completedCount = allItems.count { it.isCompleted }
                                    Text(
                                        text = "$completedCount accomplished targets",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCompletedSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 5: APP SETTINGS & MOCK BACKUPS (Last element in the rightside drawer)
                    Spacer(modifier = Modifier.height(18.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "⚙️ APP SETTINGS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        var isSettingsExpanded by remember { mutableStateOf(false) }
                        var lastBackup by remember { mutableStateOf(sharedPrefs.getString("last_backup_time", "Never") ?: "Never") }
                        var isAutoBackup by remember { mutableStateOf(sharedPrefs.getBoolean("auto_backup_enabled", true)) }
                        var isHapticOn by remember { mutableStateOf(sharedPrefs.getBoolean("haptic_feedback_enabled", true)) }
                        var isSettingsDarkTheme by remember { mutableStateOf(sharedPrefs.getBoolean("settings_dark_theme", false)) }
                        var isSyncing by remember { mutableStateOf(false) }
                        val coroutineScope = rememberCoroutineScope()

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("app_settings_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isSettingsExpanded = !isSettingsExpanded }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚙️", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                                        Text(
                                            text = "Preferences & Backups",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand preferences",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isSettingsExpanded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Mock Preference 1: Force Dark Mode Override
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("🌙", fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                                            Text("Force Dark Mode", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = isSettingsDarkTheme,
                                            onCheckedChange = { checked ->
                                                isSettingsDarkTheme = checked
                                                sharedPrefs.edit().putBoolean("settings_dark_theme", checked).apply()
                                            },
                                            modifier = Modifier.scale(0.65f).height(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Mock Preference 2: Haptic ticks on actions
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("📳", fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                                            Text("Haptic Feedback Ticks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = isHapticOn,
                                            onCheckedChange = { checked ->
                                                isHapticOn = checked
                                                sharedPrefs.edit().putBoolean("haptic_feedback_enabled", checked).apply()
                                            },
                                            modifier = Modifier.scale(0.65f).height(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Local Storage Manual Backup & Restore Section
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Text("💾", fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                                        Text(
                                            text = "Local Manual Backup & Restore",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Text(
                                        text = "Keep your checklists and goals safe. Save a local backup JSON file or restore from a previous one if you reinstall or switch devices.",
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                                                createDocumentLauncher.launch("karapan_backup_$timestamp.json")
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 6.dp)
                                        ) {
                                            Text("💾 Export Backup to File", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 6.dp)
                                        ) {
                                            Text("📂 Restore Backup from File", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

    // --- SECTION: Reminders dialog settings for active checklist ---
    var showMapPickerForChecklist by remember { mutableStateOf(false) }

    if (showEditDetailsDialog && activeChecklist != null) {
        var listName by remember { mutableStateOf(activeChecklist.name) }
        var listEmoji by remember { mutableStateOf(activeChecklist.icon) }
        var listCategoryId by remember { mutableStateOf(activeChecklist.categoryId) }
        var pastedNotes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showEditDetailsDialog = false },
            title = { Text("Edit Checklist Details") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Customize the details of checklist '${activeChecklist.name}'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1. Title Input
                    OutlinedTextField(
                        value = listName,
                        onValueChange = { listName = it },
                        label = { Text("Checklist Name") },
                        placeholder = { Text("E.g. Daily Groceries") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_checklist_name_input")
                    )

                    // 2. Icon Selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Checklist Icon Accent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val emojis = listOf("🏫", "🧺", "✈️", "🏋️", "💼", "🛒", "💻", "🪴", "🌿", "🩺", "🎨", "🍳", "📋", "📓", "🌟")
                            emojis.forEach { emoji ->
                                val isSelected = listEmoji == emoji
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { listEmoji = emoji },
                                    label = { Text(emoji, fontSize = 16.sp) },
                                    border = null,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // 3. Category selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Category Association",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = listCategoryId == cat.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { listCategoryId = cat.id },
                                    label = { Text(cat.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(100.dp)
                                )
                            }
                        }
                    }

                    // 4. Multi-line paste area for checkpoints
                    OutlinedTextField(
                        value = pastedNotes,
                        onValueChange = { pastedNotes = it },
                        label = { Text("Paste Checklist Notes (Optional)") },
                        placeholder = { Text("E.g. Paste here to auto convert:\nMarker pens\nCalculator\nLaptop\nLaptop Charger") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_pasted_checkpoints_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateChecklistWithParsedItems(
                            activeChecklist.copy(
                                name = listName.trim(),
                                icon = listEmoji,
                                categoryId = listCategoryId,
                                isTemplate = false
                            ),
                            pastedNotes
                        )
                        showEditDetailsDialog = false
                    },
                    enabled = listName.isNotBlank()
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDetailsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showListSettingsDialog && activeChecklist != null) {
        var showDatePickerForList by remember { mutableStateOf(false) }
        var showTimePickerForList by remember { mutableStateOf(false) }
        var locationInput by remember { mutableStateOf(activeChecklist.locationName ?: "") }
        var isTimeReminderEnabled by remember { mutableStateOf(activeChecklist.isReminderEnabled) }
        var isAllDay by remember { mutableStateOf(activeChecklist.isAllDay) }
        var repeatInterval by remember { mutableStateOf(activeChecklist.repeatInterval ?: "none") }

        val initialCal = Calendar.getInstance()
        if (activeChecklist.dueDate != null) {
            initialCal.timeInMillis = activeChecklist.dueDate
        }
        var yearInput by remember { mutableStateOf(initialCal.get(Calendar.YEAR).toString()) }
        var monthInput by remember { mutableStateOf((initialCal.get(Calendar.MONTH) + 1).toString()) }
        var dayInput by remember { mutableStateOf(initialCal.get(Calendar.DAY_OF_MONTH).toString()) }

        val initialHour = initialCal.get(Calendar.HOUR_OF_DAY)
        val initialMinute = initialCal.get(Calendar.MINUTE)

        var hourInput by remember { mutableStateOf(String.format("%02d", initialHour)) }
        var minuteInput by remember { mutableStateOf(String.format("%02d", initialMinute)) }

        AlertDialog(
            onDismissRequest = { showListSettingsDialog = false },
            title = { Text("Set Reminder Schedule") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Configure a scheduling reminder for '${activeChecklist.name}'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1. Time reminder configuration
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTimeReminderEnabled = !isTimeReminderEnabled },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isTimeReminderEnabled, onCheckedChange = { isTimeReminderEnabled = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enable Scheduling Reminder", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (isTimeReminderEnabled) {
                        // Date selector
                        Text(
                            "Select Reminder Starting Date", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Modern Clickable Date Picker Display
                        Card(
                            onClick = { showDatePickerForList = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Starting Date (Tap to change)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val formattedDateDisplay = remember(dayInput, monthInput, yearInput) {
                                        try {
                                            val d = dayInput.toIntOrNull() ?: 1
                                            val m = (monthInput.toIntOrNull() ?: 1) - 1
                                            val y = yearInput.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                                            val calInspect = Calendar.getInstance().apply {
                                                set(Calendar.YEAR, y)
                                                set(Calendar.MONTH, m)
                                                set(Calendar.DAY_OF_MONTH, d)
                                            }
                                            val sdf = java.text.SimpleDateFormat("EEEE, MMMM dd, yyyy", java.util.Locale.getDefault())
                                            sdf.format(calInspect.time)
                                        } catch (e: Exception) {
                                            "$dayInput/$monthInput/$yearInput"
                                        }
                                    }
                                    Text(
                                        text = formattedDateDisplay,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Open Calendar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        if (showDatePickerForList) {
                            val currentDueMillis = remember(dayInput, monthInput, yearInput) {
                                val d = dayInput.toIntOrNull() ?: 1
                                val m = (monthInput.toIntOrNull() ?: 1) - 1
                                val y = yearInput.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                                Calendar.getInstance().apply {
                                    set(Calendar.YEAR, y)
                                    set(Calendar.MONTH, m)
                                    set(Calendar.DAY_OF_MONTH, d)
                                }.timeInMillis
                            }
                            StandardDatePickerDialog(
                                initialDateMillis = currentDueMillis,
                                onDateSelected = { selected ->
                                    val selectedCal = Calendar.getInstance().apply { timeInMillis = selected }
                                    dayInput = selectedCal.get(Calendar.DAY_OF_MONTH).toString()
                                    monthInput = (selectedCal.get(Calendar.MONTH) + 1).toString()
                                    yearInput = selectedCal.get(Calendar.YEAR).toString()
                                    showDatePickerForList = false
                                },
                                onDismiss = { showDatePickerForList = false }
                            )
                        }

                        // Presets
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Today" to 0,
                                "Tomorrow" to 1,
                                "In 3 Days" to 3,
                                "Next Week" to 7
                            ).forEach { (label, offset) ->
                                AssistChip(
                                    onClick = {
                                        val newCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
                                        dayInput = newCal.get(Calendar.DAY_OF_MONTH).toString()
                                        monthInput = (newCal.get(Calendar.MONTH) + 1).toString()
                                        yearInput = newCal.get(Calendar.YEAR).toString()
                                    },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        // All day toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAllDay = !isAllDay },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isAllDay, onCheckedChange = { isAllDay = it })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All-Day Reminder (Default)", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (!isAllDay) {
                            Text(
                                "Select Specific Alarm Time", 
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Modern Clickable 24-Hour Time Picker Display
                            Card(
                                onClick = { showTimePickerForList = true },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Specific Alarm Time (Tap to change)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val displayHour = hourInput.toIntOrNull() ?: 12
                                        val displayMinute = minuteInput.toIntOrNull() ?: 0
                                        Text(
                                            text = String.format("%02d : %02d (24-Hour clock)", displayHour, displayMinute),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Open Time Picker",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            if (showTimePickerForList) {
                                StandardTimePickerDialog(
                                    initialHour = hourInput.toIntOrNull() ?: 12,
                                    initialMinute = minuteInput.toIntOrNull() ?: 0,
                                    onTimeSelected = { h, m ->
                                        hourInput = String.format("%02d", h)
                                        minuteInput = String.format("%02d", m)
                                        showTimePickerForList = false
                                    },
                                    onDismiss = { showTimePickerForList = false }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        // Repetition Selection
                        Text(
                            "Repeat Interval", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "none" to "No Repeat",
                                "daily" to "Daily",
                                "weekly" to "Weekly",
                                "monthly" to "Monthly"
                            ).forEach { (id, label) ->
                                val isSelected = repeatInterval == id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { repeatInterval = id },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // 2. Location reminder configuration
                    OutlinedTextField(
                        value = locationInput,
                        onValueChange = { locationInput = it },
                        label = { Text("Arrive Notification Location") },
                        placeholder = { Text("E.g. Work, Gym, Library, Grocery") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { showMapPickerForChecklist = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("pick_location_checklist_button")
                    ) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = "Pick on Map")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Location on Google Maps")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Home", "Work", "Library", "Grocery", "Gym").forEach { loc ->
                            AssistChip(
                                onClick = { locationInput = loc },
                                label = { Text(loc) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val d = dayInput.toIntOrNull() ?: initialCal.get(Calendar.DAY_OF_MONTH)
                        val mo = monthInput.toIntOrNull() ?: (initialCal.get(Calendar.MONTH) + 1)
                        val y = yearInput.toIntOrNull() ?: initialCal.get(Calendar.YEAR)
                        val h = hourInput.toIntOrNull() ?: 12
                        val m = minuteInput.toIntOrNull() ?: 0

                        val dueInMillis = if (isTimeReminderEnabled) {
                            calculateEpochMillisForDateTime(d, mo, y, isAllDay, h, m)
                        } else null

                        val remTime = if (isTimeReminderEnabled && !isAllDay) {
                            String.format("%02d:%02d", h, m)
                        } else null

                        viewModel.updateChecklist(
                            activeChecklist.copy(
                                dueDate = dueInMillis,
                                locationName = locationInput.trim().ifEmpty { null },
                                isReminderEnabled = isTimeReminderEnabled,
                                isAllDay = isAllDay,
                                reminderTime = remTime,
                                repeatInterval = repeatInterval
                            )
                        )
                        showListSettingsDialog = false
                    }
                ) {
                    Text("Save Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showListSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )

        if (showMapPickerForChecklist) {
            MapPickerDialog(
                initialLocation = locationInput,
                onDismiss = { showMapPickerForChecklist = false },
                onLocationSelected = { loc, lat, lng ->
                    locationInput = loc
                    // Store lat/lng temporarily in some state if needed, or just use them in the confirm button
                    // For now let's just use the name as requested, but we have the coordinates!
                    showMapPickerForChecklist = false
                }
            )
        }
    }

    // --- SECTION: Checkpoint item details & settings alert dialog ---
    var showMapPickerForItem by remember { mutableStateOf(false) }

    if (editingItem != null) {
        var showDatePickerForItem by remember { mutableStateOf(false) }
        var showTimePickerForItem by remember { mutableStateOf(false) }
        val item = editingItem!!
        var itemTextInput by remember { mutableStateOf(item.text) }
        var isTimeReminderEnabled by remember { mutableStateOf(item.isReminderEnabled) }
        var isAllDay by remember { mutableStateOf(item.isAllDay) }
        var repeatInterval by remember { mutableStateOf(item.repeatInterval ?: "none") }
        
        val initialCal = Calendar.getInstance()
        if (item.dueDate != null) {
            initialCal.timeInMillis = item.dueDate
        }
        var yearInput by remember { mutableStateOf(initialCal.get(Calendar.YEAR).toString()) }
        var monthInput by remember { mutableStateOf((initialCal.get(Calendar.MONTH) + 1).toString()) }
        var dayInput by remember { mutableStateOf(initialCal.get(Calendar.DAY_OF_MONTH).toString()) }

        val initialHour = initialCal.get(Calendar.HOUR_OF_DAY)
        val initialMinute = initialCal.get(Calendar.MINUTE)

        var hourInput by remember { mutableStateOf(String.format("%02d", initialHour)) }
        var minuteInput by remember { mutableStateOf(String.format("%02d", initialMinute)) }
        var locationInput by remember { mutableStateOf(item.locationName ?: "") }

        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Configure Checkpoint") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = itemTextInput,
                        onValueChange = { itemTextInput = it },
                        label = { Text("Checkpoint Description") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTimeReminderEnabled = !isTimeReminderEnabled },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isTimeReminderEnabled, onCheckedChange = { isTimeReminderEnabled = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set Scheduled Reminder")
                    }

                    if (isTimeReminderEnabled) {
                        // Date selector
                        Text(
                            "Select Starting Date", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Modern Clickable Date Picker Display
                        Card(
                            onClick = { showDatePickerForItem = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Starting Date (Tap to change)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val formattedDateDisplay = remember(dayInput, monthInput, yearInput) {
                                        try {
                                            val d = dayInput.toIntOrNull() ?: 1
                                            val m = (monthInput.toIntOrNull() ?: 1) - 1
                                            val y = yearInput.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                                            val calInspect = Calendar.getInstance().apply {
                                                set(Calendar.YEAR, y)
                                                set(Calendar.MONTH, m)
                                                set(Calendar.DAY_OF_MONTH, d)
                                            }
                                            val sdf = java.text.SimpleDateFormat("EEEE, MMMM dd, yyyy", java.util.Locale.getDefault())
                                            sdf.format(calInspect.time)
                                        } catch (e: Exception) {
                                            "$dayInput/$monthInput/$yearInput"
                                        }
                                    }
                                    Text(
                                        text = formattedDateDisplay,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Open Calendar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        if (showDatePickerForItem) {
                            val currentDueMillis = remember(dayInput, monthInput, yearInput) {
                                val d = dayInput.toIntOrNull() ?: 1
                                val m = (monthInput.toIntOrNull() ?: 1) - 1
                                val y = yearInput.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                                Calendar.getInstance().apply {
                                    set(Calendar.YEAR, y)
                                    set(Calendar.MONTH, m)
                                    set(Calendar.DAY_OF_MONTH, d)
                                }.timeInMillis
                            }
                            StandardDatePickerDialog(
                                initialDateMillis = currentDueMillis,
                                onDateSelected = { selected ->
                                    val selectedCal = Calendar.getInstance().apply { timeInMillis = selected }
                                    dayInput = selectedCal.get(Calendar.DAY_OF_MONTH).toString()
                                    monthInput = (selectedCal.get(Calendar.MONTH) + 1).toString()
                                    yearInput = selectedCal.get(Calendar.YEAR).toString()
                                    showDatePickerForItem = false
                                },
                                onDismiss = { showDatePickerForItem = false }
                            )
                        }

                        // Presets
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Today" to 0,
                                "Tomorrow" to 1,
                                "In 3 Days" to 3,
                                "Next Week" to 7
                            ).forEach { (label, offset) ->
                                AssistChip(
                                    onClick = {
                                        val newCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
                                        dayInput = newCal.get(Calendar.DAY_OF_MONTH).toString()
                                        monthInput = (newCal.get(Calendar.MONTH) + 1).toString()
                                        yearInput = newCal.get(Calendar.YEAR).toString()
                                    },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        // All day toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAllDay = !isAllDay },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isAllDay, onCheckedChange = { isAllDay = it })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All-Day Reminder (Default)", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (!isAllDay) {
                            Text(
                                "Select Specific Alarm Time Today/Tomorrow", 
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Modern Clickable 24-Hour Time Picker Display
                            Card(
                                onClick = { showTimePickerForItem = true },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Specific Alarm Time (Tap to change)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val displayHour = hourInput.toIntOrNull() ?: 12
                                        val displayMinute = minuteInput.toIntOrNull() ?: 0
                                        Text(
                                            text = String.format("%02d : %02d (24-Hour clock)", displayHour, displayMinute),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Open Time Picker",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            if (showTimePickerForItem) {
                                StandardTimePickerDialog(
                                    initialHour = hourInput.toIntOrNull() ?: 12,
                                    initialMinute = minuteInput.toIntOrNull() ?: 0,
                                    onTimeSelected = { h, m ->
                                        hourInput = String.format("%02d", h)
                                        minuteInput = String.format("%02d", m)
                                        showTimePickerForItem = false
                                    },
                                    onDismiss = { showTimePickerForItem = false }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        // Repetition Selection
                        Text(
                            "Repeat Interval", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "none" to "No Repeat",
                                "daily" to "Daily",
                                "weekly" to "Weekly",
                                "monthly" to "Monthly"
                            ).forEach { (id, label) ->
                                val isSelected = repeatInterval == id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { repeatInterval = id },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedTextField(
                        value = locationInput,
                        onValueChange = { locationInput = it },
                        label = { Text("Notification Location") },
                        placeholder = { Text("E.g. Home, Grocery, Work, Library") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { showMapPickerForItem = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("pick_location_item_button")
                    ) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = "Pick on Map")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Location on Google Maps")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Home", "Work", "Library", "Grocery", "Gym").forEach { loc ->
                            AssistChip(
                                onClick = { locationInput = loc },
                                label = { Text(loc) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val d = dayInput.toIntOrNull() ?: initialCal.get(Calendar.DAY_OF_MONTH)
                        val mo = monthInput.toIntOrNull() ?: (initialCal.get(Calendar.MONTH) + 1)
                        val y = yearInput.toIntOrNull() ?: initialCal.get(Calendar.YEAR)
                        val h = hourInput.toIntOrNull() ?: 12
                        val m = minuteInput.toIntOrNull() ?: 0

                        val dueInMillis = if (isTimeReminderEnabled) {
                            calculateEpochMillisForDateTime(d, mo, y, isAllDay, h, m)
                        } else null

                        val remTime = if (isTimeReminderEnabled && !isAllDay) {
                            String.format("%02d:%02d", h, m)
                        } else null

                        viewModel.updateItem(
                            item.copy(
                                text = itemTextInput.trim(),
                                dueDate = dueInMillis,
                                locationName = locationInput.trim().ifEmpty { null },
                                isReminderEnabled = isTimeReminderEnabled,
                                isAllDay = isAllDay,
                                reminderTime = remTime,
                                repeatInterval = repeatInterval
                            )
                        )
                        editingItem = null
                    },
                    enabled = itemTextInput.isNotBlank()
                ) {
                    Text("Update Checkpoint")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Cancel")
                }
            }
        )

        if (showMapPickerForItem) {
            MapPickerDialog(
                initialLocation = locationInput,
                onDismiss = { showMapPickerForItem = false },
                onLocationSelected = { loc, lat, lng ->
                    locationInput = loc
                    showMapPickerForItem = false
                }
            )
        }

        val hyperfocusItemsForZone = remember(allItems, selectedSection, currentItems, todayChecklist, todoChecklist) {
            when (selectedSection) {
                "today" -> {
                    val todayId = todayChecklist?.id
                    if (todayId != null) {
                        val today = allItems.filter { it.checklistId == todayId }
                        val synced = allItems.filter { it.checklistId != todayId && it.isAddedToToday }
                        today + synced
                    } else emptyList()
                }
                "todo" -> {
                    val todoId = todoChecklist?.id
                    if (todoId != null) {
                        allItems.filter { it.checklistId == todoId }
                    } else emptyList()
                }
                "checklists" -> currentItems
                else -> currentItems
            }
        }

        if (isHyperfocusActive) {
            HyperfocusModeDialog(
                isActive = isHyperfocusActive,
                onDismiss = { isHyperfocusActive = false },
                items = hyperfocusItemsForZone,
                onToggleCompletion = { item ->
                    viewModel.toggleItemCompletion(item)
                }
            )
        }
    }

    // Wizard/Dialog for Checklist Creation
    if (showAddChecklistDialog) {
        AddChecklistDialog(
            categories = categories,
            onDismiss = { showAddChecklistDialog = false },
            onConfirm = { name, icon, catId, pastedNotes ->
                viewModel.createChecklistWithParsedItems(name, icon, catId, null, pastedNotes)
                showAddChecklistDialog = false
                isRightMenuOpen = false
            },
            initialCategoryId = initialCategoryForAddDialog,
            projectsCategoryId = projectsCategoryId,
            todoCategoryId = todoCategoryId
        )
    }

    // Confirm Deletion dialog
    if (showDeleteConfirmDialog && activeChecklist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(text = "Delete Checklist?")
            },
            text = {
                Text(text = "Are you sure you want to delete '${activeChecklist.name}'? This will permanently delete all associated checkpoints and records.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChecklist(activeChecklist)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(text = "Cancel")
                }
            },
            modifier = Modifier.testTag("delete_confirm_dialog")
        )
    }

    if (checklistToDelete != null) {
        val toDelete = checklistToDelete!!
        AlertDialog(
            onDismissRequest = { checklistToDelete = null },
            title = {
                Text(text = "Delete Checklist?")
            },
            text = {
                Text(text = "Are you sure you want to delete '${toDelete.name}'? This will permanently delete all associated checkpoints and records.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChecklist(toDelete)
                        checklistToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { checklistToDelete = null }) {
                    Text(text = "Cancel")
                }
            },
            modifier = Modifier.testTag("delete_checklist_confirm_dialog")
        )
    }

    // Confirm Reset/Refresh Checklist dialog
    if (showResetConfirmDialog && activeChecklist != null) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(text = "Reset Checklist Progress?")
            },
            text = {
                Text(text = "Are you sure you want to reset all completed checkboxes for '${activeChecklist.name}' back to uncompleted progress?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetCurrentChecklist()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = "Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(text = "Cancel")
                }
            },
            modifier = Modifier.testTag("reset_confirm_dialog")
        )
    }

    // Confirm Checkpoint Item Deletion dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(text = "Delete Checkpoint?")
            },
            text = {
                Text(text = "Are you sure you want to delete checkpoint '${itemToDelete!!.text}' from your checklist?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(itemToDelete!!)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(text = "Cancel")
                }
            },
            modifier = Modifier.testTag("item_delete_confirm_dialog")
        )
    }

    // Confirm Deactive Item Alarm dialog
    if (itemToDeactivateAlarm != null) {
        AlertDialog(
            onDismissRequest = { itemToDeactivateAlarm = null },
            title = {
                Text(text = "Deactivate Alarm?")
            },
            text = {
                Text(text = "Are you sure you want to turn off the alarm reminder for '${itemToDeactivateAlarm!!.text}'?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val originalItem = itemToDeactivateAlarm!!
                        val updated = originalItem.copy(dueDate = null)
                        viewModel.updateItem(updated)
                        com.example.AlarmScheduler.cancelAlarm(context, "item_past_due_${originalItem.id}")
                        itemToDeactivateAlarm = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = "Deactivate")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeactivateAlarm = null }) {
                    Text(text = "Cancel")
                }
            },
            modifier = Modifier.testTag("item_alarm_deactivate_confirm_dialog")
        )
    }

    if (showAddCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        var selectedColor by remember { mutableStateOf("#55654C") }
        val colorsPalette = listOf("#55654C", "#D9EABB", "#75786B", "#131F0E", "#D32F2F", "#1976D2", "#388E3C", "#FBC02D")

        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Create New Label") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = { Text("Label Name (e.g., School, Leisure)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_category_label_input")
                    )

                    Text("Pick Color Preset:", style = MaterialTheme.typography.bodySmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colorsPalette.forEach { colorHex ->
                            val isColorSelected = selectedColor == colorHex
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(colorHex)))
                                    .border(
                                        width = if (isColorSelected) 3.dp else 1.dp,
                                        color = if (isColorSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorHex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catName.isNotBlank()) {
                            viewModel.createCategory(catName, selectedColor)
                            showAddCategoryDialog = false
                        }
                    },
                    enabled = catName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("add_category_label_dialog")
        )
    }

    if (showQuickIdeaDumpDialog) {
        var ideaText by remember { mutableStateOf("") }
        var excitement by remember { mutableStateOf(5) }
        var fright by remember { mutableStateOf(5) }

        AlertDialog(
            onDismissRequest = { showQuickIdeaDumpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💡 ", fontSize = 24.sp)
                    Text("Quick Concept Dump", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Dump your sudden brilliant ideas before they vanish! You can refine and organize them in your Sandbox later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = ideaText,
                        onValueChange = { ideaText = it },
                        placeholder = { Text("What's your raw spark of genius?") },
                        modifier = Modifier.fillMaxWidth().height(120.dp).testTag("quick_idea_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text("Excitement Level: $excitement/10", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = excitement.toFloat(),
                        onValueChange = { excitement = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8
                    )

                    Text("Fright/Dread Level: $fright/10", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = fright.toFloat(),
                        onValueChange = { fright = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ideaText.trim().isNotEmpty()) {
                            viewModel.addIdeaToSandbox(ideaText.trim(), excitement, fright)
                            showQuickIdeaDumpDialog = false
                        }
                    },
                    enabled = ideaText.trim().isNotEmpty()
                ) {
                    Text("Dump to Sandbox")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickIdeaDumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSimulatedLocationDialog) {
        AlertDialog(
            onDismissRequest = { showSimulatedLocationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Simulated Location Setter",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simulated Location Setting", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Mock your device's current location to test place-based checkpoints and automated alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        "Status: ${if (simulatedLocation.isBlank()) "Dynamic (Not Mocked)" else "Mocked at '$simulatedLocation'"}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Home", "Work", "Library", "Grocery", "Gym").forEach { loc ->
                            val isSelected = simulatedLocation.equals(loc, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { 
                                    viewModel.simulatedLocation.value = if (isSelected) "" else loc
                                },
                                label = { Text(loc) },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSimulatedLocationDialog = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                if (simulatedLocation.isNotBlank()) {
                    TextButton(onClick = {
                        viewModel.simulatedLocation.value = ""
                        showSimulatedLocationDialog = false
                    }) {
                        Text("Reset Mock", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            modifier = Modifier.testTag("simulated_location_dialog")
        )
    }

    // Onboarding and Alarm Popups Overlay
    if (isFirstRun) {
        OnboardingApprovalOverlay(
            onDismiss = {
                sharedPrefs.edit().putBoolean("is_first_run", false).apply()
                isFirstRun = false
            }
        )
    } else if (reminderAlerts.isNotEmpty()) {
        val activeAlert = reminderAlerts.find { !it.id.contains("imminent") }
        if (activeAlert != null) {
            PremiumAlarmOverlay(
                alert = activeAlert,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun MicroConfettiEffect(
    trigger: Int,
    modifier: Modifier = Modifier
) {
    if (trigger == 0) return

    val colors = listOf(
        Color(0xFFFFC107), // Amber/Yellow
        Color(0xFFE91E63), // Pink
        Color(0xFF00BCD4), // Cyan
        Color(0xFF4CAF50), // Green
        Color(0xFF9C27B0), // Purple
        Color(0xFFFF5722)  // Orange
    )

    // Remember particles to keep state consistent across triggers
    val particles = remember(trigger) {
        val random = java.util.Random()
        List(14) {
            val angle = random.nextFloat() * 2 * Math.PI
            val speed = 20f + random.nextFloat() * 45f
            val vx = (Math.cos(angle) * speed).toFloat()
            val vy = (Math.sin(angle) * speed).toFloat()
            val color = colors[random.nextInt(colors.size)]
            val size = 6f + random.nextFloat() * 10f
            val isStar = random.nextBoolean()
            mapOf(
                "vx" to vx,
                "vy" to vy,
                "color" to color,
                "size" to size,
                "isStar" to isStar
            )
        }
    }

    val progressAnim = remember(trigger) { Animatable(0f) }

    LaunchedEffect(trigger) {
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    if (progressAnim.value < 1f) {
        Canvas(modifier = modifier.size(140.dp)) {
            val progress = progressAnim.value
            val alpha = 1f - progress

            particles.forEach { p ->
                val vx = p["vx"] as Float
                val vy = p["vy"] as Float
                val color = p["color"] as Color
                val size = p["size"] as Float
                val isStar = p["isStar"] as Boolean

                val cx = center.x + vx * progress
                val cy = center.y + vy * progress

                if (isStar) {
                    // Draw a cute cross-star
                    val starSize = size * (1f - progress)
                    drawLine(
                        color = color.copy(alpha = alpha),
                        start = Offset(cx - starSize, cy),
                        end = Offset(cx + starSize, cy),
                        strokeWidth = starSize * 0.3f
                    )
                    drawLine(
                        color = color.copy(alpha = alpha),
                        start = Offset(cx, cy - starSize),
                        end = Offset(cx, cy + starSize),
                        strokeWidth = starSize * 0.3f
                    )
                } else {
                    // Draw a mini circle confetti
                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = size * (1f - progress),
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }
}

@Composable
fun ChecklistItemRow(
    item: ChecklistItem,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onConfigureReminder: () -> Unit,
    onCheckChange: () -> Unit,
    onDelete: () -> Unit,
    onDeactivateAlarm: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleAddedToToday: (() -> Unit)? = null,
    isTodayList: Boolean = false
) {
    val callNumber = parseCallShortcut(item.text)
    val waNumber = parseWaShortcut(item.text)

    if (callNumber != null) {
        CallShortcutRow(
            item = item,
            callNumber = callNumber,
            isFirst = isFirst,
            isLast = isLast,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onConfigureReminder = onConfigureReminder,
            onDelete = onDelete,
            onCheckChange = onCheckChange
        )
        return
    }
    if (waNumber != null) {
        WhatsAppShortcutRow(
            item = item,
            waNumber = waNumber,
            isFirst = isFirst,
            isLast = isLast,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onConfigureReminder = onConfigureReminder,
            onDelete = onDelete,
            onCheckChange = onCheckChange
        )
        return
    }

    // Scroll / entry transitions state
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    
    var showActions by remember { mutableStateOf(false) }
    var showRemoveFromTodayConfirm by remember { mutableStateOf(false) }
    
    // Smooth check/uncheck visual properties
    val cardScale by animateFloatAsState(
        targetValue = if (item.isCompleted) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (item.isCompleted) 0.65f else 1f,
        animationSpec = tween(durationMillis = 300)
    )

    // Completed state tracking for interactive micro-confetti feedback inside row
    var triggerConfetti by remember { mutableStateOf(0) }
    var previousCompletedState by remember { mutableStateOf(item.isCompleted) }

    LaunchedEffect(item.isCompleted) {
        if (item.isCompleted && !previousCompletedState) {
            triggerConfetti += 1
        }
        previousCompletedState = item.isCompleted
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(durationMillis = 350)) + 
                slideInVertically(
                    initialOffsetY = { 35 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
        exit = fadeOut(animationSpec = tween(durationMillis = 200))
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (item.isCompleted) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                } else if (item.isAddedToToday || isTodayList) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                }
            ),
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = cardAlpha
                }
                .pointerInput(item.id) {
                    detectTapGestures(
                        onDoubleTap = {
                            showActions = !showActions
                        }
                    )
                }
                .testTag("checklist_item_row_${item.id}"),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            ),
            border = if ((item.isAddedToToday || isTodayList) && !item.isCompleted) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else if (!item.isCompleted) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            } else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCheckChange() }
                        .testTag("item_checkbox_${item.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (item.isCompleted) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .border(
                                width = 2.dp,
                                color = if (item.isCompleted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(5.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed check",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Overlay the micro particle confetti explosion right inside the checkbox bound!
                    MicroConfettiEffect(
                        trigger = triggerConfetti,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Description and small reminder tags
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                ) {
                    val splitDetails = getTaskLinesClean(item.text)
                    Text(
                        text = splitDetails.first,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (item.isCompleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        )
                    )
                    if (splitDetails.second != null) {
                        Text(
                            text = splitDetails.second!!,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Normal,
                                color = if (item.isCompleted) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    if (!item.isCompleted) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if (item.isAddedToToday) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        DynamicCalendarIcon(size = 11.dp)
                                        Text(
                                            text = "Today",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                            if (item.dueDate != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                        .clickable { onDeactivateAlarm() }
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                        .testTag("item_alarm_badge_${item.id}")
                                ) {
                                    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                                    val timeStr = try { sdf.format(Date(item.dueDate)) } catch(e: Exception) { "Alarm Set" }
                                    Text(
                                        text = "⏰ $timeStr",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            if (!item.locationName.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "📍 At: ${item.locationName}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (!item.isCompleted && onToggleAddedToToday != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable {
                                if (item.isAddedToToday) {
                                    showRemoveFromTodayConfirm = true
                                } else {
                                    onToggleAddedToToday()
                                }
                            }
                            .testTag("toggle_today_button_${item.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isAddedToToday) {
                            DynamicCalendarIcon(size = 18.dp)
                        } else {
                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                DynamicCalendarIcon(size = 18.dp)
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-3).dp, y = 3.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (showActions) {
                    // Edit checkpoint button next to every item (pencil icon)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onConfigureReminder() }
                            .testTag("edit_item_button_${item.id}")
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit checkpoint details",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Delete item button with RED color
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onDelete() }
                            .testTag("delete_item_button_${item.id}")
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete check point",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Move Up button
                    Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .then(
                            if (!isFirst) Modifier.clickable { onMoveUp() }
                            else Modifier
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Move check point up",
                        tint = if (isFirst) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(14.dp).rotate(90f)
                    )
                }

                // Move Down button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .then(
                            if (!isLast) Modifier.clickable { onMoveDown() }
                            else Modifier
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Move check point down",
                        tint = if (isLast) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(14.dp).rotate(270f)
                    )
                }
                }
            }
        }
    }

    if (showRemoveFromTodayConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveFromTodayConfirm = false },
            title = { Text("Remove from Today?") },
            text = { Text("Are you sure you want to remove '${item.text}' from today's focus list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveFromTodayConfirm = false
                        onToggleAddedToToday?.invoke()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveFromTodayConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Custom Dashed Card mirroring the gorgeous botanical "browse templates" element
@Composable
fun CustomDashedCard() {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val stroke = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f),
                cap = StrokeCap.Round
            )
            drawRoundRect(
                color = outlineVariant,
                style = stroke,
                cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "✨",
                fontSize = 15.sp
            )
            Text(
                text = "Use the side menu to switch or create checklists",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChecklistDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int?, String) -> Unit,
    initialCategoryId: Int? = null,
    projectsCategoryId: Int? = null,
    todoCategoryId: Int? = null
) {
    var name by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("📓") }
    var selectedCategoryId by remember { mutableStateOf<Int?>(initialCategoryId ?: categories.firstOrNull()?.id) }
    var pastedNotes by remember { mutableStateOf("") }

    val emojis = listOf("🏫", "🧺", "✈️", "🏋️", "💼", "🛒", "💻", "🪴", "🌿", "🩺", "🎨", "🍳")

    val isProject = initialCategoryId != null && initialCategoryId == projectsCategoryId
    val isTodo = initialCategoryId != null && initialCategoryId == todoCategoryId

    val dialogTitle = if (isProject) "Add a Project" else if (isTodo) "Add a Task List" else "Create New Checklist"
    val fieldLabel = if (isProject) "Project Title" else if (isTodo) "Task List Title" else "Checklist Title (e.g. Hiking Pack, Work Setup)"
    val fieldPlaceholder = if (isProject) "E.g. Launch New Website" else if (isTodo) "E.g. Weekly Groceries" else "E.g. Going to Class"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = dialogTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(fieldLabel) },
                    placeholder = { Text(fieldPlaceholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_category_name_input")
                )

                // Select associated category
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Category Association",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategoryId == cat.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = null,
                                shape = RoundedCornerShape(100.dp)
                            )
                        }
                    }
                }

                // Multi-line paste area for checkpoints
                OutlinedTextField(
                    value = pastedNotes,
                    onValueChange = { pastedNotes = it },
                    label = { Text("Paste Checklist Notes (Optional)") },
                    placeholder = { Text("E.g. Paste here to auto convert:\nMarker pens\nCalculator\nLaptop\nLaptop Charger") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pasted_checkpoints_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose list icon emoji",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        emojis.forEach { emoji ->
                            val isSelected = selectedEmoji == emoji
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    )
                                    .clickable { selectedEmoji = emoji }
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, selectedEmoji, selectedCategoryId, pastedNotes)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_create_category_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("cancel_create_category_button")
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag("add_category_dialog")
    )
}

@Composable

@Composable
fun MapPickerDialog(
    initialLocation: String,
    initialLat: Double? = null,
    initialLng: Double? = null,
    onDismiss: () -> Unit,
    onLocationSelected: (String, Double, Double) -> Unit
) {
    val defaultLatLng = LatLng(initialLat ?: 6.9271, initialLng ?: 79.8612) // Default to Colombo if none
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 15f)
    }
    var selectedLatLng by remember { mutableStateOf(defaultLatLng) }
    var locationName by remember { mutableStateOf(initialLocation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Location on Google Maps") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("Location Name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = {
                        selectedLatLng = it
                    }
                ) {
                    Marker(
                        state = MarkerState(position = selectedLatLng),
                        title = locationName,
                        snippet = "Selected Location"
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onLocationSelected(locationName, selectedLatLng.latitude, selectedLatLng.longitude)
            }) {
                Text("Confirm Location")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class MapLocationPreset(
    val name: String,
    val x: Float,
    val y: Float,
    val address: String
)

// Helper to determine epoch millis from specified Day, Month, Year, Hour, and Minute
fun calculateEpochMillisForDateTime(
    day: Int, 
    month: Int, 
    year: Int, 
    isAllDay: Boolean, 
    hour: Int = 0, 
    minute: Int = 0
): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.YEAR, year)
    calendar.set(Calendar.MONTH, month - 1)
    calendar.set(Calendar.DAY_OF_MONTH, day)
    
    if (isAllDay) {
        calendar.set(Calendar.HOUR_OF_DAY, 9) // Default to 9:00 AM for all-day notifications
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    } else {
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

// Helper to determine epoch millis from specified Hour, Minute, and AM/PM
fun calculateEpochMillis(hour: Int, minute: Int, isPm: Boolean): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR, if (hour == 12) 0 else hour)
    calendar.set(Calendar.MINUTE, minute)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    calendar.set(Calendar.AM_PM, if (isPm) Calendar.PM else Calendar.AM)
    
    // If the selected time is in the past, assume it is for tomorrow
    if (calendar.timeInMillis < System.currentTimeMillis()) {
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    return calendar.timeInMillis
}

@Composable
fun PremiumAlarmOverlay(
    alert: ReminderAlert,
    viewModel: ChecklistViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var snoozeMinutes by remember { mutableStateOf(15) }
    
    val timeText = remember {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    }

    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val siblingItems = remember(allItems) {
        allItems.filter { it.checklistId == alert.checklistId && it.text != alert.itemText && !it.isCompleted }.take(3)
    }

    val clarityTips = remember {
        listOf(
            "💡 Action over perfection. Just spend 2 minutes on this. Only 2 minutes!",
            "💡 Action barrier is just a 10-second hurdle. Take one small breath and start.",
            "💡 Dopamine-boost: Celebrate when this task is checked! Plan your mini-reward now.",
            "💡 Multi-tasking is an illusion. Focus strictly on this step, close other tabs.",
            "💡 Brain relief: Write down a micro-step if the main task feels too heavy."
        )
    }
    val tipIndex = remember(alert.id) {
        val hash = alert.id.hashCode()
        if (hash < 0) -hash % clarityTips.size else hash % clarityTips.size
    }
    val activeTip = clarityTips[tipIndex]

    // Breathing pulse animator to calm sensory overwhelm
    val infiniteTransition = rememberInfiniteTransition()
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathing"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E0638), // Even darker for maximum visual focus
                        Color(0xFF0F011E)
                    )
                )
            )
            .pointerInput(Unit) {
                detectTapGestures { }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()), // support small/foldable screens
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Sensory Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(0xFFFFD54F), CircleShape), // Warm Attention Accent
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CLARITY ALARM",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = Color(0xFFFFD54F)
                )
            }

            // Calming Breathing Regulator Widget
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(breathScale)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFF81C784), CircleShape) // Grounding green
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Breathe with the pulse...",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Central Massive Focus Card
            val text = alert.itemText ?: alert.message
            val cleanTextDetails = getTaskLinesClean(text)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.08f)
                ),
                border = BorderStroke(2.dp, Color(0xFF9E77F1)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "RIGHT NOW, FOCUS IN ON:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = Color(0xFFBAC4FF)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = cleanTextDetails.first,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            lineHeight = 36.sp
                        ),
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (cleanTextDetails.second != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = cleanTextDetails.second!!,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp
                            ),
                            color = Color(0xFFE2D6FF),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Checklist: ${alert.checklistName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // ADHD Sibling Item Preview Block
            if (siblingItems.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.04f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "📌 Sibling steps in same checklist context:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        siblingItems.forEach { subItem ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text("⬜", fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                                Text(
                                    text = subItem.text,
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // ADHD Brain Tip Block
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF321A5C)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = activeTip,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = Color.White,
                    modifier = Modifier.padding(12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // Buttons Controls Stack
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.dismissAlert(alert.id, context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(52.dp)
                        .testTag("alarm_dismiss_button")
                ) {
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    )
                }

                Button(
                    onClick = {
                        viewModel.selectedChecklistId.value = alert.checklistId
                        viewModel.dismissAlert(alert.id, context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8152CB)
                    ),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(52.dp)
                        .testTag("alarm_view_complete_list_button")
                ) {
                    Text(
                        text = "Show Complete List",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            // Snooze Bar Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (snoozeMinutes > 5) snoozeMinutes -= 5 },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .testTag("alarm_snooze_minus")
                ) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(12.dp, 2.dp)) {
                            drawRoundRect(
                                color = Color.White,
                                cornerRadius = CornerRadius(1f, 1f)
                            )
                        }
                    }
                }

                Button(
                    onClick = { viewModel.snoozeAlert(alert, snoozeMinutes, context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(48.dp)
                        .testTag("alarm_snooze_action")
                ) {
                    Text(
                        text = "Snooze $snoozeMinutes mins",
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    )
                }

                IconButton(
                    onClick = { if (snoozeMinutes < 120) snoozeMinutes += 5 },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .testTag("alarm_snooze_plus")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase snooze time",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Text(
                text = "Time triggered: $timeText",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun OnboardingApprovalOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }

    fun updatePermissionStates() {
        hasNotificationPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
    }

    // Initial check
    LaunchedEffect(Unit) {
        updatePermissionStates()
    }

    // Dynamic update when user resumes app
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                updatePermissionStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .pointerInput(Unit) {
                detectTapGestures { }
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎯",
                        fontSize = 32.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Welcome to සිහියෙන් බලපන්!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Grant the following permissions to enjoy full screen-overlay alarm alerts and location tracking reminders.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Step 1: Notifications
                    PermissionRow(
                        title = "1. Show System Notifications",
                        description = "Posts system banners and active alarms in status drawer.",
                        isGranted = hasNotificationPermission,
                        icon = "🔔",
                        onGrantClick = {
                            val activity = context as? androidx.activity.ComponentActivity
                            if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                activity.requestPermissions(
                                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                    101
                                )
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Step 2: Location
                    PermissionRow(
                        title = "2. Real Google Location",
                        description = "Triggers checklist alerts automatically when you hit target spots.",
                        isGranted = hasLocationPermission,
                        icon = "📍",
                        onGrantClick = {
                            val activity = context as? androidx.activity.ComponentActivity
                            activity?.requestPermissions(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                ),
                                102
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Step 3: Overlay Dialog / Window
                    PermissionRow(
                        title = "3. Draw Over Other Apps",
                        description = "Enables full-screen wake alarms to cover your display instantly.",
                        isGranted = hasOverlayPermission,
                        icon = "⏰",
                        onGrantClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback standard settings intent if direct package parsing fails
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                context.startActivity(intent)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_approve_button")
                ) {
                    Text(
                        "Proceed to App",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: String,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Granted Status",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
        } else {
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "Grant",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun TodayPlannerView(
    viewModel: ChecklistViewModel,
    allItems: List<ChecklistItem>,
    todayChecklist: Checklist?,
    isTimestampToday: (Long?) -> Boolean,
    onConfigureReminder: (ChecklistItem) -> Unit,
    onDelete: (ChecklistItem) -> Unit,
    onDeactivateAlarm: (ChecklistItem) -> Unit,
    onStartHyperfocus: () -> Unit,
    onNavigateToChecklist: (Int) -> Unit,
    checklists: List<Checklist> = emptyList(),
    categories: List<Category> = emptyList(),
    isKeyboardVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    var newTaskText by remember { mutableStateOf("") }
    var shortcutItemToConfirm by remember { mutableStateOf<ChecklistItem?>(null) }
    var shortcutToChecklistIdToConfirm by remember { mutableStateOf<Int?>(null) }
    var confirmationTitle by remember { mutableStateOf("") }
    var confirmationMessage by remember { mutableStateOf("") }
    val todayItems = remember(allItems, todayChecklist) {
        if (todayChecklist == null) emptyList()
        else allItems.filter { it.checklistId == todayChecklist.id }
            .sortedWith(compareBy<ChecklistItem> { it.isCompleted }.thenBy { it.position })
    }
    
    val syncedTodayItems = remember(allItems, todayChecklist) {
        allItems.filter { it.checklistId != todayChecklist?.id && it.isAddedToToday }
            .sortedWith(compareBy<ChecklistItem> { it.isCompleted }.thenBy { it.position })
    }
    
    val checklistsScheduledForToday = remember(checklists) {
        checklists.filter { 
            viewModel.isScheduledTimeReached(it, System.currentTimeMillis())
        }.map { it.id }.toSet()
    }

    val externalTodayItems = remember(allItems, todayChecklist, checklistsScheduledForToday) {
        allItems.filter { 
            it.checklistId != todayChecklist?.id && 
            !it.isAddedToToday && 
            !it.isCompleted &&
            viewModel.isScheduledTimeReachedForItem(it, System.currentTimeMillis())
        }.sortedWith(compareBy<ChecklistItem> { it.isCompleted }.thenBy { it.position })
    }

    val totalCount = todayItems.size + externalTodayItems.size + syncedTodayItems.size
    val doneCount = todayItems.count { it.isCompleted } + externalTodayItems.count { it.isCompleted } + syncedTodayItems.count { it.isCompleted }
    val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .testTag("today_planner_view")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        if (isKeyboardVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🎯", fontSize = 20.sp, modifier = Modifier.padding(end = 6.dp))
                Text(
                    text = "Today's Focus • $doneCount/$totalCount done",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            // Large Date & Visual Today Banner (A compact, distraction-free layout styled for neurodivergent focus)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            val dateFormat = java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault())
                            Text(
                                text = dateFormat.format(java.util.Date()),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Today's Focus • $doneCount/$totalCount done",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        DynamicCalendarIcon(size = 32.dp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sleek, minimal progress bar
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )

                    if (totalCount > 0 && doneCount < totalCount) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onStartHyperfocus,
                            colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Text(
                                text = "🚀 Break Task Paralysis (Hyperfocus Mode)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (doneCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    todayItems.filter { it.isCompleted }.forEach { viewModel.deleteItem(it) }
                    syncedTodayItems.filter { it.isCompleted }.forEach { viewModel.toggleItemAddedToToday(it) }
                    // externalTodayItems should not be deleted, they are kept intact in their source checklist!
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("clear_completed_button")
            ) {
                Icon(
                     imageVector = Icons.Default.Delete,
                     contentDescription = "Clear done tasks",
                     modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("🧹 Instantly Clear Completed Tasks (${doneCount})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // List of scrolling items
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (todayItems.isEmpty() && externalTodayItems.isEmpty() && syncedTodayItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✨", fontSize = 36.sp)
                            Text(
                                text = "Your plate is empty for today!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                if (todayItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "Daily Goals",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp, top = 4.dp)
                        )
                    }

                    items(todayItems.size) { idx ->
                        val item = todayItems[idx]
                        val shortcutChecklistId = parseChecklistShortcut(item.text)
                        val callNumber = parseCallShortcut(item.text)
                        val waNumber = parseWaShortcut(item.text)

                        if (shortcutChecklistId != null) {
                            val list = checklists.find { it.id == shortcutChecklistId }
                            ChecklistShortcutRow(
                                item = item,
                                checklist = list,
                                isFirst = idx == 0,
                                isLast = idx == todayItems.lastIndex,
                                onMoveUp = { viewModel.moveItemUp(item) },
                                onMoveDown = { viewModel.moveItemDown(item) },
                                onConfigureReminder = { onConfigureReminder(item) },
                                onNavigateToChecklist = { onNavigateToChecklist(shortcutChecklistId) },
                                onDelete = { onDelete(item) },
                                onCheckChange = {
                                    shortcutItemToConfirm = item
                                    shortcutToChecklistIdToConfirm = shortcutChecklistId
                                    confirmationTitle = "Mark List as Done?"
                                    confirmationMessage = "Are you sure you want to mark this task list as completed? It will disappear from Today's planner."
                                }
                            )
                        } else if (callNumber != null) {
                            CallShortcutRow(
                                item = item,
                                callNumber = callNumber,
                                isFirst = idx == 0,
                                isLast = idx == todayItems.lastIndex,
                                onMoveUp = { viewModel.moveItemUp(item) },
                                onMoveDown = { viewModel.moveItemDown(item) },
                                onConfigureReminder = { onConfigureReminder(item) },
                                onDelete = { onDelete(item) },
                                onCheckChange = {
                                    shortcutItemToConfirm = item
                                    shortcutToChecklistIdToConfirm = null
                                    confirmationTitle = "Mark Call as Done?"
                                    confirmationMessage = "Are you sure you want to mark this phone call as completed? It will disappear from Today's planner."
                                }
                            )
                        } else if (waNumber != null) {
                            WhatsAppShortcutRow(
                                item = item,
                                waNumber = waNumber,
                                isFirst = idx == 0,
                                isLast = idx == todayItems.lastIndex,
                                onMoveUp = { viewModel.moveItemUp(item) },
                                onMoveDown = { viewModel.moveItemDown(item) },
                                onConfigureReminder = { onConfigureReminder(item) },
                                onDelete = { onDelete(item) },
                                onCheckChange = {
                                    shortcutItemToConfirm = item
                                    shortcutToChecklistIdToConfirm = null
                                    confirmationTitle = "Mark WhatsApp as Done?"
                                    confirmationMessage = "Are you sure you want to mark this WhatsApp task as completed? It will disappear from Today's planner."
                                }
                            )
                        } else {
                            ChecklistItemRow(
                                item = item,
                                isFirst = idx == 0,
                                isLast = idx == todayItems.lastIndex,
                                onMoveUp = { viewModel.moveItemUp(item) },
                                onMoveDown = { viewModel.moveItemDown(item) },
                                onConfigureReminder = { onConfigureReminder(item) },
                                onCheckChange = { viewModel.toggleItemCompletion(item) },
                                onDelete = { onDelete(item) },
                                onDeactivateAlarm = { onDeactivateAlarm(item) },
                                isTodayList = true
                            )
                        }
                    }
                }

                if (syncedTodayItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "Synced Focus Tasks from Workspace",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp, top = 10.dp)
                        )
                    }

                    items(syncedTodayItems.size) { idx ->
                        val item = syncedTodayItems[idx]
                        ChecklistItemRow(
                            item = item,
                            isFirst = idx == 0,
                            isLast = idx == syncedTodayItems.lastIndex,
                            onMoveUp = { viewModel.moveItemUp(item) },
                            onMoveDown = { viewModel.moveItemDown(item) },
                            onConfigureReminder = { onConfigureReminder(item) },
                            onCheckChange = { viewModel.toggleItemCompletion(item) },
                            onDelete = { onDelete(item) },
                            onDeactivateAlarm = { onDeactivateAlarm(item) },
                            onToggleAddedToToday = { viewModel.toggleItemAddedToToday(item) },
                            isTodayList = true
                        )
                    }
                }

                if (externalTodayItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "Scheduled Checklist Reminders",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp, top = 10.dp)
                        )
                    }

                    items(externalTodayItems.size) { idx ->
                        val item = externalTodayItems[idx]
                        ChecklistItemRow(
                            item = item,
                            isFirst = idx == 0,
                            isLast = idx == externalTodayItems.lastIndex,
                            onMoveUp = { viewModel.moveItemUp(item) },
                            onMoveDown = { viewModel.moveItemDown(item) },
                            onConfigureReminder = { onConfigureReminder(item) },
                            onCheckChange = { viewModel.toggleItemCompletion(item) },
                            onDelete = { onDelete(item) },
                            onDeactivateAlarm = { onDeactivateAlarm(item) },
                            onToggleAddedToToday = { viewModel.toggleItemAddedToToday(item) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Input field for quick today task (Now anchored at the bottom!)
        if (todayChecklist != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                CommaSuggestBox(
                    text = newTaskText,
                    onTextChange = { newTaskText = it },
                    checklists = checklists,
                    categories = categories,
                    allItems = allItems,
                    onAddItemToSpecificChecklist = { listId, taskText ->
                        viewModel.addItemToSpecificChecklist(listId, taskText, isAddedToToday = true)
                    },
                    onCreateNewListAndMove = { listName, taskText ->
                        viewModel.createChecklistWithInitialTask(listName, taskText)
                    },
                    onAddShortcutToToday = { item ->
                        viewModel.addItemToTodayAsShortcut(item)
                    },
                    onAddChecklistShortcutToToday = { checklistId ->
                        viewModel.addChecklistShortcutToToday(checklistId)
                    },
                    onAddCustomShortcut = { customText ->
                        viewModel.addItemToSpecificChecklist(todayChecklist.id, customText)
                    },
                    onDismiss = { newTaskText = "" },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-56).dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    OutlinedTextField(
                        value = newTaskText,
                        onValueChange = { newTaskText = it },
                        placeholder = { Text("Add daily focus step...", fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (newTaskText.isNotBlank()) {
                                viewModel.addItemToSpecificChecklist(todayChecklist.id, newTaskText.trim())
                                newTaskText = ""
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Today focus item",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (shortcutItemToConfirm != null) {
        AlertDialog(
            onDismissRequest = { 
                shortcutItemToConfirm = null
                shortcutToChecklistIdToConfirm = null
            },
            title = { Text(confirmationTitle) },
            text = { Text(confirmationMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        val item = shortcutItemToConfirm
                        val chkId = shortcutToChecklistIdToConfirm
                        if (item != null) {
                            if (chkId != null) {
                                viewModel.toggleChecklistShortcutDone(item, chkId)
                            } else {
                                viewModel.deleteItem(item)
                            }
                        }
                        shortcutItemToConfirm = null
                        shortcutToChecklistIdToConfirm = null
                    }
                ) {
                    Text("Check / Mark Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        shortcutItemToConfirm = null
                        shortcutToChecklistIdToConfirm = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TodoListView(
    viewModel: ChecklistViewModel,
    allItems: List<ChecklistItem>,
    todoChecklist: Checklist?,
    onConfigureReminder: (ChecklistItem) -> Unit,
    onDelete: (ChecklistItem) -> Unit,
    onDeactivateAlarm: (ChecklistItem) -> Unit,
    checklists: List<Checklist> = emptyList(),
    categories: List<Category> = emptyList(),
    modifier: Modifier = Modifier
) {
    var newTodoText by remember { mutableStateOf("") }
    val localCoroutineScope = rememberCoroutineScope()
    val todoLazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val todoItems = remember(allItems, todoChecklist) {
        if (todoChecklist == null) emptyList()
        else allItems.filter { it.checklistId == todoChecklist.id }
            .sortedWith(compareBy<ChecklistItem> { it.isCompleted }.thenBy { it.position })
    }

    val pendingCount = todoItems.count { !it.isCompleted }

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .testTag("todo_list_view")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Beautiful Card Header
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "General Todos",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "$pendingCount tasks left to tackle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                Text("✅", fontSize = 40.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Spacer(modifier = Modifier.height(14.dp))

        // Todo checklist items scrolling list
        LazyColumn(
            state = todoLazyListState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (todoItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌴", fontSize = 36.sp)
                            Text(
                                text = "All todos cleared! Grab a drink.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(todoItems.size) { idx ->
                    val item = todoItems[idx]
                    ChecklistItemRow(
                        item = item,
                        isFirst = idx == 0,
                        isLast = idx == todoItems.lastIndex,
                        onMoveUp = { viewModel.moveItemUp(item) },
                        onMoveDown = { viewModel.moveItemDown(item) },
                        onConfigureReminder = { onConfigureReminder(item) },
                        onCheckChange = { 
                            viewModel.toggleItemCompletion(item)
                        },
                        onDelete = { onDelete(item) },
                        onDeactivateAlarm = { onDeactivateAlarm(item) },
                        onToggleAddedToToday = { viewModel.toggleItemAddedToToday(item) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Add task input bar (Now anchored at the bottom!)
        if (todoChecklist != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                CommaSuggestBox(
                    text = newTodoText,
                    onTextChange = { newTodoText = it },
                    checklists = checklists,
                    categories = categories,
                    allItems = allItems,
                    onAddItemToSpecificChecklist = { listId, taskText ->
                        viewModel.addItemToSpecificChecklist(listId, taskText, isAddedToToday = true)
                    },
                    onCreateNewListAndMove = { listName, taskText ->
                        viewModel.createChecklistWithInitialTask(listName, taskText)
                    },
                    onAddShortcutToToday = { item ->
                        viewModel.addItemToTodayAsShortcut(item)
                    },
                    onAddChecklistShortcutToToday = { checklistId ->
                        viewModel.addChecklistShortcutToToday(checklistId)
                    },
                    onAddCustomShortcut = { customText ->
                        viewModel.addItemToSpecificChecklist(todoChecklist.id, customText)
                    },
                    onDismiss = { newTodoText = "" },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-56).dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    OutlinedTextField(
                        value = newTodoText,
                        onValueChange = { newTodoText = it },
                        placeholder = { Text("Add general todo task...", fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (newTodoText.isNotBlank()) {
                                viewModel.addItemToSpecificChecklist(todoChecklist.id, newTodoText.trim())
                                newTodoText = ""
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Todo Item",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectsDashboardView(
    viewModel: ChecklistViewModel,
    projectChecklists: List<Checklist>,
    allItems: List<ChecklistItem>,
    projectsCategoryId: Int?,
    onCreateProjectClick: () -> Unit,
    onSelectProject: (Int) -> Unit,
    onMoveChecklist: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .testTag("projects_dashboard_view")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Projects Hero Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Projects Hub",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "${projectChecklists.size} Active Project Planner(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Text("📁", fontSize = 40.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active Boards",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onCreateProjectClick,
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Project", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Project", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (projectChecklists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🏗️", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Construct your first project boards!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(projectChecklists.size, key = { projectChecklists[it].id }) { index ->
                    val project = projectChecklists[index]
                    val projItems = allItems.filter { it.checklistId == project.id }
                    val totalMilestones = projItems.size
                    val doneMilestones = projItems.count { it.isCompleted }
                    val progress = if (totalMilestones > 0) doneMilestones.toFloat() / totalMilestones else 0f

                    Card(
                        onClick = { onSelectProject(project.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = project.icon,
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                    Text(
                                        text = project.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "$doneMilestones/$totalMilestones done",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = { onMoveChecklist(project.id, -1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveChecklist(project.id, 1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TasksDashboardView(
    viewModel: ChecklistViewModel,
    todoChecklists: List<Checklist>,
    allItems: List<ChecklistItem>,
    todoCategoryId: Int?,
    onCreateTaskClick: () -> Unit,
    onSelectTaskList: (Int) -> Unit,
    onMoveChecklist: (Int, Int) -> Unit,
    checklists: List<Checklist>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .testTag("tasks_dashboard_view")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Tasks Hero Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Tasks Hub",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${todoChecklists.size} Active Task List(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Text("✅", fontSize = 40.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Task Lists",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onCreateTaskClick,
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Task List", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Task List", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (todoChecklists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📝", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create your first task list!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(todoChecklists.size, key = { todoChecklists[it].id }) { index ->
                    val taskList = todoChecklists[index]
                    val listItems = allItems.filter { it.checklistId == taskList.id }
                    val totalTasks = listItems.size
                    val doneTasks = listItems.count { it.isCompleted }
                    val progress = if (totalTasks > 0) doneTasks.toFloat() / totalTasks else 0f

                    Card(
                        onClick = { onSelectTaskList(taskList.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = taskList.icon,
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                    Column {
                                        Text(
                                            text = taskList.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (taskList.projectId != null) {
                                            val associatedProject = checklists.find { it.id == taskList.projectId }
                                            if (associatedProject != null) {
                                                Text(
                                                    text = "📁 Project: ${associatedProject.name}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "$doneTasks/$totalTasks done",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                    IconButton(
                                        onClick = { onMoveChecklist(taskList.id, -1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveChecklist(taskList.id, 1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChecklistsDashboardView(
    viewModel: ChecklistViewModel,
    standardChecklists: List<Checklist>,
    allItems: List<ChecklistItem>,
    selectedFilterCategoryId: Int?,
    onCreateChecklistClick: () -> Unit,
    onSelectChecklist: (Int) -> Unit,
    onMoveChecklist: (Int, Int) -> Unit,
    categories: List<Category>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .testTag("checklists_dashboard_view")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Checklists Hero Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Checklists Hub",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        val subtitleText = if (selectedFilterCategoryId != null) {
                            val activeCat = categories.find { it.id == selectedFilterCategoryId }
                            "Category: ${activeCat?.name ?: "Unknown"}"
                        } else {
                            "All Standard Checklists"
                        }
                        Text(
                            text = "${standardChecklists.size} Checklist(s) • $subtitleText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Text("📋", fontSize = 40.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active Checklists",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onCreateChecklistClick,
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Checklist", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Checklist", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (standardChecklists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📭", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create your first preparation checklist!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(standardChecklists.size, key = { standardChecklists[it].id }) { index ->
                    val checklist = standardChecklists[index]
                    val listItems = allItems.filter { it.checklistId == checklist.id }
                    val totalTasks = listItems.size
                    val doneTasks = listItems.count { it.isCompleted }
                    val progress = if (totalTasks > 0) doneTasks.toFloat() / totalTasks else 0f

                    Card(
                        onClick = { onSelectChecklist(checklist.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = checklist.icon,
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                    Column {
                                        Text(
                                            text = checklist.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val cat = categories.find { it.id == checklist.categoryId }
                                        if (cat != null) {
                                            Text(
                                                text = "Category: ${cat.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "$doneTasks/$totalTasks secured",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                    IconButton(
                                        onClick = { onMoveChecklist(checklist.id, -1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveChecklist(checklist.id, 1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
fun parseWhatsAppText(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        val len = text.length
        while (i < len) {
            val char = text[i]
            if (char == '*' || char == '_' || char == '~') {
                val closingIdx = text.indexOf(char, i + 1)
                if (closingIdx != -1 && closingIdx > i + 1) {
                    val content = text.substring(i + 1, closingIdx)
                    val style = when (char) {
                        '*' -> androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
                        '_' -> androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)
                        else -> androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                    }
                    withStyle(style) {
                        append(parseWhatsAppText(content))
                    }
                    i = closingIdx + 1
                    continue
                }
            }
            append(char)
            i++
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntrepreneurIdeaSandboxView(
    viewModel: ChecklistViewModel,
    allItems: List<ChecklistItem>,
    ideaChecklist: Checklist?,
    checklists: List<Checklist>,
    isTimestampToday: (Long?) -> Boolean,
    onDelete: (ChecklistItem) -> Unit,
    onNavigateToProject: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (ideaChecklist == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val ideaItems = remember(allItems, ideaChecklist) {
        allItems.filter { it.checklistId == ideaChecklist.id }.sortedBy { it.timestamp }
    }

    // Input States
    var ideaTitle by remember { mutableStateOf("") }
    var ideaText by remember { mutableStateOf(TextFieldValue("")) }
    var excitement by remember { mutableFloatStateOf(8f) }
    var fright by remember { mutableFloatStateOf(4f) }
    var showSuccessDopamine by remember { mutableStateOf(false) }
    var isNotesFocused by remember { mutableStateOf(false) }
    
    // Launch Project Confirmation States
    var showLaunchConfirmation by remember { mutableStateOf(false) }
    
    fun applyFormatting(formatChar: String) {
        val text = ideaText.text
        val selection = ideaText.selection
        val start = selection.start
        val end = selection.end
        if (start != end) {
            val selectedText = text.substring(start, end)
            val newText = text.substring(0, start) + formatChar + selectedText + formatChar + text.substring(end)
            val newSelectionStart = start
            val newSelectionEnd = end + formatChar.length * 2
            ideaText = TextFieldValue(
                text = newText,
                selection = TextRange(newSelectionStart, newSelectionEnd)
            )
        } else {
            val newText = text.substring(0, start) + formatChar + formatChar + text.substring(start)
            val newCursor = start + formatChar.length
            ideaText = TextFieldValue(
                text = newText,
                selection = TextRange(newCursor)
            )
        }
    }
    var launchingProjectIdea by remember { mutableStateOf<ChecklistItem?>(null) }
    var editingIdea by remember { mutableStateOf<ChecklistItem?>(null) }

    if (editingIdea != null) {
        val originalIdea = editingIdea!!
        val splitLines = remember(originalIdea.text) { originalIdea.text.split("\n", limit = 2) }
        val initialTitle = remember(splitLines) { splitLines.getOrNull(0)?.trim()?.removeSurrounding("**")?.removeSurrounding("*") ?: "" }
        val initialNotes = remember(splitLines) { splitLines.getOrNull(1)?.trim() ?: "" }

        var editedTitle by remember { mutableStateOf(initialTitle) }
        var editedNotes by remember { mutableStateOf(TextFieldValue(initialNotes)) }
        var editedExcitement by remember { mutableStateOf(originalIdea.excitementRating ?: 5) }
        var editedFright by remember { mutableStateOf(originalIdea.frightRating ?: 5) }

        fun applyEditFormatting(formatChar: String) {
            val text = editedNotes.text
            val selection = editedNotes.selection
            val start = selection.start
            val end = selection.end
            if (start != end) {
                val selectedText = text.substring(start, end)
                val newText = text.substring(0, start) + formatChar + selectedText + formatChar + text.substring(end)
                val newSelectionStart = start
                val newSelectionEnd = end + formatChar.length * 2
                editedNotes = TextFieldValue(
                    text = newText,
                    selection = TextRange(newSelectionStart, newSelectionEnd)
                )
            } else {
                val newText = text.substring(0, start) + formatChar + formatChar + text.substring(start)
                val newCursor = start + formatChar.length
                editedNotes = TextFieldValue(
                    text = newText,
                    selection = TextRange(newCursor)
                )
            }
        }

        AlertDialog(
            onDismissRequest = { editingIdea = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✏️ ", fontSize = 24.sp)
                    Text("Edit Sandbox Idea", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Idea Heading",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        placeholder = { Text("E.g., Micro-SaaS startup idea") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("edit_idea_title_input")
                    )

                    Text(
                        "Explanatory Notes (Supports Formatting)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editedNotes,
                        onValueChange = { newValue -> editedNotes = newValue },
                        placeholder = { Text("Write your explanatory thoughts and notes...") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("edit_idea_notes_input")
                    )

                    // Rich formatting helper shortcuts row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { applyEditFormatting("*") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("B", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { applyEditFormatting("_") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("I", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { applyEditFormatting("~") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("S", style = MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val text = editedNotes.text
                                val selection = editedNotes.selection
                                val start = selection.start
                                val bulletText = "• "
                                val newText = text.substring(0, start) + bulletText + text.substring(start)
                                editedNotes = TextFieldValue(text = newText, selection = TextRange(start + bulletText.length))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("• List", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val text = editedNotes.text
                                val selection = editedNotes.selection
                                val start = selection.start
                                val numberText = "1. "
                                val newText = text.substring(0, start) + numberText + text.substring(start)
                                editedNotes = TextFieldValue(text = newText, selection = TextRange(start + numberText.length))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("1. List", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Excitement Level: $editedExcitement/10", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = editedExcitement.toFloat(),
                        onValueChange = { editedExcitement = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8
                    )

                    Text("Barrier/Fright Level: $editedFright/10", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = editedFright.toFloat(),
                        onValueChange = { editedFright = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val combined = if (editedTitle.trim().isNotEmpty()) {
                            "${editedTitle.trim()}\n\n${editedNotes.text.trim()}"
                        } else {
                            editedNotes.text.trim()
                        }
                        if (combined.trim().isNotEmpty()) {
                            viewModel.updateItem(
                                originalIdea.copy(
                                    text = combined.trim(),
                                    excitementRating = editedExcitement,
                                    frightRating = editedFright
                                )
                            )
                            editingIdea = null
                        }
                    },
                    enabled = editedTitle.trim().isNotEmpty() || editedNotes.text.trim().isNotEmpty()
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingIdea = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLaunchConfirmation && launchingProjectIdea != null) {
        var enteredProjectName by remember(launchingProjectIdea) { 
            mutableStateOf(launchingProjectIdea?.text?.substringBefore("\n")?.replace(Regex("[*#~_]"), "")?.trim()?.take(40) ?: "My Startup Project") 
        }
        AlertDialog(
            onDismissRequest = {
                showLaunchConfirmation = false
                launchingProjectIdea = null
            },
            title = { Text("🚀 Launch Project Workspace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ready to launch this concept into an active Project Workspace? Put a title for your project below:")
                    OutlinedTextField(
                        value = enteredProjectName,
                        onValueChange = { enteredProjectName = it },
                        label = { Text("Project Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idea = launchingProjectIdea
                        if (idea != null && enteredProjectName.isNotBlank()) {
                            viewModel.promoteIdeaToProject(idea, enteredProjectName.trim())
                        }
                        showLaunchConfirmation = false
                        launchingProjectIdea = null
                    },
                    enabled = enteredProjectName.isNotBlank()
                ) {
                    Text("Launch Project!")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLaunchConfirmation = false
                        launchingProjectIdea = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Tab inside Sandbox: "cool_deck", "task_paralysis", "pomodoro"
    var sandboxTab by remember { mutableStateOf("cool_deck") }

    // Focus Timer States
    var secondsLeft by remember { mutableIntStateOf(1500) }
    var timerRunning by remember { mutableStateOf(false) }
    var totalTimerLength by remember { mutableIntStateOf(1500) }

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while (secondsLeft > 0) {
                kotlinx.coroutines.delay(1000L)
                secondsLeft--
            }
            timerRunning = false
        }
    }

    val lowBarrierIdeas = remember(ideaItems) {
        ideaItems.sortedBy { it.frightRating }
    }

    val progressRatio = secondsLeft.toFloat() / totalTimerLength.toFloat()
    val minutesStr = (secondsLeft / 60).toString().padStart(2, '0')
    val secondsStr = (secondsLeft % 60).toString().padStart(2, '0')

    Box(modifier = modifier.fillMaxSize()) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .testTag("ideas_sandbox_view"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Title and Slogan
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "💡 Entrepreneurial Idea Sandbox",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Got a shiny new startup idea? Dump it here immediately. Let the 48-hour cooling-off rule keep you focused!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.82f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(text = "🧠", fontSize = 36.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        // Tab Selector Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "cool_deck" to "⏳ Cooldown",
                    "task_paralysis" to "⚡ Matrix",
                    "pomodoro" to "⏰ Timer"
                ).forEach { (tabId, tabLabel) ->
                    val isSelected = sandboxTab == tabId
                    FilterChip(
                        selected = isSelected,
                        onClick = { sandboxTab = tabId },
                        label = {
                            Text(
                                text = tabLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            selectedBorderColor = MaterialTheme.colorScheme.tertiary,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Main Sandbox Layout switching via LazyColumn items
        when (sandboxTab) {
            "cool_deck" -> {
                // Quick Brain Dump Input Card (Moved to the top!)
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "⚡ Instant Spontaneous Spark Capture",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Idea Heading",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = ideaTitle,
                                onValueChange = { ideaTitle = it },
                                placeholder = { Text("E.g., Micro-SaaS to find unused domain names with AI 🚀", fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("spark_idea_heading_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Explanatory Notes (Supports B, I, S formatting, lists)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { applyFormatting("*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("B", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { applyFormatting("_") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("I", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { applyFormatting("~") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("S", style = MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val text = ideaText.text
                                        val selection = ideaText.selection
                                        val start = selection.start
                                        val bulletText = "• "
                                        val newText = text.substring(0, start) + bulletText + text.substring(start)
                                        ideaText = TextFieldValue(text = newText, selection = TextRange(start + bulletText.length))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("• List", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val text = ideaText.text
                                        val selection = ideaText.selection
                                        val start = selection.start
                                        val numberText = "1. "
                                        val newText = text.substring(0, start) + numberText + text.substring(start)
                                        ideaText = TextFieldValue(text = newText, selection = TextRange(start + numberText.length))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("1. List", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp)
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = ideaText,
                                    onValueChange = { newValue ->
                                        val textChange = newValue.text
                                        val oldText = ideaText.text
                                        
                                        val isNewlineInserted = textChange.length > oldText.length && 
                                            newValue.selection.start > 0 && 
                                            textChange[newValue.selection.start - 1] == '\n'
                                        
                                        if (isNewlineInserted) {
                                            val insertPos = newValue.selection.start
                                            val textBefore = textChange.substring(0, insertPos - 1)
                                            val textAfter = textChange.substring(insertPos)
                                            
                                            val lines = textBefore.split('\n')
                                            val lastLine = lines.lastOrNull() ?: ""
                                            
                                            val bulletRegex = Regex("""^(\s*)([•\-\*])\s*(.*)""")
                                            val bulletMatch = bulletRegex.find(lastLine)
                                            
                                            val numberRegex = Regex("""^(\s*)(\d+)\.\s*(.*)""")
                                            val numberMatch = numberRegex.find(lastLine)
                                            
                                            if (bulletMatch != null) {
                                                val indent = bulletMatch.groupValues[1]
                                                val prefix = bulletMatch.groupValues[2]
                                                val content = bulletMatch.groupValues[3].trim()
                                                
                                                if (content.isEmpty()) {
                                                    val indexPrevLineStart = textBefore.length - lastLine.length
                                                    val newText = textChange.substring(0, indexPrevLineStart) + textAfter
                                                    val newCursorPos = minOf(newText.length, indexPrevLineStart)
                                                    ideaText = TextFieldValue(
                                                        text = newText,
                                                        selection = TextRange(newCursorPos)
                                                    )
                                                } else {
                                                    val nextBullet = "$indent$prefix "
                                                    val newText = textChange.substring(0, insertPos) + nextBullet + textAfter
                                                    val newCursorPos = insertPos + nextBullet.length
                                                    ideaText = TextFieldValue(
                                                        text = newText,
                                                        selection = TextRange(newCursorPos)
                                                    )
                                                }
                                            } else if (numberMatch != null) {
                                                val indent = numberMatch.groupValues[1]
                                                val numStr = numberMatch.groupValues[2]
                                                val currentNum = numStr.toIntOrNull() ?: 1
                                                val content = numberMatch.groupValues[3].trim()
                                                
                                                if (content.isEmpty()) {
                                                    val indexPrevLineStart = textBefore.length - lastLine.length
                                                    val newText = textChange.substring(0, indexPrevLineStart) + textAfter
                                                    val newCursorPos = minOf(newText.length, indexPrevLineStart)
                                                    ideaText = TextFieldValue(
                                                        text = newText,
                                                        selection = TextRange(newCursorPos)
                                                    )
                                                } else {
                                                    val nextNumber = "$indent${currentNum + 1}. "
                                                    val newText = textChange.substring(0, insertPos) + nextNumber + textAfter
                                                    val newCursorPos = insertPos + nextNumber.length
                                                    ideaText = TextFieldValue(
                                                        text = newText,
                                                        selection = TextRange(newCursorPos)
                                                    )
                                                }
                                            } else {
                                                ideaText = newValue
                                            }
                                        } else {
                                            ideaText = newValue
                                        }
                                    },
                                    visualTransformation = WhatsAppFormattingTransformation(),
                                    placeholder = { Text("What's the breakthrough idea? Write paragraphs, paint a picture, use bullets & numbers...", fontSize = 13.sp) },
                                    singleLine = false,
                                    minLines = 4,
                                    maxLines = 5,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .onFocusChanged { isNotesFocused = it.isFocused }
                                )

                                // Real-time Rich Selection-active Context Tooltip (WhatsApp Style Format Popover)
                                if (ideaText.selection.start != ideaText.selection.end) {
                                    androidx.compose.ui.window.Popup(
                                        alignment = Alignment.TopCenter,
                                        offset = androidx.compose.ui.unit.IntOffset(0, -55)
                                    ) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                            shape = RoundedCornerShape(24.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Format Selection:",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontSize = 11.sp
                                                )
                                                
                                                // Bold Shortcut Button
                                                IconButton(
                                                    onClick = { applyFormatting("*") },
                                                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                                ) {
                                                    Text("B", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                                }
                                                
                                                // Italic Shortcut Button
                                                IconButton(
                                                    onClick = { applyFormatting("_") },
                                                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                                ) {
                                                    Text("I", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                                }
                                                
                                                // Strikethrough Shortcut Button
                                                IconButton(
                                                    onClick = { applyFormatting("~") },
                                                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                                ) {
                                                    Text("S", style = MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                                }
                                                
                                                androidx.compose.material3.VerticalDivider(
                                                    modifier = Modifier.height(18.dp),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                                )
                                                
                                                // Close / Reset Selection Cursor Button
                                                IconButton(
                                                    onClick = {
                                                        ideaText = TextFieldValue(
                                                            text = ideaText.text,
                                                            selection = TextRange(ideaText.selection.end)
                                                        )
                                                    },
                                                    modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Dismiss",
                                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "🔥 Excitement Level: ${excitement.toInt()}/10",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = excitement,
                                        onValueChange = { excitement = it },
                                        valueRange = 1f..10f,
                                        steps = 8,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "🤯 Intimidation Factor: ${fright.toInt()}/10",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = fright,
                                        onValueChange = { fright = it },
                                        valueRange = 1f..10f,
                                        steps = 8,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.error,
                                            activeTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val rawTitle = ideaTitle.trim()
                                    val rawText = ideaText.text.trim()
                                    if (rawText.isNotEmpty() || rawTitle.isNotEmpty()) {
                                        val combinedText = if (rawTitle.isNotEmpty()) {
                                            "$rawTitle\n\n$rawText"
                                        } else {
                                            rawText
                                        }
                                        viewModel.addIdeaToSandbox(
                                            text = combinedText,
                                            excitement = excitement.toInt(),
                                            fright = fright.toInt()
                                        )
                                        ideaTitle = ""
                                        ideaText = TextFieldValue("")
                                        excitement = 8f
                                        fright = 4f
                                        showSuccessDopamine = true
                                    }
                                },
                                enabled = ideaTitle.trim().isNotEmpty() || ideaText.text.trim().isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 6.dp)
                            ) {
                                Text("📥 Dump Concept", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (showSuccessDopamine) {
                    item {
                        LaunchedEffect(key1 = showSuccessDopamine) {
                            kotlinx.coroutines.delay(2500L)
                            showSuccessDopamine = false
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🎉", fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    text = "Idea safe. Dopamine micro-dose unlocked! Now get back to active work safely! 🛡️⚡",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Ideas List Title with cooling calculation
                item {
                    Text(
                        text = "Simmering Cooldown Deck (${ideaItems.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (ideaItems.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Your head is clear. No distracting ideas currently in sandbox! 🌟",
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(ideaItems.size, key = { ideaItems[it].id }) { index ->
                        val idea = ideaItems[index]
                        IdeaCard(
                            idea = idea,
                            onEdit = { editingIdea = idea },
                            onDiscard = { onDelete(idea) },
                            onConvertToProject = {
                                launchingProjectIdea = idea
                                showLaunchConfirmation = true
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            "task_paralysis" -> {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "⚡ Executive Barrier Breaker",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "When dealing with task paralysis, it is vital to start with either the absolute easiest task to build momentum ('Low Intimidation first'), or clear the high friction task ('Eat the Frog!')",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                    }
                }

                if (lowBarrierIdeas.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sandbox is empty! Add concepts to play with brain matrix categorization.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(lowBarrierIdeas.size, key = { "barrier_${lowBarrierIdeas[it].id}" }) { index ->
                        val idea = lowBarrierIdeas[index]
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (idea.frightRating >= 7) {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (idea.frightRating >= 7) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = parseWhatsAppText(idea.text),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Excitement Badge
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "🔥 Excitement: ${idea.excitementRating}/10",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }

                                    // Barrier Gauge Badge
                                    val barrierBgColor = if (idea.frightRating >= 7) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.44f)
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.44f)
                                    }
                                    val barrierOnColor = if (idea.frightRating >= 7) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = barrierBgColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (idea.frightRating >= 7) "🤯 Intimidating: ${idea.frightRating}/10" else "🟢 Easy Start: ${idea.frightRating}/10",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = barrierOnColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        launchingProjectIdea = idea
                                        showLaunchConfirmation = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = "🚀 Launch Project",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            "pomodoro" -> {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Text(
                            text = "⏱️ Dynamic Ideation Sprint",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Text(
                            text = "Dedicate brief, timed bursts for research. Stop studying and return to implementation when the sound loops ends!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(bottom = 16.dp)
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(200.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { progressRatio },
                                color = MaterialTheme.colorScheme.tertiary,
                                strokeWidth = 10.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier.fillMaxSize()
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$minutesStr:$secondsStr",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (timerRunning) "Hyper-focusing..." else "Sprint Idled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { timerRunning = !timerRunning },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (timerRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.width(120.dp)
                            ) {
                                Text(
                                    text = if (timerRunning) "⏸️ Pause" else "▶️ Focus",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    timerRunning = false
                                    secondsLeft = 1500
                                    totalTimerLength = 1500
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text("🔄 Reset", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                5 to "☕ 5m Rest",
                                15 to "⚡ 15m Sprint",
                                25 to "🧠 25m Deep"
                            ).forEach { (m, label) ->
                                TextButton(
                                    onClick = {
                                        timerRunning = false
                                        secondsLeft = m * 60
                                        totalTimerLength = m * 60
                                    }
                                ) {
                                    Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

        // Floating/Docked formatting bar: Sticky on top of Gboard/Keyboard!
        if (isNotesFocused && WindowInsets.isImeVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Bold Button
                        FilledTonalIconButton(
                            onClick = { applyFormatting("*") },
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("B", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Italic Button
                        FilledTonalIconButton(
                            onClick = { applyFormatting("_") },
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("I", fontStyle = FontStyle.Italic, fontSize = 14.sp)
                        }

                        // Strikethrough Button
                        FilledTonalIconButton(
                            onClick = { applyFormatting("~") },
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("S", textDecoration = TextDecoration.LineThrough, fontSize = 14.sp)
                        }

                        // Bullet List Button
                        FilledTonalIconButton(
                            onClick = {
                                val text = ideaText.text
                                val selection = ideaText.selection
                                val start = selection.start
                                val bulletText = "• "
                                val newText = text.substring(0, start) + bulletText + text.substring(start)
                                ideaText = TextFieldValue(text = newText, selection = TextRange(start + bulletText.length))
                            },
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("•", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        // Number List Button
                        FilledTonalIconButton(
                            onClick = {
                                val text = ideaText.text
                                val selection = ideaText.selection
                                val start = selection.start
                                val numberText = "1. "
                                val newText = text.substring(0, start) + numberText + text.substring(start)
                                ideaText = TextFieldValue(text = newText, selection = TextRange(start + numberText.length))
                            },
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("1.", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Done/Dismiss Keyboard Button
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done formatting",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IdeaCard(
    idea: ChecklistItem,
    onEdit: () -> Unit,
    onDiscard: () -> Unit,
    onConvertToProject: () -> Unit
) {
    val durationPassedMs = System.currentTimeMillis() - idea.timestamp
    val durationLeftHours = maxOf(0, 48 - (durationPassedMs / 3600000))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val lines = idea.text.split("\n", limit = 2)
                    val rawTitle = lines.getOrNull(0)?.trim() ?: ""
                    val title = rawTitle.removeSurrounding("**").removeSurrounding("*").trim()
                    val notes = lines.getOrNull(1)?.trim() ?: ""

                    if (title.isNotEmpty()) {
                        Text(
                            text = parseWhatsAppText(title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = parseWhatsAppText(notes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🔥 ${idea.excitementRating}/10 Excitement",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "🤯 ${idea.frightRating}/10 Barrier",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp).testTag("edit_idea_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit idea",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onDiscard,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Discard idea",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (durationLeftHours > 0) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (durationLeftHours > 0) "🛡️ Cooling Off: $durationLeftHours hours left. Let the hype calm down!" else "✅ Cool-off over! You are ready to execute or promote this!",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                    color = if (durationLeftHours > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onConvertToProject,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(32.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "🚀 Launch Project",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun getTaskLinesClean(text: String): Pair<String, String?> {
    val matchResults = Regex("[\\(\\[\\{]([^\\(\\)\\[\\]\\{\\}]+)[\\)\\]\\}]").find(text)
    if (matchResults != null) {
        val detailText = matchResults.groupValues[1].trim()
        val mainText = text.replace(matchResults.value, "").trim()
        val cleanedMainText = mainText.replace(Regex("\\s+"), " ").trim()
        if (cleanedMainText.isNotEmpty()) {
            return Pair(cleanedMainText, detailText)
        }
    }
    return Pair(text, null)
}

@Composable
fun HyperfocusModeDialog(
    isActive: Boolean,
    onDismiss: () -> Unit,
    items: List<ChecklistItem>,
    onToggleCompletion: (ChecklistItem) -> Unit
) {
    if (!isActive) return

    val incompleteItems = remember(items) { items.filter { !it.isCompleted } }
    var currentIndex by remember { mutableIntStateOf(0) }

    val activeItem = if (incompleteItems.isNotEmpty()) {
        val safeIndex = currentIndex.coerceIn(0, incompleteItems.lastIndex)
        incompleteItems[safeIndex]
    } else null

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🚀", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HYPER-FOCUS ZONE",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Exit Hyper-focus")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeItem != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "RIGHT NOW, FOCUS ONLY ON:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val cleanTextDetails = getTaskLinesClean(activeItem.text)
                                    Text(
                                        text = cleanTextDetails.first,
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            lineHeight = 44.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (cleanTextDetails.second != null) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = cleanTextDetails.second!!,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }

                                    if (activeItem.isIdea) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 16.dp)
                                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "💡 Entrepreneurial Concept",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    onToggleCompletion(activeItem)
                                    if (currentIndex < incompleteItems.lastIndex) {
                                    } else if (currentIndex > 0) {
                                        currentIndex--
                                    }
                                },
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .size(80.dp)
                                    .shadow(elevation = 8.dp, shape = CircleShape)
                            ) {
                                Text("✔️", fontSize = 28.sp)
                            }

                            Text(
                                text = "COMPLETE TASK FOR DOPAMINE!",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🎉🏆🚀", fontSize = 64.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "ALL CLEAR! AMAZING WORK!",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "You smashed your executive dysfunction. Your list has 0 incomplete items!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Return to Workspace", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (incompleteItems.size > 1) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (currentIndex > 0) currentIndex-- },
                                enabled = currentIndex > 0
                            ) {
                                Text("👈", fontSize = 24.sp)
                            }

                            Text(
                                text = "Item ${currentIndex + 1} of ${incompleteItems.size}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            IconButton(
                                onClick = { if (currentIndex < incompleteItems.lastIndex) currentIndex++ },
                                enabled = currentIndex < incompleteItems.lastIndex
                            ) {
                                Text("👉", fontSize = 24.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Focus Reminder: Multi-tasking is an illusion. Do one thing, then move on.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {}
                }
            }
        }
    }
}

@Composable
fun DynamicCalendarIcon(
    modifier: Modifier = Modifier,
    date: Date = Date(),
    size: androidx.compose.ui.unit.Dp = 32.dp
) {
    val calendar = Calendar.getInstance().apply { time = date }
    val monthStr = SimpleDateFormat("MMM", Locale.getDefault()).format(date).uppercase()
    val dayStr = calendar.get(Calendar.DAY_OF_MONTH).toString()

    Card(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size * 0.2f),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Red header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .background(Color(0xFFE53935)), // Standard Material Red
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = monthStr,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.22f).sp,
                        lineHeight = (size.value * 0.24f).sp
                    ),
                    maxLines = 1
                )
            }
            // Date body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f)
                    .background(Color(0xFFF9F9F9)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayStr,
                    color = Color(0xFF222222),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.45f).sp,
                        lineHeight = (size.value * 0.48f).sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

fun parseWhatsAppTextKeepMarkers(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        val len = text.length
        while (i < len) {
            val char = text[i]
            if (char == '*' || char == '_' || char == '~') {
                val closingIdx = text.indexOf(char, i + 1)
                if (closingIdx != -1 && closingIdx > i + 1) {
                    append(char)
                    val content = text.substring(i + 1, closingIdx)
                    val style = when (char) {
                        '*' -> androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
                        '_' -> androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)
                        else -> androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                    }
                    withStyle(style) {
                        append(parseWhatsAppTextKeepMarkers(content))
                    }
                    append(char)
                    i = closingIdx + 1
                    continue
                }
            }
            append(char)
            i++
        }
    }
}

class WhatsAppFormattingTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val originalText = text.text
        val transformed = parseWhatsAppTextKeepMarkers(originalText)
        return TransformedText(transformed, OffsetMapping.Identity)
    }
}

@Composable
fun CommaSuggestBox(
    text: String,
    onTextChange: (String) -> Unit,
    checklists: List<com.example.data.Checklist>,
    categories: List<com.example.data.Category>,
    allItems: List<com.example.data.ChecklistItem>,
    onAddItemToSpecificChecklist: (Int, String) -> Unit,
    onCreateNewListAndMove: (String, String) -> Unit, // (listName, taskText)
    onAddShortcutToToday: (com.example.data.ChecklistItem) -> Unit,
    onAddChecklistShortcutToToday: (Int) -> Unit,
    onAddCustomShortcut: (String) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCall = text.startsWith("call ", ignoreCase = true)
    val isMsg = text.startsWith("msg ", ignoreCase = true)
    if (!text.contains(",") && !isCall && !isMsg) return

    if (isCall || isMsg) {
        val context = LocalContext.current
        var hasReadContactsPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasReadContactsPermission = isGranted
        }

        val query = remember(text, isCall) {
            if (isCall) {
                if (text.length > 5) text.substring(5).trim() else ""
            } else {
                if (text.length > 4) text.substring(4).trim() else ""
            }
        }

        var contactsList by remember { mutableStateOf<List<ContactInfo>>(emptyList()) }
        LaunchedEffect(query, hasReadContactsPermission) {
            if (hasReadContactsPermission) {
                withContext(Dispatchers.IO) {
                    val queried = getDeviceContacts(context, query)
                    withContext(Dispatchers.Main) {
                        contactsList = queried
                    }
                }
            } else {
                contactsList = emptyList()
            }
        }

        val dummyContacts = remember {
            listOf(
                ContactInfo("Mom", "+1234567890"),
                ContactInfo("Dad", "+1987654321"),
                ContactInfo("Alice (Work)", "+1122334455"),
                ContactInfo("Bob (Friend)", "+1555123456"),
                ContactInfo("WhatsApp Business Support", "+12345550199")
            )
        }

        val filteredDummyContacts = remember(query) {
            if (query.isEmpty()) {
                dummyContacts
            } else {
                dummyContacts.filter {
                    it.name.contains(query, ignoreCase = true) || it.phoneNumber.contains(query)
                }
            }
        }

        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = modifier
                .fillMaxWidth()
                .zIndex(100f)
                .testTag("contacts_suggest_box")
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = if (isCall) "📞 Call Shortcut Assistant" else "💬 WhatsApp Business Assistant",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Permission Prompt if not granted
                if (!hasReadContactsPermission) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    ) {
                        Text("🔍", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text(
                                text = "Enable Contacts Integration",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = "Tap here to search live device contacts. Fallback sample list is shown below.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    // Direct Number option (if query is present)
                    if (query.isNotBlank()) {
                        item {
                            val displayText = if (isCall) "Add custom call shortcut: \"$query\"" else "Add WA Business shortcut: \"$query\""
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val prefix = if (isCall) "[CALL_SHORTCUT:$query]" else "[WA_SHORTCUT:$query]"
                                        onAddCustomShortcut("$prefix $query")
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("➕", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                Column {
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = "Create quick action button using entered text",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    // Device Contacts
                    if (contactsList.isNotEmpty()) {
                        item {
                            Text(
                                text = "MATCHED DEVICE CONTACTS:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        items(contactsList.size) { idx ->
                            val contact = contactsList[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val prefix = if (isCall) "[CALL_SHORTCUT:${contact.phoneNumber}]" else "[WA_SHORTCUT:${contact.phoneNumber}]"
                                        onAddCustomShortcut("$prefix ${contact.name}")
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isCall) "📞" else "💬", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                Column {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "${contact.phoneNumber} • Tap to pin shortcut",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    // Fallback / Sample contacts if device contact result is empty
                    if (contactsList.isEmpty()) {
                        item {
                            Text(
                                text = if (hasReadContactsPermission) "SAMPLE RECENT CONTACTS:" else "LOCAL FALLBACK RECENT CONTACTS:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        items(filteredDummyContacts.size) { idx ->
                            val contact = filteredDummyContacts[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val prefix = if (isCall) "[CALL_SHORTCUT:${contact.phoneNumber}]" else "[WA_SHORTCUT:${contact.phoneNumber}]"
                                        onAddCustomShortcut("$prefix ${contact.name}")
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isCall) "👤" else "🟢", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                Column {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "${contact.phoneNumber} • Recent item",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // CODE FOR TRADITIONAL COMMA SUGGESTIONS
        val commaIndex = text.indexOf(",")
        val taskText = text.substring(0, commaIndex).trim()
        val suggestionQuery = text.substring(commaIndex + 1).trim()

        val filteredChecklists = remember(checklists, suggestionQuery) {
            val nonTemplates = checklists.filter { !it.isTemplate }
            if (suggestionQuery.isEmpty()) {
                nonTemplates
            } else {
                nonTemplates.filter { it.name.contains(suggestionQuery, ignoreCase = true) }
            }
        }

        val filteredTasks = remember(allItems, checklists, suggestionQuery) {
            val candidates = allItems.filter { !it.isCompleted }
            if (suggestionQuery.isEmpty()) {
                candidates
            } else {
                candidates.filter { it.text.contains(suggestionQuery, ignoreCase = true) }
            }
        }

        val activeTasksInChecklist = remember(allItems) {
            allItems.filter { !it.isCompleted }.groupBy { it.checklistId }
        }

        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = modifier
                .fillMaxWidth()
                .zIndex(100f)
                .testTag("comma_suggest_box")
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = if (taskText.isNotEmpty()) "💡 Dynamic Comma Suggestions (Routing \"$taskText\")" else "💡 Search shortcuts to add to Today",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    if (taskText.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newListName = suggestionQuery.ifBlank { "New List" }
                                        onCreateNewListAndMove(newListName, taskText)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("➕", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                Column {
                                    Text(
                                        text = "Create list \"${suggestionQuery.ifBlank { "New List" }}\"",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = "Adds task to \"${suggestionQuery.ifBlank { "New List" }}\" & places shortcut in Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    if (filteredChecklists.isNotEmpty()) {
                        item {
                            Text(
                                text = if (taskText.isNotEmpty()) "MOVE TASK & PIN CHECKLIST SHORCUT:" else "PIN CHECKLIST TO DAILY GOALS:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        items(filteredChecklists.size) { index ->
                            val list = filteredChecklists[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (taskText.isNotEmpty()) {
                                            onAddItemToSpecificChecklist(list.id, taskText)
                                            onAddChecklistShortcutToToday(list.id)
                                        } else {
                                            onAddChecklistShortcutToToday(list.id)
                                        }
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(list.icon.ifBlank { "📋" }, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                Column {
                                    Text(
                                        text = list.name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    )
                                    val categoryName = categories.find { it.id == list.categoryId }?.name ?: "Personal"
                                    Text(
                                        text = if (taskText.isNotEmpty()) {
                                            "Move task to \"${list.name}\" & add checklist navigation shortcut to Today"
                                        } else {
                                            "Pin checklist \"${list.name}\" shortcut to Daily Goals ($categoryName)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    if (filteredTasks.isNotEmpty()) {
                        item {
                            Text(
                                text = "CHOOSE EXISTING TASK AS TODAY SHORTCUT:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        items(filteredTasks.size) { index ->
                            val item = filteredTasks[index]
                            val parentList = checklists.find { it.id == item.checklistId }
                            val parentListName = parentList?.name ?: "Task List"
                            val parentIcon = parentList?.icon?.ifBlank { "📋" } ?: "📋"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddShortcutToToday(item)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(parentIcon, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                Column {
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "In list \"$parentListName\" • Add shortcut to Today Daily Goals",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun parseChecklistShortcut(text: String): Int? {
    if (text.startsWith("[CL_SHORTCUT:")) {
        val colonIndex = text.indexOf(":")
        val bracketIndex = text.indexOf("]")
        if (colonIndex != -1 && bracketIndex != -1 && bracketIndex > colonIndex) {
            return text.substring(colonIndex + 1, bracketIndex).toIntOrNull()
        }
    }
    return null
}

fun cleanShortcutText(text: String): String {
    if (text.startsWith("[CL_SHORTCUT:")) {
        val bracketIndex = text.indexOf("]")
        if (bracketIndex != -1) {
            return text.substring(bracketIndex + 1).trim()
        }
    }
    return text
}

fun normalizeWhatsAppNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.startsWith("0")) {
        "94" + digits.substring(1)
    } else if (digits.length == 9 && digits.startsWith("7")) {
        "94" + digits
    } else {
        digits
    }
}

fun formatDisplayWhatsAppNumber(number: String): String {
    val norm = normalizeWhatsAppNumber(number)
    return if (norm.startsWith("94") && norm.length == 11) {
        "+" + norm
    } else {
        number
    }
}

@Composable
fun ChecklistShortcutRow(
    item: ChecklistItem,
    checklist: Checklist?,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onConfigureReminder: () -> Unit,
    onNavigateToChecklist: () -> Unit,
    onDelete: () -> Unit,
    onCheckChange: () -> Unit
) {
    val cleanName = checklist?.name ?: cleanShortcutText(item.text)
    val cleanIcon = checklist?.icon?.ifBlank { "📋" } ?: "📋"
    var showActions by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("checklist_shortcut_${checklist?.id ?: 0}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            // Checkbox/Done Tick Action Region
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCheckChange() }
                    .testTag("shortcut_checkbox_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.ui.graphics.Color.Transparent)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Outlined representation of an active, tickable bubble
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
            // Yellow Action Region (Icon/Emoji Button)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .clickable { onNavigateToChecklist() }
                    .testTag("shortcut_action_icon_${item.id}")
            ) {
                Text(
                    text = cleanIcon,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and Navigation Hint (Double clickable to toggle action options)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                showActions = !showActions
                            }
                        )
                    }
            ) {
                Text(
                    text = cleanName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Navigate",
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Tap to open list",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    )
                }
            }

            // Default actions of Right Side Region (Inline when showActions is triggered)
            if (showActions) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Edit Shortcut Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onConfigureReminder() }
                            .testTag("edit_checklist_shortcut_button_${item.id}")
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit shortcut",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Move Up button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .then(
                                if (!isFirst) Modifier.clickable { onMoveUp() }
                                else Modifier
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Move shortcut up",
                            tint = if (isFirst) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp).rotate(90f)
                        )
                    }

                    // Move Down button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .then(
                                if (!isLast) Modifier.clickable { onMoveDown() }
                                else Modifier
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Move shortcut down",
                            tint = if (isLast) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp).rotate(270f)
                        )
                    }
                }
            }
        }
    }
}

fun parseCallShortcut(text: String): String? {
    if (text.startsWith("[CALL_SHORTCUT:")) {
        val colonIndex = text.indexOf(":")
        val bracketIndex = text.indexOf("]")
        if (colonIndex != -1 && bracketIndex != -1 && bracketIndex > colonIndex) {
            return text.substring(colonIndex + 1, bracketIndex)
        }
    }
    return null
}

fun cleanCallShortcutText(text: String): String {
    if (text.startsWith("[CALL_SHORTCUT:")) {
        val bracketIndex = text.indexOf("]")
        if (bracketIndex != -1) {
            return text.substring(bracketIndex + 1).trim()
        }
    }
    return text
}

fun parseWaShortcut(text: String): String? {
    if (text.startsWith("[WA_SHORTCUT:")) {
        val colonIndex = text.indexOf(":")
        val bracketIndex = text.indexOf("]")
        if (colonIndex != -1 && bracketIndex != -1 && bracketIndex > colonIndex) {
            return text.substring(colonIndex + 1, bracketIndex)
        }
    }
    return null
}

fun cleanWaShortcutText(text: String): String {
    if (text.startsWith("[WA_SHORTCUT:")) {
        val bracketIndex = text.indexOf("]")
        if (bracketIndex != -1) {
            return text.substring(bracketIndex + 1).trim()
        }
    }
    return text
}

@Composable
fun CallShortcutRow(
    item: ChecklistItem,
    callNumber: String,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onConfigureReminder: () -> Unit,
    onDelete: () -> Unit,
    onCheckChange: () -> Unit
) {
    val cleanName = cleanCallShortcutText(item.text)
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("call_shortcut_${item.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            // Checkbox/Done Tick Action Region
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCheckChange() }
                    .testTag("shortcut_checkbox_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.ui.graphics.Color.Transparent)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Outlined representation of an active, tickable bubble
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Yellow Action Region (Call Button Icon)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                    .clickable {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$callNumber")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                    .testTag("shortcut_call_icon_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Place Call",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Body text / double clickable to toggle actions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                showActions = !showActions
                            }
                        )
                    }
            ) {
                Text(
                    text = cleanName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "📞 Phone Call • $callNumber",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // Right Region having Default Actions / Custom Red Option Buttons
            if (showActions) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Edit Shortcut Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onConfigureReminder() }
                            .testTag("edit_call_shortcut_button_${item.id}")
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit call shortcut",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Move Up button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .then(
                                if (!isFirst) Modifier.clickable { onMoveUp() }
                                else Modifier
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Move call shortcut up",
                            tint = if (isFirst) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp).rotate(90f)
                        )
                    }

                    // Move Down button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .then(
                                if (!isLast) Modifier.clickable { onMoveDown() }
                                else Modifier
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Move call shortcut down",
                            tint = if (isLast) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp).rotate(270f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsAppShortcutRow(
    item: ChecklistItem,
    waNumber: String,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onConfigureReminder: () -> Unit,
    onDelete: () -> Unit,
    onCheckChange: () -> Unit
) {
    val cleanName = cleanWaShortcutText(item.text)
    val displayWaNumber = formatDisplayWhatsAppNumber(waNumber)
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F5E9).copy(alpha = 0.8f)
        ),
        border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wa_shortcut_${item.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            // Checkbox/Done Tick Action Region
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCheckChange() }
                    .testTag("shortcut_checkbox_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.ui.graphics.Color.Transparent)
                        .border(
                            width = 2.dp,
                            color = Color(0xFF4CAF50),
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Outlined representation of an active, tickable bubble
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Yellow Action Region (WhatsApp Chat Button Icon)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.15f), CircleShape)
                    .clickable {
                        val normalized = normalizeWhatsAppNumber(waNumber)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://wa.me/$normalized")
                        }
                        intent.setPackage("com.whatsapp.w4b")
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            intent.setPackage("com.whatsapp")
                            try {
                                context.startActivity(intent)
                            } catch (e2: Exception) {
                                intent.setPackage(null)
                                try {
                                    context.startActivity(intent)
                                } catch (e3: Exception) {
                                    // Ignore
                                }
                            }
                        }
                    }
                    .testTag("shortcut_wa_icon_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "WhatsApp Business Chat",
                    tint = Color(0xFF388E3C),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Body text / double clickable to toggle actions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                showActions = !showActions
                            }
                        )
                    }
            ) {
                Text(
                    text = cleanName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "💬 WA Business • $displayWaNumber",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // Right Region having Default Actions / Custom Red Option Buttons
            if (showActions) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Edit Shortcut Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onConfigureReminder() }
                            .testTag("edit_wa_shortcut_button_${item.id}")
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit WA shortcut",
                            tint = Color(0xFF388E3C),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Move Up button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .then(
                                if (!isFirst) Modifier.clickable { onMoveUp() }
                                else Modifier
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Move WA shortcut up",
                            tint = if (isFirst) {
                                Color(0xFF388E3C).copy(alpha = 0.2f)
                            } else {
                                Color(0xFF388E3C)
                            },
                            modifier = Modifier.size(14.dp).rotate(90f)
                        )
                    }

                    // Move Down button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .then(
                                if (!isLast) Modifier.clickable { onMoveDown() }
                                else Modifier
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Move WA shortcut down",
                            tint = if (isLast) {
                                Color(0xFF388E3C).copy(alpha = 0.2f)
                            } else {
                                Color(0xFF388E3C)
                            },
                            modifier = Modifier.size(14.dp).rotate(270f)
                        )
                    }
                }
            } else {
                // No secondary actions needed outside edit mode since checkbox triggers confirmation!
            }
        }
    }
}

data class ContactInfo(val name: String, val phoneNumber: String)

fun getDeviceContacts(context: android.content.Context, query: String): List<ContactInfo> {
    val contactsList = mutableListOf<ContactInfo>()
    val contentResolver = context.contentResolver
    
    if (androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }

    val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val projection = arrayOf(
        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    
    val selection = if (query.isNotBlank()) {
        "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
    } else {
        null
    }
    val selectionArgs = if (query.isNotBlank()) {
        arrayOf("%$query%")
    } else {
        null
    }
    
    val sortOrder = "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
    
    try {
        contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            while (cursor.moveToNext()) {
                if (nameIndex != -1 && numberIndex != -1) {
                    val name = cursor.getString(nameIndex) ?: ""
                    val number = cursor.getString(numberIndex) ?: ""
                    if (name.isNotEmpty() && number.isNotEmpty() && !contactsList.any { it.name == name && it.phoneNumber == number }) {
                        contactsList.add(ContactInfo(name, number))
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return contactsList
}

@Composable
fun CompletedTasksView(
    viewModel: ChecklistViewModel,
    allItems: List<ChecklistItem>,
    checklists: List<Checklist>,
    onDelete: (ChecklistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val completedItems = remember(allItems) {
        allItems.filter { it.isCompleted }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .testTag("completed_tasks_view")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Card Header
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🏆 Completed Tasks Log",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "${completedItems.size} checklist points accomplished so far!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.82f)
                    )
                }
                Text("🏆", fontSize = 40.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Scrollable list of completed tasks
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (completedItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🐾", fontSize = 36.sp)
                            Text(
                                text = "No completed tasks yet. Finish some and watch them appear here!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
            } else {
                items(completedItems.size) { idx ->
                    val item = completedItems[idx]
                    val listName = checklists.find { it.id == item.checklistId }?.name ?: "Unknown Checklist"
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Checkbox to uncheck/undo
                            androidx.compose.material3.Checkbox(
                                checked = true,
                                onCheckedChange = { 
                                    viewModel.toggleItemCompletion(item)
                                },
                                modifier = Modifier.testTag("undo_completed_${item.id}")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "From checklist: $listName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = { onDelete(item) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Item Permanently",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardDatePickerDialog(
    initialDateMillis: Long?,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis ?: System.currentTimeMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                    onDismiss()
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                    onDismiss()
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                "Select 24-Hour Time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
        }
    )
}

