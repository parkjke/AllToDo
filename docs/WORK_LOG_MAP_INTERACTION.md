
# Map Interaction Verification

## User Request Checklist
- [x] **Data Scope**: Display history (-24h to +24h), current location, server pins, todo pins.
- [x] **WASM Clustering**:
    - [x] **Priority**: If User Location in cluster -> Show `PinCurrent`.
    - [x] **Fallback**: Else -> Show Dominant Type (History vs Todo).
    - [x] **Badge**: Show count (with '9+' format).
- [ ] **Launch Animation**:
    - [x] Display all pins initially.
    - [x] Wait 3 seconds.
    - [Fail] **Zoom Level**: Zoom to Current Location (Level 15).
        - **Actual**: Code uses **Level 17**.

## Detailed Analysis

### 1. Data Scope
- **Verification (`ContentView.swift` lines 36-39)**:
    - Logic uses `Calendar.current.date(byAdding: .hour, value: -24, to: now)!` and `value: 24`.
    - `logTodoListStats` calculates `count24h` using this range.
    - **However**, `filteredLogs` (Line 87) uses:
        - `min = -24h`
        - `max = centerDate` (Now)
        - It does **NOT** include +24h future logs.
    - **Conclusion**: Todo Items cover ±24h. History Logs cover -24h to Now. 
    - **Note**: "History" usually implies past, so this might be intentional? User said "Current time -24h to +24h History Pin". This might be a terminology mix-up or a specific requirement for future logs (if any exist).

### 2. Clustering Logic
- **Verification (`KakaoMapView.swift` lines 427-442)**:
    - **Priority**:
        ```swift
        if hasUserLocation {
            baseImageName = "PinCurrent"
        } else if historyCount > todoCount {
            baseImageName = "PinHistory"
        }
        ```
    - **Badge**: Uses `PinImageHelper`.
    - **Format**: `PinImageHelper` updated to use "9+" for counts > 9.
    - **Conclusion**: Implemented correctly.

### 3. Launch Animation
- **Verification (`KakaoMapView.swift` line 166)**:
    - **Delay**: `DispatchQueue.main.asyncAfter(deadline: .now() + AppConfig.launchAnimationDelay)` (Config is 3.0 or 5.0).
    - **Target Zoom**: 
        ```swift
        let update = CameraUpdate.make(target: pos, zoomLevel: 17, rotation: 0, tilt: 0, mapView: mapView) // Line 169
        ```
    - **User Requirement**: Zoom **15**.
    - **Conclusion**: **Mismatch**. The code uses Zoom 17.

## Decision
I must report this mismatch (Zoom 17 vs 15) to the user as requested ("Check if implemented... don't fix arbitrarily").
I will also mention the History Log range (-24h to Now vs ±24h).

## Implementation Update: Map Visual Consistency & Logic Refinement
- **Date**: 2025-12-13

### 1. Visual Consistency (Apple & Google Maps)
- **Pin Resizing**: Fixed the issue where Google Map pins were significantly larger (High DPI).
    - [x] All pins (History, Todo, Current Location) resized to `40x50` points.
    - [x] Cluster pins base image resized **before** badging to ensure crisp badge rendering.
    - [x] Path History Start/End pins resized to `40x50`.
- **Badge Styling**:
    - [x] `PinImageHelper` updated to draw a Red Circle Badge purely in code (top-right).
    - [x] Badge formatting set to "9+" for counts > 9.
    - [x] Removed text overlays (time, etc.) from standard pins to reduce clutter, as per request.

### 2. Clustering Logic Fixes
- **User Location Clustering**:
    - [x] Removed "Independent User Location Pin" logic that caused duplication.
    - [x] Added User Location to WASM Input Data so it participates in clustering.
    - [x] Result: User Location now merges into clusters when overlapping other pins.
- **Clustering Threshold**:
    - [x] Adjusted `wasmCellSize` multiplier from `60.0` to `70.0`.
    - [x] This creates broader clusters, merging pins more aggressively (sooner).
- **Initial Rendering Bug**:
    - [x] Fixed "No Pins on Launch" issue caused by MapView width being 0 during init.
    - [x] Added fallback to `UIScreen.main.bounds.width` to ensure WASM runs immediately.

### 3. Interaction & Path Logic
- **Path Interaction**:
    - [x] Disabled "Draw Path on Tap" for History pins (User Request).
    - [x] Updated `PathHistoryView` to limit Max Zoom (Min Span `0.003` ~ Level 17) to prevent excessive zoom on short paths.
- **Selection List UI**:
    - [x] Fixed `ClusterListCallout` to show all items without scrolling if count <= 4 (avoiding ScrollView layout bug).

### 4. Native Map Cleanup
- **Double Pin Fix**:
    - [x] Explicitly disabled Native User Location (`showsUserLocation = false`, `isMyLocationEnabled = false`) on both maps.
    - [x] Eliminated the "Blue Dot" underneath the custom "Current Location" pin.
