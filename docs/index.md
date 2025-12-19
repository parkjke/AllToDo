# AllToDo 문서 인덱스 (Documentation Index)

## 🧠 핵심 로직 및 알고리즘 (Core Logic)
*   [**알고리즘 및 로직 (ALGORITHMS)**](logic/ALGORITHMS.md)
    *   스마트 트래킹 (Smart Tracking): 정수 좌표 및 동적 임계값.
    *   스마트 테더링 (Smart Tethering): 지도 이탈 방지 로직.
    *   500km 필터링: 태평양 줌아웃 방지.
*   [**앱 실행 시나리오 (APP_LAUNCH_SCENARIO)**](logic/APP_LAUNCH_SCENARIO.md)
    *   Case A: 핀 없음 (내 위치 줌인).
    *   Case B: 핀 있음 (Fit Bounds -> 3초 -> User Zoom).
*   [**핵심 기술 및 IP (CORE_TECHNOLOGY_IP)**](logic/CORE_TECHNOLOGY_IP.md)
    *   WASM 하이브리드 아키텍처.
    *   좌표 정수화 (x100,000) 전략.
    *   RDP 경로 압축 알고리즘.

## 🎨 디자인 및 리소스 (Design & Resources)
*   [**맵 핀 디자인 명세서 (MAP_PIN_DESIGN)**](logic/MAP_PIN_DESIGN.md)
    *   핀 색상 규칙 (Red/Green/Blue).
    *   캐싱 전략 및 Parity 검증.
*   [**기능 적용 현황표 (FeatureStatus)**](logic/FeatureStatus.md)
    *   Android vs iOS 기능 대조표.

## 🪵 개발 로그 (Logs)
*   [**지도 최적화 로그 (MAP_OPTIMIZATION_LOG)**](history/MAP_OPTIMIZATION_LOG.md)
    *   주요 최적화 작업 이력 (RDP, WASM, Battery).
*   [**작업 로그 (Work Log)**](history/work_log.md)
    *   일일 작업 내역 요약.

