import SwiftUI
import MapKit
import CoreLocation
import SwiftData

struct AppleMapView: UIViewRepresentable {
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
    @Binding var creatingTodoLocation: CLLocationCoordinate2D? // [NEW]
    @Binding var targetLocation: CLLocationCoordinate2D? // [NEW] For search
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onUserLocationTap: (() -> Void)?
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((ToDoItem) -> Void)?
    var onSelectLog: ((ToDoItem) -> Void)?
    var onSelectItem: ((ToDoItem) -> Void)?
    var onFarItemsDetected: ((Int) -> Void)?
    
    // [NEW] Active Path Rendering
    var activePoints: [PathPoint] = []
    var showActivePath: Bool = true
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = false // [FIX] Hide System Blue Dot to avoid double pins
        mapView.showsCompass = false // [FIX] Hide System Compass
        mapView.isRotateEnabled = true
        mapView.isPitchEnabled = false
        
        // FALLBACK TO GWANGHWAMUN
        var initialCenter = CLLocationCoordinate2D(latitude: 37.5759, longitude: 126.9768)
        print(">>> start map: AppleMapView: No saved location, starting from Gwanghwamun")

        let hasSaved = UserDefaults.standard.bool(forKey: "has_saved_location")
        if hasSaved {
            let savedLat = UserDefaults.standard.double(forKey: "last_latitude")
            let savedLon = UserDefaults.standard.double(forKey: "last_longitude")
            initialCenter = CLLocationCoordinate2D(latitude: savedLat, longitude: savedLon)
            print(">>> start map: AppleMapView: Restored from Saved Location: \(savedLat), \(savedLon)")
        }
        // fitRegion = MKCoordinateRegion(center: initialCenter, span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05))
        
        // [FIX] Instant Display (Saved/Default at Zoom 15)
        let initialSpan = 0.01 // Zoom 15
        let initialRegion = MKCoordinateRegion(center: initialCenter, span: MKCoordinateSpan(latitudeDelta: initialSpan, longitudeDelta: initialSpan))
        mapView.setRegion(initialRegion, animated: false)
        print(">>> start map: Initial Map Displayed at Zoom 15 (Span 0.01) at \(initialCenter.latitude), \(initialCenter.longitude)")
        
        // Long Press Gesture
        let longPress = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleLongPress(_:)))
        longPress.minimumPressDuration = 0.3 // Make it snappier (default is 0.5)
        mapView.addGestureRecognizer(longPress)
        
        OptimizationLogger.shared.log(type: .launchStep, value: ">>> Map Ready")
        return mapView
    }
    
    func updateUIView(_ uiView: MKMapView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.currentMapView = uiView // [NEW] Set Reference
        uiView.showsUserLocation = false // Force disable System Blue Dot

        // 1. Calculate Change Flags
        let currentSummary = "\(allItems.count)-\(allItems.first?.id.uuidString ?? "")"
        let isDataChanged = context.coordinator.lastDataSummary != currentSummary
        
        let currentHistoryItem = selectedItem ?? viewingHistoryItem
        let isPathChanged = context.coordinator.lastHistoryItemID != currentHistoryItem?.todo_id
        
        let isActivePathChanged = context.coordinator.lastActivePointCount != activePoints.count || context.coordinator.lastShowActivePath != showActivePath
        
        let hasAction = action != .none
        let isLaunch = context.coordinator.firstRender && locationManager.currentLocation != nil
        
        // Location Check
        var isLocationChanged = false
        if let u = locationManager.currentLocation {
            if let last = context.coordinator.lastUserLocation {
                isLocationChanged = u.distance(from: last) > 0.1 // Significant move (> 10cm)
            } else {
                isLocationChanged = true
            }
        }
        
        // 2. Early Return (Silent)
        if !isDataChanged && !isPathChanged && !isActivePathChanged && !hasAction && !isLocationChanged && !isLaunch {
            return
        }
        
        // 3. Log (Only when active)
        print(">>> updateUIView: [Data:\(isDataChanged)] [Loc:\(isLocationChanged)] [Path:\(isPathChanged)] [Action:\(hasAction)]")
        
        // 4. Update States & execute
        if isLocationChanged { 
            context.coordinator.lastUserLocation = locationManager.currentLocation 
            // [OPTIMIZATION] Trigger update. The Coordinator's refreshWasmClusters will handle the Diffing.
            if !isDataChanged {
                 context.coordinator.refreshWasmClusters(mapView: uiView, force: false)
            }
        }

        // Handle Map Actions
        if hasAction {
            context.coordinator.handleAction(action, mapView: uiView)
            DispatchQueue.main.async { action = .none }
        }
        
        // Tethering
        context.coordinator.creatingTodoLocationBinding = $creatingTodoLocation
        if let u = locationManager.currentLocation, !context.coordinator.isLaunchAnimating, !context.coordinator.firstRender {
            context.coordinator.checkTethering(mapView: uiView, userLocation: u)
        }
        
        // Path Updates
        if isPathChanged {
             context.coordinator.updatePath(mapView: uiView, historyItem: currentHistoryItem)
             context.coordinator.lastHistoryItemID = currentHistoryItem?.todo_id
        }
        
        // Active Path
        if isActivePathChanged {
             context.coordinator.updateActiveRecordingPath(mapView: uiView, points: activePoints, visible: showActivePath)
             context.coordinator.lastActivePointCount = activePoints.count
             context.coordinator.lastShowActivePath = showActivePath
        }
        
        // Launch
        if isLaunch {
             context.coordinator.performLaunchAnimation(mapView: uiView, userLocation: locationManager.currentLocation!)
        }
        
        // WASM (Data)
        if isDataChanged {
             if uiView.bounds.width > 0 && !context.coordinator.isLaunchAnimating {
                 context.coordinator.lastDataSummary = currentSummary
                 context.coordinator.refreshWasmClusters(mapView: uiView, force: true)
             }
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: AppleMapView
        var firstRender = true
        var isWasmCluster = false // [FIX] Start false to block initial loop
        var userAnnotation: UnifiedAnnotation?
        var lastDataSummary: String = "" // For Smart Refresh
        var lastClusteredWm: Double = -1.0 // [NEW] 1.5x Threshold Tracking
        var lastItemIDs: Set<UUID> = []
        var lastLogIDs: Set<UUID> = []
        
        // [OPTIMIZATION] State Caching to prevent redundant Overlay Updates
        var lastUserLocation: CLLocation? = nil
        var lastHistoryItemID: UUID? = nil
        var lastActivePointCount: Int = -1
        var lastShowActivePath: Bool = false
        
        var creatingTodoLocationBinding: Binding<CLLocationCoordinate2D?>? // [NEW]
        
        weak var currentMapView: MKMapView? // [NEW] Store Reference
        
        init(_ parent: AppleMapView) {
            self.parent = parent
        }
        
        // [NEW] Tethering State
        var currentSpanLon: Int = 0
        var currentSpanLat: Int = 0 
        var moveLocation: (lat: Int, lon: Int)? = nil
        
        var isLaunchAnimating = false

        
        // [NEW] Check Tethering (Restored & Updated)
        func checkTethering(mapView: MKMapView, userLocation: CLLocation) {
            if isLaunchAnimating || firstRender { return } 
            
            let uInt = SmartLocationManager.shared.toIntLocation(userLocation)
             
            if moveLocation == nil {
                moveLocation = uInt
                return
            }
            
            if SmartLocationManager.shared.shouldRecenter(user: uInt, moveLoc: moveLocation!, hLen: currentSpanLon, vLen: currentSpanLat) {
                // Move Camera
                mapView.setCenter(userLocation.coordinate, animated: true)
                moveLocation = uInt // Update Anchor
                // OptimizationLogger.shared.log(type: .locationResume, value: ">>> Smart Tethering Activated (Apple)")
            }
        }
        
        // [NEW] Pending Selection for Auto-Center
        var pendingSelection: (annotation: MKAnnotation, position: CLLocationCoordinate2D)?

        // MARK: - Actions
        
        func mapViewDidChangeVisibleRegion(_ mapView: MKMapView) {
             let heading = mapView.camera.heading
             DispatchQueue.main.async {
                 self.parent.rotation = heading
                 // Sync Span
                 self.currentSpanLon = Int(mapView.region.span.longitudeDelta * 100_000.0)
                 self.currentSpanLat = Int(mapView.region.span.latitudeDelta * 100_000.0)
                 self.parent.locationManager.currentSpan = mapView.region.span.latitudeDelta
             }
             // Trigger WASM Clustering (Continuous) -> MOVED TO IDLE (regionDidChangeAnimated)
             // refreshWasmClusters(mapView: mapView) -> REMOVED per Spec
        }
        
        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
             // [FIX] Trigger Clustering on Idle (Region Change End)
             refreshWasmClusters(mapView: mapView, force: false)
             // Handle Pending Selection removed: Favoring Instant Selection
             pendingSelection = nil
        }
        
        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            guard let annotation = view.annotation else { return }
            
            // [TEMPORARY CHECK] Handle Native Cluster Selection
            if let cluster = annotation as? MKClusterAnnotation {
                // Extract items
                var items: [UnifiedMapItem] = []
                for member in cluster.memberAnnotations {
                    if let uni = member as? UnifiedAnnotation, let item = uni.item {
                        items.append(item)
                    }
                }
                
                // Show Overlay
                if !items.isEmpty {
                     // [FIX] 60pt Offset Strategy (Consistent with Android/Google/Naver/Kakao)
                     pendingSelection = (annotation, annotation.coordinate)
                     
                     let screenHeight = mapView.bounds.height
                     let targetY = (screenHeight / 2) + 53 // [FIX] Return to 53pt Offset
                     
                     let targetRatio = targetY / screenHeight
                     let offsetRatio = 0.5 - targetRatio
                     
                     let spanLat = mapView.region.span.latitudeDelta
                     let offsetLat = spanLat * offsetRatio
                     
                     let cameraCenter = CLLocationCoordinate2D(latitude: annotation.coordinate.latitude - offsetLat, longitude: annotation.coordinate.longitude)
                     
                     mapView.setCenter(cameraCenter, animated: true)
                     
                     parent.selectedClusterItems = items
                     parent.selectedItem = nil
                     
                     // Deselect to allow re-tap
                     mapView.deselectAnnotation(annotation, animated: false)
                }
                return
            }
            
            if annotation is MKUserLocation { return } // Ignore User Location tap
            
            // [NEW] Auto-Center Logic
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            
            // 1. Store Pending
            pendingSelection = (annotation, annotation.coordinate)
            
            // 2. Animate
            mapView.setCenter(annotation.coordinate, animated: true)
            
            // 3. Deselect immediately to allow re-tap
            mapView.deselectAnnotation(annotation, animated: false)
        }
        
        func handleAction(_ action: MapAction, mapView: MKMapView) {
            switch action {
            case .zoomIn:
                var region = mapView.region
                region.span.latitudeDelta /= 2.0
                region.span.longitudeDelta /= 2.0
                mapView.setRegion(region, animated: true)
            case .zoomOut:
                var region = mapView.region
                region.span.latitudeDelta *= 2.0
                region.span.longitudeDelta *= 2.0
                mapView.setRegion(region, animated: true)
            case .currentLocation:
                if let loc = parent.locationManager.currentLocation {
                    OptimizationLogger.shared.log(type: .locationResume, value: ">>> Current Location Button Pressed: \(loc.coordinate)")
                    // [FIX] Standard Zoom 18 (Span ~0.0013 for exact match)
                    let region = MKCoordinateRegion(center: loc.coordinate, span: MKCoordinateSpan(latitudeDelta: 0.0013, longitudeDelta: 0.0013))
                    mapView.setRegion(region, animated: true)
                } else {
                    parent.locationManager.requestPermission()
                }
            case .rotateNorth:
                let camera = mapView.camera
                camera.heading = 0
                mapView.setCamera(camera, animated: true)
            case .none:
                break
            case .zoomToFit:
                 // Custom Fit with Max Zoom Cap (Zoom 15 -> Span ~0.01)
                 var zoomRect = MKMapRect.null
                 for annotation in mapView.annotations {
                     if annotation is MKUserLocation { continue }
                     let point = MKMapPoint(annotation.coordinate)
                     let rect = MKMapRect(x: point.x, y: point.y, width: 0.1, height: 0.1)
                     zoomRect = zoomRect.union(rect)
                 }
                 if !zoomRect.isNull {
                     // Add Padding
                     let region = MKCoordinateRegion(zoomRect)
                     // Ensure Span isn't too small (Zoom > 15)
                     let minSpan = 0.01
                     let latDelta = max(region.span.latitudeDelta * 1.3, minSpan)
                     let lonDelta = max(region.span.longitudeDelta * 1.3, minSpan)
                     
                     let finalRegion = MKCoordinateRegion(center: region.center, span: MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lonDelta))
                     mapView.setRegion(finalRegion, animated: true)
                 }
            case .launchSequence:
                // [NEW] Relaunch Animation
                self.performLaunchAnimation(mapView: mapView, userLocation: parent.locationManager.currentLocation)
            case .moveToLocation:
                if let loc = parent.targetLocation {
                    let zoom18Span = 0.0013
                    let region = MKCoordinateRegion(center: loc, span: MKCoordinateSpan(latitudeDelta: zoom18Span, longitudeDelta: zoom18Span))
                    mapView.setRegion(region, animated: true)
                }
            }
        }
        
        @objc func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
            if gesture.state == .began {
                let mapView = gesture.view as! MKMapView
                let point = gesture.location(in: mapView)
                let coord = mapView.convert(point, toCoordinateFrom: mapView)
                
                // [FIX] Target: 100pt above Screen Center (2x Pin Height)
                let screenHeight = mapView.bounds.height
                let targetY = (screenHeight / 2) - 100
                
                // Calculate Offset: Distance from center (0.5) to target (targetY/screenHeight)
                let targetRatio = targetY / screenHeight
                let offsetRatio = 0.5 - targetRatio
                
                let spanLat = mapView.region.span.latitudeDelta
                let offsetLat = spanLat * offsetRatio
                
                let cameraCenter = CLLocationCoordinate2D(latitude: coord.latitude - offsetLat, longitude: coord.longitude)
                
                let region = MKCoordinateRegion(center: cameraCenter, span: mapView.region.span)
                mapView.setRegion(region, animated: true)
                
                // Feedback
                let generator = UIImpactFeedbackGenerator(style: .medium)
                generator.impactOccurred()
                
                DispatchQueue.main.async {
                    self.parent.onLongTap?(coord)
                }
            }
        }
        
        // MARK: - WASM Clustering Integration
        
        func refreshWasmClusters(mapView: MKMapView, force: Bool = false) {
            // [FIX] Performance: Do NOT update/render during launch animation (Zooming)
            // Pins are already set by 'performLaunchAnimation' -> 'renderRawItems'
            guard !isLaunchAnimating else { return }
            
            // [FIX] Explicit Control: If launch not finished, Render RAW items immediately
            guard isWasmCluster else {
                renderRawItems(mapView: mapView, allItems: parent.allItems)
                return
            }

            print(">>> start map: AppleMapView refreshing with - Total: \(parent.allItems.count)")
            
            // [FIX] Strict Layout Guard: If map has no width, we can't cluster accurately.
            guard mapView.bounds.width > 0 else { return }
            var widthPixels = mapView.bounds.width
            var heightPixels: CGFloat = mapView.bounds.height
            
            // [OPTIMIZATION] Fast Path: Bypass WASM if forced OR item count is small (< 50) AND not animating to user yet
            // This prevents initial delay and shows raw pins immediately during "Fit Bounds" phase.
            // [OPTIMIZATION] Fast Path: Bypass WASM on launch for speed.
            // visual overlap is acceptable during this phase.
            // [CRITICAL LOCK: DO NOT MODIFY] Raw First -> Cluster Strategy
            let totalCount = parent.allItems.count
            let isLaunchPhase = parent.action == .launchSequence || firstRender || isLaunchAnimating // [FIX] Include isLaunchAnimating
            
            let useFastPath = isLaunchPhase

            
            if useFastPath {
                OptimizationLogger.shared.log(type: .launchStep, value: ">>> Fast Path: Rendering \(totalCount) items raw (No WASM)")
                
                // Render Raw Immediately (Data is already filtered by ViewModel)
                DispatchQueue.main.async {
                    self.renderRawItems(mapView: mapView, allItems: self.parent.allItems)
                }
                return
            }

            // 1. Prepare Data
            var allItemsToProcess: [UnifiedMapItem] = []
            var rawPoints: [Int] = []
            
            for item in parent.allItems {
                switch item {
                case .todo(let t):
                    allItemsToProcess.append(item)
                    rawPoints.append(t.int_lat)
                    rawPoints.append(t.int_long)
                case .history(let log):
                    allItemsToProcess.append(item)
                    rawPoints.append(log.int_lat)
                    rawPoints.append(log.int_long)
                case .userLocation(let coord):
                    allItemsToProcess.append(item)
                    rawPoints.append(Int(coord.latitude * 100_000))
                    rawPoints.append(Int(coord.longitude * 100_000))
                case .serverMessage:
                    break
                }
            }
            
            // [NEW] Add Creating Todo Location if active
            if let target = creatingTodoLocationBinding?.wrappedValue {
                let newItem = ToDoItem(todo_name: "New Entry", latitude: target.latitude, longitude: target.longitude)
                allItemsToProcess.append(.todo(newItem))
                rawPoints.append(Int(target.latitude * 100_000))
                rawPoints.append(Int(target.longitude * 100_000))
            }
            
            // 2. WASM Clustering
            let region = mapView.region
            let cosLat = cos(region.center.latitude * .pi / 180.0)
            let widthMeters = region.span.longitudeDelta * 111320.0 * cosLat
            let metersPerPixel = widthMeters / widthPixels
            let wasmCellSize = metersPerPixel * 100.0 
            
            // [FIX] Invalid Region Guard
            guard region.span.latitudeDelta < 150 && region.span.latitudeDelta > 0 else {
                return
            }
            
            // [NEW] NaN Guard for Region
            if region.center.latitude.isNaN || region.center.longitude.isNaN { return }
            
            // [NEW] 1.5x Threshold Check
            let currentWm = metersPerPixel * mapView.bounds.width
            if !force && !isLaunchPhase && lastClusteredWm > 0 && isWasmCluster { // Only check if clustering enabled
                let ratio = currentWm / lastClusteredWm
                 // If change is within 0.66 ~ 1.5, SKIP clustering
                 if ratio > 0.6666 && ratio < 1.5 {
                      return
                 }
            }
            lastClusteredWm = currentWm
            
            // [OPTIMIZATION] Strict Loop Prevention: Do NOT update binding during launch or if change is negligible
            if !isLaunchPhase {
                let currentRadius = parent.clusterRadius ?? 0
                let diff = abs(currentRadius - wasmCellSize)
                if diff > 0.0001 || parent.clusterRadius == nil {
                    DispatchQueue.main.async {
                        self.parent.clusterRadius = wasmCellSize
                    }
                }
            }
            
            Task {
                let result = await WasmManager.shared.cluster(points: rawPoints, cellSize: wasmCellSize)
                await MainActor.run {
                    self.renderWasmResults(mapView: mapView, clusterResult: result, allItems: allItemsToProcess, userLocation: parent.locationManager.currentLocation)
                }
            }
        }
        
        private func renderWasmResults(mapView: MKMapView, clusterResult: [Int], allItems: [UnifiedMapItem], userLocation: CLLocation?) {
            struct Centroid { let lat: Double; let lon: Double; let count: Int }
            var centroids: [Centroid] = []
            
            if clusterResult.count % 3 == 0 {
                for i in stride(from: 0, to: clusterResult.count, by: 3) {
                    let lat = Double(clusterResult[i]) / 100_000.0
                    let lon = Double(clusterResult[i+1]) / 100_000.0
                    if lat.isNaN || lon.isNaN { continue } // [NEW] NaN Guard
                    centroids.append(Centroid(lat: lat, lon: lon, count: clusterResult[i+2]))
                }
            }
            
            var clusters: [[UnifiedMapItem]] = Array(repeating: [], count: centroids.count)
            for item in allItems {
                guard let loc = item.location, !loc.latitude.isNaN, !loc.longitude.isNaN else { continue } // [NEW] NaN Guard
                
                var bestIdx = -1
                var minDist = Double.greatestFiniteMagnitude
                for (idx, c) in centroids.enumerated() {
                    let dLat = loc.latitude - c.lat
                    let dLon = loc.longitude - c.lon
                    let dist = dLat*dLat + dLon*dLon
                    if dist < minDist { minDist = dist; bestIdx = idx }
                }
                if bestIdx >= 0 { clusters[bestIdx].append(item) }
            }
            
            // [SMOOTHING ALGORITHM - 4 STEPS]
            let oldAnnotations = mapView.annotations.filter { !($0 is MKUserLocation) }
            var toAdd: [MKAnnotation] = []
            var toRemove: [MKAnnotation] = []
            
            var newSingles: [UnifiedAnnotation] = []
            var newClusters: [WasmClusterAnnotation] = []
            
            for (idx, items) in clusters.enumerated() {
                if items.isEmpty { continue }
                let centroid = centroids[idx]
                var finalCoordinate = CLLocationCoordinate2D(latitude: centroid.lat, longitude: centroid.lon)
                
                if items.count == 1 {
                    let ann = UnifiedAnnotation()
                    ann.item = items[0]
                    ann.coordinate = items[0].location ?? finalCoordinate
                    newSingles.append(ann)
                } else {
                    let clusterAnn = WasmClusterAnnotation()
                    // [FIX] Anchor to user location if present
                    if let userItem = items.first(where: { if case .userLocation = $0 { return true }; return false }),
                       let userCoord = userItem.location {
                        finalCoordinate = userCoord
                    }
                    clusterAnn.coordinate = finalCoordinate
                    clusterAnn.items = items
                    newClusters.append(clusterAnn)
                }
            }
            
            // Step 1: New Entry (Single pins that weren't there)
            for newSingle in newSingles {
                if let item = newSingle.item, !oldAnnotations.contains(where: { ($0 as? UnifiedAnnotation)?.item?.id == item.id }) {
                    toAdd.append(newSingle)
                }
            }
            
            // Step 2: Merge Cleanup (Remove singles that are now in clusters)
            for oldAnn in oldAnnotations {
                if let oldSingle = oldAnn as? UnifiedAnnotation, let item = oldSingle.item {
                    if newClusters.contains(where: { $0.items.contains(where: { $0.id == item.id }) }) {
                        toRemove.append(oldAnn)
                    } else if !newSingles.contains(where: { $0.item?.id == item.id }) {
                        // Also remove if not in new singles at all
                        toRemove.append(oldAnn)
                    }
                }
            }
            
            // Step 3: Old Cluster Cleanup (Remove invalid clusters)
            for oldAnn in oldAnnotations {
                if oldAnn is WasmClusterAnnotation {
                    // For simplicity, we refresh clusters every time, but we could diff items.
                    // Given the nature of WASM clustering, centroids shift easily.
                    toRemove.append(oldAnn)
                }
            }
            
            // Step 4: New Cluster Entry
            toAdd.append(contentsOf: newClusters)
            
            // Keep existing singles that are still singles (Visual Stability)
            // (They were not added to toAdd or toRemove in Steps 1-2)
            
            if !toRemove.isEmpty { mapView.removeAnnotations(toRemove) }
            if !toAdd.isEmpty { mapView.addAnnotations(toAdd) }
        }
        
        // Helper Class for WASM Clusters
        class WasmClusterAnnotation: MKPointAnnotation {
            var items: [UnifiedMapItem] = []
        }
        
        private func renderRawItems(mapView: MKMapView, allItems: [UnifiedMapItem]) {
            var newAnnotations: [MKAnnotation] = []
            for item in allItems {
                let ann = UnifiedAnnotation()
                ann.item = item
                ann.coordinate = item.location ?? CLLocationCoordinate2D()
                newAnnotations.append(ann)
            }
            let oldInterval = mapView.annotations.filter { !($0 is MKUserLocation) }
            mapView.removeAnnotations(oldInterval)
            mapView.addAnnotations(newAnnotations)
        }
        
        func updateAnnotations(mapView: MKMapView, userLocation: CLLocation?) {
            refreshWasmClusters(mapView: mapView, force: true)
        }
        
        
        // MARK: - Launch Animation
        func performLaunchAnimation(mapView: MKMapView, userLocation: CLLocation?) {
            // [FIX] Debounce: Ensure this only runs ONCE
            guard firstRender else { return }
            firstRender = false 
            
            isLaunchAnimating = true
            
            // [FIX] Render Raw Items immediately before animation (Launch Integrity)
            renderRawItems(mapView: mapView, allItems: parent.allItems)
            
            // Step 1: Fit Bounds (pins within 500km)
            var points: [CLLocationCoordinate2D] = []
            let userLoc = userLocation
            
            var uLat = 0, uLon = 0
            if let u = userLoc {
                let ui = SmartLocationManager.shared.toIntLocation(u)
                uLat = ui.lat; uLon = ui.lon
                // [FIX] Also append user location to points for bounds calculation
                points.append(u.coordinate)
            }
            
            for item in parent.allItems {
                switch item {
                case .todo(let t):
                    points.append(CLLocationCoordinate2D(latitude: t.latitude, longitude: t.longitude))
                case .history(let log):
                    points.append(CLLocationCoordinate2D(latitude: log.latitude, longitude: log.longitude))
                case .userLocation(let coord):
                    points.append(coord)
                case .serverMessage:
                    break
                }
            }
            
            if !points.isEmpty {
                 var minLat = points[0].latitude; var maxLat = points[0].latitude
                 var minLon = points[0].longitude; var maxLon = points[0].longitude
                 for p in points {
                    minLat = min(minLat, p.latitude); maxLat = max(maxLat, p.latitude)
                    minLon = min(minLon, p.longitude); maxLon = max(maxLon, p.longitude)
                 }
                 
                 if let u = userLoc {
                    minLat = min(minLat, u.coordinate.latitude); maxLat = max(maxLat, u.coordinate.latitude)
                    minLon = min(minLon, u.coordinate.longitude); maxLon = max(maxLon, u.coordinate.longitude)
                 }

                  let latSpan = max((maxLat - minLat) * 1.4, 0.05) 
                  let lonSpan = max((maxLon - minLon) * 1.4, 0.05)
                  let fitRegion = MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: (minLon + maxLon) / 2), span: MKCoordinateSpan(latitudeDelta: latSpan, longitudeDelta: lonSpan))
                  
                  mapView.setRegion(fitRegion, animated: true)
            }
            
            // Step 2: Immediate Pin Display (Fast Path)
            self.refreshWasmClusters(mapView: mapView, force: true)
            
            // Step 3: Wait 3s -> Zoom 18 at Current Location
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                if let freshLoc = self.parent.locationManager.currentLocation {
                    let zoom18Span = 0.0013 // [FIX] Approx Zoom 18 (was 0.0025)
                    let finalRegion = MKCoordinateRegion(center: freshLoc.coordinate, span: MKCoordinateSpan(latitudeDelta: zoom18Span, longitudeDelta: zoom18Span))
                    
                    mapView.setRegion(finalRegion, animated: true)
                    
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                        self.isLaunchAnimating = false
                        self.firstRender = false 
                        self.isWasmCluster = true // [FIX] Enable Clustering NOW
                        
                        if let finalLoc = self.parent.locationManager.currentLocation {
                             self.moveLocation = SmartLocationManager.shared.toIntLocation(finalLoc) // [NEW] Set Initial Anchor
                        }
                        
                        print(">>> start map: Launch Sequence Completed. Transitioning to Cluster Mode.")
                        self.refreshWasmClusters(mapView: mapView, force: true)
                    }
                    
                    OptimizationLogger.shared.logLaunchStep(step: "launch sequence", data: ["success": true, "zoom": 18])
                }
            }
        }
        
        // MARK: - Delegate Methods
        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if annotation is MKUserLocation { return nil }
            
            // Check for WASM Cluster OR Native Cluster
            let isWasmCluster = annotation is WasmClusterAnnotation
            let isNativeCluster = annotation is MKClusterAnnotation
            
            let identifier = isWasmCluster ? "WasmCluster" : (isNativeCluster ? "NativeCluster" : "UnifiedPin")
            
            var view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? TouchableAnnotationView
            if view == nil {
                view = TouchableAnnotationView(annotation: annotation, reuseIdentifier: identifier)
                view?.canShowCallout = false
                view?.displayPriority = (isWasmCluster || isNativeCluster) ? .required : .defaultHigh
                view?.collisionMode = .circle
            }
            
            // [TEMPORARY CHECK] Enable Native Clustering (Only for Singles)
            // [FIX] Disable Native Clustering during Launch (Show Raw Pins)
            if isLaunchAnimating {
                view?.clusteringIdentifier = nil
            } else if !isWasmCluster && !isNativeCluster {
                view?.clusteringIdentifier = "native_cluster_id"
            } else {
                view?.clusteringIdentifier = nil
            }
            
            view?.annotation = annotation
            view?.layer.zPosition = (isWasmCluster || isNativeCluster) ? 100 : 10
            
            configurePinView(view: view!, annotation: annotation)
            
            return view
        }
        
        private func configurePinView(view: MKAnnotationView, annotation: MKAnnotation) {
            // [FIX] Protect User Location
            if annotation is MKUserLocation {
                view.frame = .zero // Let system handle MKUserLocation
                return
            }
            
            // 1. Reset
            view.subviews.forEach { $0.removeFromSuperview() }
            
            // 2. Setup Dimensions
            let width: CGFloat = 40
            let height: CGFloat = 50
            let size = CGSize(width: width, height: height)
            
            view.frame = CGRect(origin: .zero, size: size)
            view.centerOffset = CGPoint(x: 0, y: -25) // Pin Tip (Bottom Center) is Anchor
            view.isUserInteractionEnabled = true
            
            // 3. Visuals - Layer 1: Image
            let imageView = UIImageView(frame: view.bounds)
            imageView.contentMode = .scaleAspectFit
            imageView.isUserInteractionEnabled = false
            view.addSubview(imageView)
            
            // 4. Visuals - Layer 2: Label (Removed, now part of badge or not needed)
            
            // 5. Interaction - Layer 3: Invisible Button
            let btn = MapPinButton(type: .custom)
            btn.frame = view.bounds
            btn.backgroundColor = .clear
            btn.setTitle("", for: .normal) // Removed label, so no title needed
            btn.addTarget(self, action: #selector(handlePinButtonTap(_:)), for: .touchUpInside)
            view.addSubview(btn)
            
            // 6. Data Binding
            // 6. Data Binding
            if let cluster = annotation as? MKClusterAnnotation {
                var items: [UnifiedMapItem] = []
                for member in cluster.memberAnnotations {
                    if let uni = member as? UnifiedAnnotation, let item = uni.item {
                        items.append(item)
                    }
                }
                btn.items = items
                let (pinType, color, count) = MapLogicHelper.resolveClusterStyle(items: items)
                
                // [FIX] Use fetchPin + applyBadge directly
                if let baseImage = PinImageHelper.shared.fetchPin(type: pinType) {
                    var finalImage = baseImage
                    if count > 1 {
                        finalImage = PinImageHelper.shared.applyBadge(to: baseImage, count: count, badgeColor: color, badgeSize: 20)
                    }
                    imageView.image = finalImage
                    view.frame = CGRect(origin: .zero, size: finalImage.size)
                    // [FIX] Anchor Point: Badged (0.4, 1.0) -> centerOffset (5, -H/2), Raw (0.5, 1.0) -> centerOffset (0, -H/2)
                    let offsetX: CGFloat = (count > 1) ? 5 : 0
                    view.centerOffset = CGPoint(x: offsetX, y: -(finalImage.size.height / 2))
                }
            } else if let wasmCluster = annotation as? WasmClusterAnnotation {
                let items = wasmCluster.items
                btn.items = items
                let (pinType, color, count) = MapLogicHelper.resolveClusterStyle(items: items)
                
                // [FIX] Use fetchPin + applyBadge directly
                if let baseImage = PinImageHelper.shared.fetchPin(type: pinType) {
                    var finalImage = baseImage
                    if count > 1 {
                        finalImage = PinImageHelper.shared.applyBadge(to: baseImage, count: count, badgeColor: color, badgeSize: 20)
                    }
                    imageView.image = finalImage
                    view.frame = CGRect(origin: .zero, size: finalImage.size)
                    // [FIX] Anchor Point: (0.4, 1.0) -> centerOffset (5, -H/2) for Clusters
                    let offsetX: CGFloat = (count > 1) ? 5 : 0
                    view.centerOffset = CGPoint(x: offsetX, y: -(finalImage.size.height / 2))
                }
            } else if let unified = annotation as? UnifiedAnnotation, let item = unified.item {
                btn.items = [item]
                // [FIX] Use fetchPin
                if let img = PinImageHelper.shared.fetchPin(type: item.type) {
                    imageView.image = img
                    view.frame = CGRect(origin: .zero, size: img.size)
                    // [FIX] Anchor Point: (0.5, 1.0)
                    view.centerOffset = CGPoint(x: 0, y: -(img.size.height / 2))
                }
            }
            
            imageView.frame = view.bounds
            btn.frame = view.bounds
        }
        
        private func addBadge(view: UIView, count: Int) {
            let badgeSize: CGFloat = 20
            let badgeLabel = UILabel(frame: CGRect(x: 40 - (badgeSize/2), y: -(badgeSize/4), width: badgeSize, height: badgeSize))
            badgeLabel.backgroundColor = .white
            badgeLabel.textColor = .red
            badgeLabel.textAlignment = .center
            badgeLabel.font = UIFont.systemFont(ofSize: 12, weight: .bold)
            badgeLabel.text = count > 9 ? "9+" : "\(count)"
            badgeLabel.layer.cornerRadius = badgeSize / 2
            badgeLabel.layer.masksToBounds = true
            badgeLabel.layer.borderWidth = 1.5
            badgeLabel.layer.borderColor = UIColor.red.cgColor
            view.addSubview(badgeLabel)
        }
        
        @objc func handlePinButtonTap(_ sender: UIButton) {
            // [FIX] Read data directly from Button
            guard let btn = sender as? MapPinButton else { return }

            // Impact Feedback
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            
            let items = btn.items
            if items.isEmpty { return }
            
            // [NEW] Auto-Center Logic
            if let annotationView = sender.superview as? MKAnnotationView,
               let mapView = self.currentMapView,
               let annotation = annotationView.annotation {
                
                // 1. Store Pending Selection
                // Note: We need to pass the annotation so regionDidChangeAnimated can process it.
                // However, 'btn.items' already has the data. 
                // To keep consistency with 'regionDidChangeAnimated', we can attach items to annotation or pass a temporary struct?
                // Actually 'regionDidChangeAnimated' uses 'pendingSelection.annotation'.
                // If the annotation is 'WasmClusterAnnotation', it has items.
                // If 'UnifiedAnnotation', it has one item.
                // So passing the annotation is sufficient.
                
                // [FIX] Instant Selection (No delay)
                self.parent.tapPosition = CGPoint(x: mapView.bounds.width / 2, y: mapView.bounds.height / 2)
                self.parent.selectedClusterItems = btn.items
                self.parent.selectedItem = nil
                
                // [FIX] Precise 60pt Offset Strategy (10pt Gap)
                let pinPoint = mapView.convert(annotation.coordinate, toPointTo: mapView)
                let centerPoint = CGPoint(x: mapView.bounds.width / 2, y: mapView.bounds.height / 2)
                let targetPoint = CGPoint(x: centerPoint.x, y: centerPoint.y + 52) // [FIX] +1pt Shift Up (53 -> 52)
                
                let deltaX = pinPoint.x - centerPoint.x
                let deltaY = pinPoint.y - targetPoint.y
                
                let newCenterPoint = CGPoint(x: centerPoint.x + deltaX, y: centerPoint.y + deltaY)
                let newCenterCoord = mapView.convert(newCenterPoint, toCoordinateFrom: mapView)
                
                mapView.setCenter(newCenterCoord, animated: true)
                
                // Selection is now instant, no need for deferred logic.
            }
        }


        // MARK: - Overlays
        class HistoryPolyline: MKPolyline {}
        class ActiveRecordingPolyline: MKPolyline {}

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                if overlay is ActiveRecordingPolyline {
                    renderer.strokeColor = UIColor(red: 1.0, green: 0.34, blue: 0.13, alpha: 1.0) // Orange Red
                } else {
                    renderer.strokeColor = .red
                }
                renderer.lineWidth = 2.5 // Thinned from 4
                renderer.lineCap = .round
                renderer.lineJoin = .round

                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        func updatePath(mapView: MKMapView, historyItem: ToDoItem?) {
            // 1. Remove only history polylines
            let oldHistory = mapView.overlays.filter { $0 is HistoryPolyline }
            mapView.removeOverlays(oldHistory)
            
            guard let item = historyItem else { return }
            
            // 2. Query paths for this todo_id
            let searchID = item.todo_id
            let descriptor = FetchDescriptor<PathItem>(
                predicate: #Predicate<PathItem> { $0.todo_id == searchID },
                sortBy: [SortDescriptor<PathItem>(\.time)]
            )
            
            if let paths = try? parent.modelContext.fetch(descriptor) {
                var coords = paths.map { $0.coordinate }
                if coords.count >= 2 {
                    let polyline = HistoryPolyline(coordinates: &coords, count: coords.count)
                    mapView.addOverlay(polyline)
                    
                    // [NEW] Auto-zoom to history path using GeomUtils (Integer Geometry)
                    let intRect = GeomUtils.calculateIntBoundingBox(from: paths)
                    let southWest = CLLocationCoordinate2D(latitude: Double(intRect.minLat) / 100_000.0, 
                                                           longitude: Double(intRect.minLon) / 100_000.0)
                    let northEast = CLLocationCoordinate2D(latitude: Double(intRect.maxLat) / 100_000.0, 
                                                           longitude: Double(intRect.maxLon) / 100_000.0)
                    
                    let mkSouthWest = MKMapPoint(southWest)
                    let mkNorthEast = MKMapPoint(northEast)
                    let rect = MKMapRect(x: min(mkSouthWest.x, mkNorthEast.x), 
                                         y: min(mkSouthWest.y, mkNorthEast.y), 
                                         width: abs(mkSouthWest.x - mkNorthEast.x), 
                                         height: abs(mkSouthWest.y - mkNorthEast.y))
                    
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        mapView.setVisibleMapRect(rect, edgePadding: UIEdgeInsets(top: 80, left: 50, bottom: 50, right: 50), animated: true)
                    }
                }
            }
        }

        
        func updateActiveRecordingPath(mapView: MKMapView, points: [PathPoint], visible: Bool) {
            // 1. Remove existing active trail
            let oldActive = mapView.overlays.filter { $0 is ActiveRecordingPolyline }
            mapView.removeOverlays(oldActive)
            
            guard visible && points.count >= 2 else { return }
            
            // 2. Render new trail (Convert Int32 -> Double)
            var coords = points.map { CLLocationCoordinate2D(latitude: Double($0.latitude)/100_000.0, longitude: Double($0.longitude)/100_000.0) }
            let polyline = ActiveRecordingPolyline(coordinates: &coords, count: coords.count)
            mapView.addOverlay(polyline)
        }
        
        func mapView(_ mapView: MKMapView, annotationView view: MKAnnotationView, calloutAccessoryControlTapped control: UIControl) {
            // Handled in SwiftUI
        }
        
        // didSelect logic is implemented above
        
        // Helper to inject SwiftUI into Callout
        private func injectSwiftUI<T: View>(view: MKAnnotationView, swiftUIView: T, height: CGFloat, width: CGFloat = 260) {
            view.detailCalloutAccessoryView = nil
            
            let controller = UIHostingController(rootView: swiftUIView)
            controller.view.translatesAutoresizingMaskIntoConstraints = false
            controller.view.backgroundColor = UIColor.clear 
            
            let containerView = UIView()
            containerView.translatesAutoresizingMaskIntoConstraints = false
            containerView.backgroundColor = UIColor.clear 
            containerView.addSubview(controller.view)
            
            NSLayoutConstraint.activate([
                controller.view.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
                controller.view.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
                controller.view.topAnchor.constraint(equalTo: containerView.topAnchor),
                controller.view.bottomAnchor.constraint(equalTo: containerView.bottomAnchor),
                
                containerView.widthAnchor.constraint(equalToConstant: width),
                containerView.heightAnchor.constraint(equalToConstant: height)
            ])
            
            view.detailCalloutAccessoryView = containerView
        }
    } 
}


struct ClusterListCallout: View {
    var items: [UnifiedMapItem]
    var isCluster: Bool
    @AppStorage("popupFontSize") private var popupFontSize = 1
    @AppStorage("maxPopupItems") private var maxPopupItems = 5
    var onClose: () -> Void
    
    var fontSize: CGFloat {
        switch popupFontSize {
        case 0: return 14
        case 1: return 17 // Default
        case 2: return 20
        default: return 17
        }
    }

    var onDeleteToDo: (ToDoItem) -> Void
    var onDeleteLog: (ToDoItem) -> Void
    var onSelectLog: (ToDoItem) -> Void
    var onSelectItem: (ToDoItem) -> Void // [NEW] Added for todo detail
    
    var body: some View {
        let displayCount = max(1, maxPopupItems)
        let displayItems = Array(items.prefix(displayCount))
        
        VStack(spacing: 0) {
            // [Header] Center Close Button
            Button(action: onClose) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundColor(.white) // [FIX] White for better visibility on Green
                    .font(.title3)
            }
            .padding(.top, 8)
            .padding(.bottom, 4)
            .buttonStyle(.plain)
            
            // [List Contents]
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(items) { item in
                            itemRow(item)
                            if item.id != items.last?.id {
                                Divider().background(Color.white.opacity(0.2))
                            }
                        }
                    }
                }
                .frame(maxHeight: 250)
            }
        }
        .fixedSize(horizontal: false, vertical: true) // [FIX] Zero-height frame 대응
    }
    
    // Constant widths for alignment
    private let iconWidth: CGFloat = 40
    
    private func itemRow(_ item: UnifiedMapItem) -> some View {
        HStack(spacing: 8) {
            // [Col 1] Map Icon
            if case .history(let t) = item, t.no_of_path >= 2 {
                Button(action: { onSelectLog(t) }) {
                    Image(systemName: "map.fill")
                        .font(.system(size: fontSize))
                        .foregroundColor(.white) // Always White for contrast
                        .frame(width: 30)
                }
                .buttonStyle(.plain)
            } else {
                Image(systemName: "map.fill")
                    .font(.system(size: fontSize))
                    .foregroundColor(.white.opacity(0.3)) // Dimmed White
                    .frame(width: 30)
            }

            
            // [Col 2] Content (Icon, Date, Time, Title)
            HStack(spacing: 8) {
                // Path Count for History
                if case .history(let t) = item {
                    Text("(\(t.no_of_path))")
                        .font(.system(size: fontSize - 2, weight: .bold))
                        .foregroundColor(.white.opacity(0.9)) // [FIX] White for better visibility
                        .frame(width: 35)
                }

                // Date (White)
                let dateStr = item.date.formatted(.dateTime.month(.twoDigits).day(.twoDigits))
                Text(dateStr)
                    .font(.system(size: fontSize - 1))
                    .foregroundColor(.white.opacity(0.7))
                
                // Time (White Bold)
                let timeStr = {
                    let df = DateFormatter()
                    df.dateFormat = "HH:mm"
                    return df.string(from: item.date)
                }()
                Text(timeStr)
                    .font(.system(size: fontSize - 1, weight: .bold))
                    .foregroundColor(.white)

                
                // Title (White)
                Text(item.name)
                    .font(.system(size: fontSize))
                    .foregroundColor(.white)
                    .lineLimit(1)

                
                Spacer()
            }
            .contentShape(Rectangle())
            .onTapGesture {
                switch item {
                case .todo(let t): onSelectItem(t)
                case .history(let t): onSelectItem(t) // [FIX] Text tap opens Edit/Detail, Map Icon opens Map
                default: break
                }
            }

            
            // [Col 3] Trash Icon
            Button(action: {
                switch item {
                case .todo(let todo): onDeleteToDo(todo)
                case .history(let log): onDeleteLog(log)
                case .serverMessage: break
                case .userLocation: break
                }
            }) {
                Image(systemName: "trash.fill")
                    .font(.system(size: fontSize))
                    .foregroundColor(.red)
            }
            .buttonStyle(.plain)
            .frame(width: 30)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 10)
    }
}

// MARK: - Helper Classes (Top-level for visibility)
class MapPinButton: UIButton {
    var items: [UnifiedMapItem] = []
}

class TouchableAnnotationView: MKAnnotationView {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        if self.point(inside: point, with: event) {
            return self.subviews.first { $0 is UIButton } ?? super.hitTest(point, with: event)
        }
        return super.hitTest(point, with: event)
    }
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        let largerBounds = self.bounds.insetBy(dx: -20, dy: -20)
        return largerBounds.contains(point)
    }
}
