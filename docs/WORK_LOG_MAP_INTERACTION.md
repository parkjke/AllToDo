# CRITICAL PROTOCOL: REPORT ONLY, DO NOT ACT WITHOUT INSTRUCTION
**ALWAYS verify and report findings first. DO NOT modify code without explicit user permission.**
**This instruction persists across sessions.**

# Android Map Interaction & Debugging Log

## Summary of Fixes (2025-12-13 Session)

### 1. **Location Tracking & Permissions**
-   **Infinite Loading Fix**: Resolved the issue where the app was stuck in a loading state. The root cause was `LaunchedEffect` not detecting the permission grant immediately.
    -   **Fix**: Introduced `hasLocationPermission` state variable using `mutableStateOf`. The permission launcher now updates this state, which directly triggers the location tracking `LaunchedEffect`.
-   **Blue Pin Removal**: Disabled the native Google Map "My Location" layer (`isMyLocationEnabled = false`) to prevent the default blue dot from overlapping with our custom Red Pin.

### 2. **WASM Clustering Stability**
-   **Data Loss Fix**: Fixed the issue where pins disappeared ("count=0") despite valid data entering the ViewModel.
    -   **Root Cause**: The WASM clustering module was failing (or returning empty) and the app had no fallback.
    -   **Fix**: Implemented a robust **Fallback Mechanism**. If WASM returns empty/fails but input points exist, the app automatically switches to a "1-to-1 Mapping" mode, ensuring all pins are displayed.
-   **Type Safety**: Fixed a `Double?` type mismatch compilation error in `TodoViewModel` by adding explicit null checks.

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

## Next Steps
- Verify behavior on physical device (permission flows vary).
- Monitor for any other WASM instability.
