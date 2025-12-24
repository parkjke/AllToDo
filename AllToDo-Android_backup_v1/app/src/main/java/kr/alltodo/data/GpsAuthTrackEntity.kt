package kr.alltodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "gps_auth_tracks")
data class GpsAuthTrackEntity(
    @PrimaryKey val id: String,
    val startTime: Long,
    val endTime: Long,
    val pointsJson: String // Serialized List<GpsAuthPoint>
) {
    fun toDomain(): GpsAuthTrack {
        val type = object : TypeToken<List<GpsAuthPoint>>() {}.type
        val points: List<GpsAuthPoint> = Gson().fromJson(pointsJson, type)
        return GpsAuthTrack(id, startTime, endTime, points)
    }

    companion object {
        fun fromDomain(track: GpsAuthTrack): GpsAuthTrackEntity {
            val pointsJson = Gson().toJson(track.points)
            return GpsAuthTrackEntity(track.id, track.startTime, track.endTime, pointsJson)
        }
    }
}
