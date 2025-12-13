# Implementation Plan - KakaoMap WASM Integration

## Goal Description
Integrate existing WASM-based Logic (Clustering & RDP) into `KakaoMapView` to ensure performance and consistency with the Apple Map implementation. This replaces the manual clustering logic and properly visualizes optimized paths.

## User Review Required
> [!NOTE]
> All clustering and path simplification logic is now delegated to the `WasmManager`. Ensure the WASM module is correctly loaded at runtime.

## Proposed Changes

### Core Services
#### [MODIFY] [WasmManager.swift](file:///Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Services/Wasm/WasmManager.swift)
- Exposed `cluster(points:cellSize:)` function to be accessible from View layers.

#### [MODIFY] [AppLocationManager.swift](file:///Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Utilities/AppLocationManager.swift)
- Added logging for WASM RDP compression capability in `processBuffer`.

### Map Rendering (Kakao)
#### [MODIFY] [KakaoMapView.swift](file:///Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Views/KakaoMapView.swift)
- **Clustering**: Replaced manual distance calculation with `WasmManager.shared.cluster`.
- **Logic**: Added Rehydration logic to map WASM centroids back to original `UnifiedMapItem`s using Nearest Neighbor assignment.
- **Path Drawing**: Uncommented and fixed `MapPolylineShape` creation to correctly visualize user paths.
- **Fix**: Resolved `stride` variable shadowing compilation error.

## Verification Plan

### Automated / Manual Tests
1.  **Clustering**:
    - Zoom out on KakaoMap with multiple ToDo/History pins.
    - Verify pins group into Shield Icons with counts.
    - Verify logs show "set clustering" with "method: WASM".
2.  **Path Drawing**:
    - Select a History Log.
    - Verify the red path line is drawn on the map.
3.  **RDP Compression**:
    - Start a session, move around, stop session.
    - Check logs for "WASM RDP: X -> Y pts".


## Changes
- **File**: `KakaoMapView.swift`
- **New Methods**: `refreshClusters()`, `calculateClusters()`.
## Risk
- Performance: Hundreds of items re-clustering on main thread. (Should be fine for <500 items).
- Flickering: Clearing/Adding POIs might flicker. (KakaoMap SDK handles this reasonably well, but we can optimize by diffing if needed. For now, full refresh).
