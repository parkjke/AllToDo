package kr.alltodo.utils

import kr.alltodo.data.LocationEntity
import kr.alltodo.ui.UnifiedItem
import kr.alltodo.ui.PinClusterItem
import kotlin.math.abs
import kotlin.math.max

/**
 * GeomUtils: 안드로이드를 위한 정수 기반 지리 연산 유틸리티.
 * iOS의 GeomUtils.swift에서 이식되었으며 전 플랫폼의 데이터 일관성을 유지합니다.
 */
object GeomUtils {

    /** 정밀도 배율: 위경도에 이 값을 곱해 정수로 변환 (5자리 소수점 유지, 약 1.1m 정밀도) */
    const val PRECISION = 100000.0

    /** 정수 기반의 사각형 영역을 나타내는 데이터 클래스 */
    data class IntRect(
        val minLat: Int,
        val minLon: Int,
        val maxLat: Int,
        val maxLon: Int
    )

    // --- 중심점(Centroid) 계산 ---

    /**
     * 정수 좌표 목록의 평균 중심점(Centroid)을 계산합니다.
     * 모든 위도와 경도 값을 합산하여 개수로 나눈 산술 평균값으로, 여러 점들의 지리적 중앙 위치를 찾을 때 사용합니다.
     */
    fun calculateCentroid(points: List<Pair<Int, Int>>): Pair<Int, Int> {
        if (points.isEmpty()) return Pair(0, 0)
        
        var totalLat = 0L
        var totalLon = 0L
        for (p in points) {
            totalLat += p.first
            totalLon += p.second
        }
        val count = points.size
        return Pair((totalLat / count).toInt(), (totalLon / count).toInt())
    }

    /**
     * LocationEntity 목록으로부터 평균 중심점을 계산하여 Double 좌표로 반환합니다.
     * 산수로 계산된 중앙 위경도 값을 배율(PRECISION)로 나누어 실제 지도 좌표계 값으로 변환합니다.
     * (지도 초기화나 이동 시 사용)
     */
    fun calculateCentroidFromEntities(points: List<LocationEntity>): Pair<Double, Double> {
        if (points.isEmpty()) return Pair(37.566691, 126.978365) // Default Gwanghwamun
        
        var totalLat = 0L
        var totalLon = 0L
        for (p in points) {
            totalLat += p.int_lat
            totalLon += p.int_long
        }
        val count = points.size
        val avgLat = (totalLat / count) / PRECISION
        val avgLon = (totalLon / count) / PRECISION
        return Pair(avgLat, avgLon)
    }

    // --- 영역(Bounding Box) 계산 ---

    /**
     * 정수 좌표 목록을 포함하는 사각형 영역을 계산하고 지정된 비율만큼 여백(Padding)을 추가합니다.
     */
    fun calculateIntBoundingBox(
        points: List<Pair<Int, Int>>, 
        paddingPercent: Int = 10,
        minDelta: Int = 300 // default approx 330m
    ): IntRect {
        if (points.isEmpty()) return IntRect(0, 0, 0, 0)

        var minLat = points[0].first
        var maxLat = points[0].first
        var minLon = points[0].second
        var maxLon = points[0].second

        for (p in points) {
            if (p.first < minLat) minLat = p.first
            if (p.first > maxLat) maxLat = p.first
            if (p.second < minLon) minLon = p.second
            if (p.second > maxLon) maxLon = p.second
        }

        val latDelta = maxLat - minLat
        val lonDelta = maxLon - minLon

        // Ensure minimum span (approx 30m ~ 30 units)
        val safeLatDelta = max(latDelta, minDelta)
        val safeLonDelta = max(lonDelta, minDelta)

        val latPadding = (safeLatDelta * paddingPercent) / 100
        val lonPadding = (safeLonDelta * paddingPercent) / 100

        return IntRect(
            minLat = minLat - latPadding,
            minLon = minLon - lonPadding,
            maxLat = maxLat + latPadding,
            maxLon = maxLon + lonPadding
        )
    }

    /**
     * UnifiedItem 목록에 대한 Bounding Box 계산.
     * UnifiedItem은 할 일(Todo), 히스토리(History), 사용자 현재 위치(CurrentLocation)를 통합한 데이터 모델입니다.
     * 이 함수는 화면에 표시될 모든 지도 아이템들이 포함된 영역을 계산할 때 사용됩니다.
     */
    fun calculateIntBoundingBoxFromItems(
        items: List<UnifiedItem>, 
        paddingPercent: Int = 10,
        minDelta: Int = 300
    ): IntRect {
        val intPoints = items.map { it.intLat to it.intLng }
        return calculateIntBoundingBox(intPoints, paddingPercent, minDelta)
    }

    // --- 경로 길이 체크 ---

    /**
     * 경로의 총 범위가 특정 정수 단위(기본 30 = 약 33m)보다 짧은지 확인합니다.
     * 너무 짧은 경로는 단일 점으로 취급하거나 최적화할 때 사용합니다.
     * 경로를 저장할 때 범위 안에 있으면 경로를 저장하지 않게 하는 기준이 된다.
     */
    fun isShortPath(points: List<Pair<Int, Int>>, thresholdUnits: Int = 30): Boolean {
        if (points.size < 2) return true

        var minLat = points[0].first
        var maxLat = points[0].first
        var minLon = points[0].second
        var maxLon = points[0].second

        for (p in points) {
            if (p.first < minLat) minLat = p.first
            if (p.first > maxLat) maxLat = p.first
            if (p.second < minLon) minLon = p.second
            if (p.second > maxLon) maxLon = p.second
        }

        val latDiff = maxLat - minLat
        val lonDiff = maxLon - minLon

        return latDiff < thresholdUnits && lonDiff < thresholdUnits
    }
}
