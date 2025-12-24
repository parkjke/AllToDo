# AllToDo map definition & logics

## 1. 지도의 위경도 처리 방법 (Coordinate Quantization)
*   **핵심 원칙**: 모든 위경도는 위경도에 `100,000`을 곱한 값의 **정수(Integer)**만 사용하여 저장하고 연산한다. (약 1.1m 오차 무시)
*   **성능 이점**: 부동 소수점 연산 대비 정수 연산으로 기하학적 연산 속도가 **5배 이상** 향상된다.
*   **플랫폼별 구현**:
    - **Android**: `SmartLocationManager.kt`의 `toIntLocation`, `toDoubleLocation` 함수 사용.
    - **iOS**: `SmartLocationManager.swift`의 `toIntLocation` 함수 사용.

---

## 2. 지도를 위한 글로벌 변수 (Global Variables)
*   **저장된 장소 (store location)**: 마지막 확인된 현재 위치 저장. 초기값은 **광화문 좌표**이다.
*   **앵커 기반 스마트 테더링 (Anchor-based Smart Tethering)**: 
    - 지도가 현재 내 위치를 따라가는 기준점을 **앵커(Anchor)**로 관리한다.
    - 내 위치가 앵커로부터 화면 영역(Visible Region) 가로/세로 거리의 **1/4**을 벗어날 때만 지도를 내 위치로 재정렬하고 앵커를 갱신한다.
    - 이를 통해 사용자가 앵커 근처에서 지도를 자유롭게 탐색(Exploration)하는 동안 지도가 튀는 현상을 방지한다.
*   **플랫폼별 구현**:
    - **Android**: `SmartLocationManager.kt`의 `needsCentering` 판정 및 각 `MapContent.kt` 내 `moveLocation` 상태 관리.
    - **iOS**: `SmartLocationManager.swift`의 `shouldRecenter` 판정 및 각 `MapView.swift` 내 `moveLocation` 프로퍼티 활용.

---

## 3. 지도 시작 방식 (3-Stage Map Start Sequence)
*   **1단계 (Fast Jump)**: `store location`을 중심으로 **줌 15.0**에서 엔진을 즉시 렌더링하여 로딩 지연을 최소화한다.
*   **2단계 (Fit Bounds)**: 500km 이내의 모든 핀과 내 위치를 포함하도록 영역을 조정한다. 이때 줌은 15.0으로 제한(Lock)된다.
*   **3단계 (User Focus)**: 3초 후 내 위치로 **줌 18.0**으로 집중하며, **스마트 테더링 앵커를 초기화**한다.
*   **플랫폼별 구현**:
    - **Android**: `MapBegin.kt`의 `MapBeginSequence`가 3단계 제어.
    - **iOS**: 각 `MapView.swift`의 `performLaunchAnimation`에 시퀀스 구현.

---

## 4. 웹어셈블리 (WASM) 함수 (WASM Functions)
*   **클러스터링 (Clustering)**: 가까운 위치들을 묶어 그룹화하는 고속 연산 함수이다.
*   **RDP (Path Compression)**: 이동 경로에서 의미 없는 지점을 걸러내어 데이터를 압축하는 함수이다.
*   **플랫폼별 구현**:
    - **Android**: `TodoViewModel.kt`의 `recalculateClusters` 및 `processBuffer`를 통해 `WasmManager.kt`를 호출한다.
    - **iOS**: `WasmManager.swift` 인스턴스를 통해 `refreshWasmClusters` 및 `processBuffer`를 각각 수행한다.

---

## 5. 자주 사용되는 함수 (Utility Functions)
*   **영역 함수 (Bounding Box)**: 여러 좌표를 포함하는 최소 외곽 영역을 계산한다.
*   **화면 영역 함수 (Visible Region)**: 현재 화면의 위경도 범위를 정수화하여 가로/세로 거리를 산출한다.
*   **플랫폼별 구현**:
    - **Android**: `SmartLocationManager.kt`의 `ensureMinSpan` 및 각 지도의 `Projection` 연산 로직.
    - **iOS**: `SmartLocationManager.swift` 및 `AppleMapView.swift`의 `MKMapRect`, `region.span` 연산 로직.
