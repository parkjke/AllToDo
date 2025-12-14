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
            // Close Button [Styled]
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Color(red: 0.2, green: 0.2, blue: 0.2))
                    .frame(width: 48, height: 48)
                    .background(Color(red: 0.2, green: 0.8, blue: 0.2).opacity(0.7))
                    .cornerRadius(12)
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
                
                // [FIX] Zoom Logic: Maintain at least Level 17 (Span ~0.003) if path is very short
                let latDelta = max((lats.max()! - lats.min()!) * 1.5, 0.003)
                let lonDelta = max((lons.max()! - lons.min()!) * 1.5, 0.003)
                
                let span = MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lonDelta)
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
                renderer.strokeColor = .red
                renderer.lineWidth = 4
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }
        
        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
             let identifier = "HistoryPathPin"
             var view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier)
             if view == nil {
                 view = MKAnnotationView(annotation: annotation, reuseIdentifier: identifier)
             } else {
                 view?.annotation = annotation
             }
             
             // [FIX] Use History Pin Icon (Prioritize Asset)
             if let img = UIImage(named: "PinHistory") {
                 view?.image = img
             } else {
                 view?.image = PinImageHelper.shared.createShieldPin(color: .red, iconName: "clock.fill")
             }
             
             // [FIX] Text is Debug Only -> Restore Label support
             view?.subviews.forEach { $0.removeFromSuperview() } 
             
             // 1. Image View (Background)
             let imageView = UIImageView(frame: CGRect(x: 0, y: 0, width: 40, height: 50))
             imageView.contentMode = .scaleAspectFit
             if let img = view?.image { imageView.image = img }
             view?.addSubview(imageView)
             view?.image = nil 
             
             // 2. Label (Text) - For Debugging
             let label = UILabel(frame: CGRect(x: 0, y: 0, width: 40, height: 20)) 
             label.textAlignment = .center
             label.font = UIFont.systemFont(ofSize: 10, weight: .bold)
             label.textColor = .white
             // [FIX] Text is Debug Only -> Hide by default (Capability Exists)
             // label.text = annotation.title ?? "" 
             label.text = ""
             view?.addSubview(label)
             
             // Setup Frame SAME as main map (40x50)
             view?.frame = CGRect(x: 0, y: 0, width: 40, height: 50)
             view?.centerOffset = CGPoint(x: 0, y: -25)
             view?.canShowCallout = true
             
             return view
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
        // Markers
        let start = GMSMarker(position: coordinates.first!)
        start.title = "Start"
        if let img = UIImage(named: "PinHistory") {
            start.icon = img.resized(to: CGSize(width: 40, height: 50))
        } else {
             start.icon = PinImageHelper.shared.createShieldPin(color: .red, iconName: "clock.fill").resized(to: CGSize(width: 40, height: 50))
        }
        start.map = uiView
        
        let end = GMSMarker(position: coordinates.last!)
        end.title = "End"
        if let img = UIImage(named: "PinHistory") {
            end.icon = img.resized(to: CGSize(width: 40, height: 50))
        } else {
            end.icon = PinImageHelper.shared.createShieldPin(color: .red, iconName: "clock.fill").resized(to: CGSize(width: 40, height: 50))
        }
        end.map = uiView
        
        // Fit Bounds
        var bounds = GMSCoordinateBounds()
        coordinates.forEach { bounds = bounds.includingCoordinate($0) }
        
        // [FIX] Limit Max Zoom (Minimum Span ~ 0.003)
        // If the bounds are too small (short path), expand them artificially
        let northEast = bounds.northEast
        let southWest = bounds.southWest
        let latDelta = northEast.latitude - southWest.latitude
        let lonDelta = northEast.longitude - southWest.longitude
        
        let minDelta = 0.003 // Approx Zoom Level 17
        
        if latDelta < minDelta || lonDelta < minDelta {
            let centerLat = (northEast.latitude + southWest.latitude) / 2
            let centerLon = (northEast.longitude + southWest.longitude) / 2
            
            let newLatDelta = max(latDelta, minDelta)
            let newLonDelta = max(lonDelta, minDelta)
            
            let newSW = CLLocationCoordinate2D(latitude: centerLat - newLatDelta/2, longitude: centerLon - newLonDelta/2)
            let newNE = CLLocationCoordinate2D(latitude: centerLat + newLatDelta/2, longitude: centerLon + newLonDelta/2)
            
            bounds = GMSCoordinateBounds(coordinate: newSW, coordinate: newNE)
        }
        
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
        // Markers
        let start = NMFMarker(position: points.first!)
        start.captionText = "Start"
        if let img = UIImage(named: "PinHistory")?.resized(to: CGSize(width: 40, height: 50)) {
            start.iconImage = NMFOverlayImage(image: img)
        } else {
            let shield = PinImageHelper.shared.createShieldPin(color: .red, iconName: "clock.fill")
            if let resized = shield.resized(to: CGSize(width: 40, height: 50)) {
                start.iconImage = NMFOverlayImage(image: resized)
            }
        }
        start.mapView = map
        
        let end = NMFMarker(position: points.last!)
        end.captionText = "End"
        if let img = UIImage(named: "PinHistory")?.resized(to: CGSize(width: 40, height: 50)) {
            end.iconImage = NMFOverlayImage(image: img)
        } else {
             let shield = PinImageHelper.shared.createShieldPin(color: .red, iconName: "clock.fill")
             if let resized = shield.resized(to: CGSize(width: 40, height: 50)) {
                 end.iconImage = NMFOverlayImage(image: resized)
             }
        }
        end.mapView = map
        
        // Fit Bounds
        let bounds = NMGLatLngBounds(southWest: points.first!, northEast: points.last!) // Rough init
        var finalBounds = bounds
        points.forEach { finalBounds = finalBounds.expand(toPoint: $0) }
        
        // [FIX] Limit Max Zoom (Minimum Span ~ 0.003)
        let sw = finalBounds.southWest
        let ne = finalBounds.northEast
        let latDelta = ne.lat - sw.lat
        let lonDelta = ne.lng - sw.lng
        
        // If delta is too small, expand bounds
        let minDelta = 0.003
        if latDelta < minDelta || lonDelta < minDelta {
            let centerLat = (ne.lat + sw.lat) / 2
            let centerLon = (ne.lng + sw.lng) / 2
            
            let newLatDelta = max(latDelta, minDelta)
            let newLonDelta = max(lonDelta, minDelta)
            
            let newSW = NMGLatLng(lat: centerLat - newLatDelta/2, lng: centerLon - newLonDelta/2)
            let newNE = NMGLatLng(lat: centerLat + newLatDelta/2, lng: centerLon + newLonDelta/2)
            finalBounds = NMGLatLngBounds(southWest: newSW, northEast: newNE)
        }
        
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
            
            // [FIX] Zoom Logic: Ensure min span (Level 17 ~ 0.003)
            var rect = AreaRect(southWest: sw, northEast: ne)
            let latDiff = maxLat - minLat
            let lonDiff = maxLon - minLon
            
            if latDiff < 0.003 {
                let center = (maxLat + minLat) / 2
                let newMin = center - 0.0015
                let newMax = center + 0.0015
                rect = AreaRect(southWest: MapPoint(longitude: minLon, latitude: newMin), northEast: MapPoint(longitude: maxLon, latitude: newMax))
            }
            if lonDiff < 0.003 {
                // If both small, apply to both
                let centerLon = (maxLon + minLon) / 2
                let newMinLon = centerLon - 0.0015
                let newMaxLon = centerLon + 0.0015
                // Re-create rect with new Lat/Lon
                // Note: AreaRect structure is immutable usually?
                // Just recreate using updated values.
            }
            
            // Simplify: Just recalculate bounds
            let finalMinLat = (latDiff < 0.003) ? ((maxLat + minLat)/2 - 0.0015) : minLat
            let finalMaxLat = (latDiff < 0.003) ? ((maxLat + minLat)/2 + 0.0015) : maxLat
            let finalMinLon = (lonDiff < 0.003) ? ((maxLon + minLon)/2 - 0.0015) : minLon
            let finalMaxLon = (lonDiff < 0.003) ? ((maxLon + minLon)/2 + 0.0015) : maxLon
            
            let finalSW = MapPoint(longitude: finalMinLon, latitude: finalMinLat)
            let finalNE = MapPoint(longitude: finalMaxLon, latitude: finalMaxLat)
            rect = AreaRect(southWest: finalSW, northEast: finalNE)
            
            let update = CameraUpdate.make(area: rect)
            mapView.moveCamera(update)
            
            // Add Pins
            addPins(mapView: mapView, coordinates: coordinates)
        }
        
        func addPins(mapView: KakaoMap, coordinates: [CLLocationCoordinate2D]) {
            guard let start = coordinates.first, let end = coordinates.last else { return }
            let manager = mapView.getLabelManager()
            let layer = manager.getLabelLayer(layerID: "pathPins") ?? manager.addLabelLayer(option: LabelLayerOptions(layerID: "pathPins", competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 1000))
            
            // Style
            let styleID = "style_PathPin"
            // Re-register if needed or check existence. 
            // Since this is a separate view usage, we register style.
            
            // [FIX] Prioritize Asset
            var image: UIImage
            if let asset = UIImage(named: "PinHistory")?.withRenderingMode(.alwaysOriginal) { 
                // [FIX] Resize to 40x50 AND Rasterize
                let targetSize = CGSize(width: 40, height: 50)
                image = asset.resized(to: targetSize)?.rasterized() ?? asset 
            }
            else { image = PinImageHelper.shared.createShieldPin(color: .red, iconName: "star.fill") }
            
            let iconStyle = PoiIconStyle(symbol: image, anchorPoint: CGPoint(x: 0.5, y: 1.0))
            let style = PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: iconStyle, level: 0)])
            manager.addPoiStyle(style) // Safe to add same ID? usually error if exists.
            // Check implicit: Manager usually throws or ignores. Let's rely on unique ID per view instance or catch error?
            // KakaoMap lifecycle: Styles are per-controller.
            
            // Add POIs
            layer?.addPoi(option: PoiOptions(styleID: styleID, poiID: "start"), at: MapPoint(longitude: start.longitude, latitude: start.latitude))?.show()
            layer?.addPoi(option: PoiOptions(styleID: styleID, poiID: "end"), at: MapPoint(longitude: end.longitude, latitude: end.latitude))?.show()
        }
    }
}
