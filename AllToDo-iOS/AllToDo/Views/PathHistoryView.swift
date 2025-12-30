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
    @State private var pathItems: [PathItem] = [] // [FIX] Use Int32 Model directly
    
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
                let _ = print(">>> PATH HISTORY BODY: count=\(pathItems.count), provider=\(mapProvider)")
                if !pathItems.isEmpty {
                    switch mapProvider {
                    case .apple:
                        ApplePathMapView(items: pathItems, region: region, color: selectedColor, width: selectedWidth)
                    case .google:
                        GooglePathMapView(items: pathItems, color: selectedColor, width: selectedWidth)
                    case .kakao:
                         KakaoPathMapView(items: pathItems, color: selectedColor, width: selectedWidth)
                    case .naver:
                         NaverPathMapView(items: pathItems, color: selectedColor, width: selectedWidth)
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
            sortBy: [SortDescriptor<PathItem>(\.time, order: .forward)]
        )
        // path 가 없으면 경로 표시할 수 없으니 여기가 실행될 이유가 없어 단순화 한다.(2025)
        let paths = (try? modelContext.fetch(descriptor)) ?? []
        print(">>> PATH HISTORY: Loaded \(paths.count) points for ID: \(searchID)") // [DEBUG LOG]
        self.pathItems = paths // [FIX] Assign Raw Items
    }
}

// MARK: - Apple Maps Implementation
struct ApplePathMapView: UIViewRepresentable {
    var items: [PathItem]
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
        // [FIX] Update coordinator's parent reference to access latest state (color/width)
        context.coordinator.parent = self
        
        // [FIX] Detect changes in coordinates, color, or width to trigger redraw
        let colorChanged = context.coordinator.lastColor != color
        let widthChanged = context.coordinator.lastWidth != width
        let itemsChanged = context.coordinator.lastItemCount != items.count
        
        if itemsChanged || colorChanged || widthChanged {
            context.coordinator.lastItemCount = items.count
            context.coordinator.lastColor = color
            context.coordinator.lastWidth = width
            
            // Re-apply region ONLY if coordinates changed significantly or first time
            if itemsChanged && !items.isEmpty {
                // [FIX] Calculate Region Internally (User Request)
                let rect = GeomUtils.calculateIntBoundingBox(from: items, paddingPercent: 20)
                
                let centerLat = Double(rect.minLat + rect.maxLat) / 200_000.0
                let centerLon = Double(rect.minLon + rect.maxLon) / 200_000.0
                let center = CLLocationCoordinate2D(latitude: centerLat, longitude: centerLon)
                
                let latDelta = Double(rect.maxLat - rect.minLat) / 100_000.0
                let lonDelta = Double(rect.maxLon - rect.minLon) / 100_000.0
                let span = MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lonDelta)
                
                let newRegion = MKCoordinateRegion(center: center, span: span)
                uiView.setRegion(newRegion, animated: true)
            }
            
            uiView.removeOverlays(uiView.overlays)
            uiView.removeAnnotations(uiView.annotations)
            
            if !self.items.isEmpty {
                // [FIX] Int32 -> Double Conversion (Late Binding)
                var coords = self.items.map { CLLocationCoordinate2D(latitude: Double($0.int_lat) / 100_000.0, longitude: Double($0.int_long) / 100_000.0) }
                let polyline = MKPolyline(coordinates: &coords, count: coords.count)
                uiView.addOverlay(polyline)
                
                let start = MKPointAnnotation(); start.coordinate = coords.first!; start.title = "시작"
                let end = MKPointAnnotation(); end.coordinate = coords.last!; end.title = "종료"
                uiView.addAnnotations([start, end])
            }
        }
    }
    
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: ApplePathMapView
        var lastItemCount: Int = 0
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
             if let img = PinImageHelper.shared.fetchPin(type: "01") { // History Type 01
                 view?.image = img
             } else {
                 view?.image = PinImageHelper.shared.fetchPin(type: "10") // Default Type 10
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
    var items: [PathItem]
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
        
        guard !items.isEmpty else { return }
        
        // [FIX] Late Binding Conversion (Int32 -> Double)
        let coordinates: [CLLocationCoordinate2D] = items.map {
            CLLocationCoordinate2D(latitude: Double($0.int_lat) / 100_000.0, longitude: Double($0.int_long) / 100_000.0)
        }
        
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
        let start = GMSMarker(position: coordinates.first!)
        start.title = "Start"
        if let img = PinImageHelper.shared.fetchPin(type: "01") {
            start.icon = img
        } else {
             start.icon = PinImageHelper.shared.fetchPin(type: "10")
        }
        start.map = uiView
        
        let end = GMSMarker(position: coordinates.last!)
        end.title = "End"
        if let img = PinImageHelper.shared.fetchPin(type: "01") {
            end.icon = img
        } else {
            end.icon = PinImageHelper.shared.fetchPin(type: "10")
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
    var items: [PathItem]
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
        
        guard !items.isEmpty else { return }
        
        // [FIX] Late Binding Conversion
        let points = items.map { NMGLatLng(lat: Double($0.int_lat)/100_000.0, lng: Double($0.int_long)/100_000.0) }
        
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
        let headerSize = CGSize(width: 36, height: 45) // Naver Scale 0.9x
        
        let start = NMFMarker(position: points.first!)
        start.captionText = "시작"
        if let img = PinImageHelper.shared.fetchPin(type: "01"), let resized = img.resized(to: headerSize) {
            start.iconImage = NMFOverlayImage(image: resized)
        } else {
            if let fallback = PinImageHelper.shared.fetchPin(type: "10"), let resized = fallback.resized(to: headerSize)  {
                start.iconImage = NMFOverlayImage(image: resized)
            }
        }
        start.mapView = map
        context.coordinator.startMarker = start
        
        let end = NMFMarker(position: points.last!)
        end.captionText = "종료"
        if let img = PinImageHelper.shared.fetchPin(type: "01"), let resized = img.resized(to: headerSize) {
            end.iconImage = NMFOverlayImage(image: resized)
        } else {
             if let fallback = PinImageHelper.shared.fetchPin(type: "10"), let resized = fallback.resized(to: headerSize) {
                 end.iconImage = NMFOverlayImage(image: resized)
             }
        }
        end.mapView = map
        context.coordinator.endMarker = end
        
        // 4. Fit Bounds (Using GeomUtils directly with PathItems)
        // [OPTIMIZATION] No need to convert back to PathPoint!
        let intRect = GeomUtils.calculateIntBoundingBox(from: items, paddingPercent: 15) // 15% padding
        
        let southWest = NMGLatLng(lat: Double(intRect.minLat) / 100_000.0, lng: Double(intRect.minLon) / 100_000.0)
        let northEast = NMGLatLng(lat: Double(intRect.maxLat) / 100_000.0, lng: Double(intRect.maxLon) / 100_000.0)
        
        let finalBounds = NMGLatLngBounds(southWest: southWest, northEast: northEast)
        
        let update = NMFCameraUpdate(fit: finalBounds, paddingInsets: UIEdgeInsets(top: 50, left: 50, bottom: 50, right: 50))
        
        // [FIX] Delay camera update to ensure map layout is ready
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            map.moveCamera(update)
        }
    }
}

// MARK: - Kakao Maps Implementation
// Note: Kakao SDK v2 typically expects one controller. 
// If this fails, consider falling back to Apple Map for this view.
struct KakaoPathMapView: UIViewRepresentable {
    var items: [PathItem]
    var color: Color
    var width: CGFloat
    
    func makeUIView(context: Context) -> KMViewContainer {
        print("\(Date()) >>> kakaomap start: \(items.count) points") // [User Request] Debug Log
        // 여기에서 paths 를 GeomUtils 을 사용해 영역을 계산하고 fit bounds 를 사용해 지도를 설정한다.
        let view = KMViewContainer(frame: UIScreen.main.bounds)
        view.backgroundColor = UIColor.white.withAlphaComponent(0.01) // [FIX] Required
        context.coordinator.createController(view)
        return view
    }
    
    func updateUIView(_ uiView: KMViewContainer, context: Context) {
        context.coordinator.checkEngineActivation()
        context.coordinator.drawPath(items, color: color, width: width)
    }
    
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    
    class Coordinator: NSObject, MapControllerDelegate, KakaoMapEventDelegate {
        var parent: KakaoPathMapView
        var controller: KMController?
        weak var viewContainer: KMViewContainer? // [FIX] Store container
        var items: [PathItem] = []
        var selectedColor: Color = .red // [FIX] Default Red
        var selectedWidth: CGFloat = 6   // [FIX] Default 6
        var hasDrawn = false
        
        init(_ parent: KakaoPathMapView) {
            self.parent = parent
        }
        
        func createController(_ view: KMViewContainer) {
            self.viewContainer = view // [FIX] Capture container
            controller = KMController(viewContainer: view)
            controller?.delegate = self
            controller?.prepareEngine()
            
            // [FIX] Force layout update
            view.setNeedsLayout()
            view.layoutIfNeeded()
        }
        
        func checkEngineActivation() {
            guard let controller = controller else { return }
            if controller.isEnginePrepared && !controller.isEngineActive {
                controller.activateEngine()
            }
        }
        
        func drawPath(_ newItems: [PathItem], color: Color, width: CGFloat) {
             let itemsChanged = newItems.count != self.items.count
             let colorChanged = color != self.selectedColor
             let widthChanged = width != self.selectedWidth
             
             if itemsChanged || colorChanged || widthChanged {
                 self.items = newItems
                 self.selectedColor = color
                 self.selectedWidth = width
                 
                 if hasDrawn {
                     if let mapView = controller?.getView("pathmap") as? KakaoMap {
                         renderPath(mapView: mapView)
                     }
                 }
             }
        }
        
        // Delegate
        func addViews() {
            let defaultPosition: MapPoint
            let rect = GeomUtils.calculateCentroid(from: items)
            defaultPosition = MapPoint(longitude: rect.centerLon, latitude: rect.centerLat)

            let mapviewInfo = MapviewInfo(viewName: "pathmap", viewInfoName: "map", defaultPosition: defaultPosition, defaultLevel: 14)
            controller?.addView(mapviewInfo)
        }
        
        func addViewSucceeded(_ viewName: String, viewInfoName: String) {
            guard let mapView = controller?.getView("pathmap") as? KakaoMap else { return }
            controller?.activateEngine()
            hasDrawn = true
            mapView.isEnabled = true
            
            // [FIX] Force sync View Rect immediately (Safety for Gesture Coordinates)
            if let container = self.viewContainer {
                mapView.viewRect = container.bounds
            }
            
            mapView.eventDelegate = self
            
            renderPath(mapView: mapView)
        }
        
        // MARK: - KakaoMapEventDelegate
        @objc func poiDidTapped(kakaoMap: KakaoMap, layerID: String, poiID: String, position: MapPoint) {
            print(">>> SUCCESS: poiDidTapped")
        }

        @objc func terrainDidTapped(kakaoMap: KakaoMap, position: MapPoint) {
            print(">>> SUCCESS: terrainDidTapped")
        }

        @objc func terrainDidLongPressed(kakaoMap: KakaoMap, position: MapPoint) {
            print(">>> SUCCESS: terrainDidLongPressed")
        }

        // [FIX] Handle Container Resize for Gesture Coordinate Sync
        func containerDidResize(_ size: CGSize) {
            if let mapView = controller?.getView("pathmap") as? KakaoMap {
                mapView.viewRect = CGRect(origin: .zero, size: size)
            }
        }
        
        private func renderPath(mapView: KakaoMap) {
            guard !items.isEmpty, items.count >= 2 else { return }
            
            let manager = mapView.getShapeManager()
            let layer = manager.getShapeLayer(layerID: "pathLayer") ?? manager.addShapeLayer(layerID: "pathLayer", zOrder: 1000) // [FIX] Z-Order 1000
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
            // [FIX] Use unique ID to force style update (prevent Kakao caching old style)
            let styleID = "redPolyline_\(colorHex)_\(selectedWidth)_\(Date().timeIntervalSince1970)"
            
            let style = PolylineStyle(styles: [
                PerLevelPolylineStyle(bodyColor: uiColor, bodyWidth: UInt(selectedWidth * 2), strokeColor: UIColor.clear, strokeWidth: 0, level: 0)
            ])
            manager.addPolylineStyleSet(PolylineStyleSet(styleSetID: styleID, styles: [style]))
            
            // [FIX] Convert Int32 -> MapPoint
            let points = items.map { MapPoint(longitude: Double($0.int_long)/100_000.0, latitude: Double($0.int_lat)/100_000.0) }
            
            let options = MapPolylineShapeOptions(shapeID: "historyLine", styleID: styleID, zOrder: 1000)
            options.polylines.append(MapPolyline(line: points, styleIndex: 0))
            
            if let shape = shapeLayer.addMapPolylineShape(options) {
                 shape.show()
                 print(">>> PATH HISTORY: Kakao Path Rendered - \(items.count) points")
            }
            
            // Fit Bounds (Using GeomUtils directly with PathItem)
            let intRect = GeomUtils.calculateIntBoundingBox(from: items, paddingPercent: 20) // [FIX] Increase padding to 20%
            
            let southWest = MapPoint(longitude: Double(intRect.minLon) / 100_000.0, latitude: Double(intRect.minLat) / 100_000.0)
            let northEast = MapPoint(longitude: Double(intRect.maxLon) / 100_000.0, latitude: Double(intRect.maxLat) / 100_000.0)
            
            let rect = AreaRect(southWest: southWest, northEast: northEast)
            
            // [FIX] Ensure camera move happens after layout (Sheet animation ~0.3s)
            // Use recursive retry if view size is 0 (layout pending)
            func fitBoundsWithRetry(count: Int) {
                if mapView.viewRect.width > 0 && mapView.viewRect.height > 0 {
                    let now = Date()
                    // [FIX] Use CameraUpdate.make(area: rect) immediately
                    mapView.moveCamera(CameraUpdate.make(area: rect))
                    print(">>> KAKAO MAP: [\(now)] Executing Camera Move. Bounds Valid (\(mapView.viewRect.size))")
                } else {
                    if count > 0 {
                        // print(">>> KAKAO MAP: View zero size. Retrying in 0.1s... (\(count))")
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            fitBoundsWithRetry(count: count - 1)
                        }
                    } else {
                        print(">>> KAKAO MAP: Failed to fit bounds after retries. View still zero size.")
                    }
                }
            }
            
            // [FIX] Start retrying immediately (wait 0.1s for safety, not 0.5s)
            // Faster response
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                fitBoundsWithRetry(count: 10)
            }
            
            // Add Pins
            addPins(mapView: mapView, items: items)
        }
        
        func addPins(mapView: KakaoMap, items: [PathItem]) {
            guard let startItem = items.first, let endItem = items.last else { return }
            
            // Convert to Local Coords
            let start = MapPoint(longitude: Double(startItem.int_long)/100_000.0, latitude: Double(startItem.int_lat)/100_000.0)
            let end = MapPoint(longitude: Double(endItem.int_long)/100_000.0, latitude: Double(endItem.int_lat)/100_000.0)
            
            let manager = mapView.getLabelManager()
            let layer = manager.getLabelLayer(layerID: "pathPins") ?? manager.addLabelLayer(option: LabelLayerOptions(layerID: "pathPins", competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 1000))
            
            // Style
            let styleID = "style_PathPin"
            // Re-register if needed or check existence. 
            // Since this is a separate view usage, we register style.
            
            // [FIX] Prioritize Asset with Rasterization
            let image: UIImage
            if let asset = PinImageHelper.shared.fetchPin(type: "01") { 
                 image = asset.resized(to: CGSize(width: 28, height: 35)) ?? asset
            }
            else { 
                image = PinImageHelper.shared.fetchPin(type: "10")?.resized(to: CGSize(width: 28, height: 35)) ?? UIImage()
            }
            
            let iconStyle = PoiIconStyle(symbol: image, anchorPoint: CGPoint(x: 0.5, y: 1.0))
            let style = PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: iconStyle, level: 0)])
            manager.addPoiStyle(style) // Safe to add same ID? usually error if exists.
            // Check implicit: Manager usually throws or ignores. Let's rely on unique ID per view instance or catch error?
            // KakaoMap lifecycle: Styles are per-controller.
            
            // Add POIs
            layer?.addPoi(option: PoiOptions(styleID: styleID, poiID: "start"), at: start)?.show()
            layer?.addPoi(option: PoiOptions(styleID: styleID, poiID: "end"), at: end)?.show()
        }
    }
}
