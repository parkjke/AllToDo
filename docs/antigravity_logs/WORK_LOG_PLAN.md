# Implementation Plan - Universal Favicon

## Goal Description
Apply the application logo (`background_logo.png`) as the browser favicon for all 4 services: HomePage, WebApp, WebMng, and Backend. This unifies the visual identity across the development environment.

## Proposed Changes
### Source Image
- Use `/AllToDo-HomePage/public/assets/background_logo.png` as the source.

### Frontends (HomePage, WebApp, WebMng)
#### [NEW] [favicon.png]
- Copy source image to:
    - `AllToDo/AllToDo-HomePage/public/favicon.png`
    - `AllToDo/AllToDo-WebApp/public/favicon.png`
    - `AllToDo/AllToDo-WebMng/public/favicon.png`

#### [MODIFY] [index.html]
- Update `<head>` in `index.html` for all 3 projects:
    - Remove valid `vite.svg` reference.
    - Add `<link rel="icon" type="image/png" href="/favicon.png" />`.

### Backend (FastAPI)
#### [NEW] [favicon.png]
- Create `AllToDo-Backend/app/static/favicon.png`.

#### [MODIFY] [AllToDo-Backend/app/main.py]
- Mount `static` directory if not already mounted.
- Configure Swagger UI to use the custom favicon (optional, but good for completeness).

## Verification Plan
### Manual Verification (Local First)
1. Run `./dev-begin.sh` to start all services.
2. Open Browser tabs for:
    - HomePage (`http://localhost:5177`)
    - WebApp (`http://localhost:5175`)
    - WebMng (`http://localhost:5173`)
    - Backend (`http://localhost:8000/docs`)
3. Visually verify that the tab icon (Favicon) matches the app logo.
