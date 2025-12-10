# 데이터 모델 엔티티 관계도 (ERD)

현재 iOS와 Android 애플리케이션의 데이터 구조입니다.

## Mermaid ERD (다이어그램)

```mermaid
erDiagram
    %% iOS 모델 (SwiftData)
    
    iOS_ToDoItem {
        UUID id PK "고유 ID"
        String title "제목"
        Bool isCompleted "완료 여부"
        Date createdAt "생성일"
        Date dueDate "마감일 (옵션)"
        LocationData location "위치 정보 (Struct)"
    }

    iOS_Appointment {
        UUID id PK "고유 ID"
        String title "제목"
        Date startTime "시작 시간"
        Date endTime "종료 시간"
        LocationData location "위치 정보 (옵션)"
    }
    
    iOS_Contact {
        UUID id PK "고유 ID"
        String name "이름"
        String phoneNumber "전화번호 (옵션)"
        String groupName "그룹명 (옵션)"
    }

    iOS_UserLog {
        UUID id PK "고유 ID"
        Date startTime "시작 시간"
        Date endTime "종료 시간"
        Double latitude "위도"
        Double longitude "경도"
        Data pathData "경로 데이터 (JSON)"
    }

    %% 관계 설정: 일정(Appointment)은 여러 참가자(Contact)를 가질 수 있음
    iOS_Appointment ||--o{ iOS_Contact : participants

    %% Android 모델 (Room 데이터베이스)

    Android_TodoItem {
        String id PK "고유 ID (UUID String)"
        String text "내용"
        Boolean completed "완료 여부"
        Long createdAt "생성일 (Timestamp)"
        String source "출처 (local/external)"
        Double latitude "위도 (옵션)"
        Double longitude "경도 (옵션)"
    }

    Android_UserLog {
        Long id PK "자동 증가 ID"
        Double latitude "위도"
        Double longitude "경도"
        Long startTime "시작 시간 (Timestamp)"
        Long endTime "종료 시간 (Timestamp)"
        String pathData "경로 데이터 (JSON String)"
    }

    %% 참고 사항
    %% iOS는 'LocationData'라는 별도 구조체를 사용하여 위치를 관리합니다.
    %% Android는 위도/경도를 테이블 컬럼으로 직접 저장하는 방식을 사용합니다.
```

## 스키마 상세 설명

### iOS (SwiftData)
- **프레임워크**: SwiftData (`@Model`)
- **주요 타입**:
    - `ToDoItem`: 할 일을 나타냅니다. `LocationData` 구조체를 포함하여 위치를 저장합니다.
    - `Appointment`: 캘린더 일정을 나타내며, `Contact`(참가자)와 연결됩니다.
    - [UserLog](file:///Volumes/Work/AllToDo/AllToDo-Android/app/src/main/java/com/example/alltodo/data/UserLog.kt#6-15): 위치 추적 세션(이동 경로)을 저장합니다.
    - `LocationData`: DB 테이블이 아닌 단순 구조체(Struct)로, 엔티티 안에 임베드되어 저장됩니다.

### Android (Room)
- **프레임워크**: Room Database (`@Entity`)
- **주요 타입**:
    - [TodoItem](file:///Volumes/Work/AllToDo/AllToDo-Android/app/src/main/java/com/example/alltodo/data/TodoItem.kt#7-17): 할 일을 나타냅니다. 위치 정보(`latitude`, `longitude`)를 테이블의 컬럼으로 직접 가집니다. (Null 가능)
    - [UserLog](file:///Volumes/Work/AllToDo/AllToDo-Android/app/src/main/java/com/example/alltodo/data/UserLog.kt#6-15): 이동 경로가 포함된 위치 기록 세션을 나타냅니다.
