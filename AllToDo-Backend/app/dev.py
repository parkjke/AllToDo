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

from fastapi.responses import PlainTextResponse

@router.get("/logs/{device_id}", response_class=PlainTextResponse)
def view_log(device_id: str):
    """
    **View Log Content**
    Returns the raw content of the log file for the specified device.
    Supports partial ID matching (e.g. first 8 chars).
    """
    log_dir = "logs"
    # Basic sanitization
    safe_search = "".join(c for c in device_id if c.isalnum() or c in "-_")
    
    # 1. Try Exact Match
    target_file = None
    
    if os.path.exists(os.path.join(log_dir, f"{safe_search}.log")):
        target_file = f"{safe_search}.log"
    else:
        # 2. Try Prefix Match
        if os.path.exists(log_dir):
            for f in os.listdir(log_dir):
                if f.startswith(safe_search) and f.endswith(".log"):
                    target_file = f
                    break
    
    if not target_file:
         raise HTTPException(status_code=404, detail="Log file not found (No match for ID)")
         
    file_path = os.path.join(log_dir, target_file)
    
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
        return content
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to read log: {e}")
