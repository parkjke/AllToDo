# 작업 로그: WASM 클러스터링 엔진 안정화 및 iOS 통합 (2025-12-19)

## 1. 개요
iOS 앱(Apple/Google/Naver/Kakao 지도)에 **WASM 클러스터링 엔진(v1.0.2)**을 완전히 통합하고, 초기화 오류 및 로그 노이즈를 해결함.
특히, 문서 상의 설계(`1e5` 스케일링)와 실제 코드 구현(`1e6` 스케일링) 간의 불일치를 수정하여 클러스터링 정확도를 확보함.

## 2. 주요 작업 내용

### 2.1. WASM 엔진 및 백엔드 (Server & Rust)
*   **RDP 함수 추가 (`lib.rs`)**:
    *   기존 WASM 모듈에 누락되었던 `compress_trajectory` 함수를 `wasm-bindgen`으로 노출하여 구현.
    *   경로 압축(RDP) 기능 활성화.
*   **버전 및 배포 수정 (`wasm.py`)**:
    *   WASM 파일을 `app/static/wasm/` 경로로 이동 및 배포.
    *   버전 확인 로직(`get_current_version`)이 `version.txt`를 읽도록 하고, 버전을 **1.0.2**로 강제 상향하여 클라이언트가 캐시된 구버전(1.0.1) 대신 새 버전을 다운로드하도록 유도.
*   **암호화 키 동기화**:
    *   서버 `.env` 키 불일치(CryptoKit Error 3) 해결을 위해 iOS와 동일한 하드코딩 키(`h5e...`)를 사용하도록 `wasm.py` 핫픽스 적용.

### 2.2. iOS 클라이언트 (Swift)
*   **좌표계 스케일링 정규화 (Critical Fix)**:
    *   기존 코드에서 좌표를 `1,000,000` (1e6)배 하여 WASM에 전달하고 있었으나, Rust 엔진은 `100,000` (1e5)배를 기대함.
    *   모든 MapView(`Apple`, `Google`, `Naver`, `Kakao`)의 입/출력 스케일링을 `100,000`으로 수정하여 문서(`CORE_TECHNOLOGY_IP.md`) 사양과 일치시킴.
*   **클러스터링 감도(Sensitivity) 복구**:
    *   WASM 셀 크기(`wasmCellSize`) 계수를 `300.0`에서 표준값인 **`100.0`**으로 원복.
*   **초기화 경합(Race Condition) 해결**:
    *   앱 실행 직후 WebView가 로딩되기 전에 클러스터링을 요청하여 발생하던 `ReferenceError: Can't find variable: cluster` 오류 수정.
    *   `WebViewWasmRuntime.swift`에 폴링(Polling) 방식의 `ensurePageLoaded()` 대기 로직 추가.
*   **JS 인터페이스 수정**:
    *   `compressTrajectory` 호출 시 인자 개수 불일치(3개 vs 2개)로 인한 `Invalid Result Type: null` 오류 수정.

### 2.3. UI 및 로그 정리
*   **로그 삭제**: `>>> Pins Loaded`, `>>> WASM Clustering ...`, `DEBUG: Total Todos`, `MOTION_CHANGE` 등 불필요한 콘솔 로그 전면 제거.
*   **UI 제거**: 우측 하단 "Cluster Radius" 디버그 인디케이터 제거.

## 3. 결과
*   모든 맵 서비스에서 WASM 기반 클러스터링이 정상 동작하며, 앱 실행 시 오류 로그 없이 깔끔하게 초기화됨.
*   경로 압축(RDP) 기능이 WASM을 통해 정상 수행됨.
