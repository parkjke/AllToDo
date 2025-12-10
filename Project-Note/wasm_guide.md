# WASM 개발 및 배포 가이드 (Rust)

이 문서는 AllToDo 프로젝트의 핵심 코어 로직(WASM)을 수정하고 배포하는 방법을 설명합니다.

## 1. 🌟 핵심 개념: "Write Once, Run Anywhere"
가장 중요한 점은 **안드로이드용 코드와 iOS용 코드가 따로 있지 않다**는 것입니다.
아래에서 보실 [lib.rs](file:///Volumes/Work/AllToDo/WasmProject/wasm_src/src/lib.rs)라는 **단 하나의 Rust 파일**이 컴파일되어 안드로이드와 iOS 양쪽에서 똑같이 돌아갑니다.

## 2. 📝 현재 Rust 코드 ([src/lib.rs](file:///Volumes/Work/AllToDo/WasmProject/wasm_src/src/lib.rs))
이 코드가 현재 이동 경로를 압축하는 로직의 원본입니다.

```rust
use wasm_bindgen::prelude::*;
use serde::{Serialize, Deserialize};

#[derive(Serialize, Deserialize)]
pub struct IntCoordinate {
    pub lat: i32,
    pub lng: i32,
}

// 📦 이 함수가 안드로이드/iOS에서 호출되는 메인 함수입니다.
#[wasm_bindgen]
pub fn compress_trajectory(
    points_flat: &[i32],       // 입력: [lat, lng, lat, lng...] 형태의 평탄화된 배열
    min_dist_meters: f64,      // 최소 거리 (이 거리보다 가까우면 생략)
    angle_thresh_deg: f64      // 각도 변화 (직선이면 생략)
) -> Vec<i32> {
    // 1. 입력 데이터를 좌표 객체로 변환
    let mut points: Vec<IntCoordinate> = Vec::new();
    for chunk in points_flat.chunks(2) {
        if chunk.len() == 2 {
            points.push(IntCoordinate { lat: chunk[0], lng: chunk[1] });
        }
    }

    if points.len() <= 2 {
        return points_flat.to_vec();
    }

    // 2. 압축 로직 (RDP 유사 알고리즘)
    let mut compressed: Vec<IntCoordinate> = Vec::new();
    compressed.push(IntCoordinate { lat: points[0].lat, lng: points[0].lng });
    
    let mut last_kept = &points[0];

    for i in 1..points.len()-1 {
        let current = &points[i];
        
        // 거리 계산 (Haversine 공식)
        let dist = distance_meters(last_kept, current);
        
        if dist >= min_dist_meters {
            // 각도 계산
            let next_point = &points[i+1];
            let b1 = bearing(last_kept, current);
            let b2 = bearing(current, next_point);
            let diff = (b1 - b2).abs();
            let angle_diff = if diff > 180.0 { 360.0 - diff } else { diff };
            
            // 의미 있는 변화가 있을 때만 저장
            if angle_diff >= angle_thresh_deg {
                compressed.push(IntCoordinate { lat: current.lat, lng: current.lng });
                last_kept = current;
            }
        }
    }
    
    // 마지막 점은 무조건 포함
    if let Some(last) = points.last() {
        compressed.push(IntCoordinate { lat: last.lat, lng: last.lng });
    }

    // 3. 다시 배열로 변환하여 반환
    let mut result: Vec<i32> = Vec::new();
    for p in compressed {
        result.push(p.lat);
        result.push(p.lng);
    }
    
    result
}

// --- 보조 함수들 (거리, 각도 계산) ---
fn distance_meters(p1: &IntCoordinate, p2: &IntCoordinate) -> f64 {
    // ... (Haversine 공식 구현 생략) ...
    0.0 // 실제 코드는 위 참조
}

fn bearing(p1: &IntCoordinate, p2: &IntCoordinate) -> f64 {
    // ... (방위각 계산 구현 생략) ...
    0.0 // 실제 코드는 위 참조
}
```

## 3. 🛠️ 수정 및 배포 방법

코드를 수정하고 싶다면 다음 3단계만 기억하세요.

### 1단계: 코드 수정
에디터로 [/Volumes/Work/AllToDo/WasmProject/wasm_src/src/lib.rs](file:///Volumes/Work/AllToDo/WasmProject/wasm_src/src/lib.rs) 파일을 엽니다.
원하는 로직(예: 압축 강도 변경, 새로운 필터 추가 등)을 수정하고 저장합니다.

### 2단계: 빌드 및 배포 (원클릭!)
터미널을 열고 아래 명령어를 입력합니다.

```bash
cd /Volumes/Work/AllToDo/WasmProject
./build_and_deploy.sh
```

이 스크립트가 하는 일:
1.  Rust 코드를 기계어(WASM)로 컴파일합니다. (`wasm-pack build`)
2.  `AllToDo-Backend/wasm/` 폴더로 최신 파일을 복사합니다.
3.  `AllToDo-Android/app/src/main/assets/` 폴더로 복사합니다.
4.  `AllToDo-iOS/AllToDo/Resources/` 폴더로 복사합니다.

### 3단계: 확인
*   **서버**: 바로 반영됩니다. 앱들이 `/version` 체크를 통해 새 버전을 받아갑니다.
*   **앱**: 앱을 다시 빌드하거나 실행하면 내장된 새 WASM이 동작합니다.

---

## 💡 요약
> "안드로이드용, iOS용 코드를 따로 짤 필요가 없습니다. `lib.rs` 하나만 고치고 스크립트를 돌리면, **전 세계 모든 사용자(Android/iOS)**에게 새로운 로직이 적용됩니다."
