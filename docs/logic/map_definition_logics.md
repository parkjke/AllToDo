# AllToDo map definition & logics

## 1. 지도의 위경도 처리 방법 (Coordinate Quantization)
*   **핵심 원칙**: 모든 위경도는 위경도에 `100,000`을 곱한 값의 **정수(Integer)**만 사용하여 저장하고 연산한다. (약 1.1m 오차 무시)
*   **성능 이점**: 부동 소수점 연산 대비 정수 연산으로 기하학적 연산 속도가 **5배 이상** 향상된다.
*   **플랫폼별 구현**:
    - **Android**: `SmartLocationManager.kt`의 `toIntLocation`, `toDoubleLocation` 함수 사용.
    - **iOS**: `SmartLocationManager.swift`의 `toIntLocation` 함수 사용.

## 1.1. 안전 마커 생성 전략 (Properties First Strategy)
*   **핵심 원칙**: 마커 객체를 지도(Map)에 부착하기 전에 모든 필수 속성(`position`, `icon`, `anchor`)을 먼저 설정한다.
*   **이점**: SDK 내부의 유효성 검사 예외를 방지하고, 렌더링 시점에 불완전한 객체가 노출되는 것을 차단한다.
*   **NaN 가딩 (NaN Guarding)**: 위경도 좌표 연성 중 발생할 수 있는 `NaN` 값이 지도 엔진 루프에 진입하지 않도록 `lat.isNaN || lng.isNaN` 체크를 전역적으로 적용한다.

---

## 2. 지도를 위한 글로벌 변수 (Global Variables)
*   **저장된 장소 (store location)**: 마지막 확인된 현재 위치 저장. 초기값은 **광화문 좌표**이다.
*   **앵커 기반 스마트 테더링 (Anchor-based Smart Tethering)**: 
    - 지도가 현재 내 위치를 따라가는 기준점을 **앵커(Anchor)**로 관리한다.
    - 내 위치가 앵커로부터 화면 영역(Visible Region) 가로/세로 거리의 **1/4**을 벗어날 때만 지도를 내 위치로 재정렬하고 앵커를 갱신한다.
    - 재정렬 시 **현재의 줌 레벨을 절대적으로 유지(Zoom Preservation)**하여 탐색의 연속성을 보장한다.
*   **플랫폼별 구현**:
    - **Android**: `SmartLocationManager.kt`의 `needsCentering` 판정 및 각 `MapContent.kt` 내 `moveLocation` 상태 관리.
    - **iOS**: `SmartLocationManager.swift`의 `shouldRecenter` 판정 및 각 `MapView.swift` 내 `moveLocation` 프로퍼티 활용.

---

## 3. 지도 시작 방식 (3-Stage Map Start Sequence)
*   **1단계 (Fast Jump)**: `before location` (없으면 광화문)을 중심으로 **줌 15.0**에서 엔진을 즉시 렌더링한다.
*   **2단계 (Fit Bounds & Raw Pins)**:
    - 500km 이내의 모든 핀과 내 위치를 포함하도록 영역을 조정(Fit Bounds Zoom < 15.0).
    - **클러스터링 비활성화**: 이 단계에서는 모든 핀을 **Raw 상태(개별 핀)**로 렌더링한다.
    - **3초 대기**: 사용자가 핀의 전체적인 분포를 인지할 수 있도록 3초간 상태를 유지한다.
*   **3단계 (User Focus & Cluster)**:
    - 3초 후 내 위치로 **줌 18.0**으로 부드럽게 이동(Zoom In)한다.
    - 이동과 동시에 **클러스터링을 활성화**하여 시각적 정돈을 수행한다.
    - **스마트 테더링 앵커를 초기화**한다.
*   **플랫폼별 구현**:
    - **Android**: `MapFeatureViewModel.kt`의 `launchMapSequence`가 제어.
    - **iOS**: 각 `MapView.swift`의 `performLaunchAnimation` 및 `refreshWasmClusters(force: true)`로 구현.

---

## 4. 웹어셈블리 (WASM) 함수 (WASM Functions)
*   **클러스터링 (Clustering)**:
    - **기준**: 줌 레벨 대신 **화면 가로폭 거리(ScreenWidthMeters, Wm)**를 기준으로 클러스터링 반경을 동적으로 계산한다.
    - **임계값 (1.5x Threshold)**: 잦은 연산 방지를 위해 `Current Wm / Last Clustered Wm` 비율이 **[0.66, 1.5]** 범위를 벗어날 때만 재클러스터링을 수행한다.
    - **4단계 스무딩 (4-Step Smoothing)**: 클러스터 전환 시의 시각적 깜빡임을 제거하기 위해 다음 순서를 엄격히 준수한다.
        1. **신규 진입 (New Entry)**: 기존에 없던 새로운 단독 핀 추가.
        2. **병합 정리 (Merge Cleanup)**: 클러스터에 편입된 기존 단독 핀 삭제.
        3. **기존 클러스터 정리 (Old Cluster Cleanup)**: 유효하지 않은 이전 클러스터 마커 삭제.
        4. **신규 클러스터 추가 (New Cluster Add)**: 계산된 새로운 클러스터 마커 추가.
    - **안정성 (Deterministic Sorting)**: 입력 포인트 순서에 따른 깜빡임(Flickering)을 방지하기 위해 `lib.rs` 내부에서 위경도 기반의 결정론적 정렬을 수행 후 클러스터링한다.
*   **WASM Centroid Sync**: 클러스터 내 뱃지 숫자와 데이터 일관성을 위해 WASM이 반환하는 명시적 Centroid 정보를 기반으로 아이템을 그룹화한다.
*   **현재 위치 앵커링 (Cluster-at-User)**: 클러스터 내부에 '현재 위치'가 포함된 경우, 핀의 시각적 좌표를 중앙값이 아닌 **실제 사용자 좌표**에 고정하여 위치 이탈(Drift)을 방지한다.
*   **RDP (Path Compression)**: 이동 경로에서 의미 없는 지점을 걸러내어 데이터를 압축하는 함수이다.
*   **플랫폼별 구현**:
    - **Android**: `MapFeatureViewModel.kt`에서 Wm 계산 및 `recalculateClusters` 호출.
    - **iOS**: 각 `MapView` Coordinator의 `refreshWasmClusters`에서 1.5x 임계값 체크 후 4단계 스무딩 실행.

---

## 5. 자주 사용되는 함수 (Utility Functions)
*   **영역 함수 (Bounding Box)**: 여러 좌표를 포함하는 최소 외곽 영역을 계산한다.
*   **화면 영역 함수 (Visible Region)**: 현재 화면의 위경도 범위를 정수화하여 가로/세로 거리를 산출한다.
*   **플랫폼별 구현**:
    - **Android**: `SmartLocationManager.kt`의 `ensureMinSpan` 및 각 지도의 `Projection` 연산 로직.
    - **iOS**: `SmartLocationManager.swift` 및 `AppleMapView.swift`의 `MKMapRect`, `region.span` 연산 로직.

---
*Last Updated: 2026-01-02 (iOS Clustering Optimization & Smoothing Algorithm Implementation)*
