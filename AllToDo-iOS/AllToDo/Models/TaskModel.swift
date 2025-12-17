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
    var latInt: Int // Stored as Integer
    var lonInt: Int // Stored as Integer
    var pathData: Data? // JSON encoded [LocationData]
    
    // Computed Properties for compatibility
    var latitude: Double {
        get { Double(latInt) / 100_000.0 }
        set { latInt = Int(newValue * 100_000.0) }
    }
    
    var longitude: Double {
        get { Double(lonInt) / 100_000.0 }
        set { lonInt = Int(newValue * 100_000.0) }
    }
    
    init(startTime: Date, endTime: Date, latitude: Double, longitude: Double, pathData: Data? = nil) {
        self.id = UUID()
        self.startTime = startTime
        self.endTime = endTime
        self.latInt = Int(latitude * 100_000.0)
        self.lonInt = Int(longitude * 100_000.0)
        self.pathData = pathData
    }
}

// Helper struct for Location (SwiftData doesn't support CLLocation directly easily yet without ValueTransformer, keeping it simple)
// Helper struct for Location
struct LocationData: Codable {
    var latInt: Int
    var lonInt: Int
    var name: String?
    var timestamp: Date?
    
    // Computed properties wrapping integer storage
    var latitude: Double {
        get { Double(latInt) / 100_000.0 }
        set { latInt = Int(newValue * 100_000.0) }
    }
    
    var longitude: Double {
        get { Double(lonInt) / 100_000.0 }
        set { lonInt = Int(newValue * 100_000.0) }
    }
    
    // Default Init
    init(latitude: Double, longitude: Double, name: String? = nil, timestamp: Date? = nil) {
        self.latInt = Int(latitude * 100_000.0)
        self.lonInt = Int(longitude * 100_000.0)
        self.name = name
        self.timestamp = timestamp
    }
    
    // Custom CodingKeys
    enum CodingKeys: String, CodingKey {
        case latInt, lonInt, name, timestamp
        case latitude, longitude // For legacy decoding
    }
    
    // Custom Decoding for Migration
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        // Try reading new int format
        if let lLat = try? container.decode(Int.self, forKey: .latInt),
           let lLon = try? container.decode(Int.self, forKey: .lonInt) {
            self.latInt = lLat
            self.lonInt = lLon
        } else {
            // Fallback to legacy Double
            let dLat = try container.decode(Double.self, forKey: .latitude)
            let dLon = try container.decode(Double.self, forKey: .longitude)
            self.latInt = Int(dLat * 100_000.0)
            self.lonInt = Int(dLon * 100_000.0)
        }
        
        self.name = try? container.decode(String.self, forKey: .name)
        self.timestamp = try? container.decode(Date.self, forKey: .timestamp)
    }
    
    // Encode only Ints
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(latInt, forKey: .latInt)
        try container.encode(lonInt, forKey: .lonInt)
        try container.encode(name, forKey: .name)
        try container.encode(timestamp, forKey: .timestamp)
    }
    
    // [NEW] Integer-Coordinate Integration
    var intCoordinate: IntCoordinate {
        return IntCoordinate(lat: latInt, lng: lonInt)
    }
}

extension UserLog {
    // [NEW] Integer-Coordinate Integration
    var intCoordinate: IntCoordinate {
        return IntCoordinate.from(latitude: latitude, longitude: longitude)
    }
}
