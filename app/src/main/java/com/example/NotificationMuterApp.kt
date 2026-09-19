package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.db.AppDatabase
import com.example.data.repository.MuterRepository

class NotificationMuterApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: MuterRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = MuterRepository(database.appMuterDao(), this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                getString(R.string.focus_mode_active_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.focus_mode_active_channel_desc)
                enableVibration(false)
                setSound(null, null)
            }

            val testChannel = NotificationChannel(
                CHANNEL_TEST,
                "Test Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Used to test notification muting"
            }

            notificationManager?.createNotificationChannels(listOf(statusChannel, testChannel))
        }
    }

    companion object {
        const val CHANNEL_STATUS = "channel_focus_status"
        const val CHANNEL_TEST = "channel_test"
    }
}
