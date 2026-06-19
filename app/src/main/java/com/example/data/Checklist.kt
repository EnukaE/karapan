package com.example.data
import androidx.room.*
@Entity(tableName = "checklists")
data class Checklist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val icon: String = "📝",
    val categoryId: Int? = null,
    val dueDate: Long? = null,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isTemplate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
