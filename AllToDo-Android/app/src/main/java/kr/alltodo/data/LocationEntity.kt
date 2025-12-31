package kr.alltodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "location_history")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) 
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "int_lat")
    val int_lat: Int,
    
    @ColumnInfo(name = "int_long")
    val int_long: Int,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
) {
    val latitude: Double get() = int_lat / 100_000.0
    val longitude: Double get() = int_long / 100_000.0
}
