# AllToDo Work Log

## 2025-12-16 & 17

### 1. App ID Migration
- **Objective**: Change App ID from `kr.co.daam.AllToDo` to `kr.alltodo` to match the new domain.
- **Action**:
    - **Android**: Updated `applicationId` in `app/build.gradle.kts`.
    - **iOS**: Updated `PRODUCT_BUNDLE_IDENTIFIER` in `project.pbxproj`.
- **Status**: Migration completed. User confirmed maps are loading correctly, implying the Console configurations (Naver/Kakao/Google) were successfully updated by the user.

### 2. Landing Page Deployment
- **Created**: Built a premium dark-themed landing page using Vite.
- **Location**: Moved project root to `/AllToDo` to support GitHub Pages.
- **Deployment**: Configured `.nojekyll` and relative paths. Site is live at [parkjke.github.io/AllToDo](https://parkjke.github.io/AllToDo/).

### 3. Verification
- **Maps**: Confirmed functioning on both Android and iOS after ID change.
- **Landing Page**: Confirmed accessible via GitHub Pages.
