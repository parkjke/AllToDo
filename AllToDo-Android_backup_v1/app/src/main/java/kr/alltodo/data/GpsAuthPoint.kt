package kr.alltodo.data

data class GpsAuthPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val status: Int // 0: 정상, 1: 튀는 위치 (Bounced), 2: 있을 수 없는 위치 (Impossible)
)
