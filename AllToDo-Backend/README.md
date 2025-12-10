# AllToDo Backend

AllToDo 애플리케이션의 백엔드입니다. FastAPI와 PostgreSQL로 구축되었습니다.

## 필수 요구 사항 (Prerequisites)

- [Docker](https://www.docker.com/get-started) 및 Docker Compose
- Python 3.14 (로컬 개발 시)

---

## 🚀 Docker Compose로 실행 (권장)

가장 쉬운 실행 방법은 Docker Compose를 사용하는 것입니다. FastAPI 애플리케이션과 PostgreSQL 데이터베이스를 모두 설정해줍니다.

1.  **빌드 및 실행**:
    ```bash
    docker-compose up --build
    ```

2.  **API 접속**: `http://localhost:8000`
    -   API 문서 (Swagger UI): `http://localhost:8000/docs`
    -   ReDoc: `http://localhost:8000/redoc`

3.  **종료**:
    ```bash
    docker-compose down
    ```

---

## 🛠️ 로컬에서 직접 실행 (Running Locally)

Docker 없이 앱을 직접 실행하려면 다음 단계를 따르세요.

1.  **데이터베이스 설정**:
    데이터베이스는 여전히 Docker로 실행하는 것이 편리합니다:
    ```bash
    docker-compose up -d db
    ```
    또는 로컬 PostgreSQL을 실행하고 `.env` 파일의 접속 정보를 수정하세요.

2.  **의존성 설치**:
    ```bash
    pip install -r requirements.txt
    ```

3.  **앱 실행**:
    ```bash
    uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
    ```

---

## 🔄 서버 관리 방법 (Server Management)

### 1. 서버 시작 (Start)
터미널에서 다음 명령어를 실행하여 서버를 켭니다.
```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```
- `host 0.0.0.0`: 외부 접속 허용 (필수)
- `port 8000`: 포트 번호 지정 (외부에서는 8003으로 접속될 수 있음)
- `reload`: 코드 변경 시 자동 재시작

### 2. 서버 종료 (Stop)
서버가 실행 중인 터미널 창에서 **`Ctrl + C`** 키를 누르면 종료됩니다.

### 3. 서버 재시작 (Restart)
1.  실행 중인 터미널에서 `Ctrl + C`로 종료합니다.
2.  다시 **시작 명령어**를 입력합니다.
    ```bash
    uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
    ```

### 4. 로그 확인 (Logging)
- **앱 로그 전송 기능**이 활성화되어 있습니다.
- 모바일 기기에서 전송된 로그는 `logs/` 폴더에 `기기ID.log` 파일로 저장됩니다.
- **로그 보기 API**:
    - 목록: `http://localhost:8000/dev/logs`
    - 상세: `http://localhost:8000/dev/logs/{기기ID}`

---

## 📂 주요 폴더 구조
- `app/`: 메인 소스 코드
    - `main.py`: 엔트리 포인트
    - `models.py`: DB 모델 정의
    - `schemas.py`: Pydantic 스키마 정의
    - `dev.py`: 개발용 도구 및 로그 API
- `wasm/`: 웹어셈블리(WASM) 바이너리 저장소
- `logs/`: 모바일 클라이언트 로그 저장소
