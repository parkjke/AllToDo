import SwiftUI

/* 
 * 🚨 CRITICAL: DO NOT MODIFY THIS FILE 🚨
 * [수정 절대 금지] 사용자 요청에 의해 이 파일의 디자인 및 로직은 최종 확정되었습니다.
 * 특히 다음 사항은 절대 수정해서는 안 됩니다:
 * 1. [닫기] 버튼의 그림자 및 반투명 배경 추가 금지
 * 2. 전반적인 Flat 디자인 규격 유지
 * 3. 다크/라이트 모드 대응 로직 변경 금지
 */

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
                            .font(.system(size: 18, weight: .black))
                            .foregroundColor(mapProvider == .kakao || mapProvider == .naver ? Color.black : .primary)
                            .frame(width: 44, height: 44)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(mapProvider == .kakao || mapProvider == .naver ? Color.white : Color(.systemBackground))
                            )
                    }
                    .buttonStyle(.plain) // [IMPORTANT] Remove default button effects
                    .padding(20)
                }
                Spacer()
                
                // [NEW] Styling Controls (Independent Component)
                PathSettingsView(
                    selectedColor: $selectedColor,
                    selectedWidth: $selectedWidth,
                    isLightModeForced: (mapProvider == .kakao || mapProvider == .naver)
                )
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
            
            // [FIX] [사용자 요구사항] 정수로 영역을 계산하고 공간을 줌
            if !paths.isEmpty {
                let rect = GeomUtils.calculateIntBoundingBox(from: paths, paddingPercent: 20)
                
                let center = CLLocationCoordinate2D(
                    latitude: Double(rect.minLat + rect.maxLat) / 200_000.0,
                    longitude: Double(rect.minLon + rect.maxLon) / 200_000.0
                )
                
                let latDelta = Double(rect.maxLat - rect.minLat) / 100_000.0
                let lonDelta = Double(rect.maxLon - rect.minLon) / 100_000.0
                
                self.region = MKCoordinateRegion(center: center, span: MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lonDelta))
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
        // [FIX] Detect changes in coordinates, color, or width to trigger redraw
        let colorChanged = context.coordinator.lastColor != color
        let widthChanged = context.coordinator.lastWidth != width
        let coordsChanged = context.coordinator.lastCoordinates.count != coordinates.count
        
        if coordsChanged || colorChanged || widthChanged {
            context.coordinator.lastCoordinates = coordinates
            context.coordinator.lastColor = color
            context.coordinator.lastWidth = width
            
            // Re-apply region ONLY if coordinates changed significantly or first time
            if coordsChanged {
                uiView.setRegion(region, animated: true)
            }
            
            uiView.removeOverlays(uiView.overlays)
            uiView.removeAnnotations(uiView.annotations)
            
            if !self.coordinates.isEmpty {
                var coords = self.coordinates
                let polyline = MKPolyline(coordinates: &coords, count: coords.count)
                uiView.addOverlay(polyline)
                
                let start = MKPointAnnotation(); start.coordinate = self.coordinates.first!; start.title = "시작"
                let end = MKPointAnnotation(); end.coordinate = self.coordinates.last!; end.title = "종료"
                uiView.addAnnotations([start, end])
            }
        }
    }
    
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: ApplePathMapView
        var lastCoordinates: [CLLocationCoordinate2D] = []
        var lastColor: Color?
        var lastWidth: CGFloat?
        
        init(_ parent: ApplePathMapView) {
            self.parent = parent
        }
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                // [FIX] 정확한 색상 매칭을 위해 UIColor 변환 로직 보강
                if parent.color == .red { renderer.strokeColor = .red }
                else if parent.color == .blue { renderer.strokeColor = .systemBlue } // 파랑이 보라로 보이는 것 방지
                else { renderer.strokeColor = UIColor(parent.color) }
                
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
             if let img = PinImageHelper.shared.fetchBasePin(named: "PinHistory") {
                 view?.image = img
             } else {
                 view?.image = PinImageHelper.shared.createShieldPin(imageName: "PinTodoReady", color: .red)
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
        // [FIX] 정확한 색상 매칭
        if color == .red { polyline.strokeColor = .red }
        else if color == .blue { polyline.strokeColor = .systemBlue }
        else { polyline.strokeColor = UIColor(color) }
        
        polyline.strokeWidth = width
        polyline.map = uiView
        
        // Markers
        // Markers
        let start = GMSMarker(position: coordinates.first!)
        start.title = "Start"
        if let img = PinImageHelper.shared.fetchBasePin(named: "PinHistory") {
            start.icon = img
        } else {
             start.icon = PinImageHelper.shared.createShieldPin(imageName: "PinTodoReady", color: .red)
        }
        start.map = uiView
        
        let end = GMSMarker(position: coordinates.last!)
        end.title = "End"
        if let img = PinImageHelper.shared.fetchBasePin(named: "PinHistory") {
            end.icon = img
        } else {
            end.icon = PinImageHelper.shared.createShieldPin(imageName: "PinTodoReady", color: .red)
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
    
    func makeCoordinator() -> Coordinator { Coordinator() }
    
    class Coordinator: NSObject {
        var pathOverlay: NMFPath?
        var startMarker: NMFMarker?
        var endMarker: NMFMarker?
    }

    func makeUIView(context: Context) -> NMFNaverMapView {
        let view = NMFNaverMapView()
        view.showZoomControls = false
        return view
    }
    
    func updateUIView(_ uiView: NMFNaverMapView, context: Context) {
        let map = uiView.mapView
        
        // 1. Clear old overlays to avoid duplicates and ensure style updates
        context.coordinator.pathOverlay?.mapView = nil
        context.coordinator.startMarker?.mapView = nil
        context.coordinator.endMarker?.mapView = nil
        
        guard !coordinates.isEmpty else { return }
        let points = coordinates.map { NMGLatLng(lat: $0.latitude, lng: $0.longitude) }
        
        // 2. Draw Polyline
        let path = NMFPath()
        if points.count >= 2 {
            path.path = NMGLineString(points: points)
            // [FIX] 정확한 색상 매칭
            if color == .red { path.color = .red }
            else if color == .blue { path.color = .systemBlue }
            else { path.color = UIColor(color) }
            
            path.width = width
            path.mapView = map
            context.coordinator.pathOverlay = path
        }
        
        // 3. Draw Markers (Start/End)
        let start = NMFMarker(position: points.first!)
        start.captionText = "시작"
        if let img = PinImageHelper.shared.fetchBasePin(named: "PinHistory") {
            start.iconImage = NMFOverlayImage(image: img)
        } else {
            if let fallback = PinImageHelper.shared.createShieldPin(imageName: "PinTodoReady", color: .red) {
                start.iconImage = NMFOverlayImage(image: fallback)
            }
        }
        start.mapView = map
        context.coordinator.startMarker = start
        
        let end = NMFMarker(position: points.last!)
        end.captionText = "종료"
        if let img = PinImageHelper.shared.fetchBasePin(named: "PinHistory") {
            end.iconImage = NMFOverlayImage(image: img)
        } else {
             if let fallback = PinImageHelper.shared.createShieldPin(imageName: "PinTodoReady", color: .red) {
                 end.iconImage = NMFOverlayImage(image: fallback)
             }
        }
        end.mapView = map
        context.coordinator.endMarker = end
        
        // 4. Fit Bounds (Only once or if needed? Usually once is better for UX)
        // For PathHistory, we fit to the entire path.
        let bounds = NMGLatLngBounds(southWest: points.first!, northEast: points.last!)
        var finalBounds = bounds
        points.forEach { finalBounds = finalBounds.expand(toPoint: $0) }
        
        // Minimum span check
        let minDelta = 0.003
        if (finalBounds.northEast.lat - finalBounds.southWest.lat) < minDelta || 
           (finalBounds.northEast.lng - finalBounds.southWest.lng) < minDelta {
            let centerLat = (finalBounds.northEast.lat + finalBounds.southWest.lat) / 2
            let centerLon = (finalBounds.northEast.lng + finalBounds.southWest.lng) / 2
            finalBounds = NMGLatLngBounds(
                southWest: NMGLatLng(lat: centerLat - minDelta/2, lng: centerLon - minDelta/2),
                northEast: NMGLatLng(lat: centerLat + minDelta/2, lng: centerLon + minDelta/2)
            )
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
            let uiColor: UIColor
            if selectedColor == .red { uiColor = .red }
            else if selectedColor == .blue { uiColor = .systemBlue }
            else { uiColor = UIColor(selectedColor) }
            
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
            let image: UIImage
            if let asset = PinImageHelper.shared.fetchBasePin(named: "PinHistory") { 
                image = asset
            }
            else { 
                image = PinImageHelper.shared.createShieldPin(imageName: "PinTodoReady", color: .red) ?? UIImage()
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
