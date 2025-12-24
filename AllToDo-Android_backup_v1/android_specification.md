# AllToDo Android Native (Kotlin) Specification

## 1. Project Overview
- **App Name**: AllToDo
- **Platform**: Android Native
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Recommended) or XML View System
- **Map Provider**: Naver Map SDK or Kakao Map SDK (Android Native Version)
    - *Current Web Implementation uses Kakao Maps.*

## 2. Core Features & Architecture

### A. Data Layer (Room Database)
An internal database is required to replace the current `localStorage` mock.
- **Entity**: [TodoItem](file:///Volumes/Work/AllToDo/AllToDo-WebApp/src/components/TodoDrawer.tsx#97-149)
    - [id](file:///Volumes/Work/AllToDo/AllToDo-WebApp/src/context/TodoContext.tsx#27-94): UUID (String)
    - [text](file:///Volumes/Work/AllToDo/AllToDo-WebApp/src/context/TodoContext.tsx#16-24): String
    - `completed`: Boolean
    - `createdAt`: Long (Timestamp)
    - `source`: String enum (`"local"`, `"external"`)
    - `latitude`: Double? (Nullable)
    - `longitude`: Double? (Nullable)
- **DAO**: `TodoDao`
    - `getAll()`: Flow<List<TodoItem>>
    - `insert(item)`
    - `update(item)`
    - [delete(item)](file:///Volumes/Work/AllToDo/AllToDo-WebApp/src/context/TodoContext.tsx#74-77)

### B. Map Feature (MainActivity)
- **Full Screen Map**: The map should occupy the entire background.
- **User Location**:
    - Permission: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
    - Logic: Track and update user location in real-time.
    - **Custom Marker**: A "Tear Drop" shape marker for the current location.
        - **Pulse Effect**: A ping animation around the user marker (Green).
        - **Direction Indicator**: Optional (bearing).

- **Todo Markers (Overlay)**:
    - Display markers for all valid `latitude`/`longitude` tasks.
    - **Color Logic**:
        - 🔴 **Red**: `source == "external"` (Not completed)
        - 🔵 **Blue**: `source == "local"` (and has location) (Not completed)
        - ⚪ **Gray (Faded)**: Completed items.
    - **Interaction**: Clicking a marker should show a info window or toast with the task text.

- **Map Controls (Overlay UI)**:
    - **Location**: Button to center map on user.
    - **Zoom In/Out**: +/- buttons.
    - **Icons**: Black icons with White/Glass background.

### C. UI Components (Overlays)

#### 1. Top Left Widget ("To Do")
- **Layout**: Floating Card (Glassmorphism effect if possible, or semi-transparent White/Green).
- **Content**:
    - **Title**: "To Do" (Dark Green text).
    - **Stats**: 3 Circular badges (🔴 Red Count, 🔵 Blue Count, ⚫ Black Count).
    - **List**: Top 3 active tasks (Text: Black).
    - **Resize Handle**: Bottom-right corner to resize the widget (Touch drag).
- **Interaction**:
    - Clicking 'Map Icon' cycles through task locations on the map.
    - Clicking the widget opens the "To Do List" Drawer.

#### 2. Bottom "To Do List" Drawer (BottomSheet)
- **Behavior**: Draggable BottomSheet or Slide-up Panel.
- **Header**: "To Do List" (Dark Green), Add `[+]` button, Sort `[Clock/Group]` button.
- **List Items**:
    - **Text**: Black (Active), Dark Gray + Strikethrough (Completed).
    - **Checkbox**: Custom thick border design.
        - External: Red border.
        - Local: Black border.
    - **Icons**: Map Pin (opens map location), Time (Text).
- **Add Task Modal**:
    - Fields: Task Name, Participant (Contact), Location (Map Search), Date/Time.

#### 3. My Info / Settings Drawer
- **Access**: Top Right User Icon.
- **Content**:
    - User Profile (Placeholder).
    - **Map Settings**:
        - **Pin Size Selector**: Small / Medium / Large.
        - Logic: Updates the size of the Location Marker on the map.

## 3. Technical Requirements

### Libraries
| Component | Android Library Recommendation |
| :--- | :--- |
| **UI** | Jetpack Compose (Material3) |
| **Async** | Kotlin Coroutines & Flow |
| **DI** | Hilt (Dagger) |
| **Navigation** | Jetpack Navigation Compose |
| **Maps** | Naver Map SDK for Android (preferred in Korea) or Google Maps SDK |
| **Database** | Room |
| **Location** | Play Services Location |

### Development Estimation
- **Total Estimated Time**: 2 ~ 3 Weeks (for a single senior developer)
    - **Week 1**: Project Setup, Room Database, Map SDK Integration, Basic Location Logic.
    - **Week 2**: UI Implementation (Widgets, Drawers, BottomSheet), Custom Markers, Styling.
    - **Week 3**: Refinement, Animation (Pulse), Resize Logic, Device Testing.

## 4. Migration Checklist
- [ ] Setup Android Project (Min SDK 26+).
- [ ] Add Permissions (`INTERNET`, `ACCESS_FINE_LOCATION`).
- [ ] Implement `Room` Database for Todo items.
- [ ] Integrate Map SDK and display "Current Location".
- [ ] Implement `CustomMarker` rendering logic.
- [ ] Build `TopLeftWidget` with drag-resize gesture support.
- [ ] Build `TodoBottomSheet` with sorting and adding logic.
- [ ] Verify "Green Theme" colors matching the Web App.
