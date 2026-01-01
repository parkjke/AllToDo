# 작업 로그: 맵 클러스터링 및 로직 고도화

## 2026-01-02 클러스터링 및 로직 개선 세션

### 작업 개요
Android 및 iOS 양대 플랫폼에서 맵 클러스터링 성능을 개선하고, 초기 실행 시퀀스와 데이터 필터링 로직의 일관성을 확보했습니다.

### 주요 변경 사항

#### 1. 클러스터링 로직 개선 (Android/iOS 공통)
- **Wm (Screen Width in Meters) 도입**: 줌 레벨 대신 화면의 물리적 가로 거리를 기준으로 클러스터링을 수행하도록 변경했습니다.
- **1.5배 임계값 적용**: 잦은 재클러스터링 방지를 위해 `Current Wm / Last Wm` 비율이 `0.66 ~ 1.5` 범위를 벗어날 때만 갱신하도록 최적화했습니다.
- **WASM 정렬 추가**: `lib.rs`에 입력 포인트 정렬 로직을 추가하여 클러스터링 결과의 일관성(깜빡임 제거)을 확보했습니다.

#### 2. Android 구현
- **초기 시퀀스**: Zoom 15 로드 -> 전체 핀 Fit Bounds -> 3초 대기(Raw 핀 노출) -> Zoom 18(사용자 위치) 및 클러스터링 활성화.
- **데이터 갱신**: 5초 이상 백그라운드 후 복귀 시 `+/- 24시간` 데이터를 즉시 갱신하도록 `updateFilteredItems`를 수정했습니다.
- **Idle 트리거**: `GoogleMapContent`, `NaverMapContent`, `KakaoMapContent`에서 카메라 Idle 이벤트를 `MapFeatureViewModel`로 전달하도록 연결했습니다.

#### 3. iOS 구현
- **플랫폼 패리티**: `Kakao`, `Naver`, `Apple` 맵 뷰 컨트롤러에 Wm 계산 및 1.5배 임계값 로직을 이식했습니다.
- **버그 수정**: `KakaoMapView`에서 `cameraDidStopped` 시 클러스터링이 호출되지 않던 문제를 수정했습니다.
- **최적화**: 데이터 변경 시에는 임계값을 무시하고 즉시 반영하도록 `force` 파라미터를 추가했습니다.
- **지오펜싱**: `MapLogicHelper.swift`의 좌표계 처리를 Android와 동일한 정수형(1e5 scale) 로직으로 통일했습니다.

### 검증 상태
- [x] Android 빌드 및 로직 검증 완료
- [x] iOS 빌드 및 로직 이식 완료
- [x] WASM 모듈 업데이트 및 적용 완료
- [ ] 필드 테스트 및 UX 검증 필요

### 관련 파일
- `MapFeatureViewModel.kt`
- `MapLogicHelper.swift`
- `KakaoMapView.swift`, `NaverMapView.swift`, `AppleMapView.swift`
- `lib.rs` (WasmProject)
