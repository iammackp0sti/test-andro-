package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.db.AppDatabase
import com.example.data.model.FocusStateEntity
import com.example.data.repository.MuterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootAndAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        val database = AppDatabase.getInstance(context)
        val repository = MuterRepository(database.appMuterDao(), context)

        when (action) {
            ACTION_TEMPORARY_MUTE_EXPIRED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    repository.turnOffFocusMode()
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val focusState = database.appMuterDao().getFocusStateSync()
                    if (focusState != null && focusState.isFocusModeOn) {
                        val now = System.currentTimeMillis()
                        if (focusState.isTemporary) {
                            if (focusState.temporaryExpiryTimestamp <= now) {
                                // Expired during reboot
                                repository.turnOffFocusMode()
                            } else {
                                // Resume remaining temporary mute
                                val remainingMinutes = ((focusState.temporaryExpiryTimestamp - now) / (60 * 1000L)).toInt()
                                repository.turnOnFocusMode(isTemporary = true, durationMinutes = remainingMinutes)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_TEMPORARY_MUTE_EXPIRED = "com.example.ACTION_TEMPORARY_MUTE_EXPIRED"
    }
}
