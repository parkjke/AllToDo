import Foundation
import CoreLocation

struct IntCoordinate: Codable, Equatable {
    static let scale: Int = 100_000
    
    let lat: Int
    let lng: Int
    
    init(lat: Int, lng: Int) {
        self.lat = lat
        self.lng = lng
    }
    
    // Create from Double (CLLocationDegrees)
    static func from(latitude: Double, longitude: Double) -> IntCoordinate {
        return IntCoordinate(
            lat: Int((latitude * Double(scale)).rounded()),
            lng: Int((longitude * Double(scale)).rounded())
        )
    }
    
    // Create from CLLocationCoordinate2D
    static func from(_ coordinate: CLLocationCoordinate2D) -> IntCoordinate {
        return from(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }
    
    // Convert to Double tuple
    func toDouble() -> (latitude: Double, longitude: Double) {
        return (
            Double(lat) / Double(IntCoordinate.scale),
            Double(lng) / Double(IntCoordinate.scale)
        )
    }
    
    // Convert to CLLocationCoordinate2D
    var clLocationCoordinate: CLLocationCoordinate2D {
        let (latitude, longitude) = toDouble()
        return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
    
    // Calculate distance to another point in meters
    func distance(to other: IntCoordinate) -> Double {
        let loc1 = CLLocation(latitude: self.clLocationCoordinate.latitude, longitude: self.clLocationCoordinate.longitude)
        let loc2 = CLLocation(latitude: other.clLocationCoordinate.latitude, longitude: other.clLocationCoordinate.longitude)
        return loc1.distance(from: loc2)
    }
}
