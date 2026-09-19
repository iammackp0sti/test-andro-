package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.AppSettingsEntity
import com.example.data.model.CategoryAppCrossRef
import com.example.data.model.CategoryEntity
import com.example.data.model.FocusStateEntity
import com.example.data.model.MutedAppEntity
import com.example.data.model.MutedNotificationRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMuterDao {

    // --- Muted & Configured Apps ---
    @Query("SELECT * FROM muted_apps")
    fun getAllConfiguredApps(): Flow<List<MutedAppEntity>>

    @Query("SELECT * FROM muted_apps WHERE isMuted = 1")
    fun getAllMutedApps(): Flow<List<MutedAppEntity>>

    @Query("SELECT packageName FROM muted_apps WHERE isMuted = 1")
    fun getMutedPackageNamesFlow(): Flow<List<String>>

    @Query("SELECT packageName FROM muted_apps WHERE isMuted = 1")
    suspend fun getMutedPackageNamesSync(): List<String>

    @Query("SELECT packageName FROM muted_apps WHERE isAlwaysAllowed = 1")
    fun getAlwaysAllowedPackageNamesFlow(): Flow<List<String>>

    @Query("SELECT packageName FROM muted_apps WHERE isAlwaysAllowed = 1")
    suspend fun getAlwaysAllowedPackageNamesSync(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: MutedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApps(apps: List<MutedAppEntity>)

    @Query("UPDATE muted_apps SET isMuted = :isMuted WHERE packageName = :packageName")
    suspend fun setAppMuted(packageName: String, isMuted: Boolean)

    @Query("UPDATE muted_apps SET isAlwaysAllowed = :isAlwaysAllowed WHERE packageName = :packageName")
    suspend fun setAppAlwaysAllowed(packageName: String, isAlwaysAllowed: Boolean)

    @Query("DELETE FROM muted_apps WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)

    @Query("SELECT COUNT(*) FROM muted_apps WHERE isMuted = 1 AND isAlwaysAllowed = 0")
    fun getMutedAppsCountFlow(): Flow<Int>

    // --- Categories ---
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :newName WHERE id = :categoryId")
    suspend fun updateCategoryName(categoryId: Long, newName: String)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: Long)

    @Query("DELETE FROM category_app_cross_ref WHERE categoryId = :categoryId")
    suspend fun deleteCategoryAppMappings(categoryId: Long)

    @Transaction
    suspend fun deleteCategoryAndMappings(categoryId: Long) {
        deleteCategoryAppMappings(categoryId)
        deleteCategory(categoryId)
    }

    // --- Category App Cross References ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAppToCategory(crossRef: CategoryAppCrossRef)

    @Query("DELETE FROM category_app_cross_ref WHERE categoryId = :categoryId AND packageName = :packageName")
    suspend fun removeAppFromCategory(categoryId: Long, packageName: String)

    @Query("SELECT * FROM category_app_cross_ref")
    fun getAllCategoryAppCrossRefs(): Flow<List<CategoryAppCrossRef>>

    @Query("SELECT packageName FROM category_app_cross_ref WHERE categoryId = :categoryId")
    fun getAppsForCategory(categoryId: Long): Flow<List<String>>

    @Query("SELECT packageName FROM category_app_cross_ref WHERE categoryId = :categoryId")
    suspend fun getAppsForCategorySync(categoryId: Long): List<String>

    // --- Notification Records & History ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationRecord(record: MutedNotificationRecord)

    @Query("SELECT * FROM muted_notification_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentNotificationRecords(limit: Int = 100): Flow<List<MutedNotificationRecord>>

    @Query("SELECT COUNT(*) FROM muted_notification_records WHERE dateString = :dateString")
    fun getMutedCountForDate(dateString: String): Flow<Int>

    @Query("DELETE FROM muted_notification_records")
    suspend fun clearAllNotificationRecords()

    // --- Focus Mode State ---
    @Query("SELECT * FROM focus_state WHERE id = 1 LIMIT 1")
    fun getFocusState(): Flow<FocusStateEntity?>

    @Query("SELECT * FROM focus_state WHERE id = 1 LIMIT 1")
    suspend fun getFocusStateSync(): FocusStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFocusState(state: FocusStateEntity)

    // --- App Settings ---
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AppSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: AppSettingsEntity)
}
