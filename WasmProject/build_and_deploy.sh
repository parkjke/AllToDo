#!/bin/bash
set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}=== AllToDo WASM Builder & Deployer ===${NC}"

# 1. Check Pre-requisites
if ! command -v cargo &> /dev/null; then
    echo -e "${RED}Error: Rust (cargo) is not installed.${NC}"
    echo "Please install it: curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh"
    exit 1
fi

if ! command -v wasm-pack &> /dev/null; then
    echo -e "${RED}Error: wasm-pack is not installed.${NC}"
    echo "Please install it: cargo install wasm-pack"
    exit 1
fi

# 2. Build WASM
echo -e "\n${GREEN}[1/3] Building WASM Project...${NC}"
cd wasm_src
# Use no-modules for easier WebView embedding (Global variable 'wasm_bindgen')
wasm-pack build --target no-modules --release
cd ..

WASM_FILE="wasm_src/pkg/geo_engine_bg.wasm"
JS_FILE="wasm_src/pkg/geo_engine.js"
TARGET_NAME="advanced_v1.wasm"
FALLBACK_NAME="fallback.wasm"

if [ ! -f "$WASM_FILE" ]; then
    echo -e "${RED}Build failed: $WASM_FILE not found (checked geo_engine_bg.wasm).${NC}"
    # Fallback check for old name
    if [ -f "wasm_src/pkg/alltodo_wasm_core_bg.wasm" ]; then
        WASM_FILE="wasm_src/pkg/alltodo_wasm_core_bg.wasm"
        JS_FILE="wasm_src/pkg/alltodo_wasm_core.js"
        echo "Found old name: $WASM_FILE"
    else
        exit 1
    fi
fi

echo -e "${GREEN}Build Success! File size: $(du -h "$WASM_FILE" | cut -f1)${NC}"

# 3. Deploy to Backend (Local Direct Copy)
echo -e "\n${GREEN}[2/3] Deploying to Backend...${NC}"
BACKEND_DEST="../AllToDo-Backend/wasm/$TARGET_NAME"
cp "$WASM_FILE" "$BACKEND_DEST"
echo "Copied to $BACKEND_DEST"

# 4. Deploy to Mobile Apps (Fallback)
echo -e "\n${GREEN}[3/3] Deploying to Mobile Apps...${NC}"

# Android
ANDROID_DEST="../AllToDo-Android/app/src/main/assets/$FALLBACK_NAME"
ANDROID_JS_DEST="../AllToDo-Android/app/src/main/assets/wasm_glue.js"
mkdir -p "$(dirname "$ANDROID_DEST")"
cp "$WASM_FILE" "$ANDROID_DEST"
cp "$JS_FILE" "$ANDROID_JS_DEST"
echo "Copied to Android Assets"
echo "Copied .js to Android Assets ($ANDROID_JS_DEST)"

# iOS
IOS_DEST="../AllToDo-iOS/AllToDo/Resources/$FALLBACK_NAME"
IOS_JS_DEST="../AllToDo-iOS/AllToDo/Resources/wasm_glue.js"
mkdir -p "$(dirname "$IOS_DEST")"
cp "$WASM_FILE" "$IOS_DEST"
cp "$JS_FILE" "$IOS_JS_DEST"
echo "Copied .wasm to iOS Resources"
echo "Copied .js to iOS Resources ($IOS_JS_DEST)"

echo -e "\n${GREEN}=== All Done! ===${NC}"
echo "1. Backend now has the active WASM logic."
echo "2. Mobile apps have the fallback WASM logic built-in."
echo "3. You can also upload via API using: curl -X POST -F 'version=1.1' -F 'file=@$WASM_FILE' http://localhost:8000/wasm/upload"
