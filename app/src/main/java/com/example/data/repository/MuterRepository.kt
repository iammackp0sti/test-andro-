package com.example.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.db.AppMuterDao
import com.example.data.model.AppInfo
import com.example.data.model.AppSettingsEntity
import com.example.data.model.CategoryAppCrossRef
import com.example.data.model.CategoryEntity
import com.example.data.model.CategoryWithCount
import com.example.data.model.FocusStateEntity
import com.example.data.model.MutedAppEntity
import com.example.data.model.MutedNotificationRecord
import com.example.receiver.BootAndAlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MuterRepository(
    private val dao: AppMuterDao,
    private val context: Context
) {
    private val packageManager: PackageManager = context.packageManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    val focusState: Flow<FocusStateEntity?> = dao.getFocusState()
    val allCategories: Flow<List<CategoryEntity>> = dao.getAllCategories()
    val categoryAppMappings: Flow<List<CategoryAppCrossRef>> = dao.getAllCategoryAppCrossRefs()
    val settings: Flow<AppSettingsEntity?> = dao.getSettings()
    val recentNotificationRecords: Flow<List<MutedNotificationRecord>> = dao.getRecentNotificationRecords()

    fun getTodayMutedCount(): Flow<Int> {
        val todayStr = getTodayDateString()
        return dao.getMutedCountForDate(todayStr)
    }

    fun getMutedAppsCount(): Flow<Int> = dao.getMutedAppsCountFlow()

    fun getCategoriesWithCounts(): Flow<List<CategoryWithCount>> {
        return combine(allCategories, categoryAppMappings) { categories, mappings ->
            categories.map { category ->
                val appsInCat = mappings.filter { it.categoryId == category.id }.map { it.packageName }
                CategoryWithCount(
                    id = category.id,
                    name = category.name,
                    appCount = appsInCat.size,
                    appPackageNames = appsInCat
                )
            }
        }
    }

    suspend fun getInstalledAppsList(includeSystemApps: Boolean): List<AppInfo> = withContext(Dispatchers.IO) {
        val installedApps: List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }

        val configuredApps = dao.getMutedPackageNamesSync().toSet()
        val alwaysAllowedApps = dao.getAlwaysAllowedPackageNamesSync().toSet()

        val selfPackage = context.packageName

        installedApps
            .filter { appInfo ->
                appInfo.packageName != selfPackage &&
                        (includeSystemApps || (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                                packageManager.getLaunchIntentForPackage(appInfo.packageName) != null)
            }
            .map { appInfo ->
                val label = try {
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    appInfo.packageName
                }
                val icon = try {
                    packageManager.getApplicationIcon(appInfo)
                } catch (_: Exception) {
                    null
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                AppInfo(
                    packageName = appInfo.packageName,
                    appName = label,
                    icon = icon,
                    isMuted = configuredApps.contains(appInfo.packageName),
                    isAlwaysAllowed = alwaysAllowedApps.contains(appInfo.packageName),
                    isSystemApp = isSystem
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    suspend fun setAppMuted(packageName: String, appName: String, isMuted: Boolean) = withContext(Dispatchers.IO) {
        dao.upsertApp(
            MutedAppEntity(
                packageName = packageName,
                appName = appName,
                isMuted = isMuted,
                isAlwaysAllowed = false
            )
        )
    }

    suspend fun setAppAlwaysAllowed(packageName: String, appName: String, isAlwaysAllowed: Boolean) = withContext(Dispatchers.IO) {
        dao.upsertApp(
            MutedAppEntity(
                packageName = packageName,
                appName = appName,
                isMuted = if (isAlwaysAllowed) false else true,
                isAlwaysAllowed = isAlwaysAllowed
            )
        )
    }

    suspend fun setMultipleAppsMuted(apps: List<Pair<String, String>>, isMuted: Boolean) = withContext(Dispatchers.IO) {
        val entities = apps.map { (pkg, name) ->
            MutedAppEntity(
                packageName = pkg,
                appName = name,
                isMuted = isMuted,
                isAlwaysAllowed = false
            )
        }
        dao.upsertApps(entities)
    }

    suspend fun turnOnFocusMode(isTemporary: Boolean = false, durationMinutes: Int = 0) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val expiry = if (isTemporary && durationMinutes > 0) now + (durationMinutes * 60 * 1000L) else 0L

        val state = FocusStateEntity(
            id = 1,
            isFocusModeOn = true,
            isTemporary = isTemporary,
            temporaryDurationMinutes = durationMinutes,
            temporaryExpiryTimestamp = expiry,
            activatedTimestamp = now
        )
        dao.upsertFocusState(state)

        if (isTemporary && expiry > now) {
            scheduleTemporaryMuteAlarm(expiry)
        } else {
            cancelTemporaryMuteAlarm()
        }
    }

    suspend fun turnOffFocusMode() = withContext(Dispatchers.IO) {
        cancelTemporaryMuteAlarm()
        val state = FocusStateEntity(
            id = 1,
            isFocusModeOn = false,
            isTemporary = false,
            temporaryDurationMinutes = 0,
            temporaryExpiryTimestamp = 0L,
            activatedTimestamp = 0L
        )
        dao.upsertFocusState(state)
    }

    suspend fun createCategory(name: String): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext -1L
        dao.insertCategory(CategoryEntity(name = trimmed))
    }

    suspend fun renameCategory(categoryId: Long, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            dao.updateCategoryName(categoryId, trimmed)
        }
    }

    suspend fun deleteCategory(categoryId: Long) = withContext(Dispatchers.IO) {
        dao.deleteCategoryAndMappings(categoryId)
    }

    suspend fun addAppToCategory(categoryId: Long, packageName: String) = withContext(Dispatchers.IO) {
        dao.addAppToCategory(CategoryAppCrossRef(categoryId, packageName))
    }

    suspend fun removeAppFromCategory(categoryId: Long, packageName: String) = withContext(Dispatchers.IO) {
        dao.removeAppFromCategory(categoryId, packageName)
    }

    suspend fun muteAllAppsInCategory(categoryId: Long, isMuted: Boolean) = withContext(Dispatchers.IO) {
        val packageNames = dao.getAppsForCategorySync(categoryId)
        for (pkg in packageNames) {
            val appLabel = try {
                val info = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                pkg
            }
            dao.upsertApp(
                MutedAppEntity(
                    packageName = pkg,
                    appName = appLabel,
                    isMuted = isMuted,
                    isAlwaysAllowed = false
                )
            )
        }
    }

    suspend fun recordMutedNotification(packageName: String, title: String, text: String) = withContext(Dispatchers.IO) {
        val appLabel = try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }

        val record = MutedNotificationRecord(
            packageName = packageName,
            appName = appLabel,
            title = title,
            text = text,
            timestamp = System.currentTimeMillis(),
            dateString = getTodayDateString()
        )
        dao.insertNotificationRecord(record)
    }

    suspend fun clearNotificationHistory() = withContext(Dispatchers.IO) {
        dao.clearAllNotificationRecords()
    }

    suspend fun updateSettings(suppressVibration: Boolean, showShadeIndicator: Boolean, showSystemApps: Boolean) = withContext(Dispatchers.IO) {
        dao.upsertSettings(
            AppSettingsEntity(
                id = 1,
                suppressVibration = suppressVibration,
                showShadeIndicator = showShadeIndicator,
                showSystemApps = showSystemApps
            )
        )
    }

    private fun scheduleTemporaryMuteAlarm(triggerAtMillis: Long) {
        if (alarmManager == null) return
        val intent = Intent(context, BootAndAlarmReceiver::class.java).apply {
            action = BootAndAlarmReceiver.ACTION_TEMPORARY_MUTE_EXPIRED
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, 1001, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelTemporaryMuteAlarm() {
        if (alarmManager == null) return
        val intent = Intent(context, BootAndAlarmReceiver::class.java).apply {
            action = BootAndAlarmReceiver.ACTION_TEMPORARY_MUTE_EXPIRED
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, 1001, intent, flags)
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        fun getTodayDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}
