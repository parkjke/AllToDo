import SwiftUI
import MapKit
import Combine

/// [Phase 1 Refactoring]
/// Central Controller for Map Related Features.
/// Handles State, Actions, and User Interactions for the Map View.
class MapFeatureViewModel: ObservableObject {
    
    // MARK: - Map Control State
    @Published var mapAction: MapAction = .none
    @Published var compassRotation: Double = 0.0
    
    // MARK: - Item Selection & Interaction
    @Published var selectedItem: ToDoItem?
    @Published var selectedClusterItems: [UnifiedMapItem]?
    @Published var viewingHistoryItem: ToDoItem?
    @Published var tapPosition: CGPoint?
    
    // MARK: - UI Flags
    @Published var showListView: Bool = false
    @Published var showHistoryMode: Bool = false
    @Published var showCalendar: Bool = false
    @Published var selectedDate: Date = Date()
    
    // MARK: - Dynamic Clustering & Filtering
    @Published var clusterRadius: Double? = 100.0
    @Published var cachedMapItems: [UnifiedMapItem] = []
    @Published var displayItems: [UnifiedMapItem] = [] // [NEW] Items filtered by distance
    
    // MARK: - Notifications
    @Published var showFarNotification: Bool = false
    @Published var farItemsCount: Int = 0
    @Published var ignoreDistanceFilter: Bool = false // [NEW] User override
    
    // MARK: - Create Todo Flow
    @Published var isCreatingTodo: Bool = false
    @Published var creatingTodoLocation: CLLocationCoordinate2D?
    @Published var initialTodoName: String = ""
    @Published var initialTodoTitle: String = "할 일 만들기"
    
    // MARK: - Anchor for filtering
    @Published var anchorDate: Date = Date()
    
    // MARK: - Smart Diffing State
    private var lastProcessedLocation: CLLocation?

    // MARK: - Actions
    func handleZoomIn() {
        self.mapAction = .zoomIn
    }
    
    func handleZoomOut() {
        self.mapAction = .zoomOut
    }
    
    func handleLocationClick() {
        self.mapAction = .currentLocation
    }
    
    func handleCompassClick() {
        self.mapAction = .rotateNorth
    }
    
    func handleHistoryClick() {
        if !showHistoryMode {
            showHistoryMode = true
            selectedDate = Date()
            mapAction = .zoomToFit
        } else {
            showCalendar = true
        }
    }
    
    // MARK: - Data Update Logic (Caller needs to pass raw data)
    func updateMapItems(
        allItems: [ToDoItem],
        currentLocation: CLLocation?,
        showHistory: Bool,
        anchor: Date,
        selectedDate: Date,
        filterByKorea: Bool // [RENAME] from filterByDistance
    ) {
        // Delegate heavy logic to Functional Core
        let transformed = MapLogicHelper.filterAndTransformItems(
            allItems: allItems,
            currentLocation: currentLocation,
            showHistoryMode: showHistory,
            anchorDate: anchor,
            selectedDate: selectedDate
            // checkKoreaLocation removed
        )
        
        // [OPTIMIZATION] Sync Last Processed Location
        self.lastProcessedLocation = currentLocation
        
        self.cachedMapItems = transformed
        
        // Partition for Display (Inside Korea vs Outside)
        if filterByKorea && !ignoreDistanceFilter {
            let (near, count) = MapLogicHelper.partitionItemsByKorea(items: transformed)
            self.displayItems = near
            
            // Update Notification State
            if count != self.farItemsCount {
                self.farItemsCount = count
                if count > 0 {
                    self.showFarNotification = true
                } else {
                    self.showFarNotification = false
                }
            }
        } else {
            // Show All
            self.displayItems = transformed
            self.showFarNotification = false
            self.farItemsCount = 0
        }
    }
    
    func showFarItems() {
        self.ignoreDistanceFilter = true
        self.displayItems = self.cachedMapItems
        self.showFarNotification = false
    }
    
    // MARK: - Smart Diffing Logic
    /// Check if location change triggers update.
    /// Now returns TRUE always to allow View-level smooth animation & diffing.
    func shouldUpdateMapItems(for newLocation: CLLocation) -> Bool {
        // [OPTIMIZATION] Always propagate to View. 
        // LogicHelper and MapView will handle the "Visual Diffing" to prevent flickering.
        return true
    }
    
    /// Syncs the last processed location after a successful update
    func commitLocationUpdate(_ location: CLLocation?) {
        self.lastProcessedLocation = location
    }
}
