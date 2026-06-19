package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = Checklist::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["checklistId"])]
)
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
    val timestamp: Long = System.currentTimeMillis(),
    val isAddedToToday: Boolean = false,
    val isIdea: Boolean = false,
    val excitementRating: Int = 0,
    val frightRating: Int = 0,
    val coolingOffStartedAt: Long? = null,
    val isReminderEnabled: Boolean = false,
    val isAllDay: Boolean = true,
    val reminderTime: String? = null,
    val repeatInterval: String? = "none"
)
