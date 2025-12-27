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
        case .userLocation: return UUID()
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
    var imageName: String {
        switch self {
        case .todo(let item):
            if item.is_completed { return "PinTodoDone" }
            return "PinTodoReady"
        case .history:
            return "PinHistory"
        case .serverMessage:
            return "PinReceiveReady"
        case .userLocation:
            return "PinCurrent"
        }
    }

    // [NEW] Centralized Cluster Style Logic (Priority: User > Majority > Blue > Green > Red)
    static func resolveClusterStyle(items: [UnifiedMapItem]) -> (baseName: String, color: UIColor, count: Int) {
        var userLocationFound = false
        var blueCount = 0   // Server Todo / Message (Type 20)
        var greenCount = 0  // Local Todo (Ready + Done) (Type 10)
        var redCount = 0    // History (Type 00)
        
        for item in items {
            switch item {
            case .userLocation: userLocationFound = true
            case .serverMessage: blueCount += 1
            case .todo(let t):
                if t.type == "20" { blueCount += 1 }
                else if t.type == "00" { redCount += 1 }
                else { greenCount += 1 }
            case .history: redCount += 1
            }
        }
        
        var baseName = "PinTodoReady" // Default
        
        if userLocationFound {
            baseName = "PinCurrent"
        } else {
            let counts = [
                ("PinReceiveReady", blueCount),
                ("PinTodoReady", greenCount),
                ("PinHistory", redCount)
            ]
            
            if let maxItem = counts.max(by: { $0.1 < $1.1 }), maxItem.1 > 0 {
                baseName = maxItem.0
            }
        }
        
        // Color Resolution (Matching PinImageHelper requirements)
        let color: UIColor
        if baseName == "PinHistory" || baseName == "PinCurrent" {
            color = .red
        } else if baseName == "PinReceiveReady" {
            color = .systemBlue
        } else {
            color = .allToDoGreen
        }
        
        return (baseName, color, items.count)
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
