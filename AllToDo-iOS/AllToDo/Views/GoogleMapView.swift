import SwiftUI
import GoogleMaps
import CoreLocation

struct GoogleMapView: UIViewRepresentable {
    @Binding var action: MapAction
    @Binding var rotation: Double
    @ObservedObject var locationManager: AppLocationManager
    
    var todoItems: [ToDoItem] // [FIX] Added
    var userLogs: [UserLog] // [FIX] Added
    
    @Binding var selectedItem: ToDoItem?
    @Binding var selectedClusterItems: [UnifiedMapItem]?
    var hasItems: Bool
    
    // Actions
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onUserLocationTap: (() -> Void)?
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((UserLog) -> Void)?
    var onSelectLog: ((UserLog) -> Void)?
    
    class Coordinator: NSObject, GMSMapViewDelegate {
        var parent: GoogleMapView
        var initialAnimationDone = false
        
        init(_ parent: GoogleMapView) {
            self.parent = parent
        }
        
        // MARK: Actions
        func handleAction(_ action: MapAction, scaling: Bool = true) {
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
                    self.parent.selectedItem = nil 
                }
                return true
            } else if let items = marker.userData as? [UnifiedMapItem] {
                DispatchQueue.main.async {
                    self.parent.selectedClusterItems = items
                    self.parent.selectedItem = nil
                }
                return true
            }
            return false
        }
        
        // Camera Change
        func mapView(_ mapView: GMSMapView, didChange position: GMSCameraPosition) {
             DispatchQueue.main.async {
                 self.parent.rotation = position.bearing
                 // self.parent.locationManager.updateZoom(Double(position.zoom)) // [FIX] Removed missing method
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
        mapView.settings.myLocationButton = false
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
                    // locationManager.requestPermission() // [FIX] Removed
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
        
        // 2. Pins (Clustered)
        uiView.clear()
        
        // [FIX] Simple Mapping instead of missing clusteredItems (WASM clustering integration can be added later)
        var clusters: [ClusterItem] = []
        
        // Map Todos
        for item in todoItems {
            if let loc = item.location {
                let cluster = ClusterItem(coordinate: CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude), count: 1, items: [.todo(item)])
                clusters.append(cluster)
            }
        }
        // Map Logs
        for log in userLogs {
            let cluster = ClusterItem(coordinate: CLLocationCoordinate2D(latitude: log.latitude, longitude: log.longitude), count: 1, items: [.history(log)])
            clusters.append(cluster)
        }
        
        // [NEW] Initial Animation Logic
        if !context.coordinator.initialAnimationDone {
            if hasItems {
                if !clusters.isEmpty {
                    var bounds = GMSCoordinateBounds()
                    for cluster in clusters {
                        bounds = bounds.includingCoordinate(cluster.coordinate)
                    }
                    
                    let update = GMSCameraUpdate.fit(bounds, withPadding: 50.0)
                    uiView.animate(with: update)
                    
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                        if let loc = locationManager.currentLocation {
                            let cam = GMSCameraUpdate.setTarget(loc.coordinate, zoom: 16)
                            uiView.animate(with: cam)
                        }
                    }
                    context.coordinator.initialAnimationDone = true
                }
            } else {
                if let loc = locationManager.currentLocation {
                     let cam = GMSCameraUpdate.setTarget(loc.coordinate, zoom: 15)
                     uiView.animate(with: cam)
                     context.coordinator.initialAnimationDone = true
                }
            }
        }
        
        for cluster in clusters {
             let marker = GMSMarker()
             marker.position = cluster.coordinate
             
             if cluster.count == 1, let item = cluster.items.first {
                 // Single Logic
                 switch item {
                 case .todo(let t):
                     marker.title = t.title
                     if let image = UIImage(named: item.imageName) {
                         marker.icon = image
                     } else {
                         marker.icon = UIImage(systemName: "mappin.circle.fill")
                     }
                 case .history(let l):
                     let timeStr = DateFormatter.localizedString(from: l.startTime, dateStyle: .none, timeStyle: .short)
                     marker.title = timeStr
                     if let image = UIImage(named: item.imageName) {
                         marker.icon = image
                     } else {
                         marker.icon = UIImage(systemName: "clock.fill")
                     }
                 default:
                     if let image = UIImage(named: item.imageName) {
                         marker.icon = image
                     }
                 }
                 marker.userData = item
             } else {
                 // Cluster Logic (Placeholder for future)
                 marker.icon = context.coordinator.createClusterImage(count: cluster.count, isRed: false)
                 marker.userData = cluster.items
             }
             
             marker.map = uiView
        }
    }
}

extension GoogleMapView.Coordinator {
    // [FIX] Updated to use Assets - Helper functions removed


    func createClusterImage(count: Int, isRed: Bool) -> UIImage {
        let size = CGSize(width: 40, height: 40)
        return UIGraphicsImageRenderer(size: size).image { context in
             let color = isRed ? UIColor.red : UIColor(red: 0.0, green: 0.67, blue: 0.0, alpha: 1.0) // Green
             color.setFill()
             
             let circle = UIBezierPath(ovalIn: CGRect(x: 2, y: 2, width: 36, height: 36))
             circle.fill()
             
             UIColor.white.setStroke()
             circle.lineWidth = 2
             circle.stroke()
             
             let text = count > 9 ? "9+" : "\(count)"
             let attrs: [NSAttributedString.Key: Any] = [
                 .font: UIFont.boldSystemFont(ofSize: 14),
                 .foregroundColor: UIColor.white
             ]
             let str = NSAttributedString(string: text, attributes: attrs)
             let textSize = str.size()
             str.draw(at: CGPoint(x: 20 - textSize.width/2, y: 20 - textSize.height/2))
        }
    }
}

