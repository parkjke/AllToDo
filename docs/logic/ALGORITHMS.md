# AllToDo 지도 알고리즘 및 로직 문서

## 1. 스마트 트래킹 (Smart Tracking) & 위치 최적화

### 1.1 개요
스마트 트래킹(Smart Tracking)은 지도 앱의 성능 최적화와 배터리 절약을 위해 고안된 위치 갱신 알고리즘입니다. 사용자의 움직임에 따라 지도를 무조건 갱신하는 것이 아니라, **현재 줌 레벨**과 **이동 거리**를 계산하여 "시각적인 변화가 필요한 경우"에만 UI를 업데이트합니다.

### 1.2 핵심 로직

#### A. 정수 좌표 사용 (Integer Coordinates)
*   **개념**: 부동 소수점(`Double`) 좌표의 오차와 미세한 떨림(Jitter)을 방지하기 위해, 위도/경도에 `100,000`을 곱해 `Int`로 변환하여 처리합니다.
*   **공식**: `IntLat = Lat * 100,000`
*   **효과**: 연산 속도 향상(ALU 사용), 메모리 절약, 비교 연산의 정확성 보장.

#### B. 동적 임계값 (Dynamic Threshold)
*   **개념**: 줌 레벨에 따라 재렌더링 민감도를 조절합니다. 확대 시 민감하게, 축소 시 둔감하게 반응합니다.
*   **Android**:
    ```kotlin
    val threshold = if (zoom >= 18) 2 else if (zoom >= 15) 5 else ...
    ```
*   **iOS**:
    ```swift
    let threshold = max(2, Int(currentSpan * 200.0))
    ```

### 1.3 500km 필터링 (Far Item Filtering)
*   **문제**: 현 위치에서 매우 먼(예: 해외) 핀이 존재할 경우, `Fit Bounds` 수행 시 지도가 대륙 단위로 줌아웃되는 문제.
*   **해결**: 현 위치 반경 **500km 이내**의 핀만 "유효한 핀"으로 간주하여 줌 계산 및 렌더링에 포함합니다.
*   **UX**: 500km 밖의 핀은 렌더링하지 않고, **"N개의 할 일이 멀리 있습니다"** 토스트 메시지로 알림.

---

## 2. 스마트 테더링 (Smart Tethering - Auto Center)

### 2.1 개요
기존의 테더링(Tethering) 방식은 사용자가 지도를 드래그하는 동안에도 강제로 중심을 맞추려 하여 "지도와 싸우는(Fighting)" 느낌을 주었습니다. **스마트 테더링**은 "개줄(Leash)" 개념을 도입하여, 사용자가 마지막으로 고정된 위치(Anchor)로부터 일정 범위(화면의 1/4) 내에서는 자유롭게 이동할 수 있도록 합니다.

### 2.2 정의
1.  **Int Location**: `Angle * 100,000` 형태의 정수 좌표. 부동 소수점 불안정성 제거.
2.  **Move Location (Anchor)**: 지도가 마지막으로 프로그램에 의해 고정된 중심점(초기 로딩 혹은 재진입 시점).
3.  **H Length (가로 폭)**: 현재 화면에 보이는 지도의 가로 경도 범위 (Integer Units).
4.  **V Length (세로 폭)**: 현재 화면에 보이는 지도의 세로 위도 범위 (Integer Units).

### 2.3 로직
사용자의 현재 위치가 `Move Location`으로부터 허용 범위(Threshold)를 벗어날 때만 지도를 재정렬(Re-center) 합니다.

```swift
func shouldRecenter(user: IntLocation, moveLoc: IntLocation, hLen: Int, vLen: Int) -> Bool {
    let deltaLat = abs(user.lat - moveLoc.lat)
    let deltaLon = abs(user.lon - moveLoc.lon)
    
    // 임계값: 화면 폭의 1/4
    let thresholdH = hLen / 4
    let thresholdV = vLen / 4
    
    // 가로 혹은 세로 중 하나라도 임계값을 넘으면 트리거
    return deltaLat > thresholdV || deltaLon > thresholdH
}
```

### 2.4 지도별 구현 방식
*   **Apple Map (`MKMapView`)**: `region.span` 사용.
*   **Google Map (`GMSMapView`)**: `projection.visibleRegion()`의 영역(Bounds) 사용.
*   **Naver Map (`NMFMapView`)**: `contentBounds` 사용.
*   **Kakao Map (`KakaoMap`)**: `metersPerPixel`을 이용하여 화면 픽셀 크기로부터 역산.
67: 
68: ### 2.5 사용자 시나리오: 정지 vs 이동
69: *   **정지 상태 (안전함)**:
70:     *   지도를 확대/축소/회전/이동해도, '내 위치'와 '기준점(Anchor)'은 변하지 않습니다 (거리차 = 0).
71:     *   화면 범위가 변하더라도 **재배치 조건(거리차 > 범위/4)**을 충족할 수 없으므로, **갑자기 위치가 튀는 현상은 발생하지 않습니다.**
72: *   **이동 중 (주의)**:
73:     *   내 위치가 계속 변하기 때문에, 다른 곳을 보고 있더라도 **'내 위치'가 '기준점'에서 화면의 1/4 이상 멀어지는 순간** 강제로 화면이 내 위치로 돌아옵니다.
74:     *   이후 '기준점'이 현재 '내 위치'로 갱신되므로, 다시 멀어지기 전까지는 자유롭게 조작 가능합니다.

---

## 3. 핏 바운즈 & 런치 시퀀스 (Fit Bounds & Launch)
(상세 내용은 `APP_LAUNCH_SCENARIO.md` 참조)

## 4. WASM 클러스터링 (WASM Clustering)
*   **Grid 방식**: 화면을 그리드로 나누어 가까운 핀들을 그룹화.
*   **우선순위**: Red(History/User) > Green(Local) > Blue(Server).
*   **뱃지**: 10개 이상은 "9+"로 표기.
