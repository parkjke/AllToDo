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
        
        OptimizationLogger.shared.log(type: .launchStep, value: ">>> Map Ready")
        
        let update = NMFCameraUpdate(scrollTo: initialTarget, zoomTo: 16)
        update.animation = .none
        view.mapView.moveCamera(update)
        
        // Initial Delegate Call
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
        }
        
        // 3. Launch Animation
        if context.coordinator.firstRender {
             context.coordinator.performLaunchAnimation(userLocation: locationManager.currentLocation)
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
             // [FIX] Disabled Path Drawing as per user request
             return
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
                 var allItems: [UnifiedMapItem] = []
                 for item in parent.todoItems { if item.location != nil { allItems.append(.todo(item)) } }
                 for log in parent.userLogs { allItems.append(.history(log)) }
                 if let u = parent.locationManager.currentLocation { allItems.append(.userLocation) }
                 
                 DispatchQueue.main.async {
                     self.renderRawItems(mapView: map, allItems: allItems)
                 }
                 return
            }
            
            let zoom = map.zoomLevel
            let centerLat = map.cameraPosition.target.lat
            
            // Meter/Pixel Calc: 156543.03392 * cos(lat) / 2^zoom
            let metersPerPixel = 156543.03392 * cos(centerLat * .pi / 180.0) / pow(2, zoom)
            let wasmCellSize = metersPerPixel * 100.0 // [FIX] Increased to 100.0 for Naver Map to ensure overlapping pins merge
            
            // Prepare Data
            let currentItems = parent.todoItems
            let currentLogs = parent.userLogs
            let userLocation = parent.locationManager.currentLocation
            
            var allItems: [UnifiedMapItem] = []
            var rawPoints: [Int32] = []
            
            var farItemsCount = 0
            OptimizationLogger.shared.log(type: .launchStep, value: ">>> Pins Loaded: \(currentItems.count) Items, \(currentLogs.count) Logs")
            
            for item in currentItems {
                if let loc = item.location {
                     // 500km Filter removed
                     /*if let u = parent.locationManager.currentLocation, SmartLocationManager.shared.isFar(u, CLLocation(latitude: loc.latitude, longitude: loc.longitude)) {
                         farItemsCount += 1
                         continue
                     }*/
                    allItems.append(.todo(item))
                    rawPoints.append(Int32(loc.latitude * 1_000_000))
                    rawPoints.append(Int32(loc.longitude * 1_000_000))
                }
            }
            for log in currentLogs {
                  // 500km Filter removed
                  /*if let u = parent.locationManager.currentLocation, SmartLocationManager.shared.isFar(u, CLLocation(latitude: log.latitude, longitude: log.longitude)) {
                      farItemsCount += 1
                      continue
                  }*/
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
            firstRender = false
            
            // 1. Refresh & Fast Path (Pins appear immediately)
            refreshWasmClusters()
            
            // 2. Fit Bounds (Dynamic)
            // Instead of Zoom 5 (Korea), calculate actual bounds of pins
            var bounds = NMGLatLngBounds(southWest: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude),
                                         northEast: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude))
            
            // Expand to include visible items
            for item in parent.todoItems { if let l = item.location { bounds = bounds.expand(to: NMGLatLng(lat: l.latitude, lng: l.longitude)) } }
            for log in parent.userLogs { bounds = bounds.expand(to: NMGLatLng(lat: log.latitude, lng: log.longitude)) }
            
            // Apply Fit Bounds with Padding
            let cameraUpdate = NMFCameraUpdate(fit: bounds, paddingInsets: UIEdgeInsets(top: 100, left: 50, bottom: 100, right: 50))
            cameraUpdate.animation = .easeOut
            cameraUpdate.animationDuration = 1.0 // Move smoothly to fit bounds
            map.moveCamera(cameraUpdate)
            
            // 3. Wait Longer (4.0s) -> Zoom In
            // Delay increased to ensure user sees the pins for "at least 3 seconds" after animation finishes
            DispatchQueue.main.asyncAfter(deadline: .now() + 4.0) {
                // Action 2: Zoom to 15 (User Request), Duration 1.0s for smoother feel
                let end = NMFCameraUpdate(scrollTo: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude), zoomTo: 15)
                end.animation = .fly
                end.animationDuration = 1.0
                map.moveCamera(end)
                
                OptimizationLogger.shared.log(type: .launchStep, value: ">>> Current Location: \(loc.coordinate)")
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
        
        func mapView(_ mapView: NMFMapView, cameraDidChangeByReason reason: Int, animated: Bool) {
             let heading = mapView.cameraPosition.heading
             DispatchQueue.main.async {
                 self.parent.rotation = heading
             }
             // Trigger Re-clustering on movement
             refreshWasmClusters()
        }
    }
}
