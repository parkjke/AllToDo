import SwiftUI
import MapKit
import GoogleMaps
import KakaoMapsSDK
import NMapsMap
import SwiftData

struct PathHistoryView: View {
    var item: ToDoItem
    var onClose: () -> Void
    
    @Environment(\.modelContext) private var modelContext
    @AppStorage("selectedMapProvider") private var mapProvider: MapProvider = .apple
    @State private var pathCoordinates: [CLLocationCoordinate2D] = []
    
    // For Apple Maps
    @State private var region: MKCoordinateRegion = MKCoordinateRegion()
    
    // [NEW] Styling State (Standardized across all providers)
    @State private var selectedColor: Color = .red
    @State private var selectedWidth: CGFloat = 8.0
    
    // [NEW] RGB Names for UI
    private let colors: [(name: String, color: Color)] = [
        ("R", .red), ("G", .allToDoGreen), ("B", .blue)
    ]
    private let widths: [CGFloat] = [4, 8, 16]
    
    var body: some View {
        ZStack(alignment: .topTrailing) {
            Group {
                if !pathCoordinates.isEmpty {
                    switch mapProvider {
                    case .apple:
                        ApplePathMapView(coordinates: pathCoordinates, region: region, color: selectedColor, width: selectedWidth)
                    case .google:
                        GooglePathMapView(coordinates: pathCoordinates, color: selectedColor, width: selectedWidth)
                    case .kakao:
                         KakaoPathMapView(coordinates: pathCoordinates, color: selectedColor, width: selectedWidth)
                    case .naver:
                         NaverPathMapView(coordinates: pathCoordinates, color: selectedColor, width: selectedWidth)
                    }
                } else {
                     ProgressView("Loading Path...")
                }
            }
            .ignoresSafeArea()
            
            // Close Button
            VStack {
                HStack {
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(Color(red: 0.2, green: 0.2, blue: 0.2))
                            .frame(width: 44, height: 44)
                            .background(Color.white.opacity(0.8))
                            .cornerRadius(22)
                            .shadow(radius: 4)
                            .padding()
                    }
                }
                Spacer()
                
                // [NEW] Styling Controls (Android Style)
                VStack(spacing: 12) {
                    // Color Selection [R | G | B]
                    HStack(spacing: 16) {
                        Text("Color")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.gray.opacity(0.8))
                        
                        HStack(spacing: 0) {
                            ForEach(colors, id: \.name) { item in
                                Button(action: { selectedColor = item.color }) {
                                    Text(item.name)
                                        .font(.system(size: 12, weight: .bold))
                                        .frame(width: 40, height: 32)
                                        .background(selectedColor == item.color ? item.color : Color.gray.opacity(0.1))
                                        .foregroundColor(selectedColor == item.color ? .white : .gray)
                                }
                                if item.name != colors.last?.name {
                                    Divider().frame(height: 20)
                                }
                            }
                        }
                        .background(Color.white.opacity(0.9))
                        .cornerRadius(8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.2), lineWidth: 1))
                    }
                    
                    // Width Selection [...]
                    HStack(spacing: 16) {
                        Text("Width")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.gray.opacity(0.8))
                        
                        HStack(spacing: 12) {
                            ForEach(widths, id: \.self) { w in
                                Button(action: { selectedWidth = w }) {
                                    Circle()
                                        .fill(selectedWidth == w ? Color.black : Color.gray.opacity(0.3))
                                        .frame(width: w, height: w)
                                        .padding(8)
                                        .background(selectedWidth == w ? Color.gray.opacity(0.1) : Color.clear)
                                        .cornerRadius(4)
                                }
                            }
                        }
                    }
                }
                .padding()
                .background(Color.white.opacity(0.9))
                .cornerRadius(16)
                .shadow(radius: 5)
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
        }
        .onAppear {
            loadPath()
        }
    }
    
    private func loadPath() {
        // Query paths for this todo_id
        let searchID = item.todo_id
        let descriptor = FetchDescriptor<PathItem>(
            predicate: #Predicate<PathItem> { $0.todo_id == searchID },
            sortBy: [SortDescriptor<PathItem>(\.timestamp, order: .forward)]
        )
        
        if let paths = try? modelContext.fetch(descriptor) {
            self.pathCoordinates = paths.map { $0.coordinate }
            
            // Initial Region Calculation for Apple Key
            if !pathCoordinates.isEmpty {
                let lats = pathCoordinates.map { $0.latitude }
                let lons = pathCoordinates.map { $0.longitude }
                let center = CLLocationCoordinate2D(latitude: (lats.min()! + lats.max()!) / 2, longitude: (lons.min()! + lons.max()!) / 2)
                
                let latDelta = max((lats.max()! - lats.min()!) * 1.5, 0.003)
                let lonDelta = max((lons.max()! - lons.min()!) * 1.5, 0.003)
                
                let span = MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lonDelta)
                self.region = MKCoordinateRegion(center: center, span: span)
            }
        }
    }
}

// MARK: - Apple Maps Implementation
struct ApplePathMapView: UIViewRepresentable {
    var coordinates: [CLLocationCoordinate2D]
    var region: MKCoordinateRegion
    var color: Color
    var width: CGFloat
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.isPitchEnabled = false
        return mapView
    }
    
    func updateUIView(_ uiView: MKMapView, context: Context) {
        // [FIX] Avoid infinite region loops by only setting once or using a flag
        // uiView.setRegion(region, animated: true) 
        
        uiView.removeOverlays(uiView.overlays)
        uiView.removeAnnotations(uiView.annotations)
        
        if !self.coordinates.isEmpty {
            var coords = self.coordinates
            let polyline = MKPolyline(coordinates: &coords, count: coords.count)
            uiView.addOverlay(polyline)
            
            let start = MKPointAnnotation(); start.coordinate = self.coordinates.first!; start.title = "Start"
            let end = MKPointAnnotation(); end.coordinate = self.coordinates.last!; end.title = "End"
            uiView.addAnnotations([start, end])
        }
    }
    
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: ApplePathMapView
        
        init(_ parent: ApplePathMapView) {
            self.parent = parent
        }
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                renderer.strokeColor = (parent.color == .red ? .red : (parent.color == .allToDoGreen ? .allToDoGreen : .blue))
                renderer.lineWidth = parent.width
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
    var color: Color
    var width: CGFloat
    
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
        polyline.strokeColor = (color == .red ? .red : (color == .allToDoGreen ? .allToDoGreen : .blue))
        polyline.strokeWidth = width
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
    var color: Color
    var width: CGFloat
    
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
            path.color = (color == .red ? .red : (color == .allToDoGreen ? .allToDoGreen : .blue))
            path.width = width
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
    var color: Color
    var width: CGFloat
    
    func makeUIView(context: Context) -> KMViewContainer {
        let view = KMViewContainer(frame: UIScreen.main.bounds)
        context.coordinator.createController(view)
        return view
    }
    
    func updateUIView(_ uiView: KMViewContainer, context: Context) {
        context.coordinator.checkEngineActivation()
        context.coordinator.drawPath(coordinates, color: color, width: width)
    }
    
    func makeCoordinator() -> Coordinator { Coordinator() }
    
    class Coordinator: NSObject, MapControllerDelegate {
        var controller: KMController?
        var coordinates: [CLLocationCoordinate2D] = []
        var selectedColor: Color = .red
        var selectedWidth: CGFloat = 8.0
        var hasDrawn = false
        
        func createController(_ view: KMViewContainer) {
            controller = KMController(viewContainer: view)
            controller?.delegate = self
            controller?.prepareEngine()
        }

        func checkEngineActivation() {
            guard let controller = controller else { return }
            if controller.isEnginePrepared && !controller.isEngineActive {
                controller.activateEngine()
            }
        }
        
        func drawPath(_ coords: [CLLocationCoordinate2D], color: Color, width: CGFloat) {
             self.coordinates = coords
             self.selectedColor = color
             self.selectedWidth = width
             
             if hasDrawn {
                 if let mapView = controller?.getView("pathmap") as? KakaoMap {
                     renderPath(mapView: mapView)
                 }
             }
        }
        
        // Delegate
        func addViews() {
            // [FIX] Default position: Start from path's first point to avoid Gwanghwamun flicker
            let startCoord = coordinates.first ?? CLLocationCoordinate2D(latitude: 37.566691, longitude: 126.978365)
            let defaultPosition = MapPoint(longitude: startCoord.longitude, latitude: startCoord.latitude)
            let mapviewInfo = MapviewInfo(viewName: "pathmap", viewInfoName: "map", defaultPosition: defaultPosition, defaultLevel: 14)
            controller?.addView(mapviewInfo)
        }
        
        func addViewSucceeded(_ viewName: String, viewInfoName: String) {
            guard let mapView = controller?.getView("pathmap") as? KakaoMap else { return }
            controller?.activateEngine()
            hasDrawn = true
            mapView.isEnabled = true
            
            renderPath(mapView: mapView)
        }
        
        private func renderPath(mapView: KakaoMap) {
            guard !coordinates.isEmpty, coordinates.count >= 2 else { return }
            
            let manager = mapView.getShapeManager()
            let layer = manager.getShapeLayer(layerID: "pathLayer") ?? manager.addShapeLayer(layerID: "pathLayer", zOrder: 0)
            guard let shapeLayer = layer else { return }
            
            // Clean up old
            shapeLayer.removeMapPolylineShape(shapeID: "historyLine")
            mapView.getLabelManager().removeLabelLayer(layerID: "pathPins")
            
            // Style
            let uiColor = (selectedColor == .red ? UIColor.red : (selectedColor == .allToDoGreen ? UIColor.allToDoGreen : UIColor.blue))
            let colorHex = uiColor.cgColor.components?.map { String(format: "%02X", Int($0 * 255)) }.joined() ?? "FF0000"
            let styleID = "redPolyline_\(colorHex)_\(selectedWidth)"
            
            let style = PolylineStyle(styles: [
                PerLevelPolylineStyle(bodyColor: uiColor, bodyWidth: UInt(selectedWidth * 2), strokeColor: UIColor.clear, strokeWidth: 0, level: 0)
            ])
            manager.addPolylineStyleSet(PolylineStyleSet(styleSetID: styleID, styles: [style]))
            
            let points = coordinates.map { MapPoint(longitude: $0.longitude, latitude: $0.latitude) }
            let options = MapPolylineShapeOptions(shapeID: "historyLine", styleID: styleID, zOrder: 1000)
            options.polylines.append(MapPolyline(line: points, styleIndex: 0))
            
            if let shape = shapeLayer.addMapPolylineShape(options) {
                 shape.show()
            }
            
            // Fit Bounds
            let minLat = coordinates.map{$0.latitude}.min()!
            let maxLat = coordinates.map{$0.latitude}.max()!
            let minLon = coordinates.map{$0.longitude}.min()!
            let maxLon = coordinates.map{$0.longitude}.max()!
            
            let finalMinLat = (maxLat - minLat < 0.003) ? ((maxLat + minLat)/2 - 0.0015) : minLat
            let finalMaxLat = (maxLat - minLat < 0.003) ? ((maxLat + minLat)/2 + 0.0015) : maxLat
            let finalMinLon = (maxLon - minLon < 0.003) ? ((maxLon + minLon)/2 - 0.0015) : minLon
            let finalMaxLon = (maxLon - minLon < 0.003) ? ((maxLon + minLon)/2 + 0.0015) : maxLon
            
            let rect = AreaRect(southWest: MapPoint(longitude: finalMinLon, latitude: finalMinLat), 
                                northEast: MapPoint(longitude: finalMaxLon, latitude: finalMaxLat))
            
            // [FIX] Ensure camera move happens after a tiny delay to override default Gwanghwamun centering
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                mapView.moveCamera(CameraUpdate.make(area: rect))
            }
            
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
            
            // [FIX] Prioritize Asset with Rasterization
            var image: UIImage
            if let asset = UIImage(named: "PinHistory"), 
               let resized = asset.resized(to: CGSize(width: 40, height: 50)),
               let rasterized = resized.rasterized() { 
                image = rasterized
            }
            else { 
                let shield = PinImageHelper.shared.createShieldPin(color: .red, iconName: "star.fill")
                image = shield.resized(to: CGSize(width: 40, height: 50))?.rasterized() ?? shield
            }
            
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
