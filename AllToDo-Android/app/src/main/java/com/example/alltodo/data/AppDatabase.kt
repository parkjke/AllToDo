package com.example.alltodo.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TodoItem::class, LocationEntity::class, UserLog::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun locationDao(): LocationDao
    abstract fun userLogDao(): UserLogDao
}
