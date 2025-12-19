import SwiftUI
import MapKit
import CoreLocation

struct AppleMapView: UIViewRepresentable {
    @Binding var action: MapAction
    @Binding var rotation: Double
    @ObservedObject var locationManager: AppLocationManager
    var todoItems: [ToDoItem]
    var userLogs: [UserLog]
    @Binding var selectedItem: ToDoItem?
    @Binding var selectedClusterItems: [UnifiedMapItem]?
    @Binding var tapPosition: CGPoint?
    @Binding var clusterRadius: Double? // [NEW] // [NEW]
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onUserLocationTap: (() -> Void)?
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((UserLog) -> Void)?
    var onSelectLog: ((UserLog) -> Void)?
    var onFarItemsDetected: ((Int) -> Void)? // [NEW] Callback for hidden items
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = false // [FIX] Hide System Blue Dot to avoid double pins
        mapView.showsCompass = false // [FIX] Hide System Compass
        mapView.isRotateEnabled = true
        mapView.isPitchEnabled = false
        
        // [FIX] Initial Region Calculation (User Location > Pins Centroid > Gwanghwamun)
        var initialCenter = CLLocationCoordinate2D(latitude: 37.5759, longitude: 126.9768) // Default Gwanghwamun
        var fitRegion = MKCoordinateRegion(center: initialCenter, span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)) // Default span

        if let userLoc = locationManager.currentLocation {
            // Calculate dynamic bounds including User Location
            var minLat = userLoc.coordinate.latitude
            var maxLat = userLoc.coordinate.latitude
            var minLon = userLoc.coordinate.longitude
            var maxLon = userLoc.coordinate.longitude
            var hasPoints = true // User location is always a point
            
            // [FIX] Apply 500km Filter to Bounds Calculation (Using Int Logic)
            let uLat = Int(userLoc.coordinate.latitude * 100_000)
            let uLon = Int(userLoc.coordinate.longitude * 100_000)
            
            for item in todoItems { 
                if let l = item.location {
                    // Filter Check (Int Ops)
                    if SmartLocationManager.shared.isFar(lat1: uLat, lon1: uLon, lat2: l.latInt, lon2: l.lonInt) { continue }
                    minLat = min(minLat, l.latitude); maxLat = max(maxLat, l.latitude); minLon = min(minLon, l.longitude); maxLon = max(maxLon, l.longitude) 
                } 
            }
            for log in userLogs { 
                // Filter Check (Int Ops)
                if SmartLocationManager.shared.isFar(lat1: uLat, lon1: uLon, lat2: log.latInt, lon2: log.lonInt) { continue }
                minLat = min(minLat, log.latitude); maxLat = max(maxLat, log.latitude); minLon = min(minLon, log.longitude); maxLon = max(maxLon, log.longitude) 
            }
            
            if hasPoints {
                let center = CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: (minLon + maxLon) / 2)
                let span = MKCoordinateSpan(latitudeDelta: (maxLat - minLat) * 1.5 + 0.01, longitudeDelta: (maxLon - minLon) * 1.5 + 0.01)
                fitRegion = MKCoordinateRegion(center: center, span: span)
            }
        } else {
            // [FIX] If no user location, DEFAULT TO GWANGHWAMUN.
            // Do NOT try to fit all pins because we don't know which ones are "far" without a reference point.
            // Fitting all pins causes the "Beijing Zoom" issue.
            initialCenter = CLLocationCoordinate2D(latitude: 37.5759, longitude: 126.9768)
            fitRegion = MKCoordinateRegion(center: initialCenter, span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05))
        }
        
        // [FIX] Instant Display (Saved/Default at Zoom 15)
        let initialSpan = 0.01 // Zoom 15
        let initialRegion = MKCoordinateRegion(center: initialCenter, span: MKCoordinateSpan(latitudeDelta: initialSpan, longitudeDelta: initialSpan))
        mapView.setRegion(initialRegion, animated: false)
        
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
        
        // Handle Map Actions
        if action != .none {
            context.coordinator.handleAction(action, mapView: uiView)
            DispatchQueue.main.async {
                action = .none
            }
        }
        
        // Update Annotations
        // Update Annotations
        context.coordinator.updateAnnotations(mapView: uiView, items: todoItems, userLocation: locationManager.currentLocation)
        
        // [NEW] Check Tethering on Location Update
        if let u = locationManager.currentLocation {
            context.coordinator.checkTethering(mapView: uiView, userLocation: u)
        }
        
        // Update Path Visualization -> REMOVED
        // context.coordinator.updatePath(mapView: uiView, selectedItems: selectedClusterItems)
        
        // Launch Animation
        // [FIX] Only launch when User Location is READY.
        if context.coordinator.firstRender, let u = locationManager.currentLocation {
            context.coordinator.performLaunchAnimation(mapView: uiView, userLocation: u)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: AppleMapView
        var firstRender = true
        var userAnnotation: UnifiedAnnotation?
        var lastItemIDs: Set<UUID> = []
        var lastLogIDs: Set<UUID> = []
        
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
             // Trigger WASM Clustering (Continuous)
             refreshWasmClusters(mapView: mapView)
        }
        
        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
             // [NEW] Handle Pending Selection (Auto-Center Complete)
             if let pending = pendingSelection {
                 let annotation = pending.annotation
                 let position = pending.position
                 pendingSelection = nil
                 
                 DispatchQueue.main.async {
                     // 1. Calculate Screen Point
                     let point = mapView.convert(position, toPointTo: mapView)
                     self.parent.tapPosition = point
                     
                     // 2. Show Callout
                     if let cluster = annotation as? WasmClusterAnnotation {
                         self.parent.selectedClusterItems = cluster.items
                         self.parent.selectedItem = nil
                     } else if let nativeCluster = annotation as? MKClusterAnnotation {
                         // [FIX] Handle Native Cluster in Pending Selection
                         var items: [UnifiedMapItem] = []
                         for member in nativeCluster.memberAnnotations {
                             if let uni = member as? UnifiedAnnotation, let item = uni.item {
                                 items.append(item)
                             }
                         }
                         self.parent.selectedClusterItems = items
                         self.parent.selectedItem = nil
                     } else if let uni = annotation as? UnifiedAnnotation, let item = uni.item {
                         self.parent.selectedClusterItems = [item]
                         self.parent.selectedItem = nil
                     }
                 }
             }
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
                     // Auto-Center on Cluster
                     mapView.setCenter(cluster.coordinate, animated: true)
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
                    let region = MKCoordinateRegion(center: loc.coordinate, span: MKCoordinateSpan(latitudeDelta: 0.0025, longitudeDelta: 0.0025))
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
            }
        }
        
        @objc func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
            if gesture.state == .began {
                let mapView = gesture.view as! MKMapView
                let point = gesture.location(in: mapView)
                let coord = mapView.convert(point, toCoordinateFrom: mapView)
                
                // Feedback
                let generator = UIImpactFeedbackGenerator(style: .medium)
                generator.impactOccurred()
                
                parent.onLongTap?(coord)
            }
        }
        
        // MARK: - WASM Clustering Integration
        
        private func refreshWasmClusters(mapView: MKMapView) {
            // [FIX] Fallback to Screen Width if Map View is not yet laid out (Width=0)
            // This prevents "No pins on launch" bug.
            var widthPixels = mapView.bounds.width
            if widthPixels <= 0 {
                widthPixels = UIScreen.main.bounds.width
            }
            // guard widthPixels > 0 else { return } // Removed guard
            
            // [OPTIMIZATION] Fast Path: Bypass WASM if forced OR item count is small (< 50) AND not animating to user yet
            // This prevents initial delay and shows raw pins immediately during "Fit Bounds" phase.
            // [OPTIMIZATION] Fast Path: Bypass WASM on launch for speed.
            // visual overlap is acceptable during this phase.
            // [CRITICAL LOCK: DO NOT MODIFY] Raw First -> Cluster Strategy
            let totalCount = parent.todoItems.count + parent.userLogs.count
            let isLaunchPhase = parent.action == .launchSequence || firstRender // Identify launch
            
            // [TEMPORARY CHECK] switch to native clustering
            let useNativeClustering = false 
            
            if useNativeClustering {
                OptimizationLogger.shared.log(type: .launchStep, value: ">>> Native Clustering Mode Active")
                 // Pre-calc user int location
                var uInt: (lat: Int, lon: Int)? = nil
                if let u = parent.locationManager.currentLocation {
                    uInt = SmartLocationManager.shared.toIntLocation(u)
                }
                
                // Collect All Items
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
                 // User Location handled by system or separate annotation? 
                 // If using native clustering, we treat UserLocation as a pin too if we want it clustered.
                 // Current logic: UserLocation is separate .userLocation item.
                 if let u = parent.locationManager.currentLocation { allItems.append(.userLocation) }
                 
                 // Notify Far Items
                 if farCount > 0 {
                     DispatchQueue.main.async { self.parent.onFarItemsDetected?(farCount) }
                 }
                 
                 // Render Raw Immediately (Native Clustering will group them)
                 DispatchQueue.main.async {
                     self.renderRawItems(mapView: mapView, allItems: allItems)
                 }
                 return
            }
            
            let useFastPath = isLaunchPhase
            
            if useFastPath {
                 OptimizationLogger.shared.log(type: .launchStep, value: ">>> Fast Path: Rendering \(totalCount) items raw (No WASM)")
                // Pre-calc user int location
                var uInt: (lat: Int, lon: Int)? = nil
                if let u = parent.locationManager.currentLocation {
                    uInt = SmartLocationManager.shared.toIntLocation(u)
                }
                
                // Fast Path Loop
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
                 
                 // Notify Far Items
                 if farCount > 0 {
                     DispatchQueue.main.async { self.parent.onFarItemsDetected?(farCount) }
                 }
                 
                 // Render Raw Immediately
                 DispatchQueue.main.async {
                     self.renderRawItems(mapView: mapView, allItems: allItems)
                 }
                 return
            }

            let currentItems = self.parent.todoItems
            let currentLogs = self.parent.userLogs
            let userLocation = self.parent.locationManager.currentLocation
            
            // OptimizationLogger.shared.log(type: .launchStep, value: ">>> Pins Loaded: \(currentItems.count) Items, \(currentLogs.count) Logs")
            if let u = userLocation {
                 OptimizationLogger.shared.log(type: .launchStep, value: ">>> Current Location: \(u.coordinate.latitude), \(u.coordinate.longitude)")
            }
            
            // 1. Prepare Data
            var allItems: [UnifiedMapItem] = []
            var rawPoints: [Int32] = []
            
            var farItemsCount = 0 // [NEW] Track hidden items
            
            // Pre-calc user int location
            var uInt: (lat: Int, lon: Int)? = nil
            if let u = userLocation {
                uInt = SmartLocationManager.shared.toIntLocation(u)
            }
            
            for item in currentItems {
                if let loc = item.location {
                    // Standard Path: Show ALL items (Filter only on Fast Path)
                    allItems.append(.todo(item))
                    rawPoints.append(Int32(loc.latitude * 100_000))
                    rawPoints.append(Int32(loc.longitude * 100_000))
                }
            }
            for log in currentLogs {
                // Standard Path: Show ALL items
                allItems.append(.history(log))
                rawPoints.append(Int32(log.latitude * 100_000))
                rawPoints.append(Int32(log.longitude * 100_000))
            }
            
            // Trigger Callback
            if farItemsCount > 0 {
                DispatchQueue.main.async {
                    self.parent.onFarItemsDetected?(farItemsCount)
                }
            }
            
            // [FIX] Add User Location to Clustering Data
            if let userLoc = userLocation {
                allItems.append(.userLocation)
                rawPoints.append(Int32(userLoc.coordinate.latitude * 100_000))
                rawPoints.append(Int32(userLoc.coordinate.longitude * 100_000))
            }
            
            // 2. Cell Size Calculation
            let region = mapView.region
            let cosLat = cos(region.center.latitude * .pi / 180.0)
            let widthMeters = region.span.longitudeDelta * 111320.0 * cosLat
            let metersPerPixel = widthMeters / widthPixels
            let wasmCellSize = metersPerPixel * 100.0 // [FIX] Restored Standard Sensitivity (100.0) for broader clustering
            
            // [NEW] Update Binding for UI
            DispatchQueue.main.async {
                self.parent.clusterRadius = wasmCellSize
            }
            
            Task {
                // print(">>> WASM Clustering Start")
                let start = Date()
                let result = await WasmManager.shared.cluster(points: rawPoints, cellSize: wasmCellSize)
                // print(">>> WASM Clustering Result: \(result.count/3)")
                let _ = Date().timeIntervalSince(start) * 1000 
                                
                await MainActor.run {
                    self.renderWasmResults(mapView: mapView, clusterResult: result, allItems: allItems, userLocation: userLocation)
                }
            }
        }
        
        private func renderWasmResults(mapView: MKMapView, clusterResult: [Int32], allItems: [UnifiedMapItem], userLocation: CLLocation?) {
            // Parse Results
            // Result format: [lat, lon, count, lat, lon, count...]
            
            var newAnnotations: [MKAnnotation] = []
            
            // 1. (Removed) User Location is now handled within WASM clustering data directly.
            
            // 2. Process Clusters
            // Simple approach: Assign items to nearest Centroid
            // This is slightly heavy O(N*M) but N(Items)~100, M(Clusters)~10. Fast enough.
            
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
            
            // Buckets
            var clusters: [[UnifiedMapItem]] = Array(repeating: [], count: centroids.count)
            
            for item in allItems {
                var itemLat: Double = 0
                var itemLon: Double = 0
                
                switch item {
                case .todo(let t): if let l = t.location { itemLat = l.latitude; itemLon = l.longitude }
                case .history(let l): itemLat = l.latitude; itemLon = l.longitude
                case .userLocation: 
                     if let userLoc = userLocation { itemLat = userLoc.coordinate.latitude; itemLon = userLoc.coordinate.longitude }
                default: break
                }
                
                // Find nearest centroid
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
            
            // Create Annotations
            for (idx, items) in clusters.enumerated() {
                if items.isEmpty { continue }
                
                let centroid = centroids[idx]
                let coordinate = CLLocationCoordinate2D(latitude: centroid.lat, longitude: centroid.lon)
                
                if items.count == 1 {
                    // Single Item
                    let item = items[0]
                    let ann = UnifiedAnnotation()
                    ann.item = item
                    // Use actual item location instead of centroid for precision
                     switch item {
                    case .todo(let t): if let l = t.location { ann.coordinate = CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude) }
                    case .history(let l): ann.coordinate = CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude)
                    default: ann.coordinate = coordinate
                    }
                    ann.title = "Item"
                    newAnnotations.append(ann)
                } else {
                    // Cluster Item
                    // We need a way to represent a "WASM Cluster" as a single annotation.
                    // We can reuse UnifiedAnnotation but with a special flag or list.
                    // UnifiedAnnotation doesn't have a list.
                    // Let's use MKPointAnnotation and handle it in viewFor, 
                    // OR reuse UnifiedAnnotation and inject the whole list into 'item' (if extended)
                    // Hack: Use the first item as valid 'item', but attach list to View later?
                    // Better: Create a 'WasmClusterAnnotation' class.
                    
                    let clusterAnn = WasmClusterAnnotation()
                    clusterAnn.coordinate = coordinate
                    clusterAnn.items = items
                    clusterAnn.title = "\(items.count)"
                    newAnnotations.append(clusterAnn)
                }
            }
            
            // Sync Map
            // remove all EXCEPT UserLocation to correct flashing?
            // MKMapView handles add/remove gracefully if IDs match? No, annotations are objects.
            // Full Diff is hard. Let's just remove all non-User and add new.
            
            let oldInterval = mapView.annotations.filter { !($0 is MKUserLocation) }
            mapView.removeAnnotations(oldInterval)
            mapView.addAnnotations(newAnnotations)
        }
        
        // Helper Class for WASM Clusters
        class WasmClusterAnnotation: MKPointAnnotation {
            var items: [UnifiedMapItem] = []
        }
        
        // [NEW] Raw Renderer for Fast Path
        private func renderRawItems(mapView: MKMapView, allItems: [UnifiedMapItem]) {
            var newAnnotations: [MKAnnotation] = []
            
            for item in allItems {
                let ann = UnifiedAnnotation()
                ann.item = item
                switch item {
                case .todo(let t): if let l = t.location { ann.coordinate = CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude) }
                case .history(let l): ann.coordinate = CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude)
                case .userLocation: if let l = parent.locationManager.currentLocation { ann.coordinate = l.coordinate }
                default: break
                }
                newAnnotations.append(ann)
            }
            
            let oldInterval = mapView.annotations.filter { !($0 is MKUserLocation) }
            mapView.removeAnnotations(oldInterval)
            mapView.addAnnotations(newAnnotations)
        }
        
        // MARK: - Legacy Update (Disabled)
        func updateAnnotations(mapView: MKMapView, items: [ToDoItem], userLocation: CLLocation?) {
            // [FIX] Always refresh clusters when data/location updates, not just on first render.
            // This ensures User Location participates in clustering dynamically.
            refreshWasmClusters(mapView: mapView)
        }
        
        // MARK: - Launch Animation
        func performLaunchAnimation(mapView: MKMapView, userLocation: CLLocation?) {
            isLaunchAnimating = true
            
            // Step 1: Fit Bounds (pins within 500km)
            var points: [CLLocationCoordinate2D] = []
            let userLoc = userLocation
            
            var uLat = 0
            var uLon = 0
            if let u = userLoc {
                let ui = SmartLocationManager.shared.toIntLocation(u)
                uLat = ui.lat; uLon = ui.lon
            }
            
            for item in parent.todoItems { 
                if let l = item.location { 
                     if userLoc != nil && SmartLocationManager.shared.isFar(lat1: uLat, lon1: uLon, lat2: l.latInt, lon2: l.lonInt) { continue }
                     points.append(CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude)) 
                 } 
            }
            for log in parent.userLogs { 
                 if userLoc != nil && SmartLocationManager.shared.isFar(lat1: uLat, lon1: uLon, lat2: log.latInt, lon2: log.lonInt) { continue }
                 points.append(CLLocationCoordinate2D(latitude: log.latitude, longitude: log.longitude)) 
             }
            
            if !points.isEmpty {
                 var minLat = points[0].latitude; var maxLat = points[0].latitude
                 var minLon = points[0].longitude; var maxLon = points[0].longitude
                 for p in points {
                    if p.latitude < minLat { minLat = p.latitude }
                    if p.latitude > maxLat { maxLat = p.latitude }
                    if p.longitude < minLon { minLon = p.longitude }
                    if p.longitude > maxLon { maxLon = p.longitude }
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
            self.refreshWasmClusters(mapView: mapView)
            
            // Step 3: Wait 3s -> Zoom 18 at Current Location
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                if let freshLoc = self.parent.locationManager.currentLocation {
                    let zoom18Span = 0.0025 // Approx Zoom 18
                    let finalRegion = MKCoordinateRegion(center: freshLoc.coordinate, span: MKCoordinateSpan(latitudeDelta: zoom18Span, longitudeDelta: zoom18Span))
                    
                    mapView.setRegion(finalRegion, animated: true)
                    
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                        self.isLaunchAnimating = false
                        self.firstRender = false 
                        self.refreshWasmClusters(mapView: mapView)
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
            if !isWasmCluster && !isNativeCluster {
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
            // 1. Reset
            view.subviews.forEach { $0.removeFromSuperview() }
            
            // 2. Setup Dimensions
            let width: CGFloat = 40
            let height: CGFloat = 50
            let size = CGSize(width: width, height: height)
            
            view.frame = CGRect(origin: .zero, size: size)
            view.centerOffset = CGPoint(x: 0, y: -height / 2)
            view.isUserInteractionEnabled = true
            
            // 3. Visuals - Layer 1: Image
            let imageView = UIImageView(frame: view.bounds)
            imageView.contentMode = .scaleAspectFit
            imageView.isUserInteractionEnabled = false
            view.addSubview(imageView)
            
            // 4. Visuals - Layer 2: Label
            let label = UILabel(frame: CGRect(x: 0, y: 0, width: width, height: height * 0.4))
            label.textAlignment = .center
            label.font = UIFont.systemFont(ofSize: 10, weight: .bold)
            label.textColor = .white
            view.addSubview(label)
            
            // 5. Interaction - Layer 3: Invisible Button
            let btn = MapPinButton(type: .custom)
            btn.frame = view.bounds
            btn.backgroundColor = .clear
            btn.setTitle("", for: .normal)
            btn.addTarget(self, action: #selector(handlePinButtonTap(_:)), for: .touchUpInside)
            view.addSubview(btn)
            
            // 6. Data Binding
            // [NEW] Native Cluster Support
            if let cluster = annotation as? MKClusterAnnotation {
                var items: [UnifiedMapItem] = []
                for member in cluster.memberAnnotations {
                    if let uni = member as? UnifiedAnnotation, let item = uni.item {
                        items.append(item)
                    }
                }
                btn.items = items
                let count = items.count
                
                // Style Logic
                let (baseName, color, _) = UnifiedMapItem.resolveClusterStyle(items: items)
                
                if let img = UIImage(named: baseName) {
                    imageView.image = img
                } else {
                     imageView.image = PinImageHelper.shared.createShieldPin(color: color, count: count)
                }
                
                // Badge
                if count > 1 {
                    let badgeSize: CGFloat = 20
                    let badgeLabel = UILabel(frame: CGRect(x: width - (badgeSize/2), y: -(badgeSize/4), width: badgeSize, height: badgeSize))
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
                view.bringSubviewToFront(btn)
                
            } else if let wasmCluster = annotation as? WasmClusterAnnotation {
                // [WASM CLUSTER LOGIC]
                let items = wasmCluster.items
                btn.items = items
                let count = items.count
                
                // [FIX] Centralized Logic
                let (baseName, color, _) = UnifiedMapItem.resolveClusterStyle(items: items)
                
                if let img = UIImage(named: baseName) {
                    // Use base image directly. Badge is added by code below (UIView)
                    imageView.image = img
                } else {
                     // Fallback
                     imageView.image = PinImageHelper.shared.createShieldPin(color: color, count: count)
                }
                
                // Badge
                if count > 1 {
                    let badgeSize: CGFloat = 20
                    let badgeLabel = UILabel(frame: CGRect(x: width - (badgeSize/2), y: -(badgeSize/4), width: badgeSize, height: badgeSize))
                    // Badge Style (Updated: White BG, Red Text/Border)
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
                view.bringSubviewToFront(btn)
                
            } else if let unified = annotation as? UnifiedAnnotation, let item = unified.item {
                btn.items = [item]
                 switch item {
                  case .todo(let todo):
                      var imageName = "PinTodoReady"
                      if todo.isCompleted { imageName = "PinTodoDone" }
                      if let img = UIImage(named: imageName) { imageView.image = img }
                      if let date = todo.dueDate {
                          // Only show label if needed
                      }
                   case .history(let log):
                       if let img = UIImage(named: "PinHistory") { imageView.image = img }
                       else { imageView.image = PinImageHelper.shared.createShieldPin(color: .red, iconName: "clock.fill") }
                      // [FIX] User requested to clear text for now
                      label.text = "" // f.string(from: log.startTime)
                   case .serverMessage:
                       if let img = UIImage(named: "PinReceiveReady") { imageView.image = img }
                   case .userLocation:
                        if let customImage = UIImage(named: "PinCurrent") { imageView.image = customImage }
                   }
            }
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
                
                pendingSelection = (annotation, annotation.coordinate)
                
                // 2. Animate to Center
                mapView.setCenter(annotation.coordinate, animated: true)
                
                // 3. Defer Selection Update (Handled in regionDidChangeAnimated)
                // We do NOT set parent.selectedClusterItems here.
            }
        }
        
        // Custom Button subclass just to carry data
        class MapPinButton: UIButton {
            var items: [UnifiedMapItem] = []
        }
        
        // [NEW] Custom Annotation View to enforce HitTest
        class TouchableAnnotationView: MKAnnotationView {
            override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
                // [FIX] Force-return the button if the touch is within our expanded bounds
                // This guarantees the button receives the touch event
                if self.point(inside: point, with: event) {
                    return self.subviews.first { $0 is UIButton } ?? super.hitTest(point, with: event)
                }
                return super.hitTest(point, with: event)
            }
            override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
                // [FIX] Expand hit area significantly to catch touches easily
                let largerBounds = self.bounds.insetBy(dx: -20, dy: -20)
                return largerBounds.contains(point)
            }
        }

        // MARK: - Overlays
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                renderer.strokeColor = .red
                renderer.lineWidth = 4
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }
        
        func updatePath(mapView: MKMapView, selectedItems: [UnifiedMapItem]?) {
            // 1. Remove existing polylines
            let oldOverlays = mapView.overlays.filter { $0 is MKPolyline }
            mapView.removeOverlays(oldOverlays)
            
            guard let items = selectedItems else { return }
            
            // 2. Filter history items with pathData
            let historyLogs = items.compactMap { item -> UserLog? in
                 if case .history(let log) = item, log.pathData != nil { return log }
                 return nil
            }
            
            guard let log = historyLogs.first, let data = log.pathData else { return }
            
            // 3. Decode & Draw
            if let points = try? JSONDecoder().decode([LocationData].self, from: data) {
                var coords = points.map { CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) }
                if coords.count >= 2 {
                    let polyline = MKPolyline(coordinates: &coords, count: coords.count)
                    mapView.addOverlay(polyline)
                }
            }
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
    
    var fontSize: CGFloat {
        switch popupFontSize {
        case 0: return 12
        case 1: return 15 // Default
        case 2: return 18
        default: return 15
        }
    }

    var onDeleteToDo: (ToDoItem) -> Void
    var onDeleteLog: (UserLog) -> Void
    var onSelectLog: (UserLog) -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            if items.count <= 4 {
                // Small number of items: Render ALL directly to avoid ScrollView height issues
                VStack(spacing: 0) {
                    ForEach(items) { item in
                        itemRow(item, isSingle: items.count == 1)
                        if item.id != items.last?.id {
                            Divider()
                        }
                    }
                }
            } else {
                // Many items: Use ScrollView
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(items) { item in
                            itemRow(item, isSingle: false)
                            Divider()
                        }
                    }
                }
                .frame(maxHeight: 250) // Limit height for large lists
            }
        }
    }
    
    // Constant widths for alignment
    private let iconWidth: CGFloat = 40
    
    @ViewBuilder
    func itemRow(_ item: UnifiedMapItem, isSingle: Bool) -> some View {
        HStack(spacing: 0) {
            // [Col 1] Map Icon / Spacer
            Group {
                if case .history(let log) = item, log.pathData != nil {
                    Button(action: {
                        onSelectLog(log)
                    }) {
                        Image(systemName: "map.fill")
                            .font(.system(size: fontSize))
                            .foregroundColor(isCluster ? .red : .black)
                            .frame(width: iconWidth, height: iconWidth)
                    }
                    .buttonStyle(.plain)
                } else {
                    // Placeholder Map Icon for Balance
                    Image(systemName: "map.fill")
                        .font(.system(size: fontSize))
                        .foregroundColor(.gray.opacity(0.3)) // Light Gray
                        .frame(width: iconWidth, height: iconWidth)
                }
            }
            
            Spacer()
            
            // [Col 2] Content (Time / Title)
            Group {
                switch item {
                case .todo(let todo):
                    VStack(alignment: .center, spacing: 2) {
                        Text(todo.title)
                            .font(.system(size: fontSize, weight: .bold))
                            .foregroundColor(.green)
                            .lineLimit(1)
                        if let date = todo.dueDate {
                            Text(date.formatted(date: .omitted, time: .shortened))
                                .font(.system(size: fontSize * 0.8))
                                .foregroundColor(.gray)
                        }
                    }
                case .history(let log):
                    Text(log.startTime.formatted(date: .omitted, time: .shortened))
                        .font(.system(size: fontSize))
                        .foregroundColor(isCluster ? .red : .black)
                case .serverMessage(let msg):
                    Text(msg)
                        .font(.system(size: fontSize))
                        .foregroundColor(isCluster ? .blue : .black)
                case .userLocation:
                    Text(Date().formatted(date: .omitted, time: .shortened))
                        .font(.system(size: fontSize, weight: .bold))
                        .foregroundColor(.red)
                }
            }
            .frame(maxWidth: .infinity) // Fill center
            
            Spacer()
            
            // [Col 3] Trash Icon
            Button(action: {
                switch item {
                case .todo(let todo): onDeleteToDo(todo)
                case .history(let log): onDeleteLog(log)
                case .serverMessage(_): break
                case .userLocation: break // No action
                }
            }) {
                if case .userLocation = item {
                    Image(systemName: "person.fill")
                        .font(.system(size: fontSize)) // Consistent size
                        .foregroundColor(.red)
                } else {
                    Image(systemName: "trash.fill")
                        .font(.system(size: fontSize))
                        .foregroundColor(.red)
                }
            }
            .frame(width: iconWidth, height: iconWidth)
            .buttonStyle(.plain)
        }
        .padding(.vertical, isSingle ? 0 : 4)
        .padding(.horizontal, 8)
        .frame(height: isSingle ? 50 : nil)
    }
}
