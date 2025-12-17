# CRITICAL PROTOCOL: REPORT ONLY, DO NOT ACT WITHOUT INSTRUCTION
**ALWAYS verify and report findings first. DO NOT modify code without explicit user permission.**
**This instruction persists across sessions.**

# Android Map Interaction & Debugging Log

## Summary of Fixes (2025-12-14 Session)

### 1. **Naver Map Integration**
-   **Client ID Configuration**: Confirmed correct Client ID.
    -   **Issue**: Initial confusion about which Client ID was valid, leading to 401 errors.
    -   **Resolution**: Reverted `com.naver.maps.map.CLIENT_ID` to **`i7652syq10`**. The 401 error was likely due to temporary configuration lag or package name registration issues on the console side, which the user verified.
    -   **Status**: Naver Map is now **displayed correctly**.

## Summary of Fixes (2025-12-13 Session)

### 1. **Location Tracking & Permissions**
-   **Infinite Loading Fix**: Resolved the issue where the app was stuck in a loading state. The root cause was `LaunchedEffect` not detecting the permission grant immediately.
    -   **Fix**: Introduced `hasLocationPermission` state variable using `mutableStateOf`. The permission launcher now updates this state, which directly triggers the location tracking `LaunchedEffect`.
-   **Blue Pin Removal**: Disabled the native Google Map "My Location" layer (`isMyLocationEnabled = false`) to prevent the default blue dot from overlapping with our custom Red Pin.

### 2. **WASM Clustering Stability**
-   **Data Loss Fix**: Fixed the issue where pins disappeared ("count=0") despite valid data entering the ViewModel.
    -   **Root Cause**: The WASM clustering module was failing (or returning empty) and the app had no fallback.
    -   **Fix**: Implemented a robust **Fallback Mechanism**. If WASM returns empty/fails but input points exist, the app automatically switches to a "1-to-1 Mapping" mode, ensuring all pins are displayed.

### 3. **Map Animation & Interaction**
-   **Infinite Zoom Loop Fix**: Resolved the critical bug where the map would repeatedly force-zoom to the current location on every update.
    -   **Fix**: Hoisted the `initialAnimationDone` state to `MainScreen` (parent) from `GoogleMapContent`. Also refactored the animation logic to run exactly once per session using a `while(true)` loop to wait for data, execute the sequence, and then break.
-   **Zoom Level Adjustment**: Adjusted the initial "Show All" view to target approximately **Zoom Level 11** (changed `MIN_SPAN` from 1.5 to 0.4) to avoid showing the map from too far away.

### 4. **Visual Consistency (iOS Parity)**
-   **Pin Sizing**: Adjusted pin sizes to match the iOS implementation exactly.
    -   **Spec**: **40x50** (width x height) for both Cluster and Single pins.
    -   **Budge**: Radius 10.
-   **Pin Distortion Fix**: Fixed the rendering logic in `createClusterBitmap` where pin images were being stretched/distorted.
    -   **Fix**: Implemented aspect-ratio respecting scaling. The pin image is now centered and fitted within the 40x50 box without distortion.

### Step 1: 전체 뷰 (Whole View) - [즉시 실행]
- 앱이 켜지자마자 내 주변(500km 이내)의 모든 핀/경로가 한 화면에 들어오도록 줌을 맞춥니다.
- **핵심 기술 (Dynamic Filter)**:
  - **초기 필터링 (Status: ON)**: 초기 Bounds 계산 시 **500km 밖의 핀(예: 베이징, 미국)은 제외**합니다. 이를 통해 지도가 대륙 단위로 줌아웃되는 것을 방지하고 대한민국/현재위치 주변에 집중합니다.
  - **최대 줌 제한**: 핀이 하나밖에 없거나 너무 가까이 몰려 있어 구글/네이버 지도가 자동으로 20레벨까지 확대하려 할 때, **최소 15레벨**까지만 확대되도록(너무 가까워지는 것을 방지) 제한합니다. (if zoom > 15 then zoom = 15)

### Step 2: 감상 대기 (Wait) - [3초]
- 사용자가 전체적인 할 일 분포를 눈으로 확인할 수 있도록 **3초간 화면을 유지**합니다.
- 이때 500km 밖에 핀이 있다면, 상단에 **"N개의 할 일이 멀리 있습니다"** 라는 알림창(Toast)이 뜬 후 사라집니다.

### Step 3: 현재 위치 집중 (Zoom to Current) - [자동 이동]
- 3초 후, 카메라가 부드럽게 **현재 사용자 위치(Current Location)**로 이동하며 확대됩니다. (줌 레벨 18.0)
- **필터 해제 (Status: OFF)**: 이동이 시작되는 순간 500km 필터를 **해제**합니다.
- **효과**: 카메라는 내 위치로 줌인되지만, 지도상에는 이제 **전 세계의 모든 핀(베이징 포함)**이 렌더링됩니다. 사용자가 추후 지도를 축소하면 먼 곳의 핀도 볼 수 있게 됩니다.
- **목표 줌 레벨**: **18.0** (건물 식별 가능 수준).

## Verification Checklist
- [x] **Location**: 
    - [x] Permissions granted -> Immediate tracking start.
    - [x] No "Infinite Loading" spinner.
    - [x] Only Custom Red Pin visible (No Blue Dot).
- [x] **Data Display**:
    - [x] Pins displayed even if WASM fails (Fallback).
    - [x] Correct count (Todo + History).
- [x] **Animation**:
    - [x] Initial: Show all pins (Zoom ~11).
    - [x] Wait 3 seconds.
    - [x] Zoom to User (Zoom 15).
    - [x] **NO** repeated forced zooming afterwards.
- [x] **Visuals**:
    - [x] Pins are 40x50 (matching iOS).
    - [x] Pin icons are not distorted.
- [x] **Naver Map**:
    - [x] Client ID Configured (`i7652syq10`).
    - [x] Map Rendering Verified by User.

## Next Steps
- Verify behavior on physical device (permission flows vary).
- Monitor for any other WASM instability.

## Summary of Fixes (2025-12-14 Session - Evening)

### 1. **Pin Gallery (Design Verification Tool)**
-   **Objective**: Ensure 100% visual consistency between iOS and Android map pins.
-   **Implementation**:
    -   **iOS**: Added `PinGalleryView.swift` with Korean guide.
    -   **Android**: Created `PinGalleryScreen.kt` using Compose `Dialog` to ensure it overlays all UI (e.g., Profile Card).
-   **Features**:
    -   **Base Assets**: Verify raw image clarity (40x50dp).
    -   **Overhang Test**: Verify badge (red circle) protrudes correctly from the top-right of the pin.
    -   **Anchor Point**: Verify the pin tip correctly aligns with the map coordinate (visualized with a Red Dot).
    -   **Results**: Confirmed consistency across platforms.

### 2. **Android Map Callout Unification**
-   **Issue**: Naver Map displayed a native `AlertDialog` on pin click, while Google/Kakao used a custom Bubble Callout.
-   **Fix**:
    -   Updated `NaverMapContent.kt` to calculate and pass Screen Coordinates (`x`, `y`) on marker click.
    -   Refactored `MainScreen` to use the unified `Box` overlay (Callout Bubble) for Naver Map events.
-   **Result**: All 3 maps (Google, Naver, Kakao) now provide the **same UX** (Bubble Callout).

### 3. **Android Pin Rendering Logic**
-   **Feature**: Implemented `PinImageManager.createShieldPin` for Android.
-   **Details**: Replicated iOS logic to generate a 50x60dp bitmap containing the 40x50dp pin and the overhanging badge, ensuring clustered pins look identical on both OSs.

### 4. **Z-Index & Layout Fixes**
-   **Issue**: Pin Gallery and Map Callouts appeared behind the "My Info" Card.
-   **Fix**:
    -   Adjusted `zIndex` and invocation order in `UserProfileView` and `MainScreen`.

### 5. **UI Polish: Liquid Glass (REVERTED)**
- **Attempted**: Applied "Liquid Glass" styling to map controls.
- **Outcome**: Reverted to original "Semi-transparent Green" style per user request ("시인성이 떨어지고 글래스 느낌이 좀 안나네").
- **Current State**: Controls use `Color.allToDoGreen.opacity(0.7)` background.

## Summary of Fixes (2025-12-18 Session - Critical)

### 1. **Recursion Error & Stability (SELF-CORRECTION)**
-   **Critical Mistake**: While fixing the "Dalian/West Sea" default view issue, I inadvertently broke the **Google Map Marker Click** (popups stopped working) and **Marker Rendering** (flickering reappeared due to removal of caching).
-   **Lesson Learned**: Modifying shared or similar logic across map providers (Kakao/Google) requires extreme caution. **Must verify existing features (Click, Render) after every logic change.**
-   **Resolution**:
    -   Restored `getCachedClusterBitmap` usage in `GoogleMapContent.kt` to fix flickering.
    -   Restored `toScreenLocation` calculation in `onClick` to fix popups ("Water Balloons").

### 2. **"Dalian" / Default View Fix**
-   **Issue**: Map initialized at (0,0) or West Sea because `currentLocation` was null and distant pins (Beijing) skewed the center.
-   **Fix**:
    -   **Persistence**: Save `currentLocation` (Lat/Lon) and `MapProvider` to `SharedPreferences`.
    -   **Default**: If no saved location, start at **Gwanghwamun (37.5759, 126.9768)** instead of (0,0).
    -   **Smart Tracking**: Persist integer coordinates (`latE7`, `lonE7`) to maintain state across restarts.

### 3. **Dynamic 500km Filter (The "Show All" Logic)**
-   **Requirement**: "Don't show distant pins (Beijing) initially (so map doesn't zoom out too much), but show them once we move to current location."
-   **Implementation**:
    -   **State**: `isDistanceFilterEnabled` (Default: `true`).
    -   **Step 1~4**: Calculate bounds using **only pins within 500km**. (Prevents zooming out to Asia level).
    -   **Step 5 (Move to Current)**: Set `isDistanceFilterEnabled = false`. This triggers a re-render showing **ALL pins** (including Beijing) while the camera zooms into the user.

### 4. **Diagnostic Logs (MapStep)**
-   **Implemented**: Standardized 5-step log sequence (`[MapStep 1..5]`) across maps to trace:
    1.  Map Initialized
    2.  Data Loaded (with Polling/Timeout)
    3.  Filtered & Calculated
    4.  FitBounds
    5.  Move to Current Location (Log + OS Location Event)
-   **Result**: Confirmed perfect timing: Map Loads -> Data Ready -> FitBounds -> 3s Wait -> User Location Arrives -> Move.
