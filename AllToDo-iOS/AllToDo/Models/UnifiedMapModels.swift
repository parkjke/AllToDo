import Foundation
import MapKit

// Wrapper for different item types
enum UnifiedMapItem: Identifiable {
    case todo(ToDoItem)
    case history(UserLog)
    case serverMessage(String)
    case userLocation // [NEW]
    
    var id: UUID {
        switch self {
        case .todo(let item): return item.id
        case .history(let log): return log.id
        case .serverMessage: return UUID()
        case .userLocation: return UUID()
        }
    }
    
    var date: Date {
        switch self {
        case .todo(let item): return item.dueDate ?? Date.distantFuture
        case .history(let log): return log.startTime
        case .serverMessage: return Date()
        case .userLocation: return Date()
        }
    }
    var location: CLLocationCoordinate2D? {
        switch self {
        case .todo(let item): 
            if let loc = item.location {
                return CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude)
            }
            return nil
        case .history(let log):
            return CLLocationCoordinate2D(latitude: log.latitude, longitude: log.longitude)
        case .serverMessage: return nil
        case .userLocation: return nil // dynamic
        }
    }
    
    // [NEW] Asset Image Name Mapping
    var imageName: String {
        switch self {
        case .todo(let item):
            return item.isCompleted ? "PinTodoDone" : "PinTodoReady"
        case .history:
            return "PinHistory"
        case .serverMessage:
            return "PinReceiveReady"
        case .userLocation:
            return "PinCurrent"
        }
    }
}

// Custom Annotation Class
class UnifiedAnnotation: MKPointAnnotation {
    var item: UnifiedMapItem?
}

// [NEW] Map Provider Setting Enum
enum MapProvider: String, CaseIterable, Identifiable {
    case apple = "Apple Maps"
    case kakao = "Kakao Maps"
    case naver = "Naver Maps"
    case google = "Google Maps"
    
    var id: String { self.rawValue }
}
