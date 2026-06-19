package com.example.ui
import androidx.lifecycle.*
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.Context

class ChecklistViewModel(private val repository: ChecklistRepository, private val context: Context) : ViewModel() {
    val checklists = repository.allChecklists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allItems = repository.allItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selectedChecklistId = MutableStateFlow<Int?>(null)
    val currentItems = selectedChecklistId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getItemsForChecklist(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createChecklist(name: String, icon: String, categoryId: Int?, dueDate: Long?, isTemplate: Boolean, latitude: Double? = null, longitude: Double? = null) {
        viewModelScope.launch { repository.insertChecklist(Checklist(name = name, icon = icon, categoryId = categoryId, dueDate = dueDate, latitude = latitude, longitude = longitude)) }
    }

    fun updateItemCompletion(item: ChecklistItem, completed: Boolean) {
        viewModelScope.launch { repository.updateItemCompletion(item.id, completed) }
    }

    fun deleteChecklist(cl: Checklist) { viewModelScope.launch { repository.deleteChecklist(cl) } }
}

class ChecklistViewModelFactory(private val repository: ChecklistRepository, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ChecklistViewModel(repository, context) as T
}
