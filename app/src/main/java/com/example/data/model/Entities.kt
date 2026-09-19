package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an installed app that the user has selected or configured.
 */
@Entity(tableName = "muted_apps")
data class MutedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isMuted: Boolean = true,
    val isAlwaysAllowed: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis()
)

/**
 * User-created organizational category (e.g. Shopping, Social, Games).
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdTimestamp: Long = System.currentTimeMillis()
)

/**
 * Mapping between a Category and an App Package.
 */
@Entity(
    tableName = "category_app_cross_ref",
    primaryKeys = ["categoryId", "packageName"],
    indices = [Index(value = ["packageName"])]
)
data class CategoryAppCrossRef(
    val categoryId: Long,
    val packageName: String
)

/**
 * Log of a notification intercepted and muted while Focus Mode was ON.
 */
@Entity(
    tableName = "muted_notification_records",
    indices = [Index(value = ["dateString"]), Index(value = ["timestamp"])]
)
data class MutedNotificationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val dateString: String // YYYY-MM-DD
)

/**
 * Global persistent state for Focus Mode.
 */
@Entity(tableName = "focus_state")
data class FocusStateEntity(
    @PrimaryKey val id: Int = 1,
    val isFocusModeOn: Boolean = false,
    val isTemporary: Boolean = false,
    val temporaryDurationMinutes: Int = 0,
    val temporaryExpiryTimestamp: Long = 0L,
    val activatedTimestamp: Long = 0L
)

/**
 * App Settings.
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val suppressVibration: Boolean = true,
    val showShadeIndicator: Boolean = true,
    val showSystemApps: Boolean = false
)
