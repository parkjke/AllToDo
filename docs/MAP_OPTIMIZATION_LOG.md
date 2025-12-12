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

---

## 📝 작업 내역 (Work Log)

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
3.  **Map Pin Asset Standardization (Design Pending)**:
    *   **Objective**: 모든 맵(Android/iOS)에서 공통으로 사용할 SVG 핀 에셋 제작 (Shield Shape).
    *   **ToDo Pin**: Green (Apple Map Style). States: Ready, Done, Cancel, Fail.
    *   **Receive Pin**: Blue (Apple Map Style). States: Ready, Done, Reject.
    *   **History/Current Pin**: Red (Apple Map Style). History(Star), Current(TBD).
    *   *Note: 내부 아이콘(Mark) 디자인은 추후 결정 후 일괄 생성 예정.*
