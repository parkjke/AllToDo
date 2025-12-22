package kr.alltodo.data

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "gps_auth_tracks")
data class GpsAuthTrackEntity(
    @PrimaryKey 
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "start_time")
    val start_time: Long,
    
    @ColumnInfo(name = "end_time")
    val end_time: Long,
    
    @ColumnInfo(name = "points_json")
    val points_json: String // Serialized List<GpsAuthPoint>
) {
    fun toDomain(): GpsAuthTrack {
        val type = object : TypeToken<List<GpsAuthPoint>>() {}.type
        val points: List<GpsAuthPoint> = Gson().fromJson(points_json, type)
        return GpsAuthTrack(id, start_time, end_time, points)
    }

    companion object {
        fun fromDomain(track: GpsAuthTrack): GpsAuthTrackEntity {
            val pointsJson = Gson().toJson(track.points)
            return GpsAuthTrackEntity(track.id, track.startTime, track.endTime, pointsJson)
        }
    }
}
