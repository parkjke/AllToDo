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
        
        let dy = Int64(p1.lat - p2.lat)
        
        let avgLatRad = (loc1.coordinate.latitude + loc2.coordinate.latitude) / 2.0 * .pi / 180.0
        let dx = Int64(Double(p1.lon - p2.lon) * cos(avgLatRad))
        
        let distSq = dx*dx + dy*dy
        // 500km ~= 500,000m. 
        // In units (x100,000): 450,000 units (approx, adjusting for deg->m variance)
        // 1 deg lat ~= 111km. 500km ~= 4.5 deg.
        // 4.5 deg * 100,000 = 450,000 units.
        let limit: Int64 = 450000 
        return distSq > (limit * limit)
    }
}
