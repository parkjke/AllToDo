from fastapi import FastAPI, Depends, HTTPException
from sqlalchemy.orm import Session
from dotenv import load_dotenv
import os

load_dotenv()

from .database import engine, Base, get_db
from . import models, schemas, crud

# Create tables
models.Base.metadata.create_all(bind=engine)

app = FastAPI(title="AllToDo Backend")

import logging
from fastapi import Request

# Logging Configuration
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler()]
)
logger = logging.getLogger("API_LOGGER")

@app.middleware("http")
async def log_requests(request: Request, call_next):
    # Log Request Details
    logger.info(f"➡️  {request.method} {request.url}")
    
    # Log Body
    try:
        body = await request.body()
        if body:
            logger.info(f"📝 Body: {body.decode('utf-8')}")
    except Exception as e:
        logger.error(f"Failed to read body: {e}")

    response = await call_next(request)
    
    # Log Response Status
    logger.info(f"⬅️  Status: {response.status_code}")
    return response

@app.post("/check-user", response_model=schemas.UserResponse)
def check_user(user: schemas.UserCreate, db: Session = Depends(get_db)):
    """
    **사용자 확인 및 생성**

    제공된 UUID로 사용자가 존재하는지 확인합니다.
    
    - **존재 시**: 기존 정보를 반환합니다.
    - **미존재 시**: 새로운 사용자를 **생성**하고 반환합니다.
    """
    db_user = crud.get_user(db, uuid=user.uuid)
    if db_user:
        return {"uuid": db_user.uuid, "created_at": db_user.created_at, "message": "User exists"}
    
    # Create new user
    new_user = crud.create_user(db, user=user)
    return {"uuid": new_user.uuid, "created_at": new_user.created_at, "message": "User created"}

@app.post("/log-usage", response_model=schemas.LogResponse)
def log_usage(log: schemas.LogCreate, db: Session = Depends(get_db)):
    """
    **사용 로그 기록**

    사용자의 활동(위치, 시간)을 기록합니다.
    
    - 백그라운드에서 주기적으로 호출되어 사용자의 동선을 추적하는 데 사용됩니다.
    """
    crud.create_usage_log(db, log=log)
    crud.create_usage_log(db, log=log)
    return {"status": "success"}



@app.post("/update-info", response_model=schemas.UserUpdateResponse)
def update_info(info: schemas.UserInfoUpdate, db: Session = Depends(get_db)):
    """
    **사용자 정보를 업데이트합니다.**

    이 API는 사용자의 상세 정보를 갱신할 때 사용됩니다.
    
    - **자동 생성**: 만일 해당 UUID의 사용자 정보가 없으면 *새로 생성*합니다.
    - **부분 업데이트**: 변경하지 않는 항목은 `null`로 보내거나 아예 필드를 생략하세요.
    
    **주의사항:**
    1. 모든 민감 정보는 서버에서 **자동으로 암호화**되어 저장됩니다.
    2. `user_uuid`는 필수 항목입니다.
    """
    try:
        crud.update_user_info(db, info=info)
        return {"status": "updated"}
    except Exception as e:
        print(f"Error in update_info: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/")
def read_root():
    """
    **API 상태 확인**

    서버가 정상적으로 작동 중인지 확인하는 헬스 체크용 엔드포인트입니다.
    """
    return {"message": "Welcome to AllToDo API"}

@app.post("/recover-uuid")
def recover_uuid(request: schemas.RecoverRequest, db: Session = Depends(get_db)):
    """
    **UUID 복구**

    기기 변경 등으로 UUID를 분실했을 때 사용합니다.
    
    - **필수 조건**: 이전에 `nickname`과 `password`를 설정해 두었어야 합니다.
    - **검증**: 닉네임과 비밀번호가 일치하면 원래의 UUID를 반환합니다.
    """
    user = crud.get_user_by_nickname(db, nickname=request.nickname)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    # Verify password
    if not crud.verify_user_password(db, user.uuid, request.password):
        raise HTTPException(status_code=401, detail="Invalid password")
    
    return {"uuid": user.uuid, "message": "Recovery successful"}

@app.get("/user-info", response_model=schemas.UserInfoResponse)
def get_user_info(uuid: str, db: Session = Depends(get_db)):
    """
    **내 정보 조회**

    사용자의 상세 정보를 조회합니다.
    
    - **자동 복호화**: 서버에 암호화되어 저장된 개인정보를 **복호화**하여 반환합니다.
    - **빈 값 처리**: 저장되지 않은 항목은 `null`로 반환됩니다.
    """
    # 1. Get User Info (Encrypted)
    db_info = db.query(models.UserInfo).filter(models.UserInfo.user_uuid == uuid).first()
    
    if not db_info:
        # Return empty info with just UUID
        return schemas.UserInfoResponse(user_uuid=uuid)
    
    # 2. Decrypt Fields
    # Helper to decrypt or return None if empty/fail
    def safe_decrypt(val):
        if not val: return None
        try:
            return decrypt(val)
        except:
            return None

    return schemas.UserInfoResponse(
        user_uuid=db_info.user_uuid,
        name=safe_decrypt(db_info.name),
        phone_number=safe_decrypt(db_info.phone_number),
        age=safe_decrypt(db_info.age),
        address=safe_decrypt(db_info.address),
        address_lat=safe_decrypt(db_info.address_lat),
        address_long=safe_decrypt(db_info.address_long),
        work_address=safe_decrypt(db_info.work_address),
        work_lat=safe_decrypt(db_info.work_lat),
        work_long=safe_decrypt(db_info.work_long),
        nickname=db_info.user.nickname # Access nickname from relationship
    )

# Development APIs
from . import dev
# WASM APIs
from . import wasm

app.include_router(dev.router)
app.include_router(wasm.router)
