package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        DeviceSeriesEntity::class,
        DeviceEntity::class,
        FirmwareEntity::class,
        FirmwareCategoryCrossRef::class,
        FirmwareDeviceCrossRef::class,
        RepoSourceEntity::class,
        ScriptEntity::class,
        ConnectionProfileEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taxonomyDao(): TaxonomyDao
    abstract fun firmwareDao(): FirmwareDao
    abstract fun repoSourceDao(): RepoSourceDao
    abstract fun scriptDao(): ScriptDao
    abstract fun connectionProfileDao(): ConnectionProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pocketburner.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
