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
    var latitude: Double // Midpoint Latitude
    var longitude: Double // Midpoint Longitude
    var pathData: Data? // JSON encoded [LocationData]
    
    // Computed property for easy access, ignored by SwiftData persistence if not stored
    // Note: SwiftData doesn't support computed properties well in queries, but for access it's fine.
    
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
struct LocationData: Codable {
    var latitude: Double
    var longitude: Double
    var name: String?
    var timestamp: Date? // Added to support time-based path logging
    
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
