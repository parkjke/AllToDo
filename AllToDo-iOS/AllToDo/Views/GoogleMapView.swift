import SwiftUI
import GoogleMaps
import CoreLocation
import SwiftData

struct GoogleMapView: UIViewRepresentable {
    @Environment(\.modelContext) var modelContext
    @Binding var action: MapAction
    @Binding var rotation: Double
    @ObservedObject var locationManager: AppLocationManager
    
    var todoItems: [ToDoItem]
    var userLogs: [ToDoItem]
    
    @Binding var selectedItem: ToDoItem?
    @Binding var selectedClusterItems: [UnifiedMapItem]?
    @Binding var tapPosition: CGPoint? // [NEW]
    @Binding var clusterRadius: Double? // [NEW]
    var hasItems: Bool
    
    // Actions
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onUserLocationTap: (() -> Void)?
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((ToDoItem) -> Void)?
    var onSelectLog: ((ToDoItem) -> Void)?
    var onSelectItem: ((ToDoItem) -> Void)?
    var onFarItemsDetected: ((Int) -> Void)? // [NEW] Callback for hidden items
    
    func makeUIView(context: Context) -> GMSMapView {
        let options = GMSMapViewOptions()
        options.frame = .zero
        
        // [FIX] Initial Camera Calculation (User Location > Pins Centroid > Seoul)
        // [FIX] Initial Camera Calculation (User Location > Pins Centroid > Gwanghwamun)
        var initialTarget = CLLocationCoordinate2D(latitude: 37.5759, longitude: 126.9768) // Default Gwanghwamun
        
        if let userLoc = locationManager.currentLocation {
            initialTarget = userLoc.coordinate
        } else {
            // Calculate Centroid
            var latSum: Double = 0
            var lonSum: Double = 0
            var count: Double = 0
            
            for item in todoItems {
                if let loc = item.location {
                    latSum += loc.latitude
                    lonSum += loc.longitude
                    count += 1
                }
            }
            for log in userLogs {
                latSum += log.latitude
                lonSum += log.longitude
                count += 1
            }
            
            if count > 0 {
                initialTarget = CLLocationCoordinate2D(latitude: latSum / count, longitude: lonSum / count)
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
        
        // [FIX] Ensure clusters are refreshed when SwiftUI state changes, BUT avoid redundant calls at init
        // Only refresh if not in first render sequence (Launch Animation handles the first refresh)
        // 2. Refresh WASM Clusters (if needed) - Logic inside
        context.coordinator.refreshWasmClusters(mapView: uiView)
        
        // [NEW] Check Tethering (Conditional)
        // Only if NOT animating and NOT first render
        if let u = locationManager.currentLocation, !context.coordinator.firstRender, !context.coordinator.isAnimating {
            context.coordinator.checkTethering(mapView: uiView, userLocation: u)
        }
        
        // [FIX] Update Path Visualization -> REMOVED per user request
        // context.coordinator.updatePath(mapView: uiView, selectedItems: selectedClusterItems)
        
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
        var isAnimating = false
        
        init(_ parent: GoogleMapView) {
            self.parent = parent
        }
        


        // ...Actions...

        // [NEW] Raw Renderer for Google Maps
        func renderRawItems(mapView: GMSMapView, allItems: [UnifiedMapItem]) {
            mapView.clear()
            for item in allItems {
                let marker = WasmClusterMarker()
                marker.items = [item]
                
                // 1. Position
                switch item {
                 case .todo(let t): if let l = t.location { marker.position = CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude) }
                 case .history(let l): marker.position = CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude)
                 case .userLocation: if let u = parent.locationManager.currentLocation { marker.position = u.coordinate }
                 default: break
                }
                
                // 2. Icon Type
                var name = "PinTodoReady" // Default Green
                switch item {
                case .todo(let t):
                    if t.isCompleted { name = "PinTodoDone" }
                    // Source check not available currently
                    // if t.source != "local" { name = "PinReceiveReady" } // Blue
                case .history: name = "PinHistory"
                case .userLocation: name = "PinCurrent"
                case .serverMessage: name = "PinReceiveReady"
                }
                
                marker.icon = UIImage(named: name)?.resized(to: CGSize(width: 40, height: 50))
                // Explicit Anchor
                marker.groundAnchor = CGPoint(x: 0.5, y: 1.0)
                
                marker.map = mapView
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
                for item in parent.todoItems {
                    if let l = item.location {
                        bounds = bounds.includingCoordinate(CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude))
                        count += 1
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
        
        // [NEW] Pending Selection for Auto-Center
        var pendingSelection: (items: [UnifiedMapItem], position: CLLocationCoordinate2D)?

        // [FIX] Restored Missing Properties
        var currentSpanLon: Int = 0
        var currentSpanLat: Int = 0 
        var moveLocation: (lat: Int, lon: Int)? = nil

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
            if firstRender || isAnimating { return }
            
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
                let items = pending.items
                let pos = pending.position
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
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            parent.onLongTap?(coordinate)
        }
        
        func mapView(_ mapView: GMSMapView, didTap marker: GMSMarker) -> Bool {
             // [NEW] Auto-Center Logic
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
        
        // MARK: - WASM Clustering
        func refreshWasmClusters(mapView: GMSMapView) {
            // Avoid calc if not ready
            if firstRender && isAnimating { return } 
            
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
            
            // guard widthPixels > 0 else { return } // Removed guard
            
            // [OPTIMIZATION] Fast Path
            // [CRITICAL LOCK: DO NOT MODIFY] Raw First -> Cluster Strategy
            let totalCount = parent.todoItems.count + parent.userLogs.count
            let isLaunchPhase = parent.action == .launchSequence || firstRender
            let useFastPath = isLaunchPhase
            
            if useFastPath {
                 OptimizationLogger.shared.log(type: .launchStep, value: ">>> Fast Path (Google): Rendering raw")
                
                // Pre-calc user int location
                var uInt: (lat: Int, lon: Int)? = nil
                if let u = parent.locationManager.currentLocation {
                    uInt = SmartLocationManager.shared.toIntLocation(u)
                }
                
                 // Prepare Raw
                 var allItems: [UnifiedMapItem] = []
                 var farCount = 0
                 
                 for item in parent.todoItems {
                     if let loc = item.location {
                         // 500km (Integer)
                         if let u = uInt, SmartLocationManager.shared.isFar(lat1: u.lat, lon1: u.lon, lat2: item.latInt, lon2: item.lonInt) {
                             farCount += 1
                             continue
                         }
                         allItems.append(.todo(item))
                     }
                 }
                 for log in parent.userLogs {
                     // 500km (Integer)
                      if let u = uInt, SmartLocationManager.shared.isFar(lat1: u.lat, lon1: u.lon, lat2: log.latInt, lon2: log.lonInt) {
                          farCount += 1
                          continue
                      }
                     allItems.append(.history(log))
                 }
                 if let u = parent.locationManager.currentLocation { allItems.append(.userLocation) }
                 
                 // Notify
                 if farCount > 0 {
                     DispatchQueue.main.async { self.parent.onFarItemsDetected?(farCount) }
                 }
                 
                 DispatchQueue.main.async {
                     self.renderRawItems(mapView: mapView, allItems: allItems)
                 }
                 return
            }
            
            let center = mapView.camera.target
            let zoom = mapView.camera.zoom
            // Meters per pixel ~ 156543.03392 * cos(lat) / 2^zoom
            let metersPerPixel = 156543.03392 * cos(center.latitude * .pi / 180.0) / pow(2, Double(zoom))
            let wasmCellSize = metersPerPixel * 100.0 // [FIX] Restored Standard Sensitivity (100.0)
            
            // [NEW] Update Binding
            DispatchQueue.main.async {
                self.parent.clusterRadius = wasmCellSize
            }
            
            // Prepare Data
            let currentItems = parent.todoItems
            let currentLogs = parent.userLogs
            
            var allItems: [UnifiedMapItem] = []
            var rawPoints: [Int32] = []
            
            var farItemsCount = 0
            
            let userLocation = parent.locationManager.currentLocation // Define userLocation here
            // Pre-calc user int
            var uInt: (lat: Int, lon: Int)? = nil
            if let u = userLocation {
                uInt = SmartLocationManager.shared.toIntLocation(u)
            }
            
            // OptimizationLogger.shared.log(type: .launchStep, value: ">>> Pins Loaded: \(currentItems.count) Items, \(currentLogs.count) Logs")
            
            for item in currentItems {
                if let loc = item.location {
                     // Standard Path: Show All
                    allItems.append(.todo(item))
                    rawPoints.append(Int32(loc.latitude * 100_000))
                    rawPoints.append(Int32(loc.longitude * 100_000))
                }
            }
            for log in currentLogs {
                  // Standard Path: Show All
                allItems.append(.history(log))
                rawPoints.append(Int32(log.latitude * 100_000))
                rawPoints.append(Int32(log.longitude * 100_000))
            }
            
            if farItemsCount > 0 {
                DispatchQueue.main.async {
                    self.parent.onFarItemsDetected?(farItemsCount)
                }
            }
            
            // [FIX] Add User Location to Clustering Data
            if let userLoc = parent.locationManager.currentLocation {
                allItems.append(.userLocation)
                rawPoints.append(Int32(userLoc.coordinate.latitude * 100_000))
                rawPoints.append(Int32(userLoc.coordinate.longitude * 100_000))
            }
            
            Task {
                // print(">>> WASM Clustering Start")
                let start = Date()
                let result = await WasmManager.shared.cluster(points: rawPoints, cellSize: wasmCellSize)
                // print(">>> WASM Clustering Result: \(result.count/3)")
                let _ = Date().timeIntervalSince(start) * 1000
                
                await MainActor.run {
                    self.renderWasmResults(mapView: mapView, clusterResult: result, allItems: allItems)
                }
            }
        }
        
        @MainActor
        func renderWasmResults(mapView: GMSMapView, clusterResult: [Int32], allItems: [UnifiedMapItem]) {
            mapView.isMyLocationEnabled = false // [FIX] Disable native blue dot to prevent double pins
            mapView.clear() // Google Maps requires clearing to remove old markers efficiently
            
            // 1. Re-add User Location
            // 1. (Removed) User Location handled in clusters
            
            // 2. Re-add Path Overlay if exists -> REMOVED per user request
            // updatePath(mapView: mapView, selectedItems: parent.selectedClusterItems)
            
            // 3. Process Clusters
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
            
            // Buckets matching
             var clusters: [[UnifiedMapItem]] = Array(repeating: [], count: centroids.count)
             
             for item in allItems {
                 var itemLat: Double = 0
                 var itemLon: Double = 0
                 switch item {
                 case .todo(let t): if let l = t.location { itemLat = l.latitude; itemLon = l.longitude }
                 case .history(let l): itemLat = l.latitude; itemLon = l.longitude
                 case .userLocation:
                     if let userLoc = parent.locationManager.currentLocation { itemLat = userLoc.coordinate.latitude; itemLon = userLoc.coordinate.longitude }
                 default: break
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
             
             // Create Markers
             for (idx, items) in clusters.enumerated() {
                 if items.isEmpty { continue }
                 let centroid = centroids[idx]
                 let marker = WasmClusterMarker()
                 marker.position = CLLocationCoordinate2D(latitude: centroid.lat, longitude: centroid.lon)
                 marker.items = items
                 
                 // Single Logic
                 if items.count == 1, let item = items.first {
                     // Recenter to actual item loc
                     switch item {
                     case .todo(let t): if let l = t.location { marker.position = CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude) }
                     case .history(let l): marker.position = CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude)
                     default: break
                     }
                     
                     // [FIX] Explicit Anchor for Single Items (Default)
                     marker.groundAnchor = CGPoint(x: 0.5, y: 1.0)
                     
                     // Icon
                     switch item {
                     case .todo(let t):
                         let name = t.isCompleted ? "PinTodoDone" : "PinTodoReady"
                         if let img = UIImage(named: name) {
                             marker.icon = img.resized(to: CGSize(width: 40, height: 50))
                         }
                     case .history(_):
                         if let img = UIImage(named: "PinHistory") {
                             marker.icon = img.resized(to: CGSize(width: 40, height: 50))
                         }
                         else { marker.icon = PinImageHelper.shared.createShieldPin(color: .red, iconName: "clock.fill") }
                     case .serverMessage:
                          if let img = UIImage(named: "PinReceiveReady") {
                              marker.icon = img.resized(to: CGSize(width: 40, height: 50))
                          }
                     case .userLocation:
                          if let img = UIImage(named: "PinCurrent") {
                              marker.icon = img.resized(to: CGSize(width: 40, height: 50))
                          }
                     default:
                         marker.icon = PinImageHelper.shared.createShieldPin(color: .blue, iconName: "circle.fill")
                     }
                 } else {
                     // Cluster Logic
                     
                     // [FIX] Adjust Anchor for Cluster (Right Badge Overhang)
                     // Visual Center is at x=20 of total width 50 -> 0.4
                     marker.groundAnchor = CGPoint(x: 0.4, y: 1.0)
                                          
                      // [FIX] Centralized Logic
                      let (baseName, color, _) = UnifiedMapItem.resolveClusterStyle(items: items)
                     
                     
                     // [FIX] Resize Base Image FIRST to match Apple Map size (40x50)
                     let baseImage = UIImage(named: baseName)?.resized(to: CGSize(width: 40, height: 50))
                     
                     // [FIX] Draw standard badge (20pt) on top of the already-resized pin.
                     // The final image will be slightly larger due to badge overhang, but the pin part will be 40x50.
                     marker.icon = PinImageHelper.shared.createShieldPin(color: color, count: items.count, baseImage: baseImage)
                 }
                 
                 marker.map = mapView
             }
        }
        


        // MARK: - Animation
        func performLaunchAnimation(mapView: GMSMapView, userLocation: CLLocation?) {
             guard let userLoc = userLocation else { return }
             // [FIX] Keep firstRender = true during animation
             // firstRender = false
             isAnimating = true
            
            // [FIX] Ensure Data is Fresh on Launch/Resume
            refreshWasmClusters(mapView: mapView)
            
             // 3. Fit Bounds (Dynamic)
             // 3. Fit Bounds (Dynamically Filtered)
             var bounds = GMSCoordinateBounds(coordinate: userLoc.coordinate, coordinate: userLoc.coordinate)
             let uLat = Int(userLoc.coordinate.latitude * 100_000)
             let uLon = Int(userLoc.coordinate.longitude * 100_000)
             
             for item in parent.todoItems { 
                 if let l = item.location {
                     if SmartLocationManager.shared.isFar(lat1: uLat, lon1: uLon, lat2: item.latInt, lon2: item.lonInt) { continue }
                     bounds = bounds.includingCoordinate(l) 
                 } 
             }
             for log in parent.userLogs {
                 if SmartLocationManager.shared.isFar(lat1: uLat, lon1: uLon, lat2: log.latInt, lon2: log.lonInt) { continue }
                 bounds = bounds.includingCoordinate(CLLocationCoordinate2D(latitude: log.latitude, longitude: log.longitude)) 
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
                  CATransaction.setValue(1.0, forKey: kCATransactionAnimationDuration) // Slower 1.0s
                  mapView.animate(with: midCam)
                  CATransaction.commit()
                  self.isAnimating = false
                  
                  OptimizationLogger.shared.log(type: .launchStep, value: ">>> Current Location: \(userLoc.coordinate)")
                  
                  // Trigger Cluster (WASM ON)
                  self.firstRender = false
                  self.refreshWasmClusters(mapView: mapView)
              }
        }
        
        // MARK: - Path
        func updatePath(mapView: GMSMapView, selectedItems: [UnifiedMapItem]?) {
             mapView.clear() // Google Maps clear actually removes polylines too
             
             // 2. Filter history items
             guard let items = selectedItems else { return }
             let historyLogs = items.compactMap { item -> ToDoItem? in
                  if case .history(let log) = item { return log }
                  return nil
             }
             
             guard let log = historyLogs.first else { return }
             
             // 3. Query PathItems
             let searchID = log.todo_id
             let descriptor = FetchDescriptor<PathItem>(predicate: #Predicate<PathItem> { $0.todo_id == searchID })
             if let paths = try? parent.modelContext.fetch(descriptor) {
                 let path = GMSMutablePath()
                 for p in paths {
                     path.add(p.coordinate)
                 }
                 if path.count() >= 2 {
                     let polyline = GMSPolyline(path: path)
                     polyline.strokeColor = .red
                     polyline.strokeWidth = 4
                     polyline.map = mapView
                 }
             }
        }
    }
}

// Marker Subclass
class WasmClusterMarker: GMSMarker {
    var items: [UnifiedMapItem] = []
}
