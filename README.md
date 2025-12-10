# AllToDo 📅🗺️

**AllToDo** is a map-based smart To-Do list and path logging application.
 intuitively manage your tasks on a map and record your daily movements to look back on your past activities.

![Project Status](https://img.shields.io/badge/Status-Active-success)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Web-blue)
![Backend](https://img.shields.io/badge/Backend-FastAPI-green)

---

## 🌍 Language
*   [English](#-english)
*   [한국어 (Korean)](#-korean)

---

## 🇬🇧 English

### ✨ Key Features

*   **📍 Map-based To-Do Management**
    *   Pin your tasks (To-Dos) directly onto the map at your current location or any specific place.
    *   Visualize your tasks geographically to plan efficient routes.
    *   Check off completed tasks with ease.

*   **👣 Path Logging & Time Travel**
    *   Automatically records your movement path in the background.
    *   **Time Travel:** Review your past routes and tasks on specific dates. "Where was I yesterday?"
    *   Paths are visualized with aesthetic red lines and pins.

*   **🔒 Privacy & Security**
    *   Personally Identifiable Information (PII) is securely encrypted and stored.
    *   Location data is used solely for your personal history and experience.

### 🏗️ Project Structure

This repository is a **Monorepo** containing source code for Android, iOS, and the Backend.

| Directory | Description | Stack |
| :--- | :--- | :--- |
| **`/AllToDo-Android`** | Android Client App | Kotlin, Jetpack Compose, Kakao Map SDK |
| **`/AllToDo-iOS`** | iOS Client App | Swift, SwiftUI, Naver Map SDK |
| **`/AllToDo-Backend`** | Server & API | Python, FastAPI, PostgreSQL |

### 🚀 Getting Started

#### 1. Backend (Server)
Built with Python FastAPI.
```bash
# Activate Virtual Environment
source AllToDo-Backend/.venv/bin/activate

# Install Dependencies
pip install -r AllToDo-Backend/requirements.txt

# Run Server (Port 8000)
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

#### 2. Android
Open `/AllToDo-Android` in Android Studio.
*   **Requirements:** Android SDK 24+, JDK 17
*   **Note:** Kakao Map API Key required in `local.properties`.

#### 3. iOS
Open `/AllToDo-iOS` in Xcode.
*   **Requirements:** iOS 16.0+, Xcode 15+
*   **Note:** Naver Map Client ID required.

---

## 🇰🇷 Korean

### ✨ 주요 기능

*   **📍 지도 기반 할 일 관리**
    *   현재 위치 또는 원하는 장소에 '할 일(To-Do)' 핀을 꽂아 메모할 수 있습니다.
    *   지도 위에서 할 일들의 위치를 한눈에 파악하고 효율적인 동선을 계획할 수 있습니다.

*   **👣 이동 경로 기록 (Path Logging)**
    *   앱이 백그라운드에 있어도 사용자의 이동 경로를 자동으로 기록합니다.
    *   **시간 여행(Time Travel):** 과거 특정 날짜의 이동 경로와 수행했던 할 일들을 지도에서 다시 확인할 수 있습니다.

*   **🔒 개인정보 보호 및 보안**
    *   사용자의 민감한 정보(PII)는 강력하게 암호화되어 서버에 저장됩니다.

### 🏗️ 프로젝트 구조

이 프로젝트는 Android, iOS, Backend 코드를 하나의 저장소에서 관리하는 **모노레포(Monorepo)** 구조입니다.

| 폴더명 | 설명 | 기술 스택 |
| :--- | :--- | :--- |
| **`/AllToDo-Android`** | 안드로이드 앱 | Kotlin, Jetpack Compose, Kakao Map SDK |
| **`/AllToDo-iOS`** | iOS 앱 | Swift, SwiftUI, Naver Map SDK |
| **`/AllToDo-Backend`** | 백엔드 서버 | Python, FastAPI, PostgreSQL |

---

## 📝 License
This project is for personal use and portfolio demonstration.
