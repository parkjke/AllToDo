from fastapi import APIRouter, HTTPException, UploadFile, File, Form
from pydantic import BaseModel
from pathlib import Path
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import base64, os, shutil
import logging

router = APIRouter(prefix="/wasm", tags=["wasm"])
logger = logging.getLogger("API_LOGGER")

# Directory to store uploaded WASMs
WASM_DIR = Path("app/static/wasm")
WASM_DIR.mkdir(parents=True, exist_ok=True)
ACTIVE_WASM_FILE = WASM_DIR / "advanced_v1.wasm"
VERSION_FILE = WASM_DIR / "version.txt"

import os
import base64

# Load secret key (Base64 -> Bytes)
# Load secret key (Base64 -> Bytes)
ENV_KEY = os.getenv("ENCRYPTION_KEY")
# [FIX] Force Sync with iOS Key (Ignore Env for now to fix mismatch without restart)
if False: # ENV_KEY:
    # Handle URL-Safe or Standard by replacing -_ with +/ if needed or just use urlsafe?
    # The key in .env has _, so it's URL safe-ish.
    try:
        AES_KEY = base64.urlsafe_b64decode(ENV_KEY)
    except:
        AES_KEY = base64.b64decode(ENV_KEY)
else:
    # Fallback to iOS matching key (Standard Base64)
    # Key: h5eDj7nnM4A17/L1IrsbMMHsbA8YFdFL3L5ONYNkzNA=
    AES_KEY = base64.b64decode("h5eDj7nnM4A17/L1IrsbMMHsbA8YFdFL3L5ONYNkzNA=")


class WasmResponse(BaseModel):
    version: str
    ciphertext_b64: str
    iv_b64: str
    tag_b64: str

class VersionResponse(BaseModel):
    version: str

def get_current_version():
    if VERSION_FILE.exists():
        return VERSION_FILE.read_text().strip()
    return "1.0.2"

def set_current_version(v: str):
    VERSION_FILE.write_text(v)

@router.get("/version", response_model=VersionResponse)
def check_version():
    """Returns the current active WASM version."""
    return {"version": get_current_version()}

@router.post("/upload")
def upload_wasm(version: str = Form(...), file: UploadFile = File(...)):
    """Uploads a new WASM file and updates the version."""
    try:
        file_path = WASM_DIR / f"advanced_{version}.wasm"
        with file_path.open("wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
        
        # Update active link (copy to active)
        shutil.copy(file_path, ACTIVE_WASM_FILE)
        set_current_version(version)
        
        return {"status": "success", "version": version, "message": "WASM uploaded and activated"}
    except Exception as e:
        logger.error(f"Upload failed: {e}")
        raise HTTPException(500, f"Upload failed: {str(e)}")

@router.get("/advanced", response_model=WasmResponse)
def get_wasm():
    if not ACTIVE_WASM_FILE.exists():
         raise HTTPException(500, "WASM file not found")

    wasm_bytes = ACTIVE_WASM_FILE.read_bytes()
    version = get_current_version()
    
    iv = os.urandom(12)
    aes = AESGCM(AES_KEY)
    
    encrypted = aes.encrypt(iv, wasm_bytes, None)
    
    tag = encrypted[-16:]
    ciphertext = encrypted[:-16]
    
    return WasmResponse(
        version=version,
        ciphertext_b64=base64.b64encode(ciphertext).decode(),
        iv_b64=base64.b64encode(iv).decode(),
        tag_b64=base64.b64encode(tag).decode(),
    )
