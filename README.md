# AllToDo 📅🗺️

**AllToDo**는 지도 기반의 스마트 할 일 관리(To-Do) 및 이동 경로 기록 애플리케이션입니다.
지도를 통해 할 일을 직관적으로 관리하고, 나의 하루 이동 경로를 기록하여 과거를 되돌아볼 수 있습니다.

![Project Status](https://img.shields.io/badge/Status-Active-success)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Web-blue)
![Backend](https://img.shields.io/badge/Backend-FastAPI-green)

---

## ✨ 주요 기능 (Key Features)

*   **📍 지도 기반 할 일 관리**
    *   현재 위치 또는 원하는 장소에 '할 일(To-Do)' 핀을 꽂아 메모할 수 있습니다.
    *   지도 위에서 할 일들의 위치를 한눈에 파악하고 효율적인 동선을 계획할 수 있습니다.
    *   완료된 일은 체크박스로 간단히 정리합니다.

*   **👣 이동 경로 기록 (Path Logging)**
    *   앱이 백그라운드에 있어도 사용자의 이동 경로를 자동으로 기록합니다.
    *   **시간 여행(Time Travel):** "어제 내가 어디 갔었지?" 과거 특정 날짜의 이동 경로와 수행한 일들을 지도에서 다시 확인할 수 있습니다.
    *   빨간색 선과 핀으로 이동 경로가 시각적으로 아름답게 표시됩니다.

*   **🔒 개인정보 보호 및 보안**
    *   사용자의 민감한 정보(PII)는 강력하게 암호화되어 서버에 저장됩니다.
    *   위치 데이터는 오직 사용자 본인의 기록 확인 용도로만 사용됩니다.

---

## 🏗️ 프로젝트 구조 (Project Structure)

이 프로젝트는 **Android**, **iOS**, **Backend** 코드를 하나의 저장소에서 관리하는 모노레포(Monorepo) 구조입니다.

| 폴더명 | 설명 | 기술 스택 |
| :--- | :--- | :--- |
| **`/AllToDo-Android`** | 안드로이드 앱 | Kotlin, Jetpack Compose, Kakao Map SDK |
| **`/AllToDo-iOS`** | iOS 앱 | Swift, SwiftUI, Naver Map SDK |
| **`/AllToDo-Backend`** | 백엔드 서버 | Python, FastAPI, PostgreSQL |

---

## 🚀 시작하기 (Getting Started)

### 1. 백엔드 (Server)
서버는 Python FastAPI로 구축되어 있습니다.

```bash
# 가상환경 활성화 (MacOS/Linux)
source AllToDo-Backend/.venv/bin/activate

# 필수 라이브러리 설치
pip install -r AllToDo-Backend/requirements.txt

# 서버 실행 (포트 8000)
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### 2. 안드로이드 (Android)
Android Studio에서 `/AllToDo-Android` 폴더를 열고 빌드하세요.
*   **필수 요건:** Android SDK 24 이상, JDK 17
*   **참고:** `local.properties` 파일에 카카오맵 API 키 설정이 필요할 수 있습니다.

### 3. 아이폰 (iOS)
Xcode에서 `/AllToDo-iOS` 폴더를 열고 빌드하세요.
*   **필수 요건:** iOS 16.0 이상, Xcode 15 이상
*   **참고:** 네이버 지도 Client ID 설정이 필요합니다.

---

## 🛠️ 기술 스택 (Tech Stack)

*   **Mobile:** Kotlin (Android), Swift (iOS)
*   **UI Framework:** Jetpack Compose (Android), SwiftUI (iOS)
*   **Maps:** Kakao Map API (Android), Naver Map API (iOS)
*   **Server:** FastAPI (Python)
*   **Database:** PostgreSQL (운영), SQLite (개발)

---

## 📝 라이선스
이 프로젝트는 개인 포트폴리오 및 학습 목적으로 제작되었습니다.
