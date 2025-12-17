import Foundation
import SwiftData
import CoreLocation

@Model
final class ToDoItem {
    var id: UUID
    var title: String
    var isCompleted: Bool
    var createdAt: Date
    var dueDate: Date?
    var location: LocationData?
    
    init(title: String, dueDate: Date? = nil, location: LocationData? = nil) {
        self.id = UUID()
        self.title = title
        self.isCompleted = false
        self.createdAt = Date()
        self.dueDate = dueDate
        self.location = location
    }
}



@Model
final class Appointment {
    var id: UUID
    var title: String
    var startTime: Date
    var endTime: Date
    var location: LocationData?
    var participants: [Contact]
    
    init(title: String, startTime: Date, endTime: Date, location: LocationData? = nil, participants: [Contact] = []) {
        self.id = UUID()
        self.title = title
        self.startTime = startTime
        self.endTime = endTime
        self.location = location
        self.participants = participants
    }
}

@Model
final class Contact {
    var id: UUID
    var name: String
    var phoneNumber: String?
    var groupName: String?
    
    init(name: String, phoneNumber: String? = nil, groupName: String? = nil) {
        self.id = UUID()
        self.name = name
        self.phoneNumber = phoneNumber
        self.groupName = groupName
    }
}

@Model
final class UserLog {
    var id: UUID
    var startTime: Date
    var endTime: Date
    var latitude: Double // Stored as Double for stability
    var longitude: Double // Stored as Double for stability
    var pathData: Data? // JSON encoded [LocationData]
    
    // Computed Ints for Performance Logic
    var latInt: Int { Int(latitude * 100_000.0) }
    var lonInt: Int { Int(longitude * 100_000.0) }
    
    init(startTime: Date, endTime: Date, latitude: Double, longitude: Double, pathData: Data? = nil) {
        self.id = UUID()
        self.startTime = startTime
        self.endTime = endTime
        self.latitude = latitude
        self.longitude = longitude
        self.pathData = pathData
    }
}

// Helper struct for Location (SwiftData doesn't support CLLocation directly easily yet without ValueTransformer, keeping it simple)
// Helper struct for Location
struct LocationData: Codable {
    var latitude: Double // Stored as Double
    var longitude: Double // Stored as Double
    var name: String?
    var timestamp: Date?
    
    // Computed Ints for Performance Logic
    var latInt: Int { Int(latitude * 100_000.0) }
    var lonInt: Int { Int(longitude * 100_000.0) }
    
    // Default Init
    init(latitude: Double, longitude: Double, name: String? = nil, timestamp: Date? = nil) {
        self.latitude = latitude
        self.longitude = longitude
        self.name = name
        self.timestamp = timestamp
    }
    
    // Custom CodingKeys include both sets
    enum CodingKeys: String, CodingKey {
        case latitude, longitude, name, timestamp
        case latInt, lonInt // For legacy integer support
    }
    
    // Custom Decoding for Migration
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        // 1. Try reading standard Double format
        if let dLat = try? container.decode(Double.self, forKey: .latitude),
           let dLon = try? container.decode(Double.self, forKey: .longitude) {
            self.latitude = dLat
            self.longitude = dLon
        } 
        // 2. Fallback: Try reading temporary Integer format (100k scale)
        else if let iLat = try? container.decode(Int.self, forKey: .latInt),
                let iLon = try? container.decode(Int.self, forKey: .lonInt) {
            self.latitude = Double(iLat) / 100_000.0
            self.longitude = Double(iLon) / 100_000.0
        } else {
            // Default 0.0 (Gwanghwamun fallback) if all fails
            self.latitude = 37.5759
            self.longitude = 126.9768
        }
        
        self.name = try container.decodeIfPresent(String.self, forKey: .name)
        self.timestamp = try container.decodeIfPresent(Date.self, forKey: .timestamp)
    }
    
    // Encode only Doubles
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(latitude, forKey: .latitude)
        try container.encode(longitude, forKey: .longitude)
        try container.encode(name, forKey: .name)
        try container.encode(timestamp, forKey: .timestamp)
    }
    
    // [NEW] Integer-Coordinate Integration
    var intCoordinate: IntCoordinate {
        return IntCoordinate.from(latitude: latitude, longitude: longitude)
    }
}

extension UserLog {
    // [NEW] Integer-Coordinate Integration
    var intCoordinate: IntCoordinate {
        return IntCoordinate.from(latitude: latitude, longitude: longitude)
    }
}
