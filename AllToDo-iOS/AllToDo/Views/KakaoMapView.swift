import SwiftUI
import CoreLocation
import KakaoMapsSDK
import SwiftData

// MARK: - KakaoMapView REBUILD (REVERT TO ATTEMPT 1 - TOUCH SUCCESS STATE)
struct KakaoMapView: UIViewRepresentable {
    @Environment(\.modelContext) var modelContext
    @Binding var action: MapAction
    @Binding var rotation: Double
    @ObservedObject var locationManager: AppLocationManager
    var allItems: [UnifiedMapItem]
    @Binding var selectedItem: ToDoItem?
    @Binding var viewingHistoryItem: ToDoItem? // [NEW]
    @Binding var selectedClusterItems: [UnifiedMapItem]?

    @Binding var tapPosition: CGPoint?
    @Binding var clusterRadius: Double?
    @Binding var creatingTodoLocation: CLLocationCoordinate2D?
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((ToDoItem) -> Void)?
    var onSelectLog: ((ToDoItem) -> Void)?
    var onSelectItem: ((ToDoItem) -> Void)?
    var onFarItemsDetected: ((Int) -> Void)?
    
    // [NEW] Active Path Rendering
    var activePoints: [PathPoint] = []
    var showActivePath: Bool = true

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> KMViewContainer {
        print(">>> MAIN MAP: makeUIView (STEP 4)")
        let view = KMViewContainer(frame: UIScreen.main.bounds)
        view.isUserInteractionEnabled = true
        view.isMultipleTouchEnabled = true
        view.backgroundColor = UIColor.white.withAlphaComponent(0.01) 
        context.coordinator.createController(view)
        return view
    }

    func updateUIView(_ uiView: KMViewContainer, context: Context) {
        context.coordinator.parent = self
        
        if uiView.window != nil {
            context.coordinator.checkEngineActivation()
            if let mapView = context.coordinator.controller?.getView("mapview") as? KakaoMap {
                // Conditional relinking to prevent session resets
                if mapView.eventDelegate == nil {
                    print(">>> MAIN MAP: Linking EventDelegate")
                    mapView.eventDelegate = context.coordinator
                }
            }
        }
        
        if action != .none {
            context.coordinator.handleAction(action)
            DispatchQueue.main.async { action = .none }
        }
        
        // [STEP 5] Smart Pins & Tethering
        let currentSummary = "\(allItems.count)-\(allItems.first?.id.uuidString ?? "")"
        if context.coordinator.lastDataSummary != currentSummary {
            context.coordinator.lastDataSummary = currentSummary
            context.coordinator.forceUpdatePins()
        }
        if let mapView = context.coordinator.controller?.getView("mapview") as? KakaoMap,
           let loc = locationManager.currentLocation {
            context.coordinator.checkTethering(mapView: mapView, userLocation: loc)
        }
        
        // [NEW] Path Visualization for selected History Item
        if let mapView = context.coordinator.controller?.getView("mapview") as? KakaoMap {
            // Update Path Visualization (Selected Item or Viewing History)
            let pathToShow = selectedItem ?? viewingHistoryItem
            context.coordinator.updatePath(mapView: mapView, historyItem: pathToShow)
            
            // [NEW] Auto-zoom to history path if it's new
            if let item = pathToShow, context.coordinator.lastHistoryID != item.todo_id {
                context.coordinator.lastHistoryID = item.todo_id
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 300_000_000)
                    context.coordinator.zoomToHistoryPath(mapView: mapView, item: item)
                }
            }

            
            // [NEW] Active Path Rendering
            context.coordinator.updateActiveRecordingPath(mapView: mapView, points: activePoints, visible: showActivePath)
        }
    }


    class Coordinator: NSObject, MapControllerDelegate, KakaoMapEventDelegate {
        var parent: KakaoMapView
        var controller: KMController?
        
        // Data State
        var lastDataSummary: String = ""
        var registeredStyleIDs: Set<String> = []
        var labelIdToClusterItems: [String: [UnifiedMapItem]] = [:]
        
        // [NEW] Active POI Tracking for Diffing
        var activePoiIDs: Set<String> = []

        
        // [STEP 5] Advanced State
        var isLaunchAnimating = false
        var firstRender = true
        var isInitialPhase = true // [FIX] Sequence: Raw pins for 3s, then Cluster
        var isUserInteracting = false
        var moveLocation: (lat: Int, lon: Int)? = nil
        var currentSpanLon: Int = 0
        var currentSpanLat: Int = 0
        var currentSpanLon: Int = 0
        var currentSpanLat: Int = 0
        var lastHistoryID: UUID? = nil
        var lastClusteredWm: Double = -1.0 // [NEW] 1.5x Threshold Tracking

        
        init(_ parent: KakaoMapView) {
            self.parent = parent
        }

        func createController(_ view: KMViewContainer) {
            controller = KMController(viewContainer: view)
            controller?.delegate = self
            controller?.prepareEngine()
        }

        func checkEngineActivation() {
            guard let controller = controller else { return }
            // [FIX] Suppress "Skip rendering" logs by only activating when active
            guard UIApplication.shared.applicationState == .active else { return }
            
            if controller.isEnginePrepared && !controller.isEngineActive {
                print(">>> MAIN MAP: Activating Engine")
                controller.activateEngine()
                self.lastDataSummary = ""
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    self.forceUpdatePins()
                }
            }
        }

        func addViews() {
            print(">>> MAIN MAP: addViews")
            let defaultPosition = MapPoint(longitude: 126.9768, latitude: 37.5759)
            let mapviewInfo = MapviewInfo(viewName: "mapview", viewInfoName: "map", defaultPosition: defaultPosition, defaultLevel: 14)
            controller?.addView(mapviewInfo)
        }

        func addViewSucceeded(_ viewName: String, viewInfoName: String) {
            print(">>> MAIN MAP: addViewSucceeded (\(viewName))")
            guard let mapView = controller?.getView(viewName) as? KakaoMap else { return }
            mapView.eventDelegate = self
            
            // [STEP 5] Reset Data State to force refresh on new engine instance
            lastDataSummary = ""
            registeredStyleIDs.removeAll()
            labelIdToClusterItems.removeAll()
            
            // [STEP 5] Initial Fit Bounds
            isLaunchAnimating = true
            fitBoundsInitially(mapView)
            
            // Initial Sync
            forceUpdatePins()
            
            // Interaction Detection (Rotation Sync)
            NotificationCenter.default.addObserver(forName: NSNotification.Name("TriggerLaunchAnimation"), object: nil, queue: .main) { _ in
                self.isLaunchAnimating = true
                self.fitBoundsInitially(mapView)
            }
            
            // [FIX] Robust Resume Fix: Ensure activation & 3s zoom sequence
            NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
                guard let self = self else { return }
                guard UIApplication.shared.applicationState == .active else { return }
                
                print(">>> MAIN MAP: App Active (Resume) -> Activating Map")
                self.checkEngineActivation()
                
                self.isInitialPhase = true 
                self.lastDataSummary = ""
                self.forceUpdatePins()
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                    guard let mapView = self.controller?.getView("mapview") as? KakaoMap else { return }
                    guard UIApplication.shared.applicationState == .active else { return }
                    
                    print(">>> MAIN MAP: Resume Zoom starting")
                    self.isInitialPhase = false
                    self.lastDataSummary = "" 
                    self.forceUpdatePins()
                    
                    if let u = self.parent.locationManager.currentLocation {
                        let pos = MapPoint(longitude: u.coordinate.longitude, latitude: u.coordinate.latitude)
                        mapView.animateCamera(cameraUpdate: CameraUpdate.make(target: pos, zoomLevel: 18, mapView: mapView), 
                                              options: CameraAnimationOptions(autoElevation: true, consecutive: false, durationInMillis: 1200))
                    }
                }
            }
            
            NotificationCenter.default.addObserver(forName: UIApplication.willResignActiveNotification, object: nil, queue: .main) { [weak self] _ in
                print(">>> MAIN MAP: Resign Active -> Pausing Engine")
                self?.controller?.pauseEngine()
            }
        }
        
        private func fitBoundsInitially(_ mapView: KakaoMap) {
            var points: [MapPoint] = []
            
            var uInt: (lat: Int, lon: Int)? = nil
            if let u = parent.locationManager.currentLocation {
                uInt = SmartLocationManager.shared.toIntLocation(u)
                points.append(MapPoint(longitude: u.coordinate.longitude, latitude: u.coordinate.latitude))
            } else {
                // [FIX] Prioritize saved location from UserDefaults
                let hasSaved = UserDefaults.standard.bool(forKey: "has_saved_location")
                if hasSaved {
                    let savedLat = UserDefaults.standard.double(forKey: "last_latitude")
                    let savedLon = UserDefaults.standard.double(forKey: "last_longitude")
                    points.append(MapPoint(longitude: savedLon, latitude: savedLat))
                    print(">>> KakaoMapView: Including Saved Location in Fit Bounds: \(savedLat), \(savedLon)")
                }
            }
            
            for item in parent.allItems {
                switch item {
                case .todo(let t):
                    points.append(MapPoint(longitude: t.longitude, latitude: t.latitude))
                case .history(let log):
                    points.append(MapPoint(longitude: log.longitude, latitude: log.latitude))
                case .userLocation(let coord):
                    points.append(MapPoint(longitude: coord.longitude, latitude: coord.latitude))
                case .serverMessage:
                    break
                }
            }

            
            if !points.isEmpty {
                let centerLon = points.map { $0.wgsCoord.longitude }.reduce(0, +) / Double(points.count)
                let centerLat = points.map { $0.wgsCoord.latitude }.reduce(0, +) / Double(points.count)
                let area = AreaRect(southWest: MapPoint(longitude: centerLon - 0.05, latitude: centerLat - 0.05),
                                    northEast: MapPoint(longitude: centerLon + 0.05, latitude: centerLat + 0.05))
                mapView.moveCamera(CameraUpdate.make(area: area))
            }
            
            // [STEP 5] Initial Render of all items
            forceUpdatePins()
            
            // [STEP 5] Zoom to User after 3 seconds
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                // [FIX] Sequence: 3s is up. Turn OFF Initial phase to enable clustering.
                self.isInitialPhase = false
                self.lastDataSummary = "" // Force refresh
                self.forceUpdatePins()
                
                if let u = self.parent.locationManager.currentLocation {
                    let pos = MapPoint(longitude: u.coordinate.longitude, latitude: u.coordinate.latitude)
                    mapView.animateCamera(cameraUpdate: CameraUpdate.make(target: pos, zoomLevel: 18, mapView: mapView), 
                                          options: CameraAnimationOptions(autoElevation: true, consecutive: false, durationInMillis: 1200))
                    
                    self.firstRender = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.3) {
                        self.isLaunchAnimating = false
                        self.moveLocation = SmartLocationManager.shared.toIntLocation(u)
                    }
                } else {
                    self.isLaunchAnimating = false
                }
            }
        }

        func containerDidResize(_ size: CGSize) {
            guard let mapView = controller?.getView("mapview") as? KakaoMap else { return }
            if size.width > 0 && size.height > 0 {
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let target = mapView.getPosition(center)
                let metersPerPixel = 156543.03392 * cos(target.wgsCoord.latitude * .pi / 180.0) / pow(2, Double(mapView.zoomLevel))
                currentSpanLon = Int((metersPerPixel * Double(size.width)) / 111320.0 * 100_000.0)
                currentSpanLat = Int((metersPerPixel * Double(size.height)) / 111320.0 * 100_000.0)
            }
            refreshWasmClusters()
        }

        // MARK: - [STEP 4] Smart Refresh & WASM
        func forceUpdatePins() {
            let createCoord = parent.creatingTodoLocation
            let summary = "\(parent.allItems.count)-\(createCoord?.latitude ?? 0)-\(createCoord?.longitude ?? 0)-\(isInitialPhase)"
            
            // If summary matches, we still check if engine just resumed
            if lastDataSummary != summary {
                lastDataSummary = summary
                
                print(">>> MAIN MAP [STEP 4]: Data Changed -> Refreshing (InitiPhase: \(isInitialPhase))")
                Task { @MainActor in
                    refreshWasmClusters(force: true)
                }
            }
        }

        @MainActor
        func refreshWasmClusters(force: Bool = false) {
            guard let mapView = controller?.getView("mapview") as? KakaoMap else { return }
            
            // 1. Calculate Radius & Metrics
            let zoom = Double(mapView.zoomLevel)
            let centerLat = mapView.getPosition(CGPoint(x: mapView.viewRect.width/2, y: mapView.viewRect.height/2)).wgsCoord.latitude
            let metersPerPixel = 156543.03392 * cos(centerLat * .pi / 180.0) / pow(2, zoom)
            let wasmCellSize = metersPerPixel * 100.0
            
            // [NEW] 1.5x Threshold Check
            let currentWm = metersPerPixel * mapView.viewRect.width
            if !force && !isInitialPhase && lastClusteredWm > 0 {
                let ratio = currentWm / lastClusteredWm
                // If change is within 0.66 ~ 1.5, SKIP clustering
                if ratio > 0.6666 && ratio < 1.5 {
                    return
                }
            }
            lastClusteredWm = currentWm 
            
            // 2. Prepare Data
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
                }
            }
            
            // [NEW] Add Creating Todo Location if active
            if let target = parent.creatingTodoLocation {
                let newItem = ToDoItem(todo_name: "New Entry", latitude: target.latitude, longitude: target.longitude)
                allItemsToProcess.append(.todo(newItem))
                rawPoints.append(Int32(target.latitude * 100_000))
                rawPoints.append(Int32(target.longitude * 100_000))
            }
            
            // 3. Request WASM Clustering
            Task {
                if self.isInitialPhase {
                    // [FIX] Initial 3 seconds: Show all pins raw (no clustering)
                    await MainActor.run {
                        print(">>> MAIN MAP [STEP 5]: Initial Phase -> Rendering Raw Items")
                        self.renderRawItems(mapView: mapView, allItems: allItemsToProcess)
                    }
                    return
                }
                
                let result = await WasmManager.shared.cluster(points: rawPoints, cellSize: wasmCellSize)
                await MainActor.run {
                    print(">>> MAIN MAP [STEP 4]: WASM Clusters found: \(result.count / 3)")
                    if result.isEmpty {
                        self.renderRawItems(mapView: mapView, allItems: allItemsToProcess) // Fallback to raw
                    } else {
                        self.renderClusters(mapView: mapView, clusterResult: result, allItems: allItemsToProcess)
                    }
                }
            }
        }

        @MainActor
        private func renderRawItems(mapView: KakaoMap, allItems: [UnifiedMapItem]) {
             let labelManager = mapView.getLabelManager()
             labelManager.removeLabelLayer(layerID: "todoLayer")
             guard let layer = labelManager.addLabelLayer(option: LabelLayerOptions(layerID: "todoLayer", competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 20000)) else { return }
             
             labelIdToClusterItems.removeAll()
             var newRawIDs: Set<String> = [] // [FIX] Track Raw Pins
             for (idx, item) in allItems.enumerated() {
                 guard let loc = item.location else { continue }
                 let (pinType, color, _) = MapLogicHelper.resolveClusterStyle(items: [item])
                 let styleID = "RawStyle_\(pinType)"
                 
                 if !registeredStyleIDs.contains(styleID) {
                      // Kakao Scale: 0.7x (28x35)
                      // Use fetchPin directly
                      if let baseImage = PinImageHelper.shared.fetchPin(type: pinType) {
                          let resized = baseImage.resized(to: CGSize(width: 28, height: 35))
                          // Kakao Scale: 0.7x (28x35) for Base
                          // Unbadged (Raw) -> Center Anchor (0.5)
                          let anchorX = 0.5
                          labelManager.addPoiStyle(PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: PoiIconStyle(symbol: resized ?? baseImage, anchorPoint: CGPoint(x: anchorX, y: 1.0)), level: 0)]))
                          registeredStyleIDs.insert(styleID)
                      }
                 }
                 
                 let poiID = "raw_\(idx)"
                 labelIdToClusterItems[poiID] = [item]
                 let options = PoiOptions(styleID: styleID, poiID: poiID)
                 options.clickable = true
                 if let poi = layer.addPoi(option: options, at: MapPoint(longitude: loc.longitude, latitude: loc.latitude)) {
                     poi.clickable = true
                     poi.show()
                     // [FIX] Track Raw ID for cleanup
                     newRawIDs.insert(poiID)
                 }
             }
             // [FIX] Update Tracker
             self.activePoiIDs = newRawIDs
        }

        @MainActor
        private func renderClusters(mapView: KakaoMap, clusterResult: [Int32], allItems: [UnifiedMapItem]) {
            let labelManager = mapView.getLabelManager()
            // Reuse Layer if exists
            let layer = labelManager.getLabelLayer(layerID: "todoLayer") ?? labelManager.addLabelLayer(option: LabelLayerOptions(layerID: "todoLayer", competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 20000))
            guard let validLayer = layer else { return }
            
            labelIdToClusterItems.removeAll()
            
            // 1. Prepare Data Structure
            struct ClusterGroup {
                var centroidLat: Double
                var centroidLon: Double
                var items: [UnifiedMapItem] = []
            }
            
            let clusterCount = clusterResult.count / 3
            var clusterGroups: [ClusterGroup] = []
            
            // Parse Centroids
            for i in 0..<clusterCount {
                let lat = Double(clusterResult[i*3]) / 100_000.0
                let lon = Double(clusterResult[i*3+1]) / 100_000.0
                // count is unused here as we recalculate from assigned items
                clusterGroups.append(ClusterGroup(centroidLat: lat, centroidLon: lon))
            }
            
            // 2. Assign items to centroids
            for item in allItems {
                var itemLat: Double = 0; var itemLon: Double = 0
                switch item {
                case .todo(let t): if let l = t.location { itemLat = l.latitude; itemLon = l.longitude }
                case .history(let l): itemLat = l.latitude; itemLon = l.longitude
                case .userLocation(let coord): itemLat = coord.latitude; itemLon = coord.longitude
                default: continue
                }
                
                var bestIdx = -1
                var minDist = Double.greatestFiniteMagnitude
                for (idx, group) in clusterGroups.enumerated() {
                    let dLat = itemLat - group.centroidLat
                    let dLon = itemLon - group.centroidLon
                    let dist = dLat*dLat + dLon*dLon
                    if dist < minDist { minDist = dist; bestIdx = idx }
                }
                if bestIdx >= 0 { clusterGroups[bestIdx].items.append(item) }
            }
            
            // 3. Filter & Sort for Stability (Avoid "Jumping" Pins)
            // [FIX] Sort by Latitude (desc) then Longitude (asc) to ensure 'poi_0' is always top-left
            // usage of enumerated index as ID requires stable order.
            var validClusters = clusterGroups.filter { !$0.items.isEmpty }
            validClusters.sort {
                if abs($0.centroidLat - $1.centroidLat) > 0.00001 {
                    return $0.centroidLat > $1.centroidLat // North to South
                }
                return $0.centroidLon < $1.centroidLon // West to East
            }
            
            // 4. Render (Diffing)
            var newPoiIDs: Set<String> = []
            let userPoiID = "UserPoi"
            
            for (idx, group) in validClusters.enumerated() {
                var finalLon = group.centroidLon
                var finalLat = group.centroidLat
                let items = group.items
                
                // Style
                let (pinType, color, count) = MapLogicHelper.resolveClusterStyle(items: items)
                let colorHex = color.cgColor.components?.map { String(format: "%02X", Int($0 * 255)) }.joined() ?? "000000"
                
                // [FIX] Check for single item to use precise location (visual accuracy)
                if count == 1 {
                    switch items[0] {
                    case .todo(let t): if let l = t.location { finalLat = l.latitude; finalLon = l.longitude }
                    case .history(let l): finalLat = l.latitude; finalLon = l.longitude
                    case .userLocation(let coord): finalLat = coord.latitude; finalLon = coord.longitude
                    default: break
                    }
                }
                
                let styleID = "Style_\(pinType)_\(count)_\(colorHex)"
                
                // Check if this cluster contains user
                var isUser = false
                if let _ = items.first(where: { if case .userLocation = $0 { return true }; return false }) {
                    // For User Cluster, we might want to prioritize user location? 
                    // Usually user is single, but if clustered, use centroid (or user loc provided by single check above)
                    isUser = true
                }
                
                // Determine POI ID
                // User Pin gets FIXED ID for reuse
                let poiID = isUser ? userPoiID : "poi_\(idx)"
                newPoiIDs.insert(poiID)
                labelIdToClusterItems[poiID] = items
                
                // Prepare Style
                if !registeredStyleIDs.contains(styleID) {
                    let targetSize = CGSize(width: 28, height: 35)
                    let finalImage: UIImage?
                    if count > 1 {
                        if let baseImage = PinImageHelper.shared.fetchPin(type: pinType),
                           let resizedBase = baseImage.resized(to: targetSize) {
                             finalImage = PinImageHelper.shared.applyBadge(to: resizedBase, count: count, badgeColor: color, badgeSize: 14).rasterized()
                        } else { finalImage = nil }
                    } else {
                         if let baseImage = PinImageHelper.shared.fetchPin(type: pinType) {
                             finalImage = baseImage.resized(to: targetSize)
                         } else { finalImage = nil }
                    }
                    
                    if let img = finalImage {
                        // Badged: Base(28) + 10 = 38. Tip at 14. Anchor = 14/38.
                        // Unbadged: Base(28). Tip at 14. Anchor = 14/28 = 0.5.
                        let anchorX = count > 1 ? (14.0 / 38.0) : 0.5
                        let anchor = CGPoint(x: anchorX, y: 1.0)
                        labelManager.addPoiStyle(PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: PoiIconStyle(symbol: img, anchorPoint: anchor), level: 0)]))
                        registeredStyleIDs.insert(styleID)
                    }
                }
                
                // Add or Move
                if let existingPoi = validLayer.getPoi(poiID: poiID) {
                    // Reuse (Move + Style Update)
                    existingPoi.moveAt(MapPoint(longitude: finalLon, latitude: finalLat), duration: 200) // 200ms smoothing
                    existingPoi.changeStyle(styleID: styleID, enableTransition: false)
                    existingPoi.show()
                } else {
                    // Create New
                    let options = PoiOptions(styleID: styleID, poiID: poiID)
                    options.clickable = true
                    if let poi = validLayer.addPoi(option: options, at: MapPoint(longitude: finalLon, latitude: finalLat)) {
                        poi.clickable = true
                        poi.show()
                    }
                }
            }
            
            // 5. Cleanup Old POIs
            for oldID in activePoiIDs {
                if !newPoiIDs.contains(oldID) {
                    validLayer.removePoi(poiID: oldID)
                }
            }
            activePoiIDs = newPoiIDs
        }

        @MainActor
        // [FIX] Redundant Logic Removed: Merged into refreshWasmClusters
//        func syncCreatingTodoPin() {
//            guard let mapView = controller?.getView("mapview") as? KakaoMap else { return }
//            let labelManager = mapView.getLabelManager()
//            labelManager.removeLabelLayer(layerID: "creLayer")
//            
//            guard let loc = parent.creatingTodoLocation else { return }
//            let layer = labelManager.addLabelLayer(option: LabelLayerOptions(layerID: "creLayer", competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 10000))
//            
//            let styleID = "creStyle"
//            if !registeredStyleIDs.contains(styleID) {
//                if let img = UIImage(named: "PinTodoReady")?.resized(to: CGSize(width: 28, height: 35)),
//                   let rasterized = img.rasterized() {
//                    labelManager.addPoiStyle(PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: PoiIconStyle(symbol: rasterized, anchorPoint: CGPoint(x: 0.5, y: 1.0)), level: 0)]))
//                    registeredStyleIDs.insert(styleID)
//                }
//            }
//            
//            let options = PoiOptions(styleID: styleID, poiID: "cre_pin")
//            options.clickable = true
//            if let poi = layer?.addPoi(option: options, at: MapPoint(longitude: loc.longitude, latitude: loc.latitude)) {
//                poi.clickable = true
//                poi.show()
//            }
//        }

        // MARK: - KakaoMapEventDelegate
        @objc func poiDidTapped(kakaoMap: KakaoMap, layerID: String, poiID: String, position: MapPoint) {
            if poiID == "cre_pin" || poiID == "creation_pin" { return }
            
            if let items = labelIdToClusterItems[poiID] {
                // [FIX] Faster Animation (200ms) for snappy "Apple-like" response
                kakaoMap.animateCamera(cameraUpdate: CameraUpdate.make(target: position, mapView: kakaoMap), options: CameraAnimationOptions(autoElevation: false, consecutive: true, durationInMillis: 200))
                
                Task { @MainActor in
                    // [FIX] Instant response: No delay for SwiftUI redraw
                    let center = CGPoint(x: kakaoMap.viewRect.size.width / 2, y: kakaoMap.viewRect.size.height / 2)
                    self.parent.tapPosition = center
                    self.parent.selectedClusterItems = items
                }
            }
        }

        @objc func terrainDidTapped(kakaoMap: KakaoMap, position: MapPoint) {
            print(">>> SUCCESS: terrainDidTapped")
        }

        @objc func terrainDidLongPressed(kakaoMap: KakaoMap, position: MapPoint) {
            print(">>> SUCCESS: terrainDidLongPressed")
            let coord = CLLocationCoordinate2D(latitude: position.wgsCoord.latitude, longitude: position.wgsCoord.longitude)
            Task { @MainActor in
                parent.onLongTap?(coord)
            }
        }

        @objc func cameraWillMove(kakaoMap: KakaoMap, by: MoveBy) {
            if by != .notUserAction { isUserInteracting = true }
        }
        
        @objc func cameraDidStopped(kakaoMap: KakaoMap, by: MoveBy) {
            if by != .notUserAction { isUserInteracting = false }
            let rot = kakaoMap.rotationAngle * 180.0 / .pi
            NotificationCenter.default.post(name: NSNotification.Name("MapRotationChanged"), object: nil, userInfo: ["rotation": rot])
            
            // [FIX] Update Spans to enable Smart Tethering
            let size = kakaoMap.viewRect.size
            if size.width > 0 && size.height > 0 {
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let target = kakaoMap.getPosition(center)
                let metersPerPixel = 156543.03392 * cos(target.wgsCoord.latitude * .pi / 180.0) / pow(2, Double(kakaoMap.zoomLevel))
                currentSpanLon = Int((metersPerPixel * Double(size.width)) / 111320.0 * 100_000.0)
                currentSpanLat = Int((metersPerPixel * Double(size.height)) / 111320.0 * 100_000.0)
            }
            
            // [FIX] Trigger clustering on idle (Subject to Threshold)
            Task { @MainActor in
                refreshWasmClusters(force: false)
            }
        }


        func checkTethering(mapView: KakaoMap, userLocation: CLLocation) {
            if isLaunchAnimating || firstRender || isUserInteracting { return }
            let uInt = SmartLocationManager.shared.toIntLocation(userLocation)
            if moveLocation == nil { moveLocation = uInt; return }
            
            if moveLocation == nil {
                moveLocation = uInt
                return
            }
            
            if SmartLocationManager.shared.shouldRecenter(user: uInt, moveLoc: moveLocation!, hLen: currentSpanLon, vLen: currentSpanLat) {
                let pos = MapPoint(longitude: userLocation.coordinate.longitude, latitude: userLocation.coordinate.latitude)
                mapView.animateCamera(cameraUpdate: CameraUpdate.make(target: pos, zoomLevel: mapView.zoomLevel, mapView: mapView), options: CameraAnimationOptions(autoElevation: false, consecutive: true, durationInMillis: 600))
                moveLocation = uInt
            }

        }

        func handleAction(_ action: MapAction) {
            guard let mapView = controller?.getView("mapview") as? KakaoMap else { return }
            print(">>> MAIN MAP ACTION: \(action)")
            switch action {
            case .zoomIn: mapView.moveCamera(CameraUpdate.make(zoomLevel: mapView.zoomLevel + 1, mapView: mapView))
            case .zoomOut: mapView.moveCamera(CameraUpdate.make(zoomLevel: mapView.zoomLevel - 1, mapView: mapView))
            case .currentLocation:
                if let loc = parent.locationManager.currentLocation {
                    let pos = MapPoint(longitude: loc.coordinate.longitude, latitude: loc.coordinate.latitude)
                    // [FIX] Update zoomLevel to 18 as requested
                    mapView.animateCamera(cameraUpdate: CameraUpdate.make(target: pos, zoomLevel: 18, mapView: mapView), options: CameraAnimationOptions(autoElevation: false, consecutive: true, durationInMillis: 500))
                    moveLocation = SmartLocationManager.shared.toIntLocation(loc)
                }
            case .rotateNorth:
                mapView.moveCamera(CameraUpdate.make(rotation: 0, tilt: 0, mapView: mapView))
            default: break
            }
        }
        
        func updatePath(mapView: KakaoMap, historyItem: ToDoItem?) {
            let manager = mapView.getShapeManager()
            let layer = manager.getShapeLayer(layerID: "pathLayer") ?? manager.addShapeLayer(layerID: "pathLayer", zOrder: 1000)
            guard let shapeLayer = layer else { return }
            
            guard let item = historyItem, item.type == "00" else {
                shapeLayer.removeMapPolylineShape(shapeID: "historyLine")
                return
            }
            
            // Query PathItems
            let searchID = item.todo_id
            let descriptor = FetchDescriptor<PathItem>(
                predicate: #Predicate<PathItem> { $0.todo_id == searchID },
                sortBy: [SortDescriptor<PathItem>(\.time, order: .forward)]
            )
            
            if let paths = try? parent.modelContext.fetch(descriptor), !paths.isEmpty {
                let coords = paths.map { MapPoint(longitude: $0.coordinate.longitude, latitude: $0.coordinate.latitude) }
                if coords.count >= 2 {
                    // Unique style for the path
                    let styleID = "MainPathStyle_\(item.todo_id.uuidString.prefix(8))"
                    let style = PolylineStyle(styles: [
                        PerLevelPolylineStyle(bodyColor: .red, bodyWidth: 3, strokeColor: .clear, strokeWidth: 0, level: 0)
                    ])
                    manager.addPolylineStyleSet(PolylineStyleSet(styleSetID: styleID, styles: [style]))


                    
                    shapeLayer.removeMapPolylineShape(shapeID: "historyLine")
                    let options = MapPolylineShapeOptions(shapeID: "historyLine", styleID: styleID, zOrder: 0)
                    options.polylines.append(MapPolyline(line: coords, styleIndex: 0))
                    
                    if let shape = shapeLayer.addMapPolylineShape(options) {
                        shape.show()
                        print(">>> MAIN KAKAO: Path Rendered (\(coords.count) pts)")
                    }
                }
            } else {
                shapeLayer.removeMapPolylineShape(shapeID: "historyLine")
            }
        }
        
        func updateActiveRecordingPath(mapView: KakaoMap, points: [PathPoint], visible: Bool) {
            let manager = mapView.getShapeManager()
            let layer = manager.getShapeLayer(layerID: "activePathLayer") ?? manager.addShapeLayer(layerID: "activePathLayer", zOrder: 1100)
            guard let shapeLayer = layer else { return }
            
            shapeLayer.removeMapPolylineShape(shapeID: "activeTrail")
            
            guard visible && points.count >= 2 else { return }
            
            // Convert Int32 -> MapPoint (Double)
            let coords = points.map { MapPoint(longitude: Double($0.longitude)/100_000.0, latitude: Double($0.latitude)/100_000.0) }
            
            let styleID = "ActiveTrailStyle"
            // Re-register only if needed (Optimized)
            // Re-register only if needed (Optimized)
            // Note: getPolylineStyleSet API missing. Just adding.
            let style = PolylineStyle(styles: [
                PerLevelPolylineStyle(bodyColor: UIColor(red: 1.0, green: 0.34, blue: 0.13, alpha: 1.0), bodyWidth: 3, strokeColor: .clear, strokeWidth: 0, level: 0)
            ])
            manager.addPolylineStyleSet(PolylineStyleSet(styleSetID: styleID, styles: [style]))
            
            let options = MapPolylineShapeOptions(shapeID: "activeTrail", styleID: styleID, zOrder: 0)
            options.polylines.append(MapPolyline(line: coords, styleIndex: 0))
            
            if let shape = shapeLayer.addMapPolylineShape(options) {
                shape.show()
            }
        }
        
        func zoomToHistoryPath(mapView: KakaoMap, item: ToDoItem?) {
            guard let item = item else { return }
            let searchID = item.todo_id
            let descriptor = FetchDescriptor<PathItem>(
                predicate: #Predicate<PathItem> { $0.todo_id == searchID },
                sortBy: [SortDescriptor<PathItem>(\.time)]
            )
            if let paths = try? parent.modelContext.fetch(descriptor), !paths.isEmpty {
                // [FIX] Use GeomUtils for integer-based Fit Bounds
                let intRect = GeomUtils.calculateIntBoundingBox(from: paths)
                
                let southWest = MapPoint(longitude: Double(intRect.minLon) / 100_000.0, 
                                         latitude: Double(intRect.minLat) / 100_000.0)
                let northEast = MapPoint(longitude: Double(intRect.maxLon) / 100_000.0, 
                                         latitude: Double(intRect.maxLat) / 100_000.0)
                
                let area = AreaRect(southWest: southWest, northEast: northEast)
                mapView.animateCamera(cameraUpdate: CameraUpdate.make(area: area), 
                                      options: CameraAnimationOptions(autoElevation: false, consecutive: false, durationInMillis: 800))
            }
        }
    }


    static func dismantleUIView(_ uiView: KMViewContainer, coordinator: Coordinator) {
        coordinator.controller?.pauseEngine()
        coordinator.controller?.resetEngine()
    }
}
