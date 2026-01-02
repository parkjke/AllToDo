# Naver Map Android Integration Case Study (문제 해결 가이드)

본 문서는 **AllToDo-Android** 프로젝트에서 네이버 지도(Naver Map) SDK를 통합하는 과정에서 발생한 주요 장애와 그 해결 방법을 기록합니다. 향후 동일한 문제가 발생하거나 환경 재구축 시 참고하십시오.

---

## 1. 빌드 및 종속성 오류 (Build & Dependency)

### [Issue] `checkDebugAarMetadata` FAILED
- **증상**: 그레이들 빌드 중 AAR 메타데이터 확인 단계에서 실패함.
- **원인**: `naver-map-compose` 라이브러리와 `naver-map-sdk` 간의 버전 불일치. `naver-map-compose` 1.6.0~1.7.0 버전은 특정 버전의 SDK를 강제하는 경향이 있음.
- **해결**: 
  - `naver-map-sdk` 버전을 **3.21.0**으로 업그레이드.
  - `io.github.fornewid:naver-map-compose` 최신 버전을 사용하여 호환성 확보.
  - `build.gradle.kts` 설정:
    ```kotlin
    implementation("com.naver.maps:map-sdk:3.21.0")
    implementation("io.github.fornewid:naver-map-compose:1.8.0")
    ```

### [Issue] `naver-map-sdk-compose` 아티팩트를 찾을 수 없음
- **증상**: `com.naver.maps:android-map-sdk-compose` 종속성을 추가했으나 Maven 레포지토리에서 찾지 못함.
- **원인**: 네이버 공식 Compose 라이브러리 경로가 표준과 다르거나 배포 방식이 변경됨.
- **해결**: 커뮤니티 표준인 **`io.github.fornewid:naver-map-compose`**를 사용함.

---

## 2. 인증 및 초기화 오류 (Authentication & API Key)

### [Issue] `[800] Client is unspecified`
- **증상**: 앱 실행 시 지도가 뜨지 않고 로그캣에 800번 에러 출력.
- **원인**: `AndroidManifest.xml`에 클라이언트 ID 메타데이터가 누락되었거나 키 이름이 잘못됨.
- **해결**: `AndroidManifest.xml`에 다음 두 가지 키 이름을 모두 기입하여 호환성을 확보함.
    ```xml
    <meta-data
        android:name="com.naver.maps.map.NCP_KEY_ID"
        android:value="YOUR_CLIENT_ID" />
    <meta-data
        android:name="com.naver.maps.map.CLIENT_ID"
        android:value="YOUR_CLIENT_ID" />
    ```

### [Issue] `401 Unauthorized client`
- **증상**: 지도가 회색으로 표시되며 인증 실패 메시지 발생.
- **원인**: 
  1. 클라이언트 ID가 틀림.
  2. 네이버 클라우드 콘솔에 등록된 **패키지 명(Application ID)**과 실제 앱의 패키지 명이 다름.
  3. 디버그/릴리스 **SHA-1 지문**이 콘솔에 등록되지 않음.
- **해결**:
  - `app/build.gradle.kts`의 `applicationId`를 콘솔 등록 정보와 일치시킴 (예: `kr.navermaptest` 또는 `kr.alltodo`).
  - 다음 명령어로 SHA-1을 추출하여 네이버 콘솔에 등록:
    `./gradlew signingReport`

---

## 3. 런타임 동작 오류 (Runtime Behavior)

### [Issue] 지도 초기 위치가 (0,0)으로 튀는 현상 (Dalian Problem)
- **증상**: 위치 권한 수락 직후 또는 GPS가 잡히기 전 지도가 대련(중국) 인근 바다인 (0,0) 좌표로 점프함.
- **원인**: `fusedLocationClient.lastLocation`이 null이 아니더라도 내부 좌표가 (0,0)인 상태로 반환되는 경우를 필터링하지 않음.
- **해결**: 좌표 유효성 검사 로직 추가. (0,0)이거나 null인 경우 **광화문(37.5759, 126.9768)**을 기본값으로 강제 설정.
    ```kotlin
    if (userLoc == null || (userLoc.latitude == 0.0 && userLoc.longitude == 0.0)) {
        // 광화문으로 점프
    }
    ```

### [Issue] 마커(Marker) 앵커 미세 틀어짐
- **증상**: iOS 스타일의 뱃지가 달린 핀을 사용할 때 핀의 끝부분이 지도의 실제 좌표와 맞지 않음.
- **원인**: 뱃지 공간 확보를 위해 비트맵 캔버스가 상단과 우측으로 확장되었으나, 앵커 포인트는 여전히 (0.5, 1.0)을 유지함.
- **해결**: 확장된 픽셀 비율을 계산하여 앵커를 **(0.4f, 1.0f)**로 조정하여 핀 끝점을 물리적 좌표에 정확히 일치시킴.

### [Issue] 안드로이드 맵 프리징 (Silent Killer)
- **증상**: 지도 전환 시 또는 특정 핀이 겹칠 때 지도가 굳어버리며 터치 등 어떤 조작도 먹지 않음.
- **원인 1 (Google Map)**: 핀 데이터가 완전히 겹칠 때 Compose `key` 중복으로 인한 렌더링 충돌.
- **원인 2 (Naver Map)**: 
    - 마커 생성 로직에서 위치(`position`)를 설정하기 전에 지도(`map`)에 먼저 추가함.
    - 입력 데이터에 `NaN`(Not a Number) 좌표가 포함되어 SDK 내부 렌더링 루프를 중지시킴.
- **해결**: 
    - 중복 키 방어(`distinctBy`) 적용.
    - 마커 속성 설정 시 **`position`을 가장 먼저 설정**하도록 강제.
    - `NaN` 좌표 유입 시 앱이 죽지 않도록 방어 코드(Guard) 삽입.

---

## 4. 성능 최적화 (Performance)

### [Issue] 마커 플리커(Flickering) 및 메모리 부족
- **증상**: 지도 이동 시 마커가 깜빡이거나 메모리 사용량이 급증함.
- **원인**: `onCameraChange` 시마다 마커 비트맵을 새로 생성함.
- **해결**: `PinImageManager`에서 `LruCache`를 사용하여 한 번 생성된 비트맵을 재사용함.

---

## 5. 요약 체크리스트 (Summary Checklist)
1. `build.gradle.kts`에 `https://repository.map.naver.com/archive/maven` 레포지토리 추가 여부 확인.
2. `AndroidManifest.xml`에 `ACCESS_FINE_LOCATION` 권한 및 메타데이터 2종 확인.
3. 네이버 콘솔 시스템 상의 패키지 명 + SHA-1 지문 대조 확인.
4. 초기화 시 (0,0) 좌표 방어 로직 작동 확인.
