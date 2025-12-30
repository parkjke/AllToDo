import Foundation

/// [사용자 요구사항] 정수로 값을 주고 정수로 계산하고 정수로 값을 반환하는 지리 연산 유틸리티
struct GeomUtils {
    
    struct IntRect {
        var minLat: Int
        var minLon: Int
        var maxLat: Int
        var maxLon: Int
    }
    
   
    /// PathPoint 배열의 중심점(Centroid)을 정수 좌표로 계산
    /// - Parameter points: PathPoint 배열 ([Int32, Int32])
    /// - Returns: (lat: Int, lon: Int)
    static func calculateCentroid(from points: [PathPoint]) -> (lat: Int, lon: Int) {
        guard !points.isEmpty else { return (0, 0) }
        
        // Int32 합산 시 오버플로우 방지를 위해 Int(64bit)로 변환하여 합산
        let totalLat = points.reduce(0) { $0 + Int($1.latitude) }
        let totalLon = points.reduce(0) { $0 + Int($1.longitude) }
        let count = points.count
        
        return (totalLat / count, totalLon / count)
    }

    /// [Overload] PathItem 배열의 중심점을 계산하여 Double 좌표로 반환 (For Map Initialization)
    /// - Parameter points: PathItem 배열
    /// - Returns: (centerLat: Double, centerLon: Double)
    static func calculateCentroid(from points: [PathItem]) -> (centerLat: Double, centerLon: Double) {
        guard !points.isEmpty else { return (37.566691, 126.978365) }
        
        let totalLat = points.reduce(0) { $0 + Int($1.int_lat) }
        let totalLon = points.reduce(0) { $0 + Int($1.int_long) }
        let count = points.count
        
        let avgLat = Double(totalLat / count) / 100_000.0
        let avgLon = Double(totalLon / count) / 100_000.0
        
        return (avgLat, avgLon)
    }
    
    /// 경로 포인트들을 모두 포함하는 정수 기반 영역을 계산하고 여백을 추가함 (For PathItem)
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
 
     /// PathPoint 배열의 정수 기반 영역 계산 (Overload)
    static func calculateIntBoundingBox(from points: [PathPoint], paddingPercent: Int = 10) -> IntRect {
        guard !points.isEmpty else {
            return IntRect(minLat: 0, minLon: 0, maxLat: 0, maxLon: 0)
        }
        
        var minLat = Int(points[0].latitude)
        var maxLat = Int(points[0].latitude)
        var minLon = Int(points[0].longitude)
        var maxLon = Int(points[0].longitude)
        
        for p in points {
            let lat = Int(p.latitude)
            let lon = Int(p.longitude)
            
            if lat < minLat { minLat = lat }
            if lat > maxLat { maxLat = lat }
            if lon < minLon { minLon = lon }
            if lon > maxLon { maxLon = lon }
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

    /// 경로가 지정된 거리(단위)보다 짧은지 확인 (30m 최적화용)
    /// - Parameters:
    ///   - points: PathPoint 배열
    ///   - thresholdUnits: 기준 정수 단위 (기본 30 = 약 33m)
    /// - Returns: Bool (True if shorter than threshold)
    static func isShortPath(points: [PathPoint], thresholdUnits: Int = 30) -> Bool {
        guard points.count >= 2 else { return true }
        
        // 패딩 없이 순수 Bounding Box 계산을 위해 내부 로직 사용 혹은 padding 0 호출
        // 계산 효율을 위해 직접 Min/Max 만 빠르게 추출 (Padding 불필요)
        var minLat = Int(points[0].latitude)
        var maxLat = Int(points[0].latitude)
        var minLon = Int(points[0].longitude)
        var maxLon = Int(points[0].longitude)
        
        for p in points {
            let lat = Int(p.latitude)
            let lon = Int(p.longitude)
            if lat < minLat { minLat = lat }
            if lat > maxLat { maxLat = lat }
            if lon < minLon { minLon = lon }
            if lon > maxLon { maxLon = lon }
        }
        
        let latDiff = maxLat - minLat
        let lonDiff = maxLon - minLon
        
        // [User Request] Simple Integer Check
        return latDiff < thresholdUnits && lonDiff < thresholdUnits
    }
}
