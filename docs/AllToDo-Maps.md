# AllToDo Unified Map System (통합 지도 시스템)

**작성일**: 2026-01-03  
**버전**: 2.0 (Geospatial Perfection)

이 문서는 AllToDo 서비스의 핵심인 지도 시스템의 설계 철학, 기술적 규격, UX 로직, 그리고 유지보수 가이드를 집대성한 종합 기술 문서(Master Documentation)입니다. iOS와 Android 양대 플랫폼에서 Apple, Google, Naver, Kakao 등 4대 지도 엔진을 단일한 사용자 경험으로 통합하기 위한 모든 지식이 담겨 있습니다.

---

## 1. 핵심 철학 (Core Philosophy)

### 1.1 Multi-Engine, Single Experience
사용자가 어떤 지도를 선택하든(**Google, Naver, Kakao, Apple**) 앱의 동작 방식, 핀의 모양, 반응 속도, 클릭 감각은 완전히 동일해야 합니다. SDK 고유의 특성을 숨기고 AllToDo만의 추상화된 경험(Abstracted Experience)을 제공하는 것이 목표입니다.

### 1.2 Integer Precision (정수 좌표계)
부동 소수점(Floating Point) 오차로 인한 마커 떨림과 연산 불일치를 막기 위해, 내부적으로 **100,000 스케일의 정수 좌표계(Integer Coordinate System)**를 사용합니다.
- **Lat/Lon**: `Double * 100,000` -> `Int` (약 1.1m 정밀도)
- **장점**: 기하 연산 속도 5배 향상 및 크로스 플랫폼 데이터 동기화 보장.

### 1.3 Properties First (속성 우선 할당)
지도에 핀을 그리기(Add) 전에 '위치', '아이콘', '앵커' 등 모든 속성을 완벽하게 확정합니다. 불완전한 객체가 렌더링 루프에 진입하여 발생하는 '깜빡임'이나 '크래시'를 원천 차단합니다.

---

## 2. 핀 디자인 및 렌더링 (Pin Engineering)

### 2.1 핀 규격 (Specification)
- **Visual Size**: 40dp x 50dp (사용자 눈에 보이는 크기)
- **Canvas Size**: 50dp x 60dp (뱃지 및 여백 포함)
- **Badge**:
    - **위치**: 우측 상단으로 튀어나온(Overhang) 입체적 디자인.
    - **Overhang**: +11dp (상단/우측 확장)
- **Anchor Point**: **(0.392f, 1.0f)**
    - **중요**: 캔버스 확장에 따라 기하학적 중심이 이동했으므로, 핀의 뾰족한 끝(Tip)이 정확한 좌표를 가리키기 위해 `0.5`가 아닌 `0.392`를 사용합니다. **(수정 금지)**

### 2.2 핀 종류 및 우선순위
1.  **🔴 Red (위치/기록)**: 현재 위치(User), 지나간 경로(History). **[최우선]**
2.  **🔵 Blue (서버/수신)**: 타인으로부터 받은 할 일.
3.  **🟢 Green (로컬/자작)**: 내가 만든 할 일 (완료된 할 일 포함).

---

## 3. 물풍선 시스템 (Callout System)

핀을 클릭했을 때 나타나는 정보 창(물풍선)은 OS의 제약을 뛰어넘어 커스텀 렌더링된 컴포넌트입니다.

### 3.1 53dp 정밀 오프셋 (Precision Offset)
핀을 선택하면 지도가 이동하는데, 단순히 중앙에 두는 것이 아니라 **핀의 머리가 화면에 잘리지 않고 물풍선 꼬리가 정중앙에 오도록** 정교하게 계산된 위치로 이동합니다.
- **Offset Value**: 화면 중앙 + **53dp** (하방 이동)
- **구현**: iOS(`centerOffset`), Android(`Projecton.toScreenLocation`) 양쪽에서 픽셀 단위로 일치시킴.

### 3.2 디자인 (Look & Feel)
- **배경**: `AllToDoGreen` (#28CD41) + **80% Alpha** (Glassmorphism)
- **구조**: Header(닫기) + Body(리스트) + Footer(Tail)
- **인터랙션**: 12개 이상의 아이템도 내부 스크롤로 처리하며, 탭 즉시 반응(Instant Display) 기술 적용.

---

## 4. 지도 시작 시퀀스 (3-Stage Launch Sequence)

앱 실행 시 지도가 뜨는 과정을 3단계로 정형화하여 "빠르고 부드러운" 느낌을 줍니다. 시각적 점프(Jump)를 없애는 것이 핵심입니다.

### 🆕 Stage 1: Fast Jump (즉시 렌더링)
- **동작**: 저장된 마지막 위치(Before Location)와 **줌 15.0**으로 엔진을 강제 초기화.
- **효과**: 로딩 없이 즉시 "아는 장소"를 보여줌.

### ↔️ Stage 2: Fit Bounds (분포 파악)
- **동작**: 500km 이내의 유효 핀들을 모두 포함하는 영역(Bounds)으로 이동.
- **특징**: **클러스터링 OFF**. 모든 핀을 개별적으로(Raw) 보여주며 3초간 대기. 사용자가 전체적인 분포를 "훑어볼" 시간을 줌.
- **줌 락(Zoom Lock)**: 최대 줌을 15.0으로 제한하여 과도한 확대 방지.

### 🎯 Stage 3: User Focus (사용자 집중)
- **동작**: 3초 후, 현재 내 위치로 **줌 18.0**으로 부드럽게 진입.
- **특징**: **클러스터링 ON**. 핀들이 깔끔하게 묶이며 탐색 준비 완료.
- **테더링 시작**: 이때부터 지도가 내 위치를 따라다니는 '스마트 테더링' 모드 활성화.

---

## 5. 핵심 기술 로직 (Core Logics)

### 5.1 스마트 테더링 (Smart Tethering)
무조건 내 위치를 중앙에 고정하지 않습니다.
- **Anchor**: 지도가 잡고 있는 기준점.
- **Rule**: 내 위치가 화면의 **[25% ~ 75%]** 영역(Safe Zone)을 벗어날 때만 부드럽게 중앙으로 복귀.
- **장점**: 사용자가 지도를 조금씩 움직이며 탐색할 때 방해하지 않음.

### 5.2 WASM 클러스터링 & 4단계 스무딩
대량의 핀을 처리하기 위해 Rust 기반의 **WebAssembly(WASM)** 엔진을 사용합니다.
- **기준**: 줌 레벨이 아닌 **실제 화면 거리(Screen Width Meters)** 기반.
- **Smoothing**: 클러스터가 풀리거나 묶일 때 [생성 -> 합병 -> 정리 -> 최신화]의 4단계를 거쳐 "깜빡임 없는" 전환 구현.

### 5.3 대한민국 파티셔닝 (Korea Rule)
- **Global Map (Google/Apple)**: 전 세계 모든 핀 표시.
- **Local Map (Naver/Kakao)**: **대한민국 영토 내**의 핀만 기본 표시. 해외 핀이 있으면 "먼 곳에 핀이 있습니다" 버튼 노출.

---

## 6. 유지보수 및 금지 구역 (Maintenance & Locks)

### 🚨 CRITICAL LOCKS (수정 금지)
다음 로직은 수천 번의 튜닝을 거친 결과물이므로 **절대 임의 수정해서는 안 됩니다.**

1.  **Multi-Engine Geospatial Integrity**: 4대 엔진의 핀 앵커(**0.392f**)와 센터링 오프셋(**53dp**). 미세하게라도 건드리면 플랫폼 간 정합성이 깨집니다.
2.  **Raw First Strategy**: 초기 실행 시 클러스터링을 끄고 Raw 핀을 먼저 보여주는 로직.
3.  **Background 5-sec Rule**: 백그라운드 5초 경과 시 재부팅 시퀀스(Re-Launch)를 타는 로직. (OpenGL 컨텍스트 유실 방지)

### ⚠️ 작업 수칙
- **지도 엔진 보호 정책**: `Apple`, `Google`, `Naver`, `Kakao` 엔진 코드를 **한 번에(Batch)** 리팩토링하지 마십시오. 반드시 **한 번에 하나의 엔진**만 수정하고 검증해야 합니다.

---

**관련 문서**:
- [CRITICAL_CODE_LOCKS.md](CRITICAL_CODE_LOCKS.md)
- [logic/map_begin_logic.md](logic/map_begin_logic.md)
- [logic/water_balloon_logic.md](logic/water_balloon_logic.md)
- [logic/MAP_PIN_DESIGN.md](logic/MAP_PIN_DESIGN.md)
