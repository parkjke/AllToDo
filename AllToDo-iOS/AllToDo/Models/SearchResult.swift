import Foundation
import CoreLocation

struct SearchResult: Identifiable, Codable {
    let id: String
    let name: String
    let address: String
    let latitude: Double
    let longitude: Double
    let distance: String?
    
    enum CodingKeys: String, CodingKey {
        case id = "id"
        case name = "place_name"
        case address = "address_name"
        case latitude = "y"
        case longitude = "x"
        case distance = "distance"
    }
    
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

struct KakaoSearchResponse: Codable {
    let documents: [SearchResult]
}
