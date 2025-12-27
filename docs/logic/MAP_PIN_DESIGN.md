# 맵 핀 디자인 명세서 (Map Pin Design Specifications)

## 1. 디자인 개요 (Design Overview)
*   **기본 형태**: 방패 모양 (Shield/Badge) - `Icons/map-pin-gemini.svg` 기반.
*   **디자인 목표**: Android (Google, Naver, Kakao) 및 iOS (Apple, Google, Naver, Kakao) 간 통일된 시각적 언어 구축.
*   **에셋 타입**: SVG (벡터) -> **런타임 최적화 비트맵 캐시 (Runtime Optimized Bitmap Cache)**.

## 2. 핀 종류 및 색상 정의 (Pin Types & Color Logic)
**사용자 정의에 따른 절대 기준 (Absolute Standard)**

### A. 빨강 (Red) - 위치 및 자취
*   **대상**: 
    1.  **현재 위치 (Current Location)**: 사용자의 실시간 위치.
    2.  **지나간 위치 (History)**: 앱 시작부터 종료 시까지 기록된 경로/위치 로그.
*   **아이콘**: 방패 모양 핀 + 내부 마크 (별, 사람 등 구분).

### B. 녹색 (Green) - 사용자가 생성한 할 일 (Local Todo)
*   **대상**: 사용자가 직접 생성한 모든 할 일.
*   **상태 구분 (내부 마크로 구분)**:
    1.  **계획 (Plan)**
    2.  **완료 (Done)**
    3.  **취소 (Cancel)**
    4.  **못함 (Fail)**
*   **특이사항**: 위치 정보가 있을 수도 있고 없을 수도 있음 (없으면 지도 표시 불가).
*   **주의**: **완료된 투두도 녹색**이어야 함 (파란색 아님).

### C. 청색 (Blue) - 서버로부터 전달받은 할 일 (Server Todo / Doit)
*   **대상**: 서버(상대방/시스템)에서 받은 지시 사항.
*   **상태 구분 (내부 마크로 구분)**:
    1.  **수신 (Receive)**
    2.  **완료 (Done)**: 서버 투두의 완료 상태.
    3.  **진행중 (In Progress)**
    4.  **거부 (Reject)**
    5.  **수락 (Accept)**

## 3. 클러스터링 로직 (Cluster Priority)
핀이 뭉쳐 있을 때(Cluster) 대표 아이콘 표시 우선순위는 **색상(카테고리) 우선**입니다.

1.  **1순위: 내 위치 포함 시** -> **빨강 (현재 위치 아이콘)**.
2.  **2순위: 빨강 (History)** -> 그룹 내 History가 과반수 이상이거나 중요도가 높을 때.
3.  **3순위: 청색 (Server Todo)** -> 포함되어 있을 경우 표시 (알림 성격).
4.  **4순위: 녹색 (Local Todo)** -> 기본값.

## 4. 구현 참고사항
*   **iOS/Android 공통**: 핀 생성 시 `Category` (Red/Green/Blue)를 먼저 결정하고, 그 위의 `Status Icon` (Check, X, Star...)을 덧그리는 방식 권장.
*   **기존 오류 수정**: "완료된 투두는 파란색"이라는 잘못된 로직을 "녹색"으로 수정해야 함. 파란색은 오직 **서버 투두**에만 적용.

## 5. 성능 최적화 및 캐싱 시스템 (Pin Caching & Parity)
[2025-12-14 Update] 성능 향상과 핀 선명도 유지를 위해 **PinImageManager** 시스템이 도입되었습니다.

### 5.1. 캐싱 전략 (Caching Strategy)
*   **최초 1회 생성**: 앱 시작 시 SVG 리소스(Vector Drawable)를 비트맵으로 변환하여 내부 저장소에 파일로 캐싱합니다.
*   **메모리 캐시**: 자주 사용하는 핀 이미지는 `LruCache` 대신 `BitmapCache` 메모리에 상주시켜 G/C 오버헤드를 줄입니다.
*   **클러스터 핀 최적화**: 매번 비트맵을 생성하지 않고, 캐싱된 **기본 핀 이미지(Base Bitmap)** 위에 숫자(Badge)만 덧그리는 방식으로 렌더링 속도를 획기적으로 개선했습니다.

### 5.2. 멀티 디바이스 대응 (Density-Aware)
*   **동적 해상도 대응**: 각 기기의 화면 밀도(Density)에 맞춰 최적화된 해상도로 비트맵을 생성합니다.
*   **파일명 규칙**: `pin_name_v1_d{density}.png` (예: `pin_current_v1_d3.0.png`)
*   **크기 표준화 (Size Standardization)**:
    *   **Visual Pin Size**: **40dp x 50dp** (사용자가 보는 핀 크기).
    *   **Canvas Size**: **50dp x 60dp** (뱃지 Overhang을 위한 여백 포함).
    *   **Anchor Point**: **(0.4, 1.0)** - 캔버스 확장에 따른 중심점 보정 (핀 끝이 정확한 좌표를 가리키도록).

### 5.3. 무결성 검증 및 스케일 관리 (Integrity & Scale Control)
*   **안전한 저장**: 파일 저장 시 단순 이미지가 아닌, 커스텀 헤더(**`ALLTODO_V7`**)를 포함하여 저장합니다.
*   **스케일 보존 로드**: 파일을 불러올 때 기기 고유의 스케일(3.0x 등) 정보를 수동으로 주입하여, 고해상도 픽셀 데이터가 비정상적으로 크게 표시(1 Pixel = 1 Point 왜곡)되는 현상을 원천 차단합니다.
*   **검증 로직**: 헤더가 일치하지 않거나 버전이 다르면 즉시 캐시를 폐기하고 원본 SVG로부터 고해상도 비트맵을 재생성합니다.

## 6. 최신 디자인 변경 사항 (Latest Design Updates - 2025-12-14)
*   **뱃지 스타일 (Badge Style)**: 핀 내부에 갇혀있던 숫자를 **핀 우측 상단으로 튀어나오게 (Overhang)** 변경하여 가독성 및 디자인 입체감 향상.
*   **경로 보기 (Path Detail View)**:
    *   **도착지 (End Marker)**: **History Pin (Red Clock)** 사용으로 "목적지/기록" 의미 강조.
    *   **출발지 (Start Marker)**: **Red Dot**으로 심플하게 표시.
    *   **경로선 (Polyline)**: 불필요한 중간 마커 제거, **Solid Red Line (Width 20)** 만 깔끔하게 표시.
*   **UI 통일 (UI Consistency)**:
    *   팝업 닫기 버튼 [X]을 우측 메인 컨트롤 버튼과 동일한 **Green Rounded Square** 스타일로 통일.
    *   Android/iOS 간 핀 렌더링 로직(Canvas, Anchor) 100% 일치화.

## 7. 지도 플랫폼별 핀 스케일링 (Map Provider Scaling - 2025-12-25)
지도 제공자(SDK)마다의 시각적 밀도 차이를 보정하기 위해 플랫폼별 스케일링 값을 적용합니다.

### 7.1. iOS 스케일링 및 뱃지 정밀 조정 (2025-12-28)
*   **핀 규격 (Standard Size)**: **40pt x 50pt** (Apple Map 기준)
*   **캔버스 규격 (Canvas Size)**: **50pt x 60pt** (뱃지 Overhang 10pt 포함)
*   **뱃지 위치 조정**: 사용자의 시각적 안정감 선호도에 따라 뱃지 중심을 기존보다 **3pt 아래**로 내려 배치 (`badgeOverhang + 1.0`). 상단 잘림 현상을 해결함.
*   **엔진별 스케일 및 뱃지 크기 동기화**:
    | 지도 엔진 | 핀 스케일 | 뱃지 지름 | 앵커 포인트 (Anchor) | 비고 |
    | :--- | :--- | :--- | :--- | :--- |
    | **애플 맵** | **1.0x** | **20pt** | `centerOffset` **(-5, 30)** | 50x60 기준 |
    | **구글 맵** | **1.0x** | **20pt** | `groundAnchor` **(0.4, 1.0)** | 20/50 = 0.4 |
    | **네이버 맵** | **0.9x** | **18pt** | `anchorPoint` **(18/46, 1.0)** | 36x45 + 10pt 여백 |
    | **카카오 맵** | **0.7x** | **14pt** | `anchorPoint` **(14/38, 1.0)** | 28x35 + 10pt 여백 |

### 7.2. Android 스케일링 기준
*   **기본 크기**: **40dp x 50dp** (모든 지도 제공자 공통 적용).
*   **뱃지 규격**: iOS와 동일한 **10pt(지름 20pt)** 뱃지 규격 및 우상단 오버행(Overhang) 적용.
*   **앵커 (Anchor)**: **(0.4, 1.0)** - 50x60 전체 캔버스 내 핀 끝점 정렬.
