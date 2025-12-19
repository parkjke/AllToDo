## 2025-12-19
### iOS Path Recording & Visualization Fixes
- **Visualisation Fix**:
  - Found that `NaverMapView`, `GoogleMapView`, and `KakaoMapView` were **not calling** the `updatePath` method in their rendering loop (`updateUIView`).
  - Added `context.coordinator.updatePath(...)` to all three, ensuring that saved history paths are now correctly drawn.
- **Background Recording**:
  - Enabled `allowsBackgroundLocationUpdates = true` in `AppLocationManager.swift` to ensure the app continues recording location even when the screen is locked or the app is in the background (essential for reliable path tracking).
- **KakaoMap Interaction Fixes**:
  - **Sensitivity**: Fixed the "2x movement needed" issue by updating `locationManager.currentSpan` correctly within KakaoMap's `updateSpan`. Previously, it was using a stale default value, causing the update threshold to be too high at deep zoom levels.
  - **Path Visualization**: REMOVED implicit path drawing on the main map for all providers (Apple, Naver, Google, Kakao) as per user request. Path visualization is now exclusively handled by the `PathHistoryView` (Callout -> History Sheet).
- **Background Resume Reliability**:
  - Increased the Launch Sequence delay from 0.1s to **1.0s** in `ContentView` to ensure map engines (GL Context) are fully initialized before receiving camera updates.
  - Added explicit `refreshWasmClusters()` calls within the `performLaunchAnimation` sequence for Naver and Google Maps to guarantee pins are rendered immediately upon resume. (Kakao Map already handles this via `checkEngineActivation`).
- **Interaction Fixes (Google & Kakao)**:
  - **Google Map**: Removed a residual `updatePath` call that was re-introduced during the background resume fix, ensuring paths are not drawn on the main map.
  - **Kakao Map**: Changed pin tap logic to **always show the Callout (Water Balloon)** first, even for single items. Previously, single items skipped the callout and opened the detail sheet directly, which confused the user. Now behavior is consistent with the "Water Balloon -> Tap -> Detail Sheet" flow.
- **UX Improvements**:
  - **Auto-Center**: Implemented camera animation to automatically center the map on a tapped pin across all providers (Apple, Naver, Google, Kakao). This ensures the callout ("Water Balloon") has enough space to appear without being clipped by the screen edge.


## 2025-12-18
### iOS Map Visuals & Logic Refinement
- **Unified Pin Sizes**:
  - Updated `NaverMap` pin rendering to use **48x60** size for both initial "Raw" rendering and subsequent "WASM Cluster" rendering. This resolves the visual discrepancy where pins appeared smaller after the initial animation.
  - Adjusted `PinImageHelper` to respect the input `baseImage` size dynamically instead of hardcoding to 32x40, allowing map-specific sizing (e.g., Naver's larger pins).
- **Zoom Level Standardization**:
  - Updated `GoogleMap` and `KakaoMap` launch animation sequences.
  - Changed the final "Current Location" zoom level from 15 to **18** to match the behavior of the manual "Current Location" button.
- **Tethering (Auto-Centering) Disabled**:
  - Disabled `checkTethering` logic in `NaverMapView.swift` and `GoogleMapView.swift` (iOS) to prevent the map from fighting user panning.
  - Verified `GoogleMapContent.kt` (Android) has the `SmartLocationManager.needsCentering` logic disabled as well.
- **Bug Fixes**:
  - Resolved iOS compilation errors in `NaverMapView` and `GoogleMapView` (duplicate functions, missing properties).
