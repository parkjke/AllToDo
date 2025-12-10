# AllToDo 📅🗺️

**AllToDo**는 지도 기반의 스마트 할 일 관리(To-Do) 및 이동 경로 기록 애플리케이션입니다.
내가 해야 할 일을 지도 위의 핀으로 확인하고, 나의 이동 경로를 기록하여 과거의 활동을 되돌아볼 수 있습니다.

![Project Status](https://img.shields.io/badge/Status-Active-success)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Web-blue)
![Backend](https://img.shields.io/badge/Backend-FastAPI-green)

---

## ✨ Key Features (주요 기능)

*   **📍 Map-based To-Do (지도 기반 할 일 관리)**
    *   현재 위치 또는 지도상 특정 위치에 할 일(To-Do)을 핀으로 등록합니다.
    *   지도에서 직관적으로 할 일의 위치를 확인하고 관리할 수 있습니다.
    *   완료된 할 일을 체크하고 관리합니다.

*   **👣 Path Logging (이동 경로 기록)**
    *   백그라운드에서 사용자의 이동 경로를 기록합니다.
    *   **Time Travel:** 과거 특정 날짜의 이동 경로와 수행했던 할 일들을 지도에서 다시 볼 수 있습니다.
    *   이동 경로는 빨간색 선과 핀으로 시각화되어 제공됩니다.

*   **🔒 Privacy & Security (개인정보 보호)**
    *   개인 식별 정보(PII)는 암호화되어 안전하게 관리됩니다.
    *   위치 데이터는 사용자 경험 향상을 위해서만 사용됩니다.

---

## 🏗️ Project Structure (프로젝트 구조)

이 저장소는 **Android**, **iOS**, **Backend** 코드를 모두 포함하는 모노레포(Monorepo)입니다.

| Directory | Description | Stack |
| :--- | :--- | :--- |
| **`/AllToDo-Android`** | 안드로이드 클라이언트 앱 | Kotlin, Jetpack Compose, Kakao Map SDK |
| **`/AllToDo-iOS`** | iOS 클라이언트 앱 | Swift, SwiftUI, Naver Map SDK |
| **`/AllToDo-Backend`** | 서버 및 API | Python, FastAPI, PostgreSQL |

---

## 🚀 Getting Started

### 1. Backend (Server)
서버는 Python FastAPI로 작성되었습니다.

```bash
# 가상환경 활성화 (MacOS/Linux)
source AllToDo-Backend/.venv/bin/activate

# 의존성 설치
pip install -r AllToDo-Backend/requirements.txt

# 서버 실행 (Port 8000)
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### 2. Android
Android Studio에서 `/AllToDo-Android` 폴더를 열어 빌드합니다.
*   **Requirements:** Android SDK 24+, JDK 17
*   **Map Key:** `local.properties`에 Kakao Map API Key 설정이 필요할 수 있습니다.

### 3. iOS
Xcode에서 `/AllToDo-iOS` 폴더(또는 `.xcodeproj`)를 열어 빌드합니다.
*   **Requirements:** iOS 16.0+, Xcode 15+
*   **Map Key:** Naver Map Client ID 설정이 필요합니다.

---

## 🛠️ Tech Stack

*   **Mobile:** Kotlin (Android), Swift (iOS)
*   **UI Framework:** Jetpack Compose, SwiftUI
*   **Maps:** Kakao Map API (Android), Naver Map API (iOS)
*   **Server:** FastAPI (Python)
*   **Database:** PostgreSQL (Production), SQLite (Dev)

---

## 📝 License
This project is for personal use and portfolio demonstration.
