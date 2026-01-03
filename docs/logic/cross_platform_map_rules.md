# 크로스 플랫폼 지도 필터링 규칙 (iOS & Android)

이 문서는 iOS 리팩토링 과정에서 정립된, 지도 아이템 필터링 및 표시에 대한 통합 로직을 정의합니다. **안드로이드 구현 시 반드시 이 규칙을 따라야 합니다.**

## 1. 핵심 필터링 원칙 (Core Filtering Principles)
모든 아이템은 화면에 표시되기 위해 다음의 기본 필터링 조건을 통과해야 합니다:
1.  **경로 존재 여부**: `item.is_exist_location_path == true`일 것.
2.  **시간 범위**: `item.date_time`이 현재 기준 날짜(실시간 또는 타임 트래블)의 **-24시간 이후**일 것 (미래 데이터는 무제한 표시).
3.  **가상 아이템**: "사용자 위치" 가상 아이템(Type 00)은 거리나 시간과 관계없이 **항상 포함**합니다.

---

## 2. 대한민국 영역 파티셔닝 ("Korea Rule")

단순한 500km 반경 거리 제한을 사용하지 **않습니다**. 대신, 아이템이 **대한민국 지리적 영역(Geo-fence)** 내부에 있는지 여부로 데이터를 분리합니다.

### "대한민국 영역" 정의
- **위도 (Latitude)**: 32.0 ~ 44.0
- **경도 (Longitude)**: 123.0 ~ 133.0

### 로직 (Logic)
```swift
func isInKorea(lat: Double, lon: Double) -> Bool {
    return lat >= 32.0 && lat <= 44.0 && lon >= 123.0 && lon <= 133.0
}
```

---

## 3. 지도 제공자별 동작 (Map Provider Specific Behaviors)

지도 제공자가 **글로벌(Global)**인지 **로컬(Local)**인지에 따라 표시 로직이 달라집니다.

### A. 글로벌 지도 (Apple, Google)
- **규칙**: **거리 및 지역 필터링 없음.**
- **동작**: 검증된 모든 아이템을 위치(예: 도쿄, 뉴욕, 서울)에 상관없이 **즉시 표시**합니다.
- **원거리 핀 UI**: "먼 곳의 핀" 알림 버튼을 **숨깁니다**.

### B. 로컬 지도 (Kakao, Naver)
- **규칙**: **대한민국 영역 기준 분할(Partition by Korea Region).**
- **트리거 조건(Trigger Conditions)**:
    - 앱 실행 시 (App Launch)
    - 백그라운드에서 5초 이상 경과 후 복귀 시 (Returning to Foreground > 5s)
- **기본 상태 (Default State)**: 
    - 대한민국 **내부**의 아이템만 표시합니다.
    - 대한민국 **외부**의 아이템은 숨깁니다.
- **원거리 핀 알림 (Far Items Notification)**: 
    - 대한민국 **외부**에 아이템이 존재할 경우, "N개의 핀이 먼 곳에 있습니다" 버튼을 표시합니다.
- **사용자 액션**:
    - 사용자가 알림 버튼을 탭하면, **필터를 해제**하고 모든 아이템(해외 포함)을 표시합니다.

---

## 4. 요약 테이블 (Summary Table)

| 기능 | 글로벌 지도 (Apple/Google) | 로컬 지도 (Kakao/Naver) |
| :--- | :--- | :--- |
| **기본 뷰 (Default View)** | 전 세계 표시 (Global) | 한국 내부만 표시 |
| **숨겨진 아이템** | 없음 | 한국 외부 아이템 |
| **"먼 곳의 핀" 버튼** | 표시 안 함 | 한국 외부 아이템 존재 시 표시 |
| **버튼 동작** | 해당 없음 | 숨겨진 아이템 표시 |

---

## 5. 안드로이드 구현 체크리스트 (Implementation Checklist)
- [ ] `MapLogicHelper`와 동등한 클래스에 `isInKorea()` 함수 구현.
- [ ] 아이템을 분리하는 `partitionItemsByKorea()` 로직 구현.
- [ ] `MapFeatureViewModel`(또는 동등한 뷰모델)에 `filterByKorea` 플래그 추가.
- [ ] Kakao/Naver 지도에는 `filterByKorea = true`, Google 지도에는 `false` 전달.
