package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AppSettingsEntity
import com.example.data.model.CategoryAppCrossRef
import com.example.data.model.CategoryEntity
import com.example.data.model.FocusStateEntity
import com.example.data.model.MutedAppEntity
import com.example.data.model.MutedNotificationRecord

@Database(
    entities = [
        MutedAppEntity::class,
        CategoryEntity::class,
        CategoryAppCrossRef::class,
        MutedNotificationRecord::class,
        FocusStateEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appMuterDao(): AppMuterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notification_muter_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
