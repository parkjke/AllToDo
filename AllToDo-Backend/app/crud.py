from sqlalchemy.orm import Session
from . import models, schemas
from cryptography.fernet import Fernet
import os
import base64

# Encryption Helper
# In a real app, ensure ENCRYPTION_KEY is set securely.
# If not set, we generate one for demo purposes (BUT this means data is lost on restart if not persisted)
# For this task, we assume it's passed via env or we use a static one for dev if missing.
KEY = os.getenv("ENCRYPTION_KEY")
if not KEY:
    # Fallback for dev only - DO NOT USE IN PRODUCTION
    KEY = Fernet.generate_key().decode()

cipher_suite = Fernet(KEY.encode() if isinstance(KEY, str) else KEY)

def encrypt(data: str) -> str:
    if not data: return None
    return cipher_suite.encrypt(data.encode()).decode()

def decrypt(data: str) -> str:
    if not data: return None
    return cipher_suite.decrypt(data.encode()).decode()

# User Operations
def get_user(db: Session, uuid: str):
    return db.query(models.User).filter(models.User.uuid == uuid).first()

def create_user(db: Session, user: schemas.UserCreate):
    db_user = models.User(
        uuid=user.uuid,
        created_lat=user.latitude,
        created_long=user.longitude,
        nickname=user.nickname
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    return db_user

def get_user_by_nickname(db: Session, nickname: str):
    return db.query(models.User).filter(models.User.nickname == nickname).first()

def verify_user_password(db: Session, user_uuid: str, password: str) -> bool:
    db_info = db.query(models.UserInfo).filter(models.UserInfo.user_uuid == user_uuid).first()
    if not db_info or not db_info.password:
        return False
    
    try:
        decrypted_password = decrypt(db_info.password)
        return decrypted_password == password
    except:
        return False

# Usage Log Operations
def create_usage_log(db: Session, log: schemas.LogCreate):
    db_log = models.UsageLog(
        user_uuid=log.user_uuid,
        latitude=log.latitude,
        longitude=log.longitude
    )
    db.add(db_log)
    db.commit()
    return db_log

# User Info Operations
def update_user_info(db: Session, info: schemas.UserInfoUpdate):
    # Check if exists
    db_info = db.query(models.UserInfo).filter(models.UserInfo.user_uuid == info.user_uuid).first()
    
    if not db_info:
        db_info = models.UserInfo(user_uuid=info.user_uuid)
        db.add(db_info)
    
    # Encrypt and Update (Only update if provided)
    if info.name is not None: db_info.name = encrypt(info.name)
    if info.password is not None: db_info.password = encrypt(info.password)
    if info.phone_number is not None: db_info.phone_number = encrypt(info.phone_number)
    if info.age is not None: db_info.age = encrypt(str(info.age))
    if info.address is not None: db_info.address = encrypt(info.address)
    if info.address_lat is not None: db_info.address_lat = encrypt(str(info.address_lat))
    if info.address_long is not None: db_info.address_long = encrypt(str(info.address_long))
    if info.work_address is not None: db_info.work_address = encrypt(info.work_address)
    if info.work_lat is not None: db_info.work_lat = encrypt(str(info.work_lat))
    if info.work_long is not None: db_info.work_long = encrypt(str(info.work_long))
    
    db.commit()
    return db_info
