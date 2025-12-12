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
        
        // Initial Delegate Call
        context.coordinator.mapView = view.mapView
        
        return view
    }
    
    func updateUIView(_ uiView: NMFNaverMapView, context: Context) {
        // Sync Logic
        context.coordinator.parent = self
        context.coordinator.mapView = uiView.mapView
        
        // 1. Handle Actions
        if action != .none {
            context.coordinator.handleAction(action)
            DispatchQueue.main.async {
                action = .none
            }
        }
        
        // 2. Update Pins & Path
        context.coordinator.updateAnnotations(items: todoItems)
        
        // 3. User Location
        if let loc = locationManager.currentLocation {
            context.coordinator.updateUserLocation(loc)
        }
        
        // 4. Launch Animation
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
        
        var markers: [NMFMarker] = []
        var pathOverlay: NMFPath?
        var userMarker: NMFMarker?
        
        var lastItemIDs: Set<UUID> = []
        var lastLogIDs: Set<UUID> = []
        
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
                    let update = NMFCameraUpdate(scrollTo: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude), zoomTo: 16)
                    update.animation = .fly
                    update.animationDuration = 1.0
                    map.moveCamera(update)
                } else {
                    // parent.locationManager.requestPermission() // Not exposed
                }
            case .rotateNorth:
                let update = NMFCameraUpdate(heading: 0)
                update.animation = .fly
                update.animationDuration = 0.5
                map.moveCamera(update)
            case .zoomToFit:
                fitToAllPins(userLocation: parent.locationManager.currentLocation)
            case .none: break
            }
        }
        
        func fitToAllPins(userLocation: CLLocation?) {
            guard let map = mapView else { return }
            
            var bounds = NMGLatLngBounds()
            var hasPoints = false
            
            if let user = userLocation {
                bounds = NMGLatLngBounds(southWest: NMGLatLng(lat: user.coordinate.latitude, lng: user.coordinate.longitude),
                                         northEast: NMGLatLng(lat: user.coordinate.latitude, lng: user.coordinate.longitude))
                hasPoints = true
            }
            
            for marker in markers {
                let pos = marker.position
                if !hasPoints {
                    bounds = NMGLatLngBounds(southWest: pos, northEast: pos)
                    hasPoints = true
                } else {
                    bounds = bounds.expand(toPoint: pos)
                }
            }
            
            if hasPoints {
                let update = NMFCameraUpdate(fit: bounds, paddingInsets: UIEdgeInsets(top: 50, left: 50, bottom: 50, right: 50))
                update.animation = .fly
                update.animationDuration = 1.0
                map.moveCamera(update)
            }
        }
        
        // MARK: - Update Logic
        func updateAnnotations(items: [ToDoItem]) {
            guard let map = mapView else { return }
            
            // Diff Check (Simple)
            let currentItemIDs = Set(items.map { $0.id })
            let currentLogIDs = Set(parent.userLogs.map { $0.id })
            
            if currentItemIDs == lastItemIDs && currentLogIDs == lastLogIDs { return }
            lastItemIDs = currentItemIDs
            lastLogIDs = currentLogIDs
            
            // Clear Old
            markers.forEach { $0.mapView = nil }
            markers.removeAll()
            
            // Add ToDos
            for item in items {
                guard let loc = item.location else { continue }
                let marker = NMFMarker()
                marker.position = NMGLatLng(lat: loc.latitude, lng: loc.longitude)
                
                let unifiedItem = UnifiedMapItem.todo(item)
                if let image = UIImage(named: unifiedItem.imageName) {
                    marker.iconImage = NMFOverlayImage(image: image)
                } else {
                    marker.iconImage = NMFOverlayImage(image: UIImage(systemName: "mappin.circle.fill")!)
                }
                
                marker.captionText = item.title
                marker.captionAlign = .top
                marker.touchHandler = { [weak self] (overlay: NMFOverlay) -> Bool in
                    self?.handleMarkerTap(item: .todo(item))
                    return true
                }
                marker.mapView = map
                markers.append(marker)
            }
            
            // Add Logs (History)
            // Path Drawing Logic for selected log? Or all logs as pins?
            // AppleMapView draws pins for logs. Let's match.
            for log in parent.userLogs {
                let marker = NMFMarker()
                marker.position = NMGLatLng(lat: log.latitude, lng: log.longitude)
                
                let unifiedItem = UnifiedMapItem.history(log)
                if let image = UIImage(named: unifiedItem.imageName) {
                    marker.iconImage = NMFOverlayImage(image: image)
                }
                
                // marker.captionText = log.startTime...
                marker.touchHandler = { [weak self] (overlay: NMFOverlay) -> Bool in
                    self?.handleMarkerTap(item: .history(log))
                    return true
                }
                marker.mapView = map
                markers.append(marker)
            }
        }
        
        func updateUserLocation(_ location: CLLocation) {
            guard let map = mapView else { return }
            
            if userMarker == nil {
                userMarker = NMFMarker()
                if let image = UIImage(named: "PinCurrent") {
                     userMarker?.iconImage = NMFOverlayImage(image: image)
                }
                // Anchor bottom-center (0.5, 1.0) because PinCurrent is also a Shield shape now?
                // Wait, PinCurrent (Crosshair) SVG is circle-like inside a shield?
                // Per design spec: "Shield (Badge style)". So yes, Bottom-Center.
                userMarker?.anchor = CGPoint(x: 0.5, y: 1.0)
                userMarker?.mapView = map
                userMarker?.zIndex = 100 // Top
            }
            userMarker?.position = NMGLatLng(lat: location.coordinate.latitude, lng: location.coordinate.longitude)
        }
        
        func performLaunchAnimation(userLocation: CLLocation?) {
            guard let loc = userLocation, let map = mapView else { return }
            firstRender = false
            
            // Start High
            let start = NMFCameraUpdate(scrollTo: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude), zoomTo: 5)
            start.animation = .none
            map.moveCamera(start)
            
            // Animate In
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                let end = NMFCameraUpdate(scrollTo: NMGLatLng(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude), zoomTo: 16)
                end.animation = .fly
                end.animationDuration = 2.0
                map.moveCamera(end)
            }
        }
        
        // MARK: - Interactions
        func mapView(_ mapView: NMFMapView, didTapMap latlng: NMGLatLng, point: CGPoint) {
            // Deselect logic if needed
            DispatchQueue.main.async {
                self.parent.selectedItem = nil
                self.parent.selectedClusterItems = nil
            }
        }
        
        func mapView(_ mapView: NMFMapView, didLongTapMap latlng: NMGLatLng, point: CGPoint) {
            let coord = CLLocationCoordinate2D(latitude: latlng.lat, longitude: latlng.lng)
            
            // Feedback
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            
            DispatchQueue.main.async {
                self.parent.onLongTap?(coord)
            }
        }
        
        func handleMarkerTap(item: UnifiedMapItem) {
             let generator = UIImpactFeedbackGenerator(style: .medium)
             generator.impactOccurred()
            
             DispatchQueue.main.async {
                 // For now, simple selection. Clustering requires more logic which we can refine.
                 // Treat as cluster of 1 for consistent UI
                 self.parent.selectedClusterItems = [item]
                 self.parent.selectedItem = nil
             }
        }
        
        // Drawing Helpers Removed - Using Assets Directly
        
        // Camera Delegate
        func mapView(_ mapView: NMFMapView, cameraDidChangeByReason reason: Int, animated: Bool) {
             let heading = mapView.cameraPosition.heading
             DispatchQueue.main.async {
                 self.parent.rotation = heading
             }
        }
    }
}
