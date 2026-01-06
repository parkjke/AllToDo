# 지도 핀 로직 고도화 및 데이터 통합 설계 (Map Pin Logic & Data Integration Plan)

**문서 버전**: v1.2.0 (2025-12-28 Finalized)
**목표**: SVG 핀 렌더링 구조의 유연성 확보, DB 기반 핀 데이터 통합, 그리고 사용자 중심의 정교한 필터링 규칙 적용.

---

## 1. 개요 (Overview)
기존의 정적 핀 에셋 방식(결합형)을 탈피하여, **DB Type 코드**에 따라 **배경(Shield)**과 **마크(Icon)**를 동적으로 합성하는 **런타임 렌더링 시스템**으로 전환합니다. 또한, '현재 위치'를 특수한 Todo 항목으로 통합하여 데이터 관리의 일관성을 확보합니다.

---

## 2. 핀 디자인 시스템 (Pin Design System)

### 2.1. 컴포넌트 분리 (Component Separation)
핀을 두 개의 독립적인 레이어로 분리하여 관리합니다.

1.  **배경 (Shield)**: 3가지 색상의 그라데이션 방패 (SVG)
    *   `pin_shp_red.svg` (History / Current Loc)
    *   `pin_shp_green.svg` (Local Todo)
    *   `pin_shp_blue.svg` (Server Instruction)
2.  **마크 (Mark)**: 의미를 나타내는 내부 아이콘 (SVG)
    *   `mark_star`, `mark_check`, `mark_x` 등 (투명 배경)

### 2.2. 배치 및 규격 (Layout & Specification)
사용자 검증을 완료한 100x125 캔버스 기준 최적 배치 규격입니다.

| 항목 | 값/좌표 | 비고 |
| :--- | :--- | :--- |
| **Canvas** | 100 x 125 | 전체 SVG 뷰박스 |
| **Safe Area** | **50 x 45** | 마크가 배치될 최대 영역 |
| **Center** | **(50, 45)** | 마크의 중심점 (Anchor) |
| **Scale** | 1.0 (Max) | 꽉 찬 느낌 선호 시 사용 |

### 2.3. 제작 전략 (Asset Creation)
*   **AI 협업**: 디자이너 없이 AI 에이전트에게 이미지를 제공하면 즉시 최적화된 SVG Path 코드로 변환.
*   **자동 규격화**: 생성 시 위 50x45 Safe Area 규격을 자동으로 준수.

---

## 3. 데이터 구조 및 매핑 (Data Structure & Mapping)

### 3.1. Type 기반 식별 규칙 (Type Identification)
DB의 `type` 필드(2자리 문자열)가 핀의 정체성(Master Key)을 결정합니다.

*   **십의 자리 (1x)**: **배경색 (Background)** 결정.
    *   `0` -> Red, `1` -> Green, `2` -> Blue
*   **일의 자리 (x1)**: **마크 아이콘 (Icon)** 결정.
    *   `0`: Plan (기본), `1`: Missed/No-Path, `2`: Done/Exist-Path, `3`: Bookmark (Blue Star).

### 3.2. DB 스키마 연동 (Schema Integration)
*   **Todo Table**: `int_long`, `int_lat` (Integer, x100,000) 필드를 통해 위치 정보 확보.
*   **경로 데이터**: `no_of_path` (Integer) 필드로 경로 존재 여부 및 데이터 양 확인.

---

## 4. 렌더링 및 캐싱 로직 (Rendering & Caching)

### 4.1. 런타임 합성 (Runtime Compositing)
앱 구동 시(또는 필요 시) 메모리에서 `Shield`와 `Mark` 이미지를 겹쳐 비트맵을 생성합니다.
```swift
// Pseudo Code
let finalPin = combine(shield: "pin_shp_green", mark: "mark_check", center: CGPoint(50, 45))
```

### 4.2. 캐싱 전략 (Caching)
합성된 비트맵은 `ALLTODO_V7` 헤더와 함께 디스크에 영구 캐싱되어, 재실행 시 합성 비용 없이 즉시 로드됩니다.

---

## 5. 표시 및 필터링 규칙 (Display & Filtering Rules)

Todo Table에서 데이터를 조회할 때 적용되는 엄격한 5대 필터링 규칙입니다. (WASM 주입 전 적용)

1.  **데이터 유효성**: `no_of_path == 0`인 항목 제외.
2.  **시간 제한 (Time Window)**: 기준 시간(현재) **±24시간** 이내 항목만 표시.
3.  **거리 제한 (Distance)**: 내 위치 반경 **500km** 초과 시 제외 (Zoom-out 방지).
4.  **지역 제한 (Geo-Fencing)**: 한국 영역 밖의 핀은 카카오/네이버 지도에서 숨김 처리.
5.  **WASM 최적화**: 위 필터링을 통해 클러스터링 엔진 부하를 최소화.

---

## 6. 현재 위치 통합 전략 (Current Location Integration)
'내 위치'를 별도 객체가 아닌 표준 `TodoItem`으로 추상화하여 관리 일관성을 확보합니다.

*   **Type**: `00` (Red Shield + Current Mark)
*   **ID**: 0 (Fixed) 또는 임시 UUID
*   **갱신**: 이동 시 `no_of_path` 증가 및 좌표 Update (In-Memory).
*   **저장**: 앱 종료 시 `Type 01`(History)로 변환되어 DB에 영구 저장 (30분 자동 분할).

---

## 7. 최종 점검 및 상태 (Final Status)
| 구분 | 상태 | 결과 요약 |
| :--- | :--- | :--- |
| **Logic** | ✅ Ready | Type 기반 1:1 매핑 및 합성 설계 완료 |
| **Data** | ✅ Ready | DB 스키마 정의 및 필터링 규칙 확정 |
| **Asset** | ✅ Ready | 3색 Shield 및 규격화된 Mark 제작 준비 완료 |

> **Conclusion**: 시스템 설계가 완료되었으며, 구현(Implementation) 단계로 진입을 승인합니다.
