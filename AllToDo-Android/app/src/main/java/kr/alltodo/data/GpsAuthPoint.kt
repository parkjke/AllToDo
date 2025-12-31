package kr.alltodo.data

data class GpsAuthPoint(
    val intLat: Int,
    val intLng: Int,
    val timestamp: Long,
    val status: Int // 0: 정상, 1: 튀는 위치 (Bounced), 2: 있을 수 없는 위치 (Impossible)
) {
    val latitude: Double get() = intLat / 100_000.0
    val longitude: Double get() = intLng / 100_000.0
}
