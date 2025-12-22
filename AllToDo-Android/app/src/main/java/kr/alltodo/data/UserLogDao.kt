package kr.alltodo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserLogDao {
    @Query("SELECT * FROM user_logs ORDER BY start_time DESC")
    fun getAllLogs(): Flow<List<UserLog>>

    @Insert
    suspend fun insertLog(log: UserLog)

    @Delete
    suspend fun deleteLog(log: UserLog)
    
    @Query("SELECT * FROM user_logs WHERE start_time >= :startOfDay AND end_time <= :endOfDay")
    fun getLogsForDay(startOfDay: Long, endOfDay: Long): Flow<List<UserLog>>
}
