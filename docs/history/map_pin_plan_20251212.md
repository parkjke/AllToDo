# 지도 핀 에셋 표준화 구현 계획

이 문서는 지도 핀 디자인을 표준화하고 Android 및 iOS 애플리케이션에 일관되게 적용하기 위한 계획을 기술합니다.

## 목표
- Apple Map 스타일(Shield 형태, 그라데이션)을 따르는 고품질 지도 핀 에셋 생성.
- 상태(ToDo, History, Current, Receive)에 따른 색상 및 아이콘 명확화.
- 양대 플랫폼(Android, iOS)에 자동화된 방식으로 리소스 통합.

## 사용자 검토 필요 사항
> [!NOTE]
> 모든 핀은 SVG로 생성된 후 각 플랫폼에 맞는 포맷(Android: Vector Drawable, iOS: Asset Catalog)으로 변환됩니다. 디자인 변경이 필요한 경우 `Tools/generate_pins.py` 스크립트를 수정해야 합니다.

## 제안된 변경 사항

### 도구 (Tools)
핀 생성 및 통합을 위한 스크립트가 추가됩니다.
#### [NEW] [generate_pins.py](file:///Volumes/Work/AllToDo/Tools/generate_pins.py)
#### [NEW] [integrate_ios_assets.py](file:///Volumes/Work/AllToDo/Tools/integrate_ios_assets.py)

### Android
#### [MODIFY] [MapCommon.kt](file:///Volumes/Work/AllToDo/AllToDo-Android/app/src/main/java/com/example/alltodo/ui/MapCommon.kt)
- `UnifiedItem` 모델에 `getPinResId()` 메서드 추가 (상태별 리소스 매핑).

#### [MODIFY] [GoogleMapContent.kt](file:///Volumes/Work/AllToDo/AllToDo-Android/app/src/main/java/com/example/alltodo/ui/components/GoogleMapContent.kt)
#### [MODIFY] [KakaoMapContent.kt](file:///Volumes/Work/AllToDo/AllToDo-Android/app/src/main/java/com/example/alltodo/ui/components/KakaoMapContent.kt)
#### [MODIFY] [NaverMapContent.kt](file:///Volumes/Work/AllToDo/AllToDo-Android/app/src/main/java/com/example/alltodo/ui/components/NaverMapContent.kt)
- 하드코딩된 마커 로직을 `getPinResId()`를 사용하는 동적 로직으로 교체.

### iOS
#### [MODIFY] [UnifiedMapModels.swift](file:///Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Models/UnifiedMapModels.swift)
- `UnifiedMapItem` 열거형에 `imageName` 프로퍼티 추가.

#### [MODIFY] [KakaoMapView.swift](file:///Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/KakaoMapView.swift)
- `updatePins` 메서드에서 모든 핀 스타일을 사전 등록하고 `imageName`으로 스타일 선택.

#### [MODIFY] [NaverMapView.swift](file:///Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/NaverMapView.swift)
#### [MODIFY] [GoogleMapView.swift](file:///Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/GoogleMapView.swift)
- 에셋 카탈로그 이미지(`UIImage(named:)`)를 사용하도록 변경.

## 검증 계획

### 자동화 테스트
- 생성된 파일(XML, Imageset)의 존재 여부 확인 스크립트 실행.

### 수동 검증
- **Android/iOS 앱 실행**: 지도 화면 진입.
- **핀 확인**:
    - [ ] `Todo`(녹색) 핀이 올바른 아이콘(깃발/체크)으로 표시되는지 확인.
    - [ ] `History`(빨강) 핀이 별 모양으로 표시되는지 확인.
    - [ ] `Current Location`(빨강) 핀이 조준선 모양으로 표시되는지 확인.
    - [ ] 확대/축소 시 해상도 깨짐 없이 선명한지 확인.
