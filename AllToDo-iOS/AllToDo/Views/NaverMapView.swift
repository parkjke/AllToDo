import SwiftUI
import NMapsMap
import CoreLocation
import SwiftData

struct NaverMapView: UIViewRepresentable {
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
    @Binding var creatingTodoLocation: CLLocationCoordinate2D? // [NEW]
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onUserLocationTap: (() -> Void)?
    
    // Callbacks
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((ToDoItem) -> Void)?
    var onSelectLog: ((ToDoItem) -> Void)?
    var onSelectItem: ((ToDoItem) -> Void)?
    var onFarItemsDetected: ((Int) -> Void)? // [NEW] Callback

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
        
        // [FIX] Initial Camera Calculation (User Location > Pins Centroid > Gwanghwamun)
        var initialTarget = NMGLatLng(lat: 37.5759, lng: 126.9768) // Default Gwanghwamun
        
        if let userLoc = locationManager.currentLocation {
            initialTarget = NMGLatLng(lat: userLoc.coordinate.latitude, lng: userLoc.coordinate.longitude)
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
                initialTarget = NMGLatLng(lat: latSum / count, lng: lonSum / count)
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
            let currentSummary = "\(todoItems.count)-\(todoItems.first?.todo_id.uuidString ?? "")-\(userLogs.count)-\(userLogs.first?.todo_id.uuidString ?? "")"
            if context.coordinator.lastDataSummary != currentSummary {
                context.coordinator.lastDataSummary = currentSummary
                context.coordinator.refreshWasmClusters()
            }
            
            // [NEW] Check Tethering (Conditional)
            if let u = locationManager.currentLocation, !context.coordinator.firstRender, !context.coordinator.isLaunchAnimating {
                context.coordinator.checkTethering(mapView: uiView.mapView, userLocation: u)
            }
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
        
        init(_ parent: NaverMapView) {
            self.parent = parent
        }
        
        // ... (Actions omitted) ...

        // [NEW] Raw Renderer for Naver
        func renderRawItems(mapView: NMFMapView, allItems: [UnifiedMapItem]) {
            markers.forEach { $0.mapView = nil }; markers = []
            
            for item in allItems {
                let marker = NMFMarker()
                
                // 1. Position
                switch item {
                case .todo(let t): if let l = t.location { marker.position = NMGLatLng(lat: l.latitude, lng: l.longitude) }
                case .history(let l): marker.position = NMGLatLng(lat: l.latitude, lng: l.longitude)
                case .userLocation: if let u = parent.locationManager.currentLocation { marker.position = NMGLatLng(lat: u.coordinate.latitude, lng: u.coordinate.longitude) }
                default: break
                }
                
                // 2. Icon Type
                var name = "PinTodoReady" // Default Green
                
                switch item {
                case .todo(let t):
                    if t.isCompleted { name = "PinTodoDone" }
                    // Source check not available on ToDoItem currently
                    // if t.source != "local" { name = "PinReceiveReady" }
                    
                case .history:
                    name = "PinHistory" // Red
                case .userLocation:
                    name = "PinCurrent" // Red
                case .serverMessage:
                    name = "PinReceiveReady" // Blue
                }
                
                // [NEW] Check if it's the creating todo pin
                if let target = parent.creatingTodoLocation, 
                   abs(marker.position.lat - target.latitude) < 0.00001 && abs(marker.position.lng - target.longitude) < 0.00001 {
                    name = "PinTodoReady" // Green for new entry
                }
                
                let img = UIImage(named: name)?.resized(to: CGSize(width: 48, height: 60))
                if let i = img { 
                    marker.iconImage = NMFOverlayImage(image: i) 
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
        func updatePath(selectedItems: [UnifiedMapItem]?) {
             guard let map = mapView, let items = selectedItems else { return }
             
             // 1. Remove existing
             pathOverlay?.mapView = nil
             pathOverlay = nil
             
             // 2. Filter history items (Using ToDoItem)
             guard let items = selectedItems else { return }
             let historyItems = items.compactMap { item -> ToDoItem? in
                 if case .history(let log) = item { return log }
                 return nil
             }
             
             guard let log = historyItems.first else { return }
             
             // 3. Query PathItems
             let idString = log.todo_id.uuidString
             let searchID = UUID(uuidString: idString) ?? log.todo_id
             let descriptor = FetchDescriptor<PathItem>(predicate: #Predicate<PathItem> { path in
                 path.todo_id == searchID
             })
             if let paths = try? parent.modelContext.fetch(descriptor) {
                 let coords = paths.map { NMGLatLng(lat: $0.coordinate.latitude, lng: $0.coordinate.longitude) }
                 if coords.count >= 2 {
                     let path = NMFPath()
                     path.path = NMGLineString(points: coords)
                     path.color = .red
                     path.width = 4
                     path.outlineWidth = 0
                     path.mapView = map
                     self.pathOverlay = path
                 }
             }
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
            let total = parent.todoItems.count + parent.userLogs.count
            // If launching, render raw regardless of count
            // Naver uses 'firstRender' flag in Coordinator.
            // [CRITICAL LOCK: DO NOT MODIFY] Raw First -> Cluster Strategy
            if firstRender {
                 OptimizationLogger.shared.log(type: .launchStep, value: ">>> Fast Path (Naver): Raw Render")
                 
                // Pre-calc user int
                var uInt: (lat: Int, lon: Int)? = nil
                if let u = parent.locationManager.currentLocation {
                    uInt = SmartLocationManager.shared.toIntLocation(u)
                }
                 
                 var allItems: [UnifiedMapItem] = []
                 var farCount = 0
                 
                 for item in parent.todoItems {
                     if let loc = item.location {
                         // 500km Filter (Integer)
                         if let u = uInt, SmartLocationManager.shared.isFar(lat1: u.lat, lon1: u.lon, lat2: item.latInt, lon2: item.lonInt) {
                             farCount += 1
                             continue
                         }
                         allItems.append(.todo(item))
                     }
                 }
                 for log in parent.userLogs {
                     // 500km Filter (Integer)
                      if let u = uInt, SmartLocationManager.shared.isFar(lat1: u.lat, lon1: u.lon, lat2: log.latInt, lon2: log.lonInt) {
                          farCount += 1
                          continue
                      }
                     allItems.append(.history(log))
                 }
                 if let u = parent.locationManager.currentLocation { allItems.append(.userLocation) }
                 
                 // [NEW] Add Creating Todo Location
                 if let target = creatingTodoLocationBinding?.wrappedValue {
                     allItems.append(.todo(ToDoItem(todo_name: "New Entry", latitude: target.latitude, longitude: target.longitude)))
                 }
                 
                 // Notify
                 if farCount > 0 {
                     DispatchQueue.main.async { self.parent.onFarItemsDetected?(farCount) }
                 }
                 
                 DispatchQueue.main.async {
                     self.renderRawItems(mapView: map, allItems: allItems)
                 }
                 return
            }
            
            let zoom = map.zoomLevel
            let centerLat = map.cameraPosition.target.lat
            
            // Meter/Pixel Calc: 156543.03392 * cos(lat) / 2^zoom
            let metersPerPixel = 156543.03392 * cos(centerLat * .pi / 180.0) / pow(2, zoom)
            let wasmCellSize = metersPerPixel * 100.0 // [FIX] Restored Standard Sensitivity (100.0)
            
            // [NEW] Update Binding
            DispatchQueue.main.async {
                self.parent.clusterRadius = wasmCellSize
            }
            
            // Prepare Data
            let currentItems = parent.todoItems
            let currentLogs = parent.userLogs
            let userLocation = parent.locationManager.currentLocation
            
            var allItems: [UnifiedMapItem] = []
            var rawPoints: [Int32] = []
            
            var farItemsCount = 0
            
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
                    self.onFarItemsDetected?(farItemsCount)
                }
            }
            
            // [FIX] Include User Location in Clustering
            if let userLoc = userLocation {
                allItems.append(.userLocation)
                rawPoints.append(Int32(userLoc.coordinate.latitude * 100_000))
                rawPoints.append(Int32(userLoc.coordinate.longitude * 100_000))
            }
            
            // [NEW] Add Creating Todo Location if active
            if let target = creatingTodoLocationBinding?.wrappedValue {
                allItems.append(.todo(ToDoItem(todo_name: "New Entry", latitude: target.latitude, longitude: target.longitude)))
                rawPoints.append(Int32(target.latitude * 100_000))
                rawPoints.append(Int32(target.longitude * 100_000))
            }
            
            Task {
                // print(">>> WASM Clustering Start")
                let result = await WasmManager.shared.cluster(points: rawPoints, cellSize: wasmCellSize)
                // print(">>> WASM Clustering Result: \(result.count/3)")
                
                await MainActor.run {
                    self.renderWasmResults(mapView: map, clusterResult: result, allItems: allItems)
                }
            }
        }
        
        @MainActor
        func renderWasmResults(mapView: NMFMapView, clusterResult: [Int32], allItems: [UnifiedMapItem]) {
            // [FIX] Disable Native Location Overlay (Blue Dot)
            mapView.locationOverlay.hidden = true
            
            // Clear Old Markers
            markers.forEach { $0.mapView = nil }
            markers = []
            
            // Parse Buckets (Simple Assignment)
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
                case .userLocation: 
                    if let u = parent.locationManager.currentLocation { itemLat = u.coordinate.latitude; itemLon = u.coordinate.longitude }
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
                guard !items.isEmpty else { continue }
                let centroid = centroids[idx]
                let marker = NMFMarker()
                
                // Position
                if items.count == 1 {
                    let item = items[0]
                    switch item {
                    case .todo(let t): if let l = t.location { marker.position = NMGLatLng(lat: l.latitude, lng: l.longitude) }
                    case .history(let l): marker.position = NMGLatLng(lat: l.latitude, lng: l.longitude)
                    case .userLocation: if let u = parent.locationManager.currentLocation { marker.position = NMGLatLng(lat: u.coordinate.latitude, lng: u.coordinate.longitude) }
                    default: marker.position = NMGLatLng(lat: centroid.lat, lng: centroid.lon)
                    }
                } else {
                    marker.position = NMGLatLng(lat: centroid.lat, lng: centroid.lon)
                }
                
                
                // [FIX] Centralized Logic
                let (baseName, color, _) = UnifiedMapItem.resolveClusterStyle(items: items)
                
                // [FIX] Resize Base Image FIRST to 48x60 (Naver Special)
                let baseImage = UIImage(named: baseName)?.resized(to: CGSize(width: 48, height: 60))
                
                // [FIX] Overlay Badge ONLY if count > 1
                let displayCount: Int? = items.count > 1 ? items.count : nil
                let finalImage = PinImageHelper.shared.createShieldPin(color: color, count: displayCount, baseImage: baseImage)
                
                marker.iconImage = NMFOverlayImage(image: finalImage)
                marker.anchor = CGPoint(x: 0.5, y: 1.0) 
                
                // Interaction
                marker.touchHandler = { [weak self] (overlay: NMFOverlay) -> Bool in
                    guard let self = self, let marker = overlay as? NMFMarker else { return false }
                    
                    // [NEW] Auto-Center Logic
                    let generator = UIImpactFeedbackGenerator(style: .medium)
                    generator.impactOccurred()
                    
                    // 1. Store Pending Selection
                    self.pendingSelection = (items, marker.position)
                    
                    // 2. Animate to Center
                    let update = NMFCameraUpdate(scrollTo: marker.position)
                    update.animation = .fly
                    update.animationDuration = 0.3
                    self.mapView?.moveCamera(update)
                    
                    return true
                }
                
                marker.mapView = mapView
                markers.append(marker)
                markers.append(marker)
            }
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
             for item in parent.todoItems {
                 if let l = item.location {
                     minLat = min(minLat, l.latitude)
                     maxLat = max(maxLat, l.latitude)
                     minLon = min(minLon, l.longitude)
                     maxLon = max(maxLon, l.longitude)
                     hasPins = true
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
