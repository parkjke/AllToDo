import SwiftUI
import NMapsMap
import CoreLocation

struct NaverMapView: UIViewRepresentable {
    @Binding var action: MapAction
    @Binding var rotation: Double
    @ObservedObject var locationManager: AppLocationManager
    var todoItems: [ToDoItem]
    var userLogs: [UserLog]
    @Binding var selectedItem: ToDoItem?
    @Binding var selectedClusterItems: [UnifiedMapItem]?
    @Binding var tapPosition: CGPoint? // [NEW]
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onUserLocationTap: (() -> Void)?
    
    // Callbacks
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((UserLog) -> Void)?
    var onSelectLog: ((UserLog) -> Void)?
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
        
        // 1. Handle Actions
        if action != .none {
            context.coordinator.handleAction(action)
            DispatchQueue.main.async {
                action = .none
            }
        }
        
        // 2. Trigger Clustering (Only if not in first render sequence)
        if !context.coordinator.firstRender {
            context.coordinator.refreshWasmClusters()
            
            
            // [NEW] Check Tethering
            if let u = locationManager.currentLocation {
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
        var onFarItemsDetected: ((Int) -> Void)?
        
        var markers: [NMFMarker] = []
        // var pathOverlay: NMFPath? // Disabled
        
        init(_ parent: NaverMapView) {
            self.parent = parent
        }
        
        // [NEW] Check Tethering
        func checkTethering(mapView: NMFMapView, userLocation: CLLocation) {
            let target = mapView.cameraPosition.target
            let mapCenter = SmartLocationManager.shared.toIntLocation(CLLocation(latitude: target.lat, longitude: target.lng))
            let userInt = SmartLocationManager.shared.toIntLocation(userLocation)
            
            if SmartLocationManager.shared.needsCentering(user: userInt, center: mapCenter, spanLon: currentSpanLon) {
                let update = NMFCameraUpdate(scrollTo: NMGLatLng(lat: userLocation.coordinate.latitude, lng: userLocation.coordinate.longitude))
                update.animation = .linear
                mapView.moveCamera(update)
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
             // Naver doesn't use removeOverlays generic, we need to track it
             // Let's use a private property if possible or search.
             // For now, removing all NMFPath objects if possible.
             
             // 2. Filter history items with pathData
             let historyItems = items.compactMap { item -> UserLog? in
                 if case .history(let log) = item, log.pathData != nil { return log }
                 return nil
             }
             
             guard let log = historyItems.first, let data = log.pathData else { return }
             
             // 3. Decode & Draw
             if let points = try? JSONDecoder().decode([LocationData].self, from: data) {
                 let coords = points.map { NMGLatLng(lat: $0.latitude, lng: $0.longitude) }
                 if coords.count >= 2 {
                     let path = NMFPath()
                     path.coords = coords
                     path.color = .red
                     path.width = 4
                     path.outlineWidth = 0
                     path.mapView = map
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
                widthPixels = UIScreen.main.bounds.width
            }
            // [OPTIMIZATION] Fast Path
            let total = parent.todoItems.count + parent.userLogs.count
            // If launching and small data, render raw
            // Naver uses 'firstRender' flag in Coordinator.
            if firstRender && total < 50 {
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
                         if let u = uInt, SmartLocationManager.shared.isFar(lat1: u.lat, lon1: u.lon, lat2: loc.latInt, lon2: loc.lonInt) {
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
            let wasmCellSize = metersPerPixel * 70.0 // [FIX] Sync with Apple Map (70.0)
            
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
            
            OptimizationLogger.shared.log(type: .launchStep, value: ">>> Pins Loaded: \(currentItems.count) Items, \(currentLogs.count) Logs")
            
            for item in currentItems {
                if let loc = item.location {
                     // Standard Path: Show All
                    allItems.append(.todo(item))
                    rawPoints.append(Int32(loc.latitude * 1_000_000))
                    rawPoints.append(Int32(loc.longitude * 1_000_000))
                }
            }
            for log in currentLogs {
                  // Standard Path: Show All
                allItems.append(.history(log))
                rawPoints.append(Int32(log.latitude * 1_000_000))
                rawPoints.append(Int32(log.longitude * 1_000_000))
            }
            
            if farItemsCount > 0 {
                DispatchQueue.main.async {
                    self.onFarItemsDetected?(farItemsCount)
                }
            }
            // [FIX] Include User Location in Clustering
            if let userLoc = userLocation {
                allItems.append(.userLocation)
                rawPoints.append(Int32(userLoc.coordinate.latitude * 1_000_000))
                rawPoints.append(Int32(userLoc.coordinate.longitude * 1_000_000))
            }
            
            Task {
                let result = await WasmManager.shared.cluster(points: rawPoints, cellSize: wasmCellSize)
                
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
                    let lat = Double(clusterResult[i]) / 1_000_000.0
                    let lon = Double(clusterResult[i+1]) / 1_000_000.0
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
                
                // [FIX] Logic for Base Image & Badge (Same as Apple/Google)
                var userLocationFound = false
                var historyCount = 0
                var todoReadyCount = 0
                var todoDoneCount = 0
                var messageCount = 0
                
                for item in items {
                    switch item {
                    case .userLocation: userLocationFound = true
                    case .history: historyCount += 1
                    case .todo(let t): if t.isCompleted { todoDoneCount += 1 } else { todoReadyCount += 1 }
                    case .serverMessage: messageCount += 1
                    }
                }
                
                var baseName = "PinTodoReady"
                if userLocationFound {
                    baseName = "PinCurrent"
                } else {
                    let counts = [("PinHistory", historyCount), ("PinTodoReady", todoReadyCount), ("PinTodoDone", todoDoneCount), ("PinReceiveReady", messageCount)]
                    if let max = counts.max(by: { $0.1 < $1.1 }), max.1 > 0 { baseName = max.0 }
                }
                
                let color: UIColor
                if baseName == "PinHistory" { color = .red }
                else if baseName == "PinReceiveReady" { color = .blue }
                else { color = UIColor(red: 0.2, green: 0.8, blue: 0.2, alpha: 1.0) }
                
                // [FIX] Resize Base Image FIRST to 40x50
                let baseImage = UIImage(named: baseName)?.resized(to: CGSize(width: 40, height: 50))
                
                // [FIX] Overlay Badge ONLY if count > 1
                let displayCount: Int? = items.count > 1 ? items.count : nil
                let finalImage = PinImageHelper.shared.createShieldPin(color: color, count: displayCount, baseImage: baseImage)
                
                marker.iconImage = NMFOverlayImage(image: finalImage)
                marker.anchor = CGPoint(x: 0.5, y: 1.0) 
                
                // Interaction
                marker.touchHandler = { [weak self] (overlay: NMFOverlay) -> Bool in
                    let generator = UIImpactFeedbackGenerator(style: .medium)
                    generator.impactOccurred()
                    
                    DispatchQueue.main.async {
                        // [NEW] Update tapPosition
                        if let map = self?.mapView {
                            let point = map.projection.point(from: marker.position)
                            self?.parent.tapPosition = point
                        }
                        
                        // [FIX] Distinguish Single Todo vs Cluster
                        if items.count == 1, let first = items.first {
                            switch first {
                            case .todo(let item):
                                self?.parent.selectedItem = item
                                self?.parent.selectedClusterItems = nil
                            default:
                                self?.parent.selectedClusterItems = items
                                self?.parent.selectedItem = nil
                            }
                        } else {
                            self?.parent.selectedClusterItems = items
                            self?.parent.selectedItem = nil
                        }
                    }
                    return true
                }
                
                marker.mapView = mapView
                markers.append(marker)
                markers.append(marker)
            }
        }
        
        // [NEW] Raw Renderer for Naver
        func renderRawItems(mapView: NMFMapView, allItems: [UnifiedMapItem]) {
            markers.forEach { $0.mapView = nil }; markers = []
            
            for item in allItems {
                let marker = NMFMarker()
                switch item {
                case .todo(let t): if let l = t.location { marker.position = NMGLatLng(lat: l.latitude, lng: l.longitude) }
                case .history(let l): marker.position = NMGLatLng(lat: l.latitude, lng: l.longitude)
                case .userLocation: if let u = parent.locationManager.currentLocation { marker.position = NMGLatLng(lat: u.coordinate.latitude, lng: u.coordinate.longitude) }
                default: break
                }
                
                // Simple Icon
                let name = "PinTodoReady"
                let img = UIImage(named: name)?.resized(to: CGSize(width: 40, height: 50))
                if let i = img { marker.iconImage = NMFOverlayImage(image: i) }
                
                marker.mapView = mapView
                markers.append(marker)
            }
        }

        func performLaunchAnimation(userLocation: CLLocation?) {
            guard let loc = userLocation, let map = mapView else { return }
            
            // Step 1: Fit Bounds (pins within 500km)
            var bounds = NMGLatLngBounds(southWest: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude),
                                         northEast: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude))
            
            let uLat = Int(loc.coordinate.latitude * 100_000)
            let uLon = Int(loc.coordinate.longitude * 100_000)
            
            var hasPins = false
            for item in parent.todoItems { 
                if let l = item.location {
                     if SmartLocationManager.shared.isFar(lat1: uLat, lon1: uLon, lat2: l.latInt, lon2: l.lonInt) { continue }
                    bounds = bounds.expand(toPoint: NMGLatLng(lat: l.latitude, lng: l.longitude)) 
                    hasPins = true
                } 
            }
            for log in parent.userLogs {
                 if SmartLocationManager.shared.isFar(lat1: uLat, lon1: uLon, lat2: log.latInt, lon2: log.lonInt) { continue }
                bounds = bounds.expand(toPoint: NMGLatLng(lat: log.latitude, lng: log.longitude)) 
                hasPins = true
            }
            
            if hasPins {
                let cameraUpdate = NMFCameraUpdate(fit: bounds, paddingInsets: UIEdgeInsets(top: 100, left: 50, bottom: 100, right: 50))
                cameraUpdate.animation = .easeOut
                cameraUpdate.animationDuration = 1.0
                map.moveCamera(cameraUpdate)
            }
            
            // Step 2: Immediate Pin Display (Fast Path)
            self.refreshWasmClusters()
            
            // Step 3: Wait 3s -> Zoom 18 at Current Location
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                let end = NMFCameraUpdate(scrollTo: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude), zoomTo: 18)
                end.animation = .fly
                end.animationDuration = 1.0
                map.moveCamera(end)
                
                OptimizationLogger.shared.log(type: .launchStep, value: ">>> Current Location Zoom 18: \(loc.coordinate)")
                
                self.firstRender = false
                self.refreshWasmClusters() // Disable 500km filter internally by setting firstRender = false
            }
        }
        
        // MARK: - Delegate Methods
        func mapView(_ mapView: NMFMapView, didTapMap latlng: NMGLatLng, point: CGPoint) {
            DispatchQueue.main.async {
                self.parent.selectedItem = nil
                self.parent.selectedClusterItems = nil
            }
        }
        
        func mapView(_ mapView: NMFMapView, didLongTapMap latlng: NMGLatLng, point: CGPoint) {
            let coord = CLLocationCoordinate2D(latitude: latlng.lat, longitude: latlng.lng)
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            DispatchQueue.main.async {
                self.parent.onLongTap?(coord)
            }
        }
        
        // [NEW] Tethering State
        var currentSpanLon: Int = 0

        func mapView(_ mapView: NMFMapView, cameraDidChangeByReason reason: Int, animated: Bool) {
             let heading = mapView.cameraPosition.heading
             DispatchQueue.main.async {
                 self.parent.rotation = heading
                 
                 // [NEW] Update Span
                 let bounds = mapView.contentBounds
                 // contentBounds returns NMGLatLngBounds
                 let span = bounds.northEastLng - bounds.southWestLng
                 self.currentSpanLon = Int(abs(span) * 100_000.0)
                 
                 // Trigger Re-clustering on movement (Inside Dispatch)
                 self.refreshWasmClusters()
             }
        }
    }
}
