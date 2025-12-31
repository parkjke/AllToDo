# 지도 핀 에셋 표준화 작업 리포트

본 문서는 디자인 명세에 따라 지도 핀 에셋을 표준화하고, Android 및 iOS 플랫폼에 일관된 프리미엄 룩을 적용하기 위해 수행된 변경 사항을 정리합니다.

## 1. SVG 에셋 생성
Apple Map 스타일(Shield 형태 및 그라데이션)을 기반으로 SVG 핀을 절차적으로 생성하는 Python 스크립트(`Tools/generate_pins.py`)를 작성했습니다.

**생성된 에셋 (9종):**
- **ToDo (녹색)**: `pin_todo_ready`(준비), `pin_todo_done`(완료), `pin_todo_cancel`(취소), `pin_todo_fail`(실패)
- **History (빨강)**: `pin_history`(별 아이콘)
- **Current Location (빨강)**: `pin_current`(조준선 아이콘)
- **Receive (파랑)**: `pin_receive_ready`(수신), `pin_receive_done`(처리완료), `pin_receive_reject`(거절)

## 2. 통합 작업

### Android 통합
- **변환**: `svg2vectordrawable` 도구를 사용하여 SVG를 `app/src/main/res/drawable/` 경로의 Android Vector Drawable(`.xml`)로 변환했습니다.
- **코드 업데이트**:
  - **`MapCommon.kt`**: `UnifiedItem`에 `getPinResId()` 메서드를 추가하여 아이템 상태(ToDo 상태, 출처 등)에 따라 동적으로 리소스 ID를 반환하도록 했습니다.
  - **`GoogleMapContent.kt`**: 마커 생성 시 `item.getPinResId()`를 사용하도록 수정했습니다.
  - **`NaverMapContent.kt`**: `OverlayImage` 생성 시 새로운 리소스를 사용하도록 업데이트했습니다.
  - **`KakaoMapContent.kt`**: 단일 아이템 표시에 다이아몬드 도형 대신 `item.getPinResId()`를 사용하도록 로직을 변경했습니다.

### iOS 통합
- **에셋 카탈로그**: `Tools/integrate_ios_assets.py` 스크립트를 작성하여 각 핀을 `Assets.xcassets`의 `.imageset`으로 자동 등록했습니다.
- **코드 업데이트**:
  - **`UnifiedMapModels.swift`**: `UnifiedMapItem`에 `imageName` 연산 프로퍼티를 추가하여 상태별 에셋 이름(예: `item.isCompleted` -> `PinTodoDone`)을 매핑했습니다.
  - **`KakaoMapView.swift`**: `updatePins` 메서드에서 `registerAllPinStyles`를 통해 모든 핀 스타일을 등록하고 동적으로 선택하도록 수정했습니다.
  - **`NaverMapView.swift`**: `UIImage(named: item.imageName)`을 로드하도록 업데이트했습니다.
  - **`GoogleMapView.swift`**: `GMSMarker` 아이콘 설정 시 올바른 에셋 이미지를 사용하도록 수정했습니다.

## 3. 검증

### 검증 절차
에셋 생성 및 통합이 프로그래밍 방식으로 이루어졌으므로, 시뮬레이터 또는 실제 기기에서 다음 사항을 육안으로 확인해야 합니다.
1.  **핀 외형**: 핀이 쉴드(방패) 모양이며 그라데이션(iOS) 또는 깔끔한 벡터(Android)로 표시되는지 확인.
2.  **상태 로직**: ToDo를 "완료" 처리했을 때 핀이 "깃발(Ready)"에서 "체크(Done)"로 변경되는지 확인.
3.  **기록(History)**: 과거 기록이 빨간색 별 모양 핀으로 표시되는지 확인.
4.  **현재 위치**: "나(Me)" 마커가 빨간색 조준선 핀으로 표시되는지 확인.

### 수행된 자동 확인
- Android용 XML 파일 생성 확인 완료.
- iOS용 Asset Catalog 항목 등록 확인 완료.
- 변경된 코드 파일의 컴파일 가능성 확인.

## 향후 계획
- **빌드 및 실행**: Android Studio와 Xcode에서 프로젝트를 빌드하여 런타임 리소스 연결 오류가 없는지 최종 확인합니다.
- **시각적 개선**: 만약 Android 핀이 iOS에 비해 너무 평면적으로 보일 경우, Vector Drawable 경로에 그림자를 추가하거나 그림자가 포함된 비트맵 생성 방식을 고려할 수 있습니다.
