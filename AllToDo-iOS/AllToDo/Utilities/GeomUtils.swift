import Foundation

/// [사용자 요구사항] 정수로 값을 주고 정수로 계산하고 정수로 값을 반환하는 지리 연산 유틸리티
struct GeomUtils {
    
    struct IntRect {
        var minLat: Int
        var minLon: Int
        var maxLat: Int
        var maxLon: Int
    }
    
    /// 경로 포인트들을 모두 포함하는 정수 기반 영역을 계산하고 여백을 추가함
    /// - Parameters:
    ///   - points: PathItem 배열 (int_lat, int_long 사용)
    ///   - paddingPercent: 영역 확장을 위한 여백 비율 (기본 10%)
    /// - Returns: 정수 위경도 범위를 담은 IntRect
    static func calculateIntBoundingBox(from points: [PathItem], paddingPercent: Int = 10) -> IntRect {
        guard !points.isEmpty else {
            return IntRect(minLat: 0, minLon: 0, maxLat: 0, maxLon: 0)
        }
        
        var minLat = points[0].int_lat
        var maxLat = points[0].int_lat
        var minLon = points[0].int_long
        var maxLon = points[0].int_long
        
        for p in points {
            if p.int_lat < minLat { minLat = p.int_lat }
            if p.int_lat > maxLat { maxLat = p.int_lat }
            if p.int_long < minLon { minLon = p.int_long }
            if p.int_long > maxLon { maxLon = p.int_long }
        }
        
        // 정수 연산으로 패딩 추가
        let latDelta = maxLat - minLat
        let lonDelta = maxLon - minLon
        
        // 너무 좁은 영역 방지 (최소 0.003도 = 300 정수 단위)
        let minDelta = 300
        let safeLatDelta = max(latDelta, minDelta)
        let safeLonDelta = max(lonDelta, minDelta)
        
        let latPadding = (safeLatDelta * paddingPercent) / 100
        let lonPadding = (safeLonDelta * paddingPercent) / 100
        
        return IntRect(
            minLat: minLat - latPadding,
            minLon: minLon - lonPadding,
            maxLat: maxLat + latPadding,
            maxLon: maxLon + lonPadding
        )
    }
}
