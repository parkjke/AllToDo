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
}

// Custom Annotation Class
class UnifiedAnnotation: MKPointAnnotation {
    var item: UnifiedMapItem?
}
