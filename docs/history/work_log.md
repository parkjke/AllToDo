## 2026-01-08
### 🛰️ Android GPS Auth Overlay Encapsulation & Logic Refinement
- **Experimental Logic Encapsulation (Independent Component Pattern)**:
    - `GpsAuthViewModel.kt`에 흩어져 있던 시험용 상태(Visibility, Points, Time Machine)와 로직(가지치기, 재생 제어)을 모두 `GpsAuthOverlay.kt` 내부의 로컬 뷰 상태로 이동 및 캡슐화 완료.
    - 이를 통해 실험적 기능의 독립성을 확보하고, 향후 기능 제거 및 유지보수가 용이한 '독립형 컴포넌트 패턴'을 정립함.
- **Advanced Trajectory Pruning System 고도화**:
    - **Parametrized Logic**: `prune1`, `prune2` 함수가 외부에서 경로 데이터를 받아 처리하도록 파라미터화하여 범용성을 높임.
    - **Multi-Result Storage**: `stage1Points` 및 `stage2Points`를 배열의 배열(`List<List<GpsAuthPoint>>`) 구조로 변경하여 모든 가공 결과물을 누적 저장하고 관리할 수 있도록 개선.
    - **Sequential Processing Pipeline**: 고속 재생(Fast Replay) 시 전체 경로가 시각적으로 모두 그려진 후에 Stage 2 가지치기가 트리거되도록 실행 시점을 정밀하게 조정하여 시각적 정합성 확보.
- **Build Integrity & Code Cleanup**:
    - **ViewModel Restoration**: `NonCancellable` 임포트 누락 및 트래킹 필수 로직 복구 완료.
    - **MainScreen Sync**: ViewModel에서 제거된 상태 참조를 `setShowActivePath` 등 정규화된 메서드로 교체하여 빌드 에러 해결.
    - **Overlay Polish**: 미사용 변수 및 레거시 코드를 제거하여 프로젝트 전반의 경고와 에러를 박멸하고 빌드 안정성을 확보함.

## 2026-01-06
### 🤖 Agent Process & Environment Optimization
- **Agent Work Rules Integration**:
    - `docs/AGENT_WORK_RULES.md` 내용을 숙지하고 협업 프로세스에 반영 완료.
    - 한국어 소통, `Implementation Plan` 기반 승인 절차, 개별 지도 엔진 보호 정책 등을 최상위 수칙으로 준수.
- **Development Environment Polish**:
    - 안드로이드 스튜디오와 macOS 간의 단축키 충돌(Shortcut Conflict) 이슈 원인 규명 및 해결 가이드(System Settings 및 Keymap 조정) 제공.

## 2026-01-05
### 🎨 UI/UX Detail Refinement & Layout Optimization (iOS/Android)
- **iOS Persistent UI Refinement**:
    - **Search Button Alignment**: `SearchButton`의 위치를 하단 Safe Area로부터 **16pt 여백**을 두도록 조정하여 시각적 안정감과 조작 편의성을 확보함.
    - **Typography Elevation**: 할 일 만들기/상세 화면의 가이드 문구 폰트 크기를 **19pt**로 강화하여 시인성을 대폭 개선함.
    - **Memo Area Expansion**: 창 전체 높이(550pt)를 유지하면서 메모 입력 필드의 기본 높이를 **180pt**로 확장하여 풍부한 입력 공간 확보.
- **Android Layout Standardization**:
    - **Unified Spacing (iOS Style)**: 모든 입력 항목(이름, 인물, 날짜, 메모) 사이의 간격을 **16dp**로 통일하여 iOS 수준의 정갈한 수직 리듬과 디자인 일관성을 구현함.
    - **Button & Icon Enhancement**: 상단 [완료], [취소] 아이콘 크기를 **32dp**로 확대하고, 빌드 안정성을 위해 `Icons.Default` 세트를 적용하여 웅장하고 뚜렷한 조작계 완성.
    - **Height Optimization**: 사용자 피드백에 따라 바텀 시트 전체 높이를 **0.7f(70%)**로 복구하여 화면 점유율을 최적화하고, 내부 메모 영역(min 250dp)은 스크롤을 통해 충분히 활용 가능하도록 레이아웃 재배치.
- **My Info Window & Semantic Theming (Stage 5)**:
    - **Semantic Theme Centralization**: `AppColors.UserProfile` 객체를 도입하여 '내 정보' 창의 모든 색상을 `Color.kt`에서 중앙 관리하고, 각 색상에 상세 한국어 주석을 추가하여 설계 의도 명확화.
    - **UI Reconstruction**: 사용자 피드백을 즉각 반영하여 프로필 아이콘을 **100dp**로 확대하고, 갤러리/프로필/GPS 인증의 **3분할 버튼 레이아웃**을 황금 비율로 복구함.
    - **Layering & Z-Order Optimization**: [내 정보], [GPS 인증], [캘린더] 오버레이가 검색 시스템보다 항상 위에 노출되도록 호출 순서를 최적화하고, 창 활성 시 메인 화면의 검색 버튼을 자동 숨김 처리하여 겹침 문제 해결.
- **Build & Stability Fixes**:
    - **Android 아이콘 참조 오류 수정**: `Icons.Rounded` 호출 시 발생하던 컴파일 에러를 `Icons.Default` 교체 및 크기(32dp) 유지 방식으로 해결함.
    - **Refactoring Scope Recovery**: Z-Order 재배치 중 유실되었던 변수 참조(`isOverlayVisible`, `showCalendar`) 및 우측 컨트롤러(`RightSideControls`)를 완벽히 복구하고, `BorderStroke` 모호성 문제를 네임드 파라미터 사용으로 해결함.

### 🚀 Data Management Elevation & Pin Display Refinement
- **DB & History Logic Elevation (Android)**:
    - **0번 레코드(`CURRENT_LOCATION`) 관리**: 실시간 위치 업데이트 시 DB의 고정 ID 레코드를 즉각 갱신하여 지도의 반응성을 높이고 데이터 비대화를 방지함.
    - **30분 단위 히스토리 자동 분할**: 경로 기록 중 30분 경과 시 세션을 자동으로 분할 저장하여 대규모 데이터 처리 효율성을 확보함.
    - **유실 방지 (DeathScope)**: 앱 종료 또는 강제 스와이프 시 기록 중인 무명 세션을 즉시 영구 저장하도록 보강.
- **Pin Display Filter Refinement (Cross-Platform)**:
    - 핀 표시 시간 범위를 기존 `±24시간`에서 **`-24시간 ~ 무제한(미래)`**으로 확장하여, 어제부터 내일 이후의 모든 일정이 지도에 표시되도록 양대 플랫폼 동시 적용 완료.

- **Unified Search Experience (iOS/Android)**:
    - **UI/UX Refinement**: iOS `SearchOverlay`, `CreateTodoLayer`, `UserProfileView`의 모든 버튼에 `PlainButtonStyle`을 적용하여 유령 그림자(Ghost Shadow) 현상을 완전히 박멸함.
    - **Layout Stability**: iOS 오버레이에 `.ignoresSafeArea(.keyboard)` 정책을 강화하여 키보드 호출 시 화면 레이아웃이 밀리는 현상을 원천 차단함.
    - **Android Polish**: 안드로이드 검색창의 커서 색상(테마 감응), 입력 밑줄(VisualTransformation으로 시각적 제거), 한글 입력 복구 및 볼드(Bold) 스타일 적용 및 거리 단위 km 자동 변환 로직(1000m 이상) 구현.
- **Kakao Search Feature Implementation**:
    - **iOS/Android**: 카카오 로컬 키워드 검색 API 연동 및 `SearchViewModel`을 통한 양대 플랫폼 검색 로직 통합.
    - **Features**: 음성 인식 검색 지원 및 검색 결과 클릭 시 해당 장소로의 즉시 카메라 이동 기능 구현.
    - **Color System**: `searchBarTint` 시맨틱 컬러 도입 및 플랫폼별 라이트 모드 배경(Pure White) 고정 정책 적용.

## 2026-01-03
### 🎉 Map Development Milestone: "지도 완료" (Geospatial Perfection)
이번 업데이트를 통해 AllToDo의 핵심인 4대 지도 엔진(Apple, Google, Naver, Kakao)의 정합성, 안정성, 그리고 시각적 품질을 최종적으로 확보했습니다.
- **Kakao Map 앱 복귀 시 `KMInitializeException` 크래시 해결**:
    - 앱이 백그라운드에서 포그라운드로 복귀할 때 엔진이 완전히 활성화되기 전 SDK API(`forceUpdatePins`, `refreshWasmClusters`)를 호출하여 발생하는 'Not initialized' 에러를 해결했습니다.
    - `isEngineActive` 상태 확인 가드를 추가하고, 불필요한 즉시 호출을 제거하여 안정성을 확보했습니다.
    - `Coordinator`에 `deinit`을 추가하여 `NotificationCenter` observer 중복 문제 및 메모리 누수를 방지했습니다.
- **물풍선(CalloutBubble) 프리미엄 디자인 및 버그 수정**:
    - 너비 260dp, 12dp 라운딩, 꼬리(20x10dp) 및 80% 투명도 `AllToDoGreen` 적용.
    - **스크롤 이슈 해결**: `maxPopupItems`에 의한 리스트 잘림 현상을 해결하기 위해 `take` 제약을 제거하고 스크롤 가능한 `LazyColumn` 구조로 전환.
    - **테마 정책 최적화**: 구글 맵의 시스템 다크 모드 감응 색상 조정 및 네이버/카카오의 라이트 모드 강제 적용(`.forceLightMode`) 완료.
    - **경로 아이콘 조건 강화**: [지도] 버튼 활성화 조건을 `no_of_path >= 2`로 상향하고 '할 일' 항목도 경로가 있으면 표시되도록 로직 통합.
- **단거리 경로 저장 최적화 (30m 미만)**:
    - `GeomUtils.isShortPath`를 활용하여 30m 미만 이동 시 실제 위경도 포인트를 DB에 저장하지 않고 `no_of_path = 1`로 처리하도록 `TodoViewModel.kt` 리팩토링.
    - 불필요한 DB 쓰기를 방지하고 리스트 조회 성능을 개선함.
- **정밀 화면 중앙 정렬(Center-Focused Positioning) 고도화**:
    - `displayMetrics` 의존성을 제거하고 **지도의 실제 픽셀 크기(`width`, `height`)**를 기반으로 오프셋을 계산하도록 개선.
    - **53dp 오프셋 전략**: 핀 끝점(Bottom)을 화면 정중앙 기준 +53dp 지점에 배치하여, 핀 머리(Top)와 물풍선 꼬리(Bottom)가 화면 센터에 완벽히 정렬되도록 전 엔진(Google, Naver, Kakao) 로직 통일.
- **물풍선 및 핀 갤러리 시각적 최적화**:
    - **핀 위치 원상 복구**: 애플, 카카오, 구글, 네이버 등 모든 지도 엔진의 핀 앵커 포인트 및 스케일 설정을 이전 상태로 롤백하여 좌표 정밀도를 확보.
    - **Android Pin Alignment Fix**: 뱃지 핀의 상단 절단 문제를 `overhang` 변수 도입으로 해결하고, 모든 엔진의 앵커를 `0.392f`로 통일 (지도 완료).
- **DB & History Logic Elevation (Android)**: 실시간 위치를 0번 레코드(`CURRENT_LOCATION`)로 즉각 업데이트하여 지도의 반응성을 높이고, 30분 단위 히스토리 자동 분할 및 앱 종료 시 자동 저장 로직(DeathScope)을 구현함.
- **Pin Consistency Refinement**: 핀 표시 시간 범위를 `-24h ~ 무제한`으로 확장하여 미래의 할 일들이 지도에 항상 표시되도록 양대 플랫폼 동시 수정.
    - **물풍선(Callout) 센터링 오프셋 정밀 조정**: 핀 선택 시 지도가 중앙으로 이동할 때, 핀 머리가 가려지지 않도록 엔진별 수직 오프셋(Apple +52pt, Kakao +55pt, Google/Naver +54pt 하향)을 카메라 타겟에 반영.
    - **핀 갤러리 시뮬레이션 동기화**: 실제 지도 환경과 동일한 핀 앵커 및 스케일링이 갤러리 미리보기에도 적용되도록 로직 업데이트.
    - **네이버 맵 스케일 원복**: 네이버 맵 핀 크기를 `0.95x`에서 원래의 `0.9x` (36x45)로 원복.
- **컴파일 및 구문 정합성 확보**:
    - `GoogleMapContent.kt`의 코루틴 스코프 호출 오류, 누락된 `launch` 임포트, `BoxWithConstraints` 관련 중괄호 누락 이슈를 최종 해결.
    - `KakaoMapContent.kt`의 `fromScreenPoint` API 파라미터 타입 mismatch 수정.

### GeomUtils Integration & Google Map Path Fix
- **정수 좌표계 기반 지리 연산 통합 (`GeomUtils`)**:
    - 안드로이드 프로젝트에도 iOS와 동일한 100,000배 정수 좌표계 시스템(`IntRect`)을 도입하여 지리 연산의 정밀도와 일관성을 확보했습니다.
    - `MapBegin.kt`를 리팩토링하여 기존의 절차적 위경도 계산 로직을 `GeomUtils.calculateIntBoundingBox`로 일원화했습니다.
- **선언적 줌 제어 (Declarative Zoom Control)**:
    - 하드코딩된 `delay`와 `setMaxZoomPreference` 등 명령형 줌 락 로직을 전면 제거했습니다.
    - 대신 `GeomUtils.minDelta` 파라미터를 통해 데이터 수준에서 최소 지리 범위를 보장함으로써, 엔진의 자연스러운 애니메이션을 방해하지 않고도 구글 맵의 줌 고착 이슈를 근본적으로 해결했습니다.
- **구글 맵 경로 표시 버그 수정**:
    - `MainScreen.kt`에서 구글 맵 인스턴스에 `livePath` 데이터 바인딩이 누락되었던 문제를 수정했습니다.
    - `GoogleMapContent.kt`의 경로 렌더링 가시성을 강화(선 굵기 8dp, 전체 궤적 점 표시)하여 사용자 피드백의 명확성을 높였습니다.

## 2026-01-02
### Android Map Stabilization & Clustering Optimization
- **Naver Map Freeze Resolution (Silent Killer)**:
    - **Issue**: Naver Map was freezing/crashing on Android due to `InvalidCoordinateException` (NaN coordinates) and incorrect initialization order.
    - **Fix 1 (Order)**: Reordered marker property assignments to set `position` **before** attaching it to the `map`.
    - **Fix 2 (Guard)**: Implemented a robust `NaN` guard at the beginning of the cluster rendering loop to skip invalid data without stopping the engine.
    - **Diagnosis**: Identified the issue through instrumented `try-catch` logging after a 4-hour investigation into thread contention proved to be a red herring.
- **Cross-Platform Stabilization (Google/Kakao)**:
    - Proactively applied the same `NaN` coordinate guards to `GoogleMapContent.kt` and `KakaoMapContent.kt` to ensure architectural consistency and prevent similar crashes.
- **Log Purge & Code Purity**:
    - Removed all instrumented debug logs and over 20 `System.out.println` statements from `MapFeatureViewModel`, `NaverMapContent`, and `GoogleMapContent` to restore a clean production-ready state.
- **Technical Documentation Refinement**:
    - Updated `map_begin_logic.md` and `APP_LAUNCH_SCENARIO.md` with the new **ScreenWidthMeters-based clustering** strategy (1.5x threshold) and unified **SSOT filtering** rules (+/- 24h, `no_of_path > 0`).
    - Added a detailed 'Silent Killer' post-mortem to `NAVER_MAP_ANDROID_CASE_STUDY.md`.
- **iOS Clustering Optimization & Stability (Apple, Naver, Google, Kakao)**:
    - **Smoothing Algorithm**: Implemented the **4-step Smoothing Algorithm** across all iOS map providers to ensure visual continuity during cluster transitions (New Entry -> Merge Cleanup -> Old Cluster Cleanup -> New Cluster Add).
    - **NaN Guarding**: Applied strict `isNaN` checks in `AppleMapView.swift`, `NaverMapView.swift`, `GoogleMapView.swift`, and `KakaoMapView.swift` to prevent coordinate-related crashes and freezes.
    - **Standardization**: Unified the 1.5x clustering threshold and ScreenWidthMeters-based logic for all iOS platforms, matching the optimized Android implementation.
- **Technical Documentation Synchronization**:
    - Updated `map_definition_logics.md`, `map_begin_logic.md`, and `APP_LAUNCH_SCENARIO.md` to precisely reflect the latest 2026-01-02 technical standards.
    - Standardized terminology for **'Properties First Strategy'**, **'NaN Guarding'**, and the **'4-Step Smoothing Algorithm'** across all high-level logic documents.
    - Synchronized platform-specific implementation details (Android's `MapFeatureViewModel` and iOS's `MapView` coordinators) to ensure a single truth for the cross-platform geospatial engine.

## 2026-01-01
### Web Deployment Refactor & Android Map Stabilization
- **Web Landing Page Deployment Strategy (Final)**:
  - **Refactor**: Renamed build output directory from `docs` to **`dist`** to follow standard conventions and avoid conflict with project documentation.
  - **Documentation Restoration**: Restored the original `docs/` folder containing project specifications and history logs (which were temporarily displaced).
  - **Manual CI Workflow**: Implemented `.github/workflows/deploy-docs.yml` to manually archive the `dist` folder using `tar` and upload it to GitHub Pages, bypassing the buggy "root folder" detection of the default action.
  - **Result**: `alltodo.kr` domain verification complete, automated deployment pipeline established (`npm run build` -> `git push` -> CI deploys `dist`).

- **Android Google Map Stabilization**:
  - **Freeze Fix**: Resolved the UI freeze issue on Google Map loading by ensuring correct initialization sequence and clearing corrupted cache via clean install.
  - **Logging**: Added detailed logs to `MapBeginSequence` and `GoogleMapContent` to track lifecycle states.

- **Path Recording "Blue Dot" Feedback**:
  - **Issue**: Visual lag between actual user location and the optimized (RDP-processed) path line causing "is it recording?" uncertainty.
  - **Solution**: Implemented an immediate "Blue Dot" trail for the last 20 raw location points on all map engines (Naver/Google/Kakao).
  - **Implementation**: Used `Circle` overlay (Google), `Marker` with dot bitmap (Naver), and `Label` (Kakao) to render the trail effectively.

## 2025-12-31
### Path History Refinement & KakaoMap Standalone Implementation
- **KakaoPathMapView 독립 구현 (`PathHistoryView.swift`)**:
  - `ApplePathMapView` 패턴을 준수하여 `KakaoPathMapView`를 독립적인 `UIViewRepresentable`로 구현 완료.
  - 전용 `KMController` 생명주기 관리 및 `KMPath`, `Poi` 렌더링 로직 통합.
  - `DebugKMViewContainer`를 도입하여 SwiftUI 시트 내에서의 지도 제스처 안정성 확보.
- **물풍선(Callout) UI 개선 (`AppleMapView.swift`)**:
  - `ClusterListCallout` 내 항목 리스트에 경로 점 개수(`no_of_path`) 표시 부착.
  - 히스토리 항목의 경우 지도 아이콘 옆에 `(개수)` 형식을 추가하여 진입 전 상세도 확인 가능.
- **경로 저장 로직 최적화 (`AppLocationManager.swift`)**:
  - 30m 미만 짧은 경로(`isShortPath`) 발생 시, 불필요한 DB 조회를 생략하고 `ToDoItem` 생성 시 즉시 `no_of_path = 1`을 할당하도록 최적화.
- **미결 사항**:
  - **카카오맵 전용 경로보기 확대/축소(Fit Bounds) 에러**: 좌표 계산 로직은 정상이나, 일부 상황에서 카메라 이동이 의도대로 작동하지 않는 이슈가 잔존함.

## 2025-12-30
### Map Pin Design Refinement & Asset Regeneration
- **SVG 마크 디자인 정제 (Icons/map_pin_1)**:
  - **01 (이동 경로)**: 출발점(A)의 녹색 점을 **빨간색 점(#FF3B30)**으로 변경하여 시인성을 강화.
  - **02 (경로)**: 기존 사다리꼴 디자인을 폐기하고, 01번과 동일한 화살표 디자인을 채택하되 **녹색 점(#2FB344)**을 적용하여 차별화. 위치는 쉴드와의 겹침을 방지하기 위해 **좌하단(Y=39)**으로 미세 조정.
  - **00~24 전체**: 00, 01, 02번의 변경 사항과 나머지 마크들의 복구 사항을 반영하여 `merge_pins.py`로 일괄 병합 완료.
- **정적 에셋 생성 로직 수정 (`generate_static_assets.py`)**:
  - 기존의 마크/쉴드 재합성 로직이 병합된 SVG를 무시하는 문제를 발견하고, `pin_merge_XX.svg` 파일을 **있는 그대로(As-Is)** 비트맵(PNG)으로 변환하도록 스크립트를 전면 수정.
  - 이를 통해 SVG 병합 결과와 최종 앱 내 PNG 에셋이 100% 일치하도록 보장.
- **전체 에셋 갱신**:
  - 수정된 스크립트를 통해 `Assets.xcassets/Pins` 내의 모든 핀 이미지(00~24)를 재생성하여 커밋.

## 2025-12-29
- [x] `merge_pins.py` 실행하여 최종 쉴드 결합 완료
- [x] 프로젝트 구조 정리 (mark_v2, map_pin_2 삭제)
- [x] GitHub 업데이트 및 작업 로그 최신화 완료

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


### 2025-12-29
- **Cleanup**: 더 이상 사용되지 않는 초기 디자인 폴더인 `Icons/map_pin_1/mark_v2` 및 `Icons/map_pin_2` 디렉토리를 완전히 삭제하여 프로젝트 구조를 정리했습니다.
- **Merge**: `merge_pins.py`를 실행하여 쉴드와 합성된 최종 SVG 결과를 생성했습니다.
  - Adjusted `PinImageHelper` to respect the input `baseImage` size dynamically instead of hardcoding to 32x40, allowing map-specific sizing (e.g., Naver's larger pins).
- **Zoom Level Standardization**:
  - Updated `GoogleMap` and `KakaoMap` launch animation sequences.
  - Changed the final "Current Location" zoom level from 15 to **18** to match the behavior of the manual "Current Location" button.
- **Tethering (Auto-Centering) Disabled**:
  - Disabled `checkTethering` logic in `NaverMapView.swift` and `GoogleMapView.swift` (iOS) to prevent the map from fighting user panning.
  - Verified `GoogleMapContent.kt` (Android) has the `SmartLocationManager.needsCentering` logic disabled as well.
- **Bug Fixes**:
  - Resolved iOS compilation errors in `NaverMapView` and `GoogleMapView` (duplicate functions, missing properties).
