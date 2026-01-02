import SwiftUI
import GoogleMaps
import CoreLocation
import SwiftData

struct GoogleMapView: UIViewRepresentable {
    @Environment(\.modelContext) var modelContext
    @Binding var action: MapAction
    @Binding var rotation: Double
    @ObservedObject var locationManager: AppLocationManager
    
    var allItems: [UnifiedMapItem]
    
    @Binding var selectedItem: ToDoItem?
    @Binding var viewingHistoryItem: ToDoItem? // [NEW]
    @Binding var selectedClusterItems: [UnifiedMapItem]?

    @Binding var tapPosition: CGPoint? // [NEW]
    @Binding var clusterRadius: Double? // [NEW]
    @Binding var creatingTodoLocation: CLLocationCoordinate2D? // [NEW]
    var hasItems: Bool
    
    // Actions
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onUserLocationTap: (() -> Void)?
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((ToDoItem) -> Void)?
    var onSelectLog: ((ToDoItem) -> Void)?
    var onSelectItem: ((ToDoItem) -> Void)?
    var onFarItemsDetected: ((Int) -> Void)? // [NEW] Callback for hidden items
    
    // [NEW] Active Path Rendering
    var activePoints: [PathPoint] = []
    var showActivePath: Bool = true
    
    func makeUIView(context: Context) -> GMSMapView {
        let options = GMSMapViewOptions()
        options.frame = .zero
        var initialTarget = CLLocationCoordinate2D(latitude: 37.5759, longitude: 126.9768)
        
        if let userLoc = locationManager.currentLocation {
            initialTarget = userLoc.coordinate
        } else {
            // [FIX] Prioritize saved location from UserDefaults
            let hasSaved = UserDefaults.standard.bool(forKey: "has_saved_location")
            if hasSaved {
                let savedLat = UserDefaults.standard.double(forKey: "last_latitude")
                let savedLon = UserDefaults.standard.double(forKey: "last_longitude")
                initialTarget = CLLocationCoordinate2D(latitude: savedLat, longitude: savedLon)
                print(">>> GoogleMapView: Restored from Saved Location: \(savedLat), \(savedLon)")
            } else {
                // Calculate Centroid fallback
                var latSum: Double = 0
                var lonSum: Double = 0
                var count: Double = 0
                
                for item in allItems {
                    switch item {
                    case .todo(let t):
                        latSum += t.latitude
                        lonSum += t.longitude
                        count += 1
                    case .history(let t):
                        latSum += t.latitude
                        lonSum += t.longitude
                        count += 1
                    case .userLocation(let coord):
                        latSum += coord.latitude
                        lonSum += coord.longitude
                        count += 1
                    case .serverMessage:
                          break
                    }
                }
                
                if count > 0 {
                    initialTarget = CLLocationCoordinate2D(latitude: latSum / count, longitude: lonSum / count)
                }
            }
        }
        
        options.camera = GMSCameraPosition.camera(withTarget: initialTarget, zoom: 15.0)
        
        let view = GMSMapView(options: options)
        view.isMyLocationEnabled = false // [FIX] Hide native Blue Dot
        view.delegate = context.coordinator
        
        // [Parity] Disable System UI to match Apple Map Custom UI
        
        OptimizationLogger.shared.log(type: .launchStep, value: ">>> Map Ready")
        return view
    }
    
    func updateUIView(_ uiView: GMSMapView, context: Context) {
        context.coordinator.parent = self
        
        // Handle Map Actions
        if action != .none {
            context.coordinator.handleAction(action, mapView: uiView)
            DispatchQueue.main.async {
                action = .none
            }
        }
        
        // 2. Refresh WASM Clusters (if needed) - Logic inside
        let currentSummary = "\(allItems.count)-\(allItems.first?.id.uuidString ?? "")"
        if context.coordinator.lastDataSummary != currentSummary {
            context.coordinator.lastDataSummary = currentSummary
            context.coordinator.creatingTodoLocationBinding = $creatingTodoLocation // [NEW]
            context.coordinator.refreshWasmClusters(mapView: uiView)
        }
        
        // [NEW] Check Tethering (Conditional)
        // Only if NOT animating and NOT first render
        if let u = locationManager.currentLocation, !context.coordinator.firstRender, !context.coordinator.isLaunchAnimating {
            context.coordinator.checkTethering(mapView: uiView, userLocation: u)
        }
        
        // Update Path Visualization (Selected Item or Viewing History)
        context.coordinator.updatePath(mapView: uiView, historyItem: selectedItem ?? viewingHistoryItem)

        
        // [NEW] Active Path Rendering
        context.coordinator.updateActiveRecordingPath(mapView: uiView, points: activePoints, visible: showActivePath)
        
        // 3. Launch Animation
        if context.coordinator.firstRender, let u = locationManager.currentLocation {
            context.coordinator.performLaunchAnimation(mapView: uiView, userLocation: u)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, GMSMapViewDelegate {
        var parent: GoogleMapView
        var firstRender = true
        var isLaunchAnimating = false
        var creatingTodoLocationBinding: Binding<CLLocationCoordinate2D?>? // [NEW]
        
        var pathOverlay: GMSPolyline?
        var activePathOverlay: GMSPolyline?
        var lastDataSummary: String = ""
        var pendingSelection: (items: [UnifiedMapItem], position: CLLocationCoordinate2D)?
        var moveLocation: (lat: Int, lon: Int)?
        
        // [NEW] Marker Management for Visual Diffing
        var markers: [GMSMarker] = []

        
        init(_ parent: GoogleMapView) {
            self.parent = parent
        }
        


        // ...Actions...

        // [NEW] Raw Renderer for Google Maps
        func renderRawItems(mapView: GMSMapView, allItems: [UnifiedMapItem]) {
            mapView.clear()
            self.markers.removeAll() // [FIX] Reset state
            for item in allItems {
                let marker = WasmClusterMarker()
                marker.items = [item]
                
                if let pos = item.location {
                    marker.position = pos
                } else { continue }
                
                // 2. Use PinImageHelper for cached/standardized icon
                // [FIX] Use fetchPin
                marker.icon = PinImageHelper.shared.fetchPin(type: item.type)
                
                // Ground Anchor: Tip is at center-bottom
                marker.groundAnchor = CGPoint(x: 0.5, y: 1.0)
                marker.map = mapView
                
                // [FIX] Track Raw Markers so renderClusters can remove them later
                self.markers.append(marker)
            }
        }
        
        // MARK: - Actions
        func handleAction(_ action: MapAction, mapView: GMSMapView) {
            switch action {
            case .zoomIn:
                let zoom = mapView.camera.zoom + 1
                mapView.animate(toZoom: zoom)
            case .zoomOut:
                let zoom = mapView.camera.zoom - 1
                mapView.animate(toZoom: zoom)
            case .currentLocation:
                if let loc = parent.locationManager.currentLocation {
                    OptimizationLogger.shared.log(type: .locationResume, value: ">>> Current Location Button Pressed: \(loc.coordinate)")
                    let update = GMSCameraUpdate.setTarget(loc.coordinate, zoom: 18)
                    mapView.animate(with: update)
                } else {
                    parent.locationManager.requestPermission()
                }
            case .rotateNorth:
                mapView.animate(toBearing: 0)
            case .zoomToFit:
                // Fit bounds logic
                var bounds = GMSCoordinateBounds()
                var count = 0
                if let loc = parent.locationManager.currentLocation {
                    bounds = bounds.includingCoordinate(loc.coordinate)
                    count += 1
                }
                for item in parent.allItems {
                    switch item {
                    case .todo(let t):
                        bounds = bounds.includingCoordinate(CLLocationCoordinate2D(latitude: t.latitude, longitude: t.longitude))
                        count += 1
                    case .history(let log):
                        bounds = bounds.includingCoordinate(CLLocationCoordinate2D(latitude: log.latitude, longitude: log.longitude))
                        count += 1
                    case .userLocation(let coord):
                      bounds = bounds.includingCoordinate(coord)
                  case .serverMessage:
                          break
                        count += 1
                    case .serverMessage:
                          break
                    }
                }
                if count > 0 {
                    // Manually expand bounds to ensure Zoom <= 15
                    let northEast = bounds.northEast 
                    let southWest = bounds.southWest
                    
                    let latSpan = northEast.latitude - southWest.latitude
                    let lonSpan = northEast.longitude - southWest.longitude
                    
                    // Min Span ~ 0.01 for Zoom 15
                    let MIN_SPAN = 0.01
                    
                    if latSpan < MIN_SPAN || lonSpan < MIN_SPAN {
                         // Create new bounds centered on original center
                         let centerLat = (northEast.latitude + southWest.latitude) / 2
                         let centerLon = (northEast.longitude + southWest.longitude) / 2
                         
                         let deltaLat = max(latSpan, MIN_SPAN) / 2
                         let deltaLon = max(lonSpan, MIN_SPAN) / 2
                         
                         let newNE = CLLocationCoordinate2D(latitude: centerLat + deltaLat, longitude: centerLon + deltaLon)
                         let newSW = CLLocationCoordinate2D(latitude: centerLat - deltaLat, longitude: centerLon - deltaLon)
                         
                         bounds = GMSCoordinateBounds(coordinate: newNE, coordinate: newSW)
                    }
                    
                    mapView.animate(with: GMSCameraUpdate.fit(bounds, withPadding: 50.0))
                }
            case .launchSequence:
                self.performLaunchAnimation(mapView: mapView, userLocation: parent.locationManager.currentLocation)
            case .none: break
            }
        }
        
        // (Removed duplicate properties as they are now at the top of Coordinator)
        var currentSpanLon: Int = 0
        var currentSpanLat: Int = 0 

        // MARK: - Delegate Methods
        func mapView(_ mapView: GMSMapView, didChange position: GMSCameraPosition) {
             DispatchQueue.main.async {
                 self.parent.rotation = position.bearing
                 
                 // [NEW] Update Span
                 let region = mapView.projection.visibleRegion()
                 let bounds = GMSCoordinateBounds(region: region)
                 
                 let spanLon = abs(bounds.northEast.longitude - bounds.southWest.longitude)
                 let spanLat = abs(bounds.northEast.latitude - bounds.southWest.latitude)
                 
                 self.currentSpanLon = Int(spanLon * 100_000.0)
                 self.currentSpanLat = Int(spanLat * 100_000.0)
             }
        }
        
        // (Removed duplicate performLaunchAnimation)
        
        // [NEW] Check Tethering (Restored)
        func checkTethering(mapView: GMSMapView, userLocation: CLLocation) {
            if firstRender || isLaunchAnimating { return }
            
            let uInt = SmartLocationManager.shared.toIntLocation(userLocation)
            
            if moveLocation == nil {
                moveLocation = uInt
                return 
            }
            
            if SmartLocationManager.shared.shouldRecenter(user: uInt, moveLoc: moveLocation!, hLen: currentSpanLon, vLen: currentSpanLat) {
                let update = GMSCameraUpdate.setTarget(userLocation.coordinate)
                mapView.animate(with: update)
                moveLocation = uInt // Update Anchor
                // OptimizationLogger.shared.log(type: .locationResume, value: ">>> Smart Tethering Activated (Google)")
            }
        }
        
        func mapView(_ mapView: GMSMapView, idleAt position: GMSCameraPosition) {
            // [NEW] Handle Pending Selection (Auto-Center Complete)
            if let pending = pendingSelection {
                let items = pending.0
                let pos = pending.1
                pendingSelection = nil
                
                DispatchQueue.main.async {
                    // 1. Calculate Screen Point
                    let point = mapView.projection.point(for: pos)
                    self.parent.tapPosition = point
                    
                    // 2. Show Callout
                    self.parent.selectedClusterItems = items
                    self.parent.selectedItem = nil
                }
            } else {
                 // Trigger WASM Clustering on Idle (Region Change End)
                 refreshWasmClusters(mapView: mapView)
            }
        }
        
        func mapView(_ mapView: GMSMapView, didTapAt coordinate: CLLocationCoordinate2D) {
            // Clear Selection
            DispatchQueue.main.async {
                self.parent.selectedClusterItems = nil
            }
        }
        
        func mapView(_ mapView: GMSMapView, didLongPressAt coordinate: CLLocationCoordinate2D) {
            // [FIX] Target: 100pt above Screen Center (2x Pin Height)
            let screenHeight = mapView.bounds.height
            let targetY = (screenHeight / 2) - 100
            
            // Calculate Offset Ratio
            let targetRatio = targetY / screenHeight
            let offsetRatio = 0.5 - targetRatio
            
            let region = mapView.projection.visibleRegion()
            let bounds = GMSCoordinateBounds(region: region)
            let spanLat = abs(bounds.northEast.latitude - bounds.southWest.latitude)
            let offsetLat = spanLat * offsetRatio
            
            let cameraCenter = CLLocationCoordinate2D(latitude: coordinate.latitude - offsetLat, longitude: coordinate.longitude)
            mapView.animate(with: GMSCameraUpdate.setTarget(cameraCenter))
            
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            
            DispatchQueue.main.async {
                self.parent.onLongTap?(coordinate)
            }
        }
        
        func mapView(_ mapView: GMSMapView, didTap marker: GMSMarker) -> Bool {
             // [NEW] Auto-center Logic
             let update = GMSCameraUpdate.setTarget(marker.position)
             mapView.animate(with: update)

             if let custom = marker as? WasmClusterMarker {
                 let generator = UIImpactFeedbackGenerator(style: .medium)
                 generator.impactOccurred()
                 
                 // [NEW] Pending Logic
                 pendingSelection = (custom.items, marker.position)
                 
                 return true
             }
             return false
        }
        
        var lastClusteredWm: Double = -1.0 // [NEW] 1.5x Threshold Tracking

        // MARK: - WASM Clustering
        func refreshWasmClusters(mapView: GMSMapView) {
            // Avoid calc if not ready
            if firstRender && isLaunchAnimating { return } 
            
            let region = mapView.projection.visibleRegion()
            let bounds = GMSCoordinateBounds(region: region)

            // [FIX] Fallback to Screen Width if Map View is not yet laid out
            var widthPixels = mapView.frame.width // frame used in GMS
            if widthPixels <= 0 {
                if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                    widthPixels = windowScene.screen.bounds.width
                } else {
                    widthPixels = 375 // Fallback
                }
            }

            // [OPTIMIZATION] Fast Path
            // [CRITICAL LOCK: DO NOT MODIFY] Raw First -> Cluster Strategy
            let isLaunchPhase = parent.action == .launchSequence || firstRender
            let useFastPath = isLaunchPhase
            
            if useFastPath {
                 OptimizationLogger.shared.log(type: .launchStep, value: ">>> Fast Path (Google): Rendering raw")
                
                 // [NEW] Add Creating Todo Location
                var allItemsToRender = parent.allItems
                if let target = creatingTodoLocationBinding?.wrappedValue {
                    allItemsToRender.append(.todo(ToDoItem(todo_name: "New Entry", latitude: target.latitude, longitude: target.longitude)))
                }
                
                DispatchQueue.main.async {
                    self.renderRawItems(mapView: mapView, allItems: allItemsToRender)
                }
                return
            }
            
            let center = mapView.camera.target
            let zoom = mapView.camera.zoom
            // Meters per pixel ~ 156543.03392 * cos(lat) / 2^zoom
            let metersPerPixel = 156543.03392 * cos(center.latitude * .pi / 180.0) / pow(2, Double(zoom))
            let wasmCellSize = metersPerPixel * 30.0 // [MODIFIED] Reduced to 30.0
            
            // [NEW] 1.5x Threshold Check
            let currentWm = metersPerPixel * widthPixels
            if !isLaunchPhase && lastClusteredWm > 0 {
                let ratio = currentWm / lastClusteredWm
                // If change is within 0.66 ~ 1.5, SKIP clustering
                if ratio > 0.6666 && ratio < 1.5 {
                    return
                }
            }
            lastClusteredWm = currentWm

            // [OPTIMIZATION] Strict Loop Prevention: Do NOT update binding during launch or if change is negligible
            if !isLaunchPhase {
                let currentRadius = parent.clusterRadius ?? 0
                let diff = abs(currentRadius - wasmCellSize)
                if diff > 0.0001 || parent.clusterRadius == nil {
                    DispatchQueue.main.async {
                        self.parent.clusterRadius = wasmCellSize
                    }
                }
            }
            
            // Prepare Data
            var allItemsToProcess: [UnifiedMapItem] = []
            var rawPoints: [Int32] = []
            
            for item in parent.allItems {
                switch item {
                case .todo(let t):
                    if t.latitude.isNaN || t.longitude.isNaN { continue } // [NEW] NaN Guard
                    allItemsToProcess.append(item)
                    rawPoints.append(Int32(t.int_lat))
                    rawPoints.append(Int32(t.int_long))
                case .history(let log):
                    if log.latitude.isNaN || log.longitude.isNaN { continue } // [NEW] NaN Guard
                    allItemsToProcess.append(item)
                    rawPoints.append(Int32(log.int_lat))
                    rawPoints.append(Int32(log.int_long))
                case .userLocation(let coord):
                    if coord.latitude.isNaN || coord.longitude.isNaN { continue } // [NEW] NaN Guard
                    allItemsToProcess.append(item)
                    rawPoints.append(Int32(coord.latitude * 100_000))
                    rawPoints.append(Int32(coord.longitude * 100_000))
                default: break
                }
            }
            
            // [NEW] Add Creating Todo Location if active
            if let target = creatingTodoLocationBinding?.wrappedValue {
                let newItem = ToDoItem(todo_name: "New Entry", latitude: target.latitude, longitude: target.longitude)
                allItemsToProcess.append(.todo(newItem))
                rawPoints.append(Int32(target.latitude * 100_000))
                rawPoints.append(Int32(target.longitude * 100_000))
            }
            
            Task {
                let result = await WasmManager.shared.cluster(points: rawPoints, cellSize: wasmCellSize)
                await MainActor.run {
                    self.renderWasmResults(mapView: mapView, clusterResult: result, allItems: allItemsToProcess)
                }
            }
        }
        
        @MainActor
        func renderWasmResults(mapView: GMSMapView, clusterResult: [Int32], allItems: [UnifiedMapItem]) {
            // [FIX] Restore Parsing Logic
            struct Centroid { let lat: Double; let lon: Double; let count: Int }
            var centroids: [Centroid] = []
            
             if clusterResult.count % 3 == 0 {
                 for i in stride(from: 0, to: clusterResult.count, by: 3) {
                     centroids.append(Centroid(lat: Double(clusterResult[i])/100_000.0, lon: Double(clusterResult[i+1])/100_000.0, count: Int(clusterResult[i+2])))
                 }
             }
            
            var clusters: [[UnifiedMapItem]] = Array(repeating: [], count: centroids.count)
             
             for item in allItems {
                 guard let loc = item.location else { continue }
                 var bestIdx = -1
                 var minDist = Double.greatestFiniteMagnitude
                 for (idx, c) in centroids.enumerated() {
                     let dist = pow(loc.latitude - c.lat, 2) + pow(loc.longitude - c.lon, 2)
                     if dist < minDist { minDist = dist; bestIdx = idx }
                 }
                 if bestIdx >= 0 { clusters[bestIdx].append(item) }
             }

            // [SMOOTHING ALGORITHM - 4 STEPS]
            // We use markers' items as keys for identification.
            
            var oldMarkers = self.markers
            var newMarkers: [GMSMarker] = []
            
            // 1. New Entry: Add single pins that weren't there (or were in clusters)
            // 2. Merge Cleanup: Remove single pins that are now in clusters
            // 3. Old Cluster Cleanup: Remove old invalid clusters
            // 4. New Cluster Entry: Add new clusters
            
            // For GMS, we'll do literal Step-by-Step for visual consistency.
            
            let userMarker = oldMarkers.first(where: { ($0 as? WasmClusterMarker)?.isUserLocation == true })
            
            // Step 1 & 4 Pre-calc: Identify what we need
            var clustersToAdd: [(centroid: Centroid, items: [UnifiedMapItem])] = []
            var singlesToAdd: [UnifiedMapItem] = []
            
            for (idx, items) in clusters.enumerated() {
                if items.count == 1 {
                    singlesToAdd.append(items[0])
                } else {
                    clustersToAdd.append((centroids[idx], items))
                }
            }
            
            // Step 2 & 3: Find what to remove
            var markersToRemove: [GMSMarker] = []
            for marker in oldMarkers {
                guard let custom = marker as? WasmClusterMarker else { continue }
                if custom.isUserLocation { continue } // Handled separately if needed, but usually reused
                
                if custom.items.count == 1 {
                    // It was a single pin. Is it still a single pin?
                    let item = custom.items[0]
                    if !singlesToAdd.contains(where: { $0.id == item.id }) {
                        markersToRemove.append(marker) // Step 2: It's now in a cluster or gone
                    } else {
                        newMarkers.append(marker) // Keep it
                        singlesToAdd.removeAll(where: { $0.id == item.id }) // Already there
                    }
                } else {
                    // It was a cluster. Remove it (Step 3) - we'll re-add new ones
                    markersToRemove.append(marker)
                }
            }
            
            // Apply Steps in Order
            
            // Step 1: Add New Singles
            for item in singlesToAdd {
                let marker = WasmClusterMarker()
                marker.items = [item]
                marker.position = item.location ?? CLLocationCoordinate2D(latitude: 0, longitude: 0)
                marker.icon = PinImageHelper.shared.fetchPin(type: item.type)
                marker.groundAnchor = CGPoint(x: 0.5, y: 1.0)
                marker.map = mapView
                newMarkers.append(marker)
            }
            
            // Step 2 & 3: Remove
            for marker in markersToRemove {
                marker.map = nil
            }
            
            // Step 4: Add New Clusters
            for cluster in clustersToAdd {
                let marker = WasmClusterMarker()
                marker.items = cluster.items
                
                var finalCoord = CLLocationCoordinate2D(latitude: cluster.centroid.lat, longitude: cluster.centroid.lon)
                var isUser = false
                if let userItem = cluster.items.first(where: { if case .userLocation = $0 { return true }; return false }),
                   case .userLocation(let coord) = userItem {
                    finalCoord = coord
                    isUser = true
                }
                
                marker.position = finalCoord
                marker.isUserLocation = isUser
                
                let (pinType, color, count) = MapLogicHelper.resolveClusterStyle(items: cluster.items)
                if let baseImage = PinImageHelper.shared.fetchPin(type: pinType) {
                    marker.icon = PinImageHelper.shared.applyBadge(to: baseImage, count: count, badgeColor: color, badgeSize: 20)
                }
                marker.groundAnchor = isUser ? CGPoint(x: 0.5, y: 1.0) : CGPoint(x: 0.4, y: 1.0)
                marker.map = mapView
                newMarkers.append(marker)
            }
            
            // Special Handle: User Marker (if exists and not in newMarkers yet)
            if let user = userMarker, !newMarkers.contains(user) {
                // Check if user is in any of the new clusters/singles
                // If not, remove it.
                let userStillExists = allItems.contains(where: { if case .userLocation = $0 { return true }; return false })
                if !userStillExists { user.map = nil }
            }
            
            self.markers = newMarkers
        }
        


        // MARK: - Animation
        func performLaunchAnimation(mapView: GMSMapView, userLocation: CLLocation?) {
             guard let userLoc = userLocation else { return }
             isLaunchAnimating = true
            
            // [FIX] Ensure Data is Fresh on Launch/Resume
            refreshWasmClusters(mapView: mapView)
            
             // 3. Fit Bounds (Dynamic)
             // 3. Fit Bounds (Dynamically Filtered)
             var bounds = GMSCoordinateBounds(coordinate: userLoc.coordinate, coordinate: userLoc.coordinate)
             let uLat = Int(userLoc.coordinate.latitude * 100_000)
             let uLon = Int(userLoc.coordinate.longitude * 100_000)
             
             for item in parent.allItems {
                 switch item {
                 case .todo(let t):
                     bounds = bounds.includingCoordinate(CLLocationCoordinate2D(latitude: t.latitude, longitude: t.longitude))
                 case .history(let log):
                     bounds = bounds.includingCoordinate(CLLocationCoordinate2D(latitude: log.latitude, longitude: log.longitude))
                 case .userLocation(let coord):
                      bounds = bounds.includingCoordinate(coord)
                  case .serverMessage:
                          break
                 }
             }
            
             // Apply Fit with Padding
             // [FIX] Ensure min span 0.05 (~Zoom 13) for visible animation
             var ne = bounds.northEast; var sw = bounds.southWest
             let latSpan = max(ne.latitude - sw.latitude, 0.05)
             let lonSpan = max(ne.longitude - sw.longitude, 0.05)
             let centerLat = (ne.latitude + sw.latitude) / 2
             let centerLon = (ne.longitude + sw.longitude) / 2
             
             let newBounds = GMSCoordinateBounds(
                coordinate: CLLocationCoordinate2D(latitude: centerLat + latSpan/2, longitude: centerLon + lonSpan/2),
                coordinate: CLLocationCoordinate2D(latitude: centerLat - latSpan/2, longitude: centerLon - lonSpan/2)
             )
             
             let update = GMSCameraUpdate.fit(newBounds, withPadding: 50.0)
             mapView.animate(with: update)

                // Launch Animation (Wait 3s -> Zoom User)
                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) { [weak self] in
                    guard let self = self else { return }
                  let midCam = GMSCameraUpdate.setTarget(userLoc.coordinate, zoom: 18)
                  CATransaction.begin()
                  CATransaction.setAnimationDuration(1.0)
                  mapView.animate(with: midCam)
                  CATransaction.commit()
                  self.isLaunchAnimating = false
                  
                  OptimizationLogger.shared.log(type: .launchStep, value: ">>> Current Location: \(userLoc.coordinate)")
                  
                  // [FIX] End Launch Phase
                  self.firstRender = false
                  self.moveLocation = SmartLocationManager.shared.toIntLocation(userLoc) // [NEW] Set Initial Anchor
                  self.refreshWasmClusters(mapView: mapView)
               }
        }
        
        // MARK: - Path
        func updatePath(mapView: GMSMapView, historyItem: ToDoItem?) {
             // 1. Remove existing
             pathOverlay?.map = nil
             pathOverlay = nil
             
             guard let log = historyItem, log.type == "00" else { return }
             
             // 3. Query PathItems
             let searchID = log.todo_id
             let descriptor = FetchDescriptor<PathItem>(
                 predicate: #Predicate<PathItem> { $0.todo_id == searchID },
                 sortBy: [SortDescriptor<PathItem>(\.time, order: .forward)]
             )
             if let paths = try? parent.modelContext.fetch(descriptor) {
                 let path = GMSMutablePath()
                 for p in paths {
                     path.add(p.coordinate)
                 }
                 if path.count() >= 2 {
                     let polyline = GMSPolyline(path: path)
                     polyline.strokeColor = .red
                     polyline.strokeWidth = 2.5 // Thinned from 4
                     polyline.map = mapView
                     self.pathOverlay = polyline
                     
                     // [NEW] Auto-zoom to history path using GeomUtils
                     let intRect = GeomUtils.calculateIntBoundingBox(from: paths)
                     let southWest = CLLocationCoordinate2D(latitude: Double(intRect.minLat) / 100_000.0, 
                                                            longitude: Double(intRect.minLon) / 100_000.0)
                     let northEast = CLLocationCoordinate2D(latitude: Double(intRect.maxLat) / 100_000.0, 
                                                            longitude: Double(intRect.maxLon) / 100_000.0)
                     
                     DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                         let bounds = GMSCoordinateBounds(coordinate: southWest, coordinate: northEast)
                         let update = GMSCameraUpdate.fit(bounds, withPadding: 50)
                         mapView.animate(with: update)
                     }
                 }

             }
        }
        
        func updateActiveRecordingPath(mapView: GMSMapView, points: [PathPoint], visible: Bool) {
            // 1. Remove existing
            activePathOverlay?.map = nil
            activePathOverlay = nil
            
            // 2. Check visibility
            guard visible && points.count >= 2 else { return }
            
            // 3. Render new trail (Convert Int32 -> Double)
            let path = GMSMutablePath()
            for p in points {
                path.add(CLLocationCoordinate2D(latitude: Double(p.latitude)/100_000.0, longitude: Double(p.longitude)/100_000.0))
            }
            
            let polyline = GMSPolyline(path: path)
            polyline.strokeColor = UIColor(red: 1.0, green: 0.34, blue: 0.13, alpha: 1.0) // Orange Red
            polyline.strokeWidth = 2.5 // Thinned from 4
            polyline.map = mapView


            self.activePathOverlay = polyline
        }
    }
}

// Marker Subclass
class WasmClusterMarker: GMSMarker {
    var items: [UnifiedMapItem] = []
    var isUserLocation: Bool = false
}
