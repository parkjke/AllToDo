import Foundation
import CoreLocation

struct SearchResult: Identifiable, Codable {
    let id: String
    let name: String
    let address: String
    let latitude: Double
    let longitude: Double
    let distance: String?
    let isAddress: Bool // [NEW] Flag to distinguish Address search
    
    enum CodingKeys: String, CodingKey {
        case id = "id"
        case name = "place_name"
        case address = "address_name"
        case latitude = "y"
        case longitude = "x"
        case distance = "distance"
    }
    
    init(id: String, name: String, address: String, latitude: Double, longitude: Double, distance: String? = nil, isAddress: Bool = false) {
        self.id = id
        self.name = name
        self.address = address
        self.latitude = latitude
        self.longitude = longitude
        self.distance = distance
        self.isAddress = isAddress
    }
    
    // Custom Decoder to handle local-only fields
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(String.self, forKey: .id)
        self.name = try container.decode(String.self, forKey: .name)
        self.address = try container.decode(String.self, forKey: .address)
        
        // Handle coordinates as String (Kakao API format)
        let latStr = try container.decode(String.self, forKey: .latitude)
        let lonStr = try container.decode(String.self, forKey: .longitude)
        self.latitude = Double(latStr) ?? 0.0
        self.longitude = Double(lonStr) ?? 0.0
        
        self.distance = try? container.decode(String.self, forKey: .distance)
        self.isAddress = false // Default for Codable (from API)
    }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

struct KakaoSearchResponse: Codable {
    let documents: [SearchResult]
}
