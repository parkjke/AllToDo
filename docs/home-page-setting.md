# Home Page Setting Guide

AllToDo 랜딩 페이지(홈페이지)의 로컬 실행 및 배포 방법에 대한 가이드입니다.

## 1. Local Development (로컬 실행)

Vite를 사용하여 로컬 환경에서 웹 서버를 실행합니다.

1. **프로젝트 루트 폴더로 이동** (`/Volumes/Work/AllToDo`)
2. **의존성 설치** (최초 1회)
   ```bash
   npm install
   ```
3. **개발 서버 실행**
   ```bash
   npm run dev
   ```
4. **접속 확인**
   - 터미널에 표시된 주소(예: `http://localhost:5177`)로 접속합니다.
   - 포트는 `vite.config.js` 파일에서 변경 가능합니다.

---

## 2. GitHub Pages Deployment (배포)

GitHub Pages를 통해 정적 웹사이트를 무료로 호스팅할 수 있습니다.

### 설정 방법 (1회 설정)
1. GitHub 저장소의 **Settings** > **Pages** 메뉴로 이동합니다.
2. **Build and deployment** 섹션의 Source를 **GitHub Actions**로 변경하거나,
   - (현재 설정된 방식) `.github/workflows/deploy.yml` 파일이 존재하므로 **GitHub Actions** 탭에서 워크플로우 실행 상태를 확인할 수 있습니다.
   - 만약 워크플로우 파일 없이 수동 설정하려면 **Deploy from a branch**를 선택하고 `gh-pages` 브랜치를 지정합니다. (현재 자동화되어 있음)

### 자동 배포 프로세스
1. `main` 브랜치에 코드를 Push 합니다.
2. GitHub Actions가 자동으로 감지하여 Build 및 Deploy를 수행합니다.
3. 배포가 완료되면 `https://[username].github.io/AllToDo/` 주소에서 확인 가능합니다.

---

## 3. Configuration (설정 파일)

- **`vite.config.js`**: 포트 번호(`server.port`) 및 기본 경로(`base`) 설정
- **`.github/workflows/deploy.yml`**: GitHub Actions 자동 배포 스크립트
