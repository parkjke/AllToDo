package kr.alltodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "location_history")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) 
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
) {
    val intCoordinate: IntCoordinate
        get() = IntCoordinate.fromDouble(latitude, longitude)
}
