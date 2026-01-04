import SwiftUI
import NMapsMap
import CoreLocation
import SwiftData

struct NaverMapView: UIViewRepresentable {
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
    @Binding var targetLocation: CLLocationCoordinate2D? // [NEW] For search
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onUserLocationTap: (() -> Void)?
    
    // Callbacks
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((ToDoItem) -> Void)?
    var onSelectLog: ((ToDoItem) -> Void)?
    var onSelectItem: ((ToDoItem) -> Void)?
    var onFarItemsDetected: ((Int) -> Void)? // [NEW] Callback
    
    // [NEW] Active Path Rendering
    var activePoints: [PathPoint] = []
    var showActivePath: Bool = true

    func makeUIView(context: Context) -> NMFNaverMapView {
        let view = NMFNaverMapView()
        view.showZoomControls = false
        view.showLocationButton = false
        view.showScaleBar = false // Clean Look
        view.mapView.positionMode = .disabled
        view.mapView.isRotateGestureEnabled = true
        view.mapView.isTiltGestureEnabled = false // Keep simple
        
        view.mapView.touchDelegate = context.coordinator
        view.mapView.addCameraDelegate(delegate: context.coordinator)
        
        // [NEW] Theme Policy: Forced Light Mode
        view.mapView.isNightModeEnabled = false
        
        var initialTarget = NMGLatLng(lat: 37.5759, lng: 126.9768)
        
        if let userLoc = locationManager.currentLocation {
            initialTarget = NMGLatLng(lat: userLoc.coordinate.latitude, lng: userLoc.coordinate.longitude)
        } else {
            // [FIX] Prioritize saved location from UserDefaults
            let hasSaved = UserDefaults.standard.bool(forKey: "has_saved_location")
            if hasSaved {
                let savedLat = UserDefaults.standard.double(forKey: "last_latitude")
                let savedLon = UserDefaults.standard.double(forKey: "last_longitude")
                initialTarget = NMGLatLng(lat: savedLat, lng: savedLon)
                print(">>> NaverMapView: Restored from Saved Location: \(savedLat), \(savedLon)")
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
                    case .history(let log):
                        latSum += log.latitude
                        lonSum += log.longitude
                        count += 1
                    case .userLocation(let coord):
                        latSum += coord.latitude
                        lonSum += coord.longitude
                        count += 1
                    case .serverMessage:
                    break
                        break
                    }
                }
                
                if count > 0 {
                    initialTarget = NMGLatLng(lat: latSum / count, lng: lonSum / count)
                }
            }
        }
        
        // [FIX] Instant Display (Saved/Default at Zoom 15)
        let update = NMFCameraUpdate(scrollTo: initialTarget, zoomTo: 15)
        update.animation = .none
        view.mapView.moveCamera(update)
        
        context.coordinator.mapView = view.mapView
        
        return view
    }
    
    func updateUIView(_ uiView: NMFNaverMapView, context: Context) {
        // Sync Logic
        context.coordinator.parent = self
        context.coordinator.mapView = uiView.mapView
        context.coordinator.onFarItemsDetected = onFarItemsDetected
        context.coordinator.creatingTodoLocationBinding = $creatingTodoLocation // [NEW]
        
        // 1. Handle Actions
        if action != .none {
            context.coordinator.handleAction(action)
            DispatchQueue.main.async {
                action = .none
            }
        }
        
        // 2. Trigger Clustering (Only if not in first render sequence)
        if !context.coordinator.firstRender {
            let currentSummary = "\(allItems.count)-\(allItems.first?.id.uuidString ?? "")"
            if context.coordinator.lastDataSummary != currentSummary {
                context.coordinator.lastDataSummary = currentSummary
                context.coordinator.refreshWasmClusters(force: true)
            }
            
            // [NEW] Check Tethering (Conditional)
            if let u = locationManager.currentLocation, !context.coordinator.firstRender, !context.coordinator.isLaunchAnimating {
                context.coordinator.checkTethering(mapView: uiView.mapView, userLocation: u)
            }
            
            // [NEW] Update Path Visualization (Selected Item or Viewing History)
            context.coordinator.updatePath(historyItem: selectedItem ?? viewingHistoryItem)

            
            // [NEW] Active Path Rendering
            context.coordinator.updateActiveRecordingPath(points: activePoints, visible: showActivePath)
        }
        
        // 3. Launch Animation
        if context.coordinator.firstRender, let u = locationManager.currentLocation {
            context.coordinator.performLaunchAnimation(userLocation: u)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, NMFMapViewTouchDelegate, NMFMapViewCameraDelegate {
        var parent: NaverMapView
        var mapView: NMFMapView?
        var firstRender = true

        var lastDataSummary: String = "" // For Smart Refresh
        var lastClusteredWm: Double = -1.0 // [NEW] 1.5x Threshold Tracking
        var onFarItemsDetected: ((Int) -> Void)?
        var creatingTodoLocationBinding: Binding<CLLocationCoordinate2D?>? // [NEW]
        
        var markers: [NMFMarker] = []
        var pathOverlay: NMFPath? 
        var activePathOverlay: NMFPath? 
        
        init(_ parent: NaverMapView) {
            self.parent = parent
        }
        
        // ... (Actions omitted) ...

        // [NEW] Raw Renderer for Naver
        func renderRawItems(mapView: NMFMapView, allItems: [UnifiedMapItem]) {
            markers.forEach { $0.mapView = nil }; markers = []
            
            for item in allItems {
                let marker = NMFMarker()
                
                if let pos = item.location {
                    marker.position = NMGLatLng(lat: pos.latitude, lng: pos.longitude)
                } else { continue }
                
                // Naver Scale: 0.9x (36x45)
                let targetSize = CGSize(width: 36, height: 45)
                
                // UnifiedMapItem definition check:
                // case todo(ToDoItem) -> item.shieldName / item.markName are computable properties
                // but simpler to use MapLogicHelper if needed. However, UnifiedMapItem has them directly.
                
                // [FIX] Use fetchPin
                if let img = PinImageHelper.shared.fetchPin(type: item.type) {
                     // Naver requires resizing if needed, but PinImageHelper returns correct assets.
                     // If adjustment needed:
                    let resized = img.resized(to: targetSize)
                    marker.iconImage = NMFOverlayImage(image: resized ?? img)
                    marker.anchor = CGPoint(x: 0.5, y: 1.0)
                }
                
                marker.mapView = mapView
                markers.append(marker)
            }
        }
        
        // MARK: - Actions
        func handleAction(_ action: MapAction) {
            guard let map = mapView else { return }
            
            switch action {
            case .zoomIn:
                let update = NMFCameraUpdate(zoomTo: map.zoomLevel + 1)
                update.animation = .fly
                update.animationDuration = 0.5
                map.moveCamera(update)
            case .zoomOut:
                let update = NMFCameraUpdate(zoomTo: map.zoomLevel - 1)
                update.animation = .fly
                update.animationDuration = 0.5
                map.moveCamera(update)
            case .currentLocation:
                if let loc = parent.locationManager.currentLocation {
                    OptimizationLogger.shared.log(type: .locationResume, value: ">>> Current Location Button Pressed: \(loc.coordinate)")
                    let update = NMFCameraUpdate(scrollTo: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude), zoomTo: 18)
                    update.animation = .fly
                    update.animationDuration = 1.0
                    map.moveCamera(update)
                }
            case .rotateNorth:
                let update = NMFCameraUpdate(heading: 0)
                update.animation = .fly
                update.animationDuration = 0.5
                map.moveCamera(update)
            case .zoomToFit:
                // Simple Fit to User (or Pins if implemented)
                 if let loc = parent.locationManager.currentLocation {
                    let update = NMFCameraUpdate(scrollTo: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude), zoomTo: 16)
                    map.moveCamera(update)
                 }
            case .launchSequence:
                performLaunchAnimation(userLocation: parent.locationManager.currentLocation)
            case .moveToLocation:
                if let loc = parent.targetLocation {
                    let update = NMFCameraUpdate(scrollTo: NMGLatLng(lat: loc.latitude, lng: loc.longitude), zoomTo: 18)
                    update.animation = .fly
                    update.animationDuration = 0.5
                    map.moveCamera(update)
                }
            case .none: break
            }
        }
        
        // MARK: - Legacy Update (Bridged to WASM)
        func updateAnnotations(items: [ToDoItem]) {
            refreshWasmClusters(force: true)
        }
        func updatePath(historyItem: ToDoItem?) {
             guard let map = mapView else { return }
             
             // 1. Remove existing
             pathOverlay?.mapView = nil
             pathOverlay = nil
             
             // 2. Filter history items (Using ToDoItem)
             guard let log = historyItem, log.type == "00" else { return }
             
             // 3. Query PathItems
             let searchID = log.todo_id
             let descriptor = FetchDescriptor<PathItem>(
                 predicate: #Predicate<PathItem> { $0.todo_id == searchID },
                 sortBy: [SortDescriptor<PathItem>(\.time)]
             )
             if let paths: [PathItem] = try? parent.modelContext.fetch(descriptor) {
                 let coords = paths.map { NMGLatLng(lat: $0.coordinate.latitude, lng: $0.coordinate.longitude) }
                 if coords.count >= 2 {
                     let path = NMFPath()
                     path.path = NMGLineString(points: coords)
                     path.color = .red
                     path.width = 2.5 // Thinned from 4
                     path.outlineWidth = 0
                     path.mapView = map
                     self.pathOverlay = path
                     
                     // [NEW] Auto-zoom to history path using GeomUtils
                     let intRect = GeomUtils.calculateIntBoundingBox(from: paths)
                     let southWest = NMGLatLng(lat: Double(intRect.minLat) / 100_000.0, 
                                               lng: Double(intRect.minLon) / 100_000.0)
                     let northEast = NMGLatLng(lat: Double(intRect.maxLat) / 100_000.0, 
                                               lng: Double(intRect.maxLon) / 100_000.0)
                     
                     DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                         let bounds = NMGLatLngBounds(southWest: southWest, northEast: northEast)
                         let update = NMFCameraUpdate(fit: bounds, padding: 80)
                         update.animation = .easeIn
                         map.moveCamera(update)
                     }
                 }

             }
        }
        
        func updateActiveRecordingPath(points: [PathPoint], visible: Bool) {
            guard let map = mapView else { return }
            
            // 1. Remove existing
            activePathOverlay?.mapView = nil
            activePathOverlay = nil
            
            // 2. Check visibility
            guard visible && points.count >= 2 else { return }
            
            // 3. Render new trail (Convert Int32 -> Double)
            let coords = points.map { NMGLatLng(lat: Double($0.latitude)/100_000.0, lng: Double($0.longitude)/100_000.0) }
            let path = NMFPath()
            path.path = NMGLineString(points: coords)
            path.color = UIColor(red: 1.0, green: 0.34, blue: 0.13, alpha: 1.0) // Orange Red
            path.width = 2.5 // Thinned from 4
            path.outlineWidth = 0


            path.mapView = map
            self.activePathOverlay = path
        }

        func updateUserLocation(_ location: CLLocation) {
            // Handled by WASM now
            refreshWasmClusters()
        }
        
        // MARK: - WASM Clustering
        func refreshWasmClusters(force: Bool = false) {
            guard let map = mapView else { return }
            
            // [FIX] Fallback to Screen Width to prevent initial render failure
            var widthPixels = map.frame.width
            if widthPixels <= 0 {
                widthPixels = 375
            }
            // [OPTIMIZATION] Fast Path
            let total = parent.allItems.count
            // If launching, render raw regardless of count
            // Naver uses 'firstRender' flag in Coordinator.
            // [CRITICAL LOCK: DO NOT MODIFY] Raw First -> Cluster Strategy
             if firstRender {
                  OptimizationLogger.shared.log(type: .launchStep, value: ">>> Fast Path (Naver): Raw Render")
                  
                  // [NEW] Add Creating Todo Location
                  var allItemsToRender = parent.allItems
                  if let target = creatingTodoLocationBinding?.wrappedValue {
                      allItemsToRender.append(.todo(ToDoItem(todo_name: "New Entry", latitude: target.latitude, longitude: target.longitude)))
                  }
                  
                  DispatchQueue.main.async {
                      self.renderRawItems(mapView: map, allItems: allItemsToRender)
                  }
                  return
             }
            
            let zoom = map.zoomLevel
            let centerLat = map.cameraPosition.target.lat
            
            let metersPerPixel = 156543.03392 * cos(centerLat * .pi / 180.0) / pow(2, zoom)
            let wasmCellSize = metersPerPixel * 30.0 // [MODIFIED] Reduced to 30.0
            
            // [NEW] 1.5x Threshold Check
            let isLaunchPhase = parent.action == .launchSequence || firstRender
            let currentWm = metersPerPixel * map.frame.width // Using logical width
            
            if !force && !isLaunchPhase && lastClusteredWm > 0 {
                let ratio = currentWm / lastClusteredWm
                // If change is within 0.66 ~ 1.5, SKIP clustering
                if ratio > 0.6666 && ratio < 1.5 {
                     return
                }
            }
            lastClusteredWm = currentWm
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
                // print(">>> WASM Clustering Start")
                let result = await WasmManager.shared.cluster(points: rawPoints, cellSize: wasmCellSize)
                // print(">>> WASM Clustering Result: \(result.count/3)")
                
                await MainActor.run {
                    self.renderWasmResults(mapView: map, clusterResult: result, allItems: allItemsToProcess)
                }
            }
        }
        
        @MainActor
        func renderWasmResults(mapView: NMFMapView, clusterResult: [Int32], allItems: [UnifiedMapItem]) {
            mapView.locationOverlay.hidden = true
            
            struct Centroid { let lat: Double; let lon: Double; let count: Int }
            var centroids: [Centroid] = []
            
            if clusterResult.count % 3 == 0 {
                for i in stride(from: 0, to: clusterResult.count, by: 3) {
                    let lat = Double(clusterResult[i]) / 100_000.0
                    let lon = Double(clusterResult[i+1]) / 100_000.0
                    if lat.isNaN || lon.isNaN { continue } // [NEW] NaN Guard
                    centroids.append(Centroid(lat: lat, lon: lon, count: Int(clusterResult[i+2])))
                }
            }
            
            var clusters: [[UnifiedMapItem]] = Array(repeating: [], count: centroids.count)
            for item in allItems {
                guard let loc = item.location, !loc.latitude.isNaN, !loc.longitude.isNaN else { continue } // [NEW] NaN Guard
                
                var bestIdx = -1
                var minDist = Double.greatestFiniteMagnitude
                for (idx, c) in centroids.enumerated() {
                    let dist = pow(loc.latitude - c.lat, 2) + pow(loc.longitude - c.lon, 2)
                    if dist < minDist { minDist = dist; bestIdx = idx }
                }
                if bestIdx >= 0 { clusters[bestIdx].append(item) }
            }

            // [SMOOTHING ALGORITHM - 4 STEPS]
            // Step 1: New Entry (Single pins that weren't there)
            // Step 2: Merge Cleanup (Remove singles that are now in clusters)
            // Step 3: Old Cluster Cleanup (Remove old clusters)
            // Step 4: New Cluster Entry (Add new clusters)
            
            var oldMarkers = self.markers
            var newMarkers: [NMFMarker] = []
            
            var clustersToProcess: [(centroid: Centroid, items: [UnifiedMapItem])] = []
            var singlesToProcess: [UnifiedMapItem] = []
            
            for (idx, items) in clusters.enumerated() {
                if items.count == 1 {
                    singlesToProcess.append(items[0])
                } else if items.count > 1 {
                    clustersToProcess.append((centroids[idx], items))
                }
            }
            
            let userMarker = oldMarkers.first(where: { ($0.userInfo["isUser"] as? Bool) == true })
            
            // Step 1: New Entry (Add new single pins)
            for item in singlesToProcess {
                if let existing = oldMarkers.first(where: { ($0.userInfo["id"] as? UUID) == item.id }) {
                    newMarkers.append(existing)
                } else {
                    let marker = createMarker(for: [item], lat: item.location?.latitude ?? 0, lon: item.location?.longitude ?? 0)
                    marker.userInfo["id"] = item.id
                    if item.type == "user" { marker.userInfo["isUser"] = true }
                    marker.mapView = mapView
                    newMarkers.append(marker)
                }
            }
            
            // Step 2 & 3: Find what to remove
            for marker in oldMarkers {
                if marker === userMarker { continue } // Handled via clusters/singles logic
                
                if let id = marker.userInfo["id"] as? UUID {
                    // It was a single pin. Is it still a single pin?
                    if !singlesToProcess.contains(where: { $0.id == id }) {
                        marker.mapView = nil // Step 2: Now in cluster or gone
                    }
                } else {
                    // It was a cluster. Step 3: Remove old clusters
                    marker.mapView = nil
                }
            }
            
            // Step 4: Add New Clusters
            for cluster in clustersToProcess {
                var finalLat = cluster.centroid.lat
                var finalLon = cluster.centroid.lon
                var isUser = false
                
                if let userItem = cluster.items.first(where: { if case .userLocation = $0 { return true }; return false }),
                   let userCoord = userItem.location {
                    finalLat = userCoord.latitude
                    finalLon = userCoord.longitude
                    isUser = true
                }
                
                let marker = createMarker(for: cluster.items, lat: finalLat, lon: finalLon)
                if isUser { marker.userInfo["isUser"] = true }
                marker.mapView = mapView
                newMarkers.append(marker)
            }
            
            // Clean up old user marker if not reused
            if let user = userMarker, !newMarkers.contains(user) {
                user.mapView = nil
            }
            
            self.markers = newMarkers
        }
        
        private func createMarker(for items: [UnifiedMapItem], lat: Double, lon: Double) -> NMFMarker {
            let marker = NMFMarker()
            marker.position = NMGLatLng(lat: lat, lng: lon)
            
            let (pinType, color, count) = MapLogicHelper.resolveClusterStyle(items: items)
            let targetSize = CGSize(width: 36, height: 45)
            
            if items.count == 1 {
                marker.anchor = CGPoint(x: 0.5, y: 1.0)
                if let img = PinImageHelper.shared.fetchPin(type: items[0].type) {
                    let resized = img.resized(to: targetSize)
                    marker.iconImage = NMFOverlayImage(image: resized ?? img)
                }
            } else {
                marker.anchor = CGPoint(x: 18.0/46.0, y: 1.0)
                if let baseImage = PinImageHelper.shared.fetchPin(type: pinType) {
                    let resized = baseImage.resized(to: targetSize) ?? baseImage
                    marker.iconImage = NMFOverlayImage(image: PinImageHelper.shared.applyBadge(to: resized, count: count, badgeColor: color, badgeSize: 18))
                }
            }
            
            marker.touchHandler = { [weak self] (overlay: NMFOverlay) -> Bool in
                guard let self = self, let map = self.mapView, let m = overlay as? NMFMarker else { return false }
                let generator = UIImpactFeedbackGenerator(style: .medium); generator.impactOccurred()
                
                // [FIX] 60pt Offset Strategy (10pt Gap)
                let centerX = map.bounds.width / 2
                let centerY = map.bounds.height / 2
                    let targetY = centerY + 54 // [FIX] +6pt Shift Up (60 -> 54)
                
                if centerY > 0 {
                    let pinPoint = map.projection.point(from: m.position)
                    let deltaX = pinPoint.x - centerX
                    let deltaY = pinPoint.y - targetY
                    
                    let newCenterPoint = CGPoint(x: centerX + deltaX, y: centerY + deltaY)
                    let newCenterCoord = map.projection.latlng(from: newCenterPoint)
                    
                    // [FIX] Instant Selection
                    self.parent.tapPosition = CGPoint(x: centerX, y: centerY)
                    self.parent.selectedClusterItems = items
                    self.parent.selectedItem = nil
                    
                    let update = NMFCameraUpdate(scrollTo: newCenterCoord)
                    update.animation = .fly; update.animationDuration = 0.2
                    map.moveCamera(update)
                }
                return true
            }
            
            return marker
        }
        

        // [NEW] Animation State
        var isLaunchAnimating = false
        
        func performLaunchAnimation(userLocation: CLLocation?) {
             guard let map = mapView, let u = userLocation else { return }
             if isLaunchAnimating { return }
             
             // [FIX] Keep firstRender = true until Stage 3 is complete 
             // to preserve Fast Path (Raw Pins) during Fit Bounds phase.
             isLaunchAnimating = true
             
             // [FIX] Ensure Data is Fresh
             refreshWasmClusters()
             
             OptimizationLogger.shared.log(type: .launchStep, value: "start (Naver)")
             
             // Step 1: Fit Bounds for All Items (User + Pins)
             var minLat = 90.0
             var maxLat = -90.0
             var minLon = 180.0
             var maxLon = -180.0
             var hasPins = false
             
             // Include User
             if let loc = parent.locationManager.currentLocation {
                 minLat = min(minLat, loc.coordinate.latitude)
                 maxLat = max(maxLat, loc.coordinate.latitude)
                 minLon = min(minLon, loc.coordinate.longitude)
                 maxLon = max(maxLon, loc.coordinate.longitude)
             }
             
             // Include Pins
             for item in parent.allItems {
                  switch item {
                  case .todo(let t):
                      minLat = min(minLat, t.latitude)
                      maxLat = max(maxLat, t.latitude)
                      minLon = min(minLon, t.longitude)
                      maxLon = max(maxLon, t.longitude)
                      hasPins = true
                  case .history(let log):
                      minLat = min(minLat, log.latitude)
                      maxLat = max(maxLat, log.latitude)
                      minLon = min(minLon, log.longitude)
                      maxLon = max(maxLon, log.longitude)
                      hasPins = true
                  case .userLocation(let coord):
                      minLat = min(minLat, coord.latitude)
                      maxLat = max(maxLat, coord.latitude)
                      minLon = min(minLon, coord.longitude)
                      maxLon = max(maxLon, coord.longitude)
                      hasPins = true
                  case .serverMessage:
                      break
                  }
             }

             
             if hasPins {
                 let southWest = NMGLatLng(lat: minLat, lng: minLon)
                 let northEast = NMGLatLng(lat: maxLat, lng: maxLon)
                 let bounds = NMGLatLngBounds(southWest: southWest, northEast: northEast)
                 
                 let cameraUpdate = NMFCameraUpdate(fit: bounds, paddingInsets: UIEdgeInsets(top: 100, left: 50, bottom: 100, right: 50))
                 cameraUpdate.animation = .fly
                 cameraUpdate.animationDuration = 1.0
                 map.moveCamera(cameraUpdate)
                 
                 // Step 2: Wait 3s then Zoom to User
                 DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) { [weak self] in
                     guard let self = self else { return }
                     if let loc = self.parent.locationManager.currentLocation {
                         let update = NMFCameraUpdate(scrollTo: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude), zoomTo: 18)
                         update.animation = .fly
                         update.animationDuration = 1.5
                         self.mapView?.moveCamera(update) { _ in
                             self.isLaunchAnimating = false
                             self.firstRender = false
                             self.moveLocation = SmartLocationManager.shared.toIntLocation(loc)
                             self.refreshWasmClusters(force: true)
                         }
                     } else {
                         self.isLaunchAnimating = false
                         self.firstRender = false
                         self.moveLocation = nil
                         self.refreshWasmClusters()
                     }
                 }
             } else {
                 // No Pins: Direct Zoom to User
                 let update = NMFCameraUpdate(scrollTo: NMGLatLng(lat: u.coordinate.latitude, lng: u.coordinate.longitude), zoomTo: 18)
                 update.animation = .fly
                 update.animationDuration = 1.0
                 map.moveCamera(update) { _ in
                     self.isLaunchAnimating = false
                     self.firstRender = false
                     self.moveLocation = SmartLocationManager.shared.toIntLocation(u) // [NEW] Set Initial Anchor
                     self.refreshWasmClusters(force: true)
                 }
             }
        }
        
        // [NEW] Pending Selection for Auto-Center
        var pendingSelection: (items: [UnifiedMapItem], position: NMGLatLng)?

        // MARK: - Delegate Methods
        func mapView(_ mapView: NMFMapView, didTapMap latlng: NMGLatLng, point: CGPoint) {
            DispatchQueue.main.async {
                self.parent.selectedItem = nil
                self.parent.selectedClusterItems = nil
            }
        }
        
        func mapViewCameraIdle(_ mapView: NMFMapView) {
             let rotation = mapView.cameraPosition.heading
             DispatchQueue.main.async {
                 self.parent.rotation = rotation
             }
             
             if let pending = pendingSelection {
                 let items = pending.items
                 let position = pending.position
                 pendingSelection = nil // Reset
                 
                 DispatchQueue.main.async {
                     // 1. Set tapPosition to Screen Center (to align callout tail at center)
                     self.parent.tapPosition = CGPoint(x: mapView.bounds.width / 2, y: mapView.bounds.height / 2)
                     
                     // 2. Show Callout
                     self.parent.selectedClusterItems = items
                     self.parent.selectedItem = nil
                 }
             } else {
                 // Trigger Clustering (Idle)
                 refreshWasmClusters(force: false)
             }
        }
        
        func mapView(_ mapView: NMFMapView, didLongTapMap latlng: NMGLatLng, point: CGPoint) {
            let coord = CLLocationCoordinate2D(latitude: latlng.lat, longitude: latlng.lng)
            
            // [FIX] Target: 100pt above Screen Center (2x Pin Height)
            let screenHeight = mapView.bounds.height
            let targetY = (screenHeight / 2) - 100
            
            // Calculate Offset Ratio
            let targetRatio = targetY / screenHeight
            let offsetRatio = 0.5 - targetRatio
            
            let bounds = mapView.contentBounds
            let spanLat = abs(bounds.northEastLat - bounds.southWestLat)
            let offsetLat = spanLat * offsetRatio
            
            let cameraUpdate = NMFCameraUpdate(scrollTo: NMGLatLng(lat: latlng.lat - offsetLat, lng: latlng.lng))
            cameraUpdate.animation = .fly
            cameraUpdate.animationDuration = 0.5
            mapView.moveCamera(cameraUpdate)
            
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            
            DispatchQueue.main.async {
                self.parent.onLongTap?(coord)
            }
        }
        
        // [NEW] Tethering State
        var currentSpanLon: Int = 0
        var currentSpanLat: Int = 0 // [NEW] Vertical Span
        var moveLocation: (lat: Int, lon: Int)? = nil // [NEW] Anchor Point

        func mapView(_ mapView: NMFMapView, cameraDidChangeByReason reason: Int, animated: Bool) {
             let heading = mapView.cameraPosition.heading
             DispatchQueue.main.async {
                 self.parent.rotation = heading
                 
                 // [NEW] Update Span (H Length / V Length)
                 // contentBounds returns the visible region's bounds.
                 // Even if rotated, Naver seems to provide the aligned bounds or the region bounds?
                 // Documentation says "area covered by the camera".
                 let bounds = mapView.contentBounds
                 
                 let spanLon = abs(bounds.northEastLng - bounds.southWestLng)
                 let spanLat = abs(bounds.northEastLat - bounds.southWestLat)
                 
                 self.currentSpanLon = Int(spanLon * 100_000.0)
                 self.currentSpanLat = Int(spanLat * 100_000.0)
                 
                 // Trigger Re-clustering
                 self.refreshWasmClusters()
             }
        }
        
        // [NEW] Check Tethering (Restored)
        func checkTethering(mapView: NMFMapView, userLocation: CLLocation) {
            // Guard: Launching
            if firstRender || isLaunchAnimating { return }
            
            let uInt = SmartLocationManager.shared.toIntLocation(userLocation)
            
            // 1. Initialize Move Location if Empty
            if moveLocation == nil {
                moveLocation = uInt
                return // Just init, don't move yet? Or check? User said "put current location if empty".
            }
            
            // 2. Check Conditions
            if SmartLocationManager.shared.shouldRecenter(user: uInt, moveLoc: moveLocation!, hLen: currentSpanLon, vLen: currentSpanLat) {
                // 3. Move Camera
                let update = NMFCameraUpdate(scrollTo: NMGLatLng(lat: userLocation.coordinate.latitude, lng: userLocation.coordinate.longitude))
                update.animation = .easeOut
                update.animationDuration = 0.5
                mapView.moveCamera(update)
                
                // 4. Update Move Location
                moveLocation = uInt
                OptimizationLogger.shared.log(type: .locationResume, value: ">>> Smart Tethering Activated (Naver)")
            }
        }
    }
}
