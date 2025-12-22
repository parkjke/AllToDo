package kr.alltodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "user_logs")
data class UserLog(
    @PrimaryKey(autoGenerate = true) 
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "latitude")
    val latitude: Double, // Midpoint latitude
    
    @ColumnInfo(name = "longitude")
    val longitude: Double, // Midpoint longitude
    
    @ColumnInfo(name = "start_time")
    val start_time: Long,
    
    @ColumnInfo(name = "end_time")
    val end_time: Long,
    
    @ColumnInfo(name = "path_data")
    val path_data: String // JSON string of List<LocationEntity>
)
