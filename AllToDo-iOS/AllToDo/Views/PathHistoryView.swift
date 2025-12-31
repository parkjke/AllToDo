import SwiftUI
import MapKit
import GoogleMaps
import NMapsMap
import SwiftData
import KakaoMapsSDK

// [NOTE] 카카오맵 전용 모드: 카카오맵일 때는 별도 윈도우를 띄우지 않고 
// 메인 지도의 경로 표시 로직을 활용하며, 여기서는 설정창(색상, 두께)과 닫기 버튼만 표시합니다.

struct PathHistoryView: View {
    var item: ToDoItem
    var onClose: () -> Void
    
    @Environment(\.modelContext) private var modelContext
    @AppStorage("selectedMapProvider") private var mapProvider: MapProvider = .apple
    @State private var pathItems: [PathItem] = [] 
    
    @State private var selectedColor: Color = .red
    @State private var selectedWidth: CGFloat = 8.0
    
    var body: some View {
        ZStack(alignment: .topTrailing) {
            Group {
                    switch mapProvider {
                    case .apple:
                        ApplePathMapView(items: pathItems, color: selectedColor, width: selectedWidth)
                    case .google:
                        GooglePathMapView(items: pathItems, color: selectedColor, width: selectedWidth)
                    case .naver:
                        NaverPathMapView(items: pathItems, color: selectedColor, width: selectedWidth)
                    case .kakao:
                        KakaoPathMapView(items: pathItems, color: selectedColor, width: selectedWidth)
                    default:
                        Color.clear
                    }
            }
            .ignoresSafeArea()
            
            // Close Button & Settings
            VStack {
                HStack {
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .black))
                            .foregroundColor(.black)
                            .frame(width: 44, height: 44)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color.white)
                            )
                    }
                    .buttonStyle(.plain)
                    .padding(20)
                }
                Spacer()
                
                // Styling Controls
                if !pathItems.isEmpty {
                    PathSettingsView(
                        selectedColor: $selectedColor,
                        selectedWidth: $selectedWidth,
                        isLightModeForced: true
                    )
                    .padding(.horizontal, 20)
                    .padding(.bottom, 40)
                }
            }
        }
        .onAppear {
            loadPath()
        }
        .interactiveDismissDisabled()
    }
    
    private func loadPath() {
        let searchID = item.todo_id
        let descriptor = FetchDescriptor<PathItem>(
            predicate: #Predicate<PathItem> { $0.todo_id == searchID },
            sortBy: [SortDescriptor<PathItem>(\.time, order: .forward)]
        )
        let paths = (try? modelContext.fetch(descriptor)) ?? []
        self.pathItems = paths
    }
}

// MARK: - Apple Maps Implementation
struct ApplePathMapView: UIViewRepresentable {
    var items: [PathItem]
    var color: Color
    var width: CGFloat
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.isPitchEnabled = false
        return mapView
    }
    
    func updateUIView(_ uiView: MKMapView, context: Context) {
        context.coordinator.parent = self
        
        let colorChanged = context.coordinator.lastColor != color
        let widthChanged = context.coordinator.lastWidth != width
        let itemsChanged = context.coordinator.lastItemCount != items.count
        
        if itemsChanged || colorChanged || widthChanged {
            context.coordinator.lastItemCount = items.count
            context.coordinator.lastColor = color
            context.coordinator.lastWidth = width
            
            if itemsChanged && !items.isEmpty {
                let rect = GeomUtils.calculateIntBoundingBox(from: items, paddingPercent: 20)
                let centerLat = Double(rect.minLat + rect.maxLat) / 200_000.0
                let centerLon = Double(rect.minLon + rect.maxLon) / 200_000.0
                let center = CLLocationCoordinate2D(latitude: centerLat, longitude: centerLon)
                let latDelta = Double(rect.maxLat - rect.minLat) / 100_000.0
                let lonDelta = Double(rect.maxLon - rect.minLon) / 100_000.0
                let span = MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lonDelta)
                uiView.setRegion(MKCoordinateRegion(center: center, span: span), animated: true)
            }
            
            uiView.removeOverlays(uiView.overlays)
            uiView.removeAnnotations(uiView.annotations)
            
            if !items.isEmpty {
                var coords = items.map { CLLocationCoordinate2D(latitude: Double($0.int_lat) / 100_000.0, longitude: Double($0.int_long) / 100_000.0) }
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
        
        init(_ parent: ApplePathMapView) { self.parent = parent }
        
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                renderer.strokeColor = UIColor(parent.color)
                renderer.lineWidth = parent.width
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }
        
        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if annotation is MKUserLocation { return nil }
            let identifier = "HistoryPathPin"
            var view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier)
            if view == nil {
                view = MKAnnotationView(annotation: annotation, reuseIdentifier: identifier)
            } else {
                view?.annotation = annotation
            }
            if let img = PinImageHelper.shared.fetchPin(type: "01") { view?.image = img }
            view?.frame = CGRect(x: 0, y: 0, width: 40, height: 50)
            view?.centerOffset = CGPoint(x: 0, y: -25)
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
        let view = GMSMapView(frame: .zero)
        return view
    }
    
    func updateUIView(_ uiView: GMSMapView, context: Context) {
        uiView.clear()
        guard !items.isEmpty else { return }
        
        let coordinates = items.map { CLLocationCoordinate2D(latitude: Double($0.int_lat) / 100_000.0, longitude: Double($0.int_long) / 100_000.0) }
        
        let path = GMSMutablePath()
        coordinates.forEach { path.add($0) }
        let polyline = GMSPolyline(path: path)
        polyline.strokeColor = UIColor(color)
        polyline.strokeWidth = width
        polyline.map = uiView
        
        let start = GMSMarker(position: coordinates.first!)
        start.icon = PinImageHelper.shared.fetchPin(type: "01")
        start.map = uiView
        
        let end = GMSMarker(position: coordinates.last!)
        end.icon = PinImageHelper.shared.fetchPin(type: "01")
        end.map = uiView
        
        var bounds = GMSCoordinateBounds()
        coordinates.forEach { bounds = bounds.includingCoordinate($0) }
        uiView.animate(with: GMSCameraUpdate.fit(bounds, withPadding: 50))
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
        context.coordinator.pathOverlay?.mapView = nil
        context.coordinator.startMarker?.mapView = nil
        context.coordinator.endMarker?.mapView = nil
        
        guard !items.isEmpty else { return }
        let points = items.map { NMGLatLng(lat: Double($0.int_lat)/100_000.0, lng: Double($0.int_long)/100_000.0) }
        
        let path = NMFPath()
        if points.count >= 2 {
            path.path = NMGLineString(points: points)
            path.color = UIColor(color)
            path.width = width
            path.mapView = map
            context.coordinator.pathOverlay = path
        }
        
        let start = NMFMarker(position: points.first!)
        if let img = PinImageHelper.shared.fetchPin(type: "01") { start.iconImage = NMFOverlayImage(image: img) }
        start.mapView = map
        context.coordinator.startMarker = start
        
        let end = NMFMarker(position: points.last!)
        if let img = PinImageHelper.shared.fetchPin(type: "01") { end.iconImage = NMFOverlayImage(image: img) }
        end.mapView = map
        context.coordinator.endMarker = end
        
        let intRect = GeomUtils.calculateIntBoundingBox(from: items, paddingPercent: 20)
        let sw = NMGLatLng(lat: Double(intRect.minLat)/100_000.0, lng: Double(intRect.minLon)/100_000.0)
        let ne = NMGLatLng(lat: Double(intRect.maxLat)/100_000.0, lng: Double(intRect.maxLon)/100_000.0)
        map.moveCamera(NMFCameraUpdate(fit: NMGLatLngBounds(southWest: sw, northEast: ne), padding: 50))
    }
}

// MARK: - Kakao Maps Implementation
class DebugKMViewContainer: KMViewContainer, UIGestureRecognizerDelegate {
    override init(frame: CGRect) {
        super.init(frame: frame)
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        return true
    }
}

struct KakaoPathMapView: UIViewRepresentable {
    var items: [PathItem]
    var color: Color
    var width: CGFloat

    func makeUIView(context: Context) -> DebugKMViewContainer {
        let view = DebugKMViewContainer(frame: UIScreen.main.bounds)
        view.isUserInteractionEnabled = true
        view.isMultipleTouchEnabled = true
        context.coordinator.createController(view)
        return view
    }

    func updateUIView(_ uiView: DebugKMViewContainer, context: Context) {
        context.coordinator.parent = self
        context.coordinator.checkEngineActivation()
        context.coordinator.updatePath()
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    class Coordinator: NSObject, MapControllerDelegate, KakaoMapEventDelegate {
        var parent: KakaoPathMapView
        var controller: KMController?
        var hasDrawn = false

        init(_ parent: KakaoPathMapView) {
            self.parent = parent
        }

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

        func addViews() {
            let defaultPosition = MapPoint(longitude: 126.9768, latitude: 37.5759)
            let mapviewInfo = MapviewInfo(viewName: "pathmap", viewInfoName: "map", defaultPosition: defaultPosition, defaultLevel: 14)
            controller?.addView(mapviewInfo)
        }

        func addViewSucceeded(_ viewName: String, viewInfoName: String) {
            guard let mapView = controller?.getView("pathmap") as? KakaoMap else { return }
            mapView.eventDelegate = self
            hasDrawn = true
            render(mapView: mapView)
        }

        func updatePath() {
            if hasDrawn, let mapView = controller?.getView("pathmap") as? KakaoMap {
                render(mapView: mapView)
            }
        }

        private func render(mapView: KakaoMap) {
            let shapeManager = mapView.getShapeManager()
            let shapeLayer = shapeManager.getShapeLayer(layerID: "pathLayer") ?? shapeManager.addShapeLayer(layerID: "pathLayer", zOrder: 1000)
            shapeLayer?.removeMapPolylineShape(shapeID: "historyLine")

            let labelManager = mapView.getLabelManager()
            labelManager.removeLabelLayer(layerID: "pathPins")
            let labelLayer = labelManager.addLabelLayer(option: LabelLayerOptions(layerID: "pathPins", competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 1100))

            guard !parent.items.isEmpty else { return }

            // Polyline
            let points = parent.items.map { MapPoint(longitude: Double($0.int_long) / 100_000.0, latitude: Double($0.int_lat) / 100_000.0) }
            if points.count >= 2 {
                let uiColor = UIColor(parent.color)
                let style = PolylineStyle(styles: [
                    PerLevelPolylineStyle(bodyColor: uiColor, bodyWidth: UInt(parent.width), strokeColor: .clear, strokeWidth: 0, level: 0)
                ])
                let styleID = "pathStyle_\(uiColor.hashValue)"
                shapeManager.addPolylineStyleSet(PolylineStyleSet(styleSetID: styleID, styles: [style]))

                let options = MapPolylineShapeOptions(shapeID: "historyLine", styleID: styleID, zOrder: 0)
                options.polylines.append(MapPolyline(line: points, styleIndex: 0))
                shapeLayer?.addMapPolylineShape(options)?.show()
            }

            // Pins
            if let start = points.first, let end = points.last {
                let styleID = "pinStyle"
                if let img = PinImageHelper.shared.fetchPin(type: "01") {
                    let resized = img.resized(to: CGSize(width: 28, height: 35)) ?? img
                    labelManager.addPoiStyle(PoiStyle(styleID: styleID, styles: [PerLevelPoiStyle(iconStyle: PoiIconStyle(symbol: resized, anchorPoint: CGPoint(x: 0.5, y: 1.0)), level: 0)]))
                }
                labelLayer?.addPoi(option: PoiOptions(styleID: styleID, poiID: "start"), at: start)?.show()
                labelLayer?.addPoi(option: PoiOptions(styleID: styleID, poiID: "end"), at: end)?.show()
            }

            // Fit Bounds
            let intRect = GeomUtils.calculateIntBoundingBox(from: parent.items, paddingPercent: 20)
            let sw = MapPoint(longitude: Double(intRect.minLon)/100_000.0, latitude: Double(intRect.minLat)/100_000.0)
            let ne = MapPoint(longitude: Double(intRect.maxLon)/100_000.0, latitude: Double(intRect.maxLat)/100_000.0)
            mapView.moveCamera(CameraUpdate.make(area: AreaRect(southWest: sw, northEast: ne)))
        }
    }
}