from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from pathlib import Path
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import base64, os

app = FastAPI()

# Adjusted path to match where we run it from or relative to backend
WASM_PATH = Path("../wasm/advanced_v1.wasm")
AES_KEY = b"0123456789abcdef0123456789abcdef" # 32 bytes

class WasmResponse(BaseModel):
    version: str
    ciphertext_b64: str
    iv_b64: str
    tag_b64: str

@app.get("/wasm/advanced", response_model=WasmResponse)
def get_wasm():
    if not WASM_PATH.exists():
        # Fallback check if running from backend dir
        WASM_PATH_ALT = Path("wasm/advanced_v1.wasm") # if copied? No, instructions say ../wasm likely or project root.
        # User instruction said: "project/wasm/advanced_v1.wasm" and "backend/main.py".
        # So from backend/, it is ../wasm/advanced_v1.wasm
        if not WASM_PATH.exists():
             raise HTTPException(500, f"wasm not found at {WASM_PATH.absolute()}")
            
    wasm_bytes = WASM_PATH.read_bytes()
    
    # If dummy content is text (asm_dummy_bytes), encode it if read as bytes.
    # read_bytes() returns bytes.
    
    iv = os.urandom(12)
    aes = AESGCM(AES_KEY)
    
    encrypted = aes.encrypt(iv, wasm_bytes, None)
    
    # AESGCM.encrypt returns ciphertext + tag appended
    # Tag is 16 bytes for AESGCM usually. Check docs or assume library behavior.
    # cryptography library AESGCM.encrypt returns ciphertext + tag.
    
    tag = encrypted[-16:]
    ciphertext = encrypted[:-16]
    
    return WasmResponse(
        version="1.0.0",
        ciphertext_b64=base64.b64encode(ciphertext).decode(),
        iv_b64=base64.b64encode(iv).decode(),
        tag_b64=base64.b64encode(tag).decode(),
    )
