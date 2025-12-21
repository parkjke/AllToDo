# 앱 실행 및 지도 동작 시나리오 공통 (iOS/Android)

이 문서는 앱 실행 시 지도의 초기화, 줌 레벨 제어, 클러스터링 및 핀 표시 정책을 정의합니다. (2025-12-21 최신화)

---

## 1. 기본 정의 (Definitions)

### 📍 핀 (Pin)
- **정의**: 위치 정보(Latitude, Longitude)가 존재하며, **"현재 날짜 기준 ±30일 이내"**인 **"할 일(To-Do)"** 항목.
- **포함**: 현재 내 위치(Current Location)도 하나의 핀으로 간주하여 계산에 포함함.
- **제외**: 위치가 없는 할 일, 날짜 범위를 벗어난 할 일.
- **원거리 필터 (500km Filter)**:
    - **기준**: 현재 위치에서 **500km**를 초과하는 핀은 지도 표시 및 계산에서 제외.
    - **알림**: 제외된 핀이 있을 경우 "현재 위치에서 너무 먼 핀 X개는 표시되지 않습니다." 토스트 노출 (3초 후 소멸).

### 🔍 줌 레벨 (Zoom Level) 참고
- **Level 15**: 기본 상세 뷰 (Initial View).
- **Level 18**: 현재 위치 집중 뷰 (High-detail / Tracking).
- **작동 원리**: 숫자가 클수록 지도가 확대(Close-up)됩니다.

---

## 2. 앱 실행 시계열 시퀀스 (3-Stage Sequence)

지도가 표시되는 시간, 현 위치 인식 시간, 모든 핀 계산 시간의 경합(Race Condition)을 고려하여 가장 빠른 것부터 단계별로 표시합니다.

### [Stage 1] 초기 지도 로딩 (Fast Display)
*   **조건**: 지도가 준비되었으나 현 위치나 핀 데이터가 아직 준비되지 않은 찰나의 순간.
*   **동작**:
    1.  **기본 위치**: 현재 위치 인식 전까지는 **광화문(또는 앱 서버 기준 기본 좌표)**를 중심으로 표시.
    2.  **줌**: **15**로 일단 지도를 띄워 사용자 대기 체감 감소.
*   **현재 위치 확인 시**: 즉시 지도를 **현재 위치 중심, 줌 15**로 재설정 (이동 애니메이션 없이 즉시 이동 권장).

### [Stage 2] 모든 핀 표시 (All Pins View)
*   **조건**: 500km 필터링 및 전체 핀(내 위치 포함) 계산이 완료된 시점.
*   **동작**:
    1.  **줌 설정**: 모든 핀이 한 화면에 들어오는 최적 영역(Fit Bounds) 계산.
    2.  **줌 캡핑 (Max Zoom 15 Limit)**: 
        - 핀들이 좁은 지역에 모여 있어 계산된 `FitZoom`이 15보다 크면(더 확대되면), **줌 레벨을 15로 고정**.
        - 핀들이 넓게 퍼져 있으면 `FitZoom` 그대로 적용.
    3.  **마커 표시**: **클러스터링을 사용하지 않고**, 모든 핀을 단일 핀으로 지도에 개별 표시.

### [Stage 3] 고정밀 트래킹 전환 (3s Transition)
*   **조건**: 모든 핀 표시 완료 후 **3초 지연**.
*   **동작**:
    1.  **클러스터링 활성화**: 지도상의 개별 핀들을 클러스터링 모드로 전환.
    2.  **줌 인 애니메이션**: 현재 위치(User Location)를 중심으로 **줌 레벨 18**까지 부드럽게 애니메이션 이동.
    3.  **최종 상태**: 내 위치 주변의 핀들은 클러스터링되어 표시됨.

---

## 3. 플랫폼별 구현 참고 (Implementation)

### Android (`TodoViewModel.kt` & `NaverMapContent.kt`)
- `isFirstLaunch` 플래그와 `LaunchedEffect`를 사용하여 Stage 1~3 시퀀스 관리.
- `TodoViewModel`에서 500km 필터링 로직 및 원거리 핀 카운트 제공.
- Naver Map SDK의 `CameraUpdate.fitBounds()`와 `CameraUpdate.zoomTo().animate()` 활용.

### iOS (`ContentViewModel` & `MapView` Controllers)
- `Combine` 또는 `SwiftUI Task`를 사용하여 비동기 데이터(Location/Pins) 상태 감시.
- `MapKit`의 `setRegion`과 `animate` 시퀀스를 통해 Stage 별 부드러운 전환 구현.

---

## 4. 로깅 및 디버깅 표준 (Logging Standards)

1.  **`setup map fast`**: Stage 1 (광화문/기본위치 줌 15) 완료 시.
2.  **`all pin filter`**: 500km 필터 적용 결과 (제외된 핀 수 기록).
3.  **`all pin display`**: Stage 2 (Fit Bounds, 클러스터링 OFF) 완료 시.
4.  **`clustering start`**: Stage 3 애니메이션 직전, 클러스터링 옵션 켰을 때.
5.  **`final zoom 18`**: Stage 3 (현 위치 줌 18) 이동 완료 시.

