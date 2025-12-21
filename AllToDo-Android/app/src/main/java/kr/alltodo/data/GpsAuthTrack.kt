package kr.alltodo.data

data class GpsAuthTrack(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long,
    val points: List<GpsAuthPoint>
) {
    val durationSeconds: Long get() = (endTime - startTime) / 1000
    val totalPoints: Int get() = points.size
    val jumpyCount: Int get() = points.count { it.status == 1 }
    val impossibleCount: Int get() = points.count { it.status == 2 }
}
