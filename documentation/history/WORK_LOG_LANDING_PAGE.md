# AllToDo Landing Page Work Log

## 2025-12-16

### 1. Project Initialization
- **Created Project**: `AllToDo-Landing` using Vite (Vanilla JS template).
- **Structure**:
    - `index.html`: Main entry point with Korean locale.
    - `src/style.css`: Custom CSS variables, dark theme, and animations.
    - `src/main.js`: Simple entry point (console log only).
    - `public/favicon.png`: Linked to the main app icon.

### 2. Design Implementation
- **Theme**: Premium Dark Theme.
    - Background: `#0a0a0a` with a subtle `AllToDo Green (#00E676)` radial gradient.
    - Typography: `Inter` (English) and `Noto Sans KR` (Korean).
- **Content**:
    - **Header**: Logo and Slogan ("당신의 모든 일을 기록하고 증명하는 플랫폼").
    - **Body**: Two main sections describing the platform's value.
    - **Footer**: Status ("Creating v1") and Contact (`parkjgy@gmail.com`).
- **Animations**: implemented `FadeInUp` and `FadeInDown` for a smooth, premium entrance effect.

### 3. Integration
- **Project Structure**: Renamed directory from `alltodo-landing` to `AllToDo-Landing` to match the monorepo convention.
- **Documentation**: Updated root `README.md` with:
    - New table entry for the Landing Page.
    - Instructions on how to run (`npm install`, `npm run dev`).

## 2025-12-25

### 1. Background Enhancement (Scattered Slideshow)
- **Goal**: Create a dynamic, premium feeling background using actual app screenshots.
- **Implementation**:
    - **Assets**: Processed 7 layout screenshots (Mobile & Dashboard), optimized/resized to 800px width (`bg_mobile_1` ~ `6`, `bg_dashboard`).
    - **Logic (`main.js`)**:
        - **Sequential Spawning**: Images appear every **3 seconds** (previously 4s) to keep visual interest.
        - **Instant Start**: Pre-spawns 5 images on load to prevent initial emptiness.
        - **Randomization**: Random position (0-90%), rotation, scale (0.4-1.0), and blur.
        - **Animation**: Images rotate very slowly (**60-120s duration**) using Web Animations API.
    - **Styling**: 
        - Z-Index layering fixed: Background (`0`) vs Content (`1`).
        - Dark overlay (`#0a0a0a`) mixed with radial gradient for text readability.
