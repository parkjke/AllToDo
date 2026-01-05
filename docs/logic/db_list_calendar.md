# 할 일 목록 및 캘린더 시스템 명세 (Todo List & Calendar System)

AllToDo의 할 일 목록 레이어와 캘린더 시스템은 사용자의 과거 기록과 미래 일정을 시각적으로 통합하여 관리하는 핵심 인터페이스입니다.

## 1. 데이터 파이프라인 (Data Pipeline)

### 1.1. 데이터 소스 (Data Source)
- **TodoViewModel**: 안드로이드 앱의 상태 관리 주체로서, 로컬 DB(Room)와 서버 데이터를 통합한 `todoItems` StateFlow를 제공합니다.
- **UnifiedItem**: 캘린더와 목록에서는 `UnifiedItem.Todo` 피드와 `UnifiedItem.History` 피드를 필터링하여 사용합니다.

### 1.2. 필터링 정책 (Filtering Policy)
- **날짜 필터링**: 선택된 날짜(`LocalDate`)를 기준으로 `begin_time` 또는 `created_at`이 일치하는 항목을 추출합니다.
- **유형 필터링**: 서버(Blue), 할 일(Green), 히스토리(Red) 필터 버튼 상태에 따라 동적으로 목록을 갱신합니다.
- **기술 데이터 배제**: `CURRENT_LOCATION` (todo_id: 0) 항목은 통계 및 목록 렌더링에서 철저히 제외하여 순수 비즈니스 데이터만 노출합니다.

## 2. 캘린더 시스템 (Calendar System)

### 2.1. 핀 통계 (Pin Statistics)
- 각 날짜 셀(`DayCell`)에는 당일 발생한 항목의 색상별 개수가 표시됩니다.
- 데이터가 있는 색상만 표시하며, 최대 3개의 통계 라인을 유지하여 그리드 정렬을 보존합니다.

### 2.2. 인터랙션 (Interaction)
- **날짜 선택**: 터치 시 해당 날짜의 할 일 목록(`TodoSummaryArea`)이 즉시 로드됩니다.
- **월 이동**: 연/월 네비게이션을 통해 과거 기록과 미래 계획을 자유롭게 탐색합니다.

## 3. 할 일 목록 레이어 (Todo List Layer)

### 3.1. 요약 모드 (Summary Mode in Calendar)
- 캘린더 하단에 배치되며, `TodoItemCard` 형식을 계승하여 상세 정보를 제공합니다.
- [지도] 아이콘: 클릭 시 해당 항목의 경로를 보여주는 `PathViewer`로 이동합니다.
- [이름] 텍스트: 클릭 시 할 일을 수정할 수 있는 `CreateTodoLayer`로 이동합니다.

### 3.2. 화면 복원 로직 (Navigation Recovery)
상세 보기나 수정 후 사용자의 맥락을 유지하기 위한 상태 장치를 가집 가지:
- `shouldRestoreCalendar`: 수정 완료/취소 시 자동으로 캘린더를 다시 오픈합니다.
- `shouldRestoreList`: 캘린더 이전에 목록 레이어가 열려있었다면 이를 함께 복원합니다.

## 4. 디자인 표준
- **폰트**: 날짜 14sp, 요일 15sp, 통계 10sp (Bold).
- **색상**: `AppColors.Calendar` 및 `AppColors.TodoList` (Gray8 표준 기반).
- **테마**: 지도 엔진의 라이트/다크 정책에 연동되어 최적의 가독성을 제공합니다.
