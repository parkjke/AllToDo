# 맵 핀 디자인 명세서 (Map Pin Design Specifications)

## 1. 디자인 개요 (Design Overview)
*   **기본 형태**: 방패 모양 (Shield/Badge) - `Icons/map-pin-gemini.svg` 기반.
*   **디자인 목표**: Android (Google, Naver, Kakao) 및 iOS (Apple, Google, Naver, Kakao) 간 통일된 시각적 언어 구축.
*   **에셋 타입**: SVG (벡터) - 확장성 및 색상 변경 용이.

## 2. 핀 종류 및 로직 (Pin Types & Logic)

### A. ToDo 핀 (계획)
*   **색상**: **녹색 (Green)** (여름 녹음 / 애플 지도 스타일).
*   **용도**: 사용자가 계획한 장소 표시.
*   **상태 및 변화 (Variations)**:
    1.  **준비 (Ready)**: 실행 전.
    2.  **완료 (Done)**: 할 일 완료.
    3.  **취소 (Cancel)**: 사용자 취소.
    4.  **포기/실패 (Fail/Drop)**: 실행 포기 또는 실패.

### B. History 핀 (기록)
*   **색상**: **빨강 (Red)** (시스템 레드 / 애플 지도 스타일).
*   **용도**: 사용자가 앱을 실행했다가 나갔을 때 있었던 위치 (자취).
*   **아이콘**: ⭐ 별 (Star) - 기록된 장소(Badge) 상징.
*   **상태**: 정적 (변화 없음).

### C. Current Location 핀 (현재 위치)
*   **색상**: **빨강 (Red)** (History와 동일).
*   **로직**: 현재 위치는 결국 시간이 지나면 History가 되므로 동일한 색상 테마 사용.
*   **아이콘**: **미정 (TBD)**. '현재'임을 나타내기 위해 History(별)와는 구별 필요.
*   **상태**: 정적.

### D. Receive Location 핀 (서버 지시)
*   **색상**: **파랑 (Blue)** (시스템 블루 / 애플 지도 스타일).
*   **용도**: 서버로부터 받은 지시 사항 또는 위치.
*   **상태 및 변화 (Variations)**:
    1.  **준비 (Ready)**: 지시 수신, 수행 대기.
    2.  **완료 (Done)**: 수행 완료.
    3.  **거부 (Reject)**: 수행 거부.

## 3. 구현 계획 (Implementation Plan)
1.  **에셋 생성**: 상태별 개별 SVG 파일 생성 (예: `pin_todo_ready.svg`, `pin_receive_reject.svg`).
2.  **아이콘화**: 상태를 나타내는 내부 아이콘(체크, X, 슬래시 등) 디자인 추후 결정.
3.  **Android 통합**: `VectorDrawable` 사용 또는 필요 시 PNG 변환.
4.  **iOS 통합**: 에셋 카탈로그 내 `SVG` (Single Scale) 또는 PDF/PNG 사용.

## 4. 참고 자료 (Reference)
*   **원본 파일**: `/Icons/map-pin-gemini.svg`
*   **색상 참조**: 애플 지도 구현 리소스에서 정확한 Hex 코드 추출 필요.
