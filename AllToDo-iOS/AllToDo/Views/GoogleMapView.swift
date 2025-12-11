import SwiftUI
import GoogleMaps
import CoreLocation

struct GoogleMapView: UIViewRepresentable {
    @Binding var action: MapAction
    @Binding var rotation: Double
    @ObservedObject var locationManager: AppLocationManager
    var todoItems: [ToDoItem]
    var userLogs: [UserLog]
    @Binding var selectedItem: ToDoItem?
    @Binding var selectedClusterItems: [UnifiedMapItem]?
    
    // Actions
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onUserLocationTap: (() -> Void)?
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((UserLog) -> Void)?
    var onSelectLog: ((UserLog) -> Void)?
    
    class Coordinator: NSObject, GMSMapViewDelegate {
        var parent: GoogleMapView
        
        init(_ parent: GoogleMapView) {
            self.parent = parent
        }
        
        // MARK: Actions
        func handleAction(_ action: MapAction, scaling: Bool = true) {
            // Note: In make/updateUIView, we need access to the map instance.
            // Since Coordinator is delegate, we can access it via callbacks usually, 
            // but for external actions we need a reference or notification.
            // NaverMap wrapper stored parent reference but handled action in updateUIView by passing context.
        }
        
        // Map Tap (Clear)
        func mapView(_ mapView: GMSMapView, didTapAt coordinate: CLLocationCoordinate2D) {
            DispatchQueue.main.async {
                self.parent.selectedItem = nil
                self.parent.selectedClusterItems = nil
            }
        }
        
        // Long Press
        func mapView(_ mapView: GMSMapView, didLongPressAt coordinate: CLLocationCoordinate2D) {
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            DispatchQueue.main.async {
                self.parent.onLongTap?(coordinate)
            }
        }
        
        // Marker Tap
        func mapView(_ mapView: GMSMapView, didTap marker: GMSMarker) -> Bool {
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            
            if let item = marker.userData as? UnifiedMapItem {
                DispatchQueue.main.async {
                    self.parent.selectedClusterItems = [item]
                    self.parent.selectedItem = nil // Clear single select for unified behavior
                }
                return true
            }
            return false
        }
        
        // Camera Change
        func mapView(_ mapView: GMSMapView, didChange position: GMSCameraPosition) {
             DispatchQueue.main.async {
                 self.parent.rotation = position.bearing
             }
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    func makeUIView(context: Context) -> GMSMapView {
        // Smart Initial Camera Logic
        var target = CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780) // Default Seoul City Hall
        
        if let firstItem = todoItems.first, let loc = firstItem.location {
            target = CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude)
        } else if let userLoc = locationManager.currentLocation {
            target = userLoc.coordinate
        }
        
        let camera = GMSCameraPosition.camera(withTarget: target, zoom: 15.0)
        let mapView = GMSMapView.map(withFrame: .zero, camera: camera)
        mapView.delegate = context.coordinator
        
        mapView.isMyLocationEnabled = true
        mapView.settings.myLocationButton = false // We implement custom UI
        mapView.settings.compassButton = false
        
        return mapView
    }
    
    func updateUIView(_ uiView: GMSMapView, context: Context) {
        // 1. Actions
        if action != .none {
            switch action {
            case .zoomIn:
                uiView.animate(toZoom: uiView.camera.zoom + 1)
            case .zoomOut:
                 uiView.animate(toZoom: uiView.camera.zoom - 1)
            case .currentLocation:
                if let loc = locationManager.currentLocation {
                    let cam = GMSCameraUpdate.setTarget(loc.coordinate, zoom: 16)
                    uiView.animate(with: cam)
                } else {
                    locationManager.requestPermission()
                }
            case .rotateNorth:
                uiView.animate(toBearing: 0)
            case .zoomToFit:
                 var bounds = GMSCoordinateBounds()
                 var hasPoints = false
                 
                 if let loc = locationManager.currentLocation {
                     bounds = bounds.includingCoordinate(loc.coordinate)
                     hasPoints = true
                 }
                 
                 for item in todoItems {
                     if let loc = item.location {
                         bounds = bounds.includingCoordinate(CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude))
                         hasPoints = true
                     }
                 }
                 
                 if hasPoints {
                     let update = GMSCameraUpdate.fit(bounds, withPadding: 50.0)
                     uiView.animate(with: update)
                 }
                
            case .none: break
            }
            
            DispatchQueue.main.async {
                action = .none
            }
        }
        
        // 2. Pins
        // print("GoogleMapView: updateUIView called. Items: \(todoItems.count), Logs: \(userLogs.count)") // DEBUG
        uiView.clear() 
        
        // Re-add ToDos
        for item in todoItems {
            guard let loc = item.location else { continue }
            let marker = GMSMarker()
            marker.position = CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude)
            marker.title = item.title
            
            let icon = context.coordinator.createGreenPinImage()
            marker.icon = icon // Custom Pin restored
            marker.userData = UnifiedMapItem.todo(item)
            marker.map = uiView
            // print("GoogleMapView: Added Marker for \(item.title) at \(loc.latitude), \(loc.longitude)") // DEBUG
        }
        
        // Re-add Logs
        for log in userLogs {
            let marker = GMSMarker()
            marker.position = CLLocationCoordinate2D(latitude: log.latitude, longitude: log.longitude)
            let timeStr = DateFormatter.localizedString(from: log.startTime, dateStyle: .none, timeStyle: .short)
            marker.title = timeStr
            
            let icon = context.coordinator.createRedPinImage()
            marker.icon = icon // Custom Pin restored
            marker.userData = UnifiedMapItem.history(log)
            marker.map = uiView
            // print("GoogleMapView: Added Log Marker at \(log.latitude), \(log.longitude)") // DEBUG
        }
    }
    
    // Extensions to Coordinator to keep main struct clean(er) or just put methods in Coordinator as helper
    // Since we need them in updateUIView which has access to context.coordinator, let's put them in Coordinator.
}

extension GoogleMapView.Coordinator {
    func createGreenPinImage() -> UIImage {
        let size = CGSize(width: 40, height: 50)
        return UIGraphicsImageRenderer(size: size).image { context in
            UIColor(red: 0.2, green: 0.8, blue: 0.2, alpha: 1.0).setFill()
            
            let path = UIBezierPath()
            path.move(to: CGPoint(x: 20, y: 0))
            path.addLine(to: CGPoint(x: 40, y: 20))
            path.addLine(to: CGPoint(x: 20, y: 50))
            path.addLine(to: CGPoint(x: 0, y: 20))
            path.close()
            path.fill()
            
            let config = UIImage.SymbolConfiguration(pointSize: 14, weight: .bold)
            if let icon = UIImage(systemName: "checkmark", withConfiguration: config)?.withTintColor(.white, renderingMode: .alwaysOriginal) {
                icon.draw(at: CGPoint(x: 20 - icon.size.width/2, y: 20 - icon.size.height/2))
            }
        }
    }
    
    func createRedPinImage() -> UIImage {
        let size = CGSize(width: 40, height: 50)
        return UIGraphicsImageRenderer(size: size).image { context in
             UIColor.red.setFill()
             
             let path = UIBezierPath()
             path.move(to: CGPoint(x: 20, y: 0))
             path.addLine(to: CGPoint(x: 40, y: 20))
             path.addLine(to: CGPoint(x: 20, y: 50))
             path.addLine(to: CGPoint(x: 0, y: 20))
             path.close()
             path.fill()
             
             let config = UIImage.SymbolConfiguration(pointSize: 14, weight: .bold)
             if let icon = UIImage(systemName: "clock.fill", withConfiguration: config)?.withTintColor(.white, renderingMode: .alwaysOriginal) {
                 icon.draw(at: CGPoint(x: 20 - icon.size.width/2, y: 20 - icon.size.height/2))
             }
        }
    }
}

