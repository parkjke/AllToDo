# 🚨 Project Critical Code Locks (CRITICAL_CODE_LOCKS)

이 문서는 AI 에이전트 및 개발자가 절대 수정해서는 안 되는 **3가지 핵심 잠금(Lock) 요소**를 정의합니다.  
이 코드들은 앱의 안정성, 성능, UI/UX 품질을 보장하기 위해 정밀하게 튜닝되었으므로, **"DO NOT MODIFY"** 주석이 없더라도 임의로 변경해서는 안 됩니다.

## ⚠️ 통합 준수 수칙 (Unified Compliance Rule)
**에이전트는 작업 수행 중 아래에 정의된 잠금 구역(Critical Code Locks)을 수정해야 할 필요성을 발견할 경우, 즉시 모든 작업을 중단하고 사용자에게 상황을 보고한 후 지침을 기다려야 합니다.** 사용자의 명시적인 승인 없이는 어떠한 변경도 진행할 수 없습니다.

## 1. Raw First -> Cluster Strategy (초기화 성능 최적화)
- **위치**: `KakaoMapView.swift`, `GoogleMapView.swift`, `NaverMapView.swift`, `AppleMapView.swift`  
- **키워드**: `[CRITICAL LOCK: DO NOT MODIFY] Raw First -> Cluster Strategy`
- **설명**: 앱 초기 실행(Launch Sequence) 시, 무거운 WASM 클러스터링을 건너뛰고 **모든 핀을 Raw 상태로 즉시 렌더링**하는 로직입니다.
- **수정 금지 사유**: 이 로직을 제거하거나 변경하면 WASM 로딩 대기 시간 동안 지도가 비어 보여 체감 실행 속도가 급격히 느려집니다. "일단 보여주고, 나중에 묶는다"는 전략을 유지해야 합니다.

## 2. User-Defined Offsets (핀 팝업 위치 보정)
- **위치**: `ContentView.swift` (Lines ~426, ~438)
- **키워드**: `User-Defined Offsets`, `[CRITICAL LOCK: DO NOT MODIFY]`
- **설명**: 사용자가 핀을 터치했을 때 말풍선(Callout)이 핀의 머리 부분을 가리지 않고 정확히 위에 뜨도록 계산된 **X축/Y축 오프셋 값**입니다.
    - 예: `y: -110` (핀 높이 및 여백 고려)
- **수정 금지 사유**: 이 값은 단순 수학적 중앙이 아니라, 핀 이미지 디자인(40x50dp 등)과 시각적 밸런스를 고려해 'Ghost Balloon' 현상을 막기 위해 픽셀 단위로 고정된 값입니다.

## 3. Background Re-Launch Logic (백그라운드 복귀 처리)
- **위치**: `ContentView.swift` (Lines ~707)
- **키워드**: `Background Re-Launch Logic`
- **설명**: 앱이 백그라운드에 **5초 이상** 머물렀다 돌아왔을 때, 지도를 "새로운 세션"으로 간주하고 초기화 시퀀스(Step 1~4)를 다시 실행하는 로직입니다.
- **수정 금지 사유**: 
    1. **OpenGL 컨텍스트 유실 방지**: 오래된 세션의 맵 뷰가 회색으로 굳는 현상을 방지합니다.
    2. **GPS Cold Start 은폐**: 백그라운드에서 GPS가 끊긴 동안의 위치 점프를 자연스럽게 처리하기 위해 재진입 시퀀스가 필수적입니다.

## 4. Multi-Engine Geospatial Integrity (지도 엔진 정합성)
- **위치**: 
    - iOS: `AppleMapView.swift`, `GoogleMapView.swift`, `KakaoMapView.swift`, `NaverMapView.swift`
    - Android: `GoogleMapContent.kt`, `KakaoMapContent.kt`, `NaverMapContent.kt`, `PinImageManager.kt`
- **키워드**: `Pin Anchor`, `Center Offset`, `0.392f`, `53dp/pt`
- **설명**: 4대 지도 엔진 간의 **핀 앵커 포인트(0.392f)** 및 **카메라 센터링 오프셋(53dp/pt)** 등을 픽셀 단위로 일원화한 로직입니다.
- **수정 금지 사유**: 
    - 이 로직은 각 SDK의 서로 다른 좌표계와 이미지 처리 방식을 수천 번의 테스트를 통해 동기화한 결과물입니다. 
    - **절대 지도 엔진 전체를 한 번에 리팩토링하지 마십시오.** 작은 오차만으로도 핀이 공중에 떠 보이거나 물풍선이 핀을 가리는 등의 시각적 파손이 발생합니다.
