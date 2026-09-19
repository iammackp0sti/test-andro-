package com.example.service

import android.app.Notification
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.db.AppDatabase
import com.example.data.model.AppSettingsEntity
import com.example.data.model.FocusStateEntity
import com.example.data.repository.MuterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationMuterService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private lateinit var database: AppDatabase
    private lateinit var repository: MuterRepository
    private var audioManager: AudioManager? = null
    private val handler = Handler(Looper.getMainLooper())

    // In-memory cache for fast lookup during notification delivery
    @Volatile
    private var isFocusModeOn: Boolean = false

    @Volatile
    private var temporaryExpiryTimestamp: Long = 0L

    @Volatile
    private var isTemporary: Boolean = false

    private val mutedPackages = mutableSetOf<String>()
    private val alwaysAllowedPackages = mutableSetOf<String>()

    @Volatile
    private var suppressVibration: Boolean = true

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(applicationContext)
        repository = MuterRepository(database.appMuterDao(), applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        startObservingData()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        job?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startObservingData() {
        job?.cancel()
        job = serviceScope.launch {
            // Observe Focus Mode State
            launch {
                database.appMuterDao().getFocusState().collect { state ->
                    if (state != null) {
                        isFocusModeOn = state.isFocusModeOn
                        isTemporary = state.isTemporary
                        temporaryExpiryTimestamp = state.temporaryExpiryTimestamp

                        // Check if temporary mode expired while connected
                        if (isFocusModeOn && isTemporary && temporaryExpiryTimestamp > 0L) {
                            if (System.currentTimeMillis() >= temporaryExpiryTimestamp) {
                                repository.turnOffFocusMode()
                            }
                        }
                    } else {
                        isFocusModeOn = false
                    }
                }
            }

            // Observe Muted Packages
            launch {
                database.appMuterDao().getMutedPackageNamesFlow().collect { list ->
                    synchronized(mutedPackages) {
                        mutedPackages.clear()
                        mutedPackages.addAll(list)
                    }
                }
            }

            // Observe Always-Allowed (Emergency) Packages
            launch {
                database.appMuterDao().getAlwaysAllowedPackageNamesFlow().collect { list ->
                    synchronized(alwaysAllowedPackages) {
                        alwaysAllowedPackages.clear()
                        alwaysAllowedPackages.addAll(list)
                    }
                }
            }

            // Observe Settings
            launch {
                database.appMuterDao().getSettings().collect { settings ->
                    suppressVibration = settings?.suppressVibration ?: true
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName ?: return

        // 1. Never mute our own app notifications
        if (pkg == packageName) return

        // 2. Check if Focus Mode is currently active
        if (!isFocusModeOn) return

        // Check if temporary mute has expired
        if (isTemporary && temporaryExpiryTimestamp > 0L && System.currentTimeMillis() >= temporaryExpiryTimestamp) {
            serviceScope.launch {
                repository.turnOffFocusMode()
            }
            return
        }

        // 3. Emergency / Always Allow apps must remain completely unaffected
        val isAlwaysAllowed = synchronized(alwaysAllowedPackages) {
            alwaysAllowedPackages.contains(pkg)
        }
        if (isAlwaysAllowed) return

        // 4. Check if this application is selected for muting
        val isMuted = synchronized(mutedPackages) {
            mutedPackages.contains(pkg)
        }
        if (!isMuted) return

        // 5. App is selected for muting:
        // Core Concept: "I still receive the notification, but I don't hear the notification sound."
        // We do NOT cancel the notification.
        // We do NOT delete the notification.
        // We silence the incoming sound trigger cleanly.
        silenceIncomingNotificationSound()

        // 6. Record the muted notification in Room database for count & history
        serviceScope.launch {
            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

            repository.recordMutedNotification(
                packageName = pkg,
                title = title,
                text = text
            )
        }
    }

    private fun silenceIncomingNotificationSound() {
        try {
            audioManager?.let { am ->
                // Mute notification stream momentarily for the incoming sound alert
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                    handler.postDelayed({
                        try {
                            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                        } catch (_: Exception) {}
                    }, 650)
                } else {
                    val currentVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                    am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
                    handler.postDelayed({
                        try {
                            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, currentVol, 0)
                        } catch (_: Exception) {}
                    }, 650)
                }
            }

            // Suppress vibration if enabled in settings
            if (suppressVibration) {
                cancelDeviceVibration()
            }
        } catch (_: Exception) {
            // Gracefully ignore audio permission edge cases on restricted OEM profiles
        }
    }

    private fun cancelDeviceVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.cancel()
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.cancel()
            }
        } catch (_: Exception) {}
    }
}
