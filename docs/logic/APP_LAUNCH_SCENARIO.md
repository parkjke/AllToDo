# 앱 실행 및 지도 동작 시나리오 공통 (iOS/Android)

이 문서는 앱 실행 시 지도의 초기화, 줌 레벨 제어, 클러스터링 및 핀 표시 정책을 정의합니다.

## 1. 기본 정의 (Definitions)

### 📍 핀 (Pin)
- **정의**: 위치 정보(Latitude, Longitude)가 존재하며, **"현재 날짜 기준 ±30일 이내"**인 **"할 일(To-Do)"** 항목.
- **제외**: 위치가 없는 할 일, 날짜 범위를 벗어난 할 일, 단순 이동 기록(UserLog)은 "저장된 핀"으로 간주하지 않음.

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
2. **[T+1s or T+3s] 애니메이션 (Delay: 상용 1s / 개발 3s)**:
   - **동작**: 줌 아웃 (Zoom Out) 또는 줌 인(Zoom In).
   - **목표**: 현재 사용자 위치 중심, **줌 17** (초상세).
   - **표시**: 현재 위치 핀(User Pin) 활성화.

### Case B: 저장된 핀이 있는 경우 (With Pins)
1. **[T=0] 초기 로딩 ("핀 기준")**:
   - **중심**: 모든 핀을 포함하는 영역(Bounds)의 중심.
   - **줌 계산**: `FitZoom` (핀이 모두 보이는 최적 줌 레벨).
   - **적용 로직**:
     > **Formula**: `FinalZoom = max(FitZoom, 15)`
     - **핀이 넓게 퍼짐 (`FitZoom` < 15)**: **줌 15**로 강제 설정 (상세 뷰 우선).
     - **핀이 좁게 모임 (`FitZoom` >= 15)**: **`FitZoom`** 그대로 적용 (핀 전체 표시).
2. **[T+1s or T+3s] 애니메이션 ("사용자 기준")**:
   - **동작**: 줌 변경 및 중심 이동.
   - **목표**: 현재 사용자 위치 중심, **줌 17** (초상세).
   - **표시**: 현재 위치 핀(User Pin) 활성화. 가까운 핀들은 클러스터링됨.

### Background Re-entry Logic (백그라운드 복귀)
*   **Threshold (기준 시간)**:
    *   **DEBUG**: 5초
    *   **RELEASE**: 9분 (540초)
*   **동작**:
    *   **Threshold 이내 (짧은 외출)**: 아무 작업 안 함 (기존 화면 유지).
    *   **Threshold 초과 (긴 외출)**: 위 Launch Animation (T+1s/3s) 재실행.

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

## 5. 앱 실행 디버깅 및 로그 표준 (Launch Debugging & Logging Standards)

앱 실행 과정의 디버깅 및 로깅 표준입니다. 로그는 로컬에 저장되며 서버로 전송될 수 있습니다.

### 📋 필수 로그 포인트 (Required Log Points)

앱 실행 시 다음 6단계의 로그가 순서대로 기록되어야 합니다:

#### 1. `set todo list`
*   **시점 (Trigger)**: 로컬 데이터베이스에서 `todoItems` 로딩 완료 시.
*   **내용 (Content)**:
    *   `count_24h`: ±24시간 이내 항목 수.
    *   `count_future`: +24시간 이후 항목 수.
    *   `total`: 전체 항목 수.

#### 2. `setup map`
*   **시점 (Trigger)**: 특정 지도 뷰(`MapView`)가 초기화될 때.
*   **내용 (Content)**:
    *   `provider`: 선택된 지도 공급자 이름 (Apple/Naver/Kakao/Google).
    *   `status_visible`: UI 상태 위젯 표시 여부 (Boolean).
    *   `history_visible`: UI 히스토리 뷰 표시 여부 (Boolean).
    *   `current_loc_btn_visible`: 현 위치 버튼 표시 여부 (Boolean).
    *   `zoom_controls_visible`: 줌 버튼 표시 여부 (Boolean).
    *   `compass_visible`: 나침반 표시 여부 (Boolean).

#### 3. `set current location`
*   **시점 (Trigger)**: `AppLocationManager`가 첫 번째 유효 위치를 수신했을 때.
*   **내용 (Content)**:
    *   `latitude`: 위도.
    *   `longitude`: 경도.
    *   `accuracy`: 수평 정확도 (미터).
    *   `timestamp`: 위치 수신 시간.

#### 4. `setup map zoom`
*   **시점 (Trigger)**: 초기화 애니메이션(Launch Logic) 시작 직전.
*   **내용 (Content)**:
    *   `case`: "Case A" (핀 없음) 또는 "Case B" (핀 있음).
    *   `fit_zoom`: 계산된 최적 줌 레벨 (핀이 있을 경우).
    *   `final_zoom`: 실제 적용된 초기 줌 레벨 (예: 15).

#### 5. `all pin view`
*   **시점 (Trigger)**: `updateAnnotations`가 지도에 핀 추가를 완료한 직후.
*   **내용 (Content)**:
    *   `added_pin_count`: 지도에 추가된 핀 개수.
    *   `user_pin_added`: 사용자 위치 핀 추가 여부 (Boolean).

#### 6. `go current location`
*   **시점 (Trigger)**: 초기 1초 애니메이션(사용자 위치로 줌인) 완료 후.
*   **내용 (Content)**:
    *   `final_action`: "Zoom to User Location (Level 9)".
    *   `success`: 성공 여부 (에러 없으면 True).

### 🛠 구현 (Implementation)

*   **Logger**: `OptimizationLogger.swift`
*   **Type**: `LAUNCH_STEP` (New Enum Case)
*   **Format**: JSON
