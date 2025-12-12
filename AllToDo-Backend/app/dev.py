from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import inspect, text
from .database import engine, get_db
from .crud import decrypt

router = APIRouter(
    prefix="/dev",
    tags=["development"]
)

def verify_dev_password(password: str = ""):
    if password != "pw3355":
        raise HTTPException(status_code=401, detail="Unauthorized")
    return True

@router.get("/tables")
def list_tables(authorized: bool = Depends(verify_dev_password)):
    """
    **[개발용] 테이블 목록 조회**

    데이터베이스에 존재하는 모든 테이블의 이름을 조회합니다.
    
    - **보안**: `password` 파라미터가 필요합니다.
    """
    inspector = inspect(engine)
    return inspector.get_table_names()

@router.get("/tables/{table_name}")
def view_table(table_name: str, authorized: bool = Depends(verify_dev_password), db: Session = Depends(get_db)):
    """
    **[개발용] 테이블 내용 조회**

    특정 테이블의 데이터를 조회합니다 (최대 100건).
    
    - **자동 복호화**: 암호화된 컬럼(이름, 주소 등)은 자동으로 복호화되어 표시됩니다.
    - **보안**: `password` 파라미터가 필요합니다.
    """
    # Validate table name to prevent SQL injection (basic check)
    inspector = inspect(engine)
    if table_name not in inspector.get_table_names():
        raise HTTPException(status_code=404, detail="Table not found")
    
    try:
        result = db.execute(text(f"SELECT * FROM {table_name} LIMIT 100"))
        
        # Define encrypted columns (based on models.py)
        encrypted_columns = {
            "name", "password", "phone_number", "age", 
            "address", "address_lat", "address_long", 
            "work_address", "work_lat", "work_long"
        }

        rows = []
        for row in result:
            row_dict = dict(row._mapping)
            # Decrypt known encrypted columns if they exist in this table
            for col in row_dict:
                if col in encrypted_columns and row_dict[col] is not None:
                    try:
                        row_dict[col] = decrypt(row_dict[col])
                    except:
                        # Keep original if decryption fails (e.g. not encrypted or bad key)
                        pass
            rows.append(row_dict)
            
        return rows
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


from . import schemas

import os

@router.post("/logs", response_model=schemas.RemoteLogResponse)
def receive_remote_log(log: schemas.RemoteLogCreate):
    """
    **Receive Client Logs**
    Saves logs to `logs/{device_id}.log`
    """
    # Ensure logs dir exists
    log_dir = "logs"
    os.makedirs(log_dir, exist_ok=True)
    
    # Sanitize device ID (basic)
    device_id = "".join(c for c in log.device if c.isalnum() or c in "-_")
    if not device_id: device_id = "unknown_device"
    
    file_path = os.path.join(log_dir, f"{device_id}.log")
    
    icon = "📱"
    if log.level == "ERROR": icon = "🚨"
    elif log.level == "WARN": icon = "⚠️"
    
    log_content = f"{icon} [{log.timestamp}] {log.level}: {log.message}\n"
    
    # 1. Print to detailed console
    print(f"{icon} [{device_id}] {log.message}")
    
    # 2. Append to file
    try:
        with open(file_path, "a", encoding="utf-8") as f:
            f.write(log_content)
    except Exception as e:
        print(f"Failed to write log: {e}")
        
    return {"status": "saved"}

@router.get("/logs", response_model=list[str])
def list_logs():
    """
    **List Available Log Files**
    Returns a list of device IDs that have sent logs.
    """
    log_dir = "logs"
    if not os.path.exists(log_dir):
        return []
    
    files = [f.replace(".log", "") for f in os.listdir(log_dir) if f.endswith(".log")]
    return sorted(files)

@router.post("/logs/batch", response_model=schemas.RemoteLogResponse)
def receive_remote_log_batch(logs: list[schemas.RemoteLogCreate]):
    """
    **[개발용] 클라이언트 로그 일괄 전송**

    여러 건의 로그를 한 번에 받아 `logs/{device_id}.log` 파일에 저장합니다.
    
    - **용도**: 클라이언트(Android/iOS)에서 배터리 최적화 로그 등을 서버로 전송할 때 사용합니다.
    - **동작**: Device ID별로 그룹화하여 파일에 내용을 추가(Append)합니다.
    """
    # Ensure logs dir exists
    log_dir = "logs"
    os.makedirs(log_dir, exist_ok=True)
    
    saved_count = 0
    
    # Simple grouping by device to minimize file opens
    logs_by_device = {}
    for log in logs:
        # Sanitize device ID
        device_id = "".join(c for c in log.device if c.isalnum() or c in "-_")
        if not device_id: device_id = "unknown_device"
        
        if device_id not in logs_by_device:
            logs_by_device[device_id] = []
        logs_by_device[device_id].append(log)
        
    for device_id, device_logs in logs_by_device.items():
        file_path = os.path.join(log_dir, f"{device_id}.log")
        try:
            with open(file_path, "a", encoding="utf-8") as f:
                for log in device_logs:
                    icon = "📱"
                    if log.level == "ERROR": icon = "🚨"
                    elif log.level == "WARN": icon = "⚠️"
                    elif log.level == "LOCATION_PAUSE": icon = "⏸️"
                    elif log.level == "LOCATION_RESUME": icon = "▶️"
                    elif log.level == "MOTION_CHANGE": icon = "🏃"
                    elif log.level == "BATTERY_LEVEL": icon = "🔋"
                    
                    log_content = f"{icon} [{log.timestamp}] {log.level}: {log.message}\n"
                    f.write(log_content)
                    saved_count += 1
        except Exception as e:
            print(f"Failed to write log for {device_id}: {e}")

    print(f"Batch saved {saved_count} logs from {len(logs_by_device)} devices.")
    return {"status": f"saved {saved_count}"}

from fastapi.responses import HTMLResponse

@router.get("/logs/view", response_class=HTMLResponse)
def view_logs_dashboard():
    """
    **[개발용] 로그 뷰어 대시보드**
    
    서버에 저장된 기기별 로그 파일을 웹 브라우저에서 편리하게 조회할 수 있는 HTML 페이지를 반환합니다.
    """
    html_content = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>AllToDo Log Viewer</title>
        <style>
            body { font-family: sans-serif; margin: 0; display: flex; height: 100vh; overflow: hidden; }
            #sidebar { width: 300px; background: #f0f0f0; border-right: 1px solid #ccc; overflow-y: auto; padding: 10px; }
            #content { flex: 1; padding: 20px; overflow-y: auto; background: #1e1e1e; color: #d4d4d4; }
            h3 { margin-top: 0; }
            .file-item { padding: 10px; cursor: pointer; border-bottom: 1px solid #ddd; }
            .file-item:hover { background: #e0e0e0; }
            .file-item.active { background: #d0d0d0; font-weight: bold; }
            pre { white-space: pre-wrap; word-wrap: break-word; font-family: 'Consolas', monospace; font-size: 14px; }
            #refresh-btn { margin-bottom: 10px; padding: 5px 10px; cursor: pointer; }
        </style>
        <script>
            async function loadList() {
                const response = await fetch('/dev/logs');
                const files = await response.json();
                const sidebar = document.getElementById('file-list');
                sidebar.innerHTML = '';
                files.forEach(file => {
                    const div = document.createElement('div');
                    div.className = 'file-item';
                    div.innerText = file;
                    div.onclick = () => loadLog(file, div);
                    sidebar.appendChild(div);
                });
            }
            
            async function loadLog(deviceId, element) {
                // Highlight
                document.querySelectorAll('.file-item').forEach(el => el.classList.remove('active'));
                if(element) element.classList.add('active');
                
                const response = await fetch('/dev/logs/' + deviceId);
                const text = await response.text();
                const content = document.getElementById('log-content');
                content.innerText = text; // Secure text content
                
                // Auto scroll to bottom
                const container = document.getElementById('content');
                container.scrollTop = container.scrollHeight;
            }
            
            window.onload = loadList;
        </script>
    </head>
    <body>
        <div id="sidebar">
            <h3>Device Logs</h3>
            <button id="refresh-btn" onclick="loadList()">Refresh List</button>
            <div id="file-list">Loading...</div>
        </div>
        <div id="content">
            <pre id="log-content">Select a log file to view...</pre>
        </div>
    </body>
    </html>
    """
    return HTMLResponse(content=html_content, status_code=200)
