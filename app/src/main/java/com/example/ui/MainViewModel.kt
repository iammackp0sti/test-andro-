package com.example.ui

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.NotificationMuterApp
import com.example.R
import com.example.data.model.AppInfo
import com.example.data.model.AppSettingsEntity
import com.example.data.model.CategoryWithCount
import com.example.data.model.FocusStateEntity
import com.example.data.model.MutedNotificationRecord
import com.example.data.repository.MuterRepository
import com.example.service.NotificationMuterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

enum class Screen {
    HOME,
    MANAGE_APPS,
    CATEGORIES,
    TEMPORARY_MUTE,
    NOTIFICATION_HISTORY,
    SETTINGS
}

enum class AppFilter {
    ALL,
    MUTED_ONLY,
    ALWAYS_ALLOWED
}

class MainViewModel(
    private val repository: MuterRepository,
    private val context: Context
) : ViewModel() {

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    val focusState: StateFlow<FocusStateEntity?> = repository.focusState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayMutedCount: StateFlow<Int> = repository.getTodayMutedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val mutedAppsCount: StateFlow<Int> = repository.getMutedAppsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val categoriesWithCounts: StateFlow<List<CategoryWithCount>> = repository.getCategoriesWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentHistory: StateFlow<List<MutedNotificationRecord>> = repository.recentNotificationRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettingsEntity?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsEntity())

    // Apps Management State
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _appFilter = MutableStateFlow(AppFilter.ALL)
    val appFilter: StateFlow<AppFilter> = _appFilter.asStateFlow()

    // Filtered apps list combining search query & filter
    val filteredApps: StateFlow<List<AppInfo>> = combine(_installedApps, _searchQuery, _appFilter) { apps, query, filter ->
        val trimmedQuery = query.trim().lowercase()
        apps.filter { app ->
            val matchesQuery = trimmedQuery.isEmpty() ||
                    app.appName.lowercase().contains(trimmedQuery) ||
                    app.packageName.lowercase().contains(trimmedQuery)

            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.MUTED_ONLY -> app.isMuted
                AppFilter.ALWAYS_ALLOWED -> app.isAlwaysAllowed
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live remaining countdown for temporary mute
    private val _remainingTimeFormatted = MutableStateFlow("Until manually turned off")
    val remainingTimeFormatted: StateFlow<String> = _remainingTimeFormatted.asStateFlow()

    // Permission state
    private val _isNotificationAccessGranted = MutableStateFlow(false)
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    init {
        checkPermission()
        loadInstalledApps()
        startCountdownTicker()
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        if (screen == Screen.MANAGE_APPS && _installedApps.value.isEmpty()) {
            loadInstalledApps()
        }
        checkPermission()
    }

    fun checkPermission() {
        val cn = ComponentName(context, NotificationMuterService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        _isNotificationAccessGranted.value = flat != null && flat.contains(cn.flattenToString())
    }

    fun openNotificationAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun toggleFocusMode() {
        val currentState = focusState.value?.isFocusModeOn ?: false
        viewModelScope.launch {
            if (currentState) {
                repository.turnOffFocusMode()
            } else {
                repository.turnOnFocusMode(isTemporary = false)
            }
        }
    }

    fun startTemporaryMute(minutes: Int) {
        viewModelScope.launch {
            repository.turnOnFocusMode(isTemporary = true, durationMinutes = minutes)
            _currentScreen.value = Screen.HOME
        }
    }

    fun cancelTemporaryMute() {
        viewModelScope.launch {
            repository.turnOffFocusMode()
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            val showSystem = settings.value?.showSystemApps ?: false
            val list = repository.getInstalledAppsList(includeSystemApps = showSystem)
            _installedApps.value = list
            _isLoadingApps.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setAppFilter(filter: AppFilter) {
        _appFilter.value = filter
    }

    fun toggleAppMuted(app: AppInfo) {
        viewModelScope.launch {
            val newMuted = !app.isMuted
            repository.setAppMuted(app.packageName, app.appName, newMuted)
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == app.packageName) it.copy(isMuted = newMuted, isAlwaysAllowed = if (newMuted) false else it.isAlwaysAllowed) else it
            }
        }
    }

    fun toggleAppAlwaysAllowed(app: AppInfo) {
        viewModelScope.launch {
            val newAllowed = !app.isAlwaysAllowed
            repository.setAppAlwaysAllowed(app.packageName, app.appName, newAllowed)
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == app.packageName) it.copy(isAlwaysAllowed = newAllowed, isMuted = if (newAllowed) false else it.isMuted) else it
            }
        }
    }

    fun muteAllCurrentFilteredApps(isMuted: Boolean) {
        viewModelScope.launch {
            val appsToUpdate = filteredApps.value.map { it.packageName to it.appName }
            repository.setMultipleAppsMuted(appsToUpdate, isMuted)
            loadInstalledApps()
        }
    }

    // Categories
    fun createCategory(name: String) {
        viewModelScope.launch {
            repository.createCategory(name)
        }
    }

    fun renameCategory(id: Long, newName: String) {
        viewModelScope.launch {
            repository.renameCategory(id, newName)
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            repository.deleteCategory(id)
        }
    }

    fun addAppToCategory(categoryId: Long, packageName: String) {
        viewModelScope.launch {
            repository.addAppToCategory(categoryId, packageName)
        }
    }

    fun removeAppFromCategory(categoryId: Long, packageName: String) {
        viewModelScope.launch {
            repository.removeAppFromCategory(categoryId, packageName)
        }
    }

    fun muteAllAppsInCategory(categoryId: Long, isMuted: Boolean) {
        viewModelScope.launch {
            repository.muteAllAppsInCategory(categoryId, isMuted)
            loadInstalledApps()
        }
    }

    // History
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearNotificationHistory()
        }
    }

    // Settings
    fun updateSettings(suppressVibration: Boolean, showShadeIndicator: Boolean, showSystemApps: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(suppressVibration, showShadeIndicator, showSystemApps)
            loadInstalledApps()
        }
    }

    // Test notification
    fun sendTestNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val builder = NotificationCompat.Builder(context, NotificationMuterApp.CHANNEL_TEST)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Test Notification")
            .setContentText("This is a test notification from Notification Muter to verify shade visibility.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager?.notify(999, builder.build())
    }

    private fun startCountdownTicker() {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val state = focusState.value
                if (state != null && state.isFocusModeOn) {
                    if (state.isTemporary && state.temporaryExpiryTimestamp > 0L) {
                        val remainingMillis = state.temporaryExpiryTimestamp - System.currentTimeMillis()
                        if (remainingMillis <= 0) {
                            _remainingTimeFormatted.value = "Expired"
                            repository.turnOffFocusMode()
                        } else {
                            val totalMinutes = (remainingMillis / (1000 * 60)).toInt()
                            val hours = totalMinutes / 60
                            val minutes = totalMinutes % 60
                            _remainingTimeFormatted.value = if (hours > 0) {
                                "${hours}h ${minutes}m remaining"
                            } else {
                                "${minutes}m remaining"
                            }
                        }
                    } else {
                        _remainingTimeFormatted.value = "Until manually turned off"
                    }
                } else {
                    _remainingTimeFormatted.value = "Off"
                }
                delay(1000)
            }
        }
    }

    class Factory(
        private val repository: MuterRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, context) as T
        }
    }
}
