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
    @Published var targetLocation: CLLocationCoordinate2D? // [NEW] For search result movement
    
    // MARK: - Item Selection & Interaction
    @Published var selectedItem: ToDoItem?
    @Published var selectedClusterItems: [UnifiedMapItem]?
    @Published var viewingHistoryItem: ToDoItem?
    @Published var tapPosition: CGPoint?
    
    // MARK: - UI Flags
    @Published var showListView: Bool = false
    @Published var showHistoryMode: Bool = false
    @Published var showCalendar: Bool = false
    @Published var showTodoList: Bool = false // [NEW] Separate List Layer
    @Published var selectedDate: Date = Date()
    @Published var shouldRestoreCalendar: Bool = false 
    @Published var shouldRestoreList: Bool = false // [NEW] Dual restoration
    
    // [NEW] 통합 할 일 시트 (iOS Sheet 스타일)
    @Published var showAllTodoSheet: Bool = false
    @Published var mainSheetTab: Int = 0 // 0: 목록, 1: 캘린더
    
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
    @Published var showRipple: Bool = false // [NEW] Search target ripple
    
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
    
    func moveToLocation(_ coordinate: CLLocationCoordinate2D) {
        self.targetLocation = coordinate
        self.mapAction = .moveToLocation
        
        // [MODIFIED] Trigger ripple AFTER arrival (approx 1s)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            self.triggerRipple()
        }
    }
    
    func triggerRipple() {
        self.showRipple = true
        // Keep visible for enough time to complete "팅팅팅" (approx 2.5s)
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            self.showRipple = false
        }
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
        
        // [FIX] Stable Sort (Date > UUID) to prevent Map Diffing Loop
        let sortedTransformed = transformed.sorted {
            let d1 = $0.date
            let d2 = $1.date
            if d1 == d2 { return $0.id.uuidString > $1.id.uuidString }
            return d1 > d2
        }
        
        // [OPTIMIZATION] Sync Last Processed Location
        self.lastProcessedLocation = currentLocation
        
        self.cachedMapItems = sortedTransformed
        
        // Partition for Display (Inside Korea vs Outside)
        if filterByKorea && !ignoreDistanceFilter {
            let (near, count) = MapLogicHelper.partitionItemsByKorea(items: sortedTransformed)
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
            self.displayItems = sortedTransformed
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
