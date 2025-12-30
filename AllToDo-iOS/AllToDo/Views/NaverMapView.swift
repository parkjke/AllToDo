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
                context.coordinator.refreshWasmClusters()
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
            case .none: break
            }
        }
        
        // MARK: - Legacy Update (Bridged to WASM)
        func updateAnnotations(items: [ToDoItem]) {
            refreshWasmClusters()
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
            
            // 3. Render new trail
            let coords = points.map { NMGLatLng(lat: $0.latitude, lng: $0.longitude) }
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
        func refreshWasmClusters() {
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
            
            // Meter/Pixel Calc: 156543.03392 * cos(lat) / 2^zoom
            let metersPerPixel = 156543.03392 * cos(centerLat * .pi / 180.0) / pow(2, zoom)
            let wasmCellSize = metersPerPixel * 100.0 // [FIX] Restored Standard Sensitivity (100.0)
            
            // [OPTIMIZATION] Strict Loop Prevention: Do NOT update binding during launch or if change is negligible
            let isLaunchPhase = parent.action == .launchSequence || firstRender
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
                    allItemsToProcess.append(item)
                    rawPoints.append(Int32(t.int_lat))
                    rawPoints.append(Int32(t.int_long))
                case .history(let log):
                    allItemsToProcess.append(item)
                    rawPoints.append(Int32(log.int_lat))
                    rawPoints.append(Int32(log.int_long))
                case .userLocation(let coord):
                    allItemsToProcess.append(item)
                    rawPoints.append(Int32(coord.latitude * 100_000))
                    rawPoints.append(Int32(coord.longitude * 100_000))
                case .serverMessage:
                    break
                    break
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
            // [FIX] Disable Native Location Overlay (Blue Dot)
            mapView.locationOverlay.hidden = true
            
            // [FIX] Restore Parsing Logic (Accidentally deleted)
            struct Centroid { let lat: Double; let lon: Double; let count: Int }
            var centroids: [Centroid] = []
            
            if clusterResult.count % 3 == 0 {
                for i in stride(from: 0, to: clusterResult.count, by: 3) {
                    let lat = Double(clusterResult[i]) / 100_000.0
                    let lon = Double(clusterResult[i+1]) / 100_000.0
                    let count = Int(clusterResult[i+2])
                    centroids.append(Centroid(lat: lat, lon: lon, count: count))
                }
            }
            
            var clusters: [[UnifiedMapItem]] = Array(repeating: [], count: centroids.count)
            
            for item in allItems {
                var itemLat: Double = 0
                var itemLon: Double = 0
                switch item {
                case .todo(let t): if let l = t.location { itemLat = l.latitude; itemLon = l.longitude }
                case .history(let l): itemLat = l.latitude; itemLon = l.longitude
                case .userLocation(let coord):
                    itemLat = coord.latitude; itemLon = coord.longitude
                case .serverMessage:
                    break
                }
                
                var bestIdx = -1
                var minDist = Double.greatestFiniteMagnitude
                
                for (idx, c) in centroids.enumerated() {
                    let dLat = itemLat - c.lat
                    let dLon = itemLon - c.lon
                    let dist = dLat*dLat + dLon*dLon
                    if dist < minDist {
                        minDist = dist
                        bestIdx = idx
                    }
                }
                
                if bestIdx >= 0 {
                    clusters[bestIdx].append(item)
                }
            }

            // [VISUAL DIFFING ALGORITHM]
            // Goal: Reuse existing "User Pin" keys to prevent flickering
            
            var newMarkers: [NMFMarker] = []
            var reusedMarker: NMFMarker? = nil
            
            // 1. Identify New User Marker Data
            var newUserMarkerData: (position: NMGLatLng, icon: NMFOverlayImage, anchor: CGPoint, size: CGSize)? = nil
            var newUserItems: [UnifiedMapItem]? = nil
            var newUserClusterIdx = -1
            
            for (idx, items) in clusters.enumerated() {
                guard !items.isEmpty else { continue }
                let centroid = centroids[idx]
                var finalCoord = NMGLatLng(lat: centroid.lat, lng: centroid.lon)
                
                // Check for User
                if let userItem = items.first(where: { if case .userLocation = $0 { return true }; return false }),
                   let userCoord = userItem.location {
                    finalCoord = NMGLatLng(lat: userCoord.latitude, lng: userCoord.longitude)
                    newUserItems = items
                    newUserClusterIdx = idx
                    
                    // Resolve Style
                     let (pinType, markName, color, count) = MapLogicHelper.resolveClusterStyle(items: items)
                     let targetSize = CGSize(width: 36, height: 45)
                     var icon: NMFOverlayImage?
                     var anchor = CGPoint(x: 0.5, y: 1.0)
                     
                     if pinType == .userLocation && count == 1 {
                         icon = NMFOverlayImage(name: "PinCurrent")
                         anchor = CGPoint(x: 0.5, y: 0.5)
                     } else {
                         anchor = CGPoint(x: 18.0 / 46.0, y: 1.0)
                         // [FIX] Use fetchPin
                         if let baseImage = PinImageHelper.shared.fetchPin(type: pinType) {
                             let resizedBaseImage = baseImage.resized(to: targetSize)
                             if count > 1 {
                                 icon = NMFOverlayImage(image: PinImageHelper.shared.applyBadge(to: resizedBaseImage ?? baseImage, count: count, badgeColor: color, badgeSize: 18))
                             } else {
                                 icon = NMFOverlayImage(image: resizedBaseImage ?? baseImage)
                             }
                         }
                     }
                    
                    if let ic = icon {
                        newUserMarkerData = (finalCoord, ic, anchor, targetSize)
                    }
                }
            }
            
            // 2. Try to Find Reusable Marker in Old Set
            if let newData = newUserMarkerData {
                // Find a marker that represents user location in `markers`
                // We don't store metadata in NMFMarker, so we might need a tag or simple heuristics.
                // Heuristic: Check if any existing marker is CLOSE to the new position (e.g. within previous update)
                // OR better: In `markers` array, we can track which one was the user marker.
                // Let's optimize by searching for a marker that *looks* like a user pin (e.g. PinCurrent) or was at the last user loc?
                // Simplest: Just iterate and check if it WAS the user pin? 
                // Since we clear `markers` every time, we lose valid references unless we save them.
                // FIX: We need to know which of the `self.markers` is the user marker. 
                // Let's assume the user marker is unique.
                
                // Let's look for a marker that is currently at the 'old' user location? 
                // No, we should rely on the fact that we can Identify it via TAG or user info.
                
                if let oldUserMarker = self.markers.first(where: {
                    // Check if tag or some property indicates it's a user pin?
                    // We can use `userInfo` dict in NMFMarker if available, or just check global state?
                    // Let's look at `userInfo`. NMFMarker has `userInfo`.
                    return ($0.userInfo["isUser"] as? Bool) == true
                }) {
                     // 3. Reuse!
                     reusedMarker = oldUserMarker
                     
                     // Animate Position
                     // Naver doesn't have built-in property animation for markers, but we can interpolate or set.
                     // Setting position is effectively instant, but if called frequently (60fps), it looks smooth.
                     oldUserMarker.position = newData.position
                     oldUserMarker.iconImage = newData.icon
                     oldUserMarker.anchor = newData.anchor
                     
                     if let items = newUserItems {
                         oldUserMarker.touchHandler = { [weak self] (overlay: NMFOverlay) -> Bool in
                             guard let self = self, let marker = overlay as? NMFMarker else { return false }
                             let generator = UIImpactFeedbackGenerator(style: .medium); generator.impactOccurred()
                             self.pendingSelection = (items, marker.position)
                             let update = NMFCameraUpdate(scrollTo: marker.position)
                             update.animation = .fly; update.animationDuration = 0.3
                             self.mapView?.moveCamera(update)
                             return true
                         }
                     }
                     
                     // Keep it in new list
                     newMarkers.append(oldUserMarker)
                }
            }
            
            // 3. Clean Up Old Markers (Except Reused)
            for marker in self.markers {
                if marker === reusedMarker { continue }
                marker.mapView = nil // Remove from map
            }
            
            // 4. Create NEW Markers (Skip User if Reused)
            for (idx, items) in clusters.enumerated() {
                if items.isEmpty { continue }
                
                // If this is the user cluster and we reused it, SKIP
                if idx == newUserClusterIdx && reusedMarker != nil { continue }
                
                let centroid = centroids[idx]
                var finalCoord = NMGLatLng(lat: centroid.lat, lng: centroid.lon)
                var isUser = false
                
                if let userItem = items.first(where: { if case .userLocation = $0 { return true }; return false }),
                   let userCoord = userItem.location {
                    finalCoord = NMGLatLng(lat: userCoord.latitude, lng: userCoord.longitude)
                    isUser = true
                }
                
                let marker = NMFMarker()
                marker.position = finalCoord
                if isUser { marker.userInfo = ["isUser": true] } // TAG
                
                let (baseName, markName, color, count) = MapLogicHelper.resolveClusterStyle(items: items)
                let targetSize = CGSize(width: 36, height: 45)
                
                if baseName == "PinCurrent" && count == 1 {
                    marker.iconImage = NMFOverlayImage(name: "PinCurrent")
                    marker.width = 30; marker.height = 30
                    marker.anchor = CGPoint(x: 0.5, y: 0.5)
                } else {
                    marker.anchor = CGPoint(x: 18.0 / 46.0, y: 1.0)
                    // [FIX] Use fetchPin
                    if let baseImage = PinImageHelper.shared.fetchPin(type: pinType) { // pinType string usually matches map_pin_XX suffix
                        let resizedBaseImage = baseImage.resized(to: targetSize)
                        if count > 1 {
                            marker.iconImage = NMFOverlayImage(image: PinImageHelper.shared.applyBadge(to: resizedBaseImage ?? baseImage, count: count, badgeColor: color, badgeSize: 18))
                        } else {
                            marker.iconImage = NMFOverlayImage(image: resizedBaseImage ?? baseImage)
                        }
                    }
                }
                
                marker.touchHandler = { [weak self] (overlay: NMFOverlay) -> Bool in
                    guard let self = self, let marker = overlay as? NMFMarker else { return false }
                    let generator = UIImpactFeedbackGenerator(style: .medium); generator.impactOccurred()
                    self.pendingSelection = (items, marker.position)
                    let update = NMFCameraUpdate(scrollTo: marker.position)
                    update.animation = .fly; update.animationDuration = 0.3
                    self.mapView?.moveCamera(update)
                    return true
                }
                
                marker.mapView = mapView
                newMarkers.append(marker)
            }
            
            // 5. Update Local State
            self.markers = newMarkers
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
                             self.refreshWasmClusters()
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
                     self.refreshWasmClusters()
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
             
             // [NEW] Handle Pending Selection (Auto-Center Complete)
             if let pending = pendingSelection {
                 let items = pending.items
                 let position = pending.position
                 pendingSelection = nil // Reset
                 
                 DispatchQueue.main.async {
                     // 1. Calculate Screen Position (Should be Center)
                     let point = mapView.projection.point(from: position)
                     self.parent.tapPosition = point
                     
                     // 2. Show Callout
                     self.parent.selectedClusterItems = items
                     self.parent.selectedItem = nil
                 }
             } else {
                 // Trigger Clustering (Idle)
                 refreshWasmClusters()
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
