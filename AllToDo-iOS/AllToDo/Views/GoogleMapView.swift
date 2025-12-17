import SwiftUI
import GoogleMaps
import CoreLocation

struct GoogleMapView: UIViewRepresentable {
    @Binding var action: MapAction
    @Binding var rotation: Double
    @ObservedObject var locationManager: AppLocationManager
    
    var todoItems: [ToDoItem]
    var userLogs: [UserLog]
    
    @Binding var selectedItem: ToDoItem?
    @Binding var selectedClusterItems: [UnifiedMapItem]?
    var hasItems: Bool
    
    // Actions
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onUserLocationTap: (() -> Void)?
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((UserLog) -> Void)?
    var onSelectLog: ((UserLog) -> Void)?
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
        if !context.coordinator.firstRender {
             context.coordinator.refreshWasmClusters(mapView: uiView)
        }
        
        // Launch Animation
        if context.coordinator.firstRender {
            context.coordinator.performLaunchAnimation(mapView: uiView, userLocation: locationManager.currentLocation)
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
        
        // MARK: - Delegate Methods
        func mapView(_ mapView: GMSMapView, didChange position: GMSCameraPosition) {
             DispatchQueue.main.async {
                 self.parent.rotation = position.bearing
             }
        }
        
        func mapView(_ mapView: GMSMapView, idleAt position: GMSCameraPosition) {
            // Trigger WASM Clustering on Idle (Region Change End)
            refreshWasmClusters(mapView: mapView)
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
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            
            // Handle Marker Tap
            if let custom = marker as? WasmClusterMarker {
                DispatchQueue.main.async {
                    // [FIX] Distinguish Single Todo vs Cluster
                    if custom.items.count == 1, let first = custom.items.first {
                        switch first {
                        case .todo(let item):
                            self.parent.selectedItem = item
                            self.parent.selectedClusterItems = nil
                        default:
                            self.parent.selectedClusterItems = custom.items
                            self.parent.selectedItem = nil
                        }
                    } else {
                        self.parent.selectedClusterItems = custom.items
                        self.parent.selectedItem = nil
                    }
                }
                return true
            }
            
            return false // Default behavior
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
                widthPixels = UIScreen.main.bounds.width
            }
            
            // guard widthPixels > 0 else { return } // Removed guard
            
            // [OPTIMIZATION] Fast Path
            let totalCount = parent.todoItems.count + parent.userLogs.count
            let isLaunchPhase = parent.action == .launchSequence || firstRender
            let useFastPath = isLaunchPhase && totalCount < 50
            
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
                         if let u = uInt, SmartLocationManager.shared.isFar(lat1: u.lat, lon1: u.lon, lat2: loc.latInt, lon2: loc.lonInt) {
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
            let wasmCellSize = metersPerPixel * 70.0 // User requested 70.0
            
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
            
            OptimizationLogger.shared.log(type: .launchStep, value: ">>> Pins Loaded: \(currentItems.count) Items, \(currentLogs.count) Logs")
            
            for item in currentItems {
                if let loc = item.location {
                     // 500km Filter Restored (Integer)
                     if let u = uInt, SmartLocationManager.shared.isFar(lat1: u.lat, lon1: u.lon, lat2: loc.latInt, lon2: loc.lonInt) {
                         farItemsCount += 1
                         continue
                     }
                    allItems.append(.todo(item))
                    rawPoints.append(Int32(loc.latitude * 1_000_000))
                    rawPoints.append(Int32(loc.longitude * 1_000_000))
                }
            }
            for log in currentLogs {
                  // 500km Filter Restored (Integer)
                  if let u = uInt, SmartLocationManager.shared.isFar(lat1: u.lat, lon1: u.lon, lat2: log.latInt, lon2: log.lonInt) {
                      farItemsCount += 1
                      continue
                  }
                allItems.append(.history(log))
                rawPoints.append(Int32(log.latitude * 1_000_000))
                rawPoints.append(Int32(log.longitude * 1_000_000))
            }
            
            if farItemsCount > 0 {
                DispatchQueue.main.async {
                    self.parent.onFarItemsDetected?(farItemsCount)
                }
            }
            
            // [FIX] Add User Location to Clustering Data
            if let userLoc = parent.locationManager.currentLocation {
                allItems.append(.userLocation)
                rawPoints.append(Int32(userLoc.coordinate.latitude * 1_000_000))
                rawPoints.append(Int32(userLoc.coordinate.longitude * 1_000_000))
            }
            
            Task {
                let start = Date()
                let result = await WasmManager.shared.cluster(points: rawPoints, cellSize: wasmCellSize)
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
            
            // 2. Re-add Path Overlay if exists
            updatePath(mapView: mapView, selectedItems: parent.selectedClusterItems)
            
            // 3. Process Clusters
             struct Centroid { let lat: Double; let lon: Double; let count: Int }
             var centroids: [Centroid] = []
             if clusterResult.count % 3 == 0 {
                 for i in stride(from: 0, to: clusterResult.count, by: 3) {
                     let lat = Double(clusterResult[i]) / 1_000_000.0
                     let lon = Double(clusterResult[i+1]) / 1_000_000.0
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
                     
                     var userLocationFound = false
                     var historyCount = 0
                     var todoReadyCount = 0
                     var todoDoneCount = 0
                     var messageCount = 0
                     
                     for i in items {
                         switch i {
                         case .userLocation: userLocationFound = true
                         case .history: historyCount += 1
                         case .todo(let t):
                             if t.isCompleted { todoDoneCount += 1 }
                             else { todoReadyCount += 1 }
                         case .serverMessage: messageCount += 1
                         }
                     }
                     
                     var baseName = "PinTodoReady"
                     if userLocationFound {
                         baseName = "PinCurrent"
                     } else {
                         let counts = [
                             ("PinHistory", historyCount),
                             ("PinTodoReady", todoReadyCount),
                             ("PinTodoDone", todoDoneCount),
                             ("PinReceiveReady", messageCount)
                         ]
                         if let max = counts.max(by: { $0.1 < $1.1 }), max.1 > 0 {
                             baseName = max.0
                         }
                     }
                     
                     let color: UIColor
                     if baseName == "PinHistory" { color = .red }
                     else if baseName == "PinReceiveReady" { color = .blue }
                     else { color = UIColor(red: 0.2, green: 0.8, blue: 0.2, alpha: 1.0) }
                     
                     
                     // [FIX] Resize Base Image FIRST to match Apple Map size (40x50)
                     let baseImage = UIImage(named: baseName)?.resized(to: CGSize(width: 40, height: 50))
                     
                     // [FIX] Draw standard badge (20pt) on top of the already-resized pin.
                     // The final image will be slightly larger due to badge overhang, but the pin part will be 40x50.
                     marker.icon = PinImageHelper.shared.createShieldPin(color: color, count: items.count, baseImage: baseImage)
                 }
                 
                 marker.map = mapView
             }
        }
        
        // [NEW] Raw Renderer for Google Maps
        func renderRawItems(mapView: GMSMapView, allItems: [UnifiedMapItem]) {
            mapView.clear()
            for item in allItems {
                let marker = WasmClusterMarker() // Reuse for convenience or GMSMarker
                marker.items = [item] // Wrap as single item cluster
                // Set position...
                switch item {
                 case .todo(let t): if let l = t.location { marker.position = CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude) }
                 case .history(let l): marker.position = CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude)
                 case .userLocation: if let u = parent.locationManager.currentLocation { marker.position = u.coordinate }
                 default: break
                }
                
                // Set Icon (Simplified for Fast Path)
                marker.icon = UIImage(named: "PinTodoReady")?.resized(to: CGSize(width: 40, height: 50))
                if case .userLocation = item { marker.icon = UIImage(named: "PinCurrent")?.resized(to: CGSize(width: 40, height: 50)) }
                
                marker.map = mapView
            }
        }

        // MARK: - Animation
        func performLaunchAnimation(mapView: GMSMapView, userLocation: CLLocation?) {
             guard let userLoc = userLocation else { return }
             firstRender = false
             isAnimating = true
            
            // 2. Refresh & Fast Path
            refreshWasmClusters(mapView: mapView)
            
             // 3. Fit Bounds (Dynamic)
             let bounds = GMSCoordinateBounds()
             bounds.includingCoordinate(userLoc.coordinate)
             for item in parent.todoItems { if let l = item.location { bounds.includingCoordinate(CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude)) } }
             for log in parent.userLogs { bounds.includingCoordinate(CLLocationCoordinate2D(latitude: log.latitude, longitude: log.longitude)) }
            
             // Apply Fit with Padding
             let update = GMSCameraUpdate.fit(bounds, withPadding: 50.0)
             mapView.animate(with: update)

                // Launch Animation (Wait 3s -> Zoom User)
                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) { [weak self] in
                    guard let self = self else { return }
                  let midCam = GMSCameraUpdate.setTarget(userLoc.coordinate, zoom: 15)
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
             // [FIX] Disabled Path Drawing as per user request
             return
        }
    }
}

// Marker Subclass
class WasmClusterMarker: GMSMarker {
    var items: [UnifiedMapItem] = []
}
