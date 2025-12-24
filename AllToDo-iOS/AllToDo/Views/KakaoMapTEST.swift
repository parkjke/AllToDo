import SwiftUI
import CoreLocation
import KakaoMapsSDK

struct KakaoMapTEST: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var locationManager: AppLocationManager
    
    var body: some View {
        ZStack {
            SimpleKakaoMapView(locationManager: locationManager)
                .ignoresSafeArea()
            
            VStack {
                HStack {
                    Button(action: { 
                        print(">>> KakaoMapTEST: Close button tapped")
                        dismiss() 
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.largeTitle)
                            .foregroundColor(.white)
                            .padding()
                    }
                    Spacer()
                }
                Spacer()
                
                // Debug Info Overlay
                Text("KakaoMap Clean Test Mode")
                    .font(.caption)
                    .padding(4)
                    .background(Color.black.opacity(0.5))
                    .foregroundColor(.white)
                    .cornerRadius(4)
                    .padding(.bottom, 20)
            }
        }
    }
}

struct SimpleKakaoMapView: UIViewRepresentable {
    @ObservedObject var locationManager: AppLocationManager
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    func makeUIView(context: Context) -> KMViewContainer {
        print(">>> SimpleMap: makeUIView")
        // 화면 크기에 맞춘 컨테이너 생성
        let view = KMViewContainer(frame: UIScreen.main.bounds)
        view.isUserInteractionEnabled = true
        context.coordinator.createController(view)
        return view
    }
    
    func updateUIView(_ uiView: KMViewContainer, context: Context) {
        // 엔진이 아직 준비 중이거나 잠들어 있다면 깨우는 시도
        context.coordinator.checkEngineActivation()
    }
    
    class Coordinator: NSObject, MapControllerDelegate, KakaoMapEventDelegate {
        var parent: SimpleKakaoMapView
        var controller: KMController?
        var isEnginePrepared = false
        var isViewAdded = false
        
        init(_ parent: SimpleKakaoMapView) {
            self.parent = parent
        }
        
        func createController(_ view: KMViewContainer) {
            print(">>> SimpleMap: createController")
            controller = KMController(viewContainer: view)
            controller?.delegate = self
            controller?.prepareEngine()
        }
        
        func checkEngineActivation() {
            guard let controller = controller else { return }
            if !controller.isEnginePrepared {
                print(">>> SimpleMap: Engine not prepared yet...")
                return
            }
            if !controller.isEngineActive {
                print(">>> SimpleMap: Activating Engine Kraftly")
                controller.activateEngine()
            }
        }
        
        // MARK: - MapControllerDelegate
        func addViews() {
            print(">>> SimpleMap: CALLBACK addViews")
            let defaultPosition = MapPoint(longitude: 127.108678, latitude: 37.402056)
            let mapviewInfo = MapviewInfo(viewName: "testMap", viewInfoName: "map", defaultPosition: defaultPosition, defaultLevel: 15)
            
            controller?.addView(mapviewInfo)
            print(">>> SimpleMap: addView requested")
            isViewAdded = true
        }
        
        func addViewSucceeded(_ viewName: String, viewInfoName: String) {
            print(">>> SimpleMap: CALLBACK addViewSucceeded (\(viewName))")
            guard let mapView = controller?.getView(viewName) as? KakaoMap else { return }
            
            // 重要: 이벤트 델리게이트를 본 코디네이터로 연결
            mapView.eventDelegate = self
            
            // 현재 위치로 이동 및 핀 추가
            setupInitialMapState(mapView)
        }
        
        private func setupInitialMapState(_ mapView: KakaoMap) {
            // Global/Parent Location 활용
            if let loc = parent.locationManager.currentLocation {
                print(">>> SimpleMap: Centering on \(loc.coordinate.latitude), \(loc.coordinate.longitude)")
                let pos = MapPoint(longitude: loc.coordinate.longitude, latitude: loc.coordinate.latitude)
                
                // 1. 카메라 이동 (줌 15)
                mapView.moveCamera(CameraUpdate.make(target: pos, zoomLevel: 15, mapView: mapView))
                
                // 2. 파란 핀 추가
                let labelManager = mapView.getLabelManager()
                let layer = labelManager.addLabelLayer(option: LabelLayerOptions(layerID: "testLayer", competitionType: .none, competitionUnit: .poi, orderType: .rank, zOrder: 2000))
                
                let iconStyle = PoiIconStyle(symbol: UIImage(named: "PinReceiveReady")?.resized(to: CGSize(width: 40, height: 50)), anchorPoint: CGPoint(x: 0.5, y: 1.0))
                let style = PoiStyle(styleID: "bluePin", styles: [PerLevelPoiStyle(iconStyle: iconStyle, level: 0)])
                labelManager.addPoiStyle(style)
                
                let poiOption = PoiOptions(styleID: "bluePin")
                poiOption.clickable = true // 터치 가능하도록 명시
                
                if let poi = layer?.addPoi(option: poiOption, at: pos) {
                    poi.show()
                    print(">>> SimpleMap: Test POI Added & Shown")
                }
            } else {
                print(">>> SimpleMap: WARNING - No Location available to center")
            }
        }
        
        // MARK: - KakaoMapEventDelegate (poiDidTapped)
        @objc func poiDidTapped(kakaoMap: KakaoMap, layerID: String, poiID: String, position: MapPoint) {
            print(">>> SimpleMap EVENT: poiDidTapped! layer:\(layerID), poi:\(poiID)")
        }
        
        @objc func terrainDidTapped(kakaoMap: KakaoMap, position: MapPoint) {
            print(">>> SimpleMap EVENT: terrainDidTapped at \(position.wgsCoord.latitude), \(position.wgsCoord.longitude)")
        }

        @objc func terrainDidLongPressed(kakaoMap: KakaoMap, position: MapPoint) {
            print(">>> SimpleMap EVENT: terrainDidLongPressed (Long Touch) at \(position.wgsCoord.latitude), \(position.wgsCoord.longitude)")
        }
        
        func authenticationSucceeded() {
            print(">>> SimpleMap: Auth Succeeded")
        }
        
        func authenticationFailed(_ errorCode: Int, desc: String) {
            print(">>> SimpleMap: Auth Failed (\(errorCode)): \(desc)")
        }
    }
}
