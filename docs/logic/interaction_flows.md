# AllToDo Interaction Flow Definitions (State Diagram)

앱의 '상태' 전환을 명확히 규합하여 지시 없는 수정을 제지하는 설계도로 활용합니다.

## 기본 지도
"핀 선택 -> 물풍선 -> 할 일/경로 진입"으로 이어지는 앱의 각 상태와 전환 조건(Trigger)을 정의합니다.

```mermaid
flowchart LR
    %% Nodes
    IdleMap(["(기본 지도 (IdleMap))"])

    subgraph RightSide ["상태 전환 (Windows/Popups)"]
        direction TB
        TodoEdit["( 할 일 편집 (TodoDetail) )"]
        CalloutShown["(( 물풍선 노출 (CalloutShown) ))"]
        PathViewing["( 경로 보기 (PathViewing) )"]
    end
    
    %% Transitions
    Start(( )) --> IdleMap
    
    IdleMap -- "[핀 터치 (Tap Pin)]" --> CalloutShown
    IdleMap -- "[지도 빈 곳 롱터치]" --> TodoEdit
    
    CalloutShown -- "['지도/경로' 터치]" --> PathViewing
    CalloutShown -- "['할 일 이름' 터치]" --> TodoEdit
    
    PathViewing -- "[닫기] 터치" --> CalloutShown
    
    TodoEdit -- "[닫기] (물풍선 진입 시)" --> CalloutShown
    TodoEdit -- "[닫기] (롱터치 진입 시)" --> IdleMap
    
    CalloutShown -- "[지도 빈 곳 터치]" --> IdleMap

    %% Styles for Nodes (High Contrast Text + Muted Backgrounds)
    style IdleMap fill:#f8f9fa,stroke:#dee2e6,stroke-width:2px,color:#212529
    style CalloutShown fill:#e7f5ff,stroke:#a5d8ff,stroke-width:3px,color:#004085
    style PathViewing fill:#fff9db,stroke:#ffec99,stroke-width:3px,color:#856404
    style TodoEdit fill:#fff4e6,stroke:#ffd8a8,stroke-width:3px,color:#7d3300
    style Start fill:#f1f3f5,stroke:#adb5bd
    style RightSide fill:none,stroke:#ced4da,stroke-dasharray: 5 5
    
    %% Styles for Lines (Edges - Subdued)
    linkStyle default stroke:#adb5bd,stroke-width:1.5px
    linkStyle 1 stroke:#1971c2,stroke-width:2px
    linkStyle 2 stroke:#7d3300,stroke-width:2px
    linkStyle 3 stroke:#856404,stroke-width:2px
    linkStyle 4 stroke:#7d3300,stroke-width:2px
    linkStyle 5 stroke:#fa5252,stroke-width:2px
    linkStyle 6 stroke:#fa5252,stroke-width:2px
    linkStyle 7 stroke:#fa5252,stroke-width:2px
    linkStyle 8 stroke:#adb5bd,stroke-width:1px
```

## 다이어그램을 통한 "수정 제제" 규약
- **상태 전이 준수**: 위 화살표에 정의되지 않은 방식으로 화면이 넘어가거나(예: 핀 터치 시 물풍선 없이 바로 상세창 이동), 임의의 중간 상태를 추가하는 행위는 사용자님의 승인 없이 수행하지 않습니다.
- **Trigger 확인**: 각 화살표에 적힌 '터치 조건'만이 유일한 상태 변경 트리거임을 보장합니다.

---


## 할 일 편집 (Todo Detail Inputs)
할 일의 각 필드를 입력하기 위해 진입하는 서브 창들과의 전환 로직입니다.

```mermaid
flowchart LR
    %% Main Node (Left)
    MainSheet["( 할 일 만들기/상세 (MainSheet) )"]

    %% Subordinate Nodes (Right)
    subgraph Inputs ["입력 관련 서브 창/팝업"]
        direction TB
        PlaceSearch["( 할 일 이름/장소 검색 )"]
        ContactSearch["( 연락처 검색 )"]
        CalendarPop["(( 캘린더 팝업 ))"]
        TimePop["(( 시간 팝업 ))"]
        MemoInput["( 메모 입력 창 )"]
    end

    %% Transitions
    MainSheet <==>|"[갔던곳] 터치"| PlaceSearch
    MainSheet <==>|"[연락처 list] 터치"| ContactSearch
    MainSheet <==>|"[날짜] 터치"| CalendarPop
    MainSheet <==>|"[시간] 터치"| TimePop
    MainSheet <==>|"[기억을 위한 메모] 터치"| MemoInput

    %% Styles (High Legibility Theme)
    style MainSheet fill:#fff4e6,stroke:#d9480f,stroke-width:3px,color:#7d3300
    style PlaceSearch fill:#f8f9fa,stroke:#dee2e6,color:#212529
    style ContactSearch fill:#f8f9fa,stroke:#dee2e6,color:#212529
    style CalendarPop fill:#e7f5ff,stroke:#339af0,color:#004085
    style TimePop fill:#e7f5ff,stroke:#339af0,color:#004085
    style MemoInput fill:#f8f9fa,stroke:#dee2e6,color:#212529
    style Inputs fill:none,stroke:#ced4da,stroke-dasharray: 5 5
```
