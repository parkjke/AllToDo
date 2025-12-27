# 지도 핀 스팩 정리 (설계중)

사용자 중심의 핀 분류 체계 및 맵 에셋 명명 규칙을 정의합니다. (v1.23.0)

## 1. 핀 배경 색상 (Pin Background Customizing)
타입 코드의 10단위 숫자에 따라 배경 색상이 결정됩니다.

| 코드 앞자리 | 배경 색상 | 의미 |
| :--- | :--- | :--- |
| **0x** | **🔴 빨강 (Red)** | 히스토리 및 현재 위치 |
| **1x** | **🟢 녹색 (Green)** | 로컬 할 일 (내 계획) |
| **2x** | **🔵 파랑 (Blue)** | 지시받은 할 일 (서버/지시) |

## 2. 상세 타입 및 에셋 명칭 (Pin Type & Asset Mapping)

### 🔴 0x: 히스토리 계열 (Red Background)
| 타입 | 에셋 명칭 (Asset Name) | 역할 및 설명 |
| :--- | :--- | :--- |
| **00** | `history_current_location` | 사용자 현재 위치 (GPS 앵커) |
| **01** | `history_no_path` | 경로 정보가 없는 히스토리 기록 |
| **02** | `history_exist_path` | 연결된 경로(Path)가 있는 히스토리 |
| **03** | `history_stored` | 영구 저장된 히스토리/즐겨찾기 장소 |

### 🟢 1x: 로컬 할 일 계열 (Green Background)
| 타입 | 에셋 명칭 (Asset Name) | 역할 및 설명 |
| :--- | :--- | :--- |
| **10** | `todo_planned` | 오늘 또는 미래에 계획된 할 일 |
| **11** | `todo_missed` | 계획되었으나 실행하지 못한(기한 초과) 할 일 |
| **12** | `todo_completed` | 정상적으로 완수한 로컬 할 일 |

### 🔵 2x: 지시 할 일 계열 (Blue Background)
| 타입 | 에셋 명칭 (Asset Name) | 역할 및 설명 |
| :--- | :--- | :--- |
| **20** | `instruction_received` | 새로운 지시가 도착한 상태 (지시받은 할일) |
| **21** | `instruction_rejected` | 사용자가 거절한 지시 (거부한 할일) |
| **22** | `instruction_accepted` | 사용자가 수락한 지시 (수락한 할일) |
| **23** | `instruction_in_progress` | 현재 진행 중인 지시 (진행중인 할일) |
| **24** | `instruction_completed` | 완수한 지시 사항 (완수한 할일) |

## 3. 핵심 규칙 및 설계 가이드
- **Master Key**: DB의 `ToDoItem.type` (String) 필드가 핀의 시각적 정체성을 결정하는 유일한 근거임.
- **시각적 언어**: 핀 배경(색상)은 데이터의 '출처'(`type`의 첫 번째 자리)를 나타내고, 내부 마크는 '상태/역할'(`type`의 두 번째 자리)을 나타냄.
- **에셋 우선 원칙**: 소스 코드의 `imageName`은 위 테이블의 명칭과 100% 일치해야 하며, 각 플랫폼(iOS, Android, Web)의 리소스 관리자는 이 이름을 키로 에셋을 로드함.
- **클러스터 우선순위**: 여러 타입이 뭉칠 경우 **Blue(2x) > Green(1x) > Red(0x)** 순으로 대표 아이콘 결정.

## 4. 플랫폼 통합 명명 및 매핑 규칙 (Unified Naming & Mapping)

각 플랫폼(iOS, Android, Web)의 리소스 명칭 불일치를 해결하고, DB `type` 코드 변화에 유연하게 대응하기 위해 **Snake Case 기반의 통합 에셋 키(Unified Asset Key)**를 사용합니다.

### 🔄 매핑 파이프라인 (Mapping Pipeline)
1.  **DB**: `ToDoItem.type` (e.g., "10")
2.  **Logic (Mapping Table)**: `10` → `todo_planned` (Unified Key)
3.  **App**: 
    *   **iOS**: `UIImage(named: "todo_planned")`
    *   **Android**: `R.drawable.todo_planned`
    *   **Web**: `<img src="/pins/todo_planned.svg" />`

### ✅ 명칭 통일 원칙
- **Snake Case 사용**: DB 필드 및 웹 표준에 가장 친숙한 `snake_case`로 통일합니다. (예: `PinHistory` → `history_no_path`)
- **접두어 기반 분류**: `history_`, `todo_`, `instruction_` 접두어를 사용하여 관리 효율을 높입니다.

## 5. 타입 치환 테이블 (Type Substitution Table) - 설계안

이 테이블은 코드 내에서 `Switch` 문이나 `Dictionary/Map` 형태로 구현되는 'Source of Truth'입니다.

| Type Code | Unified Asset Key | iOS Legacy (Reference) | Android Legacy (Reference) |
| :--- | :--- | :--- | :--- |
| **00** | `history_current_location` | `PinCurrent` | `pin_current_v1` |
| **01** | `history_no_path` | `PinHistory` | `pin_history_v1` |
| **10** | `todo_planned` | `PinTodoReady` | `pin_todo_ready_v1` |
| **12** | `todo_completed` | `PinTodoDone` | `pin_todo_done_v1` |
| **20** | `instruction_received` | `PinReceiveReady` | `pin_receive_ready_v1` |

> [!NOTE]
> 이 방식을 사용하면 향후 핀 디자인이 바뀌어도 에셋 파일명만 새로 정의된 규칙으로 교체하면 되며, 비즈니스 로직(DB 처리부)은 전혀 건드릴 필요가 없습니다.

## 6. 핀 렌더링 구조 개선 설계 (Pin Rendering Structure Improvement)

기존의 결합형 에셋(배경+마크)을 분리하여 **런타임 합성(Layer Compositing)** 방식으로 전환합니다. (2025-12-28 설계)

### 6.1. 구조 변경 (Structure Change)
*   **AS-IS**: `PinTodoReady.svg` (배경과 체크 마크가 한 파일에 있음)
*   **TO-BE**: `pin_shp_green.svg` (배경) + `mark_check.svg` (마크) -> **Runtime Combine**

### 6.2. 배경(Shield) 및 마크(Mark) 정의
1.  **배경 (Shield)**: 3가지 색상의 그라데이션 방패.
    *   `pin_shp_red.svg` (0x 계열)
    *   `pin_shp_green.svg` (1x 계열)
    *   `pin_shp_blue.svg` (2x 계열)
2.  **마크 (Mark)**: 투명 배경의 단색(White/Colored) 아이콘.
    *   `mark_star.svg`, `mark_check.svg`, `mark_x.svg` 등.

### 6.3. 배치 규격 (Layout Specification)
사용자 테스트 결과를 기반으로 한 마크의 최대 배치 영역입니다.

*   **캔버스 크기**: 100 x 125
*   **안전 영역 (Safe Area)**:
    *   **중심점 (Center)**: **(50, 45)**
    *   **최대 크기 (Max Size)**: **50 x 45** (가로 50, 세로 45)
    *   **권장 여백**: 방패의 굴곡을 고려하여 중앙 배치.

### 6.4. 구현 레퍼런스 (Implementation Reference)
사용자가 검증한 마크 배치 및 스케일링 예시 코드입니다.

```xml
<!-- Mark Placement Example -->
<g transform="translate(50, 45) scale(0.6) translate(-50, -50)">
    <!-- Scale 0.6: Smaller icon inside the safe area -->
    <path d="M55,10 L25,60 L50,60 L40,90 L75,40 L50,40 Z" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="2" stroke-linejoin="round"/>
</g>

<!-- Full Size Usage Example -->
<g transform="translate(50, 45) scale(1.0) translate(-25, -25)">
    <!-- Scale 1.0: Filling the max safe area (50x45) -->
</g>
```

### 6.5. 마크 에셋 제작 (Asset Creation Strategy)
마크(Icon) 에셋은 디자이너 없이도 **AI 에이전트와 협업하여 즉시 제작**할 수 있습니다.

*   **제작 방식**: 사용자가 레퍼런스 이미지나 아이디어를 제공하면, 에이전트가 이를 분석하여 최적화된 SVG Path 코드로 변환합니다.
*   **규격 준수**: 생성된 코드는 앞서 정의한 **Safe Area (50x45)** 및 **Center (50, 45)** 규격에 자동으로 맞춰집니다.
*   **협업 가능**: 복잡한 디자인 툴 없이 대화형으로 아이콘 모양을 다듬고 핀에 적용해 볼 수 있습니다.

## 7. Todo 기반 핀 표시 및 필터링 규칙 (Todo-based Pin Display Logic)
사용자 정의에 따른 핀 노출 조건과 '현재 위치'의 Todo 통합 전략입니다.

### 7.1. 핀 표시 필터링 규칙
다음 조건에 부합하지 않는 핀은 지도 및 클러스터링 대상에서 제외합니다.

1.  **시간 제한 (Time Window)**: `date_time` 기준 **±24시간** 이내의 항목만 표시합니다.
2.  **데이터 유효성**: `no_of_path`가 **0**인 항목은 위치 정보가 없는 것으로 간주하여 표시하지 않습니다.
3.  **지리적 제한 (Geo-Fencing)**:
    *   **한국(Korea) 영역 밖**의 핀은 카카오/네이버 지도에서 표시하지 않습니다. (구글/애플 지도는 허용)
    *   **거리 제한**: 현재 내 위치로부터 반경 **500km**를 초과하는 핀은 표시하지 않습니다. (태평양 줌아웃 방지)

### 7.2. WASM 클러스터링 영향 분석
*   **필터링 시점**: 위 규칙들은 WASM엔진에 데이터를 주입하기 **전 단계(Pre-filtering)**에서 적용해야 합니다.
*   **성능 영향**: 사전 필터링을 통해 WASM으로 전달되는 좌표 개수가 줄어들므로, 오히려 **클러스터링 연산 속도는 향상**됩니다. 전혀 방해되지 않습니다.

### 7.3. 현재 위치의 Todo 통합 전략 (Current Location as Todo)
현재 위치를 별도의 객체가 아닌 `TodoItem`으로 추상화하여 관리합니다.

*   **통합 구조**:
    *   `todo_id`: 임시 UUID 발급 (앱 실행 시 생성).
    *   `todo_name`: **"현재 위치"**.
    *   `type`: **"00"** (히스토리/위치 계열).
    *   `no_of_path`: **0에서 시작하여 이동 시 증가**. (RDP 통과한 유효 좌표 수)
    *   `date_time`: 앱 시작 시간 + 24시간 (항상 표시 되도록 미래 시간 설정).
    *   `begin_time`: 앱 시작 시간.
    *   `end_time`: 앱 종료 시간 (종료 시 확정 및 DB 저장).
*   **저장 로직**:
    *   앱 실행 중에는 메모리 상에서만 갱신되다가, **앱 종료(Deep Sleep/Terminate)** 시점에 새로운 히스토리(`type: 01`)로 변환되어 DB에 영구 저장됩니다.
