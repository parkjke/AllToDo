# 🎈 Water Balloon (Callout) Logic & Design

지도의 핀을 클릭했을 때 나타나는 **"물풍선(Callout)"** 시스템은 AllToDo 앱의 핵심 인터랙션 요소입니다. iOS의 디자인 철학을 안드로이드에 완벽하게 이식하여, 모든 지도 제공자(Naver, Kakao, Google)에서 일관되고 프리미엄한 경험을 제공합니다.

---

## 🎨 1. Design & Aesthetics

물풍선은 단순한 정보 창을 넘어, 앱의 브랜드 아이덴티티를 나타내는 시각적 요소입니다.

<img src="water_balloon_callout_mockup.png" width="400" alt="물풍선 디자인 가이드">

### 🟢 Color & Material
- **Background**: `AllToDoGreen` (#28CD41)
- **Transparency**: **80% Alpha** (Glassmorphism 효과)
- **Border**: White with 20% opacity (Subtle Divider)
- **Shadow**: Elevation 8dp (지도 면으로부터 떠 있는 느낌 부여)

### 📏 Sizing & Layout
- **Bubble Width**: Fixed 260dp
- **Row Height**: 
    - Small (0): 38dp
    - Medium (1): 42dp (기본값)
    - Large (2): 52dp
- **Corner Radius**: 12dp (부드러운 라운딩)
- **Tail (Triangle)**: Width 20dp, Height 10dp (핀의 머리 부분을 정확히 가리킴)

---

## 🛠️ 2. Core Resources

물풍선 내에서 사용되는 주요 아이콘 및 리소스입니다.

| Resource | Icon | Description |
| :--- | :---: | :--- |
| **Close** | `Icons.Default.Close` | 물풍선을 닫는 중앙 상단 버튼 |
| **History Path** | `Icons.Default.Map` | 히스토리 기록의 상세 경로를 지도에 표시 |
| **Delete** | `Icons.Default.Delete` | 할 일 또는 히스토리 기록 삭제 |
| **User Label** | `Icons.Default.Person` | 현재 위치 핀 표시 아이콘 |

---

## 🧠 3. Logic & Positioning

물풍선은 지도의 내부 Callout 기능을 사용하지 않고, Compose Overlay 방식을 채택하여 자유로운 커스터마이징이 가능합니다.

### 📍 Coordinate Transformation (Projection)
핀이 클릭되면 각 지도 SDK의 Projection 기능을 통해 위경도(`LatLng`)를 화면상의 절대 좌표(`XY Offset`)로 변환합니다.
- **Base Logic**: `map.projection.toScreenLocation(latLng)` -> `MainScreen`에 `Offset` 전달.

### 🎯 Auto-Centering
사용자가 핀을 클릭했을 때 물풍선이 화면 밖으로 나가는 것을 방지하고 시각적 안정감을 주기 위해 **자동 중앙 정렬** 애니메이션을 수행합니다.
1. 핀 탭 감지
2. 지도의 카메라를 해당 위경도로 부드럽게 이동 (Duration: 300~800ms)
3. 이동과 거의 동시에 변환된 화면 좌표를 기준으로 물풍선 페이드 온(Fade-In).

### 📐 Positioning Strategy
물풍선은 `ZStack` 최상단에 배치되며, `IntOffset`을 통해 다음과 같이 계산된 위치에 놓입니다:
```kotlin
x = tapX - (BubbleWidth / 2)
y = tapY - BubbleHeight - TailHeight - MarkerOffset
```
- **MarkerOffset**: 각 지도 SDK(구글, 네이버, 카카오)에서 핀의 실제 위치와 계산된 좌표 사이의 미세한 오차를 보정하기 위해 정의된 상수값입니다.

---

## ♻️ 4. Data Sync & Interaction
- **Max Items**: `maxPopupItems` 설정에 따라 리스트 개수가 제한되며, 초과 시 스크롤이 가능해집니다.
- **Font Scale**: `popupFontSize` 설정에 따라 텍스트 크기가 실시간으로 변동됩니다.
- **Real-time Deletion**: 물풍선 내에서 삭제 버튼을 누르면 DB 반영과 동시에 리스트에서 즉시 사라지는 애니메이션이 동기화됩니다.
- **Contextual Action (New)**: 물풍선의 정보를 담고 있는 중앙 영역은 `clickable` 속성을 가지며, 클릭 시 `onCreateTodo` 콜백을 호출하여 현재 핀의 정보를 `CreateTodoLayer`로 전달합니다.

---

> **Tip**: 물풍선은 지도의 UI와 완전히 분리된 Compose 레이어이므로, 지도를 provider 간에 전환하더라도 상태가 유지되며 일관된 스타일을 유지합니다.
