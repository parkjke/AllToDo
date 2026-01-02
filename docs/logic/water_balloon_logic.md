# 🎈 Water Balloon (Callout) Logic & Design

지도의 핀을 클릭했을 때 나타나는 **"물풍선(Callout)"** 시스템은 AllToDo 앱의 핵심 인터랙션 요소입니다. iOS의 디자인 철학을 안드로이드에 완벽하게 이식하여, 모든 지도 제공자(Naver, Kakao, Google)에서 일관되고 프리미엄한 경험을 제공합니다.

---

## 🎨 1. Design & Aesthetics

물풍선은 단순한 정보 창을 넘어, 앱의 브랜드 아이덴티티를 나타내는 시각적 요소입니다.

<img src="water_balloon_callout_mockup.png" style="width: 300px;" alt="물풍선 디자인 가이드">

### 🟢 Color & Material
- **Background (Light/Kakao/Naver)**: `AllToDoGreen` (#28CD41) - **80% Alpha** (Glassmorphism)
- **Background (Google Dark)**: #1B8A2B - **85% Alpha** (다크 테마 지도 조화도 최적화)
- **Border**: White with 20% opacity (Subtle Divider)
- **Shadow**: Elevation 8dp (지도 면으로부터 떠 있는 느낌 부여)


### 📏 Sizing & Layout
- **Bubble Width**: Fixed 260dp
- **Header Height**: 44dp (iOS Style Close area)
- **Row Height**: 
    - Small (0): **40dp**
    - Medium (1): **48dp** (기본값)
    - Large (2): **56dp**
- **Corner Radius**: 12dp (부드러운 라운딩)
- **Tail (Triangle)**: Width 20dp, Height 10dp (핀의 머리 부분을 정확히 가리킴)

---

## 🛠️ 2. Core Resources

물풍선 내에서 사용되는 주요 아이콘 및 리소스입니다.

| Resource | Icon | Description |
| :--- | :---: | :--- |
| **Close** | `Icons.Default.Close` | 물풍선을 닫는 중앙 상단 버튼 |
| **History Path** | `Icons.Default.Map` | 경로 기록이 **2개 이상**(`no_of_path >= 2`)일 때만 활성화 |
| **Delete** | `Icons.Default.Delete` | 할 일 또는 히스토리 기록 삭제 |
| **User Label** | `Icons.Default.Person` | 현재 위치 핀 표시 아이콘 |

---

## 🧠 3. Logic & Positioning

물풍선의 위치와 지도의 카메라 이동은 고도로 계산된 **"53dp Offset"** 전략을 따릅니다.

### 🎯 Center-Focused Positioning (Requirement 3, 4)
지도의 핀을 클릭하면 카메라는 단순히 핀을 중앙에 놓는 것이 아니라, 다음과 같은 정밀 오프셋을 적용합니다:
1.  **핀 정렬**: 핀의 끝점(Bottom/Tip)을 화면 정중앙 기준 **+53dp(Y축)** 지점에 위치시킵니다.
2.  **결과**: 
    - 핀의 높이가 약 50dp이므로, **핀의 머리(Top)는 화면 정중앙 3dp(4dp) 하단**에 옵니다.
    - **물풍선의 꼬리(Bottom/Tail)**는 정확히 **화면 정중앙**에 걸리게 되어 최적의 시각적 안정감을 제공합니다.

### 📍 Camera & View Dimension
- **View-Based**: `displayMetrics`가 아닌 지도의 **실제 가용 픽셀 크기(`width`, `height`)**를 기반으로 화면 중앙 좌표를 계산하여 오차가 없습니다.
- **Relative Move**: 클릭된 핀의 현재 화면 좌표와 타겟 좌표(+53dp) 사이의 델타값을 이용해 카메라를 부드럽게 이동합니다.

---

## ♻️ 4. Data Sync & Interaction
- **Scrolling Availability**: `maxPopupItems`는 리스트의 기본 노출 높이를 결정하지만, 실제 목록의 `take` 제한은 없습니다. 12개 이상의 아이템이 있어도 물풍선 내부에서 **자유로운 스크롤**이 가능합니다.
- **Theme Policy**: 
    - **Google Map**: 시스템 다크/라이트 테마에 따라 배경색과 아이콘 명도가 동적으로 변합니다.
    - **Naver/Kakao Map**: 엔진 특성에 맞춰 앱 시스템 설정과 관계없이 항상 **라이트 테마**를 고정(`forceLightMode = true`)합니다.
- **Font Scale**: `popupFontSize` 설정에 따라 텍스트 크기와 **행 높이(Row Height)**가 위 기준표에 맞춰 실시간으로 반응합니다.
- **Real-time Deletion**: 물풍선 내에서 삭제 버튼을 누르면 DB 반영과 동시에 리스트에서 즉시 사라지는 애니메이션이 동기화됩니다.
- **Contextual Action (New)**: 물풍선의 정보를 담고 있는 중앙 영역은 `clickable` 속성을 가지며, 클릭 시 `onCreateTodo` 콜백을 호출하여 현재 핀의 정보를 `CreateTodoLayer`로 전달합니다.

---

## ⚠️ 5. iOS Kakao Map Interaction Troubleshooting (v1.1)

iOS 카카오 맵 SDK v2 환경에서 핀(POI) 터치 이벤트가 유실되거나 지연되는 현상을 해결하기 위한 기술적 표준입니다.

### 🧩 Objective-C Bridge Signature
카카오 SDK는 내부적으로 Objective-C 델리게이트 시스템을 사용하므로, Swift에서 정확한 시그니처 매칭이 필수적입니다.
- **Verified Working (Case 1)**: `poiDidTapped(kakaoMap:layerID:poiID:position:)` (Labelled Version)
- **주의**: 언더바(`_`)를 포함한 시그니처나 포지션이 누락된 시그니처는 시스템 환경에 따라 이벤트가 전달되지 않을 수 있으므로, 검증된 시그니처를 최상단에 배치합니다.

### 🚫 Gesture Interference Elimination
커스텀 `UITapGestureRecognizer`가 `KMViewContainer`에 추가될 경우, 네이티브 엔진의 터치 이벤트 처리를 가로채거나 지연시킬 수 있습니다.
- **해결책**: 핀 터치가 필수인 화면에서는 `KMViewContainer`에 직접적인 `UITapGestureRecognizer`를 추가하지 않거나, `cancelsTouchesInView = false`를 매우 신중하게 사용해야 합니다. 
- AllToDo iOS v1.1에서는 모든 커스텀 제스처를 제거하여 카카오 맵 순정 터치 엔진의 반응성을 100% 회복했습니다.

###  LAYER zOrder & Hit-Testing
- **zOrder**: 핀 레이어의 `zOrder`가 너무 높으면(예: 10000) 맵 엔진이 이를 무시할 수 있고, 너무 낮으면 지형 아래로 깔립니다. **2000**이 시각적 노출과 터치 정확도의 최적점입니다.
- **CompetitionType**: 이벤트 수신을 위해 반드시 `LabelLayerOptions`의 `competitionType`을 `.same`으로 설정해야 합니다.

---

> **Tip**: 물풍선은 지도의 UI와 완전히 분리된 Compose 레이어이므로, 지도를 provider 간에 전환하더라도 상태가 유지되며 일관된 스타일을 유지합니다.
