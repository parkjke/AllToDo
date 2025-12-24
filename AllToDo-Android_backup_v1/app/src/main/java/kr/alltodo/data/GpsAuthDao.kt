package kr.alltodo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GpsAuthDao {
    @Query("SELECT * FROM gps_auth_tracks ORDER BY startTime DESC")
    fun getAllTracks(): Flow<List<GpsAuthTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: GpsAuthTrackEntity)

    @Query("DELETE FROM gps_auth_tracks WHERE id = :id")
    suspend fun deleteTrack(id: String)
}
