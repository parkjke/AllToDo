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
    @Binding var tapPosition: CGPoint? // [NEW]
    @Binding var clusterRadius: Double? // [NEW]
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
        context.coordinator.tapPositionBinding = $tapPosition // [NEW]
        context.coordinator.onLongTap = onLongTap
        context.coordinator.onFarItemsDetected = onFarItemsDetected
        
        return view
    }

    func updateUIView(_ uiView: KMViewContainer, context: Context) {
        // [FIX] Safe Activation: Only activate if attached to window and frame is valid
        if uiView.window != nil && uiView.frame.size.width > 0 {
            context.coordinator.checkEngineActivation()
        }
        
        // 1. Sync Bindings
        context.coordinator.selectedItemBinding = $selectedItem
        context.coordinator.selectedClusterBinding = $selectedClusterItems
        context.coordinator.tapPositionBinding = $tapPosition
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
        var tapPositionBinding: Binding<CGPoint?>? // [NEW]
        var rotationBinding: Binding<Double>?
        var onLongTap: ((CLLocationCoordinate2D) -> Void)?
        var onFarItemsDetected: ((Int) -> Void)?
        
        var currentItems: [ToDoItem] = []
        var currentLogs: [UserLog] = []
        var currentSpanLon: Int = 0
        var firstRender: Bool = true // [NEW] Track initial render for Raw/Switch logic
        
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
            controller?.prepareEngine() // Init Engine
        }
        
        // [NEW] Robust Activation Check
        func checkEngineActivation() {
            guard let container = viewContainer, let controller = controller else { return }
            
            // Criteria: Standard Engine State + Valid Window + Valid Frame
            if container.window != nil && container.frame.width > 0 {
                if !controller.isEngineActive {
                    print(">>> KakaoMap: Safe Activation Triggered via updateUIView")
                    controller.activateEngine()
                    
                    // Restore State if needed
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        self.refreshWasmClusters()
                    }
                }
            }
        }
        
        @objc func appWillResignActive() { controller?.pauseEngine() }
        @objc func appDidEnterBackground() { controller?.pauseEngine() }
        @objc func appDidBecomeActive() {
            checkEngineActivation()
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
                
                // [NEW] Apple Map Style: Fit Bounds Algorithm
                // 1. Collect Points & Filter 500km
                var points: [MapPoint] = []
                let userLoc = locationManager?.currentLocation
                
                var uLat = 0
                var uLon = 0
                if let u = userLoc {
                    let ui = SmartLocationManager.shared.toIntLocation(u)
                    uLat = ui.lat; uLon = ui.lon
                }
                
                for item in currentItems {
                    if let l = item.location {
                        // Integer Filter
                        if let u = userLoc, SmartLocationManager.shared.isFar(lat1: uLat, lon1: uLon, lat2: l.latInt, lon2: l.lonInt) { continue }
                        points.append(MapPoint(longitude: l.longitude, latitude: l.latitude))
                    }
                }
                for log in currentLogs {
                    // Integer Filter
                    if let u = userLoc, SmartLocationManager.shared.isFar(lat1: uLat, lon1: uLon, lat2: log.latInt, lon2: log.lonInt) { continue }
                    points.append(MapPoint(longitude: log.longitude, latitude: log.latitude))
                }
                if let u = userLoc {
                    points.append(MapPoint(longitude: u.coordinate.longitude, latitude: u.coordinate.latitude))
                }
                
                // 2. Initial Render (Clusters)
                DispatchQueue.main.async {
                    self.refreshWasmClusters()
                }
                
                // 3. Move Camera to Fit Bounds
                if !points.isEmpty {
                    var minLat = points[0].wgsCoord.latitude
                    var maxLat = points[0].wgsCoord.latitude
                    var minLon = points[0].wgsCoord.longitude
                    var maxLon = points[0].wgsCoord.longitude
                    
                    for p in points {
                        if p.wgsCoord.latitude < minLat { minLat = p.wgsCoord.latitude }
                        if p.wgsCoord.latitude > maxLat { maxLat = p.wgsCoord.latitude }
                        if p.wgsCoord.longitude < minLon { minLon = p.wgsCoord.longitude }
                        if p.wgsCoord.longitude > maxLon { maxLon = p.wgsCoord.longitude }
                    }
                    
                    // Add Padding (Approx 1.4x like Apple Map, but Kakao uses AreaRect)
                    let latSpan = max((maxLat - minLat) * 1.4, 0.005) // Reduced min span for Kakao
                    let lonSpan = max((maxLon - minLon) * 1.4, 0.005)
                    
                    // Center
                    let centerLat = (minLat + maxLat) / 2
                    let centerLon = (minLon + maxLon) / 2
                    
                    // Create AreaRect logic manually via CameraUpdate
                    // KakaoMap's AreaRect takes (minLon, minLat, maxLon, maxLat) ? No, it takes specific struct.
                    // Easier: Move to Center and Set Zoom.
                    // Or use CameraUpdate.make(area: AreaRect)
                    
                    let area = AreaRect(southWest: MapPoint(longitude: centerLon - lonSpan/2, latitude: centerLat - latSpan/2),
                                        northEast: MapPoint(longitude: centerLon + lonSpan/2, latitude: centerLat + latSpan/2))
                    
                    mapView.moveCamera(CameraUpdate.make(area: area))
                }
                
                // 4. Launch Animation (Wait 3s -> Zoom User)
                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) { [weak self] in
                    guard let self = self else { return }
                    
                    // [FIX] End Raw Mode (Fast Path) and Cluster
                    self.firstRender = false
                    self.refreshWasmClusters()
                    
                    if let loc = self.locationManager?.currentLocation {
                         OptimizationLogger.shared.log(type: .launchStep, value: ">>> Current Location Zoom: \(loc.coordinate)")
                         let pos = MapPoint(longitude: loc.coordinate.longitude, latitude: loc.coordinate.latitude)
                         let update = CameraUpdate.make(target: pos, zoomLevel: 18, rotation: 0, tilt: 0, mapView: mapView)
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
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let target = mapView?.getPosition(center) ?? MapPoint(longitude: 126.9, latitude: 37.5)
                let metersPerPixel = 156543.03392 * cos(target.wgsCoord.latitude * .pi / 180.0) / pow(2, Double(mapView?.zoomLevel ?? 12))
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
            let wasmCellSize = metersPerPixel * 100.0 // [FIX] Restored Standard Sensitivity (100.0) 
            
            // [NEW] Update Binding
            DispatchQueue.main.async {
                if let p = self.parent { p.clusterRadius = wasmCellSize }
            }
            
            // Prepare Data
            var allItems: [UnifiedMapItem] = []
            var rawPoints: [Int32] = []
            
            var farItemsCount = 0
            
            // Pre-calc user int
            var uInt: (lat: Int, lon: Int)? = nil
            if let u = locationManager?.currentLocation {
                uInt = SmartLocationManager.shared.toIntLocation(u)
            }
            
            // [OPTIMIZATION] Fast Path
            // [FIX] Use firstRender to support Map Switching scenario
            // [CRITICAL LOCK: DO NOT MODIFY] Raw First -> Cluster Strategy
            let isLaunchPhase = parent?.action == .launchSequence || firstRender
            if isLaunchPhase {
                 var rawItems: [UnifiedMapItem] = []
                 for item in currentItems { rawItems.append(.todo(item)) }
                 for log in currentLogs { rawItems.append(.history(log)) }
                 if let u = locationManager?.currentLocation { rawItems.append(.userLocation) }
                 
                 renderRawItems(mapView: mapView, allItems: rawItems)
                 return
            }
            
            // OptimizationLogger.shared.log(type: .launchStep, value: ">>> Pins Loaded: \(currentItems.count) Items, \(currentLogs.count) Logs")
            
            for item in currentItems {
                if let loc = item.location {
                     // Standard Path: Show All
                    allItems.append(.todo(item))
                    rawPoints.append(Int32(loc.latitude * 100_000)) // Use computed property for display
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
            // [FIX] Add User Location
            if let userLoc = locationManager?.currentLocation {
                allItems.append(.userLocation)
                rawPoints.append(Int32(userLoc.coordinate.latitude * 100_000))
                rawPoints.append(Int32(userLoc.coordinate.longitude * 100_000))
            }
            
            Task {
                  // print(">>> WASM Clustering Start")
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
            
            let layer = labelManager.addLabelLayer(option: LabelLayerOptions(layerID: layerID, competitionType: .same, competitionUnit: .poi, orderType: .rank, zOrder: 10000))
            guard let activeLayer = layer else { return }
            
            // activeLayer.clearAllItems() // No longer needed as layer is new
            labelIdToClusterItems.removeAll()
            
            // Parse Buckets
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
                
                
                // [FIX] Centralized Logic
                let (baseName, color, _) = UnifiedMapItem.resolveClusterStyle(items: items)
                
                // Resize Base (Reduced to 32x40 for better UI balance)
                let baseImage = UIImage(named: baseName)?.resized(to: CGSize(width: 32, height: 40))
                
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
                         // [FIX] Adjust Anchor Point for new size (32x40)
                         // Content is centered. Tip is at the bottom.
                         let iconStyle = PoiIconStyle(symbol: rasterized, anchorPoint: CGPoint(x: 0.5, y: 1.0))
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
        
        // MARK: - Raw Rendering (Fast Path)
        func renderRawItems(mapView: KakaoMap, allItems: [UnifiedMapItem]) {
             let labelManager = mapView.getLabelManager()
             let layerID = "todoLayer"
             
             // Reset Layer
             labelManager.removeLabelLayer(layerID: layerID)
             guard let layer = labelManager.addLabelLayer(option: LabelLayerOptions(layerID: layerID, competitionType: .same, competitionUnit: .poi, orderType: .rank, zOrder: 10000)) else { return }
             
             labelIdToClusterItems.removeAll()
             
             for (idx, item) in allItems.enumerated() {
                 // Determine Location
                 var pos: MapPoint?
                 switch item {
                 case .todo(let t): if let l = t.location { pos = MapPoint(longitude: l.longitude, latitude: l.latitude) }
                 case .history(let l): pos = MapPoint(longitude: l.longitude, latitude: l.latitude)
                 case .userLocation: if let u = locationManager?.currentLocation { pos = MapPoint(longitude: u.coordinate.longitude, latitude: u.coordinate.latitude) }
                 default: break
                 }
                 guard let position = pos else { continue }
                                  // [FIX] Centralized Logic
                  let (baseName, _, _) = UnifiedMapItem.resolveClusterStyle(items: [item])
                 
                 // Style ID
                 let styleID = "Style_Raw_\(baseName)"
                 if !registeredStyleIDs.contains(styleID) {
                      if let img = UIImage(named: baseName)?.resized(to: CGSize(width: 32, height: 40)),
                         let rasterized = img.rasterized() {
                          let iconStyle = PoiIconStyle(symbol: rasterized, anchorPoint: CGPoint(x: 0.5, y: 1.0))
                          let style = PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: iconStyle, level: 0)])
                          labelManager.addPoiStyle(style)
                          registeredStyleIDs.insert(styleID)
                      }
                 }
                 
                 // Add POI
                 let poiID = "Raw_\(idx)"
                 labelIdToClusterItems[poiID] = [item] 
                 
                 if let poi = layer.addPoi(option: PoiOptions(styleID: styleID, poiID: poiID), at: position) {
                     poi.show()
                 }
             }
             
             OptimizationLogger.shared.log(type: .launchStep, value: ">>> Fast Path: Rendered \(allItems.count) Raw Items")
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
                      let style = PolylineStyleSet(styleSetID: "redPathSet", styles: [
                          PolylineStyle(styles: [
                              PerLevelPolylineStyle(bodyColor: .red, bodyWidth: 4, strokeColor: .clear, strokeWidth: 0, level: 0)
                          ])
                      ])
                      shapeManager.addPolylineStyleSet(style)
                      
                      let options = MapPolylineShapeOptions(shapeID: "path", styleID: "redPathSet", zOrder: 0)
                      options.polylines = [MapPolyline(line: coords, styleIndex: 0)]
                      layer?.addMapPolylineShape(options)
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
                }
            case .none: break
            }
        }
           // [FIX] Anti-Conflict Flag
        var lastPoiTapTime: Date = Date.distantPast
        
        // [NEW] Pending Selection for Auto-Center
        var pendingSelection: (layerID: String, poiID: String)?

        // MARK: - Interactions
        // [FIX] Correct Signature: position instead of param
        func poiDidTapped(kakaoMap: KakaoMap, layerID: String, poiID: String, position: MapPoint) {
             // print("DEBUG: POI Tapped: \(poiID)")
             let generator = UIImpactFeedbackGenerator(style: .medium)
             generator.impactOccurred()
            
             // 1. Store Pending Selection
             pendingSelection = (layerID, poiID)
            
             // 2. Animate to Center
             // Keep current zoom level, just change target
             let update = CameraUpdate.make(target: position, zoomLevel: kakaoMap.zoomLevel, rotation: 0, tilt: 0, mapView: kakaoMap)
             let options = CameraAnimationOptions(autoElevation: false, consecutive: true, durationInMillis: 300) // Fast 300ms animation
             kakaoMap.animateCamera(cameraUpdate: update, options: options)
        }
        
        func terrainDidTapped(kakaoMap: KakaoMap, position: MapPoint) {
            DispatchQueue.main.async {
                self.selectedClusterBinding?.wrappedValue = nil
                self.selectedItemBinding?.wrappedValue = nil
            }
        }
        
        func cameraDidStopped(kakaoMap: KakaoMap, by: MoveBy) {
             let rotation = self.getMapRotation(mapView: kakaoMap) * 180.0 / .pi
             DispatchQueue.main.async {
                 self.rotationBinding?.wrappedValue = rotation
             }
             
             // [NEW] Handle Pending Selection (Auto-Center Complete)
             if let pending = pendingSelection {
                 let layerID = pending.layerID
                 let poiID = pending.poiID
                 pendingSelection = nil // Reset
                 
                 if let items = labelIdToClusterItems[poiID] {
                     DispatchQueue.main.async {
                         // Update tapPosition (Should be center now, but calculate to be safe)
                         // We use the POI's position which is now at the center of the map
                         if let layer = kakaoMap.getLabelManager().getLabelLayer(layerID: layerID),
                            let poi = layer.getPoi(poiID: poiID) {
                             let point = self.mapToScreen(mapView: kakaoMap, mapPoint: poi.position)
                             self.tapPositionBinding?.wrappedValue = point
                         } else {
                              // Fallback if POI object is tricky, re-use logic?
                              // But we need the mapPoint. Ideally we passed it in pendingSelection?
                              // Simplification: We know we centered on it.
                              let center = CGPoint(x: kakaoMap.viewRect.width / 2, y: kakaoMap.viewRect.height / 2)
                              self.tapPositionBinding?.wrappedValue = center
                         }
                         
                         // Select Item
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
                 }
             } else {
                 // Only refresh clusters if we are NOT in the middle of a selection animation?
                 // Or always refresh? Refreshing might re-cluster and destroy the POI we just tapped...
                 // But since we just moved camera, we probably DO need to refresh.
                 // Ideally, we shouldn't re-cluster if we just tapped a pin.
                 // But the user might have zoomed.
                 // Let's keep it safe: Always refresh.
                 refreshWasmClusters()
             }
        }
        
        func authenticationFailed(_ errorCode: Int, desc: String) {
            print("KakaoMap: Auth Failed \(errorCode)")
        }
        
        // MARK: - Manual Geometric Workarounds for missing v2 APIs
        private func getMapRotation(mapView: KakaoMap) -> Double {
            let width = mapView.viewRect.width > 0 ? mapView.viewRect.width : UIScreen.main.bounds.width
            let height = mapView.viewRect.height > 0 ? mapView.viewRect.height : UIScreen.main.bounds.height
            let center = CGPoint(x: width / 2, y: height / 2)
            let p0 = mapView.getPosition(center)
            let p1 = mapView.getPosition(CGPoint(x: center.x, y: center.y - 50)) // 50px towards top
            
            let dLat = p1.wgsCoord.latitude - p0.wgsCoord.latitude
            let dLon = (p1.wgsCoord.longitude - p0.wgsCoord.longitude) * cos(p0.wgsCoord.latitude * .pi / 180.0)
            return atan2(dLon, dLat)
        }
        
        private func mapToScreen(mapView: KakaoMap, mapPoint: MapPoint) -> CGPoint {
            let width = mapView.viewRect.width > 0 ? mapView.viewRect.width : UIScreen.main.bounds.width
            let height = mapView.viewRect.height > 0 ? mapView.viewRect.height : UIScreen.main.bounds.height
            let centerScreen = CGPoint(x: width / 2, y: height / 2)
            let centerMap = mapView.getPosition(centerScreen)
            
            let lat = centerMap.wgsCoord.latitude
            let metersPerPixel = 156543.03392 * cos(lat * .pi / 180.0) / pow(2, Double(mapView.zoomLevel))
            
            let dy_m = (mapPoint.wgsCoord.latitude - centerMap.wgsCoord.latitude) * 111320.0
            let dx_m = (mapPoint.wgsCoord.longitude - centerMap.wgsCoord.longitude) * 111320.0 * cos(lat * .pi / 180.0)
            
            let dx_p = dx_m / metersPerPixel
            let dy_p = -dy_m / metersPerPixel
            
            let theta = getMapRotation(mapView: mapView)
            let rx = dx_p * cos(theta) - dy_p * sin(theta)
            let ry = dx_p * sin(theta) + dy_p * cos(theta)
            
            return CGPoint(x: centerScreen.x + rx, y: centerScreen.y + ry)
        }
    }
}
