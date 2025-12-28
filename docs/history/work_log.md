## 2025-12-29
### v3 Meaningful Pin Design & Refinement (01, 02, 10-14)
- **히스토리 마크 (01, Wandering Dot)**:
  - '돌아다니는 점' 메타포를 도입했습니다. 흰색 테두리가 있는 빨간색 시작점, 지그재그 점선 경로, 그리고 테두리 없는 큰 흰색 현재점으로 구성하여 이동의 궤적을 직관적으로 표현했습니다.
- **저장 마크 (02, Translucent Drum)**:
  - '반투명 드럼통' 메타포를 도입했습니다. 01번의 히스토리 디자인이 짧고 투명한 원통 안에 '반쯤 잠겨(Half-submerged)' 있는 레이어링 효과를 구현하여, 저장된 장소의 정적 가치와 이동의 동적 가치를 통합했습니다.
- **할 일 마크 변주 (10-14, Checkbox Variants)**:
  - **10 (Edgy)**: 네모 프레임을 엣지 있게 뚫고 나오는 반투명 체크 마크. 프레임 테두리와의 여백을 정교하게 조정했습니다.
  - **11 (Translucent)**: 프레임과 체크 마크 모두 반투명하게 처리하여 배경과의 조화를 강조했습니다.
  - **12 (Solid)**: 프레임과 체크 마크 모두 불투명한 순백색으로 강한 가시성을 확보했습니다.
  - **13 (Slashed)**: 중앙 정렬된 프레임에 빨간색 붓 터치 형태의 슬래시(Slash)를 넣어 취소/제외의 의미를 시각화했습니다.
  - **14 (Filled)**: 중앙 정렬된 프레임 내부를 연한 빨강(`#FFB3B3`)으로 채워, 빨간색 쉴드 위에서도 명확히 구분되는 '채워진 할 일' 상태를 구현했습니다.
- **디자인 시스템 및 자동화**:
  - `merge_pins.py`를 통해 Shield(배경)와 Mark(심볼)를 자동으로 합성하는 워크플로우를 확립하고, 모든 V3 핀에 대한 시각적 검증을 완료했습니다.

## 2025-12-28
### iOS Pin Rendering Stabilization & Integer Geometry Integration
- **프로젝트 환경 복구 및 빌드 최적화**:
  - `v1.20-map-integrity` 기반의 코드 복원 및 GitHub 동기화 작업을 완료했습니다.
  - `Info.plist` 중복 생성으로 인한 빌드 오류를 해결하기 위해 메인 파일을 `AllToDo-Info.plist`로 변경하고 프로젝트 설정을 업데이트했습니다.
- **지도 엔진별 핀 렌더링 표준화 및 비트맵 캐싱 (`PinImageHelper`)**:
  - 애플 맵 표준 규격(40x50pt)을 기반으로 모든 지도 엔진의 핀 크기를 통일했습니다.
  - **비트맵 캐싱 시스템**: 동일 이미지의 반복 렌더링을 방지하여 성능을 최적화했습니다.
  - **엔진별 스케일링**: Apple/Google(1.0x), Naver(0.9x), Kakao(0.7x)로 세밀하게 조정하여 시각적 일관성을 확보했습니다.
  - **앵커 포인트 보정**: 우측 상단 뱃지 오버행(8pt)을 고려하여 핀 끝점이 정확한 좌표를 가리키도록 앵커 포인트를 수정했습니다 (Kakao: 14/36, Google: 0.4, Naver: 18/44).
- **정수 좌표 기반 지리 연산 통합 (`GeomUtils`)**:
  - `GeomUtils.calculateIntBoundingBox`를 Apple, Kakao, Naver 지도에 도입하여 히스토리 경로 자동 줌(Fit Bounds) 로직의 정밀도를 높였습니다.
- **컴필레이션 에러 해결**:
  - `AppleMapView.swift`, `PinGalleryView.swift`, `PathHistoryView.swift` 등에서 발생한 중복 선언 및 API 변경으로 인한 에러를 모두 수정했습니다.

## 2025-12-25
### iOS/Android Map UI Refinement & Localization
- **iOS 지도 테마 및 다크 모드 최적화**:
  - **애플/구글 맵**: 기기 설정에 따라 다크/라이트 모드가 자동으로 전환되도록 설정하여 사용자 경험을 최적화했습니다.
  - **카카오/네이버 맵**: SDK 제약 및 UI 일관성을 위해 시스템 테마와 관계없이 항상 **라이트 모드**를 유지하도록 강제 적용했습니다.
  - **오버레이 동기화**: [내 정보], [할 일 상세], [핀 갤러리] 등 모든 오버레이 창이 선택된 지도 제공자의 테마 정책(다크 지원 여부)을 따르도록 `.preferredColorScheme` 로직을 통합했습니다.
- **iOS [내 정보] 창 현지화 및 UI 개선**:
  - 모든 메뉴와 메시지를 한국어로 현지화하여 사용 편의성을 높였습니다.
  - 상단 [X] 닫기 버튼을 기존의 채워진 원형에서 깔끔한 `xmark` 아이콘으로 변경하여 테두리 없이 투명하고 세련된 디자인으로 개선했습니다.
- **iOS/Android 좌상단 [할 일 상태] 위젯 고도화**:
  - **공통**: 배지 순서를 **파랑(서버) > 녹색(로컬) > 빨강(히스토리)** 순으로 재배치하여 플랫폼 간 일관성을 맞췄습니다.
  - **iOS**: 배지 크기를 32pt로 키우고, 5시(Gray 5)에서 12시(White) 방향으로 이어지는 그라데이션 테두리를 적용하여 입체감을 살렸습니다. "할 일" 텍스트를 21pt 굵은 글씨로 확대했습니다.
  - **Android**: 배지 크기를 28dp로 키우고 1dp 화이트 테두리를 추가하여 iOS 버전과 시각적 톤앤매너를 맞췄습니다.
- **iOS 지도 핀 스케일링 미세 조정**:
  - 애플 맵(40x50pt)을 기준으로 **카카오 맵은 0.7배(28x35)**, **네이버 맵은 0.9배(36x45)**로 스케일링 값을 조정하여 지도 화면에서의 시각적 균형을 최적화했습니다.

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
