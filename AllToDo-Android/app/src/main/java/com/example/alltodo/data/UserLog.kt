package com.example.alltodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_logs")
data class UserLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double, // Midpoint latitude
    val longitude: Double, // Midpoint longitude
    val startTime: Long,
    val endTime: Long,
    val pathData: String // JSON string of List<LocationEntity>
)
