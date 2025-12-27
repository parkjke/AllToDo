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
        case .serverMessage(let msg): 
            // Stable ID based on content hash to prevent flickering
            return UUID(uuidString: "00000000-0000-0000-0000-" + String(format: "%012X", abs(msg.hashValue))) ?? UUID()
        case .userLocation(_): 
            return UUID(uuidString: "00000000-0000-0000-0000-000000000000")!
        }
    }
    
    var date: Date {
        switch self {
        case .todo(let item): return item.date_time ?? Date(timeIntervalSince1970: Double(item.created_at) / 1000.0)
        case .history(let item): return item.begin_time ?? Date(timeIntervalSince1970: Double(item.created_at) / 1000.0)
        case .serverMessage(_): return Date()
        case .userLocation(_): return Date()
        }
    }
    
    var location: CLLocationCoordinate2D? {
        switch self {
        case .todo(let item): 
            return item.coordinate
        case .history(let item):
            return item.coordinate
        case .serverMessage(_): return nil
        case .userLocation(let coord): return coord
        }
    }
    
    var name: String {
        switch self {
        case .todo(let item): return item.todo_name
        case .history(let item): return item.todo_name
        case .serverMessage(let msg): return msg
        case .userLocation(_): return "현재 위치"
        }
    }
    
    // [FIX] Reverted to existing assets until v1.23.0 assets are ready
    var imageName: String {
        switch self {
        case .userLocation(_):
            return "PinCurrent"
        case .history(_):
            return "PinHistory"
        case .todo(let t):
            if t.type == "20" { return "PinReceiveReady" }
            if t.type == "00" { return "PinHistory" }
            return t.is_completed ? "PinTodoDone" : "PinTodoReady"
        case .serverMessage(_):
            return "PinReceiveReady"
        }
    }

    // [FIX] Reverted to existing assets until v1.23.0 assets are ready
    static func resolveClusterStyle(items: [UnifiedMapItem]) -> (baseName: String, color: UIColor, count: Int) {
        if items.count == 1, let item = items.first {
            let name = item.imageName
            let color: UIColor
            if name == "PinHistory" { color = .red }
            else if name == "PinReceiveReady" { color = .blue }
            else if name == "PinCurrent" { color = .red }
            else { color = .allToDoGreen } // PinTodoReady, PinTodoDone
            return (name, color, 1)
        }

        var userLocationFound = false
        var blueCount = 0   // Server Todo / Message
        var greenReadyCount = 0
        var greenDoneCount = 0
        var redHistoryCount = 0
        
        for item in items {
            switch item {
            case .userLocation(_): 
                userLocationFound = true
            case .serverMessage(_): 
                blueCount += 1
            case .todo(let t):
                if t.type == "20" { blueCount += 1 }
                else if t.type == "00" { redHistoryCount += 1 }
                else {
                    if t.is_completed { greenDoneCount += 1 }
                    else { greenReadyCount += 1 }
                }
            case .history(_): 
                redHistoryCount += 1
            }
        }
        
        var baseName = "PinTodoReady" // Default
        
        if userLocationFound {
            baseName = "PinCurrent"
        } else {
            // Priority for clusters:
            if blueCount > 0 {
                baseName = "PinReceiveReady"
            } else if greenReadyCount > 0 {
                baseName = "PinTodoReady"
            } else if greenDoneCount > 0 {
                baseName = "PinTodoDone"
            } else if redHistoryCount > 0 {
                baseName = "PinHistory"
            }
        }
        
        // Color Resolution
        let color: UIColor
        if baseName == "PinHistory" || baseName == "PinCurrent" { color = .red }
        else if baseName == "PinReceiveReady" { color = .blue }
        else { color = .allToDoGreen } // Green (Ready or Done)
        
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
