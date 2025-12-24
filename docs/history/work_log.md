## 2025-12-24
### iOS Map UI & Clustering Policy Optimization
- **지도 물풍선(Callout) UI 테마 일관성 확보**:
  - `ContentView.swift` 내의 모든 지도 오버레이(클러스터 목록, 할 일 상세, 사이드 메뉴 등)와 하단 시트(`.sheet`)에 `.preferredColorScheme(.light)`를 강제 적용했습니다.
  - 이를 통해 사용 중인 휴대폰의 다크 모드 설정과 관계없이 지도 관련 인터페이스는 항상 깨끗한 **화이트 테마**를 유지하도록 하여 가독성과 시각적 일관성을 확보했습니다.
- **클러스터링 우선순위 및 녹색[할일] 핀 가시성 강화**:
  - `UnifiedMapItem.resolveClusterStyle` 로직을 수정하여, 여러 성격의 핀(할 일, 히스토리, 서버 메시지 등)이 한곳에 겹칠 때 **녹색 [할 일] 핀이 최우선적으로 대표 이미지로 표시**되도록 변경했습니다. (기존: 파랑 > 초록 > 빨강 순에서 **초록 > 파랑 > 빨강** 순으로 변경)
  - 녹색 핀의 색상을 앱 표준 테마인 `allToDoGreen`으로 정확하게 매칭하여 디자인 통일성을 높였습니다.
- **카카오맵 안정화 시퀀스 유지 및 검증**:
  - 앱 런칭 및 백그라운드 복귀 시 3초간 모든 핀을 개별(Raw) 상태로 노출한 후, 현재 위치로 줌인하며 클러스터링을 활성화하는 'Raw First' 전략이 흔들림 없이 작동하도록 유지했습니다.
  - 엔진 활성화(`checkEngineActivation`) 및 이벤트 델리게이트 재연결 로직을 점검하여 백그라운드 복귀 시 핀 사라짐 현상을 방지했습니다.

## 2025-12-21
### Android Map Reconstruction & GPS Path Tracking
- **Naver Map SDK Integration (Android Fixes)**:
  - **Build Resolve**: Upgraded `map-sdk` to 3.21.0 and switched to `io.github.fornewid:naver-map-compose` for the "Final Boss" build stability.
  - **Auth Resolve**: Fixed `401 Unauthorized` and `800 Client unspecified` errors by correctly configuring `applicationId` (`kr.navermaptest`) and adding dual `meta-data` entries (`NCP_KEY_ID`, `CLIENT_ID`) in `AndroidManifest.xml`.
  - **Pin Alignment**: Adjusted Naver Marker anchor to **(0.4f, 1.0f)** to perfectly align the pin tip with coordinates, accounting for the iOS-style badge padding.
- **Startup Logic & (0,0) Defense**:
  - **Gwanghwamun Fallback**: Implemented a robust check for `(0,0)` or null coordinates. All maps (Naver, Kakao, Google) now default to **Gwanghwamun (37.5759, 126.9768)** at Zoom 15 during Stage 1 if the location is unavailable.
  - **3-Stage Launch Sequence**: Standardized the startup sequence: Fast jump (Zoom 15) -> Fit Bounds for all pins -> High-detail zoom-in (Zoom 18) to current location.
- **GPS Path Recording & Tracking Layer**:
  - **Continuous Tracking**: Replaced one-time `lastLocation` retrieval with a continuous `LocationCallback` (1s interval, High Accuracy) in `MainScreen.kt` to ensure no movement is missed.
  - **Unified Recording**: Synchronized `TodoViewModel` (History pins) and `GpsAuthViewModel` (Path recording) recording states. Both systems now trigger simultaneously from the "walking person" icon.
  - **GpsAuthOverlay Integration**: Restored the dedicated tracking layer ("New Layer") with speed analysis, time machine playback, and track management.
- **UI/UX Refinements**:
  - **Auto-Hide Drawer**: Modified the "My Info" drawer to automatically close when a map provider is selected.
  - **Distance Alert**: Implemented a Toast notification if pins are further than 500km to explain why they are hidden.
- **Documentation**:
  - Created [**Naver Map Android Case Study**](../NAVER_MAP_ANDROID_CASE_STUDY.md) to preserve the hard-earned troubleshooting knowledge for future reference.

## 2025-12-19
### iOS Path Recording & Visualization Fixes
- **Visualisation Fix**:
  - Found that `NaverMapView`, `GoogleMapView`, and `KakaoMapView` were **not calling** the `updatePath` method in their rendering loop (`updateUIView`).
  - Added `context.coordinator.updatePath(...)` to all three, ensuring that saved history paths are now correctly drawn.
- **Background Recording**:
  - Enabled `allowsBackgroundLocationUpdates = true` in `AppLocationManager.swift` to ensure the app continues recording location even when the screen is locked or the app is in the background (essential for reliable path tracking).
- **KakaoMap Interaction Fixes**:
  - **Sensitivity**: Fixed the "2x movement needed" issue by updating `locationManager.currentSpan` correctly within KakaoMap's `updateSpan`. Previously, it was using a stale default value, causing the update threshold to be too high at deep zoom levels.
  - **Path Visualization**: REMOVED implicit path drawing on the main map for all providers (Apple, Naver, Google, Kakao) as per user request. Path visualization is now exclusively handled by the `PathHistoryView` (Callout -> History Sheet).
- **Background Resume Reliability**:
  - Increased the Launch Sequence delay from 0.1s to **1.0s** in `ContentView` to ensure map engines (GL Context) are fully initialized before receiving camera updates.
  - Added explicit `refreshWasmClusters()` calls within the `performLaunchAnimation` sequence for Naver and Google Maps to guarantee pins are rendered immediately upon resume. (Kakao Map already handles this via `checkEngineActivation`).
- **Interaction Fixes (Google & Kakao)**:
  - **Google Map**: Removed a residual `updatePath` call that was re-introduced during the background resume fix, ensuring paths are not drawn on the main map.
  - **Kakao Map**: Changed pin tap logic to **always show the Callout (Water Balloon)** first, even for single items. Previously, single items skipped the callout and opened the detail sheet directly, which confused the user. Now behavior is consistent with the "Water Balloon -> Tap -> Detail Sheet" flow.
- **UX Improvements**:
  - **Auto-Center**: Implemented camera animation to automatically center the map on a tapped pin across all providers (Apple, Naver, Google, Kakao). This ensures the callout ("Water Balloon") has enough space to appear without being clipped by the screen edge.


## 2025-12-18
### iOS Map Visuals & Logic Refinement
- **Unified Pin Sizes**:
  - Updated `NaverMap` pin rendering to use **48x60** size for both initial "Raw" rendering and subsequent "WASM Cluster" rendering. This resolves the visual discrepancy where pins appeared smaller after the initial animation.
  - Adjusted `PinImageHelper` to respect the input `baseImage` size dynamically instead of hardcoding to 32x40, allowing map-specific sizing (e.g., Naver's larger pins).
- **Zoom Level Standardization**:
  - Updated `GoogleMap` and `KakaoMap` launch animation sequences.
  - Changed the final "Current Location" zoom level from 15 to **18** to match the behavior of the manual "Current Location" button.
- **Tethering (Auto-Centering) Disabled**:
  - Disabled `checkTethering` logic in `NaverMapView.swift` and `GoogleMapView.swift` (iOS) to prevent the map from fighting user panning.
  - Verified `GoogleMapContent.kt` (Android) has the `SmartLocationManager.needsCentering` logic disabled as well.
- **Bug Fixes**:
  - Resolved iOS compilation errors in `NaverMapView` and `GoogleMapView` (duplicate functions, missing properties).
