from fastapi import APIRouter, HTTPException, UploadFile, File, Form
from pydantic import BaseModel
from pathlib import Path
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import base64, os, shutil
import logging

router = APIRouter(prefix="/wasm", tags=["wasm"])
logger = logging.getLogger("API_LOGGER")

# Directory to store uploaded WASMs
WASM_DIR = Path("wasm")
WASM_DIR.mkdir(exist_ok=True)
ACTIVE_WASM_FILE = WASM_DIR / "advanced_v1.wasm"
VERSION_FILE = WASM_DIR / "version.txt"

import os
import base64

# Load secret key (Base64 -> Bytes)
# Fallback to test key if not found, but we should use the env.
ENV_KEY = os.getenv("ENCRYPTION_KEY")
if ENV_KEY:
    # Handle URL-Safe or Standard by replacing -_ with +/ if needed or just use urlsafe?
    # The key in .env has _, so it's URL safe-ish.
    # Python's urlsafe_b64decode handles -_ correctly.
    # If it is standard, it might fail? No, urlsafe usually handles both if padding correct.
    # Safe approach: Try URL safe, then standard.
    try:
        AES_KEY = base64.urlsafe_b64decode(ENV_KEY)
    except:
        AES_KEY = base64.b64decode(ENV_KEY)
else:
    AES_KEY = b"0123456789abcdef0123456789abcdef" # Fallback


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
    return "1.0.0"

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
