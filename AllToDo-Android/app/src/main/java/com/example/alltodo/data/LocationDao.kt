package com.example.alltodo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert
    suspend fun insertLocation(location: LocationEntity)

    @Query("SELECT * FROM location_history WHERE timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp ASC")
    fun getLocationsForDay(startOfDay: Long, endOfDay: Long): Flow<List<LocationEntity>>

    @Delete
    suspend fun delete(location: LocationEntity)

    @Query("DELETE FROM location_history")
    suspend fun clearHistory()
}
