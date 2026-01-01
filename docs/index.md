# AllToDo Index

## ⚡️ 개발 시작하기 (Quick Start)
**VS Code에서 모든 개발 서버를 한 번에 실행하는 가장 편한 방법입니다.**

1.  `Command` + `Shift` + `P` 를 눌러 **명령 팔레트**를 엽니다.
2.  `Run Task` 를 입력하고 엔터.
3.  **`🚀 Start All Dev`** 를 선택하세요.

> [!TIP]
> **터미널 앱을 따로 쓰고 싶다면?**
> 프로젝트 루트에서 `./dev-begin.sh` 명령어를 실행해도 똑같이 4개의 서버가 새 창에서 열립니다.

## 🤖 협업 및 보안 (Collaboration & Security)
*   [**에이전트 작업 수칙 (AGENT_WORK_RULES)**](AGENT_WORK_RULES.md)
    *   작업 승인 절차 (IP + [Proceed]), 한국어 소통 원칙, 수정 금지 구역 준수.
*   [**코드 수정 금지 구역 (CRITICAL_CODE_LOCKS)**](CRITICAL_CODE_LOCKS.md)
    *   안정성이 검증되어 에이전트의 임의 수정이 금지된 핵심 코드 섹션 목록.

## 🧠 핵심 로직 및 알고리즘 (Core Logic)
### 🗺️ 지도 엔진 및 추적 (Map & Tracking)
*   [**지도 정의 로직 (MAP_DEFINITION_LOGICS)**](logic/map_definition_logics.md)
    *   스마트 트래킹, 스마트 테더링, 500km 필터링 등 핵심 알고리즘.
*   [**맵 시작 시퀀스 (Map Begin Logic)**](logic/map_begin_logic.md)
    *   3단계 초기화 로직 (Zoom 15 -> Fit Bounds -> User Zoom 18).
*   [**앱 실행 시나리오 (APP_LAUNCH_SCENARIO)**](logic/APP_LAUNCH_SCENARIO.md)
    *   핀 유무에 따른 동적 초기화 및 테더링 활성화 시나리오.
*   [**물풍선(Callout) 디자인 및 로직**](logic/water_balloon_logic.md)
    *   인터랙티브 프리미엄 오버레이 및 중앙 정렬 로직.

### ⚙️ 시스템 아키텍처 (System Architecture)
*   [**핵심 기술 및 IP (CORE_TECHNOLOGY_IP)**](logic/CORE_TECHNOLOGY_IP.md)
    *   WASM 하이브리드 아키텍처, 좌표 정수화 전략, RDP 경로 압축.
*   [**데이터베이스 구조 명세서 (DB Schema)**](logic/database_schema.md)
    *   DB 테이블 상세 구조 및 [**개체 관계도 (ERD)**](logic/erd.md).

### ✨ 기능 및 사용자 경험 (Features & UX)
*   [**할 일 만들기 디자인 및 로직 (Create Todo)**](logic/create_todo.md)
    *   롱터치 위치 기반 핀 생성 및 스마트 추천 UI.
*   [**한글 초성 검색 로직 (Korean Search)**](logic/korean_search.md)
    *   유니코드 기반 초성 매칭 알고리즘.

## 🎨 디자인 및 리소스 (Design & Resources)
*   [**맵 핀 디자인 명세서 (MAP_PIN_DESIGN)**](logic/MAP_PIN_DESIGN.md)
    *   **[최신]** V7 캐시, 뱃지 3pt 하향 조정, 엔진별 뱃지 크기 동기화.
*   [**지도 핀 로직 개선 계획 (MAP_PIN_LOGIC_IMPROVEMENT)**](logic/map_pin_logic_improvement_plan.md)
    *   Snake Case 기반 통합 에셋 키 및 타입 치환 테이블.
*   [**기능 적용 현황표 (Feature Status)**](logic/FeatureStatus.md)
    *   Android vs iOS 플랫폼 간 기능 대조표.

## 🪵 최근 작업 히스토리 (Recent Activity & Tasks)
> [!NOTE]
> Antigravity AI 에이전트를 통해 진행 중인 최신 작업 산출물입니다.

*   [**현재 작업 요약 (Walkthrough)**](antigravity_logs/WORK_LOG_WALKTHROUGH.md)
*   [**세부 작업 체크리스트 (Task)**](antigravity_logs/WORK_LOG_TASK.md)
*   [**최근 구현 계획서 (Plan)**](antigravity_logs/WORK_LOG_PLAN.md)
*   [**코드 포팅 및 환경 복구 기록 (Porting)**](antigravity_logs/WORK_LOG_PORTING.md)

## ⏳ 프로젝트 변경 이력 (Project History Archive)
### 🗺️ 주요 플랫폼 통합 기록
*   [**네이버맵 안드로이드 통합 사례 (Case Study)**](plans/NAVER_MAP_ANDROID_CASE_STUDY.md)
*   [**안드로이드 스마트 테더링 구현 일지 (Diary)**](plans/android_tethering_diary.md)
*   [**구현 계획 아카이브 (Old Plan)**](plans/NAVER_MAP_ANDROID_INTEGRATION.md)

### 📂 과거 워크스루 및 로그 (Archived Logs)
*   [**과거 히스토리 인덱스 (History Index)**](history/INDEX.md)
*   [**지도 최적화 로그 (Optimization Log)**](history/MAP_OPTIMIZATION_LOG.md)
*   [**iOS 지도 리팩토링 보고서 (2025-12-10)**](history/ios_map_refactor_walkthrough.md)
*   [**안드로이드 지도 복구 보고서 (2025-12-09)**](history/android_map_repair_walkthrough.md)
*   [**에셋 표준화 워크스루 (2025-12-11)**](history/map_pin_walkthrough_20251212.md)
*   [**WASM 리팩토링 작업 로그 (2025-12-19)**](history/WORK_LOG_20251219_WASM_REFACTOR.md)
*   [**앱 ID 마이그레이션 작업 로그**](history/WORK_LOG_APP_ID_MIGRATION.md)
*   [**지도 인터랙션 개선 로그**](history/WORK_LOG_MAP_INTERACTION.md)
*   [**iOS 로깅 및 백엔드 수정 (2025-12-13)**](history/20251213_ios_logging_and_backend_fix.md)
*   [**안드로이드 핀 렌더링 수정 로그 (2025-12-20)**](history/WORK_LOG_20251220_ANDROID_FIXES.md)
*   [**랜딩 페이지 작업 로그**](history/WORK_LOG_LANDING_PAGE.md)
*   [**일일 작업 내역 아카이브 (work_log.md)**](history/work_log.md)

## 📁 전체 프로젝트 개요 (Readme)
*   [**메인 README (Root)**](../README.md)
    *   전체 AllToDo 프로젝트 구조 및 실행 가이드


## 📘 프로젝트별 매뉴얼 (Component Manuals)
각 컴포넌트의 상세 실행 방법 및 개발 가이드는 아래 링크를 참조하세요.

*   [**🏠 홈페이지 (HomePage)**](../AllToDo-HomePage/README.md)
    *   서비스 소개 랜딩 페이지 (Vite + GitHub Pages)
*   [**🔙 백엔드 (Backend)**](../AllToDo-Backend/README.md)
    *   Python FastAPI 서버 및 DB 관리
*   [**📱 웹 앱 (WebApp)**](../AllToDo-WebApp/README.md)
    *   사용자용 모바일 웹 애플리케이션
*   [**🖥️ 관리자 웹 (WebMng)**](../AllToDo-WebMng/README.md)
    *   데이터 관리 및 운영자용 대시보드
