# Map Optimization & Feature Parity Log

## 🎯 목표 (Objectives)
*   **Feature Parity**: Android(Google, Kakao, Naver)와 iOS(Apple, Google, Kakao, Naver) 간 기능 동기화.
*   **Modular Architecture**: 맵 공급자(Provider) 별 로직 분리 및 공통 인터페이스 사용.
*   **User Experience (UX)**: 부드러운 애니메이션, 직관적인 제스처, 일관된 UI 제공.

## 📊 현재 기능### 1. Feature Matrix (기능 현황)

| Feature | Android (Kakao) | Android (Google) | Android (Naver) | iOS (Apple) | iOS (Kakao) | iOS (Naver) | iOS (Google) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Basic Map** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Current Location** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Markers (Todo)** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Markers (History)**| ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Path Drawing** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Clustering** | ✅ | ✅ | 🔺 (N/A) | ✅ | ✅ | 🔺 | 🔺 |
| **Callout (Bubble)** | ✅ | ✅ | 🔺 | ✅ | ✅ | 🔺 | 🔺 |
| **Compass** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Long Press Add** | ✅ | ✅ | 🔺 | ✅ | 🔺 | 🔺 | 🔺 |

---

## 🛠 구현 상세 (Implementation Details)

### 1. Android Implementation
*   **구조**: `MainScreen`에서 `MapProvider` 상태에 따라 컴포저블 교체.
    *   `GoogleMapContent`: `maps-compose`, `maps-compose-utils` 사용. 클러스터링 및 커스텀 UI 적용.
    *   `KakaoMapContent`: `KakaoMap SDK` 사용. `AndroidView`로 래핑, 커스텀 마커/경로 처리.
    *   `NaverMapContent`: `Naver Map SDK` 사용. `AndroidView`로 래핑, `PathOverlay` 등 구현.
*   **공통 데이터**: `UnifiedItem` (Todo, History, Location) sealed class로 데이터 통일.
*   **주요 로직**:
    *   **Rotation Sync**: 구글 맵의 회전 각도를 상위(`MainScreen`) 나침반 UI와 동기화 (`snapshotFlow`).
    *   **Launch Animation**: 앱 실행 시 전체 핀을 보여준 뒤 3초 후 사용자 위치로 줌인.

### 2. iOS Implementation
*   **구조**: `ContentView`에서 `MapProvider`(`AppStorage`)에 따라 View 교체.
    *   `GoogleMapView`: `GMSMapView` 사용, `GMSMarker`, `GMSPolyline`.
    *   `KakaoMapView`: `KakaoMap SDK` 사용, `KMViewContainer`, `RouteLine`.
    *   `NaverMapView`: `NMFNaverMapView` 사용, `NMFMarker`, `NMFPath`.
    *   `AppleMapView`: `MapKit` 사용 (기본).
*   **기능 동기화**:
    *   모든 맵에서 `PathHistoryView`를 통해 과거 이동 경로 시각화 지원.
    *   `UserProfileView`에서 맵 엔진 실시간 변경 가능.

---

## 📝 최근 작업 내역 (Recent Updates)

### [2025-12-11] Google Map 고도화 및 문서 정리
1. #### [Android] Google Map Refactoring
*   **Added**: `GoogleMapContent.kt`에 Clustering 및 Camera Animation 추가.
*   **Pending**: 말풍선(Callout) UI 개선 및 롱탭 할일 추가 기능.
2.  **안정성 및 빌드 수정 (Stability & Build Fixes)**:
    *   **Map Provider Crash**: `isMapReady` 상태 분리 (`isGoogle/Kakao/NaverMapReady`)로 맵 전환 시 크래시 해결.
    *   **Scope & ANR**: `viewModelScope` 사용 및 변수 선언 순서/Scope 정리로 ANR 및 `Unresolved reference` 해결.
    *   **MainScreen Structure**: `MainScreen.kt` 레이아웃 구조(`Box` 중첩) 및 괄호 짝 맞추기 완료.
3.  **문서 리팩토링**:
    *   과거 개발 로그 요약 및 기능 명세 위주로 재편.

### 3. Troubleshooting & Lessons Learned (문제 해결 기록)

#### A. ANR (Application Not Responding) & Crash Fixes
*   **Problem**: 앱 종료(`endSession`) 시 `GlobalScope` 사용으로 인한 크래시, `runBlocking` 사용으로 인한 ANR 발생.
*   **Solution**:
    1.  `runBlocking` 제거 → `viewModelScope.launch(Dispatchers.IO)` + `NonCancellable` 패턴 적용.
    2.  메인 스레드를 차단하지 않으면서도 프로세스 종료 전까지 저장 작업을 보장.
    3.  저장 전 `ArrayList`로 데이터 스냅샷을 생성하여 Race Condition 방지.

#### B. Google Map Crash on Provider Switch
*   **Problem**: KakaoMap 사용 후 GoogleMap으로 전환 시, `isMapReady` 상태가 공유되어 GoogleMap이 초기화되기 전에 카메라 이동을 시도하여 크래시 발생 (`newLatLngBounds` 0x0 size error).
*   **Solution**:
    1.  `isMapReady` 변수를 `isKakaoMapReady`, `isGoogleMapReady`, `isNaverMapReady`로 분리.
    2.  각 Map Provider 별로 독립적인 준비 상태를 관리하여 초기화 순서 보장.

#### C. Empty Map Initial Zoom
*   **Problem**: 핀이 없는 상태에서 `LatLngBounds` 생성 시 에러.
*   **Solution**: 핀 데이터가 유효한지(`lat != 0.0`) 확인 후 Bounds 생성, 데이터가 없으면 `Zoom 15`로 현재 위치 포커싱.
    *   시작 애니메이션 추가 (Fit Bounds -> 3s -> User config).
    *   컴파일 오류 수정 (JVM Signature Clash 해결).
2.  **문서 리팩토링**:
    *   과거 개발 로그 요약 및 기능 명세 위주로 재편.

---

## 🚀 Next Steps
1.  **테스트**: Android/iOS 실제 기기 런타임 테스트 및 버그 수정.
2.  **Naver Map 고도화**: Android Naver Map에도 '시작 애니메이션' 등 디테일 추가 검토.
3.  **코드 안정화**: 불필요한 import 제거 및 주석 정리.
