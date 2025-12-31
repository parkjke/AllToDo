# 📝 Create Todo (할 일 만들기/기존 기반 등록) 상세 구현 명세

본 문서는 지도의 특정 위치에 새로운 할 일을 생성하거나, 기존 핀(할 일/히스토리) 데이터를 기반으로 새로운 할 일을 신속하게 등록하는 기능의 기술적 구현 상세를 담고 있습니다.

---

## 🚀 1. 핵심 트리거 (Triggers)

### A. 신규 생성 (Map Long-press)
- **동작**: 지도상의 빈 공간을 롱터치(Long-press)합니다.
- **상태 변화**: 
    - `isCreatingTodo = true`
    - `initialTodoTitle = "할 일 만들기"`
    - `initialTodoName = ""` (초기화)
- **iOS 전용 시각적 피드백 (v1.1)**:
    - **할일핀(Creation Pin)**: 롱터치한 위치에 전용 자산(`PinTodoReady`)을 사용한 핀이 즉시 생성됩니다.
    - **Vertical Offset Centering**: 사용자가 손가락으로 가림을 방지하기 위해, 지도를 이동시켜 핀의 위치를 화면 중앙에서 **상단으로 100pt** 오프셋 정렬합니다.
- **Android 현황**: 현재 롱터치 시 텍스트 레이어만 표시되며, 전용 핀 렌더링 및 오프셋 정렬 로직은 **작업 전**입니다.
- **자동 기능**: `Geocoder`를 통해 해당 좌표의 법정동(동/읍/면) 정보를 파악하여 `defaultName`으로 설정합니다.

### B. 컨텍스트 기반 생성 (Callout Center Click)
- **동작**: 지도상의 기존 핀(할 일 또는 히스토리)을 클릭하여 나타난 **물풍선(CalloutRow)**의 중앙 정보 영역을 터치합니다.
- **상태 변화**:
    - `isCreatingTodo = true`
    - `initialTodoTitle = "할 일"` (기존 데이터 기반임을 표시)
    - `initialTodoName = [기존 핀의 이름]` (이름 자동 동기화)
- **특징**: 기존 기록을 복사하거나 유사한 할 일을 등록할 때 중복 입력을 줄여주는 최적화된 경로입니다.

---

## 🎨 2. UI/UX 레이아웃 설계

### A. 물풍선 상세 가이드 (Callout Row)
- **구조**: 단일 행(Row)으로 구성: `[좌측 아이콘] 날짜 시간 제목 [우측 휴지통]`
- **정보 표시 규칙**:
    - **날짜**: `MM.dd` 형식, 반투명 Gray 색상.
    - **시간**: `HH:mm` 형식, **검정색 굵은 글씨(Bold)**로 강조.
    - **제목**: 최대 3자까지만 노출하며, 초과 시 `제목...`으로 생략 처리.
- **인터랙션**: 중앙 영역 클릭 시 `onCreateTodo` 콜백을 호출하여 등록 화면으로 전환.

### B. 등록 레이어 (CreateTodoLayer)
- **크기**: 화면 하단 70%를 점유하는 바텀 시트 스타일.
- **비주얼 표준**:
    - 라벨/Placeholder: **Gray 7 (`#616161`)**
    - 입력 텍스트: **Gray 9 (`#212121`)**
    - 배경: White (#FFFFFF) 및 RoundedCorner (24dp)
- **사용자 경험**:
    - **자동 포커스 (Auto-focus)**: 레이어가 열림과 동시에 할 일 이름 입력 칸에 커서가 위치하고 키보드가 활성화됩니다.
    - **상단 여백**: 입력창 확장 시 상단에 **24dp**의 여백을 주어 상태바 침범을 방지하고 시각적 안정감을 유지합니다.

---

## ⚙️ 3. 지도 뷰포트 및 제어 로직 (Map Viewport Control)

### A. 레이어 오픈 시 (Padding & Auto-center)
- **오프셋 적용**: 등록 레이어가 하단 70%를 가리므로, 맵 SDK의 `Padding` 기능을 사용하여 실제 지도 동작 영역을 상단 30%로 제한합니다.
- **고정 위경도**: 레이어가 열리는 동안 지도를 해당 핀 위치로 자동 이동시켜 사용자 시선이 핀에 고정되도록 합니다.

### B. 레이어 종료 시 (Re-centering)
- **여백 제거**: `isCreatingTodo`가 `false`가 되면 맵의 하단 패딩이 0으로 초기화됩니다.
- **중앙 재정렬 (Re-center)**: 패딩 제거 직후, 사용자가 보고 있던 핀 좌표를 지도의 **물리적 정중앙**으로 부드럽게 재이동시킵니다.
    - **지연 처리 (Delay)**: 패딩 제거 애니메이션과 겹치지 않도록 **300ms**의 미세한 지연 후 카메라 이동 명령을 수행합니다.
- **플랫폼별 대응**:
    - **Naver**: `CameraUpdate.scrollTo(latLng).animate(Easing)`
    - **Kakao**: `CameraUpdateFactory.newCenterPosition(latLng)`
    - **Google**: `CameraUpdateFactory.newLatLng(latLng)` (Coroutine scope 내부)

---

## 💾 4. 데이터 저장 및 추천 시스템 (Data & Business Logic)

### A. 통합 히스토리 인프라
- **테이블 구조**: 
    - `TodoItem`: 할 일(Type `"10"`)과 히스토리(Type `"00"`)를 단일 테이블에서 관리.
    - `PathItem`: 상세 경로 데이터(좌표 목록)를 `todo_id` 외래키로 연결하여 관리.
- **무결성 보장**: 앱 강제 종료 대응을 위해 `NonCancellable` 코루틴 스코프에서 `todo_id` UUID 선발행 후 **Batch Insert**로 저장합니다.

### B. 실시간 추천 (Recommendation)
- **데이터 로드**: `TodoViewModel.loadTodos` 시 사용 빈도가 높은 이름을 추출하여 `recentNames`, `recentMemos` StateFlow로 UI에 실시간 전달합니다.
- **표시 필터**: 지도의 핀은 **현재 시간 기준 ±24시간** 이내의 데이터만 노출하여 인지 부하를 줄입니다.
