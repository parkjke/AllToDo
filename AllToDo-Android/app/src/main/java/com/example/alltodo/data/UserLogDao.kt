package com.example.alltodo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserLogDao {
    @Query("SELECT * FROM user_logs ORDER BY startTime DESC")
    fun getAllLogs(): Flow<List<UserLog>>

    @Insert
    suspend fun insertLog(log: UserLog)

    @Delete
    suspend fun deleteLog(log: UserLog)
    
    @Query("SELECT * FROM user_logs WHERE startTime >= :startOfDay AND endTime <= :endOfDay")
    fun getLogsForDay(startOfDay: Long, endOfDay: Long): Flow<List<UserLog>>
}
