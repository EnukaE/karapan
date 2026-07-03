package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    // --- Category operations ---
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    // --- Checklist operations ---
    @Query("SELECT * FROM checklists ORDER BY createdAt DESC")
    fun getAllChecklists(): Flow<List<Checklist>>

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getChecklistByIdDirect(id: Int): Checklist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklist(checklist: Checklist): Long

    @Update
    suspend fun updateChecklist(checklist: Checklist)

    @Query("UPDATE checklists SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun updateChecklistsCategoryId(oldCategoryId: Int, newCategoryId: Int)

    @Delete
    suspend fun deleteChecklist(checklist: Checklist)

    // --- Item operations ---
    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY position ASC, id ASC")
    fun getItemsForChecklist(checklistId: Int): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY position ASC, id ASC")
    suspend fun getItemsForChecklistDirect(checklistId: Int): List<ChecklistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ChecklistItem): Long

    @Update
    suspend fun updateItem(item: ChecklistItem)

    @Update
    suspend fun updateItems(items: List<ChecklistItem>)

    @Delete
    suspend fun deleteItem(item: ChecklistItem)

    @Delete
    suspend fun deleteItems(items: List<ChecklistItem>)

    @Query("UPDATE checklist_items SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateItemCompletion(id: Int, isCompleted: Boolean)

    @Query("UPDATE checklist_items SET isCompleted = 0 WHERE checklistId = :checklistId")
    suspend fun resetChecklistCompletion(checklistId: Int)

    @Query("DELETE FROM checklist_items WHERE isCompleted = 1")
    suspend fun deleteCompletedItems()

    @Query("SELECT * FROM checklist_items")
    fun getAllItems(): Flow<List<ChecklistItem>>

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM checklists")
    suspend fun deleteAllChecklists()

    @Query("DELETE FROM checklist_items")
    suspend fun deleteAllChecklistItems()
}
