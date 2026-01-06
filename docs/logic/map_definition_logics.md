# AllToDo 통합 지도 클러스터링 로직 (Unified Map Logics)

이 문서는 AllToDo 프로젝트의 고성능 지도 클러스터링 시스템을 지탱하는 핵심 수치 연산 및 UX 원칙을 상세히 기술합니다. 시간이 지나도 로직의 설계 의도(Design Intent)를 명확히 파악할 수 있도록 돕는 것을 목적으로 합니다.

---

## 1. 핵심 철학: Wm (ScreenWidthMeters) 기반 동적 반경
> [!NOTE]
> 왜 줌 레벨(Zoom Level)을 직접 사용하지 않는가?

*   **문제점**: 지도 엔진마다(Apple, Google, Naver) 줌 레벨의 정의가 미세하게 다르고, 같은 줌이라도 위도(Latitude)에 따라 실제로 화면에 보이는 거리(m)가 왜곡됩니다.
*   **해결책**: 사용자의 눈에 보이는 **"화면 가로폭에 담긴 실제 미터 거리(Wm)"**를 기준으로 삼습니다.
    *   이를 통해 모든 엔진에서 시각적으로 **동일한 밀도의 클러스터링**을 보장합니다.
    *   줌을 확대하면 Wm이 작아지고, 축소하면 Wm이 커지는 반비례 관계를 활용합니다.

---

## 2. metersPerPixel 표준 계산법 (Pixel Sampling)
> [!TIP]
> 지도의 회전(Rotation)과 기울기(Tilt)에도 무너지지 않는 가장 정교한 방식입니다.

과거에는 `156543 * cos(lat) / 2^zoom` 같은 근사식을 썼으나, 이는 지도가 기울어지면(Tilt) 맞지 않습니다. AllToDo는 **실측 샘플링** 방식을 채택합니다.

### 계산 절차
1.  **샘플링**: 화면 정중앙 가로선상의 양 끝 픽셀 좌표를 잡습니다.
    -   좌측 끝: `(0, H/2)`
    -   우측 끝: `(W, H/2)`
2.  **역투영(Unprojection)**: 각 지도 엔진의 `Projection` 도구를 이용해 이를 실제 위경도로 변환합니다.
3.  **거리 측정**: 두 위경도 사이의 물리적 거리(m)를 측정합니다.
4.  **산출**: `metersPerPixel = distance / screenWidthPx`

### 왜 이 방식인가?
-   지도가 회전되거나 틸트되어 있어도, 엔진의 `Projection`은 이미 그 모든 왜곡을 계산에 넣고 있습니다. 따라서 우리는 단순히 **"1픽셀이 지상에서 몇 미터인지"**를 가장 정확한 팩트로 얻을 수 있습니다.

---

## 3. 나눗셈 배제 원칙 (No-Division Principle)
> [!IMPORTANT]
> 60FPS를 유지해야 하는 지도 렌더링 루프에서 부동 소수점 나눗셈은 치명적입니다.

### 수치 최적화의 근거
-   **CPU 부하**: `DIV`(나눗셈) 명령어는 `MUL`(곱셈) 대비 약 **10~40배** 긴 CPU 사이클을 소모합니다. 
-   **안정성**: 나눗셈 시 발생하는 정밀도 손실이나 `Divide by Zero` 에러를 원천 봉쇄합니다.

### 1.5배 임계값(Threshold) 공식 유도
우리는 화면 가로폭 `Wm1`이 이전 시점 `Wm0` 대비 1.5배 이상 변했을 때만 재계산하고 싶습니다.

1.  **기본식 (나눗셈 버전)**: `ratio = Wm1 / Wm0`, 만약 `ratio > 1.5` 거나 `ratio < 0.66` 이면 재계산.
2.  **변환식 (곱셈 버전)**:
    -   **축소(Zoom-Out)**: `Wm1 >= 1.5 * Wm0`  $\rightarrow$  `Wm1 >= (3/2) * Wm0`  $\rightarrow$  **`2 * Wm1 >= 3 * Wm0`**
    -   **확대(Zoom-In)**: `Wm1 <= 0.66 * Wm0`  $\rightarrow$  `Wm1 <= (2/3) * Wm0`  $\rightarrow$  **`3 * Wm1 <= 2 * Wm0`**

`shouldRecluster = (2 * Wm1 >= 3 * Wm0) || (3 * Wm1 <= 2 * Wm0)`
이 수식은 모든 지도 엔진의 `refreshWasmClusters`에서 초당 수십 번 호출되어도 부하가 거의 없습니다.

---

## 4. 4단계 스무딩 (4-Step Smoothing Process)
> [!CAUTION]
> 핀들이 한꺼번에 사라졌다가 나타나는 '깜빡임(Flickering)'은 사용자 신뢰를 떨어뜨립니다.

계산된 좌표를 화면에 그릴 때, `UIView`나 `Composable`을 한꺼번에 비우지 않고 아래의 순서를 지킵니다.

```mermaid
graph TD
    A[1. 신규 진입 Pin 추가] --> B[2. 클러스터에 먹힌 개별 Pin 삭제]
    B --> C[3. 낡은 Cluster 삭제]
    C --> D[4. 신규 Cluster 추가]
    style A fill:#e1f5fe,stroke:#01579b
    style B fill:#fff9c4,stroke:#fbc02d
    style C fill:#ffebee,stroke:#c62828
    style D fill:#e8f5e9,stroke:#2e7d32
```

1.  **Pin Add First**: 새로 생긴 단독 핀을 먼저 그립니다. (심리적 반응성 증대)
2.  **Cleanup Overlaps**: 이제 클러스터로 뭉쳐져서 필요 없어진 핀들을 조용히 뺍니다.
3.  **Old Cluster Removal**: 줌 레벨이 바뀌어 위치가 달라진 옛날 클러스터를 제거합니다.
4.  **New Cluster Addition**: 최종적으로 새로운 클러스터를 그 자리에 배치합니다.

이 순서를 지키면 핀들이 **부드럽게 합쳐지거나(Merge) 찢어지는(Split)** 듯한 시무딩 효과를 얻게 됩니다.

---

## 5. 결정론적 정렬 (Deterministic Sorting)
> [!NOTE]
> 왜 Rust(WASM) 내부에서 정렬을 수행하는가?

입력 데이터(`allItems`)의 순서가 서버 응답이나 DB 로드 순서에 따라 매번 달라질 수 있습니다. 만약 입력 순서가 달라졌다고 클러스터 모양이 바뀐다면 핀들이 "벌벌 떨리는" 현상이 발생합니다.

-   **해결**: `lib.rs`의 `cluster()` 함수는 입력받은 포인트들을 **[위도, 경도]** 순으로 즉시 정렬합니다.
-   **결과**: 입력 리스트의 순서가 어떻게 들어오든, 물리적 위치가 같다면 **항상 동일한 클러스터 결과**를 뱉어내어 시각적 안정성을 확보합니다.
