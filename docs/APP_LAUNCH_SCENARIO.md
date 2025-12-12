# 앱 실행 및 지도 동작 시나리오 공통 (iOS/Android)

이 문서는 앱 실행 시 지도의 초기화, 줌 레벨 제어, 클러스터링 및 핀 표시 정책을 정의합니다.

## 1. 기본 정의 (Definitions)

### 📍 핀 (Pin)
- **정의**: 위치 정보(Latitude, Longitude)가 존재하는 **"할 일(To-Do)"** 항목.
- **제외**: 위치가 없는 할 일, 단순 이동 기록(UserLog)은 "저장된 핀"으로 간주하지 않음.

### 🔍 줌 레벨 (Zoom Level) 참고
- **Level 9**: 광역 시/도 뷰 (Wide).
- **Level 15**: 동/읍/면 상세 뷰 (Detailed).
- **작동 원리**: 숫자가 클수록 지도가 확대(Close-up)됩니다.

---

## 2. 앱 실행 시나리오 (Launch Scenario)

### Case A: 저장된 핀이 없는 경우 (No Pins)
1. **[T=0] 초기 로딩**:
   - **중심**: 현재 사용자 위치 (User Location).
   - **줌**: **15** (상세).
2. **[T+1s] 애니메이션**:
   - **동작**: 줌 아웃 (Zoom Out).
   - **목표**: 현재 사용자 위치 중심, **줌 9** (광역).
   - **표시**: 현재 위치 핀(User Pin) 활성화.

### Case B: 저장된 핀이 있는 경우 (With Pins)
1. **[T=0] 초기 로딩 ("핀 기준")**:
   - **중심**: 모든 핀을 포함하는 영역(Bounds)의 중심.
   - **줌 계산**: `FitZoom` (핀이 모두 보이는 최적 줌 레벨).
   - **적용 로직**:
     > **Formula**: `FinalZoom = max(FitZoom, 15)`
     - **핀이 넓게 퍼짐 (`FitZoom` < 15)**: **줌 15**로 강제 설정 (상세 뷰 우선).
     - **핀이 좁게 모임 (`FitZoom` >= 15)**: **`FitZoom`** 그대로 적용 (핀 전체 표시).
2. **[T+1s] 애니메이션 ("사용자 기준")**:
   - **동작**: 줌 변경 및 중심 이동.
   - **목표**: 현재 사용자 위치 중심, **줌 9** (광역).
   - **표시**: 현재 위치 핀(User Pin) 활성화. 가까운 핀들은 클러스터링됨.

---

## 3. UI 및 클러스터링 규칙 (UI & Clustering)

### 🧩 클러스터링 (Clustering)
- **엔진**: WASM 공통 로직 사용 (`clusterPoints`).
- **조건**: 줌 레벨 9에서는 넓은 영역의 핀들이 그룹화될 수 있음.
- **뱃지 표기**:
  - **1 ~ 9개**: 숫자 그대로 표시 (예: `3`).
  - **10개 이상**: **`9+`** 로 통일 표기.

### 📱 플랫폼 공통 사항
- **Android**: `MainScreen.kt` 및 `KakaoMap/NaverMap/GoogleMap` 구현 시 위 로직 준수.
- **iOS**: `ContentViewModel` 및 각 Map View (`Apple/Kakao/Naver/Google`) 컨트롤러에서 위 로직 준수.

---

## 4. Reference Implementation (iOS - Swift)

*AppleMapView.swift 적용 예시 (MapKit)*

```swift
// MARK: - Animation
func performLaunchAnimation(mapView: MKMapView, userLocation: CLLocation?) {
    guard let userLoc = userLocation else { return }
    firstRender = false
    
    // Filter Valid Pins
    let validItems = parent.todoItems.filter { $0.location != nil }
    
    if validItems.isEmpty {
        // Case A: No Pins -> User Loc (Zoom 15 / Span 0.01) -> 1s -> User Loc (Zoom 9 / Span 0.5)
        let startRegion = MKCoordinateRegion(
            center: userLoc.coordinate,
            span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)
        )
        mapView.setRegion(startRegion, animated: false)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            let endRegion = MKCoordinateRegion(
                center: userLoc.coordinate,
                span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5)
            )
            mapView.setRegion(endRegion, animated: true)
        }
    } else {
        // Case B: Has Pins -> Fit Bounds -> Check Zoom 15 -> 1s -> User Loc (Zoom 9)
        // 1. Calculate Bounds
        var mapRect: MKMapRect = .null
        for item in validItems {
            if let loc = item.location {
                let point = MKMapPoint(CLLocationCoordinate2D(latitude: loc.latitude, longitude: loc.longitude))
                let rect = MKMapRect(origin: point, size: MKMapSize(width: 1, height: 1))
                mapRect = mapRect.isNull ? rect : mapRect.union(rect)
            }
        }
        
        // 2. Padding
        let paddedRect = mapView.mapRectThatFits(mapRect, edgePadding: UIEdgeInsets(top: 50, left: 50, bottom: 50, right: 50))
        var region = MKCoordinateRegion(paddedRect)
        
        // 3. Logic: FinalZoom = max(FitZoom, 15)
        // Zoom 15 ≈ Span 0.01. If Span > 0.01 (Wider), Force 0.01.
        if region.span.latitudeDelta > 0.01 {
            region.span = MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)
        }
        
        mapView.setRegion(region, animated: false)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            let endRegion = MKCoordinateRegion(
                center: userLoc.coordinate,
                span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5)
            )
            mapView.setRegion(endRegion, animated: true)
        }
    }
}
```
