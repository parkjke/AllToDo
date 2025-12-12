import SwiftUI
import MapKit
import GoogleMaps
import KakaoMapsSDK
import NMapsMap

struct PathHistoryView: View {
    var log: UserLog
    var onClose: () -> Void
    
    @AppStorage("selectedMapProvider") private var mapProvider: MapProvider = .apple
    @State private var pathCoordinates: [CLLocationCoordinate2D] = []
    
    // For Apple Maps
    @State private var region: MKCoordinateRegion = MKCoordinateRegion()
    
    var body: some View {
        ZStack(alignment: .topTrailing) {
            Group {
                if !pathCoordinates.isEmpty {
                    switch mapProvider {
                    case .apple:
                        ApplePathMapView(coordinates: pathCoordinates, region: $region)
                    case .google:
                        GooglePathMapView(coordinates: pathCoordinates)
                    case .kakao:
                         KakaoPathMapView(coordinates: pathCoordinates)
                    case .naver:
                         NaverPathMapView(coordinates: pathCoordinates)
                    }
                } else {
                     ProgressView("Loading Path...")
                }
            }
            .ignoresSafeArea()
            
            // Close Button
            Button(action: onClose) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 30))
                    .foregroundColor(.black.opacity(0.6))
                    .background(Circle().fill(Color.white))
                    .shadow(radius: 2)
                    .padding()
            }
        }
        .onAppear {
            decodePath()
        }
    }
    
    private func decodePath() {
        guard let data = log.pathData else { return }
        
        do {
            let locations = try JSONDecoder().decode([LocationData].self, from: data)
            self.pathCoordinates = locations.map { 
                CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) 
            }
            
            // Initial Region Calculation for Apple Key
            if !pathCoordinates.isEmpty {
                let lats = pathCoordinates.map { $0.latitude }
                let lons = pathCoordinates.map { $0.longitude }
                let center = CLLocationCoordinate2D(latitude: (lats.min()! + lats.max()!) / 2, longitude: (lons.min()! + lons.max()!) / 2)
                let span = MKCoordinateSpan(latitudeDelta: (lats.max()! - lats.min()!) * 1.5, longitudeDelta: (lons.max()! - lons.min()!) * 1.5)
                self.region = MKCoordinateRegion(center: center, span: span)
            }
            
        } catch {
            print("Failed to decode path: \(error)")
        }
    }
}

// MARK: - Apple Maps Implementation
struct ApplePathMapView: UIViewRepresentable {
    var coordinates: [CLLocationCoordinate2D]
    @Binding var region: MKCoordinateRegion
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.isPitchEnabled = false
        return mapView
    }
    
    func updateUIView(_ uiView: MKMapView, context: Context) {
        uiView.setRegion(region, animated: true)
        uiView.removeOverlays(uiView.overlays)
        uiView.removeAnnotations(uiView.annotations)
        
        if !coordinates.isEmpty {
            let polyline = MKPolyline(coordinates: coordinates, count: coordinates.count)
            uiView.addOverlay(polyline)
            
            let start = MKPointAnnotation(); start.coordinate = coordinates.first!; start.title = "Start"
            let end = MKPointAnnotation(); end.coordinate = coordinates.last!; end.title = "End"
            uiView.addAnnotations([start, end])
        }
    }
    
    func makeCoordinator() -> Coordinator { Coordinator() }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                renderer.strokeColor = .blue
                renderer.lineWidth = 4
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }
    }
}

// MARK: - Google Maps Implementation
struct GooglePathMapView: UIViewRepresentable {
    var coordinates: [CLLocationCoordinate2D]
    
    func makeUIView(context: Context) -> GMSMapView {
        let options = GMSMapViewOptions()
        options.camera = GMSCameraPosition(latitude: 37.5665, longitude: 126.9780, zoom: 12)
        let view = GMSMapView(options: options)
        return view
    }
    
    func updateUIView(_ uiView: GMSMapView, context: Context) {
        uiView.clear()
        
        guard !coordinates.isEmpty else { return }
        
        // Path
        let path = GMSMutablePath()
        coordinates.forEach { path.add($0) }
        let polyline = GMSPolyline(path: path)
        polyline.strokeColor = .red
        polyline.strokeWidth = 4
        polyline.map = uiView
        
        // Markers
        let start = GMSMarker(position: coordinates.first!)
        start.title = "Start"
        start.map = uiView
        
        let end = GMSMarker(position: coordinates.last!)
        end.title = "End"
        end.map = uiView
        
        // Fit Bounds
        var bounds = GMSCoordinateBounds()
        coordinates.forEach { bounds = bounds.includingCoordinate($0) }
        let update = GMSCameraUpdate.fit(bounds, withPadding: 50)
        uiView.animate(with: update)
    }
}

// MARK: - Naver Maps Implementation
struct NaverPathMapView: UIViewRepresentable {
    var coordinates: [CLLocationCoordinate2D]
    
    func makeUIView(context: Context) -> NMFNaverMapView {
        let view = NMFNaverMapView()
        view.showZoomControls = false
        return view
    }
    
    func updateUIView(_ uiView: NMFNaverMapView, context: Context) {
        let map = uiView.mapView
        
        guard !coordinates.isEmpty else { return }
        
        // Path
        let path = NMFPath()
        let points = coordinates.map { NMGLatLng(lat: $0.latitude, lng: $0.longitude) }
        if points.count >= 2 {
            path.path = NMGLineString(points: points)
            path.color = .red
            path.width = 10
            path.mapView = map
        }
        
        // Markers
        let start = NMFMarker(position: points.first!)
        start.captionText = "Start"
        start.mapView = map
        
        let end = NMFMarker(position: points.last!)
        end.captionText = "End"
        end.mapView = map
        
        // Fit Bounds
        let bounds = NMGLatLngBounds(southWest: points.first!, northEast: points.last!) // Rough init
        var finalBounds = bounds
        points.forEach { finalBounds = finalBounds.expand(toPoint: $0) }
        
        let update = NMFCameraUpdate(fit: finalBounds, paddingInsets: UIEdgeInsets(top: 50, left: 50, bottom: 50, right: 50))
        map.moveCamera(update)
    }
}

// MARK: - Kakao Maps Implementation
// Note: Kakao SDK v2 typically expects one controller. 
// If this fails, consider falling back to Apple Map for this view.
struct KakaoPathMapView: UIViewRepresentable {
    var coordinates: [CLLocationCoordinate2D]
    
    func makeUIView(context: Context) -> KMViewContainer {
        let view = KMViewContainer()
        view.sizeToFit()
        context.coordinator.createController(view)
        return view
    }
    
    func updateUIView(_ uiView: KMViewContainer, context: Context) {
        context.coordinator.drawPath(coordinates)
    }
    
    func makeCoordinator() -> Coordinator { Coordinator() }
    
    class Coordinator: NSObject, MapControllerDelegate {
        var controller: KMController?
        var coordinates: [CLLocationCoordinate2D] = []
        var hasDrawn = false
        
        func createController(_ view: KMViewContainer) {
            controller = KMController(viewContainer: view)
            controller?.delegate = self
            controller?.prepareEngine()
        }
        
        func drawPath(_ coords: [CLLocationCoordinate2D]) {
             self.coordinates = coords
             if hasDrawn {
                 // Update Logic if needed
             }
        }
        
        // Delegate
        func addViews() {
            let defaultPosition = MapPoint(longitude: 126.978365, latitude: 37.566691)
            let mapviewInfo = MapviewInfo(viewName: "pathmap", viewInfoName: "map", defaultPosition: defaultPosition, defaultLevel: 12)
            controller?.addView(mapviewInfo)
        }
        
        func addViewSucceeded(_ viewName: String, viewInfoName: String) {
            guard let mapView = controller?.getView("mapview") as? KakaoMap else { return }
            hasDrawn = true
            
            guard !coordinates.isEmpty, coordinates.count >= 2 else { return }
            
            let manager = mapView.getShapeManager()
            
            // Layer
            // Note: addShapeLayer(layerID:zOrder:) might act as creator directly if Options struct is hidden/different.
            let layer = manager.getShapeLayer(layerID: "pathLayer") ?? manager.addShapeLayer(layerID: "pathLayer", zOrder: 0)
            
            guard let shapeLayer = layer else { return }
            
            // Style
            let style = PolylineStyle(styles: [
                PerLevelPolylineStyle(bodyColor: UIColor.red, bodyWidth: 16, strokeColor: UIColor.clear, strokeWidth: 0, level: 0)
            ])
            // Fix param: styleSetID
            let styleSet = PolylineStyleSet(styleSetID: "redPolyline", styles: [style])
            manager.addPolylineStyleSet(styleSet)
            
            // Points
            let points = coordinates.map { MapPoint(longitude: $0.longitude, latitude: $0.latitude) }
            
            // Create Polyline Shape Options
            let options = MapPolylineShapeOptions(shapeID: "historyLine", styleID: "redPolyline", zOrder: 0)
            
            // Add Line to Options
            let line = MapPolyline(line: points, styleIndex: 0)
            options.polylines.append(line)
            
            // Add Shape to Layer
            if let shape = shapeLayer.addMapPolylineShape(options) {
                 shape.show()
            }
            
            // Fit Bounds
            let minLat = coordinates.map{$0.latitude}.min()!
            let maxLat = coordinates.map{$0.latitude}.max()!
            let minLon = coordinates.map{$0.longitude}.min()!
            let maxLon = coordinates.map{$0.longitude}.max()!
            
            let sw = MapPoint(longitude: minLon, latitude: minLat)
            let ne = MapPoint(longitude: maxLon, latitude: maxLat)
            let rect = AreaRect(southWest: sw, northEast: ne)
            
            let update = CameraUpdate.make(area: rect)
            mapView.moveCamera(update)
        }
    }
}
