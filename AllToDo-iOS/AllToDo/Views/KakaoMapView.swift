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
    
    func makeUIView(context: Context) -> KMViewContainer {
        let view = KMViewContainer()
        view.backgroundColor = UIColor.white.withAlphaComponent(0.01) // Invisible fill for hits
        view.isUserInteractionEnabled = true
        
        context.coordinator.createController(view)
        context.coordinator.locationManager = locationManager
        context.coordinator.selectedItemBinding = $selectedItem
        context.coordinator.selectedClusterBinding = $selectedClusterItems
        context.coordinator.onLongTap = onLongTap
        
        return view
    }

    func updateUIView(_ uiView: KMViewContainer, context: Context) {
        // 1. Sync Bindings
        context.coordinator.selectedItemBinding = $selectedItem
        context.coordinator.selectedClusterBinding = $selectedClusterItems
        context.coordinator.onLongTap = onLongTap
        
        // 2. Push Data Updates
        context.coordinator.updatePins(items: todoItems, logs: userLogs)
        
        // 3. Handle Actions
        if action != .none {
            context.coordinator.handleAction(action)
            DispatchQueue.main.async { action = .none }
        }
    }

    func makeCoordinator() -> Coordinator {
        return Coordinator()
    }

    static func dismantleUIView(_ uiView: KMViewContainer, coordinator: Coordinator) {
        coordinator.controller?.pauseEngine()
        coordinator.controller?.resetEngine()
    }

    // MARK: - Coordinator Class
    class Coordinator: NSObject, MapControllerDelegate, KakaoMapEventDelegate {
        var controller: KMController?
        weak var viewContainer: KMViewContainer?
        var locationManager: AppLocationManager?
        
        // Data & Bindings
        var selectedItemBinding: Binding<ToDoItem?>?
        var selectedClusterBinding: Binding<[UnifiedMapItem]?>?
        var onLongTap: ((CLLocationCoordinate2D) -> Void)?
        
        var currentItems: [ToDoItem] = []
        var currentLogs: [UserLog] = []
        
        // Lookup Tables
        var labelIdToItems: [String: UnifiedMapItem] = [:]
        var labelIdToClusterItems: [String: [UnifiedMapItem]] = [:]
        
        // Styles to Register
        let pinAssets = [
            "PinCurrent", "PinHistory", "PinTodoReady", "PinTodoDone",
            "PinTodoCancel", "PinTodoFail", "PinReceiveReady",
            "PinReceiveDone", "PinReceiveReject"
        ]
        
        override init() {
            super.init()
            // Lifecycle Observers
            NotificationCenter.default.addObserver(self, selector: #selector(appDidBecomeActive), name: UIApplication.didBecomeActiveNotification, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(appWillResignActive), name: UIApplication.willResignActiveNotification, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(appDidEnterBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)
        }
        
        deinit {
            NotificationCenter.default.removeObserver(self)
        }
        
        // MARK: - Engine Lifecycle
        func createController(_ view: KMViewContainer) {
            self.viewContainer = view
            controller = KMController(viewContainer: view)
            controller?.delegate = self
            controller?.prepareEngine()
        }
        
        @objc func appWillResignActive() {
            controller?.pauseEngine()
        }
        @objc func appDidEnterBackground() {
            controller?.pauseEngine()
        }
        @objc func appDidBecomeActive() {
            if controller?.isEngineActive == false {
                controller?.activateEngine()
                // Auto refresh after resume
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    self.refreshClusters()
                }
            }
        }
        
        // MARK: - MapControllerDelegate
        func addViews() {
            // [Standard] Start at User Location immediately
            let defaultPos: MapPoint
            if let loc = locationManager?.currentLocation {
                defaultPos = MapPoint(longitude: loc.coordinate.longitude, latitude: loc.coordinate.latitude)
            } else {
                defaultPos = MapPoint(longitude: 126.978365, latitude: 37.566691)
            }
            
            let mapviewInfo = MapviewInfo(viewName: "mapview", viewInfoName: "map", defaultPosition: defaultPos, defaultLevel: 12)
            
            if let controller = controller {
                controller.addView(mapviewInfo)
            }
        }
        
        func addViewSucceeded(_ viewName: String, viewInfoName: String) {
            print("KakaoMap: Engine Ready & View Added")
            controller?.activateEngine()
            
            if let mapView = controller?.getView("mapview") as? KakaoMap {
                mapView.eventDelegate = self // [CRITICAL] Connect Tap Events
                
                // Initialize Styles
                let labelManager = mapView.getLabelManager()
                registerAllPinStyles(labelManager: labelManager)
                
                // Initial Render
                refreshClusters()
                
                // [Animation] 3-Second Zoom (Apple Map Style)
                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) { [weak self] in
                    guard let self = self else { return }
                    if let loc = self.locationManager?.currentLocation {
                        let pos = MapPoint(longitude: loc.coordinate.longitude, latitude: loc.coordinate.latitude)
                        let update = CameraUpdate.make(target: pos, zoomLevel: 15, rotation: 0, tilt: 0, mapView: mapView)
                        let options = CameraAnimationOptions(autoElevation: true, consecutive: false, durationInMillis: 1500)
                        mapView.animateCamera(cameraUpdate: update, options: options)
                    }
                }
            }
        }
        
        func containerDidResize(_ size: CGSize) {
            let mapView: KakaoMap? = controller?.getView("mapview") as? KakaoMap
            mapView?.viewRect = CGRect(origin: .zero, size: size)
        }
        
        // MARK: - Styles & Data
        func registerAllPinStyles(labelManager: LabelManager) {
            for assetName in pinAssets {
                let styleID = "style_" + assetName
                if let image = UIImage(named: assetName) {
                    // Resize to standard size (e.g., width 40)
                    let targetWidth: CGFloat = 40
                    let ratio = image.size.height / image.size.width
                    let targetSize = CGSize(width: targetWidth, height: targetWidth * ratio)
                    let finalImage = image.resized(to: targetSize) ?? image
                    
                    let iconStyle = PoiIconStyle(symbol: finalImage, anchorPoint: CGPoint(x: 0.5, y: 1.0))
                    let perLevel = PerLevelPoiStyle(iconStyle: iconStyle, level: 0)
                    let style = PoiStyle(styleID: styleID, styles: [perLevel])
                    labelManager.addPoiStyle(style)
                }
            }
        }
        
        func updatePins(items: [ToDoItem], logs: [UserLog]) {
            self.currentItems = items
            self.currentLogs = logs
            refreshClusters()
        }
        
        func refreshClusters() {
            guard let controller = controller else { return }
            guard let mapView = controller.getView("mapview") as? KakaoMap else { return }
            let labelManager = mapView.getLabelManager()
            
            // 1. Prepare Data
            var allUnified: [UnifiedMapItem] = []
            for item in currentItems { if let _ = item.location { allUnified.append(.todo(item)) } }
            for log in currentLogs { allUnified.append(.history(log)) }
            
            // 2. Wasm Data
            var rawPoints: [Int32] = []
            for item in allUnified {
                if let loc = item.location {
                    rawPoints.append(Int32(loc.latitude * 1_000_000))
                    rawPoints.append(Int32(loc.longitude * 1_000_000))
                }
            }
            
            // 3. Cell Size
            let zoom = mapView.zoomLevel
            let resolution = 156543.03392 * cos(37.5 * .pi / 180) / pow(2.0, Double(zoom))
            let cellSize = 120.0 * resolution
            
            // 4. Async Cluster
            Task {
                let clusterResults = await WasmManager.shared.cluster(points: rawPoints, cellSize: cellSize)
                await MainActor.run {
                    self.renderWasmClusters(clusterResults: clusterResults, originalItems: allUnified, labelManager: labelManager, mapView: mapView)
                }
            }
        }
        
        @MainActor
        func renderWasmClusters(clusterResults: [Int32], originalItems: [UnifiedMapItem], labelManager: LabelManager, mapView: KakaoMap) {
            // [Clean Slate]
            if let _ = labelManager.getLabelLayer(layerID: "todoLayer") {
                labelManager.removeLabelLayer(layerID: "todoLayer")
            }
            let layer = labelManager.addLabelLayer(option: LabelLayerOptions(layerID: "todoLayer", competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 10001))
            
            labelIdToItems.removeAll()
            labelIdToClusterItems.removeAll()
            
            // Parse Centroids
            struct Centroid { let lat: Double; let lon: Double; let count: Int }
            var centroids: [Centroid] = []
            let strideVal = 3
            if clusterResults.count % strideVal == 0 {
                for i in stride(from: 0, to: clusterResults.count, by: strideVal) {
                    let lat = Double(clusterResults[i]) / 1_000_000.0
                    let lon = Double(clusterResults[i+1]) / 1_000_000.0
                    let count = Int(clusterResults[i+2])
                    centroids.append(Centroid(lat: lat, lon: lon, count: count))
                }
            }
            
            // Assign Items to Centroids
            var assignments: [[UnifiedMapItem]] = Array(repeating: [], count: centroids.count)
            for item in originalItems {
                guard let loc = item.location else { continue }
                var minDist = Double.greatestFiniteMagnitude
                var bestIdx = -1
                for (idx, c) in centroids.enumerated() {
                    let dLat = loc.latitude - c.lat
                    let dLon = loc.longitude - c.lon
                    let dist = dLat*dLat + dLon*dLon
                    if dist < minDist { minDist = dist; bestIdx = idx }
                }
                if bestIdx >= 0 { assignments[bestIdx].append(item) }
            }
            
            // Render
            for (idx, items) in assignments.enumerated() {
                if items.isEmpty { continue }
                let centroid = centroids[idx]
                let pos = MapPoint(longitude: centroid.lon, latitude: centroid.lat)
                
                if items.count == 1 {
                    // Single -> Use Asset Style
                    let item = items[0]
                    let poiId = "poi_" + item.id.uuidString
                    labelIdToItems[poiId] = item
                    let styleID = "style_" + item.imageName
                    addPoiToLayer(layer, styleID: styleID, poiID: poiId, at: pos, clickable: true)
                } else {
                    // Cluster -> Use Asset Base + Count
                    let clusterID = UUID().uuidString
                    let poiId = "cluster_" + clusterID
                    labelIdToClusterItems[poiId] = items
                    
                    var todoCount = 0
                    var historyCount = 0
                    var hasUser = false
                    for item in items {
                        switch item {
                        case .todo: todoCount += 1
                        case .history: historyCount += 1
                        case .userLocation: hasUser = true
                        default: break
                        }
                    }
                    
                    let isHistoryOrUser = (historyCount > todoCount) || hasUser
                    
                    // [Strategy] Load Base Asset
                    let baseAssetName = isHistoryOrUser ? "PinHistory" : "PinTodoReady"
                    let baseImage = UIImage(named: baseAssetName)
                    
                    // Generate with Count (No Optionals here now)
                    let generatedImage = PinImageHelper.shared.createShieldPin(color: .clear, count: items.count, baseImage: baseImage)
                    if let rasterized = generatedImage.rasterized() {
                        let iconStyle = PoiIconStyle(symbol: rasterized, anchorPoint: CGPoint(x: 0.5, y: 1.0))
                        let styleID = "style_cluster_\(poiId)"
                        let style = PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: iconStyle, level: 0)])
                        labelManager.addPoiStyle(style)
                        
                        addPoiToLayer(layer, styleID: styleID, poiID: poiId, at: pos, clickable: true)
                    }
                }
            }
            
            // Render Path (Logic Disabled for stability)
            if let mapView = controller?.getView("mapview") as? KakaoMap {
                updatePath(mapView: mapView, selectedItems: selectedClusterBinding?.wrappedValue)
            }
        }
        
        func addPoiToLayer(_ layer: LabelLayer?, styleID: String, poiID: String, at pos: MapPoint, clickable: Bool) {
            let options = PoiOptions(styleID: styleID, poiID: poiID)
            options.rank = 0
            options.clickable = clickable
            if let poi = layer?.addPoi(option: options, at: pos) {
                poi.show()
            }
        }
        
        // MARK: - Path Drawing
        func updatePath(mapView: KakaoMap, selectedItems: [UnifiedMapItem]?) {
            // Placeholder: Path drawing logic is temporarily disabled for safety.
            // Will implement correct MapPolyline logic after verification.
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
                    let pos = MapPoint(longitude: loc.coordinate.longitude, latitude: loc.coordinate.latitude)
                    mapView.moveCamera(CameraUpdate.make(target: pos, zoomLevel: 15, mapView: mapView))
                }
            case .rotateNorth:
                mapView.moveCamera(CameraUpdate.make(rotation: 0, tilt: 0, mapView: mapView))
            case .zoomToFit:
                break
            case .launchSequence:
                if let loc = locationManager?.currentLocation {
                     let pos = MapPoint(longitude: loc.coordinate.longitude, latitude: loc.coordinate.latitude)
                     let update = CameraUpdate.make(target: pos, zoomLevel: 15, rotation: 0, tilt: 0, mapView: mapView)
                     let options = CameraAnimationOptions(autoElevation: true, consecutive: false, durationInMillis: 1500)
                     mapView.animateCamera(cameraUpdate: update, options: options)
                }
            case .none: break
            }
        }
        
        // MARK: - Interactions
        func poiDidTapped(kakaoMap: KakaoMap, layerID: String, poiID: String, param: Any?) {
            if let clusterItems = labelIdToClusterItems[poiID] {
                DispatchQueue.main.async {
                    self.selectedClusterBinding?.wrappedValue = clusterItems
                    self.selectedItemBinding?.wrappedValue = nil
                }
                return
            }
            if let item = labelIdToItems[poiID] {
                DispatchQueue.main.async {
                    self.selectedClusterBinding?.wrappedValue = [item]
                    self.selectedItemBinding?.wrappedValue = nil
                }
            }
        }
        
        func terrainDidTapped(kakaoMap: KakaoMap, position: MapPoint) {
            DispatchQueue.main.async {
                self.selectedClusterBinding?.wrappedValue = nil
                self.selectedItemBinding?.wrappedValue = nil
            }
        }
        
        func cameraDidStopped(kakaoMap: KakaoMap, by: MoveBy) {
            refreshClusters()
        }
        
        func authenticationFailed(_ errorCode: Int, desc: String) {
            print("KakaoMap: Auth Failed \(errorCode)")
        }
    }
}
