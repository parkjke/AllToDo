import Foundation
import MapKit

// Wrapper for different item types
enum UnifiedMapItem: Identifiable {
    case todo(ToDoItem)
    case history(ToDoItem)
    case serverMessage(String)
    case userLocation(CLLocationCoordinate2D)

    
    var id: UUID {
        switch self {
        case .todo(let item): return item.todo_id
        case .history(let item): return item.todo_id
        case .serverMessage: return UUID()
        case .userLocation: return UUID(uuidString: "00000000-0000-0000-0000-000000000000")!

        }
    }
    
    var date: Date {
        switch self {
        case .todo(let item): return item.date_time ?? Date(timeIntervalSince1970: Double(item.created_at) / 1000.0)
        case .history(let item): return item.begin_time ?? Date(timeIntervalSince1970: Double(item.created_at) / 1000.0)
        case .serverMessage: return Date()
        case .userLocation: return Date()

        }
    }
    
    var location: CLLocationCoordinate2D? {
        switch self {
        case .todo(let item): 
            return item.coordinate
        case .history(let item):
            return item.coordinate
        case .serverMessage: return nil
        case .userLocation(let coord): return coord

        }
    }
    
    var name: String {
        switch self {
        case .todo(let item): return item.todo_name
        case .history(let item): return item.todo_name
        case .serverMessage(let msg): return msg
        case .userLocation: return "현재 위치"

        }
    }
    
    // [NEW] Asset Image Name Mapping (Refined)
    // [NEW] Dynamic Component Mapping
    var shieldName: String {
        switch self {
        case .todo: return "pin_shield_1X"
        case .history: return "pin_shield_0X"
        case .serverMessage: return "pin_shield_2X"
        case .userLocation: return "pin_shield_0X"

        }
    }

    // [NEW] Direct Type Mapping for Static Assets
    var type: String {
        switch self {
        case .todo(let item):
            if item.type == "25" { return "25" }
            return item.is_completed ? "12" : "10"
        case .history:
            return "01"
        case .serverMessage:
            return "20"
        case .userLocation:
            return "00"
        }
    }
    
    var markName: String {
        switch self {
        case .todo(let item):
            if item.type == "25" { return "pin_mark_25" }
            if item.is_completed { return "pin_mark_12" } // Check Mark
            return "pin_mark_10" // Exclamation
        case .history:
            return "pin_mark_01" // Footsteps
        case .serverMessage:
            return "pin_mark_20" // Envelope
        case .userLocation:
            return "pin_mark_00" // Current Position (Nav Arrow)
        }
    }
    
    // Legacy mapping (computed for backward compatibility)
    var imageName: String {
        return "Legacy_Pin" // Should not be used directly anymore
    }


    
    // [NEW] Centralized Date Formatter for Callouts
    static let calloutDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM/dd HH:mm"
        return formatter
    }()
}

// MARK: - Map Action Enum
enum MapAction {
    case none
    case zoomIn
    case zoomOut
    case currentLocation
    case rotateNorth
    case zoomToFit
    case launchSequence
    case moveToLocation
}

// Custom Annotation Class
class UnifiedAnnotation: MKPointAnnotation {
    var item: UnifiedMapItem?
    var isClusteredUser: Bool = false
    var clusterItems: [UnifiedMapItem] = []
}

// [NEW] Map Provider Setting Enum
enum MapProvider: String, CaseIterable, Identifiable {
    case apple = "Apple Maps"
    case kakao = "Kakao Maps"
    case naver = "Naver Maps"
    case google = "Google Maps"
    
    var id: String { self.rawValue }
}
