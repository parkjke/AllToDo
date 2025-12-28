import Foundation
import CoreLocation
import UIKit

/// [Phase 1 Refactoring]
/// Functional Core for Map Logic.
/// Contains only static pure functions used by MapFeatureViewModel.
struct MapLogicHelper {
    
    /// Filters and transforms raw ToDoItems into UnifiedMapItems for display.
    /// - Parameters:
    ///   - allItems: Raw DB items
    ///   - currentLocation: User's current location (for Type 00 virtual item)
    ///   - showHistoryMode: Toggle for history viewing
    ///   - anchorDate: Reference date for ±24h filtering (Realtime)
    ///   - selectedDate: Reference date for ±24h filtering (Time Travel)
    /// - Returns: Filtered and transformed list of UnifiedMapItem
    static func filterAndTransformItems(
        allItems: [ToDoItem],
        currentLocation: CLLocation?,
        showHistoryMode: Bool,
        anchorDate: Date,
        selectedDate: Date,
        // checkKoreaLocation removed - using partition strategy

    ) -> [UnifiedMapItem] {
        
        let centerDate = showHistoryMode ? selectedDate : anchorDate
        // Calculate ±24h window
        guard let min = Calendar.current.date(byAdding: .hour, value: -24, to: centerDate),
              let max = Calendar.current.date(byAdding: .hour, value: 24, to: centerDate) else {
            return []
        }
        
        // 1. Filter by location path existence
        let withPath = allItems.filter { $0.is_exist_location_path }
        
        // 2. Filter by time window
        let timeFiltered = withPath.filter {
            let itemDate = $0.begin_time ?? $0.date_time ?? Date(timeIntervalSince1970: Double($0.created_at)/1000.0)
            return itemDate >= min && itemDate <= max
        }
        
        var results: [UnifiedMapItem] = []
        
        // 3. Add Current Location (Virtual Item)
        if let current = currentLocation {
            results.append(.userLocation(current.coordinate))
        }
        
        // 4. Transform to UnifiedMapItem & Apply 5. Geo-fencing
        for item in timeFiltered {
            // Geo-fencing is now handled by Partitioning (Outside Korea = Far)

            
            if item.type.hasPrefix("0") {
                results.append(.history(item))
            } else {
                results.append(.todo(item))
            }
        }
        
        return results
    }
    
    /// Checks if coordinate is roughly within Korea
    private static func isInKorea(lat: Double, lon: Double) -> Bool {
        return lat >= 32.0 && lat <= 44.0 && lon >= 123.0 && lon <= 133.0
    }
    
    /// Calculates the distance between two coordinates in meters.
    static func distance(from: CLLocationCoordinate2D, to: CLLocationCoordinate2D) -> CLLocationDistance {
        let loc1 = CLLocation(latitude: from.latitude, longitude: from.longitude)
        let loc2 = CLLocation(latitude: to.latitude, longitude: to.longitude)
        return loc1.distance(from: loc2)
    }
    
    /// Partitions items into 'inside Korea' (Near) and 'outside Korea' (Far).
    /// - Parameters:
    ///   - items: List of UnifiedMapItem
    /// - Returns: Tuple of (nearItems, farCount)
    static func partitionItemsByKorea(
        items: [UnifiedMapItem]
    ) -> (near: [UnifiedMapItem], farCount: Int) {
        
        var near: [UnifiedMapItem] = []
        var farCount = 0
        
        for item in items {
            // Virtual item is always near
            if case .userLocation = item {
                near.append(item)
                continue
            }
            
            guard let itemLoc = item.location else {
                near.append(item)
                continue
            }
            
            if isInKorea(lat: itemLoc.latitude, lon: itemLoc.longitude) {
                near.append(item)
            } else {
                farCount += 1
            }
        }
        
        return (near, farCount)
    }
    


    // ... (rest of the file)

    /// Resolves the cluster style (icon base name and color) based on the items in the cluster.
    /// - Parameter items: List of UnifiedMapItem in the cluster
    /// - Returns: Tuple of (baseName, color, count)
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
}
