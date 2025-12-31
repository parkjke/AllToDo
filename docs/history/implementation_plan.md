# Android Interface & Interaction Refactor

## Goal Description
Align the Android application's UI and interaction model with the iOS version and recent user requests. This includes redesigning map callouts (bubbles), improving the user profile view, and adding a "Time Travel" feature for viewing past data.

## User Review Required
> [!IMPORTANT]
> **Time Travel Logic:** The "Yesterday" button will filter map pins to show items from **24 hours ago** relative to the current time, mimicking the iOS time filter logic.
> **Settings Persistence:** New settings (Max Items, Font Size) will be added to the Profile view. They will be stored in `DataStore` (or SharedPreferences) to persist across sessions.

## Proposed Changes

### UI Components

#### [NEW] `com.example.alltodo.ui.components.UserProfileView.kt`
- Create a new component for the User Profile.
- **Layout:** Non-modal overlay (or side sheet) that **does not cover the right-side icons**.
- **Content:**
    - Nickname (read-only/editable?)
    - Phone Number
    - **Settings:**
        - "Bubble Max Items" (Stepper or Slider)
        - "Bubble Time Font Size" (Stepper or Segmented Control)
- **Integration:** Replace the simple boolean/placeholder in `MainScreen` with this view.

#### [MODIFY] `com.example.alltodo.ui.components.RightSideControls.kt`
- Add a new **Time Travel Button** (Icon: Clock/History?).
- Position: To the **left** of the Notification icon.
- **Behavior:** Accepts an `isHistoryMode` state to toggle appearance (e.g., button color change).
- **Disabled State:** When in History Mode, other icons (Notification, Person) should be visually disabled/dimmed as requested.

### Map & Logic (`MainScreen.kt`)

#### [MODIFY] Callout (Bubble) Rendering
- **Current:** Custom `Canvas` + `LazyColumn`.
- **New:** Styled `Box` + `Column`.
- **Design:** `[Icon] | [Time/Title] | [Trash]` row layout.
- **Visuals:** Add a Border (`BorderStroke`). Adjust width/height dynamically based on item count (controlled by new Setting).

#### [MODIFY] Pin Filtering Logic
- **Default Mode:** Show items from `Now - 24h` to `Now + 24h`.
- **Yesterday Mode:** Show items from `Now - 48h` to `Now - 24h`. (Or "Yesterday's All Pins" -> Calendar Day logic). *Plan: Use a "Time Window" state variable.*
- **Zoom Logic:** When toggling modes, auto-fit the map to show all relevant pins.

## Verification Plan

### Manual Verification
1.  **Callout Style:** Tap a pin (single & cluster). Verify the bubble looks like "[Map Icon] [Time] [Trash Icon]" and has a border.
2.  **Profile View:** Tap the "My Info" icon. Verify it opens without covering right-side buttons. Verify Settings can be changed.
3.  **Time Travel:**
    - Tap the new "Yesterday" button.
    - Verify pins change (simulate by ensuring some old data exists or checking empty state).
    - Verify other buttons are disabled/dimmed.
    - Tap again to return to "Now".
