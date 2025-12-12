# Map Optimization & Feature Parity Log

## 🎯 목표 (Objectives)
*   **Feature Parity**: Android(Google, Kakao, Naver)와 iOS(Apple, Google, Kakao, Naver) 간 기능 동기화.
*   **WASM Only Logic**: RDP(경로 최적화) 및 Clustering(핀 그룹화) 로직을 WASM 모듈 하나로 통일.
*   **High Frequency Path**: 0.9초 단위의 정밀한 경로 추적 및 실시간 압축.
*   **Modular Architecture**: 맵 공급자(Provider) 별 로직 분리 및 공통 인터페이스 사용.

## 📊 기능 현황 (Feature Status)

### 1. General Feature Matrix
| Feature | Android | iOS | Status |
| :--- | :---: | :---: | :--- |
| **Path Tracking Interval** | ✅ 0.9s | ✅ 0.9s | **High Frequency Update** |
| **Path Tracking Logic** | ✅ Callback | ✅ Streaming | OS Event Driven 방식으로 통일 |
| **RDP Compression** | ✅ WASM | ✅ WASM | 5개 점마다 Batch 처리 |
| **Clustering** | ✅ WASM | ✅ WASM | Zoom Level 기반 자동 그룹화 |
| **Initial Animation** | ✅ | ✅ | Fit Bounds -> 1s Delay -> Zoom User |
| **Self-Test** | ✅ | ✅ | App 실행 시 WASM 무결성 자동 검증 |
| **Unified Pin Design** | ✅ Shield | ✅ Shield | All Providers Standardized (Green/Red/Blue) |
| **Path History Line** | ✅ | ✅ | UserLog 선택 시 Red Polyline 표시 |

### 2. WASM Integration Status
*   **Single Source**: `compressTrajectory` (RDP), `clusterPoints` (Clustering) 모두 서버 호스팅 WASM 사용.
*   **Auto Update**: 실행 시 버전 체크 -> 다운로드 -> `verifyWasm` 검증 루틴.

---

## 🛠 구현 상세 (Implementation Details)

### 1. Path Recording (Precision Update)
*   **Android (`MainScreen.kt`)**:
    *   기존 5초 Polling 루프 제거.
    *   `LocationRequest` (900ms Interval, High Accuracy) + `requestLocationUpdates` 콜백 방식으로 전환.
    *   `TodoViewModel` 버퍼링 로직 900ms로 동기화.
*   **iOS (`AppLocationManager.swift`)**:
    *   `desiredAccuracy` = `kCLLocationAccuracyBestForNavigation` (최고 정밀도).
    *   `distanceFilter` = `kCLDistanceFilterNone` (모디 거리 허용).
    *   기록 필터: `timeDelta >= 0.9` (0.9초) 로 완화하여 정밀 추적.

### 2. WASM Clustering & RDP
*   **RDP (Path Compression)**:
    *   위치 점이 5개 모일 때마다(`PendingBuffer`) WASM으로 전송.
    *   압축된 결과만 영구 저장소에 남기고 버퍼 비움 (실시간 메모리 최적화).
*   **Clustering (Pin Grouping)**:
    *   Zoom 변경 시 화면 전체 점들을 Grid 기반으로 WASM에서 그룹화.
    *   UI: 10개 이상 시 "9+" 뱃지, Todo(Green)/History(Red) 구분.

### 2025-12-12 App Launch Scenario & Debugging
*   **Documentation**:
    *   Created `docs/APP_LAUNCH_SCENARIO.md`: Defined Zoom 15 -> 9 logic and Clustering rules.
    *   Added `AppleMapView.swift` Reference Implementation to doc.
*   **Implementation**:
    *   **Android (`MainScreen.kt`)**: Implemented new Launch Logic (Case A/B).
    *   **iOS (`AppleMapView.swift`)**: Implemented `performLaunchAnimation` with new logic.
    *   **iOS (`AppLocationManager.swift`)**: Fixed missing `didUpdateLocations` delegate (Critical fix for "No Location" issue).
    *   **iOS (`KakaoMapView.swift`)**: Temporarily disabled Path Visualization to resolve SDK compilation errors.
*   **Status**:
    *   Build Succeeded (Exit 0).
    *   **Issue**: User reports Scenario 1 (No Pins -> Zoom) still not working on iOS despite Location fix. Requires deeper debugging of `updateAnnotations` or Data Binding flow next session.

---

## 4. 📝 작업 내역 (Work Log)

### [2025-12-11] Path Precision & Interval Optimization
1.  **Android**: `fusedLocationClient` 호출을 5초 Polling에서 **0.9초 Callback** 방식으로 전면 수정.
2.  **iOS**: `CLLocationManager` 설정을 최고 민감도(`BestForNavigation`)로 상향하고 인터벌 제한을 1초에서 **0.9초**로 단축.
3.  **Result**: 양대 플랫폼 모두 1초 미만의 정밀한 경로 추적 능력 확보.

### [2025-12-11] WASM Clustering, Test & Animation (Previous)
*   `WasmRuntime` 확장 (Cluster), `GoogleMap` 렌더링 리팩토링.
*   WASM Self-Test (RDP+Cluster) 추가 및 초기 애니메이션 시퀀스(Smart Zoom) 구현.
*   안정성 패치: Provider 교차 크래시 해결, ANR 방지(`NonCancellable`).

### [2025-12-12] iOS Map Fixes & Session Feature Parity
1. **Kakao Map Path Fix (iOS)**:
    *   `RouteManager` API 불일치 문제 해결 -> `ShapeManager` + `MapPolyline` 방식으로 전면 교체하여 안정적인 경로 렌더링 확보.
    *   빌드 에러 완전 해결 및 Provisioning Profile 설정 가이드.
2. **Session Recording Implementation (iOS)**:
    *   `AppLocationManager.swift`에 누락되었던 `startSession`, `endSession` 구현.
    *   Android와 동일하게 경로 버퍼링 -> WASM 압축 -> 영구 저장 로직 완성.
3. **Verification**:
    *   Android/iOS 양대 플랫폼 0.9초 정밀 추적(High Frequency) 코드 교차 검증 완료.

---

## 🔧 Troubleshooting & Lessons Learned

### A. Polling vs Callback (Location)
*   **Issue**: Android에서 `delay(5000)` 루프 사용 시 1초 단위 정밀 기록 불가.
*   **Fix**: `LocationCallback`을 사용하여 OS 이벤트 기반으로 전환, 900ms 간격 확보.

### B. iOS High Frequency
*   **Issue**: 기본 `Best` 정확도만으로는 1초마다 꾸준히 위치를 주지 않음 (배터리 절약 모드 동작).
*   **Fix**: `BestForNavigation` + `activityType = .fitness` 조합으로 센서를 강제 활성화.

---

## 🚀 Next Steps
1.  **Battery Optimization**: 0.9초 기록은 배터리 소모가 크므로, 정지 상태 감지(Motion Detection) 시 기록 일시 중지 로직 고려.
### [2025-12-12] Map Pin Asset Standardization (Completed)
1.  **Objective**: Android/iOS 공통 SVG 핀 에셋 표준화 (Shield Shape, Apple Map Style).
2.  **Implementation**:
    *   `generate_pins.py`: SVG 자동 생성 (ToDo: Green, History/Current: Red, Receive: Blue).
    *   **Android**: `svg2vectordrawable`로 XML 변환 및 `MapCommon.kt` 동적 리소스 매핑 적용.
    *   **iOS**: `integrate_ios_assets.py`로 Asset Catalog 등록 및 `UnifiedMapModels` 이미지 매핑 적용.
3.  **Result**: 모든 맵 뷰에서 상태별 올바른 핀 아이콘 표시 로직 통합 완료.

### [2025-12-12] Battery Optimization & Log Analysis System
1.  **Objective**: 정밀 추적으로 인한 배터리 소모 최적화 및 동작 분석.
2.  **Implementation**:
    *   **Android (`MotionDetector.kt`)**: Activity Recognition API로 `STILL` 상태 감지 시 위치 업데이트 일시 정지(`removeLocationUpdates`).
    *   **iOS (`AppLocationManager.swift` / `OptimizationLogger.swift`)**: `CMMotionActivityManager`로 정지 상태 감지 시 `stopUpdatingLocation` 및 로깅.
    *   **Backend (`dev.py`)**: `/dev/logs/batch` (업로드) 및 `/dev/logs/view` (HTML 뷰어) 구현.
    *   **Client Upload**: `UserProfileView` Triple Tap 트리거로 로컬 JSON 로그 서버 전송 기능 구현.
3.  **Result**: 기기 움직임에 따른 지능형 위치 추적 제어 및 원격 로그 분석 체계 구축.

### [2025-12-12] AllToDo-WebMng (Management Console) Kickoff
1.  **Objective**: 관리자 및 상담원을 위한 웹 콘솔 프론트엔드 구축.
2.  **Stack**: Vite + React + TypeScript + Vanilla CSS (Premium Design).
3.  **Implementation**:
    *   **Auth**: 로그인, 직원 등록(Register) UI.
    *   **Dashboard**: 주요 통계 및 바로가기.
    *   **Consultation**: B2B(전화번호 검색/고지서 발송) 및 사용자(지도 위치 조회) 상담 화면.
    *   **Master Admin**: 직원 승인/정지 관리 기능.
4.  **Status**: UI 구현 완료 (Mock Data 기반), 백엔드 연동 준비 상태.

### [2025-12-12] iOS Map Standardization & Path Visualization (Completed)
1. **Objective**: Standardize Map Pins and implement Path Visualization across all 4 map providers (Apple, Kakao, Naver, Google).
2. **Implementation**:
    *   **PinImageHelper**: Created unified helper to generate "Shield" style pins (Green/Red/Blue) programmatically.
    *   **Path Visualization**: Implemented `updatePath` in all Map Views to draw Red Polyline for selected User Log.
    *   **Validation**:
        - **Apple Maps**: Used `MKPolyline`.
        - **Kakao Maps**: Used `MapPolylineShape` with `PolylineStyleSet` (Fixed SDK API usage).
        - **Naver Maps**: Used `NMFPath`.
        - **Google Maps**: Used `GMSPolyline`.
3. **Result**: Consistent visual experience and functional path tracking across all supported maps.
