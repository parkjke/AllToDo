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
    
    // [NEW] Asset Image Name Mapping
    var imageName: String {
        switch self {
        case .todo:
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
        var blueCount = 0   // Server Todo / Message
        var greenCount = 0  // Local Todo (Ready + Done)
        var redCount = 0    // History
        
        for item in items {
            switch item {
            case .userLocation: userLocationFound = true
            case .serverMessage: blueCount += 1
            case .todo: greenCount += 1 // Treat all todos as green for now, or check source if available
            case .history: redCount += 1
            }
        }
        
        var baseName = "PinTodoReady" // Default
        
        if userLocationFound {
            // [Rule 1] User Location Priority
            baseName = "PinCurrent"
        } else {
            // [Rule 2 & 3] Majority Vote with Tie-Breaker (Blue > Green > Red)
            // Array order determines priority for ties
            let counts = [
                ("PinTodoReady", greenCount),
                ("PinReceiveReady", blueCount),
                ("PinHistory", redCount)
            ]
            
            // max(by:) returns the first element if values are equal but the closure returns false.
            // Wait, max(by:) behavior: "If there are multiple elements with the same maximum value, this method returns the first one."
            // So we want the HIGHER priority to be returned if counts are equal.
            // But we need to use strict inequality for 'less than'.
            // If we sort by Count Descending, then by Priority Order?
            
            // Let's use the standard Swift max. 
            // counts.max(by: { $0.1 < $1.1 })
            // Example: Blue=1, Green=1. 
            // Compare Blue(1) < Green(1) -> False.
            // Compare Green(1) < Blue(1) -> False.
            // They are equal. `max` returns the FIRST one encountered (Blue).
            // So implicit order in array: Blue, Green, Red matches our priority.
            
            if let max = counts.max(by: { $0.1 < $1.1 }), max.1 > 0 {
                baseName = max.0
            }
        }
        
        // Color Resolution
        let color: UIColor
        if baseName == "PinHistory" { color = .red }
        else if baseName == "PinReceiveReady" { color = .blue }
        else if baseName == "PinCurrent" { color = .red } // User Location is Red Badge (Wait, Android says Red? Doc says Red)
        else { color = .allToDoGreen } // Green
        
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
