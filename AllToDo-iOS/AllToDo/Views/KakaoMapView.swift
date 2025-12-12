import SwiftUI
import KakaoMapsSDK
import CoreLocation

enum MapAction {
    case none
    case zoomIn
    case zoomOut
    case currentLocation
    case rotateNorth
    case zoomToFit // [NEW]
}

struct KakaoMapView: UIViewRepresentable {
    @Binding var action: MapAction
    @Binding var rotation: Double
    @ObservedObject var locationManager: AppLocationManager
    var todoItems: [ToDoItem]
    var userLogs: [UserLog] // [NEW]
    @Binding var selectedItem: ToDoItem?
    @Binding var selectedClusterItems: [UnifiedMapItem]? // [NEW]
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    
    func makeUIView(context: Context) -> KMViewContainer {
        let view = KMViewContainer()
        view.sizeToFit()
        context.coordinator.createController(view)
        context.coordinator.locationManager = locationManager
        context.coordinator.selectedItemBinding = $selectedItem
        context.coordinator.selectedClusterBinding = $selectedClusterItems // [NEW]
        context.coordinator.onLongTap = onLongTap
        return view
    }

    func updateUIView(_ uiView: KMViewContainer, context: Context) {
        context.coordinator.selectedItemBinding = $selectedItem
        context.coordinator.selectedClusterBinding = $selectedClusterItems
        context.coordinator.onLongTap = onLongTap
        context.coordinator.updatePins(items: todoItems, logs: userLogs)
        // ...
        
        if action != .none {
            context.coordinator.handleAction(action)
            DispatchQueue.main.async {
                action = .none
            }
        }
        
        if let location = locationManager.currentLocation, !context.coordinator.hasMovedToUserLocation {
            context.coordinator.moveToUserLocation(location)
        }
    }

    func makeCoordinator() -> Coordinator {
        return Coordinator()
    }

    class Coordinator: NSObject, MapControllerDelegate, KakaoMapEventDelegate {
        var controller: KMController?
        var firstRender = true
        var locationManager: AppLocationManager?
        var hasMovedToUserLocation = false
        var rotationTimer: Timer?
        var selectedItemBinding: Binding<ToDoItem?>?
        var selectedClusterBinding: Binding<[UnifiedMapItem]?>? // [NEW]
        var onLongTap: ((CLLocationCoordinate2D) -> Void)?
        
        // Anti-Flickering
        var lastItemCount: Int = -1
        
        // Launch Animation
        var firstLocationUpdate = true
        
        var labelIdToItems: [String: UnifiedMapItem] = [:] // [FIX] Store UnifiedItem

        // ...



        func createController(_ view: KMViewContainer) {
            controller = KMController(viewContainer: view)
            controller?.delegate = self
            controller?.prepareEngine()
        }
        
        func handleAction(_ action: MapAction) {
            guard let controller = controller else { return }
            guard let mapView = controller.getView("mapview") as? KakaoMap else { return }
            
            switch action {
            case .zoomIn:
                let pos = mapView.getPosition(CGPoint(x: mapView.viewRect.midX, y: mapView.viewRect.midY))
                mapView.moveCamera(CameraUpdate.make(target: pos, zoomLevel: mapView.zoomLevel + 1, rotation: mapView.rotationAngle, tilt: mapView.tiltAngle, mapView: mapView))
            case .zoomOut:
                 let pos = mapView.getPosition(CGPoint(x: mapView.viewRect.midX, y: mapView.viewRect.midY))
                 mapView.moveCamera(CameraUpdate.make(target: pos, zoomLevel: mapView.zoomLevel - 1, rotation: mapView.rotationAngle, tilt: mapView.tiltAngle, mapView: mapView))
            case .currentLocation:
                moveCameraToCurrentLocation(mapView: mapView)
            case .rotateNorth:
                 let pos = mapView.getPosition(CGPoint(x: mapView.viewRect.midX, y: mapView.viewRect.midY))
                 mapView.moveCamera(CameraUpdate.make(target: pos, zoomLevel: mapView.zoomLevel, rotation: 0.0, tilt: mapView.tiltAngle, mapView: mapView))
            case .none:
                break
            case .zoomToFit:
                 fitToAllPins(mapView: mapView, userLocation: locationManager?.currentLocation)
            }
        }
        
        func moveCameraToCurrentLocation(mapView: KakaoMap) {
             if let location = locationManager?.currentLocation {
                 let pos = MapPoint(longitude: location.coordinate.longitude, latitude: location.coordinate.latitude)
                 // animateCamera with options
                 let update = CameraUpdate.make(target: pos, zoomLevel: 15, rotation: 0, tilt: 0, mapView: mapView)
                 let options = CameraAnimationOptions(autoElevation: true, consecutive: false, durationInMillis: 1000)
                 mapView.animateCamera(cameraUpdate: update, options: options)
             } else {
                 // locationManager?.requestLocationPermission() // [FIX] Assuming handled by AppLocationManager init or separate flow
             }
        }
        
        func moveToUserLocation(_ location: CLLocation) {
            guard let controller = controller else { return }
            guard let mapView = controller.getView("mapview") as? KakaoMap else { return }
            
            updateUserPin(location)
            
            // Launch Animation Logic
            if firstLocationUpdate {
                firstLocationUpdate = false
                hasMovedToUserLocation = true
                
                // Always try to Fit All first if pins exist
                if !labelIdToItems.isEmpty {
                    fitToAllPins(mapView: mapView, userLocation: location)
                    
                     // Phase 2: Zoom to User (Delayed)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                         // Animate to user high zoom
                         let pos = MapPoint(longitude: location.coordinate.longitude, latitude: location.coordinate.latitude)
                         let update = CameraUpdate.make(target: pos, zoomLevel: 17, rotation: 0, tilt: 0, mapView: mapView)
                         let options = CameraAnimationOptions(autoElevation: true, consecutive: false, durationInMillis: 1500)
                         mapView.animateCamera(cameraUpdate: update, options: options)
                    }
                } else {
                    // No pins? Just wide zoom first maybe? Or direct to user?
                    // User wants "Wide -> Zoom".
                    // Let's show relatively wide view of User then zoom in?
                    // or just direct for now if no pins.
                     let pos = MapPoint(longitude: location.coordinate.longitude, latitude: location.coordinate.latitude)
                     let update = CameraUpdate.make(target: pos, zoomLevel: 17, rotation: 0, tilt: 0, mapView: mapView)
                     let options = CameraAnimationOptions(autoElevation: true, consecutive: false, durationInMillis: 1000)
                     mapView.animateCamera(cameraUpdate: update, options: options)
                }
            }
        }
        
        func fitToAllPins(mapView: KakaoMap, userLocation: CLLocation?) {
            var minLat = 90.0, maxLat = -90.0, minLon = 180.0, maxLon = -180.0
            var hasPoint = false
            
            // Include User Location
            if let userLoc = userLocation {
                minLat = min(minLat, userLoc.coordinate.latitude)
                maxLat = max(maxLat, userLoc.coordinate.latitude)
                minLon = min(minLon, userLoc.coordinate.longitude)
                maxLon = max(maxLon, userLoc.coordinate.longitude)
                hasPoint = true
            }
            
            // Include Pins
            for item in labelIdToItems.values {
                if let loc = item.location {
                    minLat = min(minLat, loc.latitude)
                    maxLat = max(maxLat, loc.latitude)
                    minLon = min(minLon, loc.longitude)
                    maxLon = max(maxLon, loc.longitude)
                    hasPoint = true
                }
            }
            
            guard hasPoint else { return }
            
            // Adjust Logic: Find Min/Max Lat/Lon
            let sw = MapPoint(longitude: minLon, latitude: minLat)
            let ne = MapPoint(longitude: maxLon, latitude: maxLat)
            
            let rect = AreaRect(southWest: sw, northEast: ne)
            
            let update = CameraUpdate.make(area: rect)
            let options = CameraAnimationOptions(autoElevation: true, consecutive: false, durationInMillis: 1000)
            mapView.animateCamera(cameraUpdate: update, options: options)
        }
        
        func updateUserPin(_ location: CLLocation) {
            guard let controller = controller else { return }
            guard let mapView = controller.getView("mapview") as? KakaoMap else { return }
            let labelManager = mapView.getLabelManager()
            
            let layer = labelManager.getLabelLayer(layerID: "userLayer") ?? {
                let options = LabelLayerOptions(layerID: "userLayer", competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 1000)
                return labelManager.addLabelLayer(option: options)
            }()
            
            // Checking Style existence: getLabelStyle doesn't exist? Use cache or SDK method?
            // The user says "LabelManager has no member getLabelStyle".
            // Correct API is likely just trying to add, or managing ID myself.
            // As workaround, I'll rely on my 'hasAddedStyles' check or try to create unique IDs.
            // But efficient way: Just add it once in addViewSucceeded or lazily with a flag.
            
            if !hasAddedStyles {
                // Add User Style
                let userImage = createUserPinImage()
                let userIconStyle = PoiIconStyle(symbol: userImage, anchorPoint: CGPoint(x: 0.5, y: 0.5))
                let userPerLevel = PerLevelPoiStyle(iconStyle: userIconStyle, level: 0)
                let userStyle = PoiStyle(styleID: "userStyle", styles: [userPerLevel])
                labelManager.addPoiStyle(userStyle)
                
                // Add Pin Style
                let pinImage = createGreenPinImage()
                let pinIconStyle = PoiIconStyle(symbol: pinImage, anchorPoint: CGPoint(x: 0.5, y: 1.0))
                let pinPerLevel = PerLevelPoiStyle(iconStyle: pinIconStyle, level: 0)
                let pinStyle = PoiStyle(styleID: "greenPinStyle", styles: [pinPerLevel])
                labelManager.addPoiStyle(pinStyle)
                
                hasAddedStyles = true
            }
            
            let pos = MapPoint(longitude: location.coordinate.longitude, latitude: location.coordinate.latitude)
            
            if let layer = layer, let poi = layer.getPoi(poiID: "userLabel") {
                // poi.moveAt exists? Swift interface check: moveAt(point: MapPoint, duration: Int)
                // If not, remove and add.
                // Assuming basic: remove and add
                layer.removePoi(poiID: "userLabel")
                addPoiToLayer(layer, styleID: "userStyle", poiID: "userLabel", at: pos)
            } else {
                addPoiToLayer(layer, styleID: "userStyle", poiID: "userLabel", at: pos)
            }
        }
        
        var hasAddedStyles = false
        
        // [FIX] Updated Signature
        func updatePins(items: [ToDoItem], logs: [UserLog]) {
            // Anti-Flickering: Skip if count didn't change
            let totalCount = items.count + logs.count
            if totalCount == lastItemCount {
                return
            }
            lastItemCount = totalCount
            
            guard let controller = controller else { return }
            guard let mapView = controller.getView("mapview") as? KakaoMap else { return }
            let labelManager = mapView.getLabelManager()
            
            let layer = labelManager.getLabelLayer(layerID: "todoLayer") ?? {
                 let options = LabelLayerOptions(layerID: "todoLayer", competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 1000)
                 return labelManager.addLabelLayer(option: options)
            }()
            
            // Clear existing
            // Safe bet: iterate my local keys.
            for key in labelIdToItems.keys {
                layer?.removePoi(poiID: key)
            }
            labelIdToItems.removeAll()
            
            // Ensure styles added
            if !hasAddedStyles {
                let pinImage = createGreenPinImage()
                let pinIconStyle = PoiIconStyle(symbol: pinImage, anchorPoint: CGPoint(x: 0.5, y: 1.0))
                let pinPerLevel = PerLevelPoiStyle(iconStyle: pinIconStyle, level: 0)
                let pinStyle = PoiStyle(styleID: "greenPinStyle", styles: [pinPerLevel])
                labelManager.addPoiStyle(pinStyle)
                
                let userImage = createUserPinImage()
                let userIconStyle = PoiIconStyle(symbol: userImage, anchorPoint: CGPoint(x: 0.5, y: 0.5))
                let userPerLevel = PerLevelPoiStyle(iconStyle: userIconStyle, level: 0)
                let userStyle = PoiStyle(styleID: "userStyle", styles: [userPerLevel])
                labelManager.addPoiStyle(userStyle)
                
                // Add History Style [NEW]
                let historyImage = createHistoryPinImage()
                let historyIconStyle = PoiIconStyle(symbol: historyImage, anchorPoint: CGPoint(x: 0.5, y: 1.0))
                let historyPerLevel = PerLevelPoiStyle(iconStyle: historyIconStyle, level: 0)
                let historyStyle = PoiStyle(styleID: "historyPinStyle", styles: [historyPerLevel])
                labelManager.addPoiStyle(historyStyle)

                hasAddedStyles = true
            }
            
            // Add Todos
            for item in items {
                guard let loc = item.location else { continue }
                
                let labelId = "todo_" + item.id.uuidString
                labelIdToItems[labelId] = .todo(item)
                let pos = MapPoint(longitude: loc.longitude, latitude: loc.latitude)
                
                addPoiToLayer(layer, styleID: "greenPinStyle", poiID: labelId, at: pos, clickable: true)
            }
            
            // Add Logs [NEW]
            for log in logs {
                let labelId = "log_" + log.id.uuidString
                labelIdToItems[labelId] = .history(log)
                let pos = MapPoint(longitude: log.longitude, latitude: log.latitude)
                
                addPoiToLayer(layer, styleID: "historyPinStyle", poiID: labelId, at: pos, clickable: true)
            }
            
            // Launch Animation Phase 1: Fit All Pins (Show wide view first)
            if firstRender && !items.isEmpty {
                // Determine bounding box of pins + User Location if available
                let userLoc = locationManager?.currentLocation
                fitToAllPins(mapView: mapView, userLocation: userLoc)
                firstRender = false
                
                // If we fitted pins, we can consider firstLocationUpdate done for the purpose of "Wide View".
                // But we still want the "delayed zoom to user".
                // If we do this here, we might conflict with moveToUserLocation's logic.
                // Best to let them coordinate via flags.
                // But ensure 'fitToAllPins' moves the camera!
            }
        }
        
        func addPoiToLayer(_ layer: LabelLayer?, styleID: String, poiID: String, at pos: MapPoint, clickable: Bool = false) {
             guard let layer = layer else { return }
             let options = PoiOptions(styleID: styleID, poiID: poiID)
             options.rank = 0
             if clickable { options.clickable = true }
             
             if let poi = layer.addPoi(option: options, at: pos) {
                 poi.show()
             }
        }
        
        // Drawing Helpers - Now using Assets
        func createUserPinImage() -> UIImage {
            return UIImage(named: "pin_current") ?? UIImage(systemName: "location.circle.fill")!
        }
        
        func createGreenPinImage() -> UIImage {
            return UIImage(named: "pin_todo") ?? UIImage(systemName: "mappin.circle.fill")!
        }
        
        func createHistoryPinImage() -> UIImage {
            return UIImage(named: "pin_history") ?? UIImage(systemName: "clock.fill")!
        }

        // MARK: - MapControllerDelegate
        
        func addViews() {
            let defaultPosition: MapPoint = MapPoint(longitude: 126.978365, latitude: 37.566691)
            let mapviewInfo: MapviewInfo = MapviewInfo(viewName: "mapview", viewInfoName: "map", defaultPosition: defaultPosition, defaultLevel: 12)
            controller?.addView(mapviewInfo)
        }

        func authenticationSucceeded() {
            print("KakaoMap: Authentication Succeeded")
            controller?.activateEngine()
        }
        
        func authenticationFailed(_ errorCode: Int, desc: String) {
            print("KakaoMap: Authentication Failed (\(errorCode)): \(desc)")
        }
        
        func containerDidResize(_ size: CGSize) {
            let mapView: KakaoMap? = controller?.getView("mapview") as? KakaoMap
            mapView?.viewRect = CGRect(origin: CGPoint(x: 0.0, y: 0.0), size: size)
        }
        
        func addViewSucceeded(_ viewName: String, viewInfoName: String) {
             print("KakaoMap: addViewSucceeded")
             if let mapView = controller?.getView("mapview") as? KakaoMap {
                 mapView.eventDelegate = self
             }
             
             rotationTimer?.invalidate()
             rotationTimer = Timer.scheduledTimer(withTimeInterval: 0.03, repeats: true) { [weak self] _ in
                 guard let self = self else { return }
                 if let mapView = self.controller?.getView("mapview") as? KakaoMap {
                     let angleRadians = mapView.rotationAngle
                     let angleDegrees = angleRadians * 180.0 / .pi
                     NotificationCenter.default.post(name: NSNotification.Name("MapRotationChanged"), object: nil, userInfo: ["rotation": angleDegrees])
                 }
             }
        }
        
        // MARK: - KakaoMapEventDelegate
        func terrainDidLongPressed(kakaoMap: KakaoMap, position: MapPoint) {
             print("KakaoMap: Long Pressed at \(position.wgsCoord.latitude), \(position.wgsCoord.longitude)")
             let coord = CLLocationCoordinate2D(latitude: position.wgsCoord.latitude, longitude: position.wgsCoord.longitude)
             DispatchQueue.main.async {
                 self.onLongTap?(coord)
             }
        }
        
        func poiDidTapped(kakaoMap: KakaoMap, layerID: String, poiID: String, param: Any?) {
             print("KakaoMap: POI Tapped \(poiID)")
             if let item = labelIdToItems[poiID] {
                 DispatchQueue.main.async {
                     // Normalize selection to Cluster for consistency with ContentView
                     self.selectedClusterBinding?.wrappedValue = [item]
                     self.selectedItemBinding?.wrappedValue = nil
                 }
             }
        }
        
        deinit {
             rotationTimer?.invalidate()
             controller?.pauseEngine()
             controller?.resetEngine()
        }
    }
}
