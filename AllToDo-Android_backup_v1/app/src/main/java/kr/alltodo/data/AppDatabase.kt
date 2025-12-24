package kr.alltodo.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TodoItem::class, LocationEntity::class, UserLog::class, GpsAuthTrackEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun locationDao(): LocationDao
    abstract fun userLogDao(): UserLogDao
    abstract fun gpsAuthDao(): GpsAuthDao
}
