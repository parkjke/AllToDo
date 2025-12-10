# iOS 지도 핀 상호작용 및 UI 로직 리팩토링

## 🎯 목표
지도 핀의 터치 반응성을 개선하고, "할 일 목록"을 여는 트리거를 [내 위치 핀]에서 [좌측 상단 위젯]으로 변경하여 사용성을 개선합니다.

## 🛠️ 주요 변경 사항

### 1. 강력한 핀 상호작용 구현 (`AppleMapView.swift`)
- **문제점:** 배경이 투명한 지도 핀이 MapKit의 터치 감지(hit-testing) 로직과 순서(ZPosition) 문제로 인해 터치를 잘 인식하지 못함.
- **해결책:** 
    - `TouchableAnnotationView`를 구현하여 `hitTest`와 `point(inside:)`를 오버라이드.
    - 터치 영역을 **20포인트** 확장하여 손가락으로 누르기 쉽게 만듦.
    - `MapPinButton`을 최상위 상호작용 요소로 사용.
    - **중요:** `handlePinButtonTap`에서 `.userLocation` (내 위치) 터치를 가로채지 않도록 수정하여, 기본 물풍선(Callout)이 뜨도록 복원.

### 2. UI 로직 맞교환 (`ContentView.swift`)
- **목표:** 
    - **좌측 상단 위젯 (체크마크):** 누르면 **[전체 할 일 목록]**이 열려야 함.
    - **빨간 내 위치 핀 (내 정보):** 누르면 **[정보 창(물풍선)]**이 떠야 함.
- **구현:**
    - `TopLeftWidget` (`statusWidget`): 버튼 동작(`onExpandClick`)을 `{ withAnimation { showListView = true } }`로 연결.
    - `AppleMapView`: `onUserLocationTap` 콜백 제거.
    - `ContentView`의 `onUserLocationTap` 클로저: 아무 동작도 하지 않도록(no-op) 변경 (핀 자체의 선택 동작으로 물풍선이 뜨게 둠).

### 3. 정리
- `UnifiedMapModels` 및 `AppleMapView`에서 테스트용으로 만들었던 `.todoStatus`(투명/노랑/보라 핀) 관련 코드를 모두 삭제.

## ✅ 최종 동작
| 요소 | 동작 | 결과 |
| :--- | :--- | :--- |
| **좌측 상단 위젯** | 터치 | **[전체 할 일 목록]** 오버레이 열림 |
| **빨간 내 위치 핀** | 터치 | **[정보 창(물풍선)]** 표시 |
| **지도 핀 (할 일)** | 터치 | 해당 항목 선택 / 정보 창 표시 |

> [!NOTE]
> 6시간의 긴 여정 끝에, 지도의 네비게이션 기능(물풍선)과 앱의 정보 기능(목록 위젯)이 명확하게 분리된 직관적인 UI가 완성되었습니다. 고생하셨습니다! 🚀
