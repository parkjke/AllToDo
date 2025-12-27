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
        let coords = points.map { (lat: $0.int_lat, lon: $0.int_long) }
        return calculateIntBoundingBox(from: coords, paddingPercent: paddingPercent)
    }
    
    /// 좌표 쌍 리스트를 포함하는 정수 기반 영역을 계산 (범용 버전)
    /// - Parameters:
    ///   - coords: (lat: Int, lon: Int) 튜플 배열
    ///   - paddingPercent: 영역 확장을 위한 여백 비율
    /// - Returns: 정수 위경도 범위를 담은 IntRect
    static func calculateIntBoundingBox(from coords: [(lat: Int, lon: Int)], paddingPercent: Int = 10) -> IntRect {
        guard !coords.isEmpty else {
            return IntRect(minLat: 0, minLon: 0, maxLat: 0, maxLon: 0)
        }
        
        var minLat = coords[0].lat
        var maxLat = coords[0].lat
        var minLon = coords[0].lon
        var maxLon = coords[0].lon
        
        for p in coords {
            if p.lat < minLat { minLat = p.lat }
            if p.lat > maxLat { maxLat = p.lat }
            if p.lon < minLon { minLon = p.lon }
            if p.lon > maxLon { maxLon = p.lon }
        }
        
        let latDelta = maxLat - minLat
        let lonDelta = maxLon - minLon
        
        // 정수 단위 기준 최소 델타 (0.003도 = 300단위)
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
