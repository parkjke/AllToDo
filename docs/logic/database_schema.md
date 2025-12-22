# 데이터베이스 구조 명세서 (Database Schema Specification)

AllToDo 시스템의 데이터 영속성을 위한 테이블 구조와 관계를 정의합니다. DB 관례에 따라 모든 필드명은 `snake_case`를 사용합니다.

## 1. 할일 테이블 (Todo Table)
할일의 기본 정보, 시간, 메모 및 관련 상태를 관리하는 핵심 테이블입니다.

| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `todo_id` | UUID (PK) | 고유 식별자 |
| `todo_name` | String | 할일 이름 |
| `is_exist_person` | Boolean | 연락처(사람) 할당 여부 |
| `date_time` | DateTime | 할일 수행 예정 날짜 및 시간 |
| `memo` | String | 할일 관련 메모 |
| `is_exist_location_path`| Boolean | 이동 경로 데이터 존재 여부 |
| `begin_time` | DateTime | 경로 시작 시간 (Nullable) |
| `end_time` | DateTime | 경로 종료 시간 (Nullable) |
| `type` | String | 항목 형태 (00: 히스토리, 10: 할일, 20: 서버 지시) |
| `created_at` | Long | 생성 시각 (타임스탬프) |

## 2. 연락처 테이블 (Contact Table)
할일과 연결된 연락처(대상자) 정보를 저장합니다. 주소록과 연동될 수 있습니다.

| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `todo_id` | UUID (FK) | 할일 테이블 참조 |
| `address_id` | UUID (FK) | 주소록 테이블 참조 (Nullable) |
| `name` | String | 연락처 이름 |
| `p_name` | String | 연락처 전화번호 |
| `int_long` | Integer | 경도 (x100,000 정수화, Nullable) |
| `int_lat` | Integer | 위도 (x100,000 정수화, Nullable) |

## 3. 주소록 테이블 (Address Book Table)
모바일 기기의 주소록 데이터 및 상세 개인 정보를 저장합니다.

| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `address_id` | UUID (PK) | 고유 식별자 |
| `last_name` | String | 이름 (성) |
| `first_name` | String | 이름 (이름) |
| `name` | String | 한국식 전체 성명 |
| `name_consonants` | String | 이름의 자음 (검색용) |
| `phone_name1..5` | String | 연락처 (최대 5개, Nullable) |
| `home_address` | String | 집 주소 |
| `int_long_home` | Integer | 집 위치 경도 (x100,000 정수화) |
| `int_lat_home` | Integer | 집 위치 위도 (x100,000 정수화) |
| `company_address` | String | 회사 주소 |
| `company_int_long` | Integer | 회사 위치 경도 (x100,000 정수화) |
| `company_int_lat` | Integer | 회사 위치 위도 (x100,000 정수화) |

## 4. 경로 테이블 (Path Table)
할일(특히 히스토리 형태)에 포함된 상세 이동 경로 좌표를 저장합니다.

| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `todo_id` | UUID (FK) | 할일 테이블 참조 |
| `int_long` | Integer | 경도 (x100,000 정수화) |
| `int_lat` | Integer | 위도 (x100,000 정수화) |

---

## 💡 주소록 연동 및 처리 로직
모바일 주소록 동기화 시 다음과 같은 데이터 보완 로직을 수행합니다.

1.  **성명 자동 생성**:
    *   `name`만 존재할 경우: `first_name`, `last_name`, `name_consonants`를 분리/생성하여 저장.
    *   `first_name`, `last_name`만 존재할 경우: 두 값을 합쳐 `name`을 생성하고 `name_consonants` 생성.
2.  **데이터 내보내기**: 외부 공유 시에는 `first_name`, `last_name`, `name` 정보만 선택적으로 가공하여 노출합니다.
3.  **저장 정책**: 연락처 테이블과 주소록 테이블은 데이터의 독립성을 위해 별개로 관리합니다.

## 💡 형태(Type) 정의 규칙
`type` 필드는 현재 2자리를 사용하며, 향후 확장성을 고려한 설계입니다.

*   **00**: 히스토리 (History) - 완료된 과거 기록 (자동 생성됨)
*   **10**: 할일 (To-do) - 사용자가 직접 앱에서 만든 작업
*   **20**: 서버 지시 (Server Instruction) - 외부 서버로부터 수신된 명령

---

## 💡 히스토리 및 경로 데이터 저장 상세 로직

앱이 종료되거나 세션이 끝날 때, 위치 경로 데이터는 다음과 같은 절차로 안전하게 저장됩니다.

1.  **UUID 선발행**: `TodoItem` 객체를 생성하기 전, 클라이언트 측에서 `UUID.randomUUID()`를 통해 고유 ID를 생성합니다. 이는 DB 인서트 전후로 `todo_id`를 확정하여 후속 경로 데이터와의 연관 관계를 즉시 보장하기 위함입니다.
2.  **TodoItem 생성 (Type: 00)**: `todo_name`을 "이동 히스토리"로 설정하고, `type`을 "00"으로 지정하여 `todo_items` 테이블에 기본 정보를 먼저 저장합니다. 이때 `is_exist_location_path` 필드를 `true`로 설정합니다.
3.  **경로 데이터 벌크 저장**: 메모리에 압축되어 있던 실시간 좌표 데이터들을 `PathItem` 목록으로 변환합니다. 모든 좌표는 저장 공간 효율과 계산 일관성을 위해 `x100,000`을 곱한 정수(`Integer`) 형태로 변환되어 `paths` 테이블에 **Batch Insert** 됩니다.
4.  **앱 종료 시 무결성 보장**: 앱이 완전히 종료되는 `onCleared()` 시점에서도 `deathScope`와 `NonCancellable` 코루틴 컨텍스트를 사용하여, 저장 프로세스가 중간에 끊기지 않고 완료되도록 설계되었습니다.
