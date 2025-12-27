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

## 8. 히스토리 및 위치/경로 저장 상세 로직 (History & Path Logic V2)

히스토리 저장은 앱 종료와 무관하게 **실시간 및 주기적**으로 이루어지며, 사용자 위치는 별도의 '0번 히스토리'로 관리됩니다.

### 8.1. 히스토리 자동 분할 저장 (30분 주기)
앱이 실행되는 동안 지속적으로 경로를 추적하며, 데이터가 비대해지는 것을 방지하기 위해 **30분 단위**로 히스토리를 분리하여 저장합니다.

1.  **30분 타이머**: 경로 기록이 시작된 시점부터 30분이 경과하면 현재의 `Path` 기록을 중단합니다.
2.  **데이터 저장**: 지금까지 누적된 경로(`PathItem` List)와 메타 데이터를 `todo_items` (Type: 00) 및 `paths` 테이블에 영구 저장합니다. (`is_exist_location_path = true`)
3.  **새 세션 시작**: 새로운 `UUID`를 발급받아 빈 상태에서 다시 경로 기록을 시작합니다. (사용자는 끊김을 인지하지 못함)

### 8.2. 앱 종료 및 재시작 시나리오
앱이 강제 종료되거나 재시작될 때의 데이터 처리 로직입니다.

*   **앱 종료 시**:
    *   현재 기록 중이던 경로(30분이 안 되었더라도)를 **즉시 DB에 저장**하고 종료합니다. (`deathScope` 활용)
*   **앱 재시작 시**:
    *   DB에서 마지막으로 저장된 히스토리(`type: 00`)의 **마지막 좌표**를 불러옵니다.
    *   이 좌표를 앱 시작 시의 초기 위치(Initial Location) 및 '0번 히스토리'의 좌표로 설정하여 연속성을 보장합니다.

### 8.3. 사용자 현재 위치 관리 (0번 히스토리)
사용자의 현재 위치는 지도상에 항상 표시되어야 하는 특수 개체입니다.

*   **정의**: `type = "00"` 이면서 `idx(또는 식별자) = 0` 인 특수 레코드.
*   **갱신 로직**:
    *   GPS 위치가 변경될 때마다 이 '0번 히스토리'의 `int_lat`, `int_long` 컬럼을 **UPDATE** 합니다. (INSERT 아님)
    *   이 데이터는 지도상에 **"내 위치(Current Location Pin)"**를 그리는 소스로 활용됩니다.
