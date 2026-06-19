package com.example.data
import androidx.room.*
@Entity(tableName = "checklist_items")
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val checklistId: Int,
    val text: String,
    val isCompleted: Boolean = false,
    val position: Int = 0,
    val dueDate: Long? = null,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isAddedToToday: Boolean = false
)
