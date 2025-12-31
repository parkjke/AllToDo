package kr.alltodo.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TodoItem::class, 
        LocationEntity::class, 
        UserLog::class, 
        GpsAuthTrackEntity::class,
        ContactItem::class,
        AddressBookItem::class,
        PathItem::class
    ], 
    version = 6, // Increment version for schema change (Double -> Integer coordinates)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun addressBookDao(): AddressBookDao
    abstract fun locationDao(): LocationDao
    abstract fun userLogDao(): UserLogDao
    abstract fun gpsAuthDao(): GpsAuthDao
}
