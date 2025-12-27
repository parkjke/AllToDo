import Foundation
import CoreLocation

class SmartLocationManager {
    static let shared = SmartLocationManager()
    
    // Precision: 100,000 => ~1.1m resolution
    private let PRECISION: Double = 100000.0
    
    typealias IntLocation = (lat: Int, lon: Int)
    
    func toIntLocation(_ loc: CLLocation) -> IntLocation {
        return (
            Int(loc.coordinate.latitude * PRECISION),
            Int(loc.coordinate.longitude * PRECISION)
        )
    }
    
    /// Determines if location update is needed based on movement and map span (zoom).
    /// - Parameters:
    ///   - lastLoc: Last processed location in integer units
    ///   - newLoc: New GPS location
    ///   - currentSpan: Map latitude span (degrees). Smaller span = higher zoom.
    /// - Returns: True if update is significant enough to redraw.
    func shouldUpdate(lastLoc: IntLocation?, newLoc: CLLocation, currentSpan: Double) -> Bool {
        guard let last = lastLoc else { return true }
        
        let newInt = toIntLocation(newLoc)
        let deltaLat = abs(last.lat - newInt.lat)
        let deltaLon = abs(last.lon - newInt.lon)
        
        // Threshold Logic:
        // We want sensitivity to be roughly 1/500th of the screen height.
        // e.g. Span 0.005 (Zoom ~17) -> Threshold 0.00001 deg (~1.1m) -> 1 unit.
        // e.g. Span 0.5 (Zoom ~10)   -> Threshold 0.001 deg (~100m)   -> 100 units.
        // Formula: Threshold Units = (Span * 200).
        // Clamped to min 2 units (~2m) to avoid jitter at max zoom.
        
        let calculatedThreshold = Int(currentSpan * 200.0)
        let threshold = max(2, calculatedThreshold)
        
        return deltaLat > threshold || deltaLon > threshold
    }
    
    /// Checks if distance > 500km using integer math.
    func isFar(_ loc1: CLLocation, _ loc2: CLLocation) -> Bool {
        let p1 = toIntLocation(loc1)
        let p2 = toIntLocation(loc2)
        return isFar(lat1: p1.lat, lon1: p1.lon, lat2: p2.lat, lon2: p2.lon)
    }
    
    /// Checks if distance > 500km using pure integer coordinates (no double conversion for inputs).
    /// - Parameters:
    ///   - lat1: Latitude * 100,000
    ///   - lon1: Longitude * 100,000
    ///   - lat2: Latitude * 100,000
    ///   - lon2: Longitude * 100,000
    func isFar(lat1: Int, lon1: Int, lat2: Int, lon2: Int) -> Bool {
        let dy = Int64(lat1 - lat2)
        
        // Longitude distance depends on latitude (cos(avgLat)).
        // We still need Double for Cosine calculation unless we use a lookup table,
        // but inputs are Ints.
        // Approx Avg Lat in Rad
        let avgLatRad = Double(lat1 + lat2) / 2.0 / Double(PRECISION) * .pi / 180.0
        let dx = Int64(Double(lon1 - lon2) * cos(avgLatRad))
        
        let distSq = dx*dx + dy*dy
        
        // 500km Limit (Reverted to User Standard)
        // 500km ~= 4.5 deg lat.
        // 4.5 deg * 100,000 = 450,000 units.
        let limit: Int64 = 450000 
        return distSq > (limit * limit)
    }
    
    /// Calculates distance squared between two integer coordinates.
    /// Used for fast proximity checks in clustering.
    func distanceSq(lat1: Int, lon1: Int, lat2: Int, lon2: Int) -> Double {
        let dy = Double(lat1 - lat2)
        let dx = Double(lon1 - lon2)
        let avgLatRad = Double(lat1 + lat2) / 2.0 / PRECISION * .pi / 180.0
        let dxCorrected = dx * cos(avgLatRad)
        return dxCorrected * dxCorrected + dy * dy
    }
    
    // MARK: - New Smart Tethering Logic (User Request 2025-12-19)
    
    /// Checks if the map needs to be re-centered based on the "Move Location" anchor.
    /// Logic: If user moves beyond 1/4 of the screen dimensions from the anchor point, return true.
    /// - Parameters:
    ///   - user: User's current location (IntLocation)
    ///   - moveLoc: The anchor location where the map was last centered (IntLocation)
    ///   - hLen: Horizontal span of the visible map (Longitude delta in Int units)
    ///   - vLen: Vertical span of the visible map (Latitude delta in Int units)
    /// - Returns: True if re-centering is required.
    func shouldRecenter(user: IntLocation, moveLoc: IntLocation, hLen: Int, vLen: Int) -> Bool {
        let deltaLat = abs(user.lat - moveLoc.lat)
        let deltaLon = abs(user.lon - moveLoc.lon)
        
        // Threshold: 1/4 of the screen info
        // Guard against zero lengths
        let thresholdH = hLen > 0 ? hLen / 4 : Int.max
        let thresholdV = vLen > 0 ? vLen / 4 : Int.max
        
        return deltaLat > thresholdV || deltaLon > thresholdH
    }
    
    // Deprecated: Old centering logic
    func needsCentering(user: IntLocation, center: IntLocation, spanLon: Int) -> Bool {
        return false // Disabled in favor of new logic
    }
}
