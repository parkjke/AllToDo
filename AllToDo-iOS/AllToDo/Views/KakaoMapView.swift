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
    @Binding var targetLocation: CLLocationCoordinate2D? // [NEW] For search
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

        var lastHistoryID: UUID? = nil
        var lastClusteredWm: Double = -1.0 // [NEW] 1.5x Threshold Tracking

        
        init(_ parent: KakaoMapView) {
            self.parent = parent
        }

        deinit {
            NotificationCenter.default.removeObserver(self)
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
            
            // [FIX] Robust Resume Fix: Ensure activation
            NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
                guard let self = self else { return }
                guard UIApplication.shared.applicationState == .active else { return }
                
                print(">>> MAIN MAP: App Active (Resume) -> Triggering Engine Activation")
                self.checkEngineActivation()
                
                // [FIX] Removed immediate forceUpdatePins here to prevent KMInitializeException.
                // activation sequence in checkEngineActivation already handles the 0.5s delayed refresh.
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
            guard let controller = controller, controller.isEngineActive else { return } // [FIX] Guard Active Engine
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
            // [FIX] Engine Guard: Prevent KMInitializeException
            guard let controller = controller, controller.isEngineActive else { return }
            guard let mapView = controller.getView("mapview") as? KakaoMap else { return }
            
            // 1. Calculate Radius & Metrics
            let zoom = Double(mapView.zoomLevel)
            let centerLat = mapView.getPosition(CGPoint(x: mapView.viewRect.width/2, y: mapView.viewRect.height/2)).wgsCoord.latitude
            let metersPerPixel = 156543.03392 * cos(centerLat * .pi / 180.0) / pow(2, zoom)
            let wasmCellSize = metersPerPixel * 30.0 // [MODIFIED] Reduced to 30.0
            
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
            var rawPoints: [Int] = []
            
            for item in parent.allItems {
                switch item {
                case .todo(let t):
                    if t.latitude.isNaN || t.longitude.isNaN { continue } // [NEW] NaN Guard
                    allItemsToProcess.append(item)
                    rawPoints.append(t.int_lat)
                    rawPoints.append(t.int_long)
                case .history(let log):
                    if log.latitude.isNaN || log.longitude.isNaN { continue } // [NEW] NaN Guard
                    allItemsToProcess.append(item)
                    rawPoints.append(log.int_lat)
                    rawPoints.append(log.int_long)
                case .userLocation(let coord):
                    if coord.latitude.isNaN || coord.longitude.isNaN { continue } // [NEW] NaN Guard
                    allItemsToProcess.append(item)
                    rawPoints.append(Int(coord.latitude * 100_000))
                    rawPoints.append(Int(coord.longitude * 100_000))
                default: break
                }
            }
            
            // [NEW] Add Creating Todo Location if active
            if let target = parent.creatingTodoLocation {
                let newItem = ToDoItem(todo_name: "New Entry", latitude: target.latitude, longitude: target.longitude)
                allItemsToProcess.append(.todo(newItem))
                rawPoints.append(Int(target.latitude * 100_000))
                rawPoints.append(Int(target.longitude * 100_000))
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
        private func renderClusters(mapView: KakaoMap, clusterResult: [Int], allItems: [UnifiedMapItem]) {
            let labelManager = mapView.getLabelManager()
            let layer = labelManager.getLabelLayer(layerID: "todoLayer") ?? labelManager.addLabelLayer(option: LabelLayerOptions(layerID: "todoLayer", competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 20000))
            guard let validLayer = layer else { return }
            
            labelIdToClusterItems.removeAll()
            
            struct Centroid { let lat: Double; let lon: Double }
            var centroids: [Centroid] = []
            if clusterResult.count % 3 == 0 {
                for i in stride(from: 0, to: clusterResult.count, by: 3) {
                    let lat = Double(clusterResult[i]) / 100_000.0
                    let lon = Double(clusterResult[i+1]) / 100_000.0
                    if lat.isNaN || lon.isNaN { continue } // [NEW] NaN Guard
                    centroids.append(Centroid(lat: lat, lon: lon))
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
            var newPoiIDs: Set<String> = []
            
            // Collect single and cluster groups
            var singles: [UnifiedMapItem] = []
            var clusterGroups: [(centroid: Centroid, items: [UnifiedMapItem])] = []
            
            for (idx, items) in clusters.enumerated() {
                if items.count == 1 {
                    singles.append(items[0])
                } else if items.count > 1 {
                    clusterGroups.append((centroids[idx], items))
                }
            }
            
            // Step 1: New Entry (Singles)
            for item in singles {
                var isUserLocation = false
                if case .userLocation = item { isUserLocation = true }
                let poiID = isUserLocation ? "UserPoi" : "pin_\(item.id.uuidString.prefix(8))"
                newPoiIDs.insert(poiID)
                labelIdToClusterItems[poiID] = [item]
                
                let (pinType, color, _) = MapLogicHelper.resolveClusterStyle(items: [item])
                let styleID = "Style_Single_\(pinType)"
                
                if !registeredStyleIDs.contains(styleID) {
                    if let img = PinImageHelper.shared.fetchPin(type: pinType) {
                        let resized = img.resized(to: CGSize(width: 28, height: 35))
                        labelManager.addPoiStyle(PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: PoiIconStyle(symbol: resized ?? img, anchorPoint: CGPoint(x: 0.5, y: 1.0)), level: 0)]))
                        registeredStyleIDs.insert(styleID)
                    }
                }
                
                let targetPos = MapPoint(longitude: item.location?.longitude ?? 0, latitude: item.location?.latitude ?? 0)
                if let existing = validLayer.getPoi(poiID: poiID) {
                    existing.moveAt(targetPos, duration: 200)
                    existing.changeStyle(styleID: styleID, enableTransition: false)
                    existing.show()
                } else {
                    let opt = PoiOptions(styleID: styleID, poiID: poiID)
                    opt.clickable = true
                    validLayer.addPoi(option: opt, at: targetPos)?.show()
                }
            }
            
            // Step 2 & 3: Removal (Old pins/clusters not in new set)
            // Handled at the end by comparing activePoiIDs and newPoiIDs
            
            // Step 4: New Cluster Entry
            for (idx, group) in clusterGroups.enumerated() {
                let items = group.items
                let (pinType, color, count) = MapLogicHelper.resolveClusterStyle(items: items)
                let colorHex = color.cgColor.components?.map { String(format: "%02X", Int($0 * 255)) }.joined() ?? "000000"
                
                var isUser = items.contains(where: { if case .userLocation = $0 { return true }; return false })
                var finalLat = group.centroid.lat
                var finalLon = group.centroid.lon
                
                if isUser, let userLoc = items.first(where: { if case .userLocation = $0 { return true }; return false })?.location {
                    finalLat = userLoc.latitude
                    finalLon = userLoc.longitude
                }
                
                let poiID = isUser ? "UserPoi" : "cluster_\(idx)"
                newPoiIDs.insert(poiID)
                labelIdToClusterItems[poiID] = items
                
                let styleID = "Style_Cluster_\(pinType)_\(count)_\(colorHex)"
                if !registeredStyleIDs.contains(styleID) {
                    let targetSize = CGSize(width: 28, height: 35)
                    if let base = PinImageHelper.shared.fetchPin(type: pinType),
                       let resized = base.resized(to: targetSize) {
                        
                        let isBadged = !isUser && count > 1
                        if isBadged {
                            let badged = PinImageHelper.shared.applyBadge(to: resized, count: count, badgeColor: color, badgeSize: 14)
                            let anchor = CGPoint(x: 14.0/38.0, y: 1.0)
                            labelManager.addPoiStyle(PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: PoiIconStyle(symbol: badged, anchorPoint: anchor), level: 0)]))
                        } else {
                            labelManager.addPoiStyle(PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: PoiIconStyle(symbol: resized, anchorPoint: CGPoint(x: 0.5, y: 1.0)), level: 0)]))
                        }
                        registeredStyleIDs.insert(styleID)
                    }
                }
                
                let targetPos = MapPoint(longitude: finalLon, latitude: finalLat)
                if let existing = validLayer.getPoi(poiID: poiID) {
                    existing.moveAt(targetPos, duration: 200)
                    existing.changeStyle(styleID: styleID, enableTransition: false)
                    existing.show()
                } else {
                    let opt = PoiOptions(styleID: styleID, poiID: poiID)
                    opt.clickable = true
                    validLayer.addPoi(option: opt, at: targetPos)?.show()
                }
            }
            
            // Final Cleanup
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
                // [ROBUST FIX] Calculate Offset without map-to-screen API
                // This version uses ONLY getPosition(CGPoint) which is verified to exist.
                let centerX = kakaoMap.viewRect.width / 2
                let centerY = kakaoMap.viewRect.height / 2
                let targetY = centerY + 55 // [FIX] +1pt Shift Down (54 -> 55)
                
                // 1. Get world coordinate at screen center
                let centerPoint = CGPoint(x: centerX, y: centerY)
                let centerCoord = kakaoMap.getPosition(centerPoint).wgsCoord
                
                // 2. Get world coordinate at target point (60pt below center)
                let targetPoint = CGPoint(x: centerX, y: targetY)
                let offsetCoord = kakaoMap.getPosition(targetPoint).wgsCoord
                
                // 3. Calculate Delta Vector (Degrees per 60pt)
                let deltaLat = offsetCoord.latitude - centerCoord.latitude
                let deltaLon = offsetCoord.longitude - centerCoord.longitude
                
                // 4. New Camera Center: Pin position - Delta
                let newCenterCoord = MapPoint(longitude: position.wgsCoord.longitude - deltaLon,
                                             latitude: position.wgsCoord.latitude - deltaLat)
                
                // 5. Instant Selection (No delay)
                self.parent.tapPosition = CGPoint(x: centerX, y: centerY)
                self.parent.selectedClusterItems = items
                self.parent.selectedItem = nil
                
                // 6. Impact Feedback
                let generator = UIImpactFeedbackGenerator(style: .medium)
                generator.impactOccurred()
                
                // 7. Animate to Offset Position
                kakaoMap.animateCamera(cameraUpdate: CameraUpdate.make(target: newCenterCoord, mapView: kakaoMap), options: CameraAnimationOptions(autoElevation: false, consecutive: true, durationInMillis: 200))
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
            case .moveToLocation:
                if let loc = parent.targetLocation {
                    let pos = MapPoint(longitude: loc.longitude, latitude: loc.latitude)
                    mapView.animateCamera(cameraUpdate: CameraUpdate.make(target: pos, zoomLevel: 18, mapView: mapView), options: CameraAnimationOptions(autoElevation: false, consecutive: true, durationInMillis: 500))
                }
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
