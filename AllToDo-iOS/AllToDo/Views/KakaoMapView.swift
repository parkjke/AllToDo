import SwiftUI
import KakaoMapsSDK
import CoreLocation

// MARK: - Map Action Enum
enum MapAction {
    case none
    case zoomIn
    case zoomOut
    case currentLocation
    case rotateNorth
    case zoomToFit
    case launchSequence
}

// MARK: - KakaoMapView Struct
struct KakaoMapView: UIViewRepresentable {
    @Binding var action: MapAction
    @Binding var rotation: Double
    @ObservedObject var locationManager: AppLocationManager
    var todoItems: [ToDoItem]
    var userLogs: [UserLog]
    @Binding var selectedItem: ToDoItem?
    @Binding var selectedClusterItems: [UnifiedMapItem]?
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onFarItemsDetected: ((Int) -> Void)? // [NEW] Callback
    
    func makeUIView(context: Context) -> KMViewContainer {
        // [FIX] Initialize with a non-zero frame to ensure the engine triggers layout correctly.
        let view = KMViewContainer(frame: UIScreen.main.bounds)
        view.backgroundColor = UIColor.white.withAlphaComponent(0.01) // Invisible fill for hits
        view.isUserInteractionEnabled = true
        
        context.coordinator.createController(view)
        context.coordinator.locationManager = locationManager
        context.coordinator.selectedItemBinding = $selectedItem
        context.coordinator.selectedClusterBinding = $selectedClusterItems
        context.coordinator.onLongTap = onLongTap
        context.coordinator.onFarItemsDetected = onFarItemsDetected
        
        return view
    }

    func updateUIView(_ uiView: KMViewContainer, context: Context) {
        // 1. Sync Bindings
        context.coordinator.selectedItemBinding = $selectedItem
        context.coordinator.selectedClusterBinding = $selectedClusterItems
        context.coordinator.onLongTap = onLongTap
        context.coordinator.onFarItemsDetected = onFarItemsDetected
        context.coordinator.rotationBinding = $rotation
        
        // 2. Data Update & Refresh (Check Diff to avoid redundant WASM calls)
        let itemsChanged = todoItems.count != context.coordinator.currentItems.count 
                        || todoItems.first?.id != context.coordinator.currentItems.first?.id
        let logsChanged = userLogs.count != context.coordinator.currentLogs.count
        
        if itemsChanged || logsChanged {
            context.coordinator.currentItems = todoItems
            context.coordinator.currentLogs = userLogs
            
            // Only trigger if map is ready
            if context.coordinator.controller?.isEngineActive == true {
                context.coordinator.refreshWasmClusters()
            }
        }
        
        // 3. Handle Actions
        if action != .none {
            context.coordinator.handleAction(action)
            DispatchQueue.main.async { action = .none }
        }
        
        // 4. [NEW] Check Tethering on Location Update
        if let mapView = context.coordinator.controller?.getView("mapview") as? KakaoMap,
           let userLoc = locationManager.currentLocation {
            context.coordinator.checkTethering(mapView: mapView, userLocation: userLoc)
        }
    }

    func makeCoordinator() -> Coordinator {
        return Coordinator(self)
    }

    static func dismantleUIView(_ uiView: KMViewContainer, coordinator: Coordinator) {
        coordinator.controller?.pauseEngine()
        coordinator.controller?.resetEngine()
    }

    // MARK: - Coordinator Class
    class Coordinator: NSObject, MapControllerDelegate, KakaoMapEventDelegate {
        var parent: KakaoMapView?
        var controller: KMController?
        weak var viewContainer: KMViewContainer?
        var locationManager: AppLocationManager?
        
        // Data & Bindings
        var selectedItemBinding: Binding<ToDoItem?>?
        var selectedClusterBinding: Binding<[UnifiedMapItem]?>?
        var rotationBinding: Binding<Double>?
        var onLongTap: ((CLLocationCoordinate2D) -> Void)?
        var onFarItemsDetected: ((Int) -> Void)?
        
        var currentItems: [ToDoItem] = []
        var currentLogs: [UserLog] = []
        var currentSpanLon: Int = 0
        
        // Lookup Tables for POI Taps
        var labelIdToClusterItems: [String: [UnifiedMapItem]] = [:]
        
        // Style ID Caching
        var registeredStyleIDs: Set<String> = []
        
        init(_ parent: KakaoMapView? = nil) {
            self.parent = parent
            super.init()
            NotificationCenter.default.addObserver(self, selector: #selector(appDidBecomeActive), name: UIApplication.didBecomeActiveNotification, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(appWillResignActive), name: UIApplication.willResignActiveNotification, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(appDidEnterBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)
        }
        
        deinit {
            NotificationCenter.default.removeObserver(self)
        }
        
        // MARK: - Lifecycle
        func createController(_ view: KMViewContainer) {
            self.viewContainer = view
            controller = KMController(viewContainer: view)
            controller?.delegate = self
            controller?.prepareEngine() // Async start
        }
        
        @objc func appWillResignActive() { controller?.pauseEngine() }
        @objc func appDidEnterBackground() { controller?.pauseEngine() }
        @objc func appDidBecomeActive() {
            if controller?.isEngineActive == false {
                controller?.activateEngine()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    self.refreshWasmClusters()
                }
            }
        }
        
        // MARK: - Tethering
        func checkTethering(mapView: KakaoMap, userLocation: CLLocation) {
            let target = mapView.cameraPosition.target
            let mapCenter = SmartLocationManager.shared.toIntLocation(CLLocation(latitude: target.latitude, longitude: target.longitude))
            let userInt = SmartLocationManager.shared.toIntLocation(userLocation)
            
            // Re-calculate Span for Tethering check
            // Kakao doesn't expose span directly, we estimate from viewport and zoom
            if currentSpanLon <= 0 {
                let width = mapView.viewRect.width > 0 ? mapView.viewRect.width : UIScreen.main.bounds.width
                let metersPerPixel = 156543.03392 * cos(target.latitude * .pi / 180.0) / pow(2, Double(mapView.zoomLevel))
                let spanDegrees = (metersPerPixel * Double(width)) / 111320.0
                currentSpanLon = Int(spanDegrees * 100_000.0)
            }
            
            if SmartLocationManager.shared.needsCentering(user: userInt, center: mapCenter, spanLon: currentSpanLon) {
                let pos = MapPoint(longitude: userLocation.coordinate.longitude, latitude: userLocation.coordinate.latitude)
                mapView.moveCamera(CameraUpdate.make(target: pos, mapView: mapView))
            }
        }
        
        // MARK: - MapControllerDelegate
        func addViews() {
            // [FIX] Initial Position (User Location > Pins Centroid > Gwanghwamun)
            var defaultPos = MapPoint(longitude: 126.9768, latitude: 37.5759) // Default Gwanghwamun
            
            if let loc = locationManager?.currentLocation {
                defaultPos = MapPoint(longitude: loc.coordinate.longitude, latitude: loc.coordinate.latitude)
            } else {
                // Calculate Centroid using cached data
                var latSum: Double = 0
                var lonSum: Double = 0
                var count: Double = 0
                
                for item in currentItems {
                    if let loc = item.location {
                        latSum += loc.latitude
                        lonSum += loc.longitude
                        count += 1
                    }
                }
                for log in currentLogs {
                    latSum += log.latitude
                    lonSum += log.longitude
                    count += 1
                }
                
                if count > 0 {
                    defaultPos = MapPoint(longitude: lonSum / count, latitude: latSum / count)
                }
            }
            
            let mapviewInfo = MapviewInfo(viewName: "mapview", viewInfoName: "map", defaultPosition: defaultPos, defaultLevel: 12)
            controller?.addView(mapviewInfo)
        }
        
        func addViewSucceeded(_ viewName: String, viewInfoName: String) {
            OptimizationLogger.shared.log(type: .launchStep, value: ">>> KakaoMap: View Ready")
            controller?.activateEngine() // Ensure Active
            
            if let mapView = controller?.getView("mapview") as? KakaoMap {
                mapView.eventDelegate = self
                
                // Initial Cluster (Sync with Viewport)
                DispatchQueue.main.async {
                    self.refreshWasmClusters()
                }
                
                // Launch Animation (Wait 3s -> Zoom User)
                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) { [weak self] in
                    guard let self = self else { return }
                    
                    if let loc = self.locationManager?.currentLocation {
                         OptimizationLogger.shared.log(type: .launchStep, value: ">>> Current Location Zoom: \(loc.coordinate)")
                         let pos = MapPoint(longitude: loc.coordinate.longitude, latitude: loc.coordinate.latitude)
                         let update = CameraUpdate.make(target: pos, zoomLevel: 15, rotation: 0, tilt: 0, mapView: mapView)
                         let options = CameraAnimationOptions(autoElevation: true, consecutive: false, durationInMillis: 1000)
                         mapView.animateCamera(cameraUpdate: update, options: options)
                    }
                }
            }
        }
        
        func addViewFailed(_ viewName: String, viewInfoName: String) {
            OptimizationLogger.shared.log(type: .error, value: "KakaoMap: addViewFailed (\(viewName))")
        }
        
        func containerDidResize(_ size: CGSize) {
            let mapView: KakaoMap? = controller?.getView("mapview") as? KakaoMap
            mapView?.viewRect = CGRect(origin: .zero, size: size)
            
            // Update Span
            if size.width > 0 {
                let target = mapView?.cameraPosition.target ?? MapPoint(longitude: 126.9, latitude: 37.5)
                let metersPerPixel = 156543.03392 * cos(target.latitude * .pi / 180.0) / pow(2, Double(mapView?.zoomLevel ?? 12))
                let spanDegrees = (metersPerPixel * Double(size.width)) / 111320.0
                currentSpanLon = Int(spanDegrees * 100_000.0)
            }
            
            refreshWasmClusters()
        }
        
        // MARK: - WASM Clustering
        func refreshWasmClusters() {
            guard let controller = controller else { return }
            guard let mapView = controller.getView("mapview") as? KakaoMap else { return }
            
            // [FIX] Sync viewRect with Container before every refresh
            if let container = viewContainer {
                let size = container.bounds.size
                if size.width > 0 && size.height > 0 {
                    mapView.viewRect = CGRect(origin: .zero, size: size)
                }
            }
            
            // [FIX] Ensure Delegate is active
            if mapView.eventDelegate == nil {
                mapView.eventDelegate = self
            }
            
            var widthPixels = mapView.viewRect.width
            if widthPixels <= 0 { widthPixels = UIScreen.main.bounds.width }
            
            // Calc Cell Size
            let zoom = mapView.zoomLevel
            let centerPos = mapView.getPosition(CGPoint(x: widthPixels/2, y: (mapView.viewRect.height > 0 ? mapView.viewRect.height : UIScreen.main.bounds.height)/2))
            let centerLat = centerPos.wgsCoord.latitude
            
            let metersPerPixel = 156543.03392 * cos(centerLat * .pi / 180.0) / pow(2, Double(zoom))
            let wasmCellSize = metersPerPixel * 100.0 
            
            // Prepare Data
            var allItems: [UnifiedMapItem] = []
            var rawPoints: [Int32] = []
            
            var farItemsCount = 0
            
            // Pre-calc user int
            var uInt: (lat: Int, lon: Int)? = nil
            if let u = locationManager?.currentLocation {
                uInt = SmartLocationManager.shared.toIntLocation(u)
            }
            
            OptimizationLogger.shared.log(type: .launchStep, value: ">>> Pins Loaded: \(currentItems.count) Items, \(currentLogs.count) Logs")
            
            for item in currentItems {
                if let loc = item.location {
                     // Standard Path: Show All
                    allItems.append(.todo(item))
                    rawPoints.append(Int32(loc.latitude * 1_000_000)) // Use computed property for display
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
            // [FIX] Add User Location
            if let userLoc = locationManager?.currentLocation {
                allItems.append(.userLocation)
                rawPoints.append(Int32(userLoc.coordinate.latitude * 1_000_000))
                rawPoints.append(Int32(userLoc.coordinate.longitude * 1_000_000))
            }
            
            Task {
                 let result = await WasmManager.shared.cluster(points: rawPoints, cellSize: wasmCellSize)
                 await MainActor.run {
                     self.renderWasmResults(mapView: mapView, clusterResult: result, allItems: allItems)
                 }
            }
        }
        
        @MainActor
        func renderWasmResults(mapView: KakaoMap, clusterResult: [Int32], allItems: [UnifiedMapItem]) {
            let labelManager = mapView.getLabelManager()
            let layerID = "todoLayer"
            
            // [FIX] Force Recreate Layer to ensure Touch Interactions are fresh
            // clearAllItems might be retaining broken state.
            labelManager.removeLabelLayer(layerID: layerID)
            
            let layer = labelManager.addLabelLayer(option: LabelLayerOptions(layerID: layerID, competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 10000))
            guard let activeLayer = layer else { return }
            
            // activeLayer.clearAllItems() // No longer needed as layer is new
            labelIdToClusterItems.removeAll()
            
            // Parse Buckets
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
            
            // Assign
            for item in allItems {
                var lat: Double = 0; var lon: Double = 0
                switch item {
                case .todo(let t): if let l = t.location { lat = l.latitude; lon = l.longitude }
                case .history(let l): lat = l.latitude; lon = l.longitude
                case .userLocation: if let u = locationManager?.currentLocation { lat = u.coordinate.latitude; lon = u.coordinate.longitude }
                default: break
                }
                
                var bestIdx = -1
                var minDist = Double.greatestFiniteMagnitude
                for (idx, c) in centroids.enumerated() {
                    let dist = (lat-c.lat)*(lat-c.lat) + (lon-c.lon)*(lon-c.lon)
                    if dist < minDist { minDist = dist; bestIdx = idx }
                }
                if bestIdx >= 0 { clusters[bestIdx].append(item) }
            }
            
            // Render POIs
            for (idx, items) in clusters.enumerated() {
                guard !items.isEmpty else { continue }
                let centroid = centroids[idx]
                
                // Determine Position
                var pos: MapPoint
                if items.count == 1 {
                    let item = items[0]
                    switch item {
                    case .todo(let t): if let l = t.location { pos = MapPoint(longitude: l.longitude, latitude: l.latitude) } else { pos = MapPoint(longitude: centroid.lon, latitude: centroid.lat) }
                    case .history(let l): pos = MapPoint(longitude: l.longitude, latitude: l.latitude)
                    case .userLocation: if let u = locationManager?.currentLocation { pos = MapPoint(longitude: u.coordinate.longitude, latitude: u.coordinate.latitude) } else { pos = MapPoint(longitude: centroid.lon, latitude: centroid.lat) }
                    default: pos = MapPoint(longitude: centroid.lon, latitude: centroid.lat)
                    }
                } else {
                    pos = MapPoint(longitude: centroid.lon, latitude: centroid.lat)
                }
                
                // [FIX] Base Image & Badge Logic
                var userFound = false
                var historyCount = 0; var todoCount = 0
                for item in items {
                    switch item {
                    case .userLocation: userFound = true
                    case .history: historyCount += 1
                    case .todo: todoCount += 1
                    default: break
                    }
                }
                
                var baseName = "PinTodoReady"
                if userFound { baseName = "PinCurrent" }
                else if historyCount > todoCount { baseName = "PinHistory" }
                
                // Color Logic
                var color: UIColor = UIColor(red: 0.2, green: 0.8, blue: 0.2, alpha: 1.0)
                if baseName == "PinHistory" { color = .red }
                if baseName == "PinCurrent" { color = .blue } // Base PinCurrent is blueish/custom
                
                // Resize Base (Restored to reasonable size 44x55 for touch target, relying on scale 1.0 fix)
                let baseImage = UIImage(named: baseName)?.resized(to: CGSize(width: 44, height: 55))
                
                // Badge (Only if count > 1)
                let displayCount = items.count > 1 ? items.count : nil
                let finalImage = PinImageHelper.shared.createShieldPin(color: color, count: displayCount, baseImage: baseImage)
                
                // Register Style (Dynamic ID based on content to cache?) 
                // Creating unique style for every POI is expensive. 
                // But text changes. So strictly we need unique styles for badges.
                // In KakaoSDK, styles are heavy. 
                // However, for cluster counts, we might have many unique counts. 
                // Let's create a Style ID based on "BaseName_Count" to share styles!
                let styleID = "Style_\(baseName)_\(displayCount ?? 0)"
                
                if !registeredStyleIDs.contains(styleID) {
                    if let rasterized = finalImage.rasterized() { // Rasterize for Kakao
                         // [FIX] Adjust Anchor Point for Padding (PinImageHelper added 20px padding)
                         // Content is centered with padding. Visual bottom is at (Height - Padding).
                         // Anchor Y should be approx (TotalHeight - 20) / TotalHeight.
                         // Estimate: 0.85
                         let iconStyle = PoiIconStyle(symbol: rasterized, anchorPoint: CGPoint(x: 0.5, y: 0.85))
                         let style = PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: iconStyle, level: 0)])
                         labelManager.addPoiStyle(style)
                         registeredStyleIDs.insert(styleID)
                    }
                }
                
                // Add POI
                let poiID = "Cluster_\(idx)"
                labelIdToClusterItems[poiID] = items
                
                let options = PoiOptions(styleID: styleID, poiID: poiID)
                options.rank = 0
                options.clickable = true
                
                if let poi = activeLayer.addPoi(option: options, at: pos) {
                    poi.clickable = true // Double check
                    poi.show()
                }
            }
            
            // Update Path (Disabled)
            updatePath(mapView: mapView, selectedItems: selectedClusterBinding?.wrappedValue)
        }
        
        func updatePath(mapView: KakaoMap, selectedItems: [UnifiedMapItem]?) {
             let shapeManager = mapView.getShapeManager()
             shapeManager.removeShapeLayer(layerID: "pathLayer")
             
             guard let items = selectedItems else { return }
             let historyLogs = items.compactMap { item -> UserLog? in
                  if case .history(let log) = item, log.pathData != nil { return log }
                  return nil
             }
             
             guard let log = historyLogs.first, let data = log.pathData else { return }
             
             if let points = try? JSONDecoder().decode([LocationData].self, from: data) {
                 let coords = points.map { MapPoint(longitude: $0.longitude, latitude: $0.latitude) }
                 if coords.count >= 2 {
                     let layer = shapeManager.addShapeLayer(layerID: "pathLayer", zOrder: 500)
                     let style = PolylineStyle(styles: [
                         PerLevelPolylineStyle(styleID: "redPath", color: .red, strokeWidth: 4, level: 0)
                     ])
                     shapeManager.addPolylineStyle(style)
                     
                     let options = PolylineOptions(styleID: "redPath", layerID: "pathLayer")
                     options.addPoints(coords)
                     layer?.addPolyline(option: options)
                 }
             }
        }
        
        // MARK: - Actions
        func handleAction(_ action: MapAction) {
            guard let mapView = controller?.getView("mapview") as? KakaoMap else { return }
            switch action {
            case .zoomIn:
                mapView.moveCamera(CameraUpdate.make(zoomLevel: mapView.zoomLevel + 1, mapView: mapView))
            case .zoomOut:
                mapView.moveCamera(CameraUpdate.make(zoomLevel: mapView.zoomLevel - 1, mapView: mapView))
            case .currentLocation:
                if let loc = locationManager?.currentLocation {
                    OptimizationLogger.shared.log(type: .locationResume, value: ">>> Current Location Button Pressed: \(loc.coordinate)")
                    let pos = MapPoint(longitude: loc.coordinate.longitude, latitude: loc.coordinate.latitude)
                    mapView.moveCamera(CameraUpdate.make(target: pos, zoomLevel: 18, mapView: mapView))
                }
            case .rotateNorth:
                mapView.moveCamera(CameraUpdate.make(rotation: 0, tilt: 0, mapView: mapView))
            case .zoomToFit:
                break
            case .launchSequence:
                if let loc = locationManager?.currentLocation {
                     let pos = MapPoint(longitude: loc.coordinate.longitude, latitude: loc.coordinate.latitude)
                     let update = CameraUpdate.make(target: pos, zoomLevel: 15, rotation: 0, tilt: 0, mapView: mapView)
                     let options = CameraAnimationOptions(autoElevation: true, consecutive: false, durationInMillis: 500)
                     mapView.animateCamera(cameraUpdate: update, options: options)
                }
            case .none: break
            }
        }
        
        // MARK: - Interactions
        func poiDidTapped(kakaoMap: KakaoMap, layerID: String, poiID: String, param: Any?) {
             print("DEBUG: poiDidTapped called for poiID: \(poiID)")
             let generator = UIImpactFeedbackGenerator(style: .medium)
             generator.impactOccurred()
            
             if let items = labelIdToClusterItems[poiID] {
                 DispatchQueue.main.async {
                     // [FIX] Distinguish Single Todo vs Cluster
                     if items.count == 1, let first = items.first {
                         switch first {
                         case .todo(let todoItem):
                             self.selectedItemBinding?.wrappedValue = todoItem
                             self.selectedClusterBinding?.wrappedValue = nil
                         default:
                             self.selectedClusterBinding?.wrappedValue = items
                             self.selectedItemBinding?.wrappedValue = nil
                         }
                     } else {
                         self.selectedClusterBinding?.wrappedValue = items
                         self.selectedItemBinding?.wrappedValue = nil
                     }
                 }
                 return
             }
             

        }
        
        func terrainDidTapped(kakaoMap: KakaoMap, position: MapPoint) {
            DispatchQueue.main.async {
                self.selectedClusterBinding?.wrappedValue = nil
                self.selectedItemBinding?.wrappedValue = nil
            }
        }
        
        func cameraDidStopped(kakaoMap: KakaoMap, by: MoveBy) {
             let rotation = kakaoMap.cameraPosition.rotation * 180.0 / .pi
             DispatchQueue.main.async {
                 self.rotationBinding?.wrappedValue = rotation
             }
             
             // [FIX] Trigger refresh on stop
             refreshWasmClusters()
        }
        
        func authenticationFailed(_ errorCode: Int, desc: String) {
            print("KakaoMap: Auth Failed \(errorCode)")
        }
    }
}
